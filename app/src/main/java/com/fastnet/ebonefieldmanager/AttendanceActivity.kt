package com.fastnet.ebonefieldmanager

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
    private var complaintAddress = ""
    private var preShiftMinutes = 60
    private var complaintRadiusMeters = 500.0
    // FIX (requested): when MainActivity force-launches this screen during
    // the morning Pre-Shift Window (before the employee has checked in),
    // it passes this extra = true. While true and no check-in exists yet
    // today, the back button (both the header arrow and the system back
    // press) is locked so the employee cannot skip straight to the
    // complaints dashboard without completing biometric/PIN attendance.
    // A normal manual open later in the day never sets this, so normal
    // behavior (free back navigation) is unaffected.
    private var isForcedMorningCheckIn = false
    private var autoReturnedAfterForcedCheckIn = false
    private lateinit var backButton: ImageButton

    private var officeStartHour = 10; private var officeStartMinute = 0
    private var officeEndHour = 22; private var officeEndMinute = 0
    private var gracePeriodMinutes = 15
    private var postShiftMinutes = 60

    // Sessions
    private val sessions = mutableListOf<MutableMap<String, Any>>()
    private var currentSessionIndex = -1
    private var isCurrentlyCheckedIn = false

    // Early leave
    private var earlyLeaveRequestKey = ""
    private var earlyLeaveApproved = false
    private var earlyLeaveListener: ValueEventListener? = null

    // Listener
    private var attendanceListener: ValueEventListener? = null
    private var officeSettingsListener: ValueEventListener? = null

    // UI
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
    private lateinit var sessionsContainer: LinearLayout
    // The fingerprint icon must always mirror btnAction's enabled state.
    // Previously it stayed clickable even when btnAction was disabled
    // ("Attendance Complete"), so repeatedly tapping the fingerprint icon
    // after checkout could still trigger a new session at night.
    private lateinit var fingerprintFrame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        employeeName = getSharedPreferences("employee_session", Context.MODE_PRIVATE).getString("employee_name", "") ?: ""
        complaintAddress = intent.getStringExtra("complaintAddress") ?: ""
        isForcedMorningCheckIn = intent.getBooleanExtra("forcedMorningCheckIn", false)
        todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (employeeName.isEmpty()) {
            db.getReference("employees").child(deviceId).child("employeeName").get()
                .addOnSuccessListener { snap -> snap.value?.toString()?.let { if (it.isNotEmpty()) employeeName = it } }
        }

        setContentView(buildLayout())
        loadOfficeSettings()
        attachAttendanceListener() // ONE TIME listener
        checkApprovedLeave()
        loadMonthlyStats()
    }

    // ─────── LAYOUT ───────

    private fun buildLayout(): View {
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F6FA"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0D2E5C"))
            setPadding(px(16,dp), px(48,dp), px(16,dp), px(16,dp))
        }
        backButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert); background = null
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(px(28,dp), px(28,dp))
            setOnClickListener {
                if (isForcedMorningCheckIn && sessions.isEmpty()) {
                    showInfo("Please mark your attendance (Check-In) before continuing.")
                } else {
                    finish()
                }
            }
        }
        header.addView(backButton)

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = px(12,dp) }
        }
        titleBlock.addView(tv("Attendance", 17f, Color.WHITE, bold = true))
        titleBlock.addView(tv(SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date()), 13f, Color.parseColor("#B8C6DE")))
        header.addView(titleBlock)

        // Time Log button (top right)
        tvStatusBadge = tv("Time Log", 11f, Color.parseColor("#111111"), bold = true).also {
            it.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 16f*dp; setColor(Color.parseColor("#F5F5F5")) }
            it.setPadding(px(12,dp), px(3,dp), px(12,dp), px(3,dp))
            it.isClickable = true; it.isFocusable = true
            it.setOnClickListener { showEmployeeTimeLog() }
        }
        header.addView(tvStatusBadge)
        root.addView(header)

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(12,dp), px(12,dp), px(12,dp), px(12,dp)) }
        scroll.addView(content)

        // Check-In / Check-Out Times
        val timesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10,dp) }
        }
        val inCard = timeCard("Check-In", Color.parseColor("#E8F5E9"), Color.parseColor("#1B5E20"), dp)
        val outCard = timeCard("Check-Out", Color.parseColor("#F4F6FA"), Color.parseColor("#9E9E9E"), dp)
        tvCheckInTime = inCard.findViewWithTag("time")
        tvCheckOutTime = outCard.findViewWithTag("time")
        timesRow.addView(inCard.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = px(6,dp) } })
        timesRow.addView(outCard.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(timesRow)

        // Sessions History Card
        val sessCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 10f*dp; setColor(Color.WHITE) }
            setPadding(px(14,dp), px(10,dp), px(14,dp), px(10,dp))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10,dp) }
        }
        sessCard.addView(tv("Today's Sessions", 13f, Color.parseColor("#555555"), bold = true).also {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(6,dp) }
        })
        sessionsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sessCard.addView(sessionsContainer)
        content.addView(sessCard)

        // Biometric Card
        val bioCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 12f*dp; setColor(Color.WHITE) }
            setPadding(px(16,dp), px(20,dp), px(16,dp), px(16,dp))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10,dp) }
        }

        // Fingerprint Icon
        val frame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(px(120,dp), px(120,dp)).also { it.gravity = Gravity.CENTER; it.bottomMargin = px(14,dp) } }
        frame.addView(ring(px(120,dp), Color.parseColor("#BBDEFB"), 1f, dp))
        frame.addView(ring(px(106,dp), Color.parseColor("#90CAF9"), 1.5f, dp))
        val circle = LinearLayout(this).apply {
            val s = px(88,dp)
            layoutParams = FrameLayout.LayoutParams(s, s).also { it.gravity = Gravity.CENTER }; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E3F2FD")); setStroke(px(2,dp), Color.parseColor("#1565C0")) }
        }
        circle.addView(ImageView(this).apply { setImageResource(R.drawable.ic_fingerprint); layoutParams = LinearLayout.LayoutParams(px(46,dp), px(46,dp)); scaleType = ImageView.ScaleType.FIT_CENTER })
        frame.addView(circle)

        // Biometric icon and the main Check-In / Check-Out button use the
        // exact same attendance action. The current state decides whether
        // the action is Check-In or Check-Out, so tapping either control
        // follows the same GPS + biometric verification flow.
        // FIX: the fingerprint icon must respect the SAME disabled state as
        // btnAction — otherwise, after night checkout ("Attendance
        // Complete" / grey button), repeatedly tapping the fingerprint icon
        // could still start a new session even though the button was
        // correctly disabled.
        fingerprintFrame = frame
        frame.isClickable = true
        frame.isFocusable = true
        frame.setOnClickListener {
            if (btnAction.isEnabled) handleAttendanceClick()
        }

        bioCard.addView(LinearLayout(this).apply { gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(14,dp) }; addView(frame) })

        tvInstruction = tv("Tap to mark Check-In\nFingerprint or Face ID required", 13f, Color.parseColor("#757575")).also {
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(16,dp) }
        }
        bioCard.addView(tvInstruction)

        btnAction = Button(this).apply {
            text = "Check-In"; textSize = 15f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 12f*dp; setColor(Color.parseColor("#2E7D32")) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(8,dp) }
            setOnClickListener { handleAttendanceClick() }
        }
        bioCard.addView(btnAction)

        btnEarlyLeave = Button(this).apply {
            text = "Request Early Leave"; textSize = 13f
            setTextColor(Color.parseColor("#E65100"))
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 8f*dp; setColor(Color.WHITE); setStroke(px(1,dp), Color.parseColor("#E65100")) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
            setOnClickListener { showEarlyLeaveDialog() }
        }
        bioCard.addView(btnEarlyLeave)
        content.addView(bioCard)

        // Monthly Stats
        content.addView(tv("THIS MONTH", 13f, Color.parseColor("#9E9E9E"), bold = true).also {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(6,dp) }
        })
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(10,dp) }
        }
        tvPresentVal = tv("—", 23f, Color.parseColor("#1B5E20"), bold = true).also { it.gravity = Gravity.CENTER }
        tvAbsentVal = tv("—", 23f, Color.parseColor("#B71C1C"), bold = true).also { it.gravity = Gravity.CENTER }
        tvLateVal = tv("—", 23f, Color.parseColor("#BF360C"), bold = true).also { it.gravity = Gravity.CENTER }
        tvScoreVal = tv("—", 23f, Color.parseColor("#0D47A1"), bold = true).also { it.gravity = Gravity.CENTER }
        statsRow.addView(statBox(tvPresentVal, "Present", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4,dp) } })
        statsRow.addView(statBox(tvAbsentVal, "Absent", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4,dp) } })
        statsRow.addView(statBox(tvLateVal, "Late", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { m -> m.marginEnd = px(4,dp) } })
        statsRow.addView(statBox(tvScoreVal, "Score", dp).also { it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        content.addView(statsRow)

        // Office hours footer
        tvOfficeHours = tv("Office Hours: Loading...", 13f, Color.parseColor("#444444")).also { it.gravity = Gravity.CENTER }
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 8f*dp; setColor(Color.WHITE) }
            setPadding(px(12,dp), px(10,dp), px(12,dp), px(10,dp))
            addView(tvOfficeHours)
        })

        root.addView(scroll)
        return root
    }

    // ─────── REAL-TIME LISTENER ───────

    private fun attachAttendanceListener() {
        val ref = db.getReference("attendance").child(deviceId).child(todayKey)
        // Remove previous
        attendanceListener?.let { ref.removeEventListener(it) }

        attendanceListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                runOnUiThread { processAttendanceSnapshot(snap) }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(attendanceListener!!)
    }

    private fun processAttendanceSnapshot(snap: DataSnapshot) {
        sessions.clear()
        currentSessionIndex = -1
        isCurrentlyCheckedIn = false

        val sessSnap = snap.child("sessions")

        if (sessSnap.exists()) {
            // New sessions structure
            for (s in sessSnap.children) {
                val m = mutableMapOf<String, Any>()
                s.children.forEach { child -> child.key?.let { k -> child.value?.let { v -> m[k] = v } } }
                sessions.add(m)
            }
            // Find active session
            for (i in sessions.indices.reversed()) {
                val co = sessions[i]["checkOutTime"]?.toString() ?: ""
                if (co.isEmpty()) {
                    isCurrentlyCheckedIn = true
                    currentSessionIndex = i
                    break
                }
            }
        } else {
            // Legacy structure (old single session)
            val ci = snap.child("checkInTime").value?.toString() ?: ""
            val co = snap.child("checkOutTime").value?.toString() ?: ""
            val st = snap.child("status").value?.toString() ?: ""
            if (ci.isNotEmpty()) {
                val m = mutableMapOf<String, Any>("checkInTime" to ci, "checkOutTime" to co, "status" to st)
                if (co.isNotEmpty()) m["checkOutTime"] = co
                sessions.add(m)
                if (co.isEmpty()) { isCurrentlyCheckedIn = true; currentSessionIndex = 0 }
            }
        }

        // Update UI (Firebase callbacks run on main thread already)
        updateUI()
        renderSessions()
        checkApprovedLeave()
        loadMonthlyStats()

        // FIX (requested): once the employee's forced morning check-in has
        // actually completed (a real session now exists today), briefly
        // show the confirmed "Checked in!" state, then automatically return
        // to MainActivity's dashboard — no manual back-press needed. Guarded
        // so this only fires once per screen instance.
        if (isForcedMorningCheckIn && sessions.isNotEmpty() && !autoReturnedAfterForcedCheckIn) {
            autoReturnedAfterForcedCheckIn = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 900)
        }
    }

    private fun renderSessions() {
        val dp = resources.displayMetrics.density
        sessionsContainer.removeAllViews()

        if (sessions.isEmpty()) {
            sessionsContainer.addView(tv("No sessions today", 12f, Color.parseColor("#9E9E9E")).also {
                it.gravity = Gravity.CENTER; it.setPadding(0, px(4,dp), 0, px(4,dp))
            })
            tvCheckInTime.text = "— —"
            tvCheckOutTime.text = "— —"
            return
        }

        sessions.forEachIndexed { i, sess ->
            val ci = sess["checkInTime"]?.toString() ?: ""
            val co = sess["checkOutTime"]?.toString() ?: ""
            val st = sess["status"]?.toString() ?: ""

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6,dp), 0, px(6,dp))
                setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#FAFAFA"))
            }

            val sessionLabel = tv("Session ${i+1}", 11f, Color.parseColor("#777777"), bold = true).also {
                it.layoutParams = LinearLayout.LayoutParams(px(70,dp), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val timeText = when {
                ci.isNotEmpty() && co.isNotEmpty() -> "$ci → $co"
                ci.isNotEmpty() -> "$ci → active"
                else -> "—"
            }
            val timeTv = tv(timeText, 12f, if (co.isEmpty() && ci.isNotEmpty()) Color.parseColor("#2E7D32") else Color.parseColor("#333333")).also {
                it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeText = when {
                co.isEmpty() && ci.isNotEmpty() -> "Active"
                st == "LATE" -> "Late"
                st == "OVERTIME" -> "OT"
                ci.isNotEmpty() -> "Done"
                else -> "—"
            }
            val badgeColor = when(badgeText) { "Active" -> "#2E7D32"; "Late" -> "#C62828"; "OT" -> "#1565C0"; else -> "#9E9E9E" }
            val badge = tv(badgeText, 10f, Color.parseColor(badgeColor), bold = true).also {
                it.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 8f*dp; setColor(Color.parseColor(badgeColor + "22")) }
                it.setPadding(px(6,dp), px(2,dp), px(6,dp), px(2,dp))
            }

            row.addView(sessionLabel); row.addView(timeTv); row.addView(badge)
            sessionsContainer.addView(row)
        }

        // Update time cards with latest session
        val lastSess = sessions.last()
        val activeSess = sessions.lastOrNull { (it["checkOutTime"]?.toString() ?: "").isEmpty() && (it["checkInTime"]?.toString() ?: "").isNotEmpty() }
        tvCheckInTime.text = (activeSess ?: lastSess)["checkInTime"]?.toString() ?: "— —"
        tvCheckOutTime.text = (activeSess ?: lastSess)["checkOutTime"]?.toString().let { if (it.isNullOrEmpty()) "— —" else it }
    }

    private fun updateUI() {
        val dp = resources.displayMetrics.density

        // Check early leave approved from SharedPrefs
        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val isApprovedToday = prefs.getString("approvedLeaveDate", "") == todayKey

        when {
            !isCurrentlyCheckedIn && sessions.isEmpty() -> {
                // No sessions yet
                btnAction.text = "Check-In"
                setGreenBtn()
                fingerprintFrame.isEnabled = true
                tvInstruction.text = "Tap to mark Check-In\nFingerprint or Face ID required"
                btnEarlyLeave.visibility = View.GONE
            }
            !isCurrentlyCheckedIn && sessions.isNotEmpty() -> {
                // Has sessions but all completed
                val lastCo = sessions.last()["checkOutTime"]?.toString() ?: ""
                val nowTotal = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY)*60 + it.get(Calendar.MINUTE) }
                val endTotal = officeEndHour*60 + officeEndMinute

                if (lastCo.isNotEmpty() && nowTotal < endTotal) {
                    // Can re-check-in (within office hours)
                    btnAction.text = "Check-In"
                    setGreenBtn()
                    fingerprintFrame.isEnabled = true
                    tvInstruction.text = "Welcome back! Tap to re-check-in"
                    btnEarlyLeave.visibility = View.GONE
                } else {
                    btnAction.text = "Attendance Complete"
                    btnAction.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 12f*dp; setColor(Color.parseColor("#9E9E9E")) }
                    btnAction.isEnabled = false
                    // FIX: keep the fingerprint icon's enabled state in sync
                    // with the button — this is what stops repeated
                    // fingerprint taps from creating a new session after
                    // night checkout / office close.
                    fingerprintFrame.isEnabled = false
                    tvInstruction.text = "Today attendance recorded successfully"
                    btnEarlyLeave.visibility = View.GONE
                }
            }
            isCurrentlyCheckedIn -> {
                // Currently checked in
                btnAction.text = "Check-Out"
                btnAction.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 12f*dp; setColor(Color.parseColor("#C62828")) }
                btnAction.isEnabled = true
                fingerprintFrame.isEnabled = true
                tvInstruction.text = "Checked in! Tap to mark Check-Out"

                if (isApprovedToday) {
                    earlyLeaveApproved = true
                    btnEarlyLeave.visibility = View.VISIBLE
                    btnEarlyLeave.text = "Leave Approved  Check Out Now"
                    btnEarlyLeave.isEnabled = false
                    btnEarlyLeave.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    val nowTotal = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY)*60 + it.get(Calendar.MINUTE) }
                    val endTotal = officeEndHour*60 + officeEndMinute
                    val startTotal = officeStartHour*60 + officeStartMinute
                    btnEarlyLeave.visibility = if (nowTotal in startTotal..endTotal) View.VISIBLE else View.GONE
                    btnEarlyLeave.text = "Request Early Leave"
                    btnEarlyLeave.isEnabled = true
                    btnEarlyLeave.setTextColor(Color.parseColor("#E65100"))
                }
            }
        }
    }

    private fun setGreenBtn() {
        val dp = resources.displayMetrics.density
        btnAction.background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 12f*dp; setColor(Color.parseColor("#2E7D32")) }
        btnAction.isEnabled = true
    }

    // ─────── ATTENDANCE LOGIC ───────

    private fun handleAttendanceClick() {
        // FIX: if the app was left open overnight, todayKey (set once in
        // onCreate) could still point at yesterday's date. Refresh it and
        // re-attach the listener before doing anything else, so a tap never
        // writes a new session onto yesterday's already-closed record.
        val actualTodayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (actualTodayKey != todayKey) {
            todayKey = actualTodayKey
            attachAttendanceListener()
            showInfo("Date changed — refreshing today's attendance. Tap again to continue.")
            return
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            AlertDialog.Builder(this).setTitle("GPS Required").setMessage("Enable GPS to mark attendance.")
                .setPositiveButton("Settings") { _, _ -> startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("Cancel", null).show(); return
        }
        val now = Calendar.getInstance()

        // IMPORTANT: preserve the original post-close check-out behavior for an
        // active session, but NEVER allow a new/re-check-in after office close.
        // This guard applies to both the button and the fingerprint icon, because
        // both ultimately call handleAttendanceClick().
        if (!isCurrentlyCheckedIn) {
            val nowTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val endTotal = officeEndHour * 60 + officeEndMinute

            if (nowTotal >= endTotal) {
                showInfo("Office is closed. Attendance is disabled now.")
                return
            }
            // officeStartHour/Minute/preShiftMinutes kept up-to-date by officeSettingsListener
            val totalMins = officeStartHour * 60 + officeStartMinute - preShiftMinutes
            val wHour = (totalMins / 60).coerceAtLeast(0)
            val wMin = (totalMins % 60).coerceAtLeast(0)
            val wOpen = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, wHour); set(Calendar.MINUTE, wMin); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (now.before(wOpen)) {
                val dH = if (wHour > 12) wHour - 12 else if (wHour == 0) 12 else wHour
                val ap = if (wHour >= 12) "PM" else "AM"
                showInfo("Check-in opens at $dH:${String.format(Locale.getDefault(), "%02d", wMin)} $ap")
                return
            }
            verifyLocationThenBiometric(isCheckIn = true)
        } else {
            if (earlyLeaveApproved) { verifyLocationThenBiometric(isCheckIn = false); return }
            val checkoutWindow = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, officeEndHour)
                set(Calendar.MINUTE, officeEndMinute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (now.before(checkoutWindow)) {
                val dispH = if (officeEndHour > 12) officeEndHour - 12 else if (officeEndHour == 0) 12 else officeEndHour
                val ampm = if (officeEndHour >= 12) "PM" else "AM"
                val dispM = String.format(Locale.getDefault(), "%02d", officeEndMinute)
                showInfo("Check-out opens at $dispH:$dispM $ampm. Use Request Early Leave for early departure.")
                return
            }
            verifyLocationThenBiometric(isCheckIn = false)
        }
    }


    private fun verifyLocationThenBiometric(isCheckIn: Boolean) {
        tvInstruction.text = "Verifying location..."
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) { showInfo("Location permission required."); tvInstruction.text = "Tap to mark attendance"; return }
        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        var loc: android.location.Location? = null
        try { loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (e: SecurityException) {}
        if (loc == null) { checkComplaintThenProceed(isCheckIn, null); return }
        db.getReference("attendanceGeofence").get().addOnSuccessListener { snap ->
            val oLat = (snap.child("lat").value as? Number)?.toDouble()
            val oLng = (snap.child("lng").value as? Number)?.toDouble()
            val radius = (snap.child("radius").value as? Number)?.toDouble() ?: 200.0
            if (oLat != null && oLng != null) {
                val r = FloatArray(1); android.location.Location.distanceBetween(loc.latitude, loc.longitude, oLat, oLng, r)
                if (r[0] <= radius) { tvInstruction.text = "Office verified. Place your finger."; launchBiometric(isCheckIn); return@addOnSuccessListener }
            }
            checkComplaintThenProceed(isCheckIn, loc)
        }
    }

    private fun checkComplaintThenProceed(isCheckIn: Boolean, location: android.location.Location?) {
        if (complaintAddress.isEmpty()) { showInfo("Not at office or complaint location."); tvInstruction.text = "Tap to mark attendance"; return }
        tvInstruction.text = "Verifying complaint location..."
        Thread {
            try {
                val geocoder = android.location.Geocoder(this, Locale.getDefault())
                var lat = 0.0; var lng = 0.0; var found = false
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    geocoder.getFromLocationName("$complaintAddress, Okara", 1) { r -> if (r.isNotEmpty()) { lat = r[0].latitude; lng = r[0].longitude; found = true }; latch.countDown() }
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                } else { @Suppress("DEPRECATION") val r = geocoder.getFromLocationName("$complaintAddress, Okara", 1); if (!r.isNullOrEmpty()) { lat = r[0].latitude; lng = r[0].longitude; found = true } }
                val sr = when { complaintAddress.contains("colony", ignoreCase = true) || complaintAddress.contains("block", ignoreCase = true) -> 600.0; complaintAddress.contains("road", ignoreCase = true) || complaintAddress.contains("street", ignoreCase = true) -> 400.0; complaintAddress.contains("chowk", ignoreCase = true) || complaintAddress.contains("bazar", ignoreCase = true) -> 300.0; complaintAddress.contains("complex", ignoreCase = true) || complaintAddress.contains("town", ignoreCase = true) -> 800.0; else -> complaintRadiusMeters }
                val allowed = if (found && location != null) { val d = FloatArray(1); android.location.Location.distanceBetween(location.latitude, location.longitude, lat, lng, d); d[0] <= sr } else found
                runOnUiThread { if (allowed) { tvInstruction.text = "Complaint area verified. Place finger."; launchBiometric(isCheckIn) } else { tvInstruction.text = "Tap to mark attendance"; showInfo("Not in complaint area. Go to $complaintAddress.") } }
            } catch (e: Exception) { runOnUiThread { tvInstruction.text = "Tap to mark attendance"; showInfo("Location check failed. Try again.") } }
        }.start()
    }

    private fun launchBiometric(checkIn: Boolean) {
        val bm = BiometricManager.from(this)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK

        val biometricAvailable =
            bm.canAuthenticate(authenticators) ==
                    BiometricManager.BIOMETRIC_SUCCESS

        // Fingerprint and PIN are both available. The existing Attendance UI
        // remains unchanged; PIN is only an alternate authentication path.
        if (!biometricAvailable) {
            showAttendancePinDialog(checkIn)
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (checkIn) "Check-In" else "Check-Out")
            .setSubtitle("Fingerprint or PIN")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(authenticators)
            .build()

        BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    runOnUiThread {
                        if (checkIn) saveCheckIn() else saveCheckOut()
                    }
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    when {
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            // Employee explicitly selected the PIN alternative.
                            runOnUiThread { showAttendancePinDialog(checkIn) }
                        }

                        errorCode != BiometricPrompt.ERROR_USER_CANCELED -> {
                            runOnUiThread {
                                showInfo("Auth error: $errString")
                            }
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    runOnUiThread {
                        showInfo("Fingerprint not recognized. Try again.")
                    }
                }
            }
        ).authenticate(promptInfo)
    }

    /**
     * PIN alternative for devices/employees that cannot or do not want to use
     * fingerprint. This dialog is outside the main layout so the existing
     * Attendance screen design remains unchanged.
     *
     * Firebase:
     *   employees/{deviceId}/attendancePin
     */
    private fun showAttendancePinDialog(checkIn: Boolean) {
        val pinInput = EditText(this).apply {
            hint = "Enter PIN"
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                px(22, resources.displayMetrics.density),
                px(4, resources.displayMetrics.density),
                px(22, resources.displayMetrics.density),
                0
            )
            addView(pinInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (checkIn) "Check-In PIN" else "Check-Out PIN")
            .setMessage("Enter your attendance PIN to continue.")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enteredPin = pinInput.text.toString().trim()

                if (enteredPin.isEmpty()) {
                    pinInput.error = "Enter your PIN"
                    pinInput.requestFocus()
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

                verifyAttendancePin(
                    enteredPin = enteredPin,
                    checkIn = checkIn,
                    dialog = dialog,
                    pinInput = pinInput
                )
            }
        }

        dialog.show()
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        pinInput.requestFocus()
    }

    private fun verifyAttendancePin(
        enteredPin: String,
        checkIn: Boolean,
        dialog: AlertDialog,
        pinInput: EditText
    ) {
        if (deviceId.isBlank()) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            showInfo("Employee device could not be identified.")
            return
        }

        db.getReference("employees")
            .child(deviceId)
            .child("attendancePin")
            .get()
            .addOnSuccessListener { snapshot ->
                val savedPin = snapshot.value?.toString()?.trim().orEmpty()

                if (savedPin.isEmpty()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    showInfo("Attendance PIN is not configured for this employee.")
                    return@addOnSuccessListener
                }

                if (enteredPin == savedPin) {
                    dialog.dismiss()
                    if (checkIn) saveCheckIn() else saveCheckOut()
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    pinInput.error = "Incorrect PIN"
                    pinInput.requestFocus()
                }
            }
            .addOnFailureListener { error ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                showInfo(
                    "Could not verify PIN: ${error.message ?: "unknown error"}"
                )
            }
    }

    // ─────── SAVE (No loadTodayAttendance call — listener handles update) ───────

    private fun saveCheckIn() {
        // Clear early leave state IMMEDIATELY before Firebase write
        // So listener fires with clean state
        earlyLeaveApproved = false
        earlyLeaveRequestKey = ""
        getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE).edit().remove("approvedLeaveDate").apply()
        earlyLeaveListener?.let {
            if (earlyLeaveRequestKey.isNotEmpty())
                db.getReference("earlyLeaveRequests").child(earlyLeaveRequestKey).removeEventListener(it)
        }

        val now = Calendar.getInstance()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now.time)
        val lateThreshold = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, officeStartHour); set(Calendar.MINUTE, officeStartMinute + gracePeriodMinutes) }
        val status = if (now.after(lateThreshold)) "LATE" else "ON_TIME"

        val newIndex = sessions.size
        val sessionData = mapOf(
            "checkInTime" to timeStr,
            "checkInTimestamp" to now.timeInMillis,
            "checkOutTime" to "",
            "checkOutTimestamp" to 0L,
            "status" to status,
            "earlyLeave" to false
        )

        db.getReference("attendance").child(deviceId).child(todayKey)
            .child("sessions").child(newIndex.toString()).setValue(sessionData)
            .addOnSuccessListener {
                // Notify admin
                val locType = if (complaintAddress.isNotEmpty()) "Field: $complaintAddress" else "Office"
                db.getReference("adminNotifications").push().setValue(mapOf(
                    "message" to "$employeeName checked in — $locType",
                    "employeeName" to employeeName, "deviceId" to deviceId,
                    "timestamp" to System.currentTimeMillis(), "read" to false
                ))
                // Listener will automatically update UI
            }
            .addOnFailureListener { showInfo("Check-in failed: ${it.message}") }
    }

    private fun saveCheckOut() {
        if (currentSessionIndex < 0) { showInfo("No active session found."); return }
        val now = Calendar.getInstance()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now.time)
        // postShiftMinutes = overtime starts after this many minutes past office end
        val postShift = (officeEndMinute + postShiftMinutes) % 60
        val postShiftHour = officeEndHour + (officeEndMinute + postShiftMinutes) / 60
        val overtimeThreshold = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, postShiftHour); set(Calendar.MINUTE, postShift)
        }

        val updates = mutableMapOf<String, Any>(
            "checkOutTime" to timeStr,
            "checkOutTimestamp" to now.timeInMillis
        )
        if (now.after(overtimeThreshold)) updates["status"] = "OVERTIME"
        if (earlyLeaveApproved) updates["earlyLeave"] = true

        // Update only current session
        db.getReference("attendance").child(deviceId).child(todayKey)
            .child("sessions").child(currentSessionIndex.toString())
            .updateChildren(updates)
            .addOnFailureListener { showInfo("Check-out failed: ${it.message}") }
        // Listener will automatically update UI
    }

    // ─────── EARLY LEAVE ───────

    private fun showEarlyLeaveDialog() {
        if (earlyLeaveApproved) return
        if (earlyLeaveRequestKey.isNotEmpty()) { showInfo("Request already sent. Waiting for admin approval."); return }
        if (!isCurrentlyCheckedIn) { showInfo("You haven't checked in yet."); return }
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16,dp), px(12,dp), px(16,dp), px(8,dp)) }
        layout.addView(tv("Select reason for early leave:", 13f, Color.parseColor("#444444")).also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { m -> m.bottomMargin = px(10,dp) } })
        val reasons = arrayOf("Medical Emergency", "Family Emergency", "Personal Work", "Home Emergency", "Travel / Out of City", "Other")
        val radioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        reasons.forEachIndexed { i, r -> radioGroup.addView(RadioButton(this).apply { id = i+100; text = r; textSize = 14f; isChecked = (i==0); setPadding(0, px(8,dp), 0, px(8,dp)) }) }
        layout.addView(radioGroup)
        layout.addView(tv("Additional note (optional):", 12f, Color.parseColor("#757575")).also { it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { m -> m.topMargin = px(12,dp); m.bottomMargin = px(4,dp) } })
        val etNote = EditText(this).apply { hint = "e.g. Doctor appointment at 3 PM"; inputType = InputType.TYPE_CLASS_TEXT; maxLines = 2 }
        layout.addView(etNote)
        AlertDialog.Builder(this).setTitle("Request Early Leave").setView(layout)
            .setPositiveButton("Send Request") { _, _ ->
                val reason = reasons[(radioGroup.checkedRadioButtonId - 100).coerceIn(0, reasons.size-1)]
                val note = etNote.text.toString().trim()
                val sendRequest: (String) -> Unit = { name ->
                    val reqRef = db.getReference("earlyLeaveRequests").push()
                    reqRef.setValue(mapOf("employeeId" to deviceId, "employeeName" to name, "reason" to reason, "note" to note, "requestedAt" to System.currentTimeMillis(), "status" to "PENDING", "date" to todayKey))
                        .addOnSuccessListener {
                            earlyLeaveRequestKey = reqRef.key ?: ""
                            showInfo("Request sent. Waiting for admin approval...")
                            listenForLeaveApproval(earlyLeaveRequestKey)
                        }
                        .addOnFailureListener { showInfo("Failed: ${it.message}") }
                }
                if (employeeName.isNotEmpty()) sendRequest(employeeName)
                else db.getReference("employees").child(deviceId).child("employeeName").get()
                    .addOnSuccessListener { snap -> val n = snap.value?.toString() ?: "Employee"; employeeName = n; sendRequest(n) }
                    .addOnFailureListener { sendRequest("Employee") }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun listenForLeaveApproval(requestKey: String) {
        if (requestKey.isEmpty()) return
        earlyLeaveListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val status = snap.child("status").value?.toString() ?: return
                when (status) {
                    "APPROVED" -> {
                        earlyLeaveApproved = true
                        getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE).edit().putString("approvedLeaveDate", todayKey).apply()
                        runOnUiThread {
                            showApprovedButton()
                            AlertDialog.Builder(this@AttendanceActivity).setTitle("Request Approved ✅").setMessage("Your early leave has been approved. You can now check out.")
                                .setPositiveButton("OK", null).setCancelable(false).show()
                        }
                    }
                    "REJECTED" -> {
                        earlyLeaveApproved = false; earlyLeaveRequestKey = ""
                        runOnUiThread {
                            btnEarlyLeave.text = "Request Early Leave"; btnEarlyLeave.isEnabled = true; btnEarlyLeave.setTextColor(Color.parseColor("#E65100"))
                            AlertDialog.Builder(this@AttendanceActivity).setTitle("Request Rejected").setMessage("Early leave was rejected. Please contact admin.")
                                .setPositiveButton("OK", null).setCancelable(false).show()
                        }
                    }
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.getReference("earlyLeaveRequests").child(requestKey).addValueEventListener(earlyLeaveListener!!)
    }

    private fun showApprovedButton() {
        btnEarlyLeave.text = "Leave Approved  Check Out Now"
        btnEarlyLeave.isEnabled = false; btnEarlyLeave.visibility = View.VISIBLE
        btnEarlyLeave.setTextColor(Color.parseColor("#2E7D32"))
    }

    private fun checkApprovedLeave() {
        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        if (prefs.getString("approvedLeaveDate", "") == todayKey) { earlyLeaveApproved = true; showApprovedButton() }
        db.getReference("earlyLeaveRequests").orderByChild("date").equalTo(todayKey).get()
            .addOnSuccessListener { snap ->
                for (req in snap.children) {
                    val empId = req.child("employeeId").value?.toString() ?: ""
                    val status = req.child("status").value?.toString() ?: ""
                    if (empId == deviceId && status == "APPROVED") {
                        earlyLeaveApproved = true; earlyLeaveRequestKey = req.key ?: ""
                        prefs.edit().putString("approvedLeaveDate", todayKey).apply()
                        runOnUiThread { showApprovedButton() }; break
                    }
                }
            }
    }

    // ─────── FIREBASE LOAD ───────

    private fun loadOfficeSettings() {
        officeSettingsListener?.let {
            db.getReference("officeSettings").removeEventListener(it)
        }
        officeSettingsListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                officeStartHour = (snap.child("startHour").value as? Long)?.toInt() ?: 10
                officeStartMinute = (snap.child("startMinute").value as? Long)?.toInt() ?: 0
                officeEndHour = (snap.child("endHour").value as? Long)?.toInt() ?: 22
                officeEndMinute = (snap.child("endMinute").value as? Long)?.toInt() ?: 0
                gracePeriodMinutes = (snap.child("gracePeriodMinutes").value as? Long)?.toInt() ?: 15
                preShiftMinutes = (snap.child("preShiftMinutes").value as? Long)?.toInt() ?: 60
                postShiftMinutes = (snap.child("postShiftMinutes").value as? Long)?.toInt() ?: 60
                complaintRadiusMeters = (snap.child("complaintRadiusMeters").value as? Number)?.toDouble() ?: 500.0
                val sH = if (officeStartHour > 12) officeStartHour-12 else officeStartHour
                val eH = if (officeEndHour > 12) officeEndHour-12 else officeEndHour
                tvOfficeHours.text = "Office Hours: $sH:${String.format(Locale.getDefault(), "%02d", officeStartMinute)} ${if (officeStartHour >= 12) "PM" else "AM"} — $eH:${String.format(Locale.getDefault(), "%02d", officeEndMinute)} ${if (officeEndHour >= 12) "PM" else "AM"}"
                updateUI()
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.getReference("officeSettings").addValueEventListener(officeSettingsListener!!)
    }

    private fun loadMonthlyStats() {
        val now = Calendar.getInstance()
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(now.time)
        val todayDay = now.get(Calendar.DAY_OF_MONTH)
        db.getReference("attendance").child(deviceId).orderByKey().startAt("${monthKey}-01").endAt("${monthKey}-31").get()
            .addOnSuccessListener { snap ->
                val presentDates = mutableSetOf<String>()
                var late = 0
                for (day in snap.children) {
                    val dayKey = day.key ?: continue
                    val sessSnap = day.child("sessions")
                    if (sessSnap.exists()) {
                        var hadCheckIn = false
                        for (s in sessSnap.children) {
                            if (s.child("checkInTime").value?.toString()?.isNotEmpty() == true) {
                                hadCheckIn = true
                                if (s.child("status").value?.toString() == "LATE") late++
                            }
                        }
                        if (hadCheckIn) presentDates.add(dayKey)
                    } else {
                        val ci = day.child("checkInTime").value?.toString() ?: ""
                        val st = day.child("status").value?.toString() ?: ""
                        if (ci.isNotEmpty()) { presentDates.add(dayKey); if (st == "LATE") late++ }
                    }
                }
                // Absent = 0 if no records. Count from first check-in date
                var absent = 0
                val firstDay = if (presentDates.isNotEmpty()) {
                    try {
                        presentDates.sorted().first().split("-").last().toInt()
                    } catch (e: Exception) { todayDay }
                } else todayDay
                if (presentDates.isNotEmpty()) {
                    for (d in firstDay until todayDay) {
                        val dk = "${monthKey}-${String.format(Locale.getDefault(), "%02d", d)}"
                        if (dk !in presentDates) absent++
                    }
                }
                val present = presentDates.size
                // Score also counted only from the first check-in date
                // onward — same window as Absent — so a fresh install
                // mid-month doesn't drag the score down artificially.
                val scoreDays = if (presentDates.isEmpty()) 1 else
                    (todayDay - firstDay + 1).coerceAtLeast(1)
                val score = if (scoreDays > 0) ((present.toFloat() / scoreDays) * 100).toInt() else 0
                tvPresentVal.text = "$present"; tvAbsentVal.text = "$absent"
                tvLateVal.text = "$late"; tvScoreVal.text = "$score%"
            }
    }

    // ─────── TIME LOG ───────

    private fun parseTimeMins(timeStr: String): Int {
        return try {
            val lower = timeStr.lowercase(Locale.getDefault()).trim()
            val isPm = lower.contains("pm")
            val clean = lower.replace("am","").replace("pm","").trim()
            val parts = clean.split(":")
            var hour = parts[0].trim().toInt()
            val min = if (parts.size > 1) parts[1].trim().toInt() else 0
            if (isPm && hour != 12) hour += 12
            if (!isPm && hour == 12) hour = 0
            hour * 60 + min
        } catch (e: Exception) { -1 }
    }

    private fun showEmployeeTimeLog() {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val dp = resources.displayMetrics.density
        val scroll = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16,dp), px(8,dp), px(16,dp), px(8,dp)) }
        scroll.addView(container)
        container.addView(tv("Loading...", 13f, Color.parseColor("#9E9E9E")).also { it.gravity = Gravity.CENTER })
        AlertDialog.Builder(this).setTitle("My Time Log").setView(scroll).setPositiveButton("Close", null).show()

        db.getReference("officeSettings").get().addOnSuccessListener { settingsSnap ->
            val freshEndHour = (settingsSnap.child("endHour").value as? Long)?.toInt() ?: officeEndHour
            val freshEndMin = (settingsSnap.child("endMinute").value as? Long)?.toInt() ?: officeEndMinute
            val officeEndMins = freshEndHour * 60 + freshEndMin

            db.getReference("attendance").child(deviceId).orderByKey().startAt("${monthKey}-01").endAt("${monthKey}-31")
                .get().addOnSuccessListener { snap ->
                    container.removeAllViews()
                    var count = 0
                    val officeStartMins = freshEndHour * 0 + officeStartHour * 60 + officeStartMinute

                    for (daySnap in snap.children) {
                        val dayKey = daySnap.key ?: continue
                        val sessSnap = daySnap.child("sessions")

                        // Get check-in records (all sessions)
                        val checkIns = if (sessSnap.exists()) {
                            sessSnap.children.mapNotNull { s ->
                                val ci = s.child("checkInTime").value?.toString() ?: ""
                                val st = s.child("status").value?.toString() ?: ""
                                if (ci.isNotEmpty() && st == "LATE") ci else null
                            }
                        } else {
                            val ci = daySnap.child("checkInTime").value?.toString() ?: ""
                            val st = daySnap.child("status").value?.toString() ?: ""
                            if (ci.isNotEmpty() && st == "LATE") listOf(ci) else emptyList()
                        }

                        for (ci in checkIns) {
                            val ciMins = parseTimeMins(ci)
                            val graceMins = officeStartHour * 60 + officeStartMinute + gracePeriodMinutes
                            val lateMins = if (ciMins > graceMins) ciMins - graceMins else 0
                            val dateFmt = try {
                                val p = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayKey)
                                SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(p!!)
                            } catch (e: Exception) { dayKey }

                            count++
                            val lateHours = lateMins / 60
                            val lateRemMins = lateMins % 60
                            val lateText = when {
                                lateMins <= 0 -> "Late"
                                lateHours > 0 && lateRemMins > 0 -> "${lateHours}h ${lateRemMins}min late"
                                lateHours > 0 -> "${lateHours}h late"
                                else -> "${lateMins}min late"
                            }
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                                setPadding(0, px(10,dp), 0, px(10,dp))
                                setBackgroundColor(if (count%2==0) Color.parseColor("#FAFAFA") else Color.WHITE)
                            }
                            row.addView(tv(dateFmt, 12f, Color.parseColor("#333333"), bold = true).also {
                                it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            row.addView(tv("Arrived: $ci", 11f, Color.parseColor("#555555")).also {
                                it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            row.addView(tv(lateText, 11f, Color.parseColor("#C62828"), bold = true))
                            container.addView(row)
                            container.addView(View(this).apply {
                                setBackgroundColor(Color.parseColor("#EEEEEE"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                            })
                        }
                    }
                    if (count == 0) {
                        container.addView(tv("No late records this month.", 13f, Color.parseColor("#9E9E9E")).also {
                            it.gravity = Gravity.CENTER; it.setPadding(0, px(20,dp), 0, px(20,dp))
                        })
                    } else {
                        container.addView(tv("Total Late: $count times", 13f, Color.parseColor("#C62828"), bold = true).also {
                            it.setPadding(0, px(10,dp), 0, 0)
                        })
                    }
                }
        }
    }

    // ─────── STAT DETAIL ───────

    private fun showStatDetail(type: String) {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        db.getReference("attendance").child(deviceId)
            .orderByKey().startAt("${monthKey}-01").endAt("${monthKey}-31")
            .get().addOnSuccessListener { snap ->
                val dp = resources.displayMetrics.density
                val scroll = android.widget.ScrollView(this)
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(16,dp), px(8,dp), px(16,dp), px(8,dp))
                }
                scroll.addView(container)
                var count = 0

                // Build records list - ALL sessions across ALL days
                data class AttRecord(val date: String, val ci: String, val co: String, val status: String)
                val allRecords = mutableListOf<AttRecord>()
                val presentDays = mutableSetOf<String>()

                for (daySnap in snap.children) {
                    val dayKey = daySnap.key ?: continue
                    val sessSnap = daySnap.child("sessions")
                    if (sessSnap.exists()) {
                        for (s in sessSnap.children) {
                            val ci = s.child("checkInTime").value?.toString() ?: ""
                            val co = s.child("checkOutTime").value?.toString() ?: ""
                            val st = s.child("status").value?.toString() ?: ""
                            if (ci.isNotEmpty()) {
                                allRecords.add(AttRecord(dayKey, ci, co, st))
                                presentDays.add(dayKey)
                            }
                        }
                    } else {
                        val ci = daySnap.child("checkInTime").value?.toString() ?: ""
                        val co = daySnap.child("checkOutTime").value?.toString() ?: ""
                        val st = daySnap.child("status").value?.toString() ?: ""
                        if (ci.isNotEmpty()) {
                            allRecords.add(AttRecord(dayKey, ci, co, st))
                            presentDays.add(dayKey)
                        }
                    }
                }
                // For backward compat with absent logic
                val presentMap = presentDays.associateWith { "" }
                val statusMap = mutableMapOf<String, String>()
                allRecords.forEach { statusMap[it.date] = it.status }

                val firstPresentDay = if (presentDays.isNotEmpty()) {
                    try { presentDays.sorted().first().split("-").last().toInt() } catch (e: Exception) { todayDay }
                } else todayDay

                // Absent days
                if (type == "Absent") {
                    val absentList = if (presentDays.isEmpty()) emptyList() else
                        (firstPresentDay until todayDay).map { d -> "${monthKey}-${String.format(Locale.getDefault(), "%02d", d)}" }.filter { it !in presentDays }
                    absentList.forEach { date ->
                        count++
                        val dateFmt = try { val p = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date); SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(p!!) } catch (e: Exception) { date }
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10,dp), 0, px(10,dp)); setBackgroundColor(if (count%2==0) Color.parseColor("#F8F8F8") else Color.WHITE) }
                        row.addView(tv(dateFmt, 13f, Color.parseColor("#333333"), bold = true).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                        row.addView(tv("No attendance", 12f, Color.parseColor("#C62828")))
                        container.addView(row)
                        container.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EEEEEE")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) })
                    }
                    // FIX (requested): total line at the bottom, like a sum
                    // drawn under a column of figures — "Total Absent: X days".
                    container.addView(View(this).apply {
                        setBackgroundColor(Color.parseColor("#333333"))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(2, dp)).also { it.topMargin = px(6, dp); it.bottomMargin = px(8, dp) }
                    })
                    container.addView(tv("Total Absent: ${absentList.size} days", 14f, Color.parseColor("#C62828"), bold = true).also {
                        it.setPadding(0, 0, 0, px(6, dp))
                    })
                } else {
                    when (type) {
                        "Present" -> {
                            // One row per DAY (not per session)
                            presentDays.sorted().forEach { date ->
                                val dayRecs = allRecords.filter { it.date == date }
                                val firstCi = dayRecs.firstOrNull()?.ci ?: ""
                                val lastCo = dayRecs.lastOrNull { it.co.isNotEmpty() }?.co ?: ""
                                val daySt = dayRecs.firstOrNull()?.status ?: ""
                                count++
                                val dateFmt = try { val p = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date); SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(p!!) } catch (e: Exception) { date }
                                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10,dp), 0, px(10,dp)); setBackgroundColor(if (count%2==0) Color.parseColor("#F8F8F8") else Color.WHITE) }
                                row.addView(tv(dateFmt, 13f, Color.parseColor("#333333"), bold = true).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                                val detail = if (lastCo.isNotEmpty()) "$firstCi → $lastCo" else "In: $firstCi"
                                val sessCount = if (dayRecs.size > 1) " (${dayRecs.size} sessions)" else ""
                                val dColor = when(daySt) { "LATE" -> "#E65100"; "OVERTIME" -> "#1565C0"; else -> "#2E7D32" }
                                row.addView(tv(detail + sessCount, 12f, Color.parseColor(dColor)))
                                container.addView(row)
                                container.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EEEEEE")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) })
                            }
                            // FIX (requested): total line at the bottom —
                            // "Total Present: X days" for the current month
                            // up to today's date, like a sum drawn under a
                            // column of figures.
                            container.addView(View(this).apply {
                                setBackgroundColor(Color.parseColor("#333333"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(2, dp)).also { it.topMargin = px(6, dp); it.bottomMargin = px(8, dp) }
                            })
                            container.addView(tv("Total Present: ${presentDays.size} days", 14f, Color.parseColor("#2E7D32"), bold = true).also {
                                it.setPadding(0, 0, 0, px(6, dp))
                            })
                        }
                        "Late" -> {
                            var totalLateMinsThisMonth = 0
                            allRecords.filter { it.status == "LATE" }.sortedBy { it.date }.forEach { rec ->
                                count++
                                val dateFmt = try { val p = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(rec.date); SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(p!!) } catch (e: Exception) { rec.date }
                                // Calculate late duration
                                val ciMins = parseTimeMins(rec.ci)
                                val officeMins = officeStartHour * 60 + officeStartMinute + gracePeriodMinutes
                                val lateMins = if (ciMins > officeMins) ciMins - officeMins else 0
                                totalLateMinsThisMonth += lateMins
                                val lateHours = lateMins / 60
                                val lateRemMins = lateMins % 60
                                val lateText = when {
                                    lateHours > 0 && lateRemMins > 0 -> "${lateHours}h ${lateRemMins}min late"
                                    lateHours > 0 -> "${lateHours}h late"
                                    lateMins > 0 -> "${lateMins}min late"
                                    else -> "Late"
                                }
                                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10,dp), 0, px(10,dp)); setBackgroundColor(if (count%2==0) Color.parseColor("#F8F8F8") else Color.WHITE) }
                                row.addView(tv(dateFmt, 13f, Color.parseColor("#333333"), bold = true).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                                val detailView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                                detailView.addView(tv("In: ${rec.ci}", 11f, Color.parseColor("#555555")))
                                detailView.addView(tv(lateText, 12f, Color.parseColor("#C62828"), bold = true))
                                row.addView(detailView)
                                container.addView(row)
                                container.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EEEEEE")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) })
                            }
                            // FIX (requested): total line at the bottom —
                            // "Total Late Hours: Xh Ym" summed across every
                            // late day this month, like a sum drawn under a
                            // column of figures. (Rupee deduction intentionally
                            // left out for now, per instruction — hours only.)
                            val totalH = totalLateMinsThisMonth / 60
                            val totalM = totalLateMinsThisMonth % 60
                            container.addView(View(this).apply {
                                setBackgroundColor(Color.parseColor("#333333"))
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(2, dp)).also { it.topMargin = px(6, dp); it.bottomMargin = px(8, dp) }
                            })
                            container.addView(tv("Total Late Hours: ${totalH}h ${totalM}m", 14f, Color.parseColor("#E65100"), bold = true).also {
                                it.setPadding(0, 0, 0, px(6, dp))
                            })
                        }
                        else -> {
                            // Score - show each day summary
                            presentDays.sorted().forEach { date ->
                                val dayRecs = allRecords.filter { it.date == date }
                                val daySt = dayRecs.firstOrNull()?.status ?: ""
                                count++
                                val dateFmt = try { val p = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date); SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(p!!) } catch (e: Exception) { date }
                                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10,dp), 0, px(10,dp)); setBackgroundColor(if (count%2==0) Color.parseColor("#F8F8F8") else Color.WHITE) }
                                row.addView(tv(dateFmt, 13f, Color.parseColor("#333333"), bold = true).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                                val badge = when(daySt) { "ON_TIME" -> "On Time"; "LATE" -> "Late"; "OVERTIME" -> "Overtime"; else -> "Present" }
                                val dColor = when(daySt) { "LATE" -> "#E65100"; "OVERTIME" -> "#1565C0"; else -> "#2E7D32" }
                                row.addView(tv(badge, 12f, Color.parseColor(dColor)))
                                container.addView(row)
                                container.addView(View(this).apply { setBackgroundColor(Color.parseColor("#EEEEEE")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1) })
                            }
                        }
                    }
                }

                if (count == 0) container.addView(tv("No records.", 13f, Color.parseColor("#9E9E9E")).also {
                    it.gravity = Gravity.CENTER; it.setPadding(0, px(20,dp), 0, px(20,dp))
                })

                val title = when(type) { "Present" -> "Present Days — $count"; "Absent" -> "Absent Days — $count"; "Late" -> "Late Arrivals — $count"; else -> "Monthly Log" }
                AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show()
            }
    }

    // ─────── HELPERS ───────

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // FIX (requested): system/gesture back press must be blocked the
        // same way the header back button is, while a forced morning
        // check-in is still pending.
        if (isForcedMorningCheckIn && sessions.isEmpty()) {
            showInfo("Please mark your attendance (Check-In) before continuing.")
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // FIX: once the fingerprint icon is disabled at night, a disabled
        // view never fires its click listener again — so the previous
        // day-rollover check inside handleAttendanceClick() could never run
        // if the app was simply left open/backgrounded overnight instead of
        // being closed and reopened. onResume() fires every time the app
        // comes back to the foreground (unlock, app switch, reopen), so this
        // is the reliable place to detect "it's a new day" and refresh
        // state — re-attaching today's listener re-enables both the button
        // and the fingerprint icon together, at the exact pre-shift time
        // configured in Office Settings (grace/pre-shift/post-shift minutes
        // are untouched and still drive that calculation exactly as before).
        val actualTodayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (actualTodayKey != todayKey) {
            todayKey = actualTodayKey
            attachAttendanceListener()
        } else {
            // Same day: re-run the state calculation in case office-close /
            // pre-shift-open time boundaries were crossed while the app was
            // in the background (e.g. screen was locked overnight but the
            // app process stayed alive).
            updateUI()
        }

        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        if (prefs.getString("approvedLeaveDate", "") == todayKey) {
            earlyLeaveApproved = true
            btnEarlyLeave.post { showApprovedButton() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        attendanceListener?.let { db.getReference("attendance").child(deviceId).child(todayKey).removeEventListener(it) }
        earlyLeaveListener?.let { if (earlyLeaveRequestKey.isNotEmpty()) db.getReference("earlyLeaveRequests").child(earlyLeaveRequestKey).removeEventListener(it) }
        officeSettingsListener?.let { db.getReference("officeSettings").removeEventListener(it) }
    }

    private fun showInfo(msg: String) { AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show() }
    private fun px(v: Int, dp: Float) = (v * dp).toInt()
    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(null, android.graphics.Typeface.BOLD) }
    private fun timeCard(label: String, bg: Int, textColor: Int, dp: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(px(14,dp), px(12,dp), px(14,dp), px(12,dp))
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 10f*dp; setColor(bg) }
        addView(tv(label, 13f, Color.parseColor("#333333")))
        addView(tv("— —", 18f, textColor, bold = true).also { it.tag = "time"; it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { m -> m.topMargin = px(4,dp) } })
    }
    private fun ring(size: Int, color: Int, stroke: Float, dp: Float) = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(size, size).also { it.gravity = Gravity.CENTER }
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((stroke*dp).toInt(), color); alpha = 150 }
    }
    private fun statBox(valView: TextView, label: String, dp: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(px(8,dp), px(14,dp), px(8,dp), px(14,dp))
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 8f*dp; setColor(Color.WHITE); setStroke(px(1,dp), Color.parseColor("#DDDDDD")) }
        isClickable = true; isFocusable = true
        setOnClickListener { showStatDetail(label) }
        addView(valView)
        addView(tv(label, 12f, Color.parseColor("#333333")).also { it.gravity = Gravity.CENTER })
    }
}