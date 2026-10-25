
package com.ayush.circleguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeofenceTransitionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)

        if (event == null || event.hasError()) {
            Log.e(TAG, "Invalid geofence event: ${event?.errorCode}")
            return
        }

        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            else -> {
                Log.d(TAG, "Ignoring unsupported geofence transition")
                return
            }
        }

        // The geofence request ID is the group ID registered by GeofenceHelper.
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID)
            ?: event.triggeringGeofences?.firstOrNull()?.requestId

        if (groupId.isNullOrBlank()) {
            Log.e(TAG, "Missing group ID; cannot send notification")
            return
        }

        val title: String
        val message: String

        if (transition == "ENTER") {
            title = "CircleGuard: Entered safe zone"
            message = "This device entered the safe zone for group ${groupId.take(8)}."
        } else {
            title = "CircleGuard: Left safe zone"
            message = "This device left the safe zone for group ${groupId.take(8)}."
        }

        // Preserve the local notification.
        showNotification(context, groupId, title, message)

        // Notify other group members through the authenticated Worker.
        sendRemoteNotification(groupId, transition)
    }

    private fun sendRemoteNotification(groupId: String, transition: String) {
        val pendingResult = goAsync()

        Thread {
            try {
                val user = FirebaseAuth.getInstance().currentUser

                if (user == null) {
                    Log.e(TAG, "No signed-in Firebase user; remote alert skipped")
                    return@Thread
                }

                val idToken = Tasks.await(user.getIdToken(false)).token

                if (idToken.isNullOrBlank()) {
                    Log.e(TAG, "Could not obtain Firebase ID token")
                    return@Thread
                }

                val payload = JSONObject().apply {
                    put("groupId", groupId)
                    put("transition", transition)
                }.toString()

                val connection = URL(WORKER_URL).openConnection()
                    as HttpURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Authorization",
                        "Bearer $idToken"
                    )
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )

                    connection.outputStream.use { output ->
                        output.write(payload.toByteArray(Charsets.UTF_8))
                    }

                    val status = connection.responseCode
                    val responseStream = if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                    val responseText = responseStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()

                    if (status in 200..299) {
                        Log.d(TAG, "Worker accepted $transition event: $responseText")
                    } else {
                        Log.e(
                            TAG,
                            "Worker rejected event. HTTP $status: $responseText"
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send remote geofence notification", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun showNotification(
        context: Context,
        groupId: String,
        title: String,
        message: String
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission not granted; local alert skipped")
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safe-zone alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CircleGuard safe-zone entry and exit alerts"
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)

        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                groupId.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .apply {
                if (contentIntent != null) setContentIntent(contentIntent)
            }
            .build()

        NotificationManagerCompat.from(context).notify(
            notificationId(groupId, title),
            notification
        )
    }

    private fun notificationId(groupId: String, title: String): Int {
        val transitionId = if (title.contains("Entered")) 1 else 2
        return 31 * groupId.hashCode() + transitionId
    }

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"

        private const val TAG = "CircleGuardFCM"
        private const val CHANNEL_ID = "circleguard_safe_zone_alerts"
        private const val WORKER_URL =
            "https://circleguard-notifications.ayushsrv24.workers.dev/"
    }
}
