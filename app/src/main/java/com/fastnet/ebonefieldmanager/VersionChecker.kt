package com.fastnet.ebonefieldmanager

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object VersionChecker {

    // Call this once, e.g. in MainActivity.onCreate().
    fun checkForUpdate(context: Context) {
        val currentVersionCode = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                        it.longVersionCode.toInt()
                    else
                        @Suppress("DEPRECATION") it.versionCode
                }
        } catch (e: Exception) {
            return
        }

        FirebaseDatabase.getInstance()
            .getReference("appConfig")
            .child("employeeApp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val latestVersionCode = snapshot.child("versionCode")
                        .getValue(Int::class.java) ?: return
                    val latestVersionName = snapshot.child("versionName")
                        .getValue(String::class.java) ?: ""
                    val apkUrl = snapshot.child("apkUrl")
                        .getValue(String::class.java) ?: return
                    val notes = snapshot.child("notes")
                        .getValue(String::class.java) ?: ""

                    if (latestVersionCode > currentVersionCode) {
                        showUpdateDialog(context, latestVersionName, notes, apkUrl)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showUpdateDialog(
        context: Context,
        versionName: String,
        notes: String,
        apkUrl: String
    ) {
        val message = if (notes.isNotEmpty())
            "New version $versionName is available.\n\n$notes"
        else
            "New version $versionName is available."

        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                context.startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
}