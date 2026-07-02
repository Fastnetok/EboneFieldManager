package com.fastnet.ebonefieldmanager

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ComplaintAdapter(
    private val complaintList: MutableList<Complaint>
) : RecyclerView.Adapter<ComplaintAdapter.ViewHolder>() {

    class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val nameText: TextView =
            itemView.findViewById(
                R.id.nameText
            )

        val addressText: TextView =
            itemView.findViewById(
                R.id.addressText
            )

        val phoneText: TextView =
            itemView.findViewById(
                R.id.phoneText
            )

        val timeText: TextView =
            itemView.findViewById(
                R.id.timeText
            )

        val whatsappButton: ImageView =
            itemView.findViewById(
                R.id.whatsappButton
            )

        val callButton: ImageView =
            itemView.findViewById(
                R.id.callButton
            )

        val dragHandle: ImageView =
            itemView.findViewById(
                R.id.dragHandle
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater.from(
                parent.context
            ).inflate(
                R.layout.item_complaint,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val complaint =
            complaintList[position]

        holder.nameText.text =
            complaint.userId

        holder.addressText.text =
            complaint.address

        holder.phoneText.text =
            complaint.phoneNumber

        val dateFormat =
            java.text.SimpleDateFormat(
                "dd MMM yyyy / hh:mm a",
                java.util.Locale.getDefault()
            )

        holder.timeText.text =
            dateFormat.format(
                java.util.Date(
                    complaint.createdTime
                )
            )

        holder.callButton.setOnClickListener {

            val intent =
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse(
                        "tel:${complaint.phoneNumber}"
                    )
                )

            holder.itemView.context
                .startActivity(intent)
        }

        holder.whatsappButton.setOnClickListener {

            val number =
                complaint.phoneNumber
                    .replace("+", "")
                    .replace(" ", "")

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://wa.me/$number"
                    )
                )

            holder.itemView.context
                .startActivity(intent)
        }
    }

    override fun getItemCount(): Int {

        return complaintList.size
    }
}