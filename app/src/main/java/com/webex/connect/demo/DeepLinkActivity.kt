package com.webex.connect.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.webex.connect.core.WebexConnect

/**
 * Activity to handle deep links
 */
class DeepLinkActivity : AppCompatActivity() {
    private val logTag = "DeepLinkActivity"

    //    webexconnect://command/messaging
//    webexconnect://command/notifications
//    webexconnect://command/logout
//    webexconnect://command/openNavbar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WebexConnectSDK: Get the WebexConnect instance
        val webexConnect = WebexConnect.instance
        // WebexConnectSDK: Check if the user is registered
        if (webexConnect.isRegistered) {
            // Get the deep link intent
            intent.data?.let { uri ->
                // Get the action from the deep link
                val action = uri.path
                // Check the action
                if (action.isNullOrBlank()) {
                    // Log the error
                    Log.e(logTag, getString(R.string.unknown_deep_link, uri.toString()))
                } else {
                    // Start the HomeActivity with the deep link action
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setData(uri)
                    startActivity(intent)
                }
            } ?: run {
                // Log the error
                Log.e(logTag, getString(R.string.unable_to_find_deep_link))
            }
        } else {
            // Log the error
            Log.e(logTag, getString(R.string.user_not_registered))
            // Start the LoginActivity if the user is not registered
            LoginActivity.start(this)
        }
        // Finish the activity
        finish()
    }

    /**
     * Companion object to hold the deep link actions
     */
    companion object {
        // Deep link actions
        const val DEEPLINK_ACTION_MESSAGING = "/messaging"
        const val DEEPLINK_ACTION_NOTIFICATIONS = "/notifications"
        const val DEEPLINK_ACTION_LOGOUT = "/logout"
        const val DEEPLINK_ACTION_OPEN_NAVBAR = "/openNavbar"
    }
}