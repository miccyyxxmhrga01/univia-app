package com.example.kayara

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.Query

class ChatActivity : AppCompatActivity() {

    companion object {

        const val EXTRA_ORG_ID = "organizationId"
        const val EXTRA_ORG_NAME = "organizationName"
        const val EXTRA_AS_ADMIN = "asAdmin"

        const val EXTRA_AS_SUPER_ADMIN = "asSuperAdmin"

        const val ADMINS_ROOM_ID = "admins_all"

        private const val MAX_MESSAGES = 200
    }

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        SupabaseDatabase.getInstance()

    private var messagesListener:
            ListenerRegistration? = null

    private var organizationId = ""
    private var organizationName = ""

    private var asAdmin = false
    private var asSuperAdmin = false

    private var currentUid = ""

    // =========================================================
    // SENDER INFO
    // =========================================================

    private var senderName = "Member"
    private var senderRole = ""
    private var senderPhotoUrl = ""

    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var chatAdapter:
            ChatAdapter

    private lateinit var rvChat:
            RecyclerView

    private lateinit var etMessage:
            EditText

    private lateinit var btnSend:
            ImageView

    private lateinit var tvEmpty:
            TextView

    private var lastCount = -1

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_chat
        )

        organizationId =
            intent
                .getStringExtra(
                    EXTRA_ORG_ID
                )
                ?.trim()
                ?: ""

        organizationName =
            intent
                .getStringExtra(
                    EXTRA_ORG_NAME
                )
                ?.trim()
                ?: ""

        asAdmin =
            intent.getBooleanExtra(
                EXTRA_AS_ADMIN,
                false
            )

        asSuperAdmin =
            intent.getBooleanExtra(
                EXTRA_AS_SUPER_ADMIN,
                false
            )

        val uid =
            auth.currentUser?.uid

        if (
            uid == null ||
            organizationId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Unable to open the chat.",
                Toast.LENGTH_SHORT
            ).show()

            finish()

            return
        }

        currentUid =
            uid

        // =====================================================
        // VIEWS
        // =====================================================

        rvChat =
            findViewById(
                R.id.rvChat
            )

        etMessage =
            findViewById(
                R.id.etChatMessage
            )

        btnSend =
            findViewById(
                R.id.btnChatSend
            )

        tvEmpty =
            findViewById(
                R.id.tvChatEmpty
            )

        findViewById<ImageView>(
            R.id.btnChatBack
        ).setOnClickListener {

            finish()
        }

        // =====================================================
        // HEADER
        // =====================================================

        findViewById<TextView>(
            R.id.tvChatTitle
        ).text =
            organizationName.ifBlank {
                "Organization"
            }

        findViewById<TextView>(
            R.id.tvChatOrgAvatar
        ).text =
            getOrgInitials(
                organizationName
            )

        loadOrganizationAvatar()

        // =====================================================
        // ADMINS GROUP CHAT HEADER
        // =====================================================

        if (
            organizationId ==
            ADMINS_ROOM_ID
        ) {

            findViewById<TextView>(
                R.id.tvChatTitle
            ).text =
                "Org Managers Group Chat"

            findViewById<TextView>(
                R.id.tvChatOrgAvatar
            ).text =
                "ADM"

            findViewById<TextView>(
                R.id.tvChatSubtitle
            ).text =
                "Coordinator + all organization admins"

            etMessage.hint =
                "Message all admins..."
        }

        // =====================================================
        // CHAT LIST
        // =====================================================

        chatAdapter =
            ChatAdapter(
                currentUid
            )

        rvChat.layoutManager =
            LinearLayoutManager(
                this
            ).apply {

                stackFromEnd =
                    true
            }

        rvChat.adapter =
            chatAdapter

        // =====================================================
        // SEND BUTTON
        // =====================================================

        btnSend.isEnabled =
            false

        btnSend.alpha =
            0.5f

        btnSend.setOnClickListener {

            sendMessage()
        }

        // =====================================================
        // VERIFY ACCESS
        // =====================================================

        verifyAccessAndStart()
    }

    // =========================================================
    // ORGANIZATION AVATAR
    // =========================================================

    private fun loadOrganizationAvatar() {

        if (
            organizationId ==
            ADMINS_ROOM_ID
        ) {

            return
        }

        db.collection(
            "organizations"
        )
            .document(
                organizationId
            )
            .get()
            .addOnSuccessListener { document ->

                if (
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }

                val avatarUrl =
                    document.getString(
                        "avatarUrl"
                    )
                        ?: ""

                if (
                    avatarUrl.isNotBlank()
                ) {

                    val img =
                        findViewById<ImageView>(
                            R.id.imgChatOrgAvatar
                        )

                    img.visibility =
                        View.VISIBLE

                    findViewById<TextView>(
                        R.id.tvChatOrgAvatar
                    ).visibility =
                        View.GONE

                    Glide.with(
                        this
                    )
                        .load(
                            avatarUrl
                        )
                        .circleCrop()
                        .into(
                            img
                        )
                }
            }
    }

    // =========================================================
    // VERIFY ACCESS
    // =========================================================

    private fun verifyAccessAndStart() {
        db.accountAccess().addOnSuccessListener(this) accessLoaded@{ access ->
            val managed = access.organizations.firstOrNull { it.id == organizationId }
            val managerRoom = organizationId == ADMINS_ROOM_ID
            if ((asSuperAdmin && !access.coordinator) ||
                (asAdmin && !access.coordinator && (if (managerRoom) access.organizations.isEmpty() else managed == null))) {
                Toast.makeText(this, R.string.manager_access_missing, Toast.LENGTH_LONG).show()
                finish()
                return@accessLoaded
            }
            if (asSuperAdmin || asAdmin) {
                senderName = if (access.coordinator) "Coordinator" else managed?.name ?: "Org Manager"
                senderRole = if (access.coordinator) "Coordinator" else "ADMIN"
                AccountAccessGuard.attach(this) { latest ->
                    if (asSuperAdmin) latest.coordinator
                    else latest.coordinator || if (managerRoom) latest.organizations.isNotEmpty()
                        else latest.organizations.any { it.id == organizationId }
                }
                startChat()
                return@accessLoaded
            }
            db.collection("users").document(currentUid).get().addOnSuccessListener(this) { profile ->
                senderPhotoUrl = profile.getString("profileImageUrl").orEmpty()
                senderName = profile.getString("fullName") ?: "Student"
                db.collection("memberships").document("${currentUid}_$organizationId").get()
                    .addOnSuccessListener(this) { membership ->
                        if (membership.getString("status") == "approved") {
                            senderRole = if (membership.getString("role") == "officer") membership.getString("position").orEmpty() else ""
                            startChat()
                        } else {
                            Toast.makeText(this, "Only approved members can join this chat.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }.addOnFailureListener(this) { chatAccessFailed(it) }
            }.addOnFailureListener(this) { chatAccessFailed(it) }
        }.addOnFailureListener(this) { chatAccessFailed(it) }
    }

    private fun chatAccessFailed(error: Exception) {
        Toast.makeText(this, error.message ?: getString(R.string.access_load_failed), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun startChat() {

        btnSend.isEnabled =
            true

        btnSend.alpha =
            1f

        listenToMessages()
    }

    // =========================================================
    // LISTEN TO MESSAGES
    // =========================================================

    private fun listenToMessages() {

        messagesListener?.remove()

        messagesListener =
            db.collection(
                "chats"
            )
                .document(
                    organizationId
                )
                .collection(
                    "messages"
                )
                .orderBy(
                    "createdAt",
                    Query.Direction.ASCENDING
                )
                .limitToLast(
                    MAX_MESSAGES.toLong()
                )
                .addSnapshotListener { snapshot, error ->

                    if (
                        isDestroyed
                    ) {

                        return@addSnapshotListener
                    }

                    if (
                        error != null
                    ) {

                        Toast.makeText(
                            this,
                            "Failed to load messages: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null
                    ) {

                        return@addSnapshotListener
                    }

                    val messages =
                        snapshot.documents.map { document ->

                            val time =
                                document
                                    .getTimestamp(
                                        "createdAt",
                                        DocumentSnapshot
                                            .ServerTimestampBehavior
                                            .ESTIMATE
                                    )
                                    ?.toDate()
                                    ?.time
                                    ?: System.currentTimeMillis()

                            ChatMessageModel(

                                id =
                                    document.id,

                                senderId =
                                    document.getString(
                                        "senderId"
                                    )
                                        ?: "",

                                senderName =
                                    document.getString(
                                        "senderName"
                                    )
                                        ?: "Member",

                                senderRole =
                                    document.getString(
                                        "senderRole"
                                    )
                                        ?: "",

                                senderPhotoUrl =
                                    document.getString(
                                        "senderPhotoUrl"
                                    )
                                        ?: "",

                                text =
                                    document.getString(
                                        "text"
                                    )
                                        ?: "",

                                timeMillis =
                                    time
                            )
                        }

                    chatAdapter.submit(
                        messages
                    )

                    tvEmpty.visibility =
                        if (
                            messages.isEmpty()
                        ) {

                            View.VISIBLE

                        } else {

                            View.GONE
                        }

                    if (
                        messages.size != lastCount &&
                        messages.isNotEmpty()
                    ) {

                        rvChat.scrollToPosition(
                            messages.size - 1
                        )
                    }

                    lastCount =
                        messages.size
                }
    }

    // =========================================================
    // SEND MESSAGE
    // =========================================================

    private fun sendMessage() {

        val text =
            etMessage.text
                .toString()
                .trim()

        if (
            text.isEmpty()
        ) {

            return
        }

        etMessage.text.clear()

        val data =
            hashMapOf<String, Any>(

                "senderId" to
                        currentUid,

                "senderName" to
                        senderName,

                "senderRole" to
                        senderRole,

                "senderPhotoUrl" to
                        senderPhotoUrl,

                "text" to
                        text,

                "createdAt" to
                        FieldValue.serverTimestamp()
            )

        db.collection(
            "chats"
        )
            .document(
                organizationId
            )
            .collection(
                "messages"
            )
            .add(
                data
            )
            .addOnSuccessListener { messageDocument ->

                // Student/member message ra ang
                // mo-notify sa Admin.

                if (
                    !asAdmin &&
                    !asSuperAdmin &&
                    organizationId !=
                    ADMINS_ROOM_ID
                ) {

                    val preview =
                        if (
                            text.length > 70
                        ) {

                            text.take(
                                70
                            ) + "..."

                        } else {

                            text
                        }

                    AdminNotificationHelper.create(

                        organizationId =
                            organizationId,

                        organizationName =
                            organizationName,

                        title =
                            "New Group Chat Message",

                        message =
                            "$senderName: $preview",

                        type =
                            "chat",

                        referenceId =
                            messageDocument.id,

                        studentId =
                            currentUid
                    )
                }
            }
            .addOnFailureListener { error ->

                if (
                    isDestroyed
                ) {

                    return@addOnFailureListener
                }

                Toast.makeText(
                    this,
                    "Message not sent: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // ORGANIZATION INITIALS
    // =========================================================

    private fun getOrgInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        return when {

            words.isEmpty() -> {

                "ORG"
            }

            words.size == 1 -> {

                words[0]
                    .take(3)
                    .uppercase()
            }

            else -> {

                words
                    .take(3)
                    .map {
                        it.first()
                    }
                    .joinToString(
                        ""
                    )
                    .uppercase()
            }
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        messagesListener?.remove()

        messagesListener =
            null

        super.onDestroy()
    }
}