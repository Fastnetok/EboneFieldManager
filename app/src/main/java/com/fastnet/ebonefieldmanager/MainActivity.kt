package com.fastnet.ebonefieldmanager

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.view.Gravity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var customerNameText: TextView
    private lateinit var tvLogCompany: TextView
    private lateinit var customerAddressText: TextView
    private lateinit var customerPhoneText: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var resolvedCountText: TextView
    private lateinit var liveLocationText: TextView
    private var currentComplaint: Complaint? = null

    // NEW CONNECTION GIFT BOX — now shows Pakistani 100 Rupee notes
    // based on the count at officeSettings/new_connections/gift_box
    private var giftBoxContainer: FrameLayout? = null

    private var lastSeenComplaintId: String? = null

    private var isAppInForeground = false

    // NEW: TrackingService must never be started before location permission is
    // actually confirmed granted (previously it was started immediately after
    // just REQUESTING permission, without waiting for the result — causing a
    // crash on Android 14 when the request hadn't been answered yet).
    private var trackingServiceStarted = false
    private var hasProceeded = false

    // Only refreshes the dashboard counters at midnight.
    // It never deletes any Firebase data.
    private val counterMidnightHandler =
        Handler(Looper.getMainLooper())

    private val counterMidnightRunnable =
        object : Runnable {
            override fun run() {
                if (hasProceeded) {
                    refreshDashboard()
                }
                scheduleCounterMidnightRefresh()
            }
        }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX (root cause of the 2-3 second dashboard flash): the dashboard
        // layout used to be set here immediately (setContentView), so it
        // was already on screen before the Firebase check-in check ever
        // came back. Now we show a blank placeholder instead, and only
        // call setContentView(R.layout.activity_main) once we actually
        // know the dashboard is allowed to be shown (see
        // proceedToBuildDashboard()). Nothing about permission gating or
        // startup order below is otherwise changed.
        setContentView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#F4F6FA"))
        })

        // FIX (requested): the GitHub update check must appear ABOVE even
        // the forced biometric screen — checked here, first thing, before
        // the attendance gate or dashboard build even start.
        //
        // ROOT CAUSE OF THE POPUP VANISHING (finally fixed here): earlier
        // versions called checkForUpdate(this) with NO callback, so this
        // Activity had no way to know a dialog was about to appear — the
        // biometric-redirect used to fire (launching AttendanceActivity on
        // top of this one) with no regard for whether the update dialog was
        // still on screen. Because the dialog is attached to THIS Activity's
        // window, the moment AttendanceActivity was launched on top of it,
        // Android would pause/cover this window and the dialog would
        // disappear from view — even though it was never actually
        // dismissed. The employee would only see it again once
        // AttendanceActivity finished and this Activity resumed.
        //
        // Now: updateCheckPending stays true until GitHub responds, and if
        // a dialog ends up showing, updateDialogShowing stays true until
        // the employee dismisses it (Later / Update Now). The attendance
        // redirect (launching AttendanceActivity) is now gated on BOTH
        // being false — see maybeRedirectToAttendance() /
        // pendingAttendanceRedirect below — so the update popup always
        // gets to show and be dismissed FIRST, whether or not the employee
        // has completed biometric check-in yet.
        VersionChecker.checkForUpdate(this) { dialogShown ->
            updateCheckPending = false
            updateDialogShowing = dialogShown
            if (dialogShown) {
                VersionChecker.onDialogDismissed = {
                    updateDialogShowing = false
                    // If an attendance redirect (or the earlier finish())
                    // was waiting on this dialog, run it now that the
                    // dialog is gone.
                    runPendingAttendanceActionsIfReady()
                }
            } else {
                runPendingAttendanceActionsIfReady()
            }
        }

        // HARD GATE: app does not proceed at all until Location permission
        // is granted — no dashboard, no Firebase listeners, nothing else
        // loads until the user grants it.
        if (!hasLocationPermission()) {
            showBlockingPermissionDialog()
            return
        }

        proceedWithNormalStartup()
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    /**
     * Non-cancelable — no back button, no tap-outside-to-dismiss. The only
     * way past this screen is granting Location permission. If the user
     * taps "Deny" on the system dialog, this same screen reappears.
     */
    private fun showBlockingPermissionDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("Ebone Field Manager cannot work without Location access — it's needed to track your visits and show your live position on the map. Please grant Location permission to continue.")
            .setCancelable(false)
            .setPositiveButton("Grant Permission") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            .show()
    }

    private fun proceedWithNormalStartup() {
        hasProceeded = true

        // NOTE: VersionChecker.checkForUpdate(this) moved to the very top
        // of onCreate() — see the FIX note there — so it runs once, before
        // the attendance gate, instead of here.

        // FIX (dashboard-flash bug): checkForcedAttendanceRedirect() used to
        // run in parallel with (or after) building the whole dashboard UI,
        // so the dashboard would flash on screen for a moment before the
        // Attendance screen appeared on top. Now the dashboard build is
        // gated: we check attendance status FIRST, and only build the
        // dashboard once we know biometric isn't required right now (or
        // once it's already been completed today).
        gateDashboardOnAttendance { proceedToBuildDashboard() }
    }

    /**
     * Checks (once, before any dashboard UI is built) whether today's
     * check-in exists. If it does not, launches AttendanceActivity locked
     * (forcedMorningCheckIn) and does NOT call [onReady] — so the dashboard
     * is never built/shown at all until check-in is done. If check-in
     * already exists, calls [onReady] immediately so the dashboard shows
     * as normal, and never asks again for the rest of the day.
     */
    // FIX (double-launch flash bug): onCreate() -> proceedWithNormalStartup()
    // starts this check, and Android calls onResume() almost immediately
    // afterward — before the first (async) Firebase call has returned. Both
    // paths used to call gateDashboardOnAttendance() with no guard, so BOTH
    // could independently decide "not checked in" and each call
    // startActivity(AttendanceActivity), stacking two copies on top of each
    // other (the quick double-flash the user saw). These two flags make
    // sure the check only truly runs once, and the redirect only truly
    // fires once, no matter how many times this gets called.
    private var attendanceGateInProgress = false
    private var hasRedirectedToAttendance = false

    // FIX (update-popup-hidden-behind-attendance-screen bug): the update
    // dialog is attached to THIS Activity's window. Launching
    // AttendanceActivity on top of MainActivity — for ANY reason, whether
    // check-in is already done for today or not — pauses/covers this
    // window, which makes an in-flight or currently-showing update dialog
    // disappear from view before the employee has dismissed it. These
    // three fields + runPendingAttendanceActionsIfReady() /
    // maybeRedirectToAttendance() are the single gate that stops the
    // AttendanceActivity redirect from firing until the update check has
    // fully finished AND (if it showed a dialog) that dialog has actually
    // been dismissed by the employee — regardless of whether the employee
    // has completed biometric check-in or not.
    private var updateCheckPending = true
    private var updateDialogShowing = false
    private var pendingAttendanceRedirect = false

    /**
     * Called once the update check is fully resolved (no dialog needed, or
     * the dialog was just dismissed). If an attendance redirect was queued
     * up while the update flow was still active, fire it now.
     */
    private fun runPendingAttendanceActionsIfReady() {
        if (updateCheckPending || updateDialogShowing) return
        if (pendingAttendanceRedirect) {
            pendingAttendanceRedirect = false
            launchForcedAttendanceScreen()
        }
    }

    private fun launchForcedAttendanceScreen() {
        hasRedirectedToAttendance = true
        val intent = Intent(this, AttendanceActivity::class.java)
        intent.putExtra("forcedMorningCheckIn", true)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        // FIX (app-closes-after-biometric bug): MainActivity must NOT
        // finish() itself here. AttendanceActivity (when launched with
        // forcedMorningCheckIn) auto-finishes on its own, ~900ms after a
        // real check-in session appears (see
        // AttendanceActivity.processAttendanceSnapshot()). If MainActivity
        // had already finished itself at this point, the back-stack would
        // be empty once AttendanceActivity closes, so Android would drop
        // the user out to the home screen instead of returning to the
        // dashboard — forcing them to reopen the app manually. Keeping
        // MainActivity alive (paused, in the background) means Android
        // naturally resumes it the moment AttendanceActivity finishes.
    }

    private fun gateDashboardOnAttendance(onReady: () -> Unit) {
        if (hasRedirectedToAttendance) return
        if (checkedInTodayConfirmed) {
            onReady()
            return
        }
        if (attendanceGateInProgress) return
        attendanceGateInProgress = true

        val todayKey = java.text.SimpleDateFormat(
            "yyyy-MM-dd", java.util.Locale.getDefault()
        ).format(java.util.Date())

        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        FirebaseDatabase.getInstance().getReference("attendance")
            .child(androidId).child(todayKey).get()
            .addOnSuccessListener { attSnap ->
                attendanceGateInProgress = false
                val sessSnap = attSnap.child("sessions")
                val hasCheckedInToday = if (sessSnap.exists()) {
                    sessSnap.children.any {
                        (it.child("checkInTime").value?.toString() ?: "").isNotEmpty()
                    }
                } else {
                    (attSnap.child("checkInTime").value?.toString() ?: "").isNotEmpty()
                }

                if (hasCheckedInToday) {
                    checkedInTodayConfirmed = true
                    onReady()
                } else if (!hasRedirectedToAttendance) {
                    // FIX (update-popup-hidden-behind-attendance-screen bug):
                    // do not launch AttendanceActivity while the update
                    // check is still running or its dialog is still on
                    // screen — queue the redirect instead, and
                    // runPendingAttendanceActionsIfReady() will fire it the
                    // moment the update flow is fully done. This guarantees
                    // the update popup is always shown (and can be
                    // dismissed) on top, whether or not the employee has
                    // completed biometric check-in yet.
                    if (updateCheckPending || updateDialogShowing) {
                        pendingAttendanceRedirect = true
                    } else {
                        launchForcedAttendanceScreen()
                    }
                }
            }
            .addOnFailureListener {
                attendanceGateInProgress = false
                // Fail-safe: if Firebase can't be reached, don't trap the
                // employee on a blank screen — let the dashboard load as
                // normal (matches original behavior when offline).
                onReady()
            }
    }

    // True once today's check-in has been confirmed — skips all further
    // Firebase checks for the rest of the day so the dashboard opens
    // instantly every subsequent time, as requested.
    private var checkedInTodayConfirmed = false

    private fun proceedToBuildDashboard() {
        // FIX: the real dashboard layout is now set HERE — only once we
        // know check-in is done and the dashboard is actually allowed to
        // appear — instead of unconditionally at the top of onCreate().
        setContentView(R.layout.activity_main)
        dashboardBuilt = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        if (!RegistrationManager.isRegistered(this)) {
            startActivity(Intent(this, RegistrationActivity::class.java))
            finish()
            return
        }

        val androidId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

// ← YEH NEECHE ADD KAREIN
// App version save for admin tracking
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
        if (appVersion.isNotEmpty()) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("ApprovedDevices")
                .child(androidId)
                .updateChildren(mapOf(
                    "appVersion" to appVersion,
                    "lastVersionUpdate" to System.currentTimeMillis()
                ))
        }

        com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .getReference("ApprovedDevices")
            .child(androidId)
            .child("employeeName")
            .get()
            .addOnSuccessListener { snapshot ->
                val employeeName = snapshot.value?.toString() ?: ""
                if (employeeName.isNotEmpty()) {
                    EmployeeSession.setEmployeeName(employeeName)
                    refreshDashboard()
                    FirebaseTokenManager.saveToken(this)
                    setupNewConnectionGiftBox(employeeName)
                }
            }

        // Location permission is already confirmed granted (gated at the
        // top of onCreate) — just check that GPS itself is switched on.
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }

        profileImage = findViewById(R.id.profileImage)
        loadSavedProfileImage()
        customerNameText = findViewById(R.id.customerNameText)
        tvLogCompany = findViewById(R.id.tvLogCompany)
        customerAddressText = findViewById(R.id.customerAddressText)
        customerPhoneText = findViewById(R.id.customerPhoneText)
        pendingCountText = findViewById(R.id.pendingCountText)
        resolvedCountText = findViewById(R.id.resolvedCountText)
        liveLocationText = findViewById(R.id.liveLocationText)

        // NEW CONNECTION GIFT BOX — hidden by default, Firebase listener
        // (setupNewConnectionGiftBox) controls its visibility. Totally
        // separate from Active Complaint / Resolved Log below.
        giftBoxContainer = findViewById(R.id.giftBoxContainer)
        giftBoxContainer?.visibility = View.GONE
        giftBoxContainer?.setOnClickListener {
            startActivity(Intent(this, NewConnectionActivity::class.java))
        }

        /*
         * PENDING BOX
         *
         * Opens the EXISTING ComplaintListActivity.
         * No new list or layout is created.
         */
        (pendingCountText.parent as? android.view.View)?.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ComplaintListActivity::class.java
                )
            )
        }

        pendingCountText.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ComplaintListActivity::class.java
                )
            )
        }

        /*
         * RESOLVED BOX
         *
         * Opens the EXISTING ResolvedLogActivity.
         */
        (resolvedCountText.parent as? android.view.View)?.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ResolvedLogActivity::class.java
                )
            )
        }

        resolvedCountText.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ResolvedLogActivity::class.java
                )
            )
        }

        val customerIssueText = findViewById<TextView>(R.id.customerIssueText)
        customerIssueText.setOnClickListener {
            startActivity(Intent(this, ComplaintListActivity::class.java))
        }

        val completeComplaintButton = findViewById<Button>(R.id.completeComplaintButton)
        val callButton = findViewById<Button>(R.id.callButton)
        val whatsappButton = findViewById<Button>(R.id.whatsappButton)
        val mapButton = findViewById<Button>(R.id.mapButton)

        refreshDashboard()

        // FIXED: was unconditional `startService(...)` + `startLocationUpdates`
        // right here, regardless of whether permission was actually granted.
        startTrackingIfPermitted()

        findViewById<android.view.View>(R.id.biometricAttendanceButton)?.setOnClickListener {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }

        profileImage.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            startActivityForResult(intent, 100)
        }

        callButton.setOnClickListener {
            customerPhoneText.text.toString().let {
                if (it.isNotEmpty()) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                }
            }
        }

        whatsappButton.setOnClickListener {
            customerPhoneText.text.toString().let {
                if (it.isNotEmpty()) {
                    val number = it.replaceFirst("0", "92")
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number"))
                    )
                }
            }
        }

        mapButton.setOnClickListener {
            customerAddressText.text.toString().let {
                if (it.isNotEmpty()) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$it"))
                    )
                }
            }
        }

        completeComplaintButton.setOnClickListener {
            currentComplaint?.let { complaint ->

                val employeeName = EmployeeSession.getEmployeeName()

                val complaintRef = com.google.firebase.database
                    .FirebaseDatabase
                    .getInstance()
                    .getReference("complaints")
                    .child(complaint.complaintId)

                complaintRef.child("status").setValue("Resolved")
                complaintRef.child("resolvedBy").setValue(employeeName)
                complaintRef.child("resolvedTime").setValue(System.currentTimeMillis())

                refreshDashboard()

                val historyData = hashMapOf<String, Any>(
                    "complaintId" to complaint.complaintId,
                    "userId" to complaint.userId,
                    "address" to complaint.address,
                    "phoneNumber" to complaint.phoneNumber,
                    "assignedTo" to complaint.assignedTo,
                    "assignedTime" to complaint.assignedTime,
                    "resolvedBy" to employeeName,
                    "resolvedTime" to System.currentTimeMillis()
                )

                com.google.firebase.database
                    .FirebaseDatabase
                    .getInstance()
                    .getReference("resolvedComplaints")
                    .child(complaint.complaintId)
                    .setValue(historyData)

                ComplaintManager.addResolvedLog(complaint)
                ComplaintManager.saveResolvedLogs(this@MainActivity)

                // Admin ko notification bhejein
                val notifData = hashMapOf<String, Any>(
                    "message" to "✅ ${employeeName} ne complaint resolve kar di — User: ${complaint.userId}",
                    "complaintId" to complaint.complaintId,
                    "resolvedBy" to employeeName,
                    "userId" to complaint.userId,
                    "timestamp" to System.currentTimeMillis(),
                    "seen" to false
                )

                com.google.firebase.database
                    .FirebaseDatabase
                    .getInstance()
                    .getReference("adminNotifications")
                    .push()
                    .setValue(notifData)
            }
        }
    }

    /**
     * Only starts TrackingService + live location updates once
     * ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION is actually confirmed
     * granted — never optimistically. Safe to call multiple times
     * (e.g. from onCreate AND onRequestPermissionsResult); guarded by
     * trackingServiceStarted so the service isn't started twice.
     */
    private fun startTrackingIfPermitted() {
        if (trackingServiceStarted) return

        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return // still not granted — wait for onRequestPermissionsResult

        trackingServiceStarted = true

        val serviceIntent = Intent(this, TrackingService::class.java)
        startService(serviceIntent)

        val locationHelper = LocationHelper(this)
        locationHelper.startLocationUpdates {
            runOnUiThread {
                // FIX: startTrackingIfPermitted() can now fire before the
                // dashboard layout exists (it no longer waits on the
                // attendance gate — GPS tracking must keep working even
                // while the employee is locked on the Attendance screen).
                // liveLocationText is only initialized once
                // proceedToBuildDashboard() has actually run.
                if (dashboardBuilt) {
                    liveLocationText.text = "LAT: ${it.latitude}\nLNG: ${it.longitude}"
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission()) {
                proceedWithNormalStartup()
            } else {
                // Still denied — show the blocking screen again. There is
                // no way past this activity without granting Location.
                showBlockingPermissionDialog()
            }
            return
        }

        // Any other permission (e.g. POST_NOTIFICATIONS, or one requested
        // inside startTrackingIfPermitted()'s own flow) — just re-check.
        startTrackingIfPermitted()
    }

    // True once proceedToBuildDashboard() has actually run and the
    // dashboard's views (profileImage, customerNameText, etc.) exist.
    private var dashboardBuilt = false

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        if (hasProceeded) {
            // FIX (app-closes-after-biometric bug): MainActivity is no
            // longer finished when it redirects to AttendanceActivity (see
            // gateDashboardOnAttendance() above), so this same MainActivity
            // instance is the one that comes back to onResume() once
            // AttendanceActivity finishes itself after a successful
            // check-in. hasRedirectedToAttendance must be reset here so
            // gateDashboardOnAttendance() re-checks Firebase instead of
            // silently doing nothing (it used to short-circuit via the
            // `if (hasRedirectedToAttendance) return` guard, which — with
            // finish() removed above — would otherwise leave the employee
            // stuck on the blank placeholder screen forever after
            // completing the biometric).
            hasRedirectedToAttendance = false
            gateDashboardOnAttendance {
                if (dashboardBuilt) refreshDashboard() else proceedToBuildDashboard()
            }
        }
        // Catches the case where the user granted Location via Settings
        // while the app was paused/blocked on the gate screen.
        if (!hasProceeded && hasLocationPermission()) {
            proceedWithNormalStartup()
        }
        startTrackingIfPermitted()
        markCurrentComplaintSeen()
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    /**
     * Dashboard par jo bhi Top/Active Complaint is waqt currentComplaint mein set hai,
     * usay Firebase par seenByEmployee = true aur seenTime = Server Time ke sath mark karta hai.
     * Sirf tab chalta hai jab App Foreground mein ho aur is complaint ko pehle "seen" mark
     * na kiya gaya ho (taake baar baar Firebase write na ho).
     */
    private fun markCurrentComplaintSeen() {
        val complaint = currentComplaint ?: return
        if (!isAppInForeground) return
        if (complaint.complaintId == lastSeenComplaintId) return

        val complaintRef = com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .getReference("complaints")
            .child(complaint.complaintId)

        complaintRef.child("seenByEmployee").setValue(true)
        complaintRef.child("seenTime")
            .setValue(com.google.firebase.database.ServerValue.TIMESTAMP)

        lastSeenComplaintId = complaint.complaintId
    }

    /*
     * NEW CONNECTION GIFT BOX
     *
     * Totally separate from Dashboard/Active Complaint/Resolved.
     * Only toggles giftBoxIcon visibility based on whether this
     * employee currently has any pending New Connection items at
     * officeSettings/new_connections/gift_box/{EmployeeName}/
     */
    private fun setupNewConnectionGiftBox(employeeName: String) {
        FirebaseDatabase.getInstance()
            .getReference("officeSettings/new_connections/gift_box")
            .child(employeeName)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = if (snapshot.exists()) snapshot.childrenCount.toInt() else 0
                    if (count > 0) {
                        giftBoxContainer?.visibility = View.VISIBLE
                        updateNoteDisplay(giftBoxContainer, count)
                    } else {
                        giftBoxContainer?.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateNoteDisplay(container: FrameLayout?, count: Int) {
        container?.let {
            it.removeAllViews()
            if (count == 0) return

            val inflater = LayoutInflater.from(this)
            val density = resources.displayMetrics.density
            
            // 1. BACK of the wallet (Behind notes)
            val backWallet = View(this)
            backWallet.layoutParams = FrameLayout.LayoutParams((85 * density).toInt(), (50 * density).toInt()).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            backWallet.setBackgroundResource(R.drawable.bg_wallet_back)
            it.addView(backWallet)

            // 2. The notes (Stacked between back and front folds)
            val maxNotes = if (count > 5) 5 else count
            for (i in 0 until maxNotes) {
                val noteView = inflater.inflate(R.layout.item_rs_100_note, it, false)
                val params = FrameLayout.LayoutParams((65 * density).toInt(), (32 * density).toInt())
                
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                // Stagger them: move up and slightly sideways for each note
                params.bottomMargin = (20 * density).toInt() + (i * 6 * density).toInt()
                params.leftMargin = (i * 8 * density).toInt() - (15 * density).toInt()
                
                noteView.rotation = (-12 + (i * 6)).toFloat()
                noteView.layoutParams = params
                it.addView(noteView)
            }
            
            // 3. FRONT of the wallet (On top of notes)
            val frontWallet = View(this)
            frontWallet.layoutParams = FrameLayout.LayoutParams((85 * density).toInt(), (40 * density).toInt()).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            frontWallet.setBackgroundResource(R.drawable.bg_wallet_front)
            it.addView(frontWallet)
        }
    }

    private fun refreshDashboard() {
        val employeeName = EmployeeSession.getEmployeeName()

        /*
         * EXISTING PENDING / ACTIVE COMPLAINT LOGIC
         *
         * Pending complaints are NOT reset at midnight.
         * If they are still unresolved tomorrow morning, they remain
         * visible and continue to be counted.
         */
        FirebaseDatabase
            .getInstance()
            .getReference("complaints")
            .addValueEventListener(
                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {
                        val employeeComplaints =
                            mutableListOf<Complaint>()

                        var pendingCount = 0

                        for (child in snapshot.children) {

                            val complaint =
                                child.getValue(
                                    Complaint::class.java
                                )

                            if (
                                complaint != null &&
                                complaint.assignedTo.equals(
                                    employeeName,
                                    true
                                )
                            ) {

                                if (
                                    !complaint.status.equals(
                                        "Resolved",
                                        true
                                    )
                                ) {
                                    pendingCount++

                                    employeeComplaints.add(
                                        complaint
                                    )
                                }
                            }
                        }

                        employeeComplaints.sortBy {
                            it.displayOrder
                        }

                        pendingCountText.text =
                            pendingCount.toString()

                        /*
                         * The FIRST complaint remains the Dashboard's
                         * main/current complaint exactly as before.
                         *
                         * ComplaintListActivity is only an additional
                         * analysis/list view opened from the Pending Box.
                         */
                        if (
                            employeeComplaints.isNotEmpty()
                        ) {

                            val complaint =
                                employeeComplaints[0]

                            currentComplaint =
                                complaint

                            customerNameText.text =
                                complaint.userId

                            /*
                             * Company Badge — بالکل ProgressAdapter.kt
                             * (Admin Panel) والا exact logic۔ Firebase ke
                             * company field se sirf actual company show
                             * hogi. Badge صرف "ACTIVE COMPLAINT" card کے
                             * اندر customerNameText کے ساتھ ہے — dashboard
                             * کے top-right corner (جو medal/rating کے لیے
                             * محفوظ ہے) کو بالکل ٹچ نہیں کرتا۔
                             */
                            val company =
                                complaint.company
                                    .trim()
                                    .uppercase(java.util.Locale.getDefault())

                            if (company.isEmpty()) {

                                tvLogCompany.visibility = View.GONE

                            } else {

                                tvLogCompany.text = when (company) {
                                    "EBONE", "EBILL", "EBONE (EBILL.PK)" -> "EBONE"
                                    "WATEEN", "WATEEN.COM" -> "WATEEN"
                                    "ZONG", "TURBONET.ZONG.COM.PK" -> "ZONG"
                                    else -> company
                                }

                                tvLogCompany.visibility = View.VISIBLE
                            }

                            customerAddressText.text =
                                complaint.address

                            customerPhoneText.text =
                                complaint.phoneNumber

                            markCurrentComplaintSeen()

                        } else {

                            currentComplaint = null

                            customerNameText.text =
                                "No Complaint"

                            customerAddressText.text =
                                "-"

                            customerPhoneText.text =
                                "-"
                        }
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )

        /*
         * Resolved Box is deliberately separate from Pending.
         * It counts only complaints that are actually present in
         * resolvedComplaints and were resolved TODAY.
         */
        loadTodayResolvedCount()

        /*
         * Pending does NOT reset at midnight.
         * Resolved Box does recalculate at midnight.
         */
        scheduleCounterMidnightRefresh()
    }

    /**
     * Dashboard Resolved Box:
     *
     * 10:00 AM -> 12:00 AM = today's resolved count
     * 12:00 AM -> 0 for the new day
     *
     * Firebase history is never deleted.
     */
    private fun loadTodayResolvedCount() {

        val employeeName =
            EmployeeSession.getEmployeeName()

        val now =
            java.util.Calendar.getInstance()

        val today10AM =
            java.util.Calendar.getInstance().apply {
                set(
                    java.util.Calendar.HOUR_OF_DAY,
                    10
                )
                set(
                    java.util.Calendar.MINUTE,
                    0
                )
                set(
                    java.util.Calendar.SECOND,
                    0
                )
                set(
                    java.util.Calendar.MILLISECOND,
                    0
                )
            }

        val tomorrow12AM =
            java.util.Calendar.getInstance().apply {
                add(
                    java.util.Calendar.DAY_OF_YEAR,
                    1
                )
                set(
                    java.util.Calendar.HOUR_OF_DAY,
                    0
                )
                set(
                    java.util.Calendar.MINUTE,
                    0
                )
                set(
                    java.util.Calendar.SECOND,
                    0
                )
                set(
                    java.util.Calendar.MILLISECOND,
                    0
                )
            }

        val todayStart =
            today10AM.timeInMillis

        val tomorrowStart =
            tomorrow12AM.timeInMillis

        /*
         * Before 10 AM, there is no current office-day
         * resolved count.
         */
        if (now.timeInMillis < todayStart) {
            resolvedCountText.text = "0"
            return
        }

        FirebaseDatabase
            .getInstance()
            .getReference(
                "resolvedComplaints"
            )
            .addValueEventListener(
                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        var todayResolvedCount = 0

                        for (child in snapshot.children) {

                            val resolvedTime =
                                getResolvedTimeValue(
                                    child.child(
                                        "resolvedTime"
                                    )
                                )

                            if (
                                resolvedTime < todayStart ||
                                resolvedTime >= tomorrowStart
                            ) {
                                continue
                            }

                            val resolvedBy =
                                child.child(
                                    "resolvedBy"
                                ).getValue(
                                    String::class.java
                                ) ?: ""

                            val assignedTo =
                                child.child(
                                    "assignedTo"
                                ).getValue(
                                    String::class.java
                                ) ?: ""

                            /*
                             * New records: resolvedBy.
                             * Older records: assignedTo fallback
                             * only when resolvedBy is empty.
                             */
                            val belongsToEmployee =
                                if (
                                    resolvedBy.isNotBlank()
                                ) {
                                    resolvedBy.equals(
                                        employeeName,
                                        true
                                    )
                                } else {
                                    assignedTo.equals(
                                        employeeName,
                                        true
                                    )
                                }

                            if (belongsToEmployee) {
                                todayResolvedCount++
                            }
                        }

                        resolvedCountText.text =
                            todayResolvedCount.toString()
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )
    }

    private fun getResolvedTimeValue(
        snapshot: DataSnapshot
    ): Long {

        return when (
            val value = snapshot.value
        ) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String ->
                value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /**
     * Recalculate the Resolved Box exactly at the next midnight.
     * Pending is not changed or reset here.
     */
    private fun scheduleCounterMidnightRefresh() {

        val now =
            java.util.Calendar.getInstance()

        val nextMidnight =
            java.util.Calendar.getInstance().apply {
                add(
                    java.util.Calendar.DAY_OF_YEAR,
                    1
                )
                set(
                    java.util.Calendar.HOUR_OF_DAY,
                    0
                )
                set(
                    java.util.Calendar.MINUTE,
                    0
                )
                set(
                    java.util.Calendar.SECOND,
                    0
                )
                set(
                    java.util.Calendar.MILLISECOND,
                    0
                )
            }

        counterMidnightHandler
            .removeCallbacks(
                counterMidnightRunnable
            )

        counterMidnightHandler.postDelayed(
            counterMidnightRunnable,
            (
                    nextMidnight.timeInMillis -
                            now.timeInMillis
                    ).coerceAtLeast(1000L)
        )
    }


    private fun loadSavedProfileImage() {
        try {
            val file = File(filesDir, "profile.jpg")
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                profileImage.setImageBitmap(bitmap)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        counterMidnightHandler.removeCallbacks(
            counterMidnightRunnable
        )
        super.onDestroy()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            profileImage.setImageURI(imageUri)
            saveProfileImage(imageUri)
        }
    }

    private fun saveProfileImage(imageUri: Uri?) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri!!)
            val file = File(filesDir, "profile.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
        } catch (_: Exception) {}
    }
}