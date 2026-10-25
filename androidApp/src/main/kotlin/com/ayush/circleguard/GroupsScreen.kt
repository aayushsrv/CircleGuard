
package com.ayush.circleguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.security.SecureRandom
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign

private val db: FirebaseFirestore
    get() = FirebaseFirestore.getInstance()

private val auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()

data class TrackingGroup(
    val id: String,
    val name: String,
    val joinCode: String
)

private fun createJoinCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = SecureRandom()
    return (1..8).map { chars[random.nextInt(chars.length)] }
        .joinToString("")
}

@Composable
fun GroupsScreen(
    onSignOut: () -> Unit,
    onViewMap: (String) -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
){
    val context = LocalContext.current
    val uid = auth.currentUser?.uid

    var groupName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(emptyList<TrackingGroup>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingGroupId by remember { mutableStateOf<String?>(null) }
    var pendingCreateGroupName by remember { mutableStateOf<String?>(null) }

    fun loadGroups() {
        val userId = uid ?: return

        db.collection("users").document(userId)
            .collection("groups")
            .get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.documents.map { it.id }

                if (ids.isEmpty()) {
                    groups = emptyList()
                    return@addOnSuccessListener
                }

                val loaded = mutableListOf<TrackingGroup>()
                var remaining = ids.size

                ids.forEach { id ->
                    db.collection("groups").document(id)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                loaded.add(
                                    TrackingGroup(
                                        id = id,
                                        name = doc.getString("name")
                                            ?: "Unnamed group",
                                        joinCode = doc.getString("joinCode") ?: ""
                                    )
                                )
                            }

                            remaining--
                            if (remaining == 0) {
                                groups = loaded.toList()
                            }
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining == 0) {
                                groups = loaded.toList()
                            }
                        }
                }
            }
            .addOnFailureListener {
                message = "Couldn't load groups: ${it.localizedMessage}"
            }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            val groupId = pendingGroupId
            pendingGroupId = null
            val createName = pendingCreateGroupName
            pendingCreateGroupName = null

            if (!granted) {
                message = "Location permission is required. Enable location access and try again."
            } else if (createName != null && uid != null) {
                busy = true
                createGroupAtCurrentLocation(context, uid, createName) { result ->
                    busy = false
                    message = result
                    if (result.startsWith("Group created!")) {
                        groupName = ""
                        loadGroups()
                    }
                }
            } else if (groupId != null) {
                startSharing(context, groupId) { result -> message = result }
            }
        }

    LaunchedEffect(uid) {
        loadGroups()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CircleGuard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your circle. Your safety.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onToggleTheme) {
                Text(
                    text = if (darkTheme) "☀" else "☾",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(
                onClick = {
                    LocationTrackingService.stop(context)
                    onSignOut()
                }
            ) {
                Text("Sign out")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Create a group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Create a safe circle and invite people you trust.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = {
                                groupName = it
                                message = ""
                            },
                            label = { Text("Group name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !busy
                        )
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !busy && uid != null && groupName.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                val userId = uid ?: return@Button
                                val name = groupName.trim()
                                message = ""
                                val hasFine = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasFine || hasCoarse) {
                                    busy = true
                                    createGroupAtCurrentLocation(context, userId, name) { result ->
                                        busy = false
                                        message = result
                                        if (result.startsWith("Group created!")) {
                                            groupName = ""
                                            loadGroups()
                                        }
                                    }
                                } else {
                                    pendingCreateGroupName = name
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        ) {
                            Text(if (busy) "Please wait..." else "Create group")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Join a group",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter an invitation code shared by a group member.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = {
                                joinCode = it.uppercase(Locale.ROOT)
                                    .filter { char -> char.isLetterOrDigit() }
                                    .take(8)
                                message = ""
                            },
                            label = { Text("8-character invitation code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !busy
                        )
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !busy && uid != null && joinCode.length == 8,
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                val userId = uid ?: return@Button
                                val code = joinCode

                                busy = true
                                message = ""

                                db.collection("invites").document(code).get()
                                    .addOnSuccessListener { invite ->
                                        val groupId = invite.getString("groupId")

                                        if (!invite.exists() || groupId == null) {
                                            message = "Invitation code not found."
                                            busy = false
                                        } else {
                                            val groupRef =
                                                db.collection("groups").document(groupId)

                                            groupRef.collection("members")
                                                .document(userId)
                                                .get()
                                                .addOnSuccessListener { existing ->
                                                    if (existing.exists()) {
                                                        message =
                                                            "You're already a member of this group."
                                                        busy = false
                                                    } else {
                                                        groupRef.collection("members")
                                                            .document(userId)
                                                            .set(
                                                                mapOf(
                                                                    "uid" to userId,
                                                                    "displayName" to currentMemberName(),
                                                                    "role" to "member",
                                                                    "joinedAt" to System.currentTimeMillis(),
                                                                    "inviteCode" to code
                                                                )
                                                            )
                                                            .addOnSuccessListener {
                                                                groupRef.get()
                                                                    .addOnSuccessListener { group ->
                                                                        val name =
                                                                            group.getString("name")
                                                                                ?: "Group"

                                                                        db.collection("users")
                                                                            .document(userId)
                                                                            .collection("groups")
                                                                            .document(groupId)
                                                                            .set(mapOf("name" to name))
                                                                            .addOnSuccessListener {
                                                                                message =
                                                                                    "Successfully joined $name."
                                                                                joinCode = ""
                                                                                loadGroups()
                                                                                busy = false
                                                                            }
                                                                            .addOnFailureListener {
                                                                                message =
                                                                                    "Joined, but couldn't update your group list: ${it.localizedMessage}"
                                                                                busy = false
                                                                            }
                                                                    }
                                                                    .addOnFailureListener {
                                                                        message =
                                                                            "Couldn't load group: ${it.localizedMessage}"
                                                                        busy = false
                                                                    }
                                                            }
                                                            .addOnFailureListener {
                                                                message =
                                                                    "Couldn't join group: ${it.localizedMessage}"
                                                                busy = false
                                                            }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    message =
                                                        "Couldn't check membership: ${it.localizedMessage}"
                                                    busy = false
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        message =
                                            "Couldn't check invitation: ${it.localizedMessage}"
                                        busy = false
                                    }
                            }
                        ) {
                            Text(if (busy) "Please wait..." else "Join group")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Your groups",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${groups.size} ${if (groups.size == 1) "group" else "groups"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No groups yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Create a new group or join one with an invitation code.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            items(groups, key = { it.id }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (group.joinCode.isNotBlank()) {
                            Text(
                                text = "Invitation code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = group.joinCode,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                val hasFine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasFine || hasCoarse) {
                                    startSharing(context, group.id) { result ->
                                        message = result
                                    }
                                } else {
                                    pendingGroupId = group.id
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        ) {
                            Text("Share my location")
                        }

                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            onClick = { onViewMap(group.id) }
                        ) {
                            Text("View member map")
                        }

                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                LocationTrackingService.stop(context)
                                message =
                                    "Location tracking stopped on this device."
                            }
                        ) {
                            Text("Stop location sharing")
                        }
                    }
                }
            }
        }
    }
}

private fun createGroupAtCurrentLocation(
    context: Context,
    userId: String,
    name: String,
    onResult: (String) -> Unit
) {
    val client = LocationServices.getFusedLocationProviderClient(context)

    fun createAt(latitude: Double, longitude: Double) {
        val groupRef = db.collection("groups").document()
        val code = createJoinCode()
        val now = System.currentTimeMillis()
        val initialRadius = 300

        val groupData = mapOf(
            "name" to name,
            "createdBy" to userId,
            "createdAt" to now,
            "joinCode" to code,
            "safeZoneEnabled" to true,
            "safeZoneLatitude" to latitude,
            "safeZoneLongitude" to longitude,
            "safeZoneRadius" to initialRadius,
            "safeZoneUpdatedBy" to userId,
            "safeZoneUpdatedAt" to now
        )

        groupRef.set(groupData)
            .continueWithTask {
                db.collection("invites").document(code).set(
                    mapOf("groupId" to groupRef.id, "createdBy" to userId, "createdAt" to now)
                )
            }
            .continueWithTask {
                groupRef.collection("members").document(userId).set(
                    mapOf(
                        "uid" to userId,
                        "displayName" to currentMemberName(),
                        "role" to "owner",
                        "joinedAt" to now,
                        "inviteCode" to code,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "locationUpdatedAt" to now
                    )
                )
            }
            .continueWithTask {
                db.collection("users").document(userId)
                    .collection("groups").document(groupRef.id)
                    .set(mapOf("name" to name))
            }
            .addOnSuccessListener {
                // Start live location sharing immediately so this creator's
                // moving safe zone follows their GPS in this group.
                LocationTrackingService.start(context, groupRef.id)
                onResult("Group created! Live location sharing started; the safe zone will follow the creator. Radius: $initialRadius m. Invitation code: $code")
            }
            .addOnFailureListener { error ->
                onResult("Couldn't create group: ${error.localizedMessage}")
            }
    }

    try {
        client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                createAt(location.latitude, location.longitude)
            } else {
                client.lastLocation.addOnSuccessListener { last ->
                    if (last != null) createAt(last.latitude, last.longitude)
                    else onResult("Couldn't get your location. Turn on GPS and enable location permission, then try again.")
                }.addOnFailureListener { error ->
                    onResult("Couldn't get your location: ${error.localizedMessage}")
                }
            }
        }.addOnFailureListener { error ->
            onResult("Couldn't get your location: ${error.localizedMessage}")
        }
    } catch (_: SecurityException) {
        onResult("Location permission is required to create a group safe zone.")
    }
}

private fun currentMemberName(): String {
    val user = FirebaseAuth.getInstance().currentUser

    return user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.substringBefore("@")
        ?: "Family member"
}

private fun startSharing(
    context: Context,
    groupId: String,
    onResult: (String) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser

    if (user == null) {
        onResult("Please sign in before sharing your location.")
        return
    }

    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) {
        onResult("Location permission is required.")
        return
    }

    try {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            )
            .addOnSuccessListener { location ->
                if (location == null) {
                    onResult("Couldn't get your current location. Try again.")
                    return@addOnSuccessListener
                }

                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { profile ->
                        val name = profile.getString("displayName")
                            ?.takeIf { it.isNotBlank() }
                            ?: currentMemberName()

                        db.collection("groups")
                            .document(groupId)
                            .collection("members")
                            .document(user.uid)
                            .set(
                                mapOf(
                                    "uid" to user.uid,
                                    "displayName" to name,
                                    "latitude" to location.latitude,
                                    "longitude" to location.longitude,
                                    "locationUpdatedAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            .addOnSuccessListener {
                                LocationTrackingService.start(context, groupId)
                                onResult(
                                    "Location sharing started. Updates are requested every 5 minutes."
                                )
                            }
                            .addOnFailureListener {
                                onResult(
                                    "Couldn't save location: ${it.localizedMessage}"
                                )
                            }
                    }
                    .addOnFailureListener {
                        onResult(
                            "Couldn't load your profile: ${it.localizedMessage}"
                        )
                    }
            }
            .addOnFailureListener {
                onResult(
                    "Couldn't get your location: ${it.localizedMessage}"
                )
            }
    } catch (_: SecurityException) {
        onResult("Location permission is required.")
    } catch (e: Exception) {
        onResult("Couldn't start location sharing: ${e.localizedMessage}")
    }
}
