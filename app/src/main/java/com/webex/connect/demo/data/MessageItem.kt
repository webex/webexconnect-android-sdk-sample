package com.webex.connect.demo.data

import com.webex.connect.inapp.message.InAppMessage
import java.util.Date

/**
 * Represents a chat item.
 *
 * @param date The date of the chat item.
 * @param isHeader A flag indicating whether the chat item is a header.
 * @param message The in-app message associated with the chat item.
 */
data class MessageItem(
    val date: Date,
    val isHeader: Boolean,
    val message: InAppMessage? = null
)
