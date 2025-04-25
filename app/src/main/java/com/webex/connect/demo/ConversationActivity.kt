package com.webex.connect.demo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.webex.connect.core.errorhandling.WebexConnectException
import com.webex.connect.demo.data.MessageItem
import com.webex.connect.demo.data.InboxDataManager
import com.webex.connect.demo.databinding.ActivityConversationBinding
import com.webex.connect.demo.databinding.ConnStatusViewBinding
import com.webex.connect.demo.util.DateUtils
import com.webex.connect.demo.util.CommonUtils
import com.webex.connect.demo.ui.ConversationAdapter
import com.webex.connect.inapp.ConnectionStatus
import com.webex.connect.inapp.ConnectionStatusListener
import com.webex.connect.inapp.FetchMessagesCallback
import com.webex.connect.inapp.InAppMessaging
import com.webex.connect.inapp.InAppMessagingListener
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppMessageStatus
import com.webex.connect.inapp.message.InAppMessageType
import com.webex.connect.inapp.message.InAppThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume

/**
 * Activity to display a conversation
 */
class ConversationActivity : AppCompatActivity() {
    private lateinit var context: Context
    private lateinit var binding: ActivityConversationBinding
    private lateinit var connStatusViewBinding: ConnStatusViewBinding
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: ConversationAdapter
    private lateinit var newMessages: ExtendedFloatingActionButton
    private lateinit var inAppThread: InAppThread
    private lateinit var threadId: String
    private val chatItems = mutableListOf<MessageItem>()
    private val lock = ReentrantLock()
    private var isLoadInProgress = AtomicBoolean(false)
    private var hasMoreDataToLoad = true
    private var isUserAtBottom = true
    private val thresholdLimit = 30
    private var hasNewMessage = AtomicBoolean(false)

    // WebexConnectSDK: Get the InAppMessaging instance
    private val inAppMessaging
        get() = InAppMessaging.instance

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Get the thread from the intent
        intent.getStringExtra("thread")?.let {
            // WebexConnectSDK: Parse the thread from the intent
            inAppThread = InAppThread.fromJson(JSONObject(it))
            threadId = inAppThread.id ?: ""
            binding.title.text = inAppThread.title
        }
        // Check if the message composer should be enabled
        intent.getBooleanExtra("enableMessageComposer", false).let {
            binding.composer.visibility = if (it) View.VISIBLE else View.GONE
        }
        // Back button
        binding.backButton.setOnClickListener {
            // Finish the activity
            finish()
        }
        // Connection status view
        connStatusViewBinding = binding.headerConnStatusView
        // Recycler view
        val recyclerView = binding.recyclerView
        // Set the layout manager
        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        // Set the adapter
        adapter = ConversationAdapter({ _, _ ->
            // do nothing
        }, { _, message ->
            // Send read receipts to the server
            sendReadReceiptsToServer(message)
        })
        // Set the adapter to the recycler view
        recyclerView.adapter = adapter
        // Scroll to the bottom when the layout changes
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                binding.recyclerView.postDelayed({
                    scrollToBottom()
                }, 200)
            }
        }
        // Scroll listener for the recycler view
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                // Check if the user is at the bottom
                isUserAtBottom = lastVisiblePosition == recyclerView.adapter?.itemCount?.minus(1)
                // Load more data if the user is at the top of the list and there are more messages to load
                if (hasMoreDataToLoad && firstVisiblePosition <= thresholdLimit) {
                    // Load the data
                    reloadData(isUserAtBottom)
                }
                // Show or hide the new messages FAB
                if (isUserAtBottom) {
                    // Hide the FAB if the user is at the bottom
                    newMessages.visibility = View.GONE
                    hasNewMessage.set(false)
                } else {
                    // Show the FAB if the user is not at the bottom
                    // Check if there are new messages
                    if (hasNewMessage.get()) {
                        // Extend the FAB if there are new messages
                        newMessages.extend()
                    } else {
                        // Shrink the FAB if there are no new messages
                        newMessages.shrink()
                    }
                    // Show the FAB
                    newMessages.visibility = View.VISIBLE
                }
            }
        })
        // New messages FAB
        newMessages = binding.newMessages
        newMessages.setIconResource(R.drawable.ic_action_arrow_down)
        newMessages.visibility = View.GONE
        newMessages.setOnClickListener {
            // Scroll to the bottom when the FAB is clicked
            scrollToBottom()
        }
        // Send button
        binding.sendButton.setOnClickListener {
            // Get the message from the message composer
            val message = binding.messageComposer.text.toString()
            if (message.isNotBlank()) {
                // Hide the keyboard
                CommonUtils.hideKeyboard(this)
                // Disable the message composer and send button
                binding.messageComposer.isEnabled = false
                binding.sendButton.isEnabled = false
                // WebexConnectSDK: Create a new message
                val inAppMessage = InAppMessage().apply {
                    this.thread = inAppThread
                    this.message = message
                }
                // WebexConnectSDK: Publish the message
                // The callback is invoked when the message is published.
                inAppMessaging.publishMessage(inAppMessage) { msg, ex ->
                    // Check if the exception is null
                    if (ex == null) {
                        // Handle the published message as a new message
                        handleNewMessageReceived(msg)
                        // Clear the message composer
                        binding.messageComposer.text.clear()
                        // Update the published message in inbox data manager
                        InboxDataManager.instance.handleNewMessageReceived(msg)
                    } else {
                        // Show an error message if the message failed to send
                        CommonUtils.showToast(context, ex.message ?: getString(R.string.failed_to_send_message))
                    }
                    // Enable the message composer and send button
                    binding.messageComposer.isEnabled = true
                    binding.sendButton.isEnabled = true
                }
            } else {
                // Show an error message if the message is empty
                binding.messageComposer.error = getString(R.string.type_a_message)
            }
        }
        // Load the data
        reloadData()
        // WebexConnectSDK: Connect to the messaging service
        inAppMessaging.connect()
    }

    override fun onResume() {
        super.onResume()
        // WebexConnectSDK: Current connection status
        connectionStatusListener.invoke(inAppMessaging.connectionStatus)
        // WebexConnectSDK: Register the connection status listener
        inAppMessaging.registerConnectionStatusListener(connectionStatusListener)
        // WebexConnectSDK: Register the messaging listener
        inAppMessaging.registerMessagingListener(inAppMessagingListener)
    }

    override fun onPause() {
        super.onPause()
        // WebexConnectSDK: Current connection status
        connectionStatusListener.invoke(inAppMessaging.connectionStatus)
        // WebexConnectSDK: Unregister the connection status listener
        inAppMessaging.unregisterConnectionStatusListener(connectionStatusListener)
        // WebexConnectSDK: Unregister the messaging listener
        inAppMessaging.unregisterMessagingListener(inAppMessagingListener)
    }

    /**
     * WebexConnectSDK: Connection status listener
     * Handle the connection status changes.
     */
    private val connectionStatusListener: ConnectionStatusListener = { connectionStatus ->
        val connStatusViewBackgroundTintColor: ColorStateList?
        val connStatusColor: Int
        val connStatusTitle: String
        when (connectionStatus) {
            // WebexConnectSDK: Connection status is connected
            ConnectionStatus.CONNECTED -> {
                connStatusColor = ContextCompat.getColor(context, R.color.conn_status_connected)
                connStatusTitle = getString(R.string.conn_status_connected)
                connStatusViewBackgroundTintColor = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.bg_conn_status_connected
                    )
                )
            }
            // WebexConnectSDK: Connection status is connecting
            ConnectionStatus.CONNECTING -> {
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
        connStatusViewBinding.icon.setColorFilter(connStatusColor)
        connStatusViewBinding.title.setTextColor(connStatusColor)
        connStatusViewBinding.title.text = connStatusTitle
        connStatusViewBinding.container.backgroundTintList = connStatusViewBackgroundTintColor
    }

    /**
     * WebexConnectSDK: Messaging listener
     * Handle the new message received, delivered, read, thread update, and deleted messages.
     */
    private val inAppMessagingListener: InAppMessagingListener = { message: InAppMessage ->
        // Check if the message is for the current thread
        if (threadId == message.thread?.id) {
            when (message.type) {
                InAppMessageType.MESSAGE,
                InAppMessageType.ALERT,
                InAppMessageType.REPUBLISH -> handleNewMessageReceived(message)

                InAppMessageType.DELIVERY_RECEIPT,
                InAppMessageType.READ_RECEIPT -> handleDeliveredAndReadMessage(message)

                InAppMessageType.THREAD_UPDATE -> handleThreadUpdateMessage(message)
                InAppMessageType.MESSAGE_DELETED -> handleDeletedMessage(message)
                InAppMessageType.TYPING_START,
                InAppMessageType.TYPING_STOP,
                InAppMessageType.CLICKED_RECEIPT -> {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Scroll to the bottom of the chat.
     */
    private fun scrollToBottom() {
        binding.recyclerView.scrollToPosition(adapter.itemCount.minus(1))
        newMessages.visibility = View.GONE
        isUserAtBottom = true
        hasNewMessage.set(false)
    }

    /**
     * Reload the data.
     */
    private fun reloadData(shouldScrollToBottom: Boolean = true) {
        // Check if the load is already in progress and return
        if (!isLoadInProgress.compareAndSet(false, true)) {
            return
        }
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val submittedBefore: Date?
                lock.lock()
                try {
                    // Get the date of the first message
                    submittedBefore = chatItems.firstOrNull { !it.isHeader }?.date
                } finally {
                    lock.unlock()
                }
                // WebexConnectSDK: Load messages from the message store
                val list = inAppMessaging.messageStore?.loadMessages(
                    threadId,
                    thresholdLimit,
                    submittedBefore
                )
                // If the list is null or empty and there are more messages to load, fetch messages
                if ((list == null || list.size < thresholdLimit) && hasMoreDataToLoad) {
                    // Fetch messages from the server
                    val (fetchedMessages, hasMoreData) = fetchMessages(submittedBefore)
                    hasMoreDataToLoad = hasMoreData
                    // Add the fetched messages to the list, if any
                    if (fetchedMessages.isNotEmpty()) {
                        fetchedMessages.forEach { message ->
                            addMessage(message, shouldScrollToBottom)
                        }
                    }
                }
                // Add the loaded messages to the list, if any
                if (!list.isNullOrEmpty()) {
                    list.forEach { message ->
                        addMessage(message, shouldScrollToBottom)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                isLoadInProgress.set(false)
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Fetch messages from the server.
     */
    private suspend fun fetchMessages(submittedBefore: Date?): Pair<List<InAppMessage>, Boolean> =
        suspendCancellableCoroutine { continuation ->
            // WebexConnectSDK: Fetch messages from the server
            // with the given thread id, submitted before date, and limit.
            // FetchMessagesCallback is a callback that is invoked when the messages are fetched.
            inAppMessaging.fetchMessages(
                threadId,
                submittedBefore,
                thresholdLimit,
                object : FetchMessagesCallback {
                    override fun invoke(
                        messages: List<InAppMessage>,
                        hasMoreData: Boolean,
                        exception: WebexConnectException?
                    ) {
                        continuation.resume(Pair(messages, hasMoreData))
                    }
                }
            )
        }

    /**
     * Handle the new message received.
     */
    private fun handleNewMessageReceived(message: InAppMessage) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    if (!isUserAtBottom) {
                        hasNewMessage.set(true)
                        newMessages.extend()
                        newMessages.visibility = View.VISIBLE
                        // WebexConnectSDK: Check if the message is not outgoing and not read
                    } else if (!message.isOutgoing
                        && message.status != InAppMessageStatus.READ
                    ) {
                        // Send read receipts to the server
                        sendReadReceiptsToServer(message)
                    }
                }
                addMessage(message, isUserAtBottom, true)
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Add the message to the chat.
     */
    private suspend fun addMessage(
        message: InAppMessage,
        shouldScrollToBottom: Boolean,
        newItem: Boolean = false
    ) {
        val currentList: List<MessageItem>
        lock.lock()
        try {
            // Check if the message already exists
            val position =
                chatItems.indexOfLast { it.message?.transactionId == message.transactionId }
            if (position != -1) {
                // If the message already exists, remove it
                chatItems.removeAt(position)
            }
            // Add the message
            addChatItem(
                MessageItem(
                    message.submittedAt ?: message.createdAt ?: Date(),
                    false,
                    message
                ), newItem
            )
            // Copy the chat items
            currentList = chatItems.toList()
        } finally {
            lock.unlock()
        }
        // Update the adapter
        withContext(Dispatchers.Main) {
            adapter.submitList(currentList)
            if (shouldScrollToBottom) {
                binding.recyclerView.postDelayed({
                    scrollToBottom()
                }, 200)
            }
        }
    }

    /**
     * Add the chat item.
     */
    private fun addChatItem(chatItem: MessageItem, newItem: Boolean) {
        // Check if a date header for the messageDate exists
        val headerExists = chatItems.any {
            it.isHeader && DateUtils.isSameDay(it.date, chatItem.date)
        }
        // Add the date header if needed
        if (!headerExists) {
            val dateHeader = MessageItem(date = chatItem.date, isHeader = true)
            // Insert the header at the correct position
            val insertIndex = findInsertIndexForHeader(chatItem.date)
            chatItems.add(insertIndex, dateHeader)
        }
        // Add the new message
        if (newItem) {
            chatItems.add(chatItem)
        } else {
            chatItems.add(findInsertIndexForMessage(chatItem.date), chatItem)
        }
        // Sort the chat items by date
        chatItems.sortedByDescending { it.date }
    }

    /**
     * Find the correct index to insert a message.
     */
    private fun findInsertIndexForMessage(date: Date): Int {
        // Find the last header with the same date
        return chatItems.indexOfFirst { it.isHeader && DateUtils.isSameDay(it.date, date) } + 1
    }

    /**
     * Find the correct index to insert a header.
     */
    private fun findInsertIndexForHeader(date: Date): Int {
        // Add at the end if no header with later date is found
        return chatItems.indexOfFirst { it.isHeader && it.date.after(date) }.takeIf { it != -1 }
            ?: chatItems.size
    }

    /**
     * Remove the chat item.
     */
    private fun removeChatItem(index: Int, chatItem: MessageItem) {
        if (index != -1) {
            // Remove the message
            chatItems.removeAt(index)
            // Check if there are other messages for the same date
            val hasOtherMessagesForDate = chatItems.any {
                !it.isHeader && DateUtils.isSameDay(it.date, chatItem.date)
            }
            // Remove the date header if no other messages are left
            if (!hasOtherMessagesForDate) {
                // Remove old header if necessary
                val iterator = chatItems.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    // Check if the item is a header and has the same date as the chat item
                    if (item.isHeader && DateUtils.isSameDay(item.date, chatItem.date)) {
                        // Remove the header
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Update the chat item.
     */
    private fun updateChatItem(index: Int, chatItem: MessageItem) {
        if (index != -1) {
            // Update the message
            val oldChatItem = chatItems[index]
            chatItems[index] = chatItem
            // Check if the header for the updated date needs to be changed
            if (!DateUtils.isSameDay(oldChatItem.date, chatItem.date)) {
                // Remove old header if necessary
                val iterator = chatItems.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    // Check if the item is a header and has the same date as the old chat item
                    if (item.isHeader && DateUtils.isSameDay(item.date, oldChatItem.date)) {
                        // Remove the old header
                        iterator.remove()
                    }
                }
                // Add new header if needed
                val headerExists = chatItems.any {
                    it.isHeader && DateUtils.isSameDay(it.date, chatItem.date)
                }
                // If no header for the updated date exists
                if (!headerExists) {
                    // Create a new header
                    val dateHeader = MessageItem(date = chatItem.date, isHeader = true)
                    // Insert the header at the correct position
                    val insertIndex = findInsertIndexForHeader(chatItem.date)
                    // Add the header
                    chatItems.add(insertIndex, dateHeader)
                }
            }
        }
    }

    /**
     * Handle the delivered and read message.
     */
    private fun handleDeliveredAndReadMessage(message: InAppMessage) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                var currentList: List<MessageItem>? = null
                lock.lock()
                try {
                    // Get the index of the message
                    val index =
                        chatItems.indexOfFirst { it.message?.transactionId == message.transactionId }
                    // Check if the message exists
                    if (index != -1) {
                        // Get the current message
                        val currentMessage = chatItems[index]
                        // Update the message delivered/read date based on the status
                        if (message.status == InAppMessageStatus.DELIVERED) {
                            currentMessage.message?.deliveredAt = message.deliveredAt
                        } else {
                            currentMessage.message?.readAt = message.readAt
                        }
                        // Update chat item
                        updateChatItem(index, currentMessage)
                        // Copy the chat items
                        currentList = chatItems.toList()
                    }
                } finally {
                    lock.unlock()
                }
                if (currentList != null) {
                    withContext(Dispatchers.Main) {
                        // Update the adapter
                        adapter.submitList(currentList)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Handle the thread update message.
     */
    private fun handleThreadUpdateMessage(message: InAppMessage) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                lock.lock()
                try {
                    val threadTitle = message.thread?.title ?: ""
                    // Update the thread title if it has changed
                    if (threadTitle != inAppThread.title) {
                        withContext(Dispatchers.Main) {
                            // Update the thread title
                            binding.title.text = threadTitle
                        }
                    }
                } finally {
                    lock.unlock()
                }
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Handle the deleted message.
     */
    private fun handleDeletedMessage(message: InAppMessage) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                var currentList: List<MessageItem>? = null
                lock.lock()
                try {
                    // Get the index of the message
                    val index =
                        chatItems.indexOfFirst { it.message?.transactionId == message.transactionId }
                    // Check if the message exists
                    if (index != -1) {
                        // Remove the message
                        removeChatItem(index, chatItems[index])
                        // Copy the chat items
                        currentList = chatItems.toList()
                    }
                } finally {
                    lock.unlock()
                }
                if (currentList != null) {
                    withContext(Dispatchers.Main) {
                        // Update the adapter
                        adapter.submitList(currentList)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Send read receipts to the server.
     */
    private fun sendReadReceiptsToServer(message: InAppMessage) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                // WebexConnectSDK: Send the read status of the message to the server
                inAppMessaging.sendMessageStatus(
                    message.transactionId,
                    status = InAppMessageStatus.READ
                ) { _, _, _ ->
                    // Set the message as read date and status
                    message.readAt = Date()
                    message.status = InAppMessageStatus.READ
                    // Update the message in the inbox data manager
                    handleDeliveredAndReadMessage(message)
                }
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Companion object for the [ConversationActivity] class.
     */
    companion object {
        /**
         * Start the conversation activity.
         *
         * @param requireContext The context to start the activity.
         * @param thread The thread to start the conversation.
         * @param enableMessageComposer The flag to enable the message composer.
         */
        fun start(requireContext: Context, thread: InAppThread, enableMessageComposer: Boolean) {
            val intent = Intent(requireContext, ConversationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("thread", thread.toJson().toString())
            intent.putExtra("enableMessageComposer", enableMessageComposer)
            requireContext.startActivity(intent)
        }
    }
}