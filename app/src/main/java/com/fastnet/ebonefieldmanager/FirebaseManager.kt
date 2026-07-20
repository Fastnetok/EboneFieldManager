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

        val data = hashMapOf<String, Any>(

            "androidId" to androidId,

            "employeeName" to employeeName,

            "latitude" to latitude,

            "longitude" to longitude,

            "status" to "ONLINE",

            "lastUpdate" to
                    System.currentTimeMillis()

        )

        // FIX: was .setValue(data) — that REPLACES the entire
        // employees/{androidId} node on every single location update,
        // wiping out any field not listed here (e.g. "bikeAverage" set
        // from the Admin Panel's Fuel Settings screen). .updateChildren()
        // only writes/merges the given keys and leaves every other field
        // (bikeAverage, etc.) untouched.
        database
            .getReference("employees")
            .child(androidId)
            .updateChildren(data)

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

    // CHANGED: now takes the Firebase Auth uid so the security rules can
    // later verify that only this same signed-in device can turn its own
    // PendingDevices entry into writes on employees/, tracking/, etc.
    // The admin app must copy this "uid" field over when it approves the
    // device and moves the record into ApprovedDevices.
    fun registerEmployee(

        androidId: String,

        employeeName: String,

        mobileNumber: String,

        uid: String

    ) {

        val data = hashMapOf(

            "androidId" to androidId,

            "employeeName" to employeeName,

            "mobileNumber" to mobileNumber,

            "status" to "Pending",

            "uid" to uid,

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