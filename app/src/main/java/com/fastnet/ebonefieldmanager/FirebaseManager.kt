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

    /**
     * CHANGED (again): now writes straight to "ApprovedDevices" instead of
     * "PendingDevices" — the PIN itself (created by Admin via
     * AddEmployeeActivity) already IS the authorization, so a second manual
     * "Approve" step in the Admin Panel would just be redundant friction.
     * Requires the matching ApprovedDevices Firebase Rule to allow a
     * first-time self-write (see chat for the rules update).
     */
    fun claimEmployeePin(
        pin: String,
        enteredName: String,
        mobileNumber: String,
        androidId: String,
        uid: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val pinRef = database.getReference("employeePins").child(pin)

        pinRef.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult(false, "Invalid PIN — please check with Admin.")
                    return@addOnSuccessListener
                }

                val status = snapshot.child("status").getValue(String::class.java) ?: "PENDING"
                val linkedAndroidId = snapshot.child("linkedAndroidId").getValue(String::class.java)
                val employeeName = snapshot.child("employeeName").getValue(String::class.java) ?: enteredName

                if (status == "CLAIMED" && linkedAndroidId != null && linkedAndroidId != androidId) {
                    onResult(false, "This PIN has already been used on another device.")
                    return@addOnSuccessListener
                }

                val pinUpdate = mapOf(
                    "status" to "CLAIMED",
                    "linkedAndroidId" to androidId,
                    "linkedUid" to uid,
                    "mobileNumber" to mobileNumber
                )
                pinRef.updateChildren(pinUpdate)

                val approvedDeviceData = hashMapOf(
                    "androidId" to androidId,
                    "employeeName" to employeeName,
                    "mobileNumber" to mobileNumber,
                    "status" to "Approved",
                    "uid" to uid,
                    "createdAt" to System.currentTimeMillis()
                )

                database.getReference("ApprovedDevices")
                    .child(androidId)
                    .setValue(approvedDeviceData)
                    .addOnSuccessListener {
                        onResult(true, "Registered — Welcome!")
                    }
                    .addOnFailureListener { e ->
                        onResult(false, "Registration failed: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onResult(false, "Could not verify PIN: ${e.message}")
            }
    }
}