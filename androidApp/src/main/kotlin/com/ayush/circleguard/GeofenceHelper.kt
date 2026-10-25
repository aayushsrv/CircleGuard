
package com.ayush.circleguard

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceHelper {

    private const val DEFAULT_RADIUS_METERS = 300f
    private const val GEOFENCE_EXPIRATION = Geofence.NEVER_EXPIRE

    private fun client(context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private fun pendingIntent(
        context: Context,
        groupId: String
    ): PendingIntent {
        val intent = Intent(
            context,
            GeofenceTransitionsReceiver::class.java
        ).apply {
            action = "com.ayush.circleguard.GEOFENCE_$groupId"
            putExtra(
                GeofenceTransitionsReceiver.EXTRA_GROUP_ID,
                groupId
            )
        }

        return PendingIntent.getBroadcast(
            context,
            groupId.hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_MUTABLE
        )
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val background = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!background) return false
        }

        return true
    }

    fun register(
        context: Context,
        groupId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = DEFAULT_RADIUS_METERS,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasRequiredPermissions(context)) {
            onResult(
                false,
                "Precise and background location permissions are required to monitor the safe zone."
            )
            return
        }

        if (latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            onResult(false, "The saved safe-zone coordinates are invalid.")
            return
        }

        if (!radiusMeters.isFinite() ||
            radiusMeters !in 100f..2000f
        ) {
            onResult(false, "Choose a radius between 100 and 2,000 metres.")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(groupId)
            .setCircularRegion(
                latitude,
                longitude,
                radiusMeters
            )
            .setExpirationDuration(GEOFENCE_EXPIRATION)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .setNotificationResponsiveness(10_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        val pi = pendingIntent(context, groupId)

        try {
            client(context).removeGeofences(pi)
                .addOnCompleteListener {
                    try {
                        client(context).addGeofences(request, pi)
                            .addOnSuccessListener {
                                onResult(
                                    true,
                                    "Safe zone registered: ${radiusMeters.toInt()} metres."
                                )
                            }
                            .addOnFailureListener { error ->
                                onResult(
                                    false,
                                    "Geofence registration failed: ${error.localizedMessage}"
                                )
                            }
                    } catch (_: SecurityException) {
                        onResult(
                            false,
                            "Location permission is required to register the geofence."
                        )
                    }
                }
        } catch (_: SecurityException) {
            onResult(
                false,
                "Location permission is required to register the geofence."
            )
        }
    }

    fun unregister(
        context: Context,
        groupId: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        try {
            client(context).removeGeofences(pendingIntent(context, groupId))
                .addOnSuccessListener {
                    onResult(true, "Safe-zone monitoring stopped.")
                }
                .addOnFailureListener { error ->
                    onResult(
                        false,
                        "Couldn't remove geofence: ${error.localizedMessage}"
                    )
                }
        } catch (_: SecurityException) {
            onResult(false, "Permission is required to remove the geofence.")
        }
    }
}
