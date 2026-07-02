package com.fastnet.ebonefieldmanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ComplaintManager {

    val complaintList =
        mutableListOf<Complaint>()

    val resolvedList =
        mutableListOf<Complaint>()

    val resolvedLogList =
        mutableListOf<ResolvedLog>()

    // FILES

    private const val PENDING_FILE =
        "pending_complaints.json"

    private const val RESOLVED_FILE =
        "resolved_complaints.json"

    private const val RESOLVED_LOG_FILE =
        "resolved_logs.json"

    // LOAD SYSTEM

    fun loadComplaints(
        context: Context
    ) {

        complaintList.clear()

        resolvedList.clear()

        resolvedLogList.clear()

        // LOAD PENDING

        val pendingFile =
            File(
                context.filesDir,
                PENDING_FILE
            )

        if (pendingFile.exists()) {

            val data =
                pendingFile.readText()

            val jsonArray =
                JSONArray(data)

            for (i in 0 until jsonArray.length()) {

                val obj =
                    jsonArray.getJSONObject(i)

                complaintList.add(

                    Complaint(

                        userId =
                            obj.getString("userId"),

                        address =
                            obj.getString("address"),

                        phoneNumber =
                            obj.getString("phoneNumber")

                    )

                )

            }

        } else {

        }
        // LOAD RESOLVED

        val resolvedFile =
            File(
                context.filesDir,
                RESOLVED_FILE
            )

        if (resolvedFile.exists()) {

            val data =
                resolvedFile.readText()

            val jsonArray =
                JSONArray(data)

            for (i in 0 until jsonArray.length()) {

                val obj =
                    jsonArray.getJSONObject(i)

                resolvedList.add(

                    Complaint(
                        userId =
                            obj.getString("userId"),

                        address =
                            obj.getString("address"),

                        phoneNumber =
                            obj.getString("phoneNumber")


                    )

                )

            }

        }

        // LOAD RESOLVED LOGS

        val resolvedLogFile =
            File(
                context.filesDir,
                RESOLVED_LOG_FILE
            )

        if (resolvedLogFile.exists()) {

            val data =
                resolvedLogFile.readText()

            val jsonArray =
                JSONArray(data)

            for (i in 0 until jsonArray.length()) {

                val obj =
                    jsonArray.getJSONObject(i)

                resolvedLogList.add(

                    ResolvedLog(

                        userId =
                            obj.getString("userId"),

                        address =
                            obj.getString("address"),

                        phoneNumber =
                            obj.getString("phoneNumber"),

                        resolvedTime =
                            obj.getString("resolvedTime")

                    )

                )

            }

        }

    }

    // SAVE PENDING

    fun saveComplaints(
        context: Context
    ) {

        val jsonArray =
            JSONArray()

        complaintList.forEach {

            val obj =
                JSONObject()

            obj.put(
                "userId",
                it.userId
            )

            obj.put(
                "address",
                it.address
            )

            obj.put(
                "phoneNumber",
                it.phoneNumber
            )

            jsonArray.put(obj)

        }

        val file =
            File(
                context.filesDir,
                PENDING_FILE
            )

        file.writeText(
            jsonArray.toString()
        )

    }

    // SAVE RESOLVED

    fun saveResolved(
        context: Context
    ) {

        val jsonArray =
            JSONArray()

        resolvedList.forEach {

            val obj =
                JSONObject()

            obj.put(
                "userId",
                it.userId
            )

            obj.put(
                "address",
                it.address
            )

            obj.put(
                "phoneNumber",
                it.phoneNumber
            )

            jsonArray.put(obj)

        }

        val file =
            File(
                context.filesDir,
                RESOLVED_FILE
            )

        file.writeText(
            jsonArray.toString()
        )

    }

    // SAVE RESOLVED LOGS

    fun saveResolvedLogs(
        context: Context
    ) {

        val jsonArray =
            JSONArray()

        resolvedLogList.forEach {

            val obj =
                JSONObject()

            obj.put(
                "userId",
                it.userId
            )

            obj.put(
                "address",
                it.address
            )

            obj.put(
                "phoneNumber",
                it.phoneNumber
            )

            obj.put(
                "resolvedTime",
                it.resolvedTime
            )

            jsonArray.put(obj)

        }

        val file =
            File(
                context.filesDir,
                RESOLVED_LOG_FILE
            )

        file.writeText(
            jsonArray.toString()
        )

    }

    // ADD LOG

    fun addResolvedLog(
        complaint: Complaint
    ) {

        val currentTime =
            java.text.SimpleDateFormat(
                "dd MMM yyyy / hh:mm a",
                java.util.Locale.getDefault()
            ).format(
                java.util.Date()
            )

        resolvedLogList.add(

            ResolvedLog(

                userId =
                    complaint.userId,

                address =
                    complaint.address,

                phoneNumber =
                    complaint.phoneNumber,

                resolvedTime =
                    currentTime

            )

        )

    }

}