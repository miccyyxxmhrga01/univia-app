package com.example.kayara

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentNotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        SupabaseDatabase.getInstance()

    private val notifications =
        mutableListOf<NotificationModel>()

    private lateinit var notificationAdapter:
            NotificationAdapter

    // Real-time notification listener
    private var notificationsListener:
            ListenerRegistration? = null

    // IDs nga gi-delete/clear sa current student.
    // Notification only ang mawala; dili ang original post/chat/etc.
    private val dismissedIds =
        mutableSetOf<String>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view =
            inflater.inflate(
                R.layout.fragment_student_notifications,
                container,
                false
            )

        rvNotifications =
            view.findViewById(
                R.id.rvStudentNotifications
            )

        rvNotifications.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        notificationAdapter =
            NotificationAdapter(
                notifications = notifications,

                onNotificationLongClick = { notification ->

                    showNotificationOptions(
                        notification
                    )
                }
            )

        rvNotifications.adapter =
            notificationAdapter

        loadDismissedNotificationsThenListen()

        return view
    }


    // =========================================================
    // LOAD DISMISSED IDS FIRST
    // =========================================================

    private fun loadDismissedNotificationsThenListen() {

        val uid =
            auth.currentUser?.uid

        if (uid.isNullOrBlank()) {

            Toast.makeText(
                requireContext(),
                "You are not logged in.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        db.collection("notificationDismissals")
            .document(uid)
            .collection("items")
            .get()
            .addOnSuccessListener { result ->

                dismissedIds.clear()

                dismissedIds.addAll(
                    result.documents.map {
                        it.id
                    }
                )

                loadNotifications()
            }
            .addOnFailureListener {

                // Even if dismissals cannot be loaded,
                // keep the original notification feature working.
                loadNotifications()
            }
    }


    // =========================================================
    // LOAD NOTIFICATIONS
    // =========================================================

    private fun loadNotifications() {

        val currentUser =
            auth.currentUser

        if (currentUser == null) {

            Toast.makeText(
                requireContext(),
                "You are not logged in.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val uid =
            currentUser.uid

        notificationsListener?.remove()

        notificationsListener =
            db.collection("notifications")
                .whereEqualTo(
                    "userId",
                    uid
                )
                .addSnapshotListener { result, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Failed to load notifications: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    if (result == null) {
                        return@addSnapshotListener
                    }

                    val sortedDocuments =
                        result.documents
                            .sortedByDescending { document ->

                                document.getTimestamp(
                                    "createdAt"
                                )
                                    ?.toDate()
                                    ?.time
                                    ?: 0L
                            }

                    notifications.clear()

                    for (document in sortedDocuments) {

                        // Hidden only for this student.
                        if (
                            dismissedIds.contains(
                                document.id
                            )
                        ) {
                            continue
                        }

                        val title =
                            document.getString(
                                "title"
                            ) ?: ""

                        val message =
                            document.getString(
                                "message"
                            ) ?: ""

                        val type =
                            document.getString(
                                "type"
                            ) ?: "info"

                        val isRead =
                            document.getBoolean(
                                "read"
                            ) ?: false

                        val createdAt =
                            document.getTimestamp(
                                "createdAt"
                            )

                        val timeAgo =
                            if (createdAt != null) {

                                getTimeAgo(
                                    createdAt.toDate()
                                )

                            } else {

                                ""
                            }

                        val notification =
                            NotificationModel(

                                notificationId =
                                    document.id,

                                title =
                                    title,

                                message =
                                    message,

                                timeAgo =
                                    timeAgo,

                                type =
                                    type,

                                isRead =
                                    isRead
                            )

                        notifications.add(
                            notification
                        )
                    }

                    notificationAdapter
                        .notifyDataSetChanged()
                }
    }


    // =========================================================
    // OPTIONS: DELETE ONE / CLEAR ALL
    // =========================================================

    private fun showNotificationOptions(
        notification: NotificationModel
    ) {

        if (!isAdded) {
            return
        }

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Notification options"
            )
            .setItems(
                arrayOf(
                    "Delete notification",
                    "Clear all notifications"
                )
            ) { _, which ->

                if (which == 0) {

                    confirmDeleteNotification(
                        notification
                    )

                } else {

                    confirmClearAll()
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }


    private fun confirmDeleteNotification(
        notification: NotificationModel
    ) {

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Delete notification?"
            )
            .setMessage(
                "This will remove the notification from your list only."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                dismissNotification(
                    notification
                )
            }
            .show()
    }


    private fun dismissNotification(
        notification: NotificationModel
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return

        val notificationId =
            notification.notificationId

        if (notificationId.isBlank()) {
            return
        }

        // Hide immediately so the UI feels responsive.
        dismissedIds.add(
            notificationId
        )

        val index =
            notifications.indexOfFirst {
                it.notificationId ==
                        notificationId
            }

        if (index >= 0) {

            notifications.removeAt(
                index
            )

            notificationAdapter
                .notifyItemRemoved(
                    index
                )
        }

        saveDismissal(
            uid =
                uid,

            notificationId =
                notificationId
        )
    }


    private fun confirmClearAll() {

        if (notifications.isEmpty()) {

            Toast.makeText(
                requireContext(),
                "No notifications to clear.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Clear all notifications?"
            )
            .setMessage(
                "All notifications in your list will be cleared. Original posts, chats, requests, and files will not be deleted."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Clear All"
            ) { _, _ ->

                clearAllNotifications()
            }
            .show()
    }


    private fun clearAllNotifications() {

        val uid =
            auth.currentUser?.uid
                ?: return

        val ids =
            notifications
                .map {
                    it.notificationId
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (ids.isEmpty()) {
            return
        }

        dismissedIds.addAll(
            ids
        )

        notifications.clear()

        notificationAdapter
            .notifyDataSetChanged()

        val batch =
            db.batch()

        for (notificationId in ids) {

            val ref =
                db.collection(
                    "notificationDismissals"
                )
                    .document(uid)
                    .collection("items")
                    .document(
                        notificationId
                    )

            batch.set(
                ref,
                mapOf(
                    "notificationId" to
                            notificationId,

                    "dismissedAt" to
                            Timestamp.now()
                )
            )
        }

        batch.commit()
            .addOnFailureListener { error ->

                if (isAdded) {

                    Toast.makeText(
                        requireContext(),
                        "Unable to save cleared notifications: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }


    private fun saveDismissal(
        uid: String,
        notificationId: String
    ) {

        db.collection(
            "notificationDismissals"
        )
            .document(uid)
            .collection("items")
            .document(
                notificationId
            )
            .set(
                mapOf(
                    "notificationId" to
                            notificationId,

                    "dismissedAt" to
                            Timestamp.now()
                )
            )
            .addOnFailureListener { error ->

                if (isAdded) {

                    Toast.makeText(
                        requireContext(),
                        "Unable to delete notification: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }


    // =========================================================
    // TIME AGO
    // =========================================================

    private fun getTimeAgo(
        date: Date
    ): String {

        val now =
            System.currentTimeMillis()

        val time =
            date.time

        val difference =
            now - time

        val seconds =
            difference / 1000

        val minutes =
            seconds / 60

        val hours =
            minutes / 60

        val days =
            hours / 24

        return when {

            seconds < 60 ->
                "Just now"

            minutes < 60 -> {

                if (minutes == 1L) {
                    "1 minute ago"
                } else {
                    "$minutes minutes ago"
                }
            }

            hours < 24 -> {

                if (hours == 1L) {
                    "1 hour ago"
                } else {
                    "$hours hours ago"
                }
            }

            days < 7 -> {

                if (days == 1L) {
                    "Yesterday"
                } else {
                    "$days days ago"
                }
            }

            else -> {

                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).format(
                    date
                )
            }
        }
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroyView() {

        notificationsListener?.remove()

        notificationsListener =
            null

        super.onDestroyView()
    }
}
