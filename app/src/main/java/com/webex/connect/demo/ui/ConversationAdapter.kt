package com.webex.connect.demo.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webex.connect.demo.R
import com.webex.connect.demo.data.MessageItem
import com.webex.connect.demo.databinding.ItemConversationBinding
import com.webex.connect.demo.databinding.ItemConversationHeaderBinding
import com.webex.connect.demo.util.DateUtils
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppMessageStatus
import java.util.Date

/**
 * Adapter for the conversation.
 *
 * @param onItemClickListener The item click listener.
 * @param onUnreadItemDisplayedListener The unread item displayed listener.
 */
class ConversationAdapter(
    private val onItemClickListener: (position: Int, message: InAppMessage) -> Unit,
    private val onUnreadItemDisplayedListener: (position: Int, message: InAppMessage) -> Unit
) :
    ListAdapter<MessageItem, RecyclerView.ViewHolder>(ConversationItemDiffCallback()) {

    /**
     * Header view holder holder for the chat.
     */
    inner class HeaderViewHolder(private val binding: ItemConversationHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: Date) {
            // Set the date
            binding.title.text = DateUtils.formatDateForChatHeader(date)
        }
    }

    /**
     * Message view holder for the chat.
     */
    inner class MessageViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int, message: InAppMessage) {
            // Set the message
            val senderName = if (message.isOutgoing) "You" else "Agent"
            // Set the message details
            binding.initials.setImageResource(if (message.isOutgoing) R.drawable.avatar_you else R.drawable.avatar_agent)
            binding.message.text = message.message
            // Set the sender and timestamp
            val displayDate = getDisplayDate(message)
            val formattedDate =
                if (displayDate != null) DateUtils.formatDateForChatItem(displayDate)
                else ""
            // Set the sender and timestamp
            binding.sender.text = senderName
            binding.timestamp.text = formattedDate
            // Set the click listener
            binding.root.setOnClickListener {
                onItemClickListener(position, message)
            }
            // Set the message style based on the read status
            if (!message.isOutgoing
                && (message.readAt == null ||
                        message.status != InAppMessageStatus.READ)
            ) {
                binding.message.setTypeface(null, Typeface.NORMAL)
                onUnreadItemDisplayedListener.invoke(position, message)
            } else {
                binding.message.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isHeader) {
            TYPE_DATE_HEADER
        } else {
            TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                HeaderViewHolder(
                    ItemConversationHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }

            else -> {
                MessageViewHolder(
                    ItemConversationBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> {
                holder.bind(item.date)
            }

            is MessageViewHolder -> {
                holder.bind(position, item.message ?: InAppMessage())
            }
        }
    }

    /**
     * Gets the display date for the message.
     */
    private fun getDisplayDate(message: InAppMessage): Date? {
        var date = message.submittedAt
        if (date != null) {
            return date
        }
        val thread = message.thread
        if (thread != null) {
            val updatedAt = thread.updatedAt
            date = updatedAt ?: thread.createdAt
        }
        return date
    }

    /**
     * Companion object for the [ConversationAdapter] class.
     */
    companion object {
        private const val TYPE_MESSAGE = 0
        private const val TYPE_DATE_HEADER = 1
    }
}