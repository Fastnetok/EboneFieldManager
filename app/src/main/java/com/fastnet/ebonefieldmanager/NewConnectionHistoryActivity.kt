package com.fastnet.ebonefieldmanager

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.util.*

class NewConnectionHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NewConnectionAdapter
    private val connectionList = mutableListOf<NewConnection>()
    private val db = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_connection_history)

        findViewById<ImageButton>(R.id.nchBackButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.nchRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = NewConnectionAdapter(connectionList, showActionButtons = false) { }
        recyclerView.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        val employeeName = EmployeeSession.getEmployeeName()
        val calendar = Calendar.getInstance()
        val y = java.text.SimpleDateFormat("yyyy", Locale.getDefault()).format(calendar.time)
        val m = java.text.SimpleDateFormat("MM", Locale.getDefault()).format(calendar.time)
        val d = java.text.SimpleDateFormat("dd", Locale.getDefault()).format(calendar.time)

        db.getReference("officeSettings/new_connections/completed")
            .child(y).child(m).child(d)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    connectionList.clear()
                    for (child in snapshot.children) {
                        val conn = child.getValue(NewConnection::class.java)
                        if (conn?.assignedTo?.equals(employeeName, true) == true) {
                            conn.id = child.key ?: ""
                            connectionList.add(conn)
                        }
                    }
                    connectionList.sortByDescending { it.completionTime }
                    adapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
