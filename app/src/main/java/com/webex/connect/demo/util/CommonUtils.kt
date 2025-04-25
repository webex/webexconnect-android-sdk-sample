package com.webex.connect.demo.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.webex.connect.core.logging.LogLevel
import com.webex.connect.demo.DemoApplication
import com.webex.connect.demo.R

/**
 * Utility class for common functions.
 */
object CommonUtils {
    // Progress dialog
    private var progressDialog: AlertDialog? = null

    /**
     * Logs a message.
     *
     * @param tag The tag for the log message.
     * @param message The log message.
     * @param level The log level.
     * @param throwable The throwable.
     */
    fun log(
        tag: String,
        message: String? = "",
        level: LogLevel = LogLevel.DEBUG,
        throwable: Throwable? = null
    ) {
        // Log the message
        DemoApplication.logger?.log(level, tag, message, throwable)
    }

    /**
     * Shows a toast message.
     *
     * @param context The context.
     * @param message The message to show.
     * @param isLong A flag indicating whether the toast message should be long.
     */
    fun showToast(context: Context, message: String, isLong: Boolean = false) {
        Toast.makeText(context, message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            .show()
    }

    /**
     * Shows a progress dialog.
     *
     * @param context The context.
     * @param title The title of the dialog.
     * @param message The message of the dialog.
     * @param cancelable A flag indicating whether the dialog is cancelable.
     */
    fun showProgressDialog(
        context: Context,
        title: String = "",
        message: String = "",
        cancelable: Boolean = false
    ) {
        dismissProgressDialog()
        if (progressDialog == null) {
            val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
            builder.setTitle(title)
            builder.setMessage(message)
            builder.setView(R.layout.progress_dialog)
            builder.setCancelable(cancelable)
            progressDialog = builder.create()
            progressDialog?.show()
        }
    }

    /**
     * Dismisses the progress dialog.
     */
    fun dismissProgressDialog() {
        progressDialog?.cancel()
        progressDialog = null
    }

    /**
     * Hides the keyboard.
     *
     * @param activity The activity.
     */
    fun hideKeyboard(activity: Activity?) {
        activity?.let {
            val view = it.currentFocus
            if (view != null) {
                val imm = it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }
}