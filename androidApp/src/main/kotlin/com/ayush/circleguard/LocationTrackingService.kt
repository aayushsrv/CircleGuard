package com.ayush.circleguard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Shares this device's location with every group the user has enabled.
 * If the user is a group's creator, that group's moving safe-zone center is
 * updated to the same GPS fix. Firestore listeners in GroupMapScreen then
 * update the circle for every group member.
 */
class LocationTrackingService : Service() {

    companion object {
        private const val TAG = "CircleGuardLocation"
        private const val CHANNEL_ID = "circleguard_location"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "circleguard_location_tracking"
        private const val PREF_GROUP_IDS = "active_group_ids"
        private const val EXTRA_GROUP_ID = "groupId"
        private const val ACTION_ADD_GROUP = "com.ayush.circleguard.ADD_GROUP"
        private const val ACTION_REMOVE_GROUP = "com.ayush.circleguard.REMOVE_GROUP"

        // Requested intervals. Android can throttle background location updates.
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val MIN_UPDATE_INTERVAL_MS = 10_000L
        private const val MAX_UPDATE_DELAY_MS = 15_000L

        fun start(context: Context, groupId: String) {
            if (groupId.isBlank()) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet(PREF_GROUP_IDS, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            ids.add(groupId)
            prefs.edit().putStringSet(PREF_GROUP_IDS, ids).apply()

            val intent = Intent(context, LocationTrackingService::class.java)
                .setAction(ACTION_ADD_GROUP)
                .putExtra(EXTRA_GROUP_ID, groupId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stop sharing for one group without disabling other groups. */
        fun stop(context: Context, groupId: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet(PREF_GROUP_IDS, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            ids.remove(groupId)
            prefs.edit().putStringSet(PREF_GROUP_IDS, ids).apply()

            if (ids.isEmpty()) {
                context.stopService(Intent(context, LocationTrackingService::class.java))
            } else {
                val intent = Intent(context, LocationTrackingService::class.java)
                    .setAction(ACTION_REMOVE_GROUP)
                    .putExtra(EXTRA_GROUP_ID, groupId)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /** Stop sharing for all groups, e.g. on sign-out. */
        fun stop(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(PREF_GROUP_IDS).apply()
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var locationCallback: LocationCallback? = null
    private var activeGroupIds: Set<String> = emptySet()
    private var creatorStatus: MutableMap<String, Boolean> = mutableMapOf()
    private var creatorChecksInFlight: MutableSet<String> = mutableSetOf()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        activeGroupIds = prefs.getStringSet(PREF_GROUP_IDS, emptySet())?.toSet()
            ?: emptySet()

        if (activeGroupIds.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start foreground service.", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission is missing.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Cache creator ownership once per group. Refresh this cache when group IDs change.
        activeGroupIds.forEach { groupId -> checkCreatorStatus(groupId) }
        if (locationCallback == null) startLocationUpdates()
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val groupsNow = getActiveGroupIds()
                activeGroupIds = groupsNow
                if (groupsNow.isEmpty()) {
                    stopSelf()
                    return
                }

                groupsNow.forEach { groupId ->
                    saveMemberLocation(
                        groupId = groupId,
                        uid = uid,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                    if (creatorStatus[groupId] == true) {
                        updateMovingSafeZone(
                            groupId = groupId,
                            uid = uid,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    } else if (creatorStatus[groupId] == null) {
                        checkCreatorStatus(groupId)
                    }
                }
            }
        }
        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(request, callback, mainLooper)
                .addOnSuccessListener { Log.d(TAG, "Frequent location updates requested.") }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to request location updates.", error)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission was revoked.", e)
            stopSelf()
        }
    }

    private fun getActiveGroupIds(): Set<String> {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_GROUP_IDS, emptySet())?.toSet() ?: emptySet()
    }

    private fun checkCreatorStatus(groupId: String) {
        if (creatorStatus.containsKey(groupId) || !creatorChecksInFlight.add(groupId)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            creatorChecksInFlight.remove(groupId)
            return
        }
        firestore.collection("groups").document(groupId).get()
            .addOnSuccessListener { document ->
                creatorStatus[groupId] = document.getString("createdBy") == uid
                creatorChecksInFlight.remove(groupId)
                Log.d(TAG, "Creator status for $groupId: ${creatorStatus[groupId]}")
            }
            .addOnFailureListener { error ->
                creatorChecksInFlight.remove(groupId)
                Log.e(TAG, "Couldn't check group creator for $groupId", error)
            }
    }

    private fun saveMemberLocation(
        groupId: String,
        uid: String,
        latitude: Double,
        longitude: Double
    ) {
        val memberRef = firestore.collection("groups").document(groupId)
            .collection("members").document(uid)
        val locationData = mapOf(
            "uid" to uid,
            "latitude" to latitude,
            "longitude" to longitude,
            "locationUpdatedAt" to FieldValue.serverTimestamp()
        )
        memberRef.set(locationData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Member location saved for group $groupId: $latitude, $longitude")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save member location for $groupId", error)
            }
    }

    /** Only the group creator's device moves that group's shared safe-zone center. */
    private fun updateMovingSafeZone(
        groupId: String,
        uid: String,
        latitude: Double,
        longitude: Double
    ) {
        val groupRef = firestore.collection("groups").document(groupId)
        groupRef.update(
            mapOf(
                "safeZoneEnabled" to true,
                "safeZoneLatitude" to latitude,
                "safeZoneLongitude" to longitude,
                "safeZoneUpdatedBy" to uid,
                "safeZoneUpdatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnFailureListener { error ->
            Log.e(TAG, "Failed to update moving safe zone for $groupId", error)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CircleGuard location sharing")
            .setContentText("Sharing location for ${activeGroupIds.size} group(s).")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when CircleGuard is sharing your location."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
