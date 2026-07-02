package com.fastnet.ebonefieldmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class ResolvedLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: ResolvedLogAdapter

    private val resolvedList =
        mutableListOf<ResolvedLog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_resolved_log
        )

        recyclerView =
            findViewById(
                R.id.recyclerViewResolvedLog
            )

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        adapter =
            ResolvedLogAdapter(
                resolvedList
            )

        recyclerView.adapter =
            adapter

        loadResolvedHistory()
    }

    private fun loadResolvedHistory() {

        val employeeName =
            EmployeeSession.getEmployeeName()

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

                        resolvedList.clear()

                        for (child in snapshot.children) {

                            val assignedTo =
                                child.child("assignedTo")
                                    .getValue(String::class.java)
                                    ?: ""

                            if (
                                !assignedTo.equals(
                                    employeeName,
                                    true
                                )
                            ) {
                                continue
                            }

                            val userId =
                                child.child("userId")
                                    .getValue(String::class.java)
                                    ?: ""

                            val address =
                                child.child("address")
                                    .getValue(String::class.java)
                                    ?: ""

                            val phone =
                                child.child("phoneNumber")
                                    .getValue(String::class.java)
                                    ?: ""

                            val resolvedBy =
                                child.child("resolvedBy")
                                    .getValue(String::class.java)
                                    ?: ""

                            val assignedTimeLong =
                                child.child("assignedTime")
                                    .getValue(Long::class.java)
                                    ?: 0L

                            val resolvedTimeLong =
                                child.child("resolvedTime")
                                    .getValue(Long::class.java)
                                    ?: 0L

                            val formatter =
                                java.text.SimpleDateFormat(
                                    "dd MMM yyyy / hh:mm a",
                                    java.util.Locale.getDefault()
                                )

                            val assignedTime =
                                formatter.format(
                                    java.util.Date(
                                        assignedTimeLong
                                    )
                                )

                            val resolvedTime =
                                formatter.format(
                                    java.util.Date(
                                        resolvedTimeLong
                                    )
                                )

                            resolvedList.add(

                                ResolvedLog(

                                    userId =
                                        userId,

                                    address =
                                        address,

                                    phoneNumber =
                                        phone,

                                    assignedTime =
                                        assignedTime,

                                    resolvedTime =
                                        resolvedTime,

                                    resolvedBy =
                                        resolvedBy
                                )

                            )

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