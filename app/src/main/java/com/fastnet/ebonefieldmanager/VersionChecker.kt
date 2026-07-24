package com.fastnet.ebonefieldmanager

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.concurrent.thread

object VersionChecker {

    private const val GITHUB_API =
        "https://api.github.com/repos/Fastnetok/EboneFieldManager/releases/latest"

    private val client = OkHttpClient()

    fun checkForUpdate(context: Context) {

        android.util.Log.d("TEST_UPDATE", "checkForUpdate Called")

        val currentVersionName = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: return
        } catch (e: Exception) {
            return
        }

        checkGitHubRelease(context, currentVersionName)
    }

    private fun checkGitHubRelease(
        context: Context,
        currentVersionName: String
    ) {

        android.util.Log.d("GitHubUpdate", "checkGitHubRelease Started")

        thread {

            try {

                val request = Request.Builder()
                    .url(GITHUB_API)
                    .build()

                val response = client.newCall(request).execute()

                android.util.Log.d("GitHubUpdate", "Response Code = ${response.code}")
                android.util.Log.d("GitHubUpdate", "Response Message = ${response.message}")

                if (!response.isSuccessful) return@thread

                val body = response.body?.string() ?: return@thread

                android.util.Log.d("GitHubUpdate", body)

                val json = JSONObject(body)

                val tagName = json.getString("tag_name")

                val releaseNotes = json.getString("body")

                val downloadUrl =
                    json.getJSONArray("assets")
                        .getJSONObject(0)
                        .getString("browser_download_url")

                android.util.Log.d("GitHubUpdate", "Tag = $tagName")
                android.util.Log.d("GitHubUpdate", "APK = $downloadUrl")
                android.util.Log.d("GitHubUpdate", "Notes = $releaseNotes")

                val latestVersionName = tagName.replace("v", "", ignoreCase = true)

                android.util.Log.d("GitHubUpdate", "Installed = $currentVersionName")
                android.util.Log.d("GitHubUpdate", "Latest = $latestVersionName")

                if (isNewerVersion(latestVersionName, currentVersionName)) {

                    (context as android.app.Activity).runOnUiThread {

                        showUpdateDialog(
                            context = context,
                            versionName = tagName,
                            notes = releaseNotes,
                            apkUrl = downloadUrl
                        )

                    }

                }

            } catch (e: Exception) {

                android.util.Log.e("GitHubUpdate", "Error", e)

            }

        }

    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
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