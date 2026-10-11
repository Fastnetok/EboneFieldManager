package com.fastnet.ebonefieldmanager

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.*

class LocationHelper(
    private val context: Context
) {

    private val fusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        onLocationReceived: (Location) -> Unit
    ) {
        // 1. Instantly load last known location so it shows LAT/LNG immediately without "GPS CONNECTING..."
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val bestLocation = when {
                gpsLocation != null && netLocation != null -> {
                    if (gpsLocation.time > netLocation.time) gpsLocation else netLocation
                }
                gpsLocation != null -> gpsLocation
                netLocation != null -> netLocation
                else -> null
            }

            if (bestLocation != null) {
                onLocationReceived(bestLocation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location)
            }
        }

        // 2. Original Normal High Accuracy Settings
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        )
            .setMinUpdateIntervalMillis(3000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let {
                    onLocationReceived(it)
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            null
        )
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationProviderClient.removeLocationUpdates(it)
        }
    }
}
