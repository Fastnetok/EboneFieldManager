package com.fastnet.ebonefieldmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ResolvedLogAdapter(
    private val logList: List<ResolvedLog>
) : RecyclerView.Adapter<ResolvedLogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val textUserId: TextView =
            itemView.findViewById(R.id.textUserId)

        val textAddress: TextView =
            itemView.findViewById(R.id.textAddress)

        val textPhone: TextView =
            itemView.findViewById(R.id.textPhone)

        val textAssignedTime: TextView =
            itemView.findViewById(R.id.textAssignedTime)

        val textResolvedTime: TextView =
            itemView.findViewById(R.id.textResolvedTime)

        val textResolvedBy: TextView =
            itemView.findViewById(R.id.textResolvedBy)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): LogViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_resolved_log,
                    parent,
                    false
                )

        return LogViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: LogViewHolder,
        position: Int
    ) {

        val log =
            logList[position]

        holder.textUserId.text =
            log.userId

        holder.textAddress.text =
            log.address

        holder.textPhone.text =
            log.phoneNumber

        holder.textAssignedTime.text =
            "Assigned : ${log.assignedTime}"

        holder.textResolvedTime.text =
            "Resolved : ${log.resolvedTime}"

        holder.textResolvedBy.text =
            "Resolved By : ${log.resolvedBy}"
    }

    override fun getItemCount(): Int {

        return logList.size
    }
}