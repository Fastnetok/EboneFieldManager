package com.fastnet.ebonefieldmanager

import android.content.Context
import android.location.Location
import android.provider.Settings
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles GPS location HISTORY logging (separate from live tracking).
 * Called alongside saveLiveLocation()/checkGeoFence() in TrackingService,
 * but writes to a different Firebase node ("tracking/...") so the
 * existing live-tracking + geofence behavior is completely unaffected.
 *
 * Applies 4 filters before saving a point:
 * 1) Accuracy filter   — ignore points with poor GPS accuracy
 * 2) Movement threshold — ignore tiny GPS drift (not real movement)
 * 3) Speed validation   — ignore impossible speed jumps (bad GPS reading)
 * 4) Hybrid interval    — save on whichever comes first: enough time
 *    passed OR enough distance moved (keeps route accurate for fast
 *    movement, while saving battery when employee is stationary)
 */
class LocationTrackingHistory(private val context: Context) {

    private var lastSavedLocation: Location? = null
    private var lastSavedTime: Long = 0L

    companion object {
        private const val MIN_ACCURACY_METERS = 20f
        private const val MIN_MOVEMENT_METERS = 10f
        private const val MAX_REALISTIC_SPEED_MPS = 55f // ~198 km/h safety cap
        private const val MIN_INTERVAL_MS = 30_000L      // 30 sec
        private const val MAX_INTERVAL_MS = 60_000L      // 60 sec
        private const val MIN_DISTANCE_FOR_EARLY_SAVE = 50f // meters
    }

    fun onLocationUpdate(location: Location) {

        // 1) Accuracy filter — bad GPS reading, ignore completely
        if (location.hasAccuracy() && location.accuracy > MIN_ACCURACY_METERS) {
            return
        }

        val now = System.currentTimeMillis()
        val previous = lastSavedLocation
        val previousTime = lastSavedTime

        if (previous != null) {
            val distance = previous.distanceTo(location)
            val timeSinceLastSaveMs = now - previousTime
            val timeSinceLastSaveSec = timeSinceLastSaveMs / 1000.0

            // 2) Movement threshold — ignore GPS drift while stationary
            if (distance < MIN_MOVEMENT_METERS && timeSinceLastSaveMs < MAX_INTERVAL_MS) {
                return
            }

            // 3) Speed validation — reject clearly impossible GPS jumps
            if (timeSinceLastSaveSec > 0) {
                val impliedSpeedMps = distance / timeSinceLastSaveSec
                if (impliedSpeedMps > MAX_REALISTIC_SPEED_MPS) {
                    return
                }
            }

            // 4) Hybrid interval — save on whichever happens first
            val enoughTimePassed = timeSinceLastSaveMs >= MIN_INTERVAL_MS
            val enoughDistancePassed = distance >= MIN_DISTANCE_FOR_EARLY_SAVE
            if (!enoughTimePassed && !enoughDistancePassed) {
                return
            }
        }

        saveHistoryPoint(location, now)
        lastSavedLocation = location
        lastSavedTime = now
    }

    private fun saveHistoryPoint(location: Location, timestamp: Long) {

        val employeeId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(timestamp))

        val point = hashMapOf<String, Any>(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "accuracy" to location.accuracy,
            "speed" to if (location.hasSpeed()) location.speed else 0f,
            "bearing" to if (location.hasBearing()) location.bearing else 0f,
            "timestamp" to timestamp
        )

        FirebaseDatabase.getInstance()
            .getReference("tracking")
            .child(employeeId)
            .child(dateKey)
            .child(timestamp.toString())
            .setValue(point)
    }
}