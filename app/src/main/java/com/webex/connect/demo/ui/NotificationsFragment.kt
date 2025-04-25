package com.webex.connect.demo.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.webex.connect.demo.ConversationActivity
import com.webex.connect.demo.R
import com.webex.connect.demo.data.InboxDataManager
import com.webex.connect.demo.databinding.FragmentNotificationsBinding
import com.webex.connect.demo.util.CommonUtils

/**
 * Fragment for the notifications.
 */
class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private lateinit var sharedViewModel: InboxViewModel
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var adapter: InboxAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Get the shared view model
        sharedViewModel = ViewModelProvider(requireActivity())[InboxViewModel::class.java]
        sharedViewModel.setHeaderTitle(getString(R.string.title_notifications))
        sharedViewModel.setConnStatusViewVisibility(false)
        // Inflate the layout for this fragment
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        // Set up the RecyclerView
        val recyclerView = binding.recyclerView
        // Set up the layout manager
        layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        // Set up the adapter
        adapter = InboxAdapter({ _, message ->
            val threadId = message.thread?.id ?: ""
            // Check if the thread ID is not empty
            if (threadId.isNotBlank()) {
                // Start the conversation activity
                ConversationActivity.start(requireContext(), message.thread!!, false)
            } else {
                CommonUtils.showToast(requireContext(), getString(R.string.thread_id_empty))
            }
        }, { _, message ->
            if (message.message.isNullOrBlank() && message.submittedAt == null) {
                message.thread?.let { InboxDataManager.instance.loadMessageFromThread(it) }
            }
        })
        // Sets the adapter to the RecyclerView
        recyclerView.adapter = adapter
        // Observe the announcement threads
        sharedViewModel.announcementThreads.observe(viewLifecycleOwner) { threads ->
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            adapter.submitList(threads) // Updates the RecyclerView with new data
            // Scroll to the first item if the first item is visible
            if (firstVisibleItemPosition == 0) {
                recyclerView.postDelayed({
                    recyclerView.scrollToPosition(0)  // Scroll to the first item
                }, 200)
            }
        }
        // Return the root view
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up the binding
        _binding = null
    }
}