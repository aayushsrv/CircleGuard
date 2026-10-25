
package com.ayush.circleguard

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    private const val TAG = "CircleGuardFCM"

    /**
     * Retrieve the current FCM token and save it for the signed-in user.
     */
    fun registerDeviceToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                saveDeviceToken(token)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to retrieve FCM token", error)
            }
    }

    /**
     * Save a newly issued token, or the current token, to Firestore.
     */
    fun saveDeviceToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            Log.w(TAG, "Cannot save FCM token: no signed-in user.")
            return
        }

        val data = mapOf(
            "token" to token,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("devices")
            .document(token)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Device token saved successfully.")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save device token", error)
            }
    }
}
