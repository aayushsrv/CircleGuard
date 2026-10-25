
package com.ayush.circleguard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Midnight = Color(0xFF101827)
private val CardColor = Color(0xFF19263A)
private val Teal = Color(0xFF37D6C0)
private val Amber = Color(0xFFFFB45E)
private val Muted = Color(0xFF9EADC2)

@Composable
fun DashboardScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDemoAlert by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Midnight,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF141F30),
                contentColor = Teal
            ) {
                val tabs = listOf(
                    "Home", "Groups", "Activity", "Settings"
                )
                val icons = listOf("⌂", "◎", "◷", "⚙")

                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(icons[index], fontSize = 21.sp)
                        },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal,
                            selectedTextColor = Teal,
                            indicatorColor = CardColor,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = 20.dp,
                vertical = 22.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "C I R C L E G U A R D",
                            color = Teal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Your circle",
                            color = Color.White,
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Everyone connected, everyone informed.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(CardColor, CircleShape)
                            .border(1.dp, Teal.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "A",
                            color = Teal,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "COLLEGE FRIENDS",
                                    color = Muted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.4.sp
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Campus safe zone",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            StatusChip("ACTIVE", Teal)
                        }

                        SafeZoneIllustration()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Metric("ZONE RADIUS", "300 metres")
                            Metric("MEMBERS", "3 members")
                        }

                        Button(
                            onClick = {
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = Midnight
                            )
                        ) {
                            Text(
                                "Manage safe zone",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                SectionHeading(
                    title = "YOUR MEMBERS",
                    trailing = "3 TOTAL"
                )
            }

            item {
                MemberRow(
                    initials = "AS",
                    name = "Ayush",
                    detail = "You · Inside safe zone",
                    color = Teal
                )
            }

            item {
                MemberRow(
                    initials = "RK",
                    name = "Riya",
                    detail = "Inside safe zone",
                    color = Teal
                )
            }

            item {
                MemberRow(
                    initials = "AM",
                    name = "Aman",
                    detail = "Outside safe zone · Demo data",
                    color = Amber
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF30291F)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ACTIVITY PREVIEW",
                            color = Amber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp
                        )
                        Text(
                            "Test an exit alert",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Preview the alert experience. This does not " +
                                    "change anyone's actual location.",
                            color = Muted,
                            fontSize = 12.sp
                        )

                        OutlinedButton(
                            onClick = { showDemoAlert = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Preview demo alert",
                                color = Amber
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "+  Create or join a circle",
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showDemoAlert) {
        AlertDialog(
            onDismissRequest = { showDemoAlert = false },
            containerColor = CardColor,
            title = {
                Text(
                    "Demo exit alert",
                    color = Amber,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "A simulated member has left the safe zone. " +
                            "This is only a UI preview; no real geofence " +
                            "or push notification has been triggered.",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDemoAlert = false }
                ) {
                    Text("Got it", color = Teal)
                }
            }
        )
    }
}

@Composable
private fun SafeZoneIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                Color(0xFF22354A),
                RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .border(
                        1.dp,
                        Teal.copy(alpha = 0.65f),
                        CircleShape
                    )
                    .background(
                        Teal.copy(alpha = 0.09f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(65.dp)
                        .background(
                            Teal.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⌖",
                        color = Teal,
                        fontSize = 38.sp
                    )
                }
            }

            Text(
                "SAFE ZONE PREVIEW",
                color = Color.White,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
        Text(
            value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    trailing: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )
        Text(
            trailing,
            color = Teal,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 7.dp
            ),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MemberRow(
    initials: String,
    name: String,
    detail: String,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color.copy(alpha = 0.14f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    detail,
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
