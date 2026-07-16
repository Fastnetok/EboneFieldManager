package com.fastnet.ebonefieldmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.*

class TrackingService : Service() {

    private lateinit var locationHelper:
            LocationHelper

    // NEW: handles GPS history logging (accuracy/movement/speed filters +
    // hybrid interval) — writes to a separate "tracking/" Firebase node.
    // Does not affect live tracking or geofence logic below.
    private lateinit var locationTrackingHistory: LocationTrackingHistory

    private var isInsideGeoFence =
        false
    private fun showGeoFenceNotification(
        title: String,
        message: String,
        vibrationTime: Long
    ) {

        val notification =

            NotificationCompat.Builder(
                this,
                "tracking_channel"
            )

                .setSmallIcon(
                    android.R.drawable.ic_dialog_map
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

                .setAutoCancel(
                    true
                )

                .build()



        val vibrator =

            getSystemService(
                VIBRATOR_SERVICE
            ) as Vibrator

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            vibrator.vibrate(

                VibrationEffect
                    .createOneShot(

                        vibrationTime,

                        VibrationEffect
                            .DEFAULT_AMPLITUDE
                    )
            )

        } else {

            vibrator.vibrate(
                vibrationTime
            )
        }
    }

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        val notification: Notification =

            NotificationCompat.Builder(
                this,
                "tracking_channel"
            )

                .setContentTitle(
                    "System Update"
                )

                .setContentText(
                    ""
                )

                .setSmallIcon(
                    android.R.drawable.ic_menu_mylocation
                )

                .build()

        startForeground(
            1,
            notification
        )

        locationHelper =
            LocationHelper(this)

        // NEW: initialize the history logger
        locationTrackingHistory =
            LocationTrackingHistory(this)

        startLiveTracking()

        NotificationListener(this)
            .startListening()

        showGeoFenceNotification(
            "TEST",
            "Notification Working",
            500
        )

    }

    private fun startLiveTracking() {

        locationHelper
            .startLocationUpdates {

                saveLiveLocation(it)

                checkGeoFence(it)

                // NEW: also feed the same location into history logging.
                // This function internally decides (via its own filters)
                // whether this particular point is worth saving.
                locationTrackingHistory.onLocationUpdate(it)
            }
    }

    private fun checkGeoFence(
        location: Location
    ) {

        FirebaseDatabase
            .getInstance()
            .getReference("geofences")
            .limitToLast(1)
            .addListenerForSingleValueEvent(

                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        for (geo in snapshot.children) {

                            val latitude =
                                geo.child("latitude")
                                    .getValue(Double::class.java)
                                    ?: return

                            val longitude =
                                geo.child("longitude")
                                    .getValue(Double::class.java)
                                    ?: return

                            val radius =
                                geo.child("radius")
                                    .getValue(Double::class.java)
                                    ?: return

                            val geoFenceName =
                                geo.child("name")
                                    .getValue(String::class.java)
                                    ?: "GeoFence"

                            val results =
                                FloatArray(1)

                            Location.distanceBetween(
                                location.latitude,
                                location.longitude,
                                latitude,
                                longitude,
                                results
                            )

                            val distance =
                                results[0]

                            val androidId =
                                Settings.Secure.getString(
                                    contentResolver,
                                    Settings.Secure.ANDROID_ID
                                )

                            val employeeName =
                                RegistrationManager
                                    .getEmployeeName(
                                        this@TrackingService
                                    )

                            if (
                                distance <= radius &&
                                !isInsideGeoFence
                            ) {

                                isInsideGeoFence =
                                    true

                                FirebaseManager
                                    .saveTimeIn(

                                        androidId,

                                        employeeName,

                                        geoFenceName

                                    )

                                Log.d(
                                    "GEOFENCE",
                                    "TIME IN"
                                )
                                showGeoFenceNotification(

                                    "📍 GeoFence Enter",

                                    "$employeeName entered $geoFenceName",

                                    300

                                )
                            }

                            else if (
                                distance > radius &&
                                isInsideGeoFence
                            ) {

                                isInsideGeoFence =
                                    false

                                FirebaseManager
                                    .saveTimeOut(
                                        androidId
                                    )

                                Log.d(
                                    "GEOFENCE",
                                    "TIME OUT"
                                )
                                showGeoFenceNotification(

                                    "🚪 GeoFence Exit",

                                    "$employeeName left $geoFenceName",

                                    800

                                )
                            }
                        }
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )
    }

    private fun saveLiveLocation(
        location: Location
    ) {

        val latitude =
            location.latitude

        val longitude =
            location.longitude

        val employeeName =

            RegistrationManager
                .getEmployeeName(this)

        FirebaseManager
            .saveLocation(

                this,

                employeeName,

                latitude,

                longitude

            )

        Log.d(
            "LIVE_LOCATION",
            "LAT: $latitude LNG: $longitude"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }

    override fun onDestroy() {

        super.onDestroy()

        locationHelper
            .stopLocationUpdates()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    "tracking_channel",

                    "System Update",

                    NotificationManager
                        .IMPORTANCE_HIGH
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }
}