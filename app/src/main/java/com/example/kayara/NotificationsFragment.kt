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
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth

class NotificationsFragment : Fragment() {

    // =========================================================
    // FIRESTORE
    // =========================================================

    private val db by lazy {
        SupabaseDatabase.getInstance()
    }

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    // =========================================================
    // RECYCLER VIEW
    // =========================================================

    private lateinit var rvNotifications: RecyclerView
    private lateinit var notificationAdapter: NotificationAdapter

    // =========================================================
    // NOTIFICATION LIST
    // =========================================================

    private val notifications =
        mutableListOf<NotificationModel>()

    // Notifications nga gi-delete/clear sa current Super Admin only.
    private val dismissedIds =
        mutableSetOf<String>()

    // =========================================================
    // FIRESTORE LISTENERS
    // =========================================================

    private var postsListener: ListenerRegistration? = null

    private var chatListener: ListenerRegistration? = null

    private var renewalListener: ListenerRegistration? = null


    // =========================================================
    // TEMPORARY SOURCE LISTS
    // =========================================================

    private val postNotifications =
        mutableListOf<NotificationModel>()

    private val chatNotifications =
        mutableListOf<NotificationModel>()

    private val renewalNotifications =
        mutableListOf<NotificationModel>()


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
                R.layout.fragment_notifications,
                container,
                false
            )

        rvNotifications =
            view.findViewById(
                R.id.rvNotifications
            )

        rvNotifications.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        notificationAdapter =
            NotificationAdapter(
                notifications = notifications,

                onNotificationClick = { notification ->

                    handleNotificationClick(
                        notification
                    )
                },

                onNotificationLongClick = { notification ->

                    showNotificationOptions(
                        notification
                    )
                }
            )

        rvNotifications.adapter =
            notificationAdapter


        // =====================================================
        // START REAL-TIME LISTENERS
        // =====================================================

        loadDismissedNotificationsThenStart()


        return view
    }


    // =========================================================
    // LOAD DISMISSED IDS, THEN START EXISTING LISTENERS
    // =========================================================

    private fun loadDismissedNotificationsThenStart() {

        val uid =
            auth.currentUser?.uid

        if (uid.isNullOrBlank()) {

            startExistingListeners()

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

                startExistingListeners()
            }
            .addOnFailureListener {

                // Preserve the existing notification feature
                // even if dismissals cannot be read.
                startExistingListeners()
            }
    }


    private fun startExistingListeners() {

        listenToPosts()

        listenToGroupMessages()

        listenToRenewals()
    }


    // =========================================================
    // DELETE / CLEAR OPTIONS
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
                "This removes the notification from your Coordinator list only. The original post, chat, or renewal request will not be deleted."
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

        dismissedIds.add(
            notificationId
        )

        saveDismissal(
            uid =
                uid,

            notificationId =
                notificationId
        )

        rebuildNotifications()
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
                "This clears the notification list only. Original posts, chats, and renewal requests will remain."
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

        rebuildNotifications()

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
    }


    // =========================================================
    // LISTEN TO ADMIN POSTS
    // =========================================================

    private fun listenToPosts() {

        postsListener?.remove()

        postsListener =
            db.collection("posts")
                .orderBy(
                    "createdAt",
                    Query.Direction.DESCENDING
                )
                .limit(50)
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Failed to load post notifications.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }


                    postNotifications.clear()


                    for (
                    document in snapshot.documents
                    ) {

                        val postId =
                            document.id

                        val organizationName =
                            document.getString(
                                "organizationName"
                            ) ?: "Organization"

                        val posterName =
                            document.getString(
                                "posterName"
                            ) ?: "Admin"

                        val postType =
                            document.getString(
                                "postType"
                            ) ?: "text"

                        val textContent =
                            document.getString(
                                "textContent"
                            ) ?: ""

                        val caption =
                            document.getString(
                                "caption"
                            ) ?: ""

                        val createdAt =
                            document.getTimestamp(
                                "createdAt"
                            )


                        val previewText =
                            when {

                                caption.isNotBlank() ->
                                    caption

                                textContent.isNotBlank() ->
                                    textContent

                                postType == "photo" ->
                                    "Shared new photos."

                                else ->
                                    "Published a new post."
                            }

                        val currentUid =
                            auth.currentUser?.uid ?: ""

                        val createdBy =
                            document.getString("createdBy") ?: ""

// Ayaw ipakita sa Super Admin iyang kaugalingong post
                        if (
                            currentUid.isNotBlank() &&
                            createdBy == currentUid
                        ) {
                            continue
                        }


                        postNotifications.add(
                            NotificationModel(

                                notificationId =
                                    "post_$postId",

                                title =
                                    "$organizationName posted",

                                message =
                                    "$posterName: $previewText",

                                timeAgo =
                                    getTimeAgo(
                                        createdAt
                                    ),

                                type =
                                    "post",

                                isRead =
                                    false
                            )
                        )
                    }


                    rebuildNotifications()
                }
    }


    // =========================================================
    // LISTEN TO ALL GROUP CHAT MESSAGES
    // =========================================================

    private fun listenToGroupMessages() {

        chatListener?.remove()

        /*
         * chats/{organizationId}/messages/{messageId}
         *
         * collectionGroup("messages") allows the Super Admin
         * to listen to messages from ALL organization chats.
         */

        chatListener =
            db.collectionGroup("messages")
                .orderBy(
                    "createdAt",
                    Query.Direction.DESCENDING
                )
                .limit(100)
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        /*
                         * Don't crash the app if Firestore
                         * requires an index or access changes.
                         */

                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }


                    chatNotifications.clear()


                    for (
                    document in snapshot.documents
                    ) {

                        val messageId =
                            document.id

                        val currentUid =
                            auth.currentUser?.uid ?: ""

                        val senderId =
                            document.getString(
                                "senderId"
                            ) ?: ""

// Ayaw ipakita sa Super Admin
// iyang kaugalingong chat message
                        if (
                            currentUid.isNotBlank() &&
                            senderId == currentUid
                        ) {
                            continue
                        }

                        val senderName =
                            document.getString(
                                "senderName"
                            ) ?: "Member"

                        val text =
                            document.getString(
                                "text"
                            ) ?: ""

                        val createdAt =
                            document.getTimestamp(
                                "createdAt"
                            )


                        /*
                         * Parent document is:
                         *
                         * chats/{organizationId}
                         *
                         * So get the organization ID
                         * from the document path.
                         */

                        val organizationDocument =
                            document.reference
                                .parent
                                .parent

                        val organizationId =
                            organizationDocument?.id
                                ?: ""


                        /*
                         * Special room used by your app:
                         *
                         * admins_all
                         */

                        val roomName =
                            if (
                                organizationId ==
                                "admins_all"
                            ) {

                                "Org Managers Group Chat"

                            } else {

                                "Organization Group Chat"
                            }


                        chatNotifications.add(
                            NotificationModel(

                                notificationId =
                                    "chat_${organizationId}_${messageId}",

                                title =
                                    "New message",

                                message =
                                    "$senderName in $roomName: $text",

                                timeAgo =
                                    getTimeAgo(
                                        createdAt
                                    ),

                                type =
                                    "chat",

                                isRead =
                                    false
                            )
                        )
                    }


                    rebuildNotifications()
                }
    }


    // =========================================================
    // LISTEN TO RENEWAL REQUESTS
    // =========================================================

    private fun listenToRenewals() {

        renewalListener?.remove()


        /*
         * IMPORTANT:
         *
         * Ayaw gamita:
         *
         * whereEqualTo("status", "pending")
         * + orderBy("submittedAt")
         *
         * kay mahimo mangayo og Firestore composite index.
         *
         * Query pending only, then sort locally.
         */

        renewalListener =
            db.collection(
                "organizationRenewals"
            )
                .whereEqualTo(
                    "status",
                    "pending"
                )
                .addSnapshotListener { snapshot, error ->


                    if (!isAdded) {

                        return@addSnapshotListener
                    }


                    if (error != null) {

                        /*
                         * Do not destroy the other working
                         * notification sources.
                         */

                        renewalNotifications.clear()

                        rebuildNotifications()

                        return@addSnapshotListener
                    }


                    if (snapshot == null) {

                        return@addSnapshotListener
                    }


                    renewalNotifications.clear()


                    // =================================================
                    // SORT NEWEST FIRST LOCALLY
                    // =================================================

                    val sortedRenewals =
                        snapshot.documents
                            .sortedByDescending { document ->

                                document
                                    .getTimestamp(
                                        "submittedAt"
                                    )
                                    ?.toDate()
                                    ?.time
                                    ?: 0L
                            }


                    for (
                    document in sortedRenewals
                    ) {


                        val renewalId =
                            document.id


                        val organizationName =
                            document.getString(
                                "organizationName"
                            )
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: "Organization"


                        val academicYear =
                            document.getString(
                                "academicYear"
                            )
                                ?.trim()
                                .orEmpty()


                        val submittedAt =
                            document.getTimestamp(
                                "submittedAt"
                            )


                        val message =
                            if (
                                academicYear.isNotBlank()
                            ) {

                                "$organizationName submitted a renewal request for $academicYear."

                            } else {

                                "$organizationName submitted a renewal request."
                            }


                        renewalNotifications.add(

                            NotificationModel(

                                notificationId =
                                    renewalId,

                                title =
                                    "Renewal Request",

                                message =
                                    message,

                                timeAgo =
                                    getTimeAgo(
                                        submittedAt
                                    ),

                                type =
                                    "renewal",

                                isRead =
                                    false,

                                organizationId =
                                    document.getString(
                                        "organizationId"
                                    )
                                        ?: "",

                                organizationName =
                                    organizationName,

                                referenceId =
                                    renewalId
                            )
                        )
                    }


                    rebuildNotifications()
                }
    }

    // =========================================================
    // COMBINE ALL NOTIFICATIONS
    // =========================================================

    private fun rebuildNotifications() {

        notifications.clear()


        /*
         * Add only:
         *
         * 1. Posts
         * 2. Group messages
         * 3. Renewal requests
         *
         * DOCUMENT NOTIFICATIONS ARE NOT INCLUDED.
         */

        notifications.addAll(
            postNotifications
        )

        notifications.addAll(
            chatNotifications
        )

        notifications.addAll(
            renewalNotifications
        )

        notifications.removeAll { notification ->

            dismissedIds.contains(
                notification.notificationId
            )
        }


        /*
         * Newest first.
         *
         * We don't have the original Timestamp inside
         * NotificationModel, so we sort using a separate
         * source-independent order where possible.
         *
         * The source listeners already return newest first,
         * but combining them requires a simple timestamp
         * field for perfect global ordering.
         */

        notificationAdapter.notifyDataSetChanged()
    }


    // =========================================================
    // NOTIFICATION CLICK
    // =========================================================

    private fun handleNotificationClick(
        notification: NotificationModel
    ) {

        when (notification.type) {

            // =================================================
            // RENEWAL
            // =================================================

            "renewal" -> {

                val intent =
                    Intent(
                        requireContext(),
                        RenewalReviewActivity::class.java
                    )

                intent.putExtra(
                    "renewalId",
                    notification.notificationId
                )

                startActivity(intent)
            }


            // =================================================
            // GROUP CHAT
            // =================================================

            "chat" -> {

                /*
                 * notificationId:
                 *
                 * chat_{organizationId}_{messageId}
                 *
                 */

                val parts =
                    notification.notificationId
                        .removePrefix("chat_")
                        .split(
                            "_",
                            limit = 2
                        )

                if (
                    parts.isNotEmpty() &&
                    parts[0].isNotBlank()
                ) {

                    val organizationId =
                        parts[0]

                    val intent =
                        Intent(
                            requireContext(),
                            ChatActivity::class.java
                        )

                    intent.putExtra(
                        ChatActivity.EXTRA_ORG_ID,
                        organizationId
                    )

                    intent.putExtra(
                        ChatActivity.EXTRA_ORG_NAME,
                        if (
                            organizationId ==
                            "admins_all"
                        ) {
                            "Org Managers Group Chat"
                        } else {
                            "Organization Group Chat"
                        }
                    )

                    intent.putExtra(
                        ChatActivity.EXTRA_AS_ADMIN,
                        true
                    )

                    intent.putExtra(
                        ChatActivity.EXTRA_AS_SUPER_ADMIN,
                        true
                    )

                    startActivity(intent)
                }
            }


            // =================================================
            // POST
            // =================================================

            "post" -> {

                /*
                 * Posts don't have a dedicated Super Admin
                 * review page in the code you provided.
                 *
                 * For now, clicking a post notification
                 * simply marks it as read visually.
                 */

                markNotificationAsRead(
                    notification.notificationId
                )
            }
        }
    }


    // =========================================================
    // MARK AS READ
    // =========================================================

    private fun markNotificationAsRead(
        notificationId: String
    ) {

        val index =
            notifications.indexOfFirst {
                it.notificationId ==
                        notificationId
            }

        if (index >= 0) {

            notifications[index] =
                notifications[index].copy(
                    isRead = true
                )

            notificationAdapter
                .notifyItemChanged(index)
        }
    }


    // =========================================================
    // TIME AGO
    // =========================================================

    private fun getTimeAgo(
        timestamp:
        com.google.firebase.Timestamp?
    ): String {

        if (timestamp == null) {
            return "Just now"
        }


        val now =
            System.currentTimeMillis()

        val time =
            timestamp.toDate().time

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

            minutes < 60 ->
                "$minutes minute${if (minutes == 1L) "" else "s"} ago"

            hours < 24 ->
                "$hours hour${if (hours == 1L) "" else "s"} ago"

            days == 1L ->
                "Yesterday"

            days < 7 ->
                "$days days ago"

            else -> {

                val formatter =
                    SimpleDateFormat(
                        "MMM d, yyyy",
                        Locale.getDefault()
                    )

                formatter.format(
                    Date(time)
                )
            }
        }
    }


    // =========================================================
    // CLEAN UP LISTENERS
    // =========================================================

    override fun onDestroyView() {

        postsListener?.remove()
        postsListener = null

        chatListener?.remove()
        chatListener = null

        renewalListener?.remove()
        renewalListener = null


        super.onDestroyView()
    }
}