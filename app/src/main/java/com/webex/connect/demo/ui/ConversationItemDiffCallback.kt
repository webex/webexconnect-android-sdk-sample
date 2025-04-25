package com.webex.connect.demo.ui

import androidx.recyclerview.widget.DiffUtil
import com.webex.connect.demo.data.MessageItem

/**
 * Diff callback for the conversation items.
 */
class ConversationItemDiffCallback : DiffUtil.ItemCallback<MessageItem>() {

    override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
        // Compare unique IDs
        return oldItem.isHeader == newItem.isHeader
                && oldItem.date == newItem.date
                && oldItem.message?.transactionId == newItem.message?.transactionId
    }

    override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean {
        // Compare full content for equality
        return oldItem.message?.message == newItem.message?.message
                && oldItem.isHeader == newItem.isHeader
                && oldItem.date == newItem.date
                && oldItem.message?.status == newItem.message?.status
                && oldItem.message?.readAt == newItem.message?.readAt
                && oldItem.message?.transactionId == newItem.message?.transactionId
    }
}