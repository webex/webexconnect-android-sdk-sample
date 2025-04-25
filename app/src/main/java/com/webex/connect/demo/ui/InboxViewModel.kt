package com.webex.connect.demo.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.webex.connect.inapp.message.InAppMessage
import com.webex.connect.inapp.message.InAppThreadType

/**
 * ViewModel for the inbox.
 */
class InboxViewModel : ViewModel() {

    private val _headerTitle = MutableLiveData<String>()

    /**
     * The title of the header.
     */
    val headerTitle: LiveData<String> get() = _headerTitle
    fun setHeaderTitle(title: String) {
        _headerTitle.value = title
    }

    private val _isConnStatusViewVisible = MutableLiveData<Boolean>()

    /**
     * The visibility of the connection status view.
     */
    val isConnStatusViewVisible: LiveData<Boolean> get() = _isConnStatusViewVisible
    fun setConnStatusViewVisibility(isVisible: Boolean) {
        _isConnStatusViewVisible.value = isVisible
    }

    private val _conversationThreads = MutableLiveData<List<InAppMessage>>()

    /**
     * The conversation threads.
     */
    val conversationThreads: LiveData<List<InAppMessage>> get() = _conversationThreads

    private val _announcementThreads = MutableLiveData<List<InAppMessage>>()

    /**
     * The announcement threads.
     */
    val announcementThreads: LiveData<List<InAppMessage>> get() = _announcementThreads

    /**
     * Sets the threads.
     *
     * @param threads The threads to be set.
     */
    fun setThreads(threads: List<InAppMessage>) {
        filterThreads(threads)
    }

    /**
     * Filters the threads based on the type.
     *
     * @param messages The messages to be filtered.
     */
    private fun filterThreads(messages: List<InAppMessage>) {
        // Filter the threads based on the type
        _conversationThreads.postValue(messages.filter { it.thread?.type == InAppThreadType.CONVERSATION })
        _announcementThreads.postValue(messages.filter { it.thread?.type == InAppThreadType.ANNOUNCEMENT })
    }
}