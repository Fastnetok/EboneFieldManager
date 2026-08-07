package com.fastnet.ebonefieldmanager

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
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

        resolvedCountText.setOnClickListener {
            startActivity(Intent(this, ResolvedLogActivity::class.java))
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

        com.google.firebase.database.FirebaseDatabase
            .getInstance()
            .getReference("complaints")
            .addValueEventListener(
                object : com.google.firebase.database.ValueEventListener {

                    override fun onDataChange(
                        snapshot: com.google.firebase.database.DataSnapshot
                    ) {
                        val employeeComplaints = mutableListOf<Complaint>()
                        var pendingCount = 0
                        var resolvedCount = 0

                        for (child in snapshot.children) {
                            val complaint = child.getValue(Complaint::class.java)
                            if (complaint != null &&
                                complaint.assignedTo.equals(employeeName, true)
                            ) {
                                if (complaint.status.equals("Resolved", true)) {
                                    resolvedCount++
                                } else {
                                    pendingCount++
                                    employeeComplaints.add(complaint)
                                }
                            }
                        }

                        employeeComplaints.sortBy { it.displayOrder }
                        pendingCountText.text = pendingCount.toString()
                        resolvedCountText.text = resolvedCount.toString()

                        if (employeeComplaints.isNotEmpty()) {
                            val complaint = employeeComplaints[0]
                            currentComplaint = complaint
                            customerNameText.text = complaint.userId
                            customerAddressText.text = complaint.address
                            customerPhoneText.text = complaint.phoneNumber

                            // Active Complaint ab Dashboard par visible hai — "seen" mark karne ki koshish karo
                            markCurrentComplaintSeen()
                        } else {
                            currentComplaint = null
                            customerNameText.text = "No Complaint"
                            customerAddressText.text = "-"
                            customerPhoneText.text = "-"
                        }
                    }

                    override fun onCancelled(
                        error: com.google.firebase.database.DatabaseError
                    ) {}
                }
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