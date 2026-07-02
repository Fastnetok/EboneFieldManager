package com.fastnet.ebonefieldmanager

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.*

class LocationHelper(

    private val context: Context

) {

    private val fusedLocationProviderClient =

        LocationServices
            .getFusedLocationProviderClient(
                context
            )

    private var locationCallback:
            LocationCallback? = null

    @SuppressLint("MissingPermission")

    fun startLocationUpdates(

        onLocationReceived:
            (Location) -> Unit

    ) {

        val locationRequest =

            LocationRequest.Builder(

                Priority.PRIORITY_HIGH_ACCURACY,

                5000

            )

                .setMinUpdateIntervalMillis(
                    3000
                )

                .setMinUpdateDistanceMeters(
                    5f
                )

                .build()

        locationCallback =

            object : LocationCallback() {

                override fun onLocationResult(
                    result: LocationResult
                ) {

                    super.onLocationResult(
                        result
                    )

                    result.lastLocation?.let {

                        onLocationReceived(it)

                    }

                }

            }

        fusedLocationProviderClient
            .requestLocationUpdates(

                locationRequest,

                locationCallback!!,

                null

            )

    }

    fun stopLocationUpdates() {

        locationCallback?.let {

            fusedLocationProviderClient
                .removeLocationUpdates(it)

        }

    }

}