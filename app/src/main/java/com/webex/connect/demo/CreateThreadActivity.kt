package com.webex.connect.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.webex.connect.core.errorhandling.WebexConnectException
import com.webex.connect.demo.data.InboxDataManager
import com.webex.connect.demo.databinding.ActivityCreateThreadBinding
import com.webex.connect.demo.util.CommonUtils
import com.webex.connect.inapp.CreateThreadCallback
import com.webex.connect.inapp.InAppMessaging
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppThread

/**
 * Activity to create a new thread
 */
class CreateThreadActivity : AppCompatActivity() {
    private lateinit var context: Context
    private lateinit var binding: ActivityCreateThreadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        binding = ActivityCreateThreadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Cancel button click listener
        binding.cancelButton.setOnClickListener {
            // Finish the activity
            finish()
        }
        // Create button click listener
        binding.createButton.setOnClickListener {
            // Get the thread name from the text input
            val threadName = binding.textInput.text.toString()
            // Check if the thread name is blank
            if (threadName.isBlank()) {
                // Show an error message
                binding.textInput.error = getString(R.string.please_enter_valid_thread_name)
            } else {
                // Hide the keyboard
                CommonUtils.hideKeyboard(this)
                // Show a progress dialog
                CommonUtils.showProgressDialog(context, getString(R.string.creating_thread))
                // Disable the text input and create button
                binding.textInput.isEnabled = false
                binding.createButton.isEnabled = false
                // WebexConnectSDK: Create a new InAppThread
                val thread = InAppThread().apply {
                    title = threadName
                }
                // WebexConnectSDK: Get the InAppMessaging instance
                val inAppMessaging = InAppMessaging.instance
                // WebexConnectSDK: Create a new thread
                // CreateThreadCallback is a callback that is invoked when the thread is created.
                inAppMessaging.createThread(thread, object : CreateThreadCallback {
                    override fun invoke(thread: InAppThread?, exception: WebexConnectException?) {
                        // Dismiss the progress dialog
                        CommonUtils.dismissProgressDialog()
                        // Enable the text input and create button
                        binding.textInput.isEnabled = true
                        binding.createButton.isEnabled = true
                        // Check if the thread is created successfully
                        if (exception == null && !thread?.id.isNullOrBlank()) {
                            // WebexConnectSDK: Create a new InAppMessage
                            val message = InAppMessage()
                            message.thread = thread
                            // Handle the new message received
                            InboxDataManager.instance.handleNewMessageReceived(message)
                            // Start the conversation activity
                            ConversationActivity.start(context, thread!!, true)
                            // Finish the activity
                            finish()
                        } else {
                            // Show an error message
                            CommonUtils.showToast(
                                context,
                                getString(R.string.failed_to_create_thread, exception?.message ?: "Unknown error")
                            )
                        }
                    }
                })
            }
        }
    }

    /**
     * Companion object to start the [CreateThreadActivity].
     */
    companion object {
        /**
         * Start the CreateThreadActivity.
         * @param requireContext The context to start the activity.
         */
        fun start(requireContext: Context) {
            val intent = Intent(requireContext, CreateThreadActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext.startActivity(intent)
        }
    }
}