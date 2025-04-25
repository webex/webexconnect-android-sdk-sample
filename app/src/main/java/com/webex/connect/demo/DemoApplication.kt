package com.webex.connect.demo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import com.webex.connect.core.WebexConnect
import com.webex.connect.core.logging.ConsoleLogger
import com.webex.connect.core.logging.LogLevel
import com.webex.connect.core.logging.Logger
import com.webex.connect.demo.util.CommonUtils
import com.webex.connect.inapp.InAppMessaging
import com.webex.connect.inapp.message.db.DefaultMessageStore
import com.webex.connect.inapp.synchronization.MessageSynchronizationMode
import com.webex.connect.inapp.synchronization.MessageSynchronizationPolicy
import com.webex.connect.push.PushMessaging
import com.webex.connect.push.PushMessagingListener
import com.webex.connect.push.inappdisplay.views.InAppNotificationBannerViewBinderFactory
import com.webex.connect.push.inappdisplay.views.InAppNotificationModalViewBinderFactory
import com.webex.connect.push.notification.NotificationFactory

/**
 * Application class for the demo app.
 */
class DemoApplication : Application() {
    private lateinit var context: Context

    override fun onCreate() {
        super.onCreate()
        context = this
        // WebexConnectSDK: Set the application context for the WebexConnect instance.
        WebexConnect.applicationContext = context
        // WebexConnectSDK: Get the WebexConnect instance
        val webexConnect = WebexConnect.instance

        // WebexConnectSDK: Create a new ConsoleLogger with LogLevel.Debug.
        logger = ConsoleLogger(LogLevel.DEBUG)
        // val file = File(context.filesDir, "logging")
        // file.mkdir() // Create the subdirectory if it doesn't exist
        // WebexConnectSDK: Create a new FileLogger with LogLevel.Debug.
        // val logger = FileLogger.create(context, LogLevel.DEBUG, file.absolutePath,7)
        // WebexConnectSDK: Set the logger we just created as the logger for the webexConnect instance.
        webexConnect.setLogger(logger!!)

        // Check if the Android version is Oreo or higher. If it is, create a notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a new NotificationChannel for the push notifications.
            registerNotificationChannel()
        }

        // WebexConnectSDK:Get the PushMessaging instance
        val pushMessaging = PushMessaging.instance
        // WebexConnectSDK: Set the messaging listener for the pushMessaging instance.
        pushMessaging.registerMessagingListener(pushMessagingListener)
        // WebexConnectSDK: Get the InAppNotificationManager instance from the pushMessaging instance.
        val inAppNotificationManager = pushMessaging.inAppNotificationManager
        // WebexConnectSDK: Register the InAppBannerNotificationViewBinderFactory with the InAppNotificationManager instance.
        inAppNotificationManager.registerViewFactory(
            InAppNotificationBannerViewBinderFactory(
                context
            )
        )
        // WebexConnectSDK: Register the InAppModalNotificationViewBinderFactory with the InAppNotificationManager instance.
        inAppNotificationManager.registerViewFactory(InAppNotificationModalViewBinderFactory(context))

        // WebexConnectSDK: Get the InAppMessaging instance.
        val inAppMessaging = InAppMessaging.instance
        // WebexConnectSDK: Set the message store for the InAppMessaging instance.
        inAppMessaging.messageStore = DefaultMessageStore.create(context, "connect") // Replace "connect" with your secure password

        // WebexConnectSDK: Set the message synchronization policy to full,
        // Message sync process will be started by SDK, on the completion of InApp connection/security token change.
        inAppMessaging.messageSynchronizationPolicy =
            MessageSynchronizationPolicy(MessageSynchronizationMode.FULL)

        // WebexConnectSDK: Set the security token for the WebexConnect instance.
//         webexConnect.setSecurityToken(securityToken)
    }

    /**
     * Register a notification channel for the push notifications.
     * This is required for Android Oreo and higher.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun registerNotificationChannel() {
        val channelId = NotificationFactory.DEFAULT_CHANNEL_ID
        val channelName = getString(R.string.webex_connect_channel_name)
        val channelDescription = getString(R.string.webex_connect_channel_description)
        createChannel(context, channelId, channelName, channelDescription)
    }

    /**
     * Create a notification channel for the push notifications.
     * This is required for Android Oreo and higher.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createChannel(context: Context, id: String?, name: String?, description: String?) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(id, name, importance)
        // Configure the notification channel.
        if (!TextUtils.isEmpty(description)) {
            notificationChannel.description = description
        }
        notificationChannel.enableLights(true)
        // Sets the notification light color for notifications posted to this
        // channel, if the device supports this feature.
        notificationChannel.lightColor = Color.RED
        notificationChannel.enableVibration(true)
        notificationChannel.setVibrationPattern(
            longArrayOf(
                100,
                200,
                300,
                400,
                500,
                400,
                300,
                200,
                400
            )
        )
        // Register the channel with the system
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
    }

    /**
     * WebexConnectSDK: Create a new PushMessagingListener.
     * This listener will be called when a push message is received.
     */
    private val pushMessagingListener: PushMessagingListener = { message ->
        // Log the push message
        CommonUtils.log(TAG, getString(R.string.received_push_message, message.toJson()))
    }

    /**
     * Companion object to get the logger.
     */
    internal companion object {
        private const val TAG = "DemoApplication"
        var logger: Logger? = null
    }
}