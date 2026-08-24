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

        /*
         * Employee's Resolved List:
         * show ALL resolved complaints from the CURRENT MONTH.
         *
         * This list is separate from the Dashboard Resolved Box.
         * The Box shows today's count; this list shows the month's
         * accumulated work.
         *
         * Previous months are NOT deleted from Firebase.
         */
        val monthStart =
            java.util.Calendar.getInstance().apply {
                set(
                    java.util.Calendar.DAY_OF_MONTH,
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

        val nextMonthStart =
            java.util.Calendar.getInstance().apply {
                add(
                    java.util.Calendar.MONTH,
                    1
                )
                set(
                    java.util.Calendar.DAY_OF_MONTH,
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

        val monthStartTime =
            monthStart.timeInMillis

        val nextMonthTime =
            nextMonthStart.timeInMillis

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

                            val resolvedTimeLong =
                                getResolvedTimeValue(
                                    child.child(
                                        "resolvedTime"
                                    )
                                )

                            /*
                             * Current month only.
                             */
                            if (
                                resolvedTimeLong <
                                monthStartTime ||
                                resolvedTimeLong >=
                                nextMonthTime
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
                             * Prefer the employee who actually resolved
                             * the complaint. For older records where
                             * resolvedBy is empty, use assignedTo.
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

                            if (!belongsToEmployee) {
                                continue
                            }

                            val userId =
                                child.child(
                                    "userId"
                                ).getValue(
                                    String::class.java
                                ) ?: ""

                            val address =
                                child.child(
                                    "address"
                                ).getValue(
                                    String::class.java
                                ) ?: ""

                            val phone =
                                child.child(
                                    "phoneNumber"
                                ).getValue(
                                    String::class.java
                                ) ?: ""

                            val assignedTimeLong =
                                getResolvedTimeValue(
                                    child.child(
                                        "assignedTime"
                                    )
                                )

                            val formatter =
                                java.text.SimpleDateFormat(
                                    "dd MMM yyyy / hh:mm a",
                                    java.util.Locale.getDefault()
                                )

                            val assignedTime =
                                if (
                                    assignedTimeLong > 0L
                                ) {
                                    formatter.format(
                                        java.util.Date(
                                            assignedTimeLong
                                        )
                                    )
                                } else {
                                    ""
                                }

                            val resolvedTime =
                                if (
                                    resolvedTimeLong > 0L
                                ) {
                                    formatter.format(
                                        java.util.Date(
                                            resolvedTimeLong
                                        )
                                    )
                                } else {
                                    ""
                                }

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
                                        if (
                                            resolvedBy.isNotBlank()
                                        ) {
                                            resolvedBy
                                        } else {
                                            assignedTo
                                        }
                                )
                            )
                        }

                        /*
                         * Latest resolved complaint first.
                         */
                        resolvedList.sortByDescending {
                            it.resolvedTime
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
}