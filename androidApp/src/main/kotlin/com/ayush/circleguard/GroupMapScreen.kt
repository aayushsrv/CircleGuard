
package com.ayush.circleguard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat
import java.util.Date

private const val DEFAULT_RADIUS = 300
private const val MIN_RADIUS = 100
private const val MAX_RADIUS = 2000
private const val RADIUS_STEP = 50

private data class MemberLocation(
    val uid: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: Long
)

private fun readTimestampMillis(document: DocumentSnapshot): Long {
    return when (val value = document.get("locationUpdatedAt")) {
        is Number -> value.toLong()
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> 0L
    }
}

@Composable
fun GroupMapScreen(groupId: String) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    var groupName by remember(groupId) {
        mutableStateOf("Loading group...")
    }
    var creatorUid by remember(groupId) {
        mutableStateOf<String?>(null)
    }
    var locations by remember(groupId) {
        mutableStateOf<List<MemberLocation>>(emptyList())
    }

    var zoneEnabled by remember(groupId) {
        mutableStateOf(false)
    }
    var zoneLatitude by remember(groupId) {
        mutableStateOf<Double?>(null)
    }
    var zoneLongitude by remember(groupId) {
        mutableStateOf<Double?>(null)
    }

    var savedRadius by remember(groupId) {
        mutableIntStateOf(DEFAULT_RADIUS)
    }
    var selectedRadius by remember(groupId) {
        mutableFloatStateOf(DEFAULT_RADIUS.toFloat())
    }

    var myLocation by remember(groupId) {
        mutableStateOf<LatLng?>(null)
    }
    var message by remember(groupId) {
        mutableStateOf("")
    }

    var gettingLocation by remember(groupId) {
        mutableStateOf(false)
    }
    var savingRadius by remember(groupId) {
        mutableStateOf(false)
    }
    var resettingZone by remember(groupId) {
        mutableStateOf(false)
    }
    var isMapFullScreen by remember(groupId) {
        mutableStateOf(false)
    }
    var initialCameraFocused by remember(groupId) {
        mutableStateOf(false)
    }

    val isCreator = currentUid != null && currentUid == creatorUid

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(20.5937, 78.9629),
            4f
        )
    }

    // Get a fresh GPS fix; use last-known location as a fallback.
    fun getCurrentLocation(onSuccess: (LatLng) -> Unit) {
        if (!hasLocationPermission()) {
            message = "Please allow location permission."
            return
        }

        if (gettingLocation) return

        gettingLocation = true
        message = "Finding your current location..."

        val client =
            LocationServices.getFusedLocationProviderClient(context)

        try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    gettingLocation = false

                    val current = LatLng(
                        location.latitude,
                        location.longitude
                    )
                    myLocation = current
                    onSuccess(current)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { lastLocation ->
                            gettingLocation = false

                            if (lastLocation != null) {
                                val current = LatLng(
                                    lastLocation.latitude,
                                    lastLocation.longitude
                                )
                                myLocation = current
                                onSuccess(current)
                            } else {
                                message =
                                    "Location unavailable. Enable GPS and try again."
                            }
                        }
                        .addOnFailureListener { error ->
                            gettingLocation = false
                            message =
                                "Couldn't retrieve location: ${error.localizedMessage}"
                        }
                }
            }.addOnFailureListener { error ->
                gettingLocation = false
                message = "Location error: ${error.localizedMessage}"
            }
        } catch (_: SecurityException) {
            gettingLocation = false
            message = "Please allow location permission."
        }
    }

    // This action moves the camera only. It does not edit the safe zone.
    fun goToCurrentLocation() {
        getCurrentLocation { current ->
            cameraState.position =
                CameraPosition.fromLatLngZoom(current, 18f)
            message = "Showing your current location."
        }
    }

    // Save the radius without moving an existing safe-zone center.
    fun saveRadiusOnly() {
        val uid = currentUid

        if (uid == null || uid != creatorUid) {
            message = "Only the group creator can change the safe zone."
            return
        }

        if (!hasLocationPermission()) {
            message = "Location permission is required."
            return
        }

        if (savingRadius || resettingZone) return

        val radius = selectedRadius.toInt()
            .coerceIn(MIN_RADIUS, MAX_RADIUS)

        val centerExists =
            zoneLatitude != null && zoneLongitude != null

        savingRadius = true
        message = "Saving radius..."

        fun writeRadius(center: LatLng?) {
            val updates = mutableMapOf<String, Any>(
                "safeZoneRadius" to radius,
                "safeZoneEnabled" to true,
                "safeZoneUpdatedBy" to uid,
                "safeZoneUpdatedAt" to System.currentTimeMillis()
            )

            // Initialize the center only if the zone has no center yet.
            if (center != null) {
                updates["safeZoneLatitude"] = center.latitude
                updates["safeZoneLongitude"] = center.longitude
            }

            db.collection("groups")
                .document(groupId)
                .update(updates)
                .addOnSuccessListener {
                    savingRadius = false
                    savedRadius = radius
                    selectedRadius = radius.toFloat()
                    message = "Radius saved: $radius metres."
                }
                .addOnFailureListener { error ->
                    savingRadius = false
                    message =
                        "Couldn't save radius: ${error.localizedMessage}"
                }
        }

        if (centerExists) {
            writeRadius(null)
        } else {
            getCurrentLocation { current ->
                writeRadius(current)
            }
        }
    }

    // Reset the safe-zone center to the current location,
    // preserving the radius already saved in Firestore.
    fun resetZoneToMyLocation() {
        val uid = currentUid

        if (uid == null || uid != creatorUid) {
            message = "Only the group creator can reset the safe zone."
            return
        }

        if (!hasLocationPermission()) {
            message = "Location permission is required."
            return
        }

        if (savingRadius || resettingZone) return

        resettingZone = true
        message = "Getting your location to reset the zone..."

        getCurrentLocation { current ->
            db.collection("groups")
                .document(groupId)
                .update(
                    mapOf(
                        "safeZoneLatitude" to current.latitude,
                        "safeZoneLongitude" to current.longitude,
                        "safeZoneRadius" to savedRadius,
                        "safeZoneEnabled" to true,
                        "safeZoneUpdatedBy" to uid,
                        "safeZoneUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    resettingZone = false
                    message =
                        "Zone center reset. Radius remains $savedRadius metres."
                }
                .addOnFailureListener { error ->
                    resettingZone = false
                    message =
                        "Couldn't reset zone: ${error.localizedMessage}"
                }
        }
    }

    val currentLocationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                goToCurrentLocation()
            } else {
                message = "Allow location access to find your position."
            }
        }

    val radiusPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                saveRadiusOnly()
            } else {
                message = "Allow location access to save the radius."
            }
        }

    val resetZonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                resetZoneToMyLocation()
            } else {
                message = "Allow location access to reset the safe zone."
            }
        }

    // Listen to the group's settings.
    DisposableEffect(groupId) {
        val registration = db.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    message = "Couldn't load group: ${error.localizedMessage}"
                } else if (snapshot != null && snapshot.exists()) {
                    groupName =
                        snapshot.getString("name") ?: "Unnamed group"
                    creatorUid = snapshot.getString("createdBy")
                    zoneEnabled =
                        snapshot.getBoolean("safeZoneEnabled") == true
                    zoneLatitude = snapshot.getDouble("safeZoneLatitude")
                    zoneLongitude = snapshot.getDouble("safeZoneLongitude")

                    val radius = (
                        (snapshot.get("safeZoneRadius") as? Number)
                            ?.toInt() ?: DEFAULT_RADIUS
                        ).coerceIn(MIN_RADIUS, MAX_RADIUS)

                    savedRadius = radius

                    if (!savingRadius) {
                        selectedRadius = radius.toFloat()
                    }
                }
            }

        onDispose {
            registration.remove()
        }
    }

    // Listen to this group's member documents.
    DisposableEffect(groupId) {
        val registration = db.collection("groups")
            .document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    message =
                        "Couldn't load members: ${error.localizedMessage}"
                } else if (snapshot != null) {
                    locations = snapshot.documents
                        .distinctBy { it.id }
                        .mapNotNull { doc ->
                            val lat = doc.getDouble("latitude")
                            val lng = doc.getDouble("longitude")

                            if (
                                lat != null &&
                                lng != null &&
                                lat in -90.0..90.0 &&
                                lng in -180.0..180.0
                            ) {
                                MemberLocation(
                                    uid = doc.id,
                                    displayName =
                                        doc.getString("displayName")
                                            ?.takeIf { it.isNotBlank() }
                                            ?: "Group member",
                                    latitude = lat,
                                    longitude = lng,
                                    updatedAt = readTimestampMillis(doc)
                                )
                            } else {
                                null
                            }
                        }
                }
            }

        onDispose {
            registration.remove()
        }
    }

    // Keep geofence registration in sync with the saved safe zone.
    LaunchedEffect(
        groupId,
        currentUid,
        zoneEnabled,
        zoneLatitude,
        zoneLongitude,
        savedRadius
    ) {
        if (!zoneEnabled) {
            GeofenceHelper.unregister(context, groupId) { _, _ -> }
        } else if (
            zoneLatitude != null &&
            zoneLongitude != null &&
            currentUid != null
        ) {
            GeofenceHelper.register(
                context = context,
                groupId = groupId,
                latitude = zoneLatitude!!,
                longitude = zoneLongitude!!,
                radiusMeters = savedRadius.toFloat()
            ) { _, result ->
                message = result
            }
        }
    }

    // Set initial camera focus once. Member updates won't keep moving it.
    LaunchedEffect(
        groupId,
        zoneEnabled,
        zoneLatitude,
        zoneLongitude,
        locations.size
    ) {
        if (!initialCameraFocused) {
            val center = if (
                zoneEnabled &&
                zoneLatitude != null &&
                zoneLongitude != null
            ) {
                LatLng(zoneLatitude!!, zoneLongitude!!)
            } else {
                locations.firstOrNull()?.let {
                    LatLng(it.latitude, it.longitude)
                }
            }

            if (center != null) {
                cameraState.position =
                    CameraPosition.fromLatLngZoom(center, 15f)
                initialCameraFocused = true
            }
        }
    }

    // Full-screen mode shows only the map and its controls.
    if (isMapFullScreen) {
        GroupMapCanvas(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            groupName = groupName,
            zoneEnabled = zoneEnabled,
            zoneLatitude = zoneLatitude,
            zoneLongitude = zoneLongitude,
            savedRadius = savedRadius,
            locations = locations,
            onMyLocationClick = {
                if (hasLocationPermission()) {
                    goToCurrentLocation()
                } else {
                    currentLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onFullScreenClick = { isMapFullScreen = false },
            fullScreen = true
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Compact status row; the screen title already lives in MainActivity's top bar.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (zoneEnabled && zoneLatitude != null && zoneLongitude != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (
                                zoneEnabled &&
                                zoneLatitude != null &&
                                zoneLongitude != null
                            ) "ACTIVE" else "NOT SET",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (
                                zoneEnabled &&
                                zoneLatitude != null &&
                                zoneLongitude != null
                            ) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (
                            zoneEnabled &&
                            zoneLatitude != null &&
                            zoneLongitude != null
                        ) "Safe zone · $savedRadius m" else "No safe zone configured",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${locations.size} sharing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Compact creator controls.
            if (isCreator) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Safe zone radius",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedRadius.toInt()} m",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = selectedRadius,
                            onValueChange = { value ->
                                val snapped = MIN_RADIUS +
                                    ((value - MIN_RADIUS) / RADIUS_STEP).toInt() * RADIUS_STEP
                                selectedRadius = snapped.toFloat().coerceIn(
                                    MIN_RADIUS.toFloat(),
                                    MAX_RADIUS.toFloat()
                                )
                            },
                            valueRange = MIN_RADIUS.toFloat()..MAX_RADIUS.toFloat(),
                            steps = ((MAX_RADIUS - MIN_RADIUS) / RADIUS_STEP) - 1,
                            enabled = !savingRadius && !resettingZone
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("100 m", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2,000 m", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (hasLocationPermission()) {
                                        saveRadiusOnly()
                                    } else {
                                        radiusPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !savingRadius && !resettingZone,
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (savingRadius) "Saving…" else "Save radius",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (hasLocationPermission()) {
                                        resetZoneToMyLocation()
                                    } else {
                                        resetZonePermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !savingRadius && !resettingZone,
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (resettingZone) "Resetting…" else "Reset zone",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Safe-zone radius: $savedRadius m",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (message.isNotBlank()) {
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }

            // Map receives most of the available space; controls float on top.
            GroupMapCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                cameraState = cameraState,
                groupName = groupName,
                zoneEnabled = zoneEnabled,
                zoneLatitude = zoneLatitude,
                zoneLongitude = zoneLongitude,
                savedRadius = savedRadius,
                locations = locations,
                onMyLocationClick = {
                    if (hasLocationPermission()) {
                        goToCurrentLocation()
                    } else {
                        currentLocationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                onFullScreenClick = { isMapFullScreen = true },
                fullScreen = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${locations.size} sharing location",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 118.dp)
            ) {
                items(locations, key = { it.uid }) { member ->
                    ListItem(
                        headlineContent = {
                            Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                        },
                        supportingContent = {
                            Text(
                                if (member.updatedAt > 0L) {
                                    "Updated: ${
                                        DateFormat.getDateTimeInstance().format(
                                            Date(member.updatedAt)
                                        )
                                    }"
                                } else {
                                    "Location available"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMapCanvas(
    modifier: Modifier,
    cameraState: CameraPositionState,
    groupName: String,
    zoneEnabled: Boolean,
    zoneLatitude: Double?,
    zoneLongitude: Double?,
    savedRadius: Int,
    locations: List<MemberLocation>,
    onMyLocationClick: () -> Unit,
    onFullScreenClick: () -> Unit,
    fullScreen: Boolean
) {
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState
        ) {
            // Draw the safe-zone circle without a separate center marker.
            if (
                zoneEnabled &&
                zoneLatitude != null &&
                zoneLongitude != null
            ) {
                Circle(
                    center = LatLng(zoneLatitude, zoneLongitude),
                    radius = savedRadius.toDouble(),
                    fillColor = Color(0x3333AA66),
                    strokeColor = Color(0xFF218838),
                    strokeWidth = 3f
                )
            }

            // One marker per valid member document.
            locations.forEach { member ->
                key(member.uid) {
                    val markerState = remember(member.uid) {
                        MarkerState(
                            position = LatLng(member.latitude, member.longitude)
                        )
                    }
                    LaunchedEffect(member.latitude, member.longitude) {
                        markerState.position = LatLng(member.latitude, member.longitude)
                    }
                    Marker(
                        state = markerState,
                        title = member.displayName,
                        snippet = if (member.updatedAt > 0L) {
                            "Updated: ${DateFormat.getDateTimeInstance().format(Date(member.updatedAt))}"
                        } else {
                            "Location available"
                        }
                    )
                }
            }
        }

        // Expand / close map control.
        FilledTonalIconButton(
            onClick = onFullScreenClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text(
                text = if (fullScreen) "✕" else "⛶",
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Current location control. This does not modify the safe zone.
        SmallFloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "⌖",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
