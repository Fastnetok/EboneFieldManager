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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var customerNameText: TextView
    private lateinit var customerAddressText: TextView
    private lateinit var customerPhoneText: TextView
    private lateinit var pendingCountText: TextView
    private lateinit var resolvedCountText: TextView
    private lateinit var liveLocationText: TextView
    private var currentComplaint: Complaint? = null

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
        setContentView(R.layout.activity_main)

        setContentView(R.layout.activity_main)

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
        VersionChecker.checkForUpdate(this)

        // NOTE: the Pre-Shift Window forced-attendance check used to run
        // only here (cold start). It has moved to onResume() — see the FIX
        // note there — so it also re-evaluates when the app is simply
        // resumed from the background instead of being freshly opened.

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
        customerAddressText = findViewById(R.id.customerAddressText)
        customerPhoneText = findViewById(R.id.customerPhoneText)
        pendingCountText = findViewById(R.id.pendingCountText)
        resolvedCountText = findViewById(R.id.resolvedCountText)
        liveLocationText = findViewById(R.id.liveLocationText)

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
                liveLocationText.text = "LAT: ${it.latitude}\nLNG: ${it.longitude}"
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

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        if (hasProceeded) {
            refreshDashboard()
            // FIX (root cause of the 9:18 test not triggering): this now
            // runs on every onResume() — cold start, resuming from the
            // background, or coming back from another screen — instead of
            // only once at cold start. That's what makes it catch the
            // moment the Pre-Shift Window actually opens even if the
            // employee already had the app open before that time and
            // simply resumed it, rather than force-closing and reopening.
            // The SharedPreferences date-flag inside the function still
            // guarantees this only actually forces the Attendance screen
            // ONCE per day — repeated onResume calls after that are cheap
            // no-ops.
            checkForcedAttendanceRedirect()
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

    // Only prevents two overlapping async checks from running at the same
    // time (e.g. rapid onResume calls) — it is NOT a permanent one-shot
    // block. The actual "only force once per day" rule is enforced by the
    // persisted SharedPreferences date-flag inside the function itself.
    private var forcedAttendanceCheckInProgress = false

    /**
     * FIX (requested): forces the Attendance screen to open first thing in
     * the morning — but ONLY once per day, and ONLY when both are true:
     *   1. The current time falls inside the Pre-Shift Window (the early
     *      grace period before office start, e.g. office starts at 10:00
     *      and Pre-Shift Window is 60 min -> window is 9:00–10:00).
     *   2. The employee has NOT checked in yet today.
     * Office start time and Pre-Shift Window minutes both come straight
     * from Firebase "officeSettings" — exactly what Admin sets in the
     * Office Timings screen — so this always follows whatever schedule
     * Admin configures, with no separate rule.
     *
     * Called from onResume() (see FIX note there) so it re-evaluates the
     * instant the window opens, even if the app was already open in the
     * background rather than being freshly launched at that moment.
     */
    private fun checkForcedAttendanceRedirect() {
        if (forcedAttendanceCheckInProgress) return

        val todayKey = java.text.SimpleDateFormat(
            "yyyy-MM-dd", java.util.Locale.getDefault()
        ).format(java.util.Date())

        forcedAttendanceCheckInProgress = true
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        FirebaseDatabase.getInstance().getReference("officeSettings").get()
            .addOnSuccessListener { officeSnap ->
                val startHour = (officeSnap.child("startHour").value as? Long)?.toInt() ?: 10
                val startMinute = (officeSnap.child("startMinute").value as? Long)?.toInt() ?: 0
                val preShiftMinutes = (officeSnap.child("preShiftMinutes").value as? Long)?.toInt() ?: 60

                val now = java.util.Calendar.getInstance()
                val nowTotal = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                val officeStartTotal = startHour * 60 + startMinute
                val windowOpenTotal = officeStartTotal - preShiftMinutes

                val inPreShiftWindow = nowTotal in windowOpenTotal until officeStartTotal
                if (!inPreShiftWindow) {
                    forcedAttendanceCheckInProgress = false
                    return@addOnSuccessListener
                }

                // FIX (root cause of "closed the app without checking in,
                // reopened, and the dashboard showed instead"): there is no
                // persisted "already shown today" flag anymore. The ONLY
                // thing that suppresses this forced redirect is an ACTUAL
                // completed check-in for today (checked live from Firebase
                // below). This means: as long as check-in has NOT happened,
                // opening/reopening/force-closing the app any number of
                // times during the Pre-Shift Window will keep forcing the
                // Attendance screen every time. The moment check-in
                // succeeds, hasCheckedInToday becomes true and this stops
                // firing for the rest of the day automatically — matching
                // "sirf ek baar jab tak check-in na ho jaye, uske baad
                // normal" exactly.
                FirebaseDatabase.getInstance().getReference("attendance")
                    .child(androidId).child(todayKey).get()
                    .addOnSuccessListener { attSnap ->
                        val sessSnap = attSnap.child("sessions")
                        val hasCheckedInToday = if (sessSnap.exists()) {
                            sessSnap.children.any {
                                (it.child("checkInTime").value?.toString() ?: "").isNotEmpty()
                            }
                        } else {
                            (attSnap.child("checkInTime").value?.toString() ?: "").isNotEmpty()
                        }
                        forcedAttendanceCheckInProgress = false
                        if (hasCheckedInToday) {
                            return@addOnSuccessListener
                        }

                        val intent = Intent(this, AttendanceActivity::class.java)
                        // FIX (requested): tells AttendanceActivity this is
                        // the forced morning launch, so it locks the back
                        // button / system back press until the employee
                        // actually completes Check-In (biometric or PIN),
                        // then auto-returns to this dashboard. A normal
                        // manual open of AttendanceActivity later in the day
                        // (via the dashboard's fingerprint button) does NOT
                        // set this extra, so it behaves exactly as before.
                        intent.putExtra("forcedMorningCheckIn", true)
                        startActivity(intent)
                    }
                    .addOnFailureListener { forcedAttendanceCheckInProgress = false }
            }
            .addOnFailureListener { forcedAttendanceCheckInProgress = false }
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