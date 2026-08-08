package com.fastnet.ebonefieldmanager

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance()
    private var employeeName = ""
    private var deviceId = ""
    private var todayKey = ""

    private var officeStartHour = 10
    private var officeStartMinute = 0
    private var officeEndHour = 22
    private var officeEndMinute = 0
    private var gracePeriodMinutes = 15

    private lateinit var tvCheckInTime: TextView
    private lateinit var tvCheckOutTime: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var btnAction: Button
    private lateinit var btnEarlyLeave: Button
    private lateinit var tvOfficeHours: TextView
    private lateinit var tvPresentVal: TextView
    private lateinit var tvAbsentVal: TextView
    private lateinit var tvLateVal: TextView
    private lateinit var tvScoreVal: TextView

    private var isCheckedIn = false
    private var isCheckedOut = false
    private var earlyLeaveRequestKey = ""
    private var earlyLeaveApproved = false
    private var earlyLeaveListener: com.google.firebase.database.ValueEventListener? = null
    private var complaintAddress = ""
    private var complaintUser = ""
    private var preShiftMinutes = 60
    private var complaintRadiusMeters = 500.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        employeeName = EmployeeSession.getEmployeeName()
        // Also try loading from Firebase if name is empty
        if (employeeName.isEmpty()) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("employees").child(deviceId).child("employeeName")
                .get().addOnSuccessListener { snap ->
                    val fbName = snap.value?.toString() ?: ""
                    if (fbName.isNotEmpty()) employeeName = fbName
                }
        }
        complaintAddress = intent.getStringExtra("complaintAddress") ?: ""
        complaintUser = intent.getStringExtra("complaintUser") ?: ""
        todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        setContentView(buildLayout())
        loadOfficeSettings()
        loadTodayAttendance()
        loadMonthlyStats()
    }

    // ───────────────── LAYOUT ─────────────────

    private fun buildLayout(): View {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6FA"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D2E5C"))
            setPadding(px(16, dp), px(48, dp), px(16, dp), px(16, dp))
        }
        val backBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            background = null
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(px(28, dp), px(28, dp))
            setOnClickListener { finish() }
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = px(12, dp) }
        }
        val dateStr = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date())
        titleBlock.addView(tv("Attendance", 17f, Color.WHITE, bold = true))
        titleBlock.addView(tv(dateStr, 13f, Color.parseColor("#B8C6DE")))
        tvStatusBadge = tv("Not Marked", 12f, Color.parseColor("#9E9E9E"), bold = true).also {
            it.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * dp
                setColor(Color.parseColor("#F5F5F5"))
            }
            it.setPadding(px(12, dp), px(4, dp), px(12, dp), px(4, dp))
        }
        header.addView(backBtn)
        header.addView(titleBlock)
        header.addView(tvStatusBadge)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12, dp), px(12, dp), px(12, dp), px(12, dp))
        }
        scroll.addView(content)

        // Times
        val timesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10, dp) }
        }
        val checkInCard = timeCard("Check-In", Color.parseColor("#E8F5E9"), Color.parseColor("#1B5E20"), dp)
        val checkOutCard = timeCard("Check-Out", Color.parseColor("#F4F6FA"), Color.parseColor("#9E9E9E"), dp)
        tvCheckInTime = checkInCard.findViewWithTag("time")
        tvCheckOutTime = checkOutCard.findViewWithTag("time")
        timesRow.addView(checkInCard.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = px(6, dp) } })
        timesRow.addView(checkOutCard.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(timesRow)

        // Biometric card
        val biometricCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 12f, dp)
            setPadding(px(16, dp), px(20, dp), px(16, dp), px(16, dp))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10, dp) }
        }

        // Fingerprint rings + icon
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(120, dp), px(120, dp)).also { it.gravity = Gravity.CENTER; it.bottomMargin = px(14, dp) }
        }
        frame.addView(ring(px(120, dp), Color.parseColor("#BBDEFB"), 1f, dp))
        frame.addView(ring(px(106, dp), Color.parseColor("#90CAF9"), 1.5f, dp))
        val circle = LinearLayout(this).apply {
            val s = px(88, dp)
            layoutParams = FrameLayout.LayoutParams(s, s).also { it.gravity = Gravity.CENTER }
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E3F2FD"))
                setStroke(px(2, dp), Color.parseColor("#1565C0"))
            }
        }
        circle.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_fingerprint)
            layoutParams = LinearLayout.LayoutParams(px(46, dp), px(46, dp))
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        frame.addView(circle)

        val iconRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(14, dp) }
        }
        iconRow.addView(frame)
        biometricCard.addView(iconRow)

        tvInstruction = tv("Tap to mark your Check-In\nFingerprint or Face ID required", 13f, Color.parseColor("#757575")).also {
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(16, dp) }
        }
        biometricCard.addView(tvInstruction)

        btnAction = Button(this).apply {
            text = "Mark Check-In"
            textSize = 15f
            setTextColor(Color.WHITE)
            // ── BUTTON COLOR: Change "#2E7D32" to any color you want ──
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * dp
                setColor(Color.parseColor("#2E7D32"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(8, dp) }
            setOnClickListener { handleAttendanceClick() }
        }
        biometricCard.addView(btnAction)

        btnEarlyLeave = Button(this).apply {
            text = "Request Early Leave"
            textSize = 13f
            setTextColor(Color.parseColor("#E65100"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * dp
                setColor(Color.WHITE)
                setStroke(px(1, dp), Color.parseColor("#E65100"))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
            setOnClickListener { showEarlyLeaveDialog() }
        }
        biometricCard.addView(btnEarlyLeave)
        content.addView(biometricCard)

        // Monthly stats
        content.addView(tv("THIS MONTH", 13f, Color.parseColor("#9E9E9E"), bold = true).also {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(6, dp) }
        })

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10, dp) }
        }
        tvPresentVal = tv("—", 23f, Color.parseColor("#1B5E20"), bold = true).also { it.gravity = Gravity.CENTER }
        tvAbsentVal = tv("—", 23f, Color.parseColor("#B71C1C"), bold = true).also { it.gravity = Gravity.CENTER }
        tvLateVal = tv("—", 23f, Color.parseColor("#BF360C"), bold = true).also { it.gravity = Gravity.CENTER }
        tvScoreVal = tv("—", 23f, Color.parseColor("#0D47A1"), bold = true).also { it.gravity = Gravity.CENTER }

        statsRow.addView(statBox(tvPresentVal, "Present", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4, dp) } })
        statsRow.addView(statBox(tvAbsentVal, "Absent", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4, dp) } })
        statsRow.addView(statBox(tvLateVal, "Late", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4, dp) } })
        statsRow.addView(statBox(tvScoreVal, "Score", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(statsRow)

        // Office hours footer
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundRect(Color.WHITE, 8f, dp)
            setPadding(px(12, dp), px(10, dp), px(12, dp), px(10, dp))
        }
        tvOfficeHours = tv("Office Hours: Loading...", 13f, Color.parseColor("#444444")).also { it.gravity = Gravity.CENTER }
        footer.addView(tvOfficeHours)
        content.addView(footer)

        root.addView(scroll)
        return root
    }

    // ───────────────── HELPERS ─────────────────

    private fun px(v: Int, dp: Float) = (v * dp).toInt()

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun roundRect(color: Int, radius: Float, dp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius * dp
        setColor(color)
    }

    private fun timeCard(label: String, bg: Int, textColor: Int, dp: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(14, dp), px(12, dp), px(14, dp), px(12, dp))
        background = roundRect(bg, 10f, dp)
        addView(tv(label, 13f, Color.parseColor("#333333")))
        addView(tv("— —", 18f, textColor, bold = true).also {
            it.tag = "time"
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.topMargin = px(4, dp) }
        })
    }

    private fun ring(size: Int, color: Int, stroke: Float, dp: Float) = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(size, size).also { it.gravity = Gravity.CENTER }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke((stroke * dp).toInt(), color)
            alpha = 150
        }
    }

    private fun statBox(valView: TextView, label: String, dp: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(px(8, dp), px(14, dp), px(8, dp), px(14, dp))
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 8f*dp; setColor(Color.WHITE); setStroke(px(1,dp), Color.parseColor("#DDDDDD")) }
        isClickable = true
        isFocusable = true
        setOnClickListener { showStatDetail(label) }
        addView(valView)
        addView(tv(label, 12f, Color.parseColor("#333333")).also {
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.topMargin = px(2, dp) }
        })
    }

    private fun showStatDetail(type: String) {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        db.getReference("attendance").child(deviceId)
            .orderByKey()
            .startAt("${monthKey}-01")
            .endAt("${monthKey}-31")
            .get()
            .addOnSuccessListener { snap ->
                val dp = resources.displayMetrics.density
                val scroll = android.widget.ScrollView(this)
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(16, dp), px(8, dp), px(16, dp), px(8, dp))
                }
                scroll.addView(container)

                var count = 0
                val presentDates = snap.children.associate { it.key to it }
                val cal2 = Calendar.getInstance()
                val todayDay2 = cal2.get(Calendar.DAY_OF_MONTH)
                val monthKey2 = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                // For Absent: generate missing days list
                val iterList: List<String> = if (type == "Absent") {
                    (1 until todayDay2).map { d ->
                        "${monthKey2}-${String.format(Locale.getDefault(), "%02d", d)}"
                    }.filter { it !in presentDates }
                } else {
                    snap.children.map { it.key ?: "" }.filter { it.isNotEmpty() }.sorted()
                }

                iterList.forEach { date ->
                    val day = presentDates[date]
                    val checkIn = day?.child("checkInTime")?.value?.toString() ?: ""
                    val checkOut = day?.child("checkOutTime")?.value?.toString() ?: ""
                    val status = day?.child("status")?.value?.toString() ?: ""

                    val show = when (type) {
                        "Present" -> checkIn.isNotEmpty()
                        "Late" -> status == "LATE"
                        "Score" -> true
                        else -> false // Absent handled separately below
                    }
                    if (type != "Absent" && !show) return@forEach
                    if (type == "Absent" && checkIn.isNotEmpty()) return@forEach
                    count++

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, px(10, dp), 0, px(10, dp))
                        background = if (count % 2 == 0)
                            android.graphics.drawable.ColorDrawable(Color.parseColor("#F8F8F8"))
                        else
                            android.graphics.drawable.ColorDrawable(Color.WHITE)
                    }

                    // Format date nicely
                    val dateFmt = try {
                        val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                        SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(d!!)
                    } catch (e: Exception) { date }

                    val dateView = tv(dateFmt, 13f, Color.parseColor("#333333"), bold = true).also {
                        it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val detail = when (type) {
                        "Present" -> if (checkOut.isNotEmpty()) "$checkIn → $checkOut" else "$checkIn → —"
                        "Absent" -> "Absent"
                        "Late" -> "In: $checkIn"
                        "Score" -> {
                            val badge = when (status) {
                                "ON_TIME" -> "On Time"
                                "LATE" -> "Late"
                                "OVERTIME" -> "Overtime"
                                else -> if (checkIn.isEmpty()) "Absent" else "Present"
                            }
                            badge
                        }
                        else -> ""
                    }

                    val statusColor = when {
                        type == "Absent" -> Color.parseColor("#C62828")
                        status == "LATE" -> Color.parseColor("#E65100")
                        status == "OVERTIME" -> Color.parseColor("#1565C0")
                        else -> Color.parseColor("#2E7D32")
                    }
                    val detailView = tv(detail, 12f, statusColor)

                    row.addView(dateView)
                    row.addView(detailView)
                    container.addView(row)

                    // Divider
                    container.addView(View(this).apply {
                        setBackgroundColor(Color.parseColor("#EEEEEE"))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    })
                }

                if (count == 0) {
                    container.addView(tv("No records found for this month.", 13f, Color.parseColor("#9E9E9E")).also {
                        it.gravity = Gravity.CENTER
                        it.setPadding(0, px(20, dp), 0, px(20, dp))
                    })
                }

                val title = when (type) {
                    "Present" -> "Present Days — $count"
                    "Absent" -> "Absent Days — $count"
                    "Late" -> "Late Arrivals — $count"
                    "Score" -> "Monthly Attendance Log"
                    else -> type
                }

                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show()
            }
            .addOnFailureListener { showInfo("Failed to load data.") }
    }

    private fun listenForLeaveApproval(requestKey: String) {
        if (requestKey.isEmpty()) return
        val ref = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("earlyLeaveRequests")
            .child(requestKey)
        earlyLeaveListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snap: com.google.firebase.database.DataSnapshot) {
                val status = snap.child("status").value?.toString() ?: return
                when (status) {
                    "APPROVED" -> {
                        earlyLeaveApproved = true
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this@AttendanceActivity)
                                .setTitle("Request Approved")
                                .setMessage("Your early leave request has been approved. You can now check out.")
                                .setPositiveButton("OK") { _, _ ->
                                    loadTodayAttendance()
                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                    "REJECTED" -> {
                        earlyLeaveApproved = false
                        earlyLeaveRequestKey = ""
                        runOnUiThread {
                            androidx.appcompat.app.AlertDialog.Builder(this@AttendanceActivity)
                                .setTitle("Request Rejected")
                                .setMessage("Your early leave request was rejected. Please contact your admin.")
                                .setPositiveButton("OK", null)
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addValueEventListener(earlyLeaveListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (earlyLeaveRequestKey.isNotEmpty() && earlyLeaveListener != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("earlyLeaveRequests")
                .child(deviceId)
                .child(earlyLeaveRequestKey)
                .removeEventListener(earlyLeaveListener!!)
        }
    }

    private fun showInfo(msg: String) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    // ───────────────── ATTENDANCE LOGIC ─────────────────

    private fun handleAttendanceClick() {
        // Step 1: GPS must be ON
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsOn = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isGpsOn) {
            AlertDialog.Builder(this)
                .setTitle("GPS Required")
                .setMessage("GPS must be ON to mark attendance. Please enable location and try again.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // Step 2: Time window check
        val now = Calendar.getInstance()
        when {
            !isCheckedIn -> {
                val windowOpen = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, officeStartHour)
                    set(Calendar.MINUTE, (officeStartMinute - preShiftMinutes).coerceAtLeast(0))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (now.before(windowOpen)) {
                    val h = if (officeStartHour > 12) officeStartHour - 12 else officeStartHour
                    val m = (officeStartMinute - preShiftMinutes).coerceAtLeast(0)
                    val startAmPm = if (officeStartHour >= 12) "PM" else "AM"
                    showInfo("Check-in opens at $h:${String.format(Locale.getDefault(), "%02d", m)} $startAmPm")
                    return
                }
                // Step 3: Verify location
                verifyLocationThenBiometric(isCheckIn = true)
            }
            !isCheckedOut -> {
                val windowOpen = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, officeEndHour)
                    set(Calendar.MINUTE, (officeEndMinute - 30).coerceAtLeast(0))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (now.before(windowOpen)) {
                    val h = if (officeEndHour > 12) officeEndHour - 12 else officeEndHour
                    val endAmPm = if (officeEndHour >= 12) "PM" else "AM"
                    showInfo("Check-out opens at $h:30 $endAmPm. For early departure use Request Early Leave button.")
                    return
                }
                verifyLocationThenBiometric(isCheckIn = false)
            }
            else -> showInfo("Today attendance is already complete!")
        }
    }

    private fun verifyLocationThenBiometric(isCheckIn: Boolean) {
        tvInstruction.text = "Verifying your location..."

        // Check location permission
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            showInfo("Location permission is required. Please grant it in app settings.")
            tvInstruction.text = "Tap to mark your attendance"
            return
        }

        // Get current location
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        var currentLoc: android.location.Location? = null

        try {
            currentLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (ex: SecurityException) {
            showInfo("Location permission denied.")
            return
        }

        if (currentLoc == null) {
            // No location yet — allow anyway if complaint exists, or wait
            checkComplaintThenProceed(isCheckIn, null)
            return
        }

        // Check against office geofence
        db.getReference("attendanceGeofence").get().addOnSuccessListener { snap ->
            val officeLat = (snap.child("lat").value as? Number)?.toDouble()
            val officeLng = (snap.child("lng").value as? Number)?.toDouble()
            val radius = (snap.child("radius").value as? Number)?.toDouble() ?: 200.0

            if (officeLat != null && officeLng != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLoc.latitude, currentLoc.longitude,
                    officeLat, officeLng,
                    results
                )
                val distanceMeters = results[0]

                if (distanceMeters <= radius) {
                    // Within office geofence
                    tvInstruction.text = "Office location verified. Place your finger."
                    launchBiometric(isCheckIn)
                    return@addOnSuccessListener
                }
            }

            // Not in office — check if complaint assigned
            checkComplaintThenProceed(isCheckIn, currentLoc)
        }
    }

    private fun checkComplaintThenProceed(isCheckIn: Boolean, location: android.location.Location?) {
        if (complaintAddress.isEmpty()) {
            tvInstruction.text = "Tap to mark your attendance"
            showInfo("Location not verified. You must be at office or have an active complaint to mark attendance.")
            return
        }

        tvInstruction.text = "Verifying complaint location..."

        Thread {
            try {
                val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
                val query = "$complaintAddress, Okara"
                var lat = 0.0; var lng = 0.0; var found = false

                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    geocoder.getFromLocationName(query, 1) { results ->
                        if (results.isNotEmpty()) {
                            lat = results[0].latitude; lng = results[0].longitude; found = true
                        }
                        latch.countDown()
                    }
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                } else {
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(query, 1)
                    if (!results.isNullOrEmpty()) {
                        lat = results[0].latitude; lng = results[0].longitude; found = true
                    }
                }

                val smartRadius = when {
                    complaintAddress.contains("colony", ignoreCase = true) ||
                            complaintAddress.contains("block", ignoreCase = true) -> 600.0
                    complaintAddress.contains("road", ignoreCase = true) ||
                            complaintAddress.contains("street", ignoreCase = true) -> 400.0
                    complaintAddress.contains("chowk", ignoreCase = true) ||
                            complaintAddress.contains("bazar", ignoreCase = true) -> 300.0
                    complaintAddress.contains("complex", ignoreCase = true) ||
                            complaintAddress.contains("town", ignoreCase = true) -> 800.0
                    else -> complaintRadiusMeters
                }

                val allowed = if (found && location != null) {
                    val dist = FloatArray(1)
                    android.location.Location.distanceBetween(location.latitude, location.longitude, lat, lng, dist)
                    dist[0] <= smartRadius
                } else found  // If no GPS yet but address resolved, allow

                runOnUiThread {
                    if (allowed) {
                        tvInstruction.text = "Complaint area verified. Place your finger."
                        launchBiometric(isCheckIn)
                    } else {
                        tvInstruction.text = "Tap to mark your attendance"
                        showInfo("You are not in the complaint area ($complaintAddress). Please go to the location first.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvInstruction.text = "Tap to mark your attendance"
                    showInfo("Could not verify location. Please try again.")
                }
            }
        }.start()
    }

    // ───────────────── BIOMETRIC ─────────────────

    private fun launchBiometric(checkIn: Boolean) {
        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            showInfo("Biometric not set up. Please go to phone Settings and add your Fingerprint or Face ID first.")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                runOnUiThread { if (checkIn) saveCheckIn() else saveCheckOut() }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    runOnUiThread { showInfo("Auth error: $errString") }
                }
            }
            override fun onAuthenticationFailed() {
                runOnUiThread { showInfo("Fingerprint not recognized. Please try again.") }
            }
        }

        val title = if (checkIn) "Mark Check-In" else "Mark Check-Out"
        val subtitle = if (checkIn) "Place your finger to check in" else "Place your finger to check out"

        BiometricPrompt(this, executor, callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()
        )
    }

    // ───────────────── SAVE ─────────────────

    private fun saveCheckIn() {
        val now = Calendar.getInstance()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now.time)
        val lateThreshold = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, officeStartHour)
            set(Calendar.MINUTE, officeStartMinute + gracePeriodMinutes)
        }
        val status = if (now.after(lateThreshold)) "LATE" else "ON_TIME"

        val data = mapOf(
            "checkInTime" to timeStr,
            "checkInTimestamp" to now.timeInMillis,
            "checkOutTime" to "",
            "checkOutTimestamp" to 0L,
            "status" to status,
            "employeeName" to employeeName,
            "date" to todayKey
        )
        db.getReference("attendance").child(deviceId).child(todayKey)
            .setValue(data)
            .addOnSuccessListener {
                // Clear early leave checkout flag on re-check-in
                db.getReference("attendance").child(deviceId).child(todayKey)
                    .updateChildren(mapOf("earlyLeaveCheckout" to false))
                // Notify admin
                val locType = if (complaintAddress.isNotEmpty()) "Field: $complaintAddress" else "Office"
                db.getReference("adminNotifications").push().setValue(mapOf(
                    "message" to "$employeeName checked in — $locType",
                    "employeeName" to employeeName,
                    "deviceId" to deviceId,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                ))
                loadTodayAttendance()
            }
            .addOnFailureListener { err -> showInfo("Save failed: ${err.message}") }
    }

    private fun saveCheckOut() {
        val now = Calendar.getInstance()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now.time)
        val overtimeThreshold = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, officeEndHour)
            set(Calendar.MINUTE, officeEndMinute + 15)
        }

        val updates = mutableMapOf<String, Any>(
            "checkOutTime" to timeStr,
            "checkOutTimestamp" to now.timeInMillis
        )
        if (now.after(overtimeThreshold)) updates["status"] = "OVERTIME"
        // If early leave approved, mark as re-joinable
        if (earlyLeaveApproved) {
            updates["earlyLeaveCheckout"] = true
            updates["earlyLeaveCheckoutAt"] = now.timeInMillis
        }

        db.getReference("attendance").child(deviceId).child(todayKey)
            .updateChildren(updates)
            .addOnSuccessListener { loadTodayAttendance() }
            .addOnFailureListener { err -> showInfo("Save failed: ${err.message}") }
    }

    // ───────────────── EARLY LEAVE ─────────────────

    private fun showEarlyLeaveDialog() {
        if (earlyLeaveApproved) {
            showInfo("Early leave request is already approved. You can check out now.")
            return
        }
        if (earlyLeaveRequestKey.isNotEmpty() && !earlyLeaveApproved) {
            showInfo("Request already sent. Waiting for admin approval.")
            return
        }
        if (!isCheckedIn) { showInfo("You haven't checked in yet."); return }
        if (isCheckedOut) { showInfo("You have already checked out."); return }

        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16, dp), px(12, dp), px(16, dp), px(8, dp))
        }

        layout.addView(tv("Select reason for early leave:", 13f, Color.parseColor("#444444")).also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(10, dp) }
        })

        val reasons = arrayOf(
            "Medical Emergency",
            "Family Emergency",
            "Personal Work",
            "Home Emergency",
            "Travel / Out of City",
            "Other"
        )

        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        reasons.forEachIndexed { i, r ->
            radioGroup.addView(RadioButton(this).apply {
                id = i + 100
                text = r
                textSize = 14f
                isChecked = (i == 0)
                setPadding(0, px(8, dp), 0, px(8, dp))
            })
        }
        layout.addView(radioGroup)

        layout.addView(tv("Additional note (optional):", 12f, Color.parseColor("#757575")).also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { m -> m.topMargin = px(12, dp); m.bottomMargin = px(4, dp) }
        })

        val etNote = EditText(this).apply {
            hint = "e.g. Doctor appointment at 3 PM"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 2
        }
        layout.addView(etNote)

        AlertDialog.Builder(this)
            .setTitle("Request Early Leave")
            .setView(layout)
            .setPositiveButton("Send Request") { _, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                val selectedIndex = (checkedId - 100).coerceIn(0, reasons.size - 1)
                val selectedReason = reasons[selectedIndex]
                val note = etNote.text.toString().trim()

                val data = mapOf(
                    "employeeId" to deviceId,
                    "employeeName" to employeeName,
                    "reason" to selectedReason,
                    "note" to note,
                    "requestedAt" to System.currentTimeMillis(),
                    "status" to "PENDING",
                    "date" to todayKey
                )
                val sendRequest: (String) -> Unit = { finalName ->
                    val dataWithName = data.toMutableMap()
                    dataWithName["employeeName"] = finalName
                    val reqRef = db.getReference("earlyLeaveRequests").push()
                    reqRef.setValue(dataWithName)
                        .addOnSuccessListener {
                            earlyLeaveRequestKey = reqRef.key ?: ""
                            showInfo("Request sent. Waiting for admin approval...")
                            listenForLeaveApproval(earlyLeaveRequestKey)
                        }
                        .addOnFailureListener { e -> showInfo("Failed: ${e.message}") }
                }
                if (employeeName.isNotEmpty()) {
                    sendRequest(employeeName)
                } else {
                    db.getReference("employees").child(deviceId)
                        .child("employeeName").get()
                        .addOnSuccessListener { snap ->
                            val name = snap.value?.toString() ?: "Employee"
                            employeeName = name
                            sendRequest(name)
                        }
                        .addOnFailureListener { sendRequest("Employee") }
                }

            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ───────────────── FIREBASE ─────────────────

    private fun loadOfficeSettings() {
        db.getReference("officeSettings").get().addOnSuccessListener { snap ->
            officeStartHour = (snap.child("startHour").value as? Long)?.toInt() ?: 10
            officeStartMinute = (snap.child("startMinute").value as? Long)?.toInt() ?: 0
            officeEndHour = (snap.child("endHour").value as? Long)?.toInt() ?: 22
            officeEndMinute = (snap.child("endMinute").value as? Long)?.toInt() ?: 0
            gracePeriodMinutes = (snap.child("gracePeriodMinutes").value as? Long)?.toInt() ?: 15
            preShiftMinutes = (snap.child("preShiftMinutes").value as? Long)?.toInt() ?: 60
            complaintRadiusMeters = (snap.child("complaintRadiusMeters").value as? Number)?.toDouble() ?: 500.0

            val sH = if (officeStartHour > 12) officeStartHour - 12 else officeStartHour
            val eH = if (officeEndHour > 12) officeEndHour - 12 else officeEndHour
            val sAMPM = if (officeStartHour >= 12) "PM" else "AM"
            val eAMPM = if (officeEndHour >= 12) "PM" else "AM"
            tvOfficeHours.text = "Office Hours: $sH:${String.format(Locale.getDefault(), "%02d", officeStartMinute)} $sAMPM — $eH:${String.format(Locale.getDefault(), "%02d", officeEndMinute)} $eAMPM"
        }
    }

    private fun loadTodayAttendance() {
        db.getReference("attendance").child(deviceId).child(todayKey).get()
            .addOnSuccessListener { snap ->
                val checkIn = snap.child("checkInTime").value?.toString() ?: ""
                val checkOut = snap.child("checkOutTime").value?.toString() ?: ""
                val status = snap.child("status").value?.toString() ?: ""

                isCheckedIn = checkIn.isNotEmpty()
                isCheckedOut = checkOut.isNotEmpty()

                tvCheckInTime.text = if (checkIn.isNotEmpty()) checkIn else "— —"
                tvCheckOutTime.text = if (checkOut.isNotEmpty()) checkOut else "— —"

                when (status) {
                    "ON_TIME" -> badge("On Time", Color.parseColor("#2E7D32"), Color.parseColor("#E8F5E9"))
                    "LATE" -> badge("Late", Color.parseColor("#C62828"), Color.parseColor("#FFEBEE"))
                    "OVERTIME" -> badge("Overtime", Color.parseColor("#1565C0"), Color.parseColor("#E3F2FD"))
                    else -> badge("Not Marked", Color.parseColor("#9E9E9E"), Color.parseColor("#F5F5F5"))
                }

                when {
                    !isCheckedIn -> {
                        btnAction.text = "Mark Check-In"
                        // ── CHECK-IN COLOR: Change "#2E7D32" to any color ──
                        btnAction.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 12f * resources.displayMetrics.density
                            setColor(Color.parseColor("#2E7D32"))
                        }
                        btnAction.isEnabled = true
                        tvInstruction.text = "Tap to mark Check-In\nFingerprint or Face ID required"
                        btnEarlyLeave.visibility = android.view.View.GONE
                    }
                    !isCheckedOut -> {
                        btnAction.text = "Mark Check-Out"
                        // ── CHECK-OUT COLOR: Change "#C62828" to any color ──
                        btnAction.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 12f * resources.displayMetrics.density
                            setColor(Color.parseColor("#C62828"))
                        }
                        btnAction.isEnabled = true
                        tvInstruction.text = "Checked in! Tap to mark Check-Out\nFingerprint or Face ID required"
                        btnEarlyLeave.visibility = android.view.View.VISIBLE
                    }
                    else -> {
                        val isEarlyLeaveCheckout = snap.child("earlyLeaveCheckout").value as? Boolean ?: false
                        if (isEarlyLeaveCheckout) {
                            // Allow re-check-in
                            isCheckedIn = false
                            isCheckedOut = false
                            earlyLeaveApproved = false
                            earlyLeaveRequestKey = ""
                            btnAction.text = "Mark Check-In"
                            btnAction.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 12f * resources.displayMetrics.density
                                setColor(Color.parseColor("#2E7D32"))
                            }
                            btnAction.isEnabled = true
                            tvInstruction.text = "Welcome back! Tap to re-check-in"
                            btnEarlyLeave.visibility = android.view.View.GONE
                        } else {
                            btnAction.text = "Attendance Complete"
                            // ── COMPLETE COLOR: Change "#9E9E9E" to any color ──
                            btnAction.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 12f * resources.displayMetrics.density
                                setColor(Color.parseColor("#9E9E9E"))
                            }
                            btnAction.isEnabled = false
                            tvInstruction.text = "Today attendance recorded successfully"
                            btnEarlyLeave.visibility = android.view.View.GONE
                        }
                    }
                }
            }
    }

    private fun loadMonthlyStats() {
        val now = Calendar.getInstance()
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now.time)

        // Total days in this month (e.g. 31 for August, 30 for September)
        val todayDay = now.get(Calendar.DAY_OF_MONTH)

        db.getReference("attendance").child(deviceId)
            .orderByKey()
            .startAt("${monthKey}-01")
            .endAt("${monthKey}-31")
            .get()
            .addOnSuccessListener { snap ->
                val presentDates = mutableSetOf<String>()
                var late = 0

                for (day in snap.children) {
                    val ci = day.child("checkInTime").value?.toString() ?: ""
                    val st = day.child("status").value?.toString() ?: ""
                    if (ci.isNotEmpty()) {
                        presentDates.add(day.key ?: "")
                        if (st == "LATE") late++
                    }
                }

                // Absent = days from 1st to yesterday with no record
                // (Today is excluded — employee may still check in today)
                var absent = 0
                for (d in 1 until todayDay) {
                    val dayKey = "${monthKey}-${String.format(Locale.getDefault(), "%02d", d)}"
                    if (dayKey !in presentDates) absent++
                }

                val present = presentDates.size
                // Score = Present / Days passed so far (including today)
                val score = if (todayDay > 0) ((present.toFloat() / todayDay) * 100).toInt() else 0

                tvPresentVal.text = "$present"
                tvAbsentVal.text = "$absent"
                tvLateVal.text = "$late"
                tvScoreVal.text = "$score%"
            }
    }

    private fun badge(text: String, textColor: Int, bgColor: Int) {
        tvStatusBadge.text = text
        tvStatusBadge.setTextColor(textColor)
        val dp = resources.displayMetrics.density
        tvStatusBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * dp
            setColor(bgColor)
        }
    }
}