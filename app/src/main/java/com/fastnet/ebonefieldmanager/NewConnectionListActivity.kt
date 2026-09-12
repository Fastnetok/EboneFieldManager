package com.fastnet.ebonefieldmanager

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.util.*

class NewConnectionListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NewConnectionAdapter
    private val connectionList = mutableListOf<NewConnection>()
    private val db = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_connection_list)

        findViewById<ImageButton>(R.id.nclBackButton).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.nclRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NewConnectionAdapter(connectionList, showActionButtons = false) { }
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                Collections.swap(connectionList, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                saveDisplayOrder()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        loadPendingConnections()
    }

    private fun loadPendingConnections() {
        val employeeName = EmployeeSession.getEmployeeName()
        db.getReference("officeSettings/new_connections/gift_box")
            .child(employeeName)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    connectionList.clear()
                    for (child in snapshot.children) {
                        child.getValue(NewConnection::class.java)?.let {
                            it.id = child.key ?: ""
                            connectionList.add(it)
                        }
                    }
                    connectionList.sortBy { it.displayOrder }
                    adapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun saveDisplayOrder() {
        val employeeName = EmployeeSession.getEmployeeName()
        val ref = db.getReference("officeSettings/new_connections/gift_box").child(employeeName)
        connectionList.forEachIndexed { index, conn ->
            conn.displayOrder = index.toLong()
            ref.child(conn.id).child("displayOrder").setValue(conn.displayOrder)
        }
    }
}