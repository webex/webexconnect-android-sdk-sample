package com.webex.connect.demo.ui

import androidx.recyclerview.widget.DiffUtil
import com.webex.connect.inapp.message.InAppMessage

/**
 * Diff callback for the inbox items.
 */
class InboxItemDiffCallback : DiffUtil.ItemCallback<InAppMessage>() {

    override fun areItemsTheSame(oldItem: InAppMessage, newItem: InAppMessage): Boolean {
        // Compare unique IDs
        return oldItem.thread?.id == newItem.thread?.id
    }

    override fun areContentsTheSame(oldItem: InAppMessage, newItem: InAppMessage): Boolean {
        // Compare full content for equality
        return oldItem.message == newItem.message
                && oldItem.transactionId == newItem.transactionId
                && oldItem.readAt == newItem.readAt
                && oldItem.createdAt == newItem.createdAt
    }
}