package com.webex.connect.demo.data

import com.webex.connect.core.errorhandling.WebexConnectException
import com.webex.connect.inapp.FetchMessagesCallback
import com.webex.connect.inapp.FetchThreadsCallback
import com.webex.connect.inapp.InAppMessaging
import com.webex.connect.inapp.InAppMessagingListener
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppMessageType
import com.webex.connect.inapp.message.InAppThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume

/**
 * Manages the inbox data.
 */
class InboxDataManager {
    private val lock = ReentrantLock()
    private val inboxChangeListeners = mutableSetOf<(List<InAppMessage>) -> Unit>()
    private val threadsWithLatestMessage = mutableListOf<InAppMessage>()
    private var lastFetchThreadCreated: Date? = null
    private val thresholdLimit = 30
    private var isLoadInProgress = AtomicBoolean(false)
    private var hasMoreDataToLoad = true

    // WebexConnectSDK: Get the InAppMessaging instance
    private val inAppMessaging: InAppMessaging
        get() = InAppMessaging.instance

    val hasMoreData: Boolean
        get() = hasMoreDataToLoad

    /**
     * Indicates whether the inbox is loading.
     */
    val isLoading: Boolean
        get() = isLoadInProgress.get()

    /**
     * The listener for in-app messaging events.
     */
    private val inAppMessagingListener: InAppMessagingListener = { message: InAppMessage ->
        // Handle the message based on the message type
        when (message.type) {
            InAppMessageType.MESSAGE,
            InAppMessageType.ALERT,
            InAppMessageType.REPUBLISH -> {
                handleNewMessageReceived(message)
            }

            InAppMessageType.READ_RECEIPT -> handleReadReceiptMessage(message)
            InAppMessageType.THREAD_UPDATE -> handleThreadUpdateMessage(message)
            InAppMessageType.MESSAGE_DELETED,
            InAppMessageType.DELIVERY_RECEIPT,
            InAppMessageType.TYPING_START,
            InAppMessageType.TYPING_STOP,
            InAppMessageType.CLICKED_RECEIPT -> {
                // Do nothing
            }
        }
    }

    init {
        // WebexConnectSDK: Register the in-app messaging listener
        inAppMessaging.registerMessagingListener(inAppMessagingListener)
    }

    /**
     * Clears the inbox data.
     */
    fun clear() {
        lock.lock()
        try {
            threadsWithLatestMessage.clear()
        } finally {
            lock.unlock()
        }
        notifyInboxChanges(emptyList())
    }

    /**
     * Registers an inbox update listener.
     * @param listener The listener to register.
     */
    fun registerInboxUpdateListener(listener: (List<InAppMessage>) -> Unit) {
        inboxChangeListeners.add(listener)
    }

    /**
     * Unregisters an inbox update listener.
     * @param listener The listener to unregister.
     */
    fun unregisterInboxUpdateListener(listener: (List<InAppMessage>) -> Unit) {
        inboxChangeListeners.remove(listener)
    }

    /**
     * Gets the inbox list.
     */
    val inboxList: List<InAppMessage>
        get() {
            lock.lock()
            try {
                // Return a copy of the list
                return threadsWithLatestMessage.toList()
            } finally {
                lock.unlock()
            }
        }

    /**
     * Handles a new message received event.
     * @param message The message received.
     */
    fun handleNewMessageReceived(message: InAppMessage) {
        // If the message does not have a thread, return
        if (message.thread == null) {
            return
        }
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                // Add the message to the inbox
                addMessage(message)
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Handles a read receipt message.
     * @param message The read receipt message.
     */
    private fun handleReadReceiptMessage(message: InAppMessage) {
        // If the message is not read, return
        if (message.readAt == null) {
            return
        }
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val currentInboxList: List<InAppMessage>
                lock.lock()
                try {
                    // Find the message in the inbox
                    val position =
                        threadsWithLatestMessage.indexOfFirst { it.transactionId == message.transactionId }
                    // If the message is found, update the read date
                    if (position != -1) {
                        val currentMessage = threadsWithLatestMessage[position]
                        currentMessage.readAt = message.readAt
                        // Update the message in the inbox
                        threadsWithLatestMessage[position] = currentMessage
                    }
                    // Get the current inbox list
                    currentInboxList = threadsWithLatestMessage.toList()
                } finally {
                    lock.unlock()
                }
                // Notify the inbox changes
                notifyInboxChanges(currentInboxList)
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Handles a thread update message.
     */
    private fun handleThreadUpdateMessage(message: InAppMessage) {
        // If the message does not have a thread, return
        if (message.thread == null) {
            return
        }
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val currentInboxList: List<InAppMessage>
                lock.lock()
                try {
                    var position = -1
                    val threadId = message.thread?.id ?: ""
                    // If the thread ID is not blank, find the thread in the inbox
                    if (threadId.isNotBlank()) {
                        // Find the thread in the inbox
                        position =
                            threadsWithLatestMessage.indexOfFirst { it.thread?.id == threadId }
                    }
                    // If the thread is found, update the thread
                    if (position != -1) {
                        val currentMessage = threadsWithLatestMessage[position]
                        currentMessage.thread = message.thread
                        // Update the thread in the inbox
                        threadsWithLatestMessage[position] = currentMessage
                    }
                    // Get the current inbox list
                    currentInboxList = threadsWithLatestMessage.toList()
                } finally {
                    lock.unlock()
                }
                notifyInboxChanges(currentInboxList)
            } catch (_: Throwable) {
            } finally {
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Notifies the inbox changes.
     */
    private fun notifyInboxChanges(list: List<InAppMessage>) {
        // Notify the inbox change listeners
        inboxChangeListeners.forEach { it(list) }
    }

    /**
     * Loads the index list.
     */
    fun loadIndexList() {
        // If the load is in progress, return
        if (!isLoadInProgress.compareAndSet(false, true)) {
            return
        }
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val allThreads = mutableListOf<InAppThread>()
                // Load all threads
                loadAllThreads(allThreads, hasMoreDataToLoad)
                // Load messages for each thread
                allThreads.forEach { thread ->
                    // Load the message
                    val message = InAppMessage()
                    message.thread = thread
                    // Add the message to the inbox
                    addMessage(message, false)
                }
            } catch (_: Throwable) {
            } finally {
                // Set the load in progress to false
                isLoadInProgress.set(false)
                coroutineScope.cancel()
            }
        }
    }

    fun loadMessageFromThread(thread: InAppThread) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                // Load the message
                val message = loadMessage(thread, true)
                message.thread = thread
                // Add the message to the inbox
                addMessage(message)
            } catch (_: Throwable) {
            } finally {
                // Set the load in progress to false
                isLoadInProgress.set(false)
                coroutineScope.cancel()
            }
        }
    }

    /**
     * Adds a message to the inbox.
     */
    private fun addMessage(message: InAppMessage, shouldCheckExistingRecord: Boolean = true) {
        val currentInboxList: List<InAppMessage>
        lock.lock()
        try {
            val threadId = message.thread?.id ?: ""
            // If the thread ID is blank, return
            if (threadId.isBlank()) {
                return
            }
            var position = -1
            if (shouldCheckExistingRecord) {
                // Find the thread in the inbox
                position = threadsWithLatestMessage.indexOfFirst { it.thread?.id == threadId }
            }
            // Check if the thread is found
            if (position != -1) {
                message.thread = threadsWithLatestMessage[position].thread
                // Update the message in the inbox
                threadsWithLatestMessage[position] = message
            } else {
                // Add the message to the inbox
                threadsWithLatestMessage.add(message)
            }
            // Sort the inbox by the latest message
            threadsWithLatestMessage.sortWith(compareByDescending {
                getModifiedDate(it) ?: Date(Long.MIN_VALUE)
            })
            // Copy the inbox list
            currentInboxList = threadsWithLatestMessage.toList()
        } finally {
            lock.unlock()
        }
        notifyInboxChanges(currentInboxList)
    }

    /**
     * Loads all threads.
     */
    private suspend fun loadAllThreads(allThreads: MutableList<InAppThread>, shouldFetch: Boolean) {
        val modifiedBefore: Date? =
            allThreads.lastOrNull()?.let { getLatestDate(it.updatedAt, it.createdAt) }
        // WebexConnectSDK: Load threads from the message store
        val threads = inAppMessaging.messageStore?.loadThreads(thresholdLimit, modifiedBefore)
        // If the threads are not empty, add the threads
        if (!threads.isNullOrEmpty()) {
            // Add the threads
            addThreads(allThreads, threads, shouldFetch)
        } else if (shouldFetch) {
            val lastThreadCreated = allThreads
                .filter { it.createdAt != null }  // Filter out null dates
                .minByOrNull { it.createdAt!! }?.createdAt  // Fetch the oldest non-null created date
            // If the last fetch thread created date is the same as the last thread created date
            if (lastFetchThreadCreated != null
                && lastFetchThreadCreated == lastThreadCreated
            ) {
                // No new threads to fetch,
                return
            }
            // Update the last fetch thread created date
            lastFetchThreadCreated = lastThreadCreated
            // Fetch threads
            val (fetchedThreads, hasMoreData) = fetchThreads(lastThreadCreated)
            hasMoreDataToLoad = hasMoreData
            // If the fetched threads are not empty, add the threads
            if (fetchedThreads.isNotEmpty()) {
                // Add the threads
                addThreads(allThreads, fetchedThreads, hasMoreData)
            }
        }
    }

    /**
     * Adds threads to the list of all threads.
     */
    private suspend fun addThreads(
        allThreads: MutableList<InAppThread>,
        threads: List<InAppThread>,
        shouldFetch: Boolean
    ) {
        // Filter out the threads that are not already in the list
        val uniqueThreads = threads.filterNot { newThread ->
            allThreads.any { existingThread -> existingThread.id == newThread.id }
        }
        // Add the unique threads to the list
        uniqueThreads.forEach { thread ->
            allThreads.add(thread)
        }
        // Load messages for each thread
        loadAllThreads(allThreads, shouldFetch)
    }

    /**
     * Fetches threads.
     */
    private suspend fun fetchThreads(lastThreadCreated: Date?): Pair<List<InAppThread>, Boolean> =
        suspendCancellableCoroutine { continuation ->
            // WebexConnectSDK: Fetch threads from the server
            inAppMessaging.fetchThreads(
                lastThreadCreated ?: Date(),
                thresholdLimit,
                object : FetchThreadsCallback {
                    override fun invoke(
                        threads: List<InAppThread>,
                        hasMoreData: Boolean,
                        exception: WebexConnectException?
                    ) {
                        continuation.resume(Pair(threads, hasMoreData))
                    }
                }
            )
        }

    /**
     * Loads a message.
     */
    private suspend fun loadMessage(thread: InAppThread, shouldFetch: Boolean): InAppMessage {
        val threadId = thread.id ?: ""
        // If the thread ID is empty, return an empty message
        if (threadId.isEmpty()) {
            return InAppMessage()
        }
        // WebexConnectSDK: Load messages from the message store
        val messages = inAppMessaging.messageStore?.loadMessages(threadId, 1)
        // If the messages are not empty, return the first message
        if (!messages.isNullOrEmpty()) {
            // Return the first message
            return messages[0]
        } else if (shouldFetch) {
            // Fetch the message
            val fetchedMessages = fetchMessage(threadId)
            // If the fetched messages are not empty, return the first message
            if (fetchedMessages.isNotEmpty()) {
                // Return the first message
                return fetchedMessages[0]
            }
        }
        // Return an empty message
        return InAppMessage()
    }

    /**
     * Fetches a message.
     */
    private suspend fun fetchMessage(threadId: String): List<InAppMessage> =
        suspendCancellableCoroutine { continuation ->
            // WebexConnectSDK: Fetch messages from the server
            inAppMessaging.fetchMessages(
                threadId,
                Date(),
                1,
                // WebexConnectSDK: Fetch messages callback
                object : FetchMessagesCallback {
                    override fun invoke(
                        messages: List<InAppMessage>,
                        hasMoreData: Boolean,
                        exception: WebexConnectException?
                    ) {
                        continuation.resume(messages)
                    }
                }
            )
        }

    /**
     * Gets the modified date.
     */
    private fun getModifiedDate(message: InAppMessage): Date? {
        // Determine the message creation date
        val messageCreatedDate =
            if (message.message.isNullOrBlank() && message.submittedAt == null) {
                null
            } else {
                message.createdAt
            }
        // Get the latest date from message submitted date and created date
        val messageLatest: Date? = getLatestDate(message.submittedAt, messageCreatedDate)
        // If thread is not null, get the latest date from thread
        val threadLatest: Date? = message.thread?.let { thread ->
            getLatestDate(thread.updatedAt, thread.createdAt)
        }
        // Compare the latest date from Message and Thread
        return getLatestDate(messageLatest, threadLatest)
    }

    /**
     * Gets the latest date.
     */
    private fun getLatestDate(date1: Date?, date2: Date?): Date? {
        return when {
            date1 == null -> date2
            date2 == null -> date1
            else -> if (date1.after(date2)) date1 else date2
        }
    }

    /**
     * Companion object for the [InboxDataManager] class.
     */
    companion object {
        /**
         * The singleton instance of [InboxDataManager].
         */
        val instance: InboxDataManager by lazy { InboxDataManager() }
    }
}