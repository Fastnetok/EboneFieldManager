package com.fastnet.ebonefieldmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class ComplaintListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: ComplaintAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_complaint_list
        )

        recyclerView =
            findViewById(
                R.id.recyclerView
            )

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        adapter =
            ComplaintAdapter(
                ComplaintManager.complaintList
            )

        recyclerView.adapter =
            adapter

        val itemTouchHelper =
            ItemTouchHelper(

                object :
                    ItemTouchHelper.SimpleCallback(

                        ItemTouchHelper.UP or
                                ItemTouchHelper.DOWN,

                        0
                    ) {

                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder
                    ): Boolean {

                        val from =
                            viewHolder.adapterPosition

                        val to =
                            target.adapterPosition

                        java.util.Collections.swap(
                            ComplaintManager.complaintList,
                            from,
                            to
                        )

                        adapter.notifyItemMoved(
                            from,
                            to
                        )

                        // saveDisplayOrder()

                        return true
                    }

                    override fun onSwiped(
                        viewHolder: RecyclerView.ViewHolder,
                        direction: Int
                    ) {
                    }
                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder
                    ) {

                        super.clearView(
                            recyclerView,
                            viewHolder
                        )

                        saveDisplayOrder()
                    }
                }
            )

        itemTouchHelper.attachToRecyclerView(
            recyclerView
        )

        loadAssignedComplaints()
    }

    private fun saveDisplayOrder() {

        val ref =
            FirebaseDatabase
                .getInstance()
                .getReference(
                    "complaints"
                )

        ComplaintManager
            .complaintList
            .forEachIndexed { index, complaint ->

                complaint.displayOrder =
                    index.toLong()

                ref.child(
                    complaint.complaintId
                )
                    .child(
                        "displayOrder"
                    )
                    .setValue(
                        complaint.displayOrder
                    )
            }
    }

    private fun loadAssignedComplaints() {

        val employeeName =
            EmployeeSession.getEmployeeName()

        FirebaseDatabase
            .getInstance()
            .getReference(
                "complaints"
            )
            .addValueEventListener(

                object : ValueEventListener {

                    override fun onDataChange(
                        snapshot: DataSnapshot
                    ) {

                        ComplaintManager
                            .complaintList
                            .clear()

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
                                ) &&
                                !complaint.status.equals(
                                    "Resolved",
                                    true
                                )
                            ) {

                                ComplaintManager
                                    .complaintList
                                    .add(
                                        complaint
                                    )
                            }
                        }

                        ComplaintManager
                            .complaintList
                            .sortBy {
                                it.displayOrder
                            }

                        adapter.notifyDataSetChanged()
                    }

                    override fun onCancelled(
                        error: DatabaseError
                    ) {
                    }
                }
            )
    }
}