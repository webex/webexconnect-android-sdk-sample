package com.webex.connect.demo.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for date operations.
 */
object DateUtils {

    // Date format for chat header
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)

    /**
     * Gets the current date without the time.
     */
    val currentDateWithoutTime: Date
        get() {
            // Get a Calendar instance and set it to the current date
            val calendar = Calendar.getInstance()
            calendar.time = Date()
            // Set the hour, minute, second, and millisecond fields to zero
            calendar[Calendar.HOUR_OF_DAY] = 0
            calendar[Calendar.MINUTE] = 0
            calendar[Calendar.SECOND] = 0
            calendar[Calendar.MILLISECOND] = 0
            // Return the modified date
            return calendar.time
        }

    /**
     * Formats the date for chat header.
     *
     * @param date The date to format.
     * @return The formatted date.
     */
    fun formatDateForChatHeader(date: Date): String {
        val targetCalendar = Calendar.getInstance().apply { time = date }

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(today, targetCalendar) -> "Today"
            isSameDay(yesterday, targetCalendar) -> "Yesterday, ${dateFormat.format(date)}"
            else -> SimpleDateFormat("EEEE, dd/MM/yy", Locale.US).format(date)
        }
    }

    /**
     * Formats the date for chat item.
     *
     * @param date The date to format.
     * @return The formatted date.
     */
    fun formatDateForChatItem(date: Date): String {
        val targetCalendar = Calendar.getInstance().apply { time = date }

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(today, targetCalendar) -> getFormattedTime(date)
            isSameDay(yesterday, targetCalendar) -> "Yesterday, ${getFormattedTime(date)}"
            isWithinLastWeek(targetCalendar) -> SimpleDateFormat(
                "EEEE, dd/MM/yy ",
                Locale.US
            ).format(date) + getFormattedTime(date)

            else -> "${dateFormat.format(date)}, ${getFormattedTime(date)}"
        }
    }

    /**
     * Formats the time.
     *
     * @param date The date to format.
     * @return The formatted time.
     */
    private fun getFormattedTime(date: Date): String {
        return timeFormat.format(date).uppercase(Locale.US)
    }

    /**
     * Checks if two dates are on the same day.
     *
     * @param date1 The first date.
     * @param date2 The second date.
     * @return True if the dates are on the same day, false otherwise.
     */
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Checks if two dates are on the same day.
     *
     * @param date1 The first date.
     * @param date2 The second date.
     * @return True if the dates are on the same day, false otherwise.
     */
    fun isSameDay(date1: Date, date2: Date): Boolean {
        val calendar1 = Calendar.getInstance().apply { time = date1 }
        val calendar2 = Calendar.getInstance().apply { time = date2 }
        return isSameDay(calendar1, calendar2)
    }

    /**
     * Checks if the date is within the last week.
     *
     * @param calendar The date to check.
     * @return True if the date is within the last week, false otherwise.
     */
    private fun isWithinLastWeek(calendar: Calendar): Boolean {
        val today = Calendar.getInstance()
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        return calendar.after(sevenDaysAgo) && !isSameDay(today, calendar)
    }
}
