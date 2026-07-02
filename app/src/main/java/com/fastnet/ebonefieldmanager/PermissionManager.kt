package com.fastnet.ebonefieldmanager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(

    private val context: Context

) {

    companion object {

        const val LOCATION_PERMISSION_CODE =
            1001

    }

    // CHECK LOCATION PERMISSION

    fun hasLocationPermission(): Boolean {

        return ContextCompat.checkSelfPermission(

            context,

            Manifest.permission.ACCESS_FINE_LOCATION

        ) == PackageManager.PERMISSION_GRANTED

    }

    // REQUEST LOCATION PERMISSION

    fun requestLocationPermission(

        activity: Activity

    ) {

        ActivityCompat.requestPermissions(

            activity,

            arrayOf(

                Manifest.permission.ACCESS_FINE_LOCATION,

                Manifest.permission.ACCESS_COARSE_LOCATION

            ),

            LOCATION_PERMISSION_CODE

        )

    }

}