package com.fastnet.ebonefieldmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import java.util.*

class NewConnectionActivity : AppCompatActivity() {

    private lateinit var pendingCountText: TextView
    private lateinit var installedCountText: TextView
    private lateinit var customerNameText: TextView
    private lateinit var addressText: TextView
    private lateinit var phoneText: TextView
    private lateinit var commentsText: TextView

    private lateinit var callButton: Button
    private lateinit var whatsappButton: Button
    private lateinit var completeButton: Button
    private var giftBoxContainer: FrameLayout? = null

    private var activeConnection: NewConnection? = null
    private val db = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_connection)

        initViews()
        setupListeners()
        loadDashboardData()
    }

    private fun initViews() {
        pendingCountText = findViewById(R.id.ncPendingCountText)
        installedCountText = findViewById(R.id.ncInstalledCountText)
        customerNameText = findViewById(R.id.ncCustomerNameText)
        addressText = findViewById(R.id.ncAddressText)
        phoneText = findViewById(R.id.ncPhoneText)
        commentsText = findViewById(R.id.ncCommentsText)

        callButton = findViewById(R.id.ncCallButton)
        whatsappButton = findViewById(R.id.ncWhatsappButton)
        completeButton = findViewById(R.id.ncCompleteButton)
        giftBoxContainer = findViewById(R.id.ncGiftBoxContainer)

        findViewById<ImageButton>(R.id.ncBackButton).setOnClickListener { finish() }

        // Setup Boxes with clear click listeners
        val pendingBox = findViewById<LinearLayout>(R.id.ncPendingBox)
        val installedBox = findViewById<LinearLayout>(R.id.ncInstalledBox)

        pendingBox.setOnClickListener {
            val intent = Intent(this, NewConnectionListActivity::class.java)
            startActivity(intent)
        }

        installedBox.setOnClickListener {
            val intent = Intent(this, NewConnectionHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupListeners() {
        callButton.setOnClickListener {
            activeConnection?.phoneNumber?.let {
                if (it.isNotEmpty()) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")))
                }
            }
        }

        whatsappButton.setOnClickListener {
            activeConnection?.phoneNumber?.let {
                if (it.isNotEmpty()) {
                    val number = it.replaceFirst("0", "92").replace("+", "")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")))
                }
            }
        }

        completeButton.setOnClickListener {
            activeConnection?.let { markInstallSuccessful(it) }
        }
    }

    private fun loadDashboardData() {
        val employeeName = EmployeeSession.getEmployeeName()
        if (employeeName.isEmpty()) return

        // 1. Listen for Pending Connections (Gift Box)
        db.getReference("officeSettings/new_connections/gift_box")
            .child(employeeName)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connections = mutableListOf<NewConnection>()
                    for (child in snapshot.children) {
                        child.getValue(NewConnection::class.java)?.let {
                            it.id = child.key ?: ""
                            connections.add(it)
                        }
                    }

                    connections.sortBy { it.displayOrder }

                    pendingCountText.text = connections.size.toString()
                    updateNoteDisplay(giftBoxContainer, connections.size)

                    if (connections.isNotEmpty()) {
                        updateActiveCard(connections[0])
                    } else {
                        updateActiveCard(null)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // 2. Listen for Installed Today (Completed)
        val calendar = Calendar.getInstance()
        val y = java.text.SimpleDateFormat("yyyy", Locale.getDefault()).format(calendar.time)
        val m = java.text.SimpleDateFormat("MM", Locale.getDefault()).format(calendar.time)
        val d = java.text.SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)

        db.getReference("officeSettings/new_connections/completed")
            .child(y).child(m).child(d)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var todayCount = 0
                    for (child in snapshot.children) {
                        val conn = child.getValue(NewConnection::class.java)
                        if (conn?.assignedTo?.equals(employeeName, true) == true) {
                            todayCount++
                        }
                    }
                    installedCountText.text = todayCount.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateActiveCard(connection: NewConnection?) {
        activeConnection = connection
        if (connection != null) {
            customerNameText.text = connection.customerName
            addressText.text = connection.address
            phoneText.text = connection.phoneNumber
            if (connection.comments.isNotEmpty()) {
                commentsText.visibility = View.VISIBLE
                commentsText.text = connection.comments
            } else {
                commentsText.visibility = View.GONE
            }
            toggleButtons(true)

            // SEEN STATUS — mark as seen as soon as it appears on Dashboard
            if (!connection.seenByEmployee) {
                val employeeName = EmployeeSession.getEmployeeName()
                val updates = mapOf(
                    "seenByEmployee" to true,
                    "seenTime" to ServerValue.TIMESTAMP
                )
                db.getReference("officeSettings/new_connections/gift_box")
                    .child(employeeName).child(connection.id)
                    .updateChildren(updates)
            }
        } else {
            customerNameText.text = "No Connection"
            addressText.text = "-"
            phoneText.text = "-"
            commentsText.visibility = View.GONE
            toggleButtons(false)
        }
    }

    private fun toggleButtons(enabled: Boolean) {
        callButton.isEnabled = enabled
        whatsappButton.isEnabled = enabled
        completeButton.isEnabled = enabled
        val alpha = if (enabled) 1.0f else 0.5f
        callButton.alpha = alpha
        whatsappButton.alpha = alpha
        completeButton.alpha = alpha
    }

    private fun markInstallSuccessful(connection: NewConnection) {
        val employeeName = EmployeeSession.getEmployeeName()
        connection.status = "Completed"
        connection.completionTime = System.currentTimeMillis()

        val calendar = Calendar.getInstance()
        val y = java.text.SimpleDateFormat("yyyy", Locale.getDefault()).format(calendar.time)
        val m = java.text.SimpleDateFormat("MM", Locale.getDefault()).format(calendar.time)
        val d = java.text.SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)

        db.getReference("officeSettings/new_connections/completed")
            .child(y).child(m).child(d)
            .child(connection.id)
            .setValue(connection)
            .addOnSuccessListener {
                db.getReference("officeSettings/new_connections/gift_box")
                    .child(employeeName).child(connection.id).removeValue()

                db.getReference("adminNotifications").push().setValue(mapOf(
                    "message" to "🎁 $employeeName ne New Connection install kar diya — Customer: ${connection.customerName}",
                    "connectionId" to connection.id,
                    "installedBy" to employeeName,
                    "customerName" to connection.customerName,
                    "timestamp" to System.currentTimeMillis(),
                    "seen" to false
                ))
            }
    }

    private fun updateNoteDisplay(container: FrameLayout?, count: Int) {
        container?.let {
            it.removeAllViews()
            if (count == 0) return
            
            val inflater = LayoutInflater.from(this)
            val density = resources.displayMetrics.density
            
            // Back Wallet
            val backWallet = View(this)
            backWallet.layoutParams = FrameLayout.LayoutParams((70 * density).toInt(), (40 * density).toInt()).apply {
                gravity = Gravity.CENTER
            }
            backWallet.setBackgroundResource(R.drawable.bg_wallet_back)
            it.addView(backWallet)

            val maxNotes = if (count > 5) 5 else count
            for (i in 0 until maxNotes) {
                val noteView = inflater.inflate(R.layout.item_rs_100_note, it, false)
                val params = FrameLayout.LayoutParams((55 * density).toInt(), (28 * density).toInt())
                
                params.gravity = Gravity.CENTER
                params.bottomMargin = (15 * density).toInt() + (i * 5 * density).toInt()
                params.leftMargin = (i * 6 * density).toInt() - (10 * density).toInt()
                
                noteView.rotation = (-10 + (i * 5)).toFloat()
                noteView.layoutParams = params
                it.addView(noteView)
            }

            // Front Wallet
            val frontWallet = View(this)
            frontWallet.layoutParams = FrameLayout.LayoutParams((70 * density).toInt(), (30 * density).toInt()).apply {
                gravity = Gravity.CENTER
            }
            frontWallet.setBackgroundResource(R.drawable.bg_wallet_front)
            it.addView(frontWallet)
        }
    }
}
