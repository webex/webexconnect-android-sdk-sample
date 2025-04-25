package com.webex.connect.demo

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.webex.connect.core.WebexConnect
import com.webex.connect.demo.data.InboxDataManager
import com.webex.connect.demo.databinding.ActivityHomeBinding
import com.webex.connect.demo.databinding.ConnStatusViewBinding
import com.webex.connect.demo.databinding.NavHeaderHomeBinding
import com.webex.connect.demo.util.CommonUtils
import com.webex.connect.demo.ui.InboxViewModel
import com.webex.connect.inapp.ConnectionStatus
import com.webex.connect.inapp.ConnectionStatusListener
import com.webex.connect.inapp.InAppMessaging
import com.webex.connect.inapp.message.InAppMessage

/**
 * Home activity to display the inbox messages and notifications.
 */
class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var context: Context
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedViewModel: InboxViewModel
    private lateinit var navConnStatusView: ImageView
    private lateinit var appBarConnStatusView: ImageView
    private lateinit var connStatusViewBinding: ConnStatusViewBinding
    private val indexDataManager
        get() = InboxDataManager.instance

    // WebexConnectSDK: Get the WebexConnect instance
    private val webexConnect
        get() = WebexConnect.instance

    // WebexConnectSDK: Get the SDK version
    private val sdkVersion: String
        get() = "v${webexConnect.sdkVersion}"

    // WebexConnectSDK: Get the InAppMessaging instance
    private val inAppMessaging
        get() = InAppMessaging.instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Get the shared view model
        sharedViewModel = ViewModelProvider(this)[InboxViewModel::class.java]

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val bottomNavView: BottomNavigationView = binding.appBarMain.contentMain.bottomNavView
        val navController = navHostFragment.navController

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_messages, R.id.nav_notifications
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        // navView.setupWithNavController(navController)
        bottomNavView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener(this)

        val sdkVersionActionView = navView.menu.findItem(R.id.menu_sdk_version).actionView
        sdkVersionActionView?.findViewById<TextView>(R.id.menu_sdk_version_value)?.text = sdkVersion

        val headerView = binding.navView.getHeaderView(0)
        val headerBinding = NavHeaderHomeBinding.bind(headerView)
        // WebexConnectSDK: Get the current registered user id
        webexConnect.deviceProfile?.userId?.let {
            headerBinding.title.text = it
            headerBinding.subtitle.text = String.format("%s@%s", it, "webex.com")
        }
        navConnStatusView = headerBinding.connStatus
        appBarConnStatusView = binding.appBarMain.connStatus
        connStatusViewBinding = binding.appBarMain.headerConnStatusView
        // Observe the header title
        sharedViewModel.headerTitle.observe(this) {
            binding.appBarMain.headerTitle.text = it
        }
        // Set the connection status view visibility
        sharedViewModel.isConnStatusViewVisible.observe(this) {
            connStatusViewBinding.container.visibility = if (it) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        // Set the profile frame click listener
        binding.appBarMain.profileFrame.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // WebexConnectSDK: Connect to Webex Connect
        inAppMessaging.connect()

        // Handle the deep link action
        handleDeepLinkAction()

        // Load the inbox list if it is empty
        if (!indexDataManager.isLoading
            && indexDataManager.hasMoreData
        ) {
            indexDataManager.loadIndexList()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update the inbox list
        sharedViewModel.setThreads(indexDataManager.inboxList)
        // Register the inbox update listener
        indexDataManager.registerInboxUpdateListener(inboxUpdateListener)
        // WebexConnectSDK: Get the current connection status
        connectionStatusListener.invoke(inAppMessaging.connectionStatus)
        // WebexConnectSDK: Register the connection status listener
        inAppMessaging.registerConnectionStatusListener(connectionStatusListener)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the inbox update listener
        indexDataManager.unregisterInboxUpdateListener(inboxUpdateListener)
        // WebexConnectSDK: Get the current connection status
        connectionStatusListener.invoke(inAppMessaging.connectionStatus)
        // WebexConnectSDK: Unregister the connection status listener
        inAppMessaging.unregisterConnectionStatusListener(connectionStatusListener)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * Get the [NavHostFragment] from the layout.
     */
    private val navHostFragment: NavHostFragment
        get() = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_messages -> {
                // Navigate to the messages fragment
                navHostFragment.navController.navigate(R.id.nav_messages)
            }

            R.id.nav_notifications -> {
                // Navigate to the notifications fragment
                navHostFragment.navController.navigate(R.id.nav_notifications)
            }

            R.id.menu_sdk_version -> {
                // Show the SDK version
                Snackbar.make(binding.root, "SDK Version: $sdkVersion", Snackbar.LENGTH_SHORT)
                    .show()
            }

            R.id.menu_logout -> {
                // Logout the user
                logout()
            }
        }
        binding.drawerLayout.closeDrawers()
        return true
    }

    /**
     * Inbox update listener
     */
    private val inboxUpdateListener: (List<InAppMessage>) -> Unit =
        object : (List<InAppMessage>) -> Unit {
            override fun invoke(inboxList: List<InAppMessage>) {
                // Update the inbox list
                sharedViewModel.setThreads(inboxList)
            }
        }

    /**
     * WebexConnectSDK: Connection status listener
     */
    private val connectionStatusListener: ConnectionStatusListener = { connectionStatus ->
        val profileConnStatusColor: Int
        val connStatusViewBackgroundTintColor: ColorStateList?
        val connStatusColor: Int
        val connStatusTitle: String
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                profileConnStatusColor =
                    ContextCompat.getColor(context, R.color.profile_conn_status_connected)
                connStatusColor = ContextCompat.getColor(context, R.color.conn_status_connected)
                connStatusTitle = getString(R.string.conn_status_connected)
                connStatusViewBackgroundTintColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.bg_conn_status_connected
                    )
                )
            }

            ConnectionStatus.CONNECTING -> {
                profileConnStatusColor =
                    ContextCompat.getColor(context, R.color.profile_conn_status_connecting)
                connStatusColor = ContextCompat.getColor(context, R.color.conn_status_connecting)
                connStatusTitle = getString(R.string.conn_status_connecting)
                connStatusViewBackgroundTintColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.bg_conn_status_connecting
                    )
                )
            }

            else -> {
                profileConnStatusColor =
                    ContextCompat.getColor(context, R.color.profile_conn_status_disconnected)
                connStatusColor = ContextCompat.getColor(context, R.color.conn_status_disconnected)
                connStatusTitle = getString(R.string.conn_status_disconnected)
                connStatusViewBackgroundTintColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.bg_conn_status_disconnected
                    )
                )
            }
        }
        // Update the connection status views
        navConnStatusView.setColorFilter(profileConnStatusColor)
        appBarConnStatusView.setColorFilter(profileConnStatusColor)
        connStatusViewBinding.icon.setColorFilter(connStatusColor)
        connStatusViewBinding.title.setTextColor(connStatusColor)
        connStatusViewBinding.title.text = connStatusTitle
        connStatusViewBinding.container.backgroundTintList = connStatusViewBackgroundTintColor
    }

    /**
     * Logout the user from Webex Connect.
     */
    private fun logout() {
        // Show the progress dialog
        CommonUtils.showProgressDialog(this, getString(R.string.unregistering))
        // WebexConnectSDK: Unregister the user from Webex Connect
        // Callback will be invoked with any exception that occurred during un-registration (or null if none).
        webexConnect.unregister { _, exception ->
            // Dismiss the progress dialog
            CommonUtils.dismissProgressDialog()
            if (exception == null) {
                CommonUtils.showToast(
                    this,
                    if (webexConnect.isRegistered) getString(R.string.failed_to_logout) else getString(R.string.logout_successful),
                    true
                )
                // Clear the inbox data
                InboxDataManager.instance.clear()
                // Start the login activity
                LoginActivity.start(this)
                // Finish the activity
                finish()
            } else {
                // Show an error message
                CommonUtils.showToast(
                    this, exception.localizedMessage ?: "ErrorCode: ${exception.errorCode}", true
                )
            }
        }
    }

    /**
     * Handle the deep link action.
     */
    private fun handleDeepLinkAction() {
        intent.data?.let { uri ->
            val action = uri.path
            when {
                DeepLinkActivity.DEEPLINK_ACTION_MESSAGING.equals(
                    action,
                    ignoreCase = true
                ) -> {
                    navHostFragment.navController.navigate(R.id.nav_messages)
                }

                DeepLinkActivity.DEEPLINK_ACTION_NOTIFICATIONS.equals(
                    action,
                    ignoreCase = true
                ) -> {
                    navHostFragment.navController.navigate(R.id.nav_notifications)
                }

                DeepLinkActivity.DEEPLINK_ACTION_LOGOUT.equals(
                    action,
                    ignoreCase = true
                ) -> {
                    logout()
                }

                DeepLinkActivity.DEEPLINK_ACTION_OPEN_NAVBAR.equals(
                    action,
                    ignoreCase = true
                ) -> {
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                }

                else -> {
                    // Show an error message
                    CommonUtils.showToast(this, getString(R.string.unknown_deep_link, uri.toString()), true)
                }
            }
        }
    }

    /**
     * Companion object to start the [HomeActivity].
     */
    companion object {
        /**
         * Start the [HomeActivity].
         * @param requireContext The context to start the activity.
         */
        fun start(requireContext: Context) {
            val intent = Intent(requireContext, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext.startActivity(intent)
        }
    }
}