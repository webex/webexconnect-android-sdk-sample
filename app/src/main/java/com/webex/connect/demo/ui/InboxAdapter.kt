package com.webex.connect.demo.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webex.connect.demo.databinding.ItemInboxBinding
import com.webex.connect.demo.util.DateUtils
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppMessageStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for the inbox.
 *
 * @param onItemClickListener The item click listener.
 */
class InboxAdapter(
    private val onItemClickListener: (position: Int, message: InAppMessage) -> Unit,
    private val onItemDisplayListener: (position: Int, message: InAppMessage) -> Unit
) :
    ListAdapter<InAppMessage, InboxAdapter.ViewHolder>(InboxItemDiffCallback()) {

    // Date format for inbox
    private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    private val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    private val todayDate: Date = DateUtils.currentDateWithoutTime

    /**
     * View holder for the inbox.
     */
    inner class ViewHolder(val binding: ItemInboxBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInboxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Get the message
        val message = getItem(position)
        onItemDisplayListener(position, message)
        // Set the message details
        message.thread?.title?.let {
            holder.binding.title.text = it
            holder.binding.initials.text = getInitials(it)
        }
        holder.binding.subtitle.text = message.message
        // Set the timestamp
        val displayDate = getDisplayDate(message)
        val timeStamp = if (displayDate == null) ""
        else if (displayDate.before(todayDate)) dateFormat.format(displayDate)
        else timeFormat.format(displayDate)
        holder.binding.timestamp.text = timeStamp
        // Set the click listener
        holder.binding.root.setOnClickListener {
            onItemClickListener(position, message)
        }
        // Set the message style based on the read status
        if (!message.isOutgoing
            && (message.readAt == null ||
                    message.status != InAppMessageStatus.READ)
        ) {
            holder.binding.title.setTypeface(null, Typeface.NORMAL)
            holder.binding.subtitle.setTypeface(null, Typeface.NORMAL)
        } else {
            holder.binding.title.setTypeface(null, Typeface.NORMAL)
            holder.binding.subtitle.setTypeface(null, Typeface.NORMAL)
        }
    }

    /**
     * Gets the initials of the input.
     *
     * @param input The input to get the initials.
     * @return The initials.
     */
    private fun getInitials(input: String): String {
        // Return empty string if input is empty
        if (input.isBlank()) {
            return ""
        }
        // Split the input into words
        val words = input.trim().split("\\s+".toRegex())
        // Get the first character of the input
        val firstChar = input.firstOrNull()?.toString() ?: ""
        // Get the first character of the second word (if it exists)
        val secondWordFirstChar = if (words.size > 1) {
            words[1].firstOrNull()?.toString() ?: ""
        } else {
            ""
        }
        // Return the initials
        return if (secondWordFirstChar.isNotEmpty()) {
            "$firstChar$secondWordFirstChar".uppercase(Locale.US)
        } else {
            firstChar.uppercase(Locale.US)
        }
    }

    /**
     * Gets the display date of the message.
     *
     * @param message The message.
     * @return The display date.
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
}
