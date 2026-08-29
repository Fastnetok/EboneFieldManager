package com.fastnet.ebonefieldmanager

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

/**
 * NEW FILE — enables Firebase Realtime Database offline persistence.
 *
 * WHY THIS FILE EXISTS:
 * setPersistenceEnabled(true) must be called EXACTLY ONCE, and it must run
 * BEFORE any other part of the app touches FirebaseDatabase.getInstance()
 * (FirebaseManager, MainActivity, ComplaintListActivity, etc. all call it
 * in several places). The only place guaranteed to run before every single
 * Activity is a custom Application class's onCreate() — so this class is
 * registered in AndroidManifest.xml as the app's android:name.
 *
 * WHAT IT DOES:
 * 1. Turns on local disk caching for the whole Realtime Database, so any
 *    data the app has already read once (complaints list, addresses,
 *    phone numbers, employee info, etc.) stays available and readable with
 *    ZERO internet connection — the app will not crash or show a blank
 *    screen, it shows the last-known data from disk.
 * 2. Calls keepSynced(true) on the "complaints" node specifically. Without
 *    this, Firebase only caches what a screen has actively displayed; with
 *    keepSynced(true) the SDK proactively keeps the ENTIRE complaints list
 *    up to date in the background disk cache whenever there IS a
 *    connection, so it's ready the moment the employee opens the app with
 *    no signal (e.g. inside a customer's house).
 * 3. The moment ANY internet/data signal returns (even briefly), the
 *    Firebase SDK automatically re-syncs in both directions — no extra
 *    code is needed for that part, it is built into the SDK.
 *
 * NOTHING else changes: no UI, no layout, no existing logic anywhere else
 * is touched by this file.
 */
class EboneApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Safe to ignore — this only throws if persistence was already
            // enabled for this instance (e.g. hot-reload during development).
        }

        // Keep the complaints list continuously cached to disk so it's
        // available the instant the app opens, even with no signal at all.
        FirebaseDatabase.getInstance().getReference("complaints").keepSynced(true)
    }
}