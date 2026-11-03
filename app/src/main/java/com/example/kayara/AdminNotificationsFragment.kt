package com.example.kayara

import android.content.Intent
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

class AdminNotificationsFragment : Fragment() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var db: SupabaseDatabase
    private lateinit var auth: FirebaseAuth

    private var notificationListener:
            ListenerRegistration? = null

    // =========================================================
    // ORGANIZATION
    // =========================================================

    private var organizationId: String = ""

    // Notifications nga gi-delete/clear sa current Admin only.
    private val dismissedIds =
        mutableSetOf<String>()

    // =========================================================
    // INSTANCE
    // =========================================================

    companion object {

        private const val ARG_ORGANIZATION_ID =
            "organizationId"

        fun newInstance(
            organizationId: String
        ): AdminNotificationsFragment {

            return AdminNotificationsFragment().apply {

                arguments =
                    Bundle().apply {

                        putString(
                            ARG_ORGANIZATION_ID,
                            organizationId
                        )
                    }
            }
        }
    }

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        organizationId =
            arguments
                ?.getString(
                    ARG_ORGANIZATION_ID
                )
                ?.trim()
                ?: ""

        db =
            SupabaseDatabase.getInstance()

        auth =
            FirebaseAuth.getInstance()
    }

    // =========================================================
    // CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(
                R.layout.fragment_admin_notifications,
                container,
                false
            )

        val recyclerView =
            view.findViewById<RecyclerView>(
                R.id.rvAdminNotifications
            )

        recyclerView.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        loadDismissedNotificationsThenListen(
            recyclerView
        )

        return view
    }

    // =========================================================
    // LOAD DISMISSED NOTIFICATIONS FIRST
    // =========================================================

    private fun loadDismissedNotificationsThenListen(
        recyclerView: RecyclerView
    ) {

        val uid =
            auth.currentUser?.uid

        if (uid.isNullOrBlank()) {

            loadNotifications(
                recyclerView
            )

            return
        }

        db.collection(
            "notificationDismissals"
        )
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

                loadNotifications(
                    recyclerView
                )
            }
            .addOnFailureListener {

                loadNotifications(
                    recyclerView
                )
            }
    }


    // =========================================================
    // DELETE / CLEAR OPTIONS
    // =========================================================

    private fun showNotificationOptions(
        notification: NotificationModel,
        recyclerView: RecyclerView
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
                        notification,
                        recyclerView
                    )

                } else {

                    confirmClearAll(
                        recyclerView
                    )
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }


    private fun confirmDeleteNotification(
        notification: NotificationModel,
        recyclerView: RecyclerView
    ) {

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Delete notification?"
            )
            .setMessage(
                "This will remove the notification from your Org Manager account only."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                dismissNotification(
                    notification,
                    recyclerView
                )
            }
            .show()
    }


    private fun dismissNotification(
        notification: NotificationModel,
        recyclerView: RecyclerView
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return

        val notificationId =
            notification.notificationId

        if (notificationId.isBlank()) {
            return
        }

        dismissedIds.add(
            notificationId
        )

        saveDismissal(
            uid =
                uid,

            notificationId =
                notificationId
        )

        loadNotifications(
            recyclerView
        )
    }


    private fun confirmClearAll(
        recyclerView: RecyclerView
    ) {

        val currentItems =
            (recyclerView.adapter as? NotificationAdapter)
                ?.itemCount
                ?: 0

        if (currentItems == 0) {

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
                "This clears the notifications from your Org Manager account only. Original posts, chats, requests, and files will not be deleted."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Clear All"
            ) { _, _ ->

                clearAllVisibleNotifications(
                    recyclerView
                )
            }
            .show()
    }


    private fun clearAllVisibleNotifications(
        recyclerView: RecyclerView
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return

        db.collection(
            "notifications"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { snapshot ->

                val ids =
                    snapshot.documents
                        .map {
                            it.id
                        }
                        .filter {
                            it.isNotBlank()
                        }

                if (ids.isEmpty()) {
                    return@addOnSuccessListener
                }

                dismissedIds.addAll(
                    ids
                )

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
                    .addOnSuccessListener {

                        if (isAdded) {

                            loadNotifications(
                                recyclerView
                            )
                        }
                    }
                    .addOnFailureListener { error ->

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Unable to clear notifications: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
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
    }


    // =========================================================
    // LOAD NOTIFICATIONS
    // =========================================================

    private fun loadNotifications(
        recyclerView: RecyclerView
    ) {

        if (
            organizationId.isBlank()
        ) {

            recyclerView.adapter =
                NotificationAdapter(
                    emptyList()
                )

            return
        }


        val currentUid =
            auth.currentUser?.uid
                ?.trim()
                .orEmpty()


        notificationListener?.remove()


        /*
         * Query by organization only.
         *
         * Sorting and recipient filtering are done locally
         * so no unnecessary Firestore composite index is needed.
         */

        notificationListener =
            db.collection(
                "notifications"
            )
                .whereEqualTo(
                    "organizationId",
                    organizationId
                )
                .addSnapshotListener { snapshot, error ->


                    if (!isAdded) {

                        return@addSnapshotListener
                    }


                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load notifications: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }


                    if (snapshot == null) {

                        return@addSnapshotListener
                    }


                    val notifications =
                        snapshot.documents


                            // =================================================
                            // DO NOT SHOW DELETED / CLEARED NOTIFICATIONS
                            // =================================================

                            .filter { document ->

                                !dismissedIds.contains(
                                    document.id
                                )
                            }


                            // =================================================
                            // IMPORTANT:
                            // DO NOT SHOW STUDENT-SPECIFIC NOTIFICATIONS
                            // TO THE ORGANIZATION ADMIN
                            // =================================================

                            .filter { document ->


                                /*
                                 * StudentNotificationHelper uses userId
                                 * for the specific student receiving
                                 * the notification.
                                 *
                                 * If userId exists and it is not this
                                 * logged-in admin, do not show it here.
                                 */

                                val targetUserId =
                                    document.getString(
                                        "userId"
                                    )
                                        ?.trim()
                                        .orEmpty()


                                val recipientId =
                                    document.getString(
                                        "recipientId"
                                    )
                                        ?.trim()
                                        .orEmpty()


                                val targetRole =
                                    (
                                            document.getString(
                                                "targetRole"
                                            )
                                                ?: document.getString(
                                                    "recipientRole"
                                                )
                                                ?: ""
                                            )
                                        .trim()
                                        .lowercase()


                                // ---------------------------------------------
                                // Notification belongs to a specific other user
                                // ---------------------------------------------

                                if (
                                    targetUserId.isNotBlank() &&
                                    targetUserId != currentUid
                                ) {

                                    return@filter false
                                }


                                if (
                                    recipientId.isNotBlank() &&
                                    recipientId != currentUid
                                ) {

                                    return@filter false
                                }


                                // ---------------------------------------------
                                // Explicitly student/member-only notification
                                // ---------------------------------------------

                                if (
                                    targetRole == "student" ||
                                    targetRole == "member"
                                ) {

                                    return@filter false
                                }


                                true
                            }


                            // =================================================
                            // NEWEST FIRST
                            // =================================================

                            .sortedByDescending { document ->

                                document
                                    .getTimestamp(
                                        "createdAt"
                                    )
                                    ?.toDate()
                                    ?.time
                                    ?: 0L
                            }


                            // =================================================
                            // FIRESTORE -> NOTIFICATION MODEL
                            // =================================================

                            .map { document ->


                                val title =
                                    document.getString(
                                        "title"
                                    )
                                        ?: ""


                                val message =
                                    document.getString(
                                        "message"
                                    )
                                        ?: ""


                                val type =
                                    document.getString(
                                        "type"
                                    )
                                        ?: "info"


                                val isRead =
                                    document.getBoolean(
                                        "read"
                                    )
                                        ?: false


                                val createdAt =
                                    document.getTimestamp(
                                        "createdAt"
                                    )


                                val savedOrganizationId =
                                    document.getString(
                                        "organizationId"
                                    )
                                        ?: organizationId


                                val savedOrganizationName =
                                    document.getString(
                                        "organizationName"
                                    )
                                        ?: ""


                                /*
                                 * Supports old and new notification
                                 * reference field names.
                                 */

                                val referenceId =
                                    document.getString(
                                        "referenceId"
                                    )
                                        ?: document.getString(
                                            "membershipId"
                                        )
                                        ?: document.getString(
                                            "renewalId"
                                        )
                                        ?: document.getString(
                                            "postId"
                                        )
                                        ?: ""


                                /*
                                 * studentId is a REFERENCE to the
                                 * involved student.
                                 *
                                 * Do not use this field for recipient
                                 * filtering because an Admin membership
                                 * application notification also needs
                                 * to reference the student.
                                 */

                                val studentId =
                                    document.getString(
                                        "studentId"
                                    )
                                        ?: ""


                                val postId =
                                    document.getString(
                                        "postId"
                                    )
                                        ?: ""


                                NotificationModel(

                                    notificationId =
                                        document.id,

                                    title =
                                        title,

                                    message =
                                        message,

                                    timeAgo =
                                        formatTimeAgo(
                                            createdAt
                                        ),

                                    type =
                                        type,

                                    isRead =
                                        isRead,

                                    organizationId =
                                        savedOrganizationId,

                                    organizationName =
                                        savedOrganizationName,

                                    referenceId =
                                        referenceId,

                                    studentId =
                                        studentId,

                                    postId =
                                        postId
                                )
                            }


                    recyclerView.adapter =
                        NotificationAdapter(

                            notifications =
                                notifications,

                            onNotificationClick = { notification ->

                                handleNotificationClick(
                                    notification
                                )
                            },

                            onNotificationLongClick = { notification ->

                                showNotificationOptions(
                                    notification,
                                    recyclerView
                                )
                            }
                        )
                }
    }

    // =========================================================
    // NOTIFICATION CLICK
    // =========================================================

    private fun handleNotificationClick(
        notification: NotificationModel
    ) {

        markAsRead(
            notification
        )

        when (
            notification.type
                .trim()
                .lowercase()
        ) {

            // =================================================
            // MEMBERSHIP
            // =================================================

            "membership",
            "membership_application",
            "signup",
            "document",
            "resubmission" -> {

                openPendingMembers()
            }

            // =================================================
            // CHAT
            // =================================================

            "chat" -> {

                openOrganizationChat(
                    notification
                )
            }

            // =================================================
            // RENEWAL
            // =================================================

            "renewal" -> {

                openRenewalScreen(
                    notification
                )
            }

            // =================================================
            // POSTS / COMMENTS / ANNOUNCEMENTS
            // =================================================
            "comment",
            "post" -> {

                openOrganizationProfile(
                    notification
                )
            }

            "announcement" -> {

                openAdminHomeFeed(
                    notification
                )
            }
            // =================================================
            // WARNING / SUCCESS / INFO
            // =================================================

            "warning",
            "success",
            "info" -> {

                /*
                 * No navigation required.
                 * Mark as read only.
                 */
            }

            else -> {

                /*
                 * Unknown notification type:
                 * safely mark as read only.
                 */
            }
        }
    }

    // =========================================================
    // MARK AS READ
    // =========================================================

    private fun markAsRead(
        notification: NotificationModel
    ) {

        if (
            notification.notificationId.isBlank() ||
            notification.isRead
        ) {

            return
        }

        db.collection(
            "notifications"
        )
            .document(
                notification.notificationId
            )
            .update(
                "read",
                true
            )
    }

    // =========================================================
    // OPEN PENDING MEMBERS
    // =========================================================

    private fun openPendingMembers() {

        if (
            organizationId.isBlank()
        ) {

            return
        }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                MembersFragment.newInstance(
                    organizationId =
                        organizationId,

                    initialFilter =
                        "pending"
                )
            )
            .addToBackStack(
                "admin_notification_members"
            )
            .commit()
    }

    // =========================================================
    // OPEN CHAT
    // =========================================================

    private fun openOrganizationChat(
        notification: NotificationModel
    ) {

        val orgId =
            notification.organizationId
                .ifBlank {
                    organizationId
                }

        if (
            orgId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Organization information is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val intent =
            Intent(
                requireContext(),
                ChatActivity::class.java
            )

        intent.putExtra(
            ChatActivity.EXTRA_ORG_ID,
            orgId
        )

        intent.putExtra(
            ChatActivity.EXTRA_ORG_NAME,
            notification.organizationName
                .ifBlank {
                    "Organization"
                }
        )

        intent.putExtra(
            ChatActivity.EXTRA_AS_ADMIN,
            true
        )

        startActivity(
            intent
        )
    }

    // =========================================================
    // OPEN RENEWAL
    // =========================================================

    private fun openRenewalScreen(
        notification: NotificationModel
    ) {

        val orgId =
            notification.organizationId
                .ifBlank {
                    organizationId
                }

        if (
            orgId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Organization information is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val intent =
            Intent(
                requireContext(),
                RenewalRequestActivity::class.java
            )

        intent.putExtra(
            "organizationId",
            orgId
        )

        intent.putExtra(
            "organizationName",
            notification.organizationName
        )

        intent.putExtra(
            "organizationShortCode",
            ""
        )

        intent.putExtra(
            "adminUid",
            auth.currentUser?.uid
                ?: ""
        )

        startActivity(
            intent
        )
    }

    // =========================================================
    // OPEN ORGANIZATION PROFILE
    // =========================================================

    private fun openOrganizationProfile(
        notification: NotificationModel
    ) {

        val orgId =
            notification.organizationId
                .ifBlank {
                    organizationId
                }

        if (
            orgId.isBlank()
        ) {

            return
        }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                OrganizationProfileFragment.newInstance(
                    organizationId =
                        orgId,

                    organizationName =
                        notification.organizationName
                )
            )
            .addToBackStack(
                "admin_notification_org_profile"
            )
            .commit()
    }

    // =========================================================
// OPEN ADMIN HOME FEED
// =========================================================

    private fun openAdminHomeFeed(
        notification: NotificationModel
    ) {

        val orgId =
            notification.organizationId
                .ifBlank {
                    organizationId
                }

        if (orgId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Organization information is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                AdminDashboardFragment.newInstance(
                    organizationId =
                        orgId,

                    organizationName =
                        notification.organizationName
                )
            )
            .addToBackStack(
                "admin_notification_home"
            )
            .commit()
    }

    // =========================================================
    // TIME AGO
    // =========================================================

    private fun formatTimeAgo(
        timestamp: Timestamp?
    ): String {

        if (
            timestamp == null
        ) {

            return "Just now"
        }

        val createdTime =
            timestamp.toDate().time

        val currentTime =
            System.currentTimeMillis()

        val difference =
            currentTime -
                    createdTime

        val minute =
            60 * 1000L

        val hour =
            60 * minute

        val day =
            24 * hour

        return when {

            difference < minute -> {

                "Just now"
            }

            difference < hour -> {

                val minutes =
                    difference / minute

                if (
                    minutes == 1L
                ) {

                    "1 minute ago"

                } else {

                    "$minutes minutes ago"
                }
            }

            difference < day -> {

                val hours =
                    difference / hour

                if (
                    hours == 1L
                ) {

                    "1 hour ago"

                } else {

                    "$hours hours ago"
                }
            }

            difference < 7 * day -> {

                val days =
                    difference / day

                if (
                    days == 1L
                ) {

                    "Yesterday"

                } else {

                    "$days days ago"
                }
            }

            else -> {

                val formatter =
                    SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    )

                formatter.format(
                    Date(
                        createdTime
                    )
                )
            }
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroyView() {

        notificationListener?.remove()

        notificationListener =
            null

        super.onDestroyView()
    }
}