
package com.ayush.circleguard

import android.util.Patterns
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

private val GuardBlue = Color(0xFF2563EB)
private val GuardDarkBlue = Color(0xFF172554)
private val GuardTeal = Color(0xFF0D9488)

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    darkThemeOverride: Boolean? = null
) {
    val darkMode = darkThemeOverride ?: isSystemInDarkTheme()
    val authColors = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFF93B4FF),
            onPrimary = Color(0xFF102653),
            secondary = Color(0xFF70D7C8),
            background = Color(0xFF0B1220),
            surface = Color(0xFF111C2E),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF202D40),
            onSurfaceVariant = Color(0xFFB8C4D6),
            error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = GuardBlue,
            onPrimary = Color.White,
            secondary = GuardTeal,
            background = Color(0xFFF5F7FB),
            surface = Color.White,
            onBackground = GuardDarkBlue,
            onSurface = GuardDarkBlue,
            surfaceVariant = Color(0xFFEAF0F8),
            onSurfaceVariant = Color(0xFF64748B),
            error = Color(0xFFB3261E)
        )
    }

    MaterialTheme(colorScheme = authColors) {
        AuthContent(onLoginSuccess = onLoginSuccess)
    }
}

@Composable
private fun ShieldLogo() {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.size(76.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.primary.copy(alpha = 0.12f)
    ) {
        Box(
            modifier = Modifier.padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val shield = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.04f)
                    lineTo(size.width * 0.91f, size.height * 0.20f)
                    lineTo(size.width * 0.86f, size.height * 0.59f)
                    cubicTo(
                        size.width * 0.81f, size.height * 0.78f,
                        size.width * 0.64f, size.height * 0.92f,
                        size.width * 0.5f, size.height * 0.98f
                    )
                    cubicTo(
                        size.width * 0.36f, size.height * 0.92f,
                        size.width * 0.19f, size.height * 0.78f,
                        size.width * 0.14f, size.height * 0.59f
                    )
                    lineTo(size.width * 0.09f, size.height * 0.20f)
                    close()
                }

                drawPath(
                    path = shield,
                    color = colors.primary,
                    style = Stroke(width = size.width * 0.09f)
                )

                val check = Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.49f)
                    lineTo(size.width * 0.44f, size.height * 0.63f)
                    lineTo(size.width * 0.70f, size.height * 0.36f)
                }

                drawPath(
                    path = check,
                    color = colors.secondary,
                    style = Stroke(
                        width = size.width * 0.09f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun AuthContent(onLoginSuccess: () -> Unit) {
    var isLogin by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var resettingPassword by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by rememberSaveable { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShieldLogo()

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "CircleGuard",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your circle. Your safety. Always connected.",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surface,
                    shadowElevation = if (isSystemInDarkTheme()) 0.dp else 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (isLogin) "Welcome back" else "Create your account",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )

                        Text(
                            text = if (isLogin) {
                                "Log in to stay connected with your group."
                            } else {
                                "Create an account to protect your circle."
                            },
                            fontSize = 14.sp,
                            color = colors.onSurfaceVariant
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AuthTab(
                                    text = "Log in",
                                    selected = isLogin,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    isLogin = true
                                    message = null
                                    confirmPassword = ""
                                }

                                AuthTab(
                                    text = "Sign up",
                                    selected = !isLogin,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    isLogin = false
                                    message = null
                                }
                            }
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                message = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email address") },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = {
                                Text(
                                    text = "@",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primary
                                )
                            },
                            singleLine = true,
                            enabled = !loading && !resettingPassword,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email
                            )
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                message = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            leadingIcon = {
                                Text(
                                    text = "●",
                                    fontSize = 17.sp,
                                    color = colors.primary
                                )
                            },
                            singleLine = true,
                            enabled = !loading && !resettingPassword,
                            shape = RoundedCornerShape(14.dp),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        passwordVisible = !passwordVisible
                                    },
                                    enabled = !loading && !resettingPassword
                                ) {
                                    Text(
                                        text = if (passwordVisible) "Hide" else "Show"
                                    )
                                }
                            }
                        )

                        if (isLogin) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val cleanEmail = email.trim()

                                        if (!Patterns.EMAIL_ADDRESS.matcher(
                                                cleanEmail
                                            ).matches()
                                        ) {
                                            isError = true
                                            message =
                                                "Enter your email address above first."
                                        } else {
                                            resettingPassword = true
                                            message = null

                                            FirebaseAuth.getInstance()
                                                .sendPasswordResetEmail(cleanEmail)
                                                .addOnCompleteListener { result ->
                                                    resettingPassword = false
                                                    isError = !result.isSuccessful
                                                    message = if (result.isSuccessful) {
                                                        "If an account exists for this email, a reset link will be sent."
                                                    } else {
                                                        "Could not send the reset email. Check your connection and try again."
                                                    }
                                                }
                                        }
                                    },
                                    enabled = !loading && !resettingPassword
                                ) {
                                    Text("Forgot password?")
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    message = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Confirm password") },
                                leadingIcon = {
                                    Text(
                                        text = "✓",
                                        fontSize = 20.sp,
                                        color = colors.primary
                                    )
                                },
                                singleLine = true,
                                enabled = !loading,
                                shape = RoundedCornerShape(14.dp),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password
                                )
                            )
                        }

                        if (message != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = (if (isError) colors.error else colors.secondary)
                                    .copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = message!!,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (isError) colors.error else colors.secondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val cleanEmail = email.trim()

                                when {
                                    !Patterns.EMAIL_ADDRESS.matcher(
                                        cleanEmail
                                    ).matches() -> {
                                        isError = true
                                        message = "Enter a valid email address."
                                    }

                                    password.isBlank() -> {
                                        isError = true
                                        message = "Enter your password."
                                    }

                                    !isLogin && password.length < 6 -> {
                                        isError = true
                                        message =
                                            "Password must contain at least 6 characters."
                                    }

                                    !isLogin && password != confirmPassword -> {
                                        isError = true
                                        message = "Passwords do not match."
                                    }

                                    else -> {
                                        loading = true
                                        message = null

                                        val auth = FirebaseAuth.getInstance()

                                        val task = if (isLogin) {
                                            auth.signInWithEmailAndPassword(
                                                cleanEmail,
                                                password
                                            )
                                        } else {
                                            auth.createUserWithEmailAndPassword(
                                                cleanEmail,
                                                password
                                            )
                                        }

                                        task.addOnCompleteListener { result ->
                                            loading = false

                                            if (result.isSuccessful) {
                                                onLoginSuccess()
                                            } else {
                                                isError = true
                                                message = when {
                                                    result.exception?.message?.contains(
                                                        "network",
                                                        ignoreCase = true
                                                    ) == true ->
                                                        "Network error. Check your connection."

                                                    else ->
                                                        result.exception?.localizedMessage
                                                            ?: "Authentication failed. Please try again."
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            enabled = !loading && !resettingPassword,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            )
                        ) {
                            if (loading || resettingPassword) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(21.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.onPrimary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Please wait...")
                            } else {
                                Text(
                                    text = if (isLogin) {
                                        "Log in securely"
                                    } else {
                                        "Create account"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "Your account helps keep your circle connected.",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = colors.secondary
                    ) {}

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Your safety circle, connected.",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) colors.surface else Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.onSurfaceVariant
            )
        }
    }
}
