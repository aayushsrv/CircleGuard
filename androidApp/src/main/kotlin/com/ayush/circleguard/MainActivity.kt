
package com.ayush.circleguard

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

private const val USER_TAG = "CircleGuardUser"

private fun ensureUserDocumentAndRegisterFcm() {
    val user = FirebaseAuth.getInstance().currentUser ?: return

    val userData = mutableMapOf<String, Any>(
        "uid" to user.uid,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    user.email?.let { userData["email"] = it }
    user.displayName?.let { userData["displayName"] = it }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.uid)
        .set(userData, SetOptions.merge())
        .addOnSuccessListener {
            Log.d(USER_TAG, "User document created or updated.")
            FcmTokenManager.registerDeviceToken()
        }
        .addOnFailureListener {
            Log.e(USER_TAG, "Failed to create/update user document.", it)
        }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser != null) {
            ensureUserDocumentAndRegisterFcm()
        }

        setContent {
            CircleGuardApp()
        }
    }
}

@Composable
fun CircleGuardApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    val systemDarkTheme = isSystemInDarkTheme()

    var darkTheme by rememberSaveable {
    mutableStateOf(systemDarkTheme)
    }

    var isLoggedIn by rememberSaveable {
        mutableStateOf(auth.currentUser != null)
    }

    var selectedMapGroup by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var selectedGroupName by rememberSaveable {
        mutableStateOf("")
    }

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF93B4FF),
            onPrimary = Color(0xFF102653),
            secondary = Color(0xFF70D7C8),
            background = Color(0xFF0B1220),
            surface = Color(0xFF111C2E),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF202D40),
            onSurfaceVariant = Color(0xFFB8C4D6)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            secondary = Color(0xFF0D9488),
            background = Color(0xFFF5F7FB),
            surface = Color.White,
            onBackground = Color(0xFF172554),
            onSurface = Color(0xFF172554),
            surfaceVariant = Color(0xFFEAF0F8),
            onSurfaceVariant = Color(0xFF64748B)
        )
    }

    LaunchedEffect(selectedMapGroup) {
        val groupId = selectedMapGroup

        if (groupId == null) {
            selectedGroupName = ""
        } else {
            selectedGroupName = "Loading group..."

            db.collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener { document ->
                    if (selectedMapGroup == groupId) {
                        selectedGroupName =
                            document.getString("name") ?: "Unnamed group"
                    }
                }
                .addOnFailureListener {
                    Log.e(USER_TAG, "Failed to load group.", it)
                    if (selectedMapGroup == groupId) {
                        selectedGroupName = "Group Map"
                    }
                }
        }
    }

    MaterialTheme(colorScheme = colors) {
        if (!isLoggedIn) {
            AuthScreen(
                onLoginSuccess = {
                    ensureUserDocumentAndRegisterFcm()
                    isLoggedIn = true
                },
                darkThemeOverride = darkTheme
            )
        } else {
            val groupId = selectedMapGroup

            if (groupId != null) {
                BackHandler {
                    selectedMapGroup = null
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("$selectedGroupName Member Map")
                            },
                            navigationIcon = {
                                TextButton(
                                    onClick = {
                                        selectedMapGroup = null
                                    }
                                ) {
                                    Text("Back")
                                }
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        darkTheme = !darkTheme
                                    }
                                ) {
                                    Text(
                                        if (darkTheme) "☀ Light" else "☾ Dark"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        GroupMapScreen(groupId = groupId)
                    }
                }
            } else {
                GroupsScreen(
                    onSignOut = {
                        auth.signOut()
                        selectedMapGroup = null
                        selectedGroupName = ""
                        isLoggedIn = false
                    },
                    onViewMap = { selectedMapGroup = it },
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        darkTheme = !darkTheme
                    }
                )
            }
        }
    }
}
