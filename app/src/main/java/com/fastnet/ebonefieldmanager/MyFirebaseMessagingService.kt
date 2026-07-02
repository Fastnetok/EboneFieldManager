package com.fastnet.ebonefieldmanager

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onMessageReceived(
        message: RemoteMessage
    ) {

        Log.d(
            "FCM_RECEIVED",
            "Message Arrived"
        )

        super.onMessageReceived(
            message
        )

        val title =
            message.notification?.title
                ?: "New Complaint"

        val body =
            message.notification?.body
                ?: ""

        NotificationHelper.showNotification(

            this,

            title,

            body

        )

    }

    override fun onNewToken(
        token: String
    ) {

        Log.d(
            "FCM_TOKEN",
            token
        )

        super.onNewToken(
            token
        )

    }

}