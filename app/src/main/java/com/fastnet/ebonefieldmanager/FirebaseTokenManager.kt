package com.fastnet.ebonefieldmanager

import android.provider.Settings
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseTokenManager {

    fun saveToken(

        activity: MainActivity

    ) {

        FirebaseMessaging
            .getInstance()
            .token

            .addOnSuccessListener { token ->

                val androidId =

                    Settings.Secure.getString(

                        activity.contentResolver,

                        Settings.Secure.ANDROID_ID

                    )

                FirebaseDatabase
                    .getInstance()
                    .getReference(
                        "ApprovedDevices"
                    )
                    .child(androidId)
                    .child("fcmToken")
                    .setValue(token)

            }

    }

}