package com.fastnet.ebonefieldmanager

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NewConnectionAdapter(
    private val connectionList: MutableList<NewConnection>,
    private val showActionButtons: Boolean = true,
    private val onInstallSuccessful: (NewConnection) -> Unit
) : RecyclerView.Adapter<NewConnectionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerNameText: TextView = itemView.findViewById(R.id.ncCustomerNameText)
        val addressText: TextView = itemView.findViewById(R.id.ncAddressText)
        val phoneText: TextView = itemView.findViewById(R.id.ncPhoneText)
        val commentsText: TextView = itemView.findViewById(R.id.ncCommentsText)
        val installButton: Button = itemView.findViewById(R.id.ncInstallButton)
        val actionButtonsContainer: View = itemView.findViewById(R.id.ncActionButtons)

        // NEW: same Call/WhatsApp icons as ComplaintAdapter's item_complaint.xml
        val whatsappButton: ImageView = itemView.findViewById(R.id.ncWhatsappButton)
        val callButton: ImageView = itemView.findViewById(R.id.ncCallButton)
        val dragHandle: ImageView = itemView.findViewById(R.id.ncDragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_new_connection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val connection = connectionList[position]

        holder.customerNameText.text = connection.customerName
        holder.addressText.text = connection.address
        holder.phoneText.text = connection.phoneNumber

        if (connection.comments.isNotEmpty()) {
            holder.commentsText.visibility = View.VISIBLE
            holder.commentsText.text = connection.comments
        } else {
            holder.commentsText.visibility = View.GONE
        }

        if (showActionButtons) {
            holder.actionButtonsContainer.visibility = View.VISIBLE
            holder.installButton.setOnClickListener { onInstallSuccessful(connection) }
        } else {
            holder.actionButtonsContainer.visibility = View.GONE
        }

        // NEW: same Call/WhatsApp behaviour as ComplaintAdapter.kt
        holder.callButton.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:${connection.phoneNumber}")
            )
            holder.itemView.context.startActivity(intent)
        }

        holder.whatsappButton.setOnClickListener {
            val number = connection.phoneNumber
                .replace("+", "")
                .replace(" ", "")

            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$number")
            )
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return connectionList.size
    }
}