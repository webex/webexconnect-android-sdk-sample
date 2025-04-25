package com.webex.connect.demo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.webex.connect.core.WebexConnect
import com.webex.connect.core.profile.DeviceProfile
import com.webex.connect.demo.databinding.ActivityLoginBinding
import com.webex.connect.demo.util.CommonUtils

/**
 * Activity to register the user with Webex Connect.
 */
class LoginActivity : AppCompatActivity() {
    private val logTag = "LoginActivity"
    private lateinit var binding: ActivityLoginBinding

    // WebexConnectSDK: Get the WebexConnect instance
    private val webexConnect
        get() = WebexConnect.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // WebexConnectSDK: Check if Webex Connect is started
        if (webexConnect.isStarted) {
            // WebexConnectSDK: Check if the user is already registered
            if (!webexConnect.isRegistered) {
                CommonUtils.log(logTag, getString(R.string.webex_connect_not_registered))
            } else {
                HomeActivity.start(this)
                finish()
            }
        } else {
            CommonUtils.log(logTag, getString(R.string.webex_connect_not_started))
        }

        binding.loginButton.setOnClickListener { login() }
        binding.textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.loginButton.performClick()
                true
            } else {
                false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val styledText = String.format(
                "<big><b>%s</b></big> %s",
                getString(R.string.webex), getString(R.string.connect)
            ).trimIndent()
            binding.webexConnect.text = Html.fromHtml(styledText, Html.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun login() {
        val userId = binding.textInput.text.toString()
        if (userId.isBlank()) {
            binding.textInput.error = getString(R.string.please_enter_valid_user_id)
            return
        }
        CommonUtils.hideKeyboard(this)
        CommonUtils.showProgressDialog(this, getString(R.string.registering))
        binding.textInput.isEnabled = false
        binding.loginButton.isEnabled = false

        // WebexConnectSDK: Create a new DeviceProfile with the default device id and the user id
        val deviceProfile = DeviceProfile(
            // WebexConnectSDK: Using the default device id for the device
            // You can use your own device id if you have one
            DeviceProfile.defaultDeviceId, userId, false
        )

        // WebexConnectSDK: Register the user with Webex Connect
        // Callback will be called with any exception that occurred during registration (or null if none)
        webexConnect.register(deviceProfile) { _, exception ->
            binding.textInput.isEnabled = true
            binding.loginButton.isEnabled = true
            CommonUtils.dismissProgressDialog()
            if (exception == null) {
                HomeActivity.start(this)
                finish()
            } else {
                CommonUtils.showToast(
                    this,
                    exception.localizedMessage ?: getString(
                        R.string.error_code,
                        exception.errorCode
                    ),
                    true
                )
            }
        }
    }

    /**
     * Companion object to start the [LoginActivity].
     */
    companion object {
        /**
         * Start the [LoginActivity].
         */
        fun start(requireContext: Context) {
            val intent = Intent(requireContext, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext.startActivity(intent)
        }
    }
}