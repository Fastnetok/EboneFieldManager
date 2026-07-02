package com.fastnet.ebonefieldmanager

import android.content.Context
import android.provider.Settings
import com.google.firebase.database.FirebaseDatabase

object FirebaseManager {

    private val database =
        FirebaseDatabase.getInstance()

    fun saveLocation(
        context: Context,
        employeeName: String,
        latitude: Double,
        longitude: Double
    ) {

        val androidId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )

        val data = hashMapOf(

            "androidId" to androidId,

            "employeeName" to employeeName,

            "latitude" to latitude,

            "longitude" to longitude,

            "status" to "ONLINE",

            "lastUpdate" to
                    System.currentTimeMillis()

        )

        database
            .getReference("employees")
            .child(androidId)
            .setValue(data)

    }

    fun saveTimeIn(

        androidId: String,

        employeeName: String,

        geoFenceName: String

    ) {

        val data = hashMapOf(

            "employeeName" to employeeName,

            "geoFenceName" to geoFenceName,

            "status" to "IN",

            "timeIn" to
                    System.currentTimeMillis(),

            "timeOut" to 0

        )

        database
            .getReference("geofenceLogs")
            .child(androidId)
            .setValue(data)

    }

    fun saveTimeOut(

        androidId: String

    ) {

        database
            .getReference("geofenceLogs")
            .child(androidId)
            .child("status")
            .setValue("OUT")

        database
            .getReference("geofenceLogs")
            .child(androidId)
            .child("timeOut")
            .setValue(
                System.currentTimeMillis()
            )

    }

    fun registerEmployee(

        androidId: String,

        employeeName: String,

        mobileNumber: String

    ) {

        val data = hashMapOf(

            "androidId" to androidId,

            "employeeName" to employeeName,

            "mobileNumber" to mobileNumber,

            "status" to "Pending",

            "createdAt" to
                    System.currentTimeMillis()

        )

        database
            .getReference(
                "PendingDevices"
            )
            .child(
                androidId
            )
            .setValue(data)

    }
}