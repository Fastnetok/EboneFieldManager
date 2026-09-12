package com.fastnet.ebonefieldmanager

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class NotificationListener(

    private val context: Context

) {

    fun startListening() {

        val employeeName =
            RegistrationManager
                .getEmployeeName(context)

        android.util.Log.d(
            "EMPLOYEE_NAME",
            employeeName
        )

        if (employeeName.isEmpty()) {
            return
        }

        // FIX: old notifications used to replay every time this service
        // restarted (app update, phone reboot, process killed & revived)
        // because onChildAdded fires once for EVERY existing child the
        // first time this listener attaches — not just genuinely new
        // ones — and there was no check here for whether an entry was
        // actually old. Remembering the exact moment this listener
        // starts, and only showing entries timestamped at or after that
        // moment, means anything already sitting in the database from
        // before this app session is skipped silently instead of being
        // re-shown as if it just happened.
        val listenerStartTime = System.currentTimeMillis()

        FirebaseDatabase
            .getInstance()
            .getReference("employeeNotifications")
            .child(employeeName)
            .addChildEventListener(

                object : ChildEventListener {

                    override fun onChildAdded(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {

                        val timestamp =
                            snapshot.child("timestamp")
                                .getValue(Long::class.java)
                                ?: 0L

                        if (timestamp < listenerStartTime) {
                            // Old notification from before this app
                            // session started — do not show it again.
                            return
                        }

                        Log.d(
                            "NOTIFICATION_TEST",
                            "Notification Received"
                        )

                        val title =
                            snapshot.child("title")
                                .getValue(String::class.java)
                                ?: "New Complaint"

                        val message =
                            snapshot.child("message")
                                .getValue(String::class.java)
                                ?: ""

                        val notification =

                            NotificationCompat.Builder(
                                context,
                                "tracking_channel"
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

                                .setPriority(
                                    NotificationCompat.PRIORITY_HIGH
                                )

                                .setDefaults(
                                    NotificationCompat.DEFAULT_ALL
                                )

                                .setAutoCancel(
                                    true
                                )

                                .build()

                        val manager =

                            context.getSystemService(
                                Context.NOTIFICATION_SERVICE
                            ) as NotificationManager

                        manager.notify(
                            System.currentTimeMillis().toInt(),
                            notification
                        )

                    }

                    override fun onChildChanged(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {
                    }

                    override fun onChildRemoved(
                        snapshot: DataSnapshot
                    ) {
                    }

                    override fun onChildMoved(
                        snapshot: DataSnapshot,
                        previousChildName: String?
                    ) {
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }

                }

            )

    }

}