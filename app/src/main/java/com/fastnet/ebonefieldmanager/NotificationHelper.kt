package com.fastnet.ebonefieldmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_ID =
        "fcm_channel"

    fun showNotification(

        context: Context,

        title: String,

        message: String

    ) {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "FCM Notifications",

                    NotificationManager.IMPORTANCE_HIGH

                )

            val manager =

                context.getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )

        }

        val notification =

            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )

                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )

                .setContentTitle(
                    title
                )

                .setContentText(
                    message
                )

                .setAutoCancel(
                    true
                )

                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )

                .build()

        val manager =

            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(

            System.currentTimeMillis()
                .toInt(),

            notification

        )

    }

}