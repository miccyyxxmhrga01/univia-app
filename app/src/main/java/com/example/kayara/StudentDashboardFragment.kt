package com.example.kayara

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

class StudentDashboardFragment : Fragment() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var auth: FirebaseAuth
    private lateinit var db: SupabaseDatabase

    private var membershipListener: ListenerRegistration? = null
    private var postsListener: ListenerRegistration? = null
    private var organizationsListener: ListenerRegistration? = null
    private var orgAvatarListener: ListenerRegistration? = null

    // =========================================================
    // FEED
    // =========================================================

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var rvFeed: RecyclerView

    private val feedPosts =
        mutableListOf<FeedPostModel>()

    private var latestPostDocs:
            List<DocumentSnapshot> =
        emptyList()

    private var membershipDocs:
            List<DocumentSnapshot> =
        emptyList()

    private val validOrganizationIds =
        mutableSetOf<String>()

    // =========================================================
    // ORGANIZATION PROFILE PICTURES
    // organizationId -> avatarUrl
    // =========================================================

    private val organizationAvatarUrls =
        mutableMapOf<String, String>()

    private val likedPostIds =
        mutableSetOf<String>()

    private var likeRequestId =
        0

    // =========================================================
    // MY ORGANIZATIONS ROW
    // =========================================================

    private var myOrgsContainer:
            LinearLayout? = null

    private var btnJoinOrgShortcut:
            LinearLayout? = null

    private val maxOrganizations =
        3

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        auth =
            FirebaseAuth.getInstance()

        db =
            SupabaseDatabase.getInstance()
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
                R.layout.fragment_student_dashboard,
                container,
                false
            )

        rvFeed =
            view.findViewById(
                R.id.rvStudentFeed
            )

        rvFeed.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        feedAdapter =
            FeedAdapter(
                posts =
                    feedPosts,

                onLikeClick = { post ->

                    toggleLike(
                        post
                    )
                },

                // Ipakita ang organization name
                // imbes nga admin name.
                showOrganizationNames =
                    true
            )

        rvFeed.adapter =
            feedAdapter

        myOrgsContainer =
            view.findViewById(
                R.id.myOrgsContainer
            )

        btnJoinOrgShortcut =
            view.findViewById(
                R.id.btnJoinOrgShortcut
            )

        btnJoinOrgShortcut
            ?.setOnClickListener {

                (
                        activity as?
                                StudentDashboardActivity
                        )
                    ?.navigateToOrgsTab()
            }

        // =====================================================
        // ORGANIZATION AVATARS PARA SA FEED POSTS
        // =====================================================

        orgAvatarListener?.remove()

        orgAvatarListener =
            listenToOrgAvatars { avatars ->

                feedAdapter.setOrgAvatars(
                    avatars
                )
            }

        // =====================================================
        // START LISTENERS
        // =====================================================

        listenToOrganizations()

        listenToMemberships()

        listenToPosts()

        return view
    }

    // =========================================================
    // VALID ORGANIZATIONS
    // + CURRENT ORGANIZATION PROFILE PICTURES
    // =========================================================

    private fun listenToOrganizations() {

        organizationsListener
            ?.remove()

        organizationsListener =
            db.collection(
                "organizations"
            )
                .addSnapshotListener(useCache = true) { snapshot, error ->

                    if (
                        !isAdded ||
                        error != null ||
                        snapshot == null
                    ) {

                        return@addSnapshotListener
                    }

                    validOrganizationIds.clear()

                    organizationAvatarUrls.clear()

                    snapshot.documents
                        .forEach { document ->

                            val organizationId =
                                document.id

                            validOrganizationIds.add(
                                organizationId
                            )

                            val avatarUrl =
                                document.getString(
                                    "avatarUrl"
                                )
                                    ?.trim()
                                    ?: ""

                            organizationAvatarUrls[
                                organizationId
                            ] = avatarUrl
                        }

                    refreshMyOrgsRow()
                }
    }

    // =========================================================
    // APPROVED MEMBERSHIPS
    // para sa "My Organizations" row
    // =========================================================

    private fun listenToMemberships() {

        val currentUser =
            auth.currentUser

        if (
            currentUser == null
        ) {

            showError(
                "Please log in again."
            )

            return
        }

        membershipListener
            ?.remove()

        membershipListener =
            db.collection(
                "memberships"
            )
                .whereEqualTo(
                    "studentId",
                    currentUser.uid
                )
                .whereEqualTo(
                    "status",
                    "approved"
                )
                .addSnapshotListener(useCache = true) { snapshot, error ->

                    if (
                        !isAdded
                    ) {

                        return@addSnapshotListener
                    }

                    if (
                        error != null
                    ) {

                        showError(
                            "Failed to load memberships: ${error.message}"
                        )

                        return@addSnapshotListener
                    }

                    membershipDocs =
                        snapshot
                            ?.documents
                            ?: emptyList()

                    refreshMyOrgsRow()
                }
    }

    // =========================================================
    // MY ORGANIZATIONS ROW
    // valid organizations only
    // maximum of 3
    // =========================================================

    private fun refreshMyOrgsRow() {

        if (
            !isAdded
        ) {

            return
        }

        val container =
            myOrgsContainer
                ?: return

        container.removeAllViews()

        val myOrgs =
            membershipDocs
                .filter { document ->

                    val organizationId =
                        document.getString(
                            "organizationId"
                        )
                            ?: ""

                    validOrganizationIds
                        .contains(
                            organizationId
                        )
                }
                .sortedBy { document ->

                    document
                        .getTimestamp(
                            "approvedAt"
                        )
                        ?.toDate()
                        ?.time
                        ?: 0L
                }
                .take(
                    maxOrganizations
                )

        val avatarBackgrounds =
            listOf(
                R.drawable.bg_oc_avatar_teal,
                R.drawable.bg_oc_avatar_blue
            )

        myOrgs.forEachIndexed { index, document ->

            // =================================================
            // ORGANIZATION INFO
            // =================================================

            val organizationId =
                document.getString(
                    "organizationId"
                )
                    ?: ""

            val organizationName =
                document.getString(
                    "organizationName"
                )
                    ?: "Organization"

            val shortCode =
                document.getString(
                    "shortCode"
                )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: getInitials(
                        organizationName
                    )

            val avatarUrl =
                organizationAvatarUrls[
                    organizationId
                ]
                    .orEmpty()

            // =================================================
            // ORGANIZATION ITEM
            // =================================================

            val item =
                LinearLayout(
                    requireContext()
                )

            item.orientation =
                LinearLayout.VERTICAL

            item.gravity =
                Gravity.CENTER

            val itemParams =
                LinearLayout.LayoutParams(
                    64.dp(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            itemParams.marginEnd =
                10.dp()

            item.layoutParams =
                itemParams

            // =================================================
            // AVATAR CONTAINER
            // =================================================

            val avatarContainer =
                FrameLayout(
                    requireContext()
                )

            val avatarParams =
                LinearLayout.LayoutParams(
                    52.dp(),
                    52.dp()
                )

            avatarParams.bottomMargin =
                6.dp()

            avatarContainer.layoutParams =
                avatarParams

            // =================================================
            // INITIALS FALLBACK
            // =================================================

            val avatarInitials =
                TextView(
                    requireContext()
                )

            avatarInitials.layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

            avatarInitials.gravity =
                Gravity.CENTER

            avatarInitials.text =
                shortCode
                    .take(4)
                    .uppercase()

            avatarInitials.setTextColor(
                resources.getColor(
                    R.color.white,
                    null
                )
            )

            avatarInitials.textSize =
                10f

            avatarInitials.setTypeface(
                null,
                Typeface.BOLD
            )

            avatarInitials.setBackgroundResource(
                avatarBackgrounds[
                    index %
                            avatarBackgrounds.size
                ]
            )

            // =================================================
            // ACTUAL ORGANIZATION PROFILE PICTURE
            // =================================================

            val avatarImage =
                ImageView(
                    requireContext()
                )

            avatarImage.layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

            avatarImage.scaleType =
                ImageView.ScaleType.CENTER_CROP

            avatarImage.visibility =
                View.GONE

            avatarContainer.addView(
                avatarInitials
            )

            avatarContainer.addView(
                avatarImage
            )

            // =================================================
            // LOAD CURRENT PROFILE PICTURE
            // =================================================

            if (
                avatarUrl.isNotBlank()
            ) {

                avatarImage.visibility =
                    View.VISIBLE

                avatarInitials.visibility =
                    View.GONE

                Glide.with(this)
                    .load(
                        avatarUrl
                    )
                    .circleCrop()
                    .into(
                        avatarImage
                    )

            } else {

                Glide.with(this)
                    .clear(
                        avatarImage
                    )

                avatarImage.setImageDrawable(
                    null
                )

                avatarImage.visibility =
                    View.GONE

                avatarInitials.visibility =
                    View.VISIBLE
            }

            // =================================================
            // ORGANIZATION NAME
            // =================================================

            val label =
                TextView(
                    requireContext()
                )

            label.layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            label.gravity =
                Gravity.CENTER

            label.text =
                organizationName

            label.maxLines =
                1

            label.ellipsize =
                TextUtils.TruncateAt.END

            label.setTextColor(
                resources.getColor(
                    R.color.text_gray,
                    null
                )
            )

            label.textSize =
                9f

            label.setTypeface(
                null,
                Typeface.BOLD
            )

            // =================================================
            // ADD ITEM
            // =================================================

            item.addView(
                avatarContainer
            )

            item.addView(
                label
            )

            container.addView(
                item
            )
        }

        // =====================================================
        // JOIN ORGANIZATION BUTTON
        // =====================================================

        btnJoinOrgShortcut?.visibility =
            if (
                myOrgs.size >=
                maxOrganizations
            ) {

                View.GONE

            } else {

                View.VISIBLE
            }
    }

    // =========================================================
    // POSTS
    // =========================================================

    private fun listenToPosts() {

        postsListener
            ?.remove()

        postsListener =
            db.collection(
                "posts"
            )
                .addSnapshotListener(useCache = true) { snapshot, error ->

                    if (
                        !isAdded
                    ) {

                        return@addSnapshotListener
                    }

                    if (
                        error != null
                    ) {

                        Log.e(
                            "FEED_DEBUG",
                            "posts error",
                            error
                        )

                        showError(
                            "Failed to load posts: ${error.message}"
                        )

                        return@addSnapshotListener
                    }

                    if (
                        snapshot == null
                    ) {

                        return@addSnapshotListener
                    }

                    Log.d(
                        "FEED_DEBUG",
                        "posts total = ${snapshot.size()}"
                    )

                    // Latest una.
                    // Ang post nga wala pa'y server timestamp
                    // mahimong pinaka-una.
                    latestPostDocs =
                        snapshot.documents
                            .sortedByDescending {

                                it.getTimestamp(
                                    "createdAt"
                                )
                                    ?.toDate()
                                    ?.time
                                    ?: Long.MAX_VALUE
                            }

                    refreshFeed()
                }
    }

    // =========================================================
    // POST VISIBILITY TO STUDENTS
    // =========================================================

    private fun isPostVisibleToStudent(
        document: DocumentSnapshot
    ): Boolean {

        val audience =
            (
                    document.getString(
                        "audience"
                    )
                        ?: "everyone"
                    )
                .trim()
                .lowercase()

        return audience !=
                "admins_only"
    }

    // =========================================================
    // VISIBLE DOCUMENTS
    // =========================================================

    private fun visibleDocuments():
            List<DocumentSnapshot> {

        return latestPostDocs
            .filter {

                isPostVisibleToStudent(
                    it
                )
            }
    }

    // =========================================================
    // RENDER FEED
    // =========================================================

    private fun renderFeed() {

        if (
            !isAdded
        ) {

            return
        }

        val posts =
            visibleDocuments()
                .map { document ->

                    documentToFeedPost(
                        document,
                        likedPostIds.contains(
                            document.id
                        )
                    )
                }

        feedPosts.clear()

        feedPosts.addAll(
            posts
        )

        feedAdapter.updatePosts(
            posts.toList()
        )
    }

    // =========================================================
    // REFRESH FEED
    // =========================================================

    private fun refreshFeed() {

        renderFeed()

        fetchLikeStatus(
            visibleDocuments()
        )
    }

    // =========================================================
    // LIKE STATUS
    // =========================================================

    private fun fetchLikeStatus(
        documents: List<DocumentSnapshot>
    ) {

        val uid =
            auth.currentUser
                ?.uid
                ?: return

        if (
            documents.isEmpty()
        ) {

            return
        }

        val requestId =
            ++likeRequestId

        val newLiked =
            mutableSetOf<String>()

        var completed =
            0

        for (
        document in documents
        ) {

            db.collection(
                "posts"
            )
                .document(
                    document.id
                )
                .collection(
                    "likes"
                )
                .document(
                    uid
                )
                .get()
                .addOnSuccessListener { likeDocument ->

                    if (
                        likeDocument.exists()
                    ) {

                        newLiked.add(
                            document.id
                        )
                    }
                }
                .addOnCompleteListener {

                    completed++

                    if (
                        requestId !=
                        likeRequestId ||
                        !isAdded
                    ) {

                        return@addOnCompleteListener
                    }

                    if (
                        completed ==
                        documents.size
                    ) {

                        likedPostIds.clear()

                        likedPostIds.addAll(
                            newLiked
                        )

                        renderFeed()
                    }
                }
        }
    }

    // =========================================================
    // CONVERT FIRESTORE DOCUMENT TO FEED MODEL
    // =========================================================

    private fun documentToFeedPost(
        document: DocumentSnapshot,
        liked: Boolean
    ): FeedPostModel {

        val createdAt =
            document.getTimestamp(
                "createdAt"
            )

        return FeedPostModel(

            postId =
                document.id,

            postType =
                document.getString(
                    "postType"
                )
                    ?: "text",

            posterName =
                document.getString(
                    "posterName"
                )
                    ?: "Unknown",

            posterRole =
                document.getString(
                    "posterRole"
                )
                    ?: "",

            organizationId =
                document.getString(
                    "organizationId"
                )
                    ?: "",

            organizationName =
                document.getString(
                    "organizationName"
                )
                    ?: "",

            timeAgo =
                formatTimeAgo(
                    createdAt
                ),

            avatarInitials =
                document.getString(
                    "avatarInitials"
                )
                    ?: "",

            avatarColorStart =
                document.getString(
                    "avatarColorStart"
                )
                    ?: "#3B176A",

            avatarColorEnd =
                document.getString(
                    "avatarColorEnd"
                )
                    ?: "#7C3AED",

            audience =
                document.getString(
                    "audience"
                )
                    ?: "everyone",

            textContent =
                document.getString(
                    "textContent"
                )
                    ?: "",

            caption =
                document.getString(
                    "caption"
                )
                    ?: "",

            imageUrls =
                (
                        document.get(
                            "imageUrls"
                        ) as? List<*>
                        )
                    ?.filterIsInstance<String>()
                    ?: emptyList(),

            likeCount =
                document
                    .getLong(
                        "likeCount"
                    )
                    ?.toInt()
                    ?: 0,

            likedByCurrentUser =
                liked,

            createdBy =
                document.getString(
                    "createdBy"
                )
                    ?: "",

            commentCount =
                (
                        document.getLong(
                            "commentCount"
                        )
                            ?: 0L
                        )
                    .toInt(),

            edited =
                document.getBoolean(
                    "edited"
                )
                    ?: false
        )
    }

    // =========================================================
    // HEART / LIKE
    // =========================================================

    private fun toggleLike(
        post: FeedPostModel
    ) {

        val currentUser =
            auth.currentUser

        if (
            currentUser == null
        ) {

            showError(
                "Please log in again."
            )

            return
        }

        if (
            post.postId.isBlank()
        ) {

            return
        }

        val uid =
            currentUser.uid

        val postRef =
            db.collection(
                "posts"
            )
                .document(
                    post.postId
                )

        val likeRef =
            postRef
                .collection(
                    "likes"
                )
                .document(
                    uid
                )

        db.runTransaction { transaction ->

            val likeSnapshot =
                transaction.get(
                    likeRef
                )

            val postSnapshot =
                transaction.get(
                    postRef
                )

            val currentLikeCount =
                postSnapshot
                    .getLong(
                        "likeCount"
                    )
                    ?.toInt()
                    ?: 0

            if (
                likeSnapshot.exists()
            ) {

                transaction.delete(
                    likeRef
                )

                transaction.update(
                    postRef,
                    "likeCount",
                    maxOf(
                        0,
                        currentLikeCount - 1
                    )
                )

            } else {

                transaction.set(
                    likeRef,

                    hashMapOf(
                        "userId" to
                                uid,

                        "createdAt" to
                                FieldValue.serverTimestamp()
                    )
                )

                transaction.update(
                    postRef,
                    "likeCount",
                    currentLikeCount + 1
                )
            }

            null

        }.addOnFailureListener { error ->

            showError(
                "Unable to update like: ${error.message}"
            )
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private fun getInitials(
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
            timestamp
                .toDate()
                .time

        val difference =
            System.currentTimeMillis() -
                    createdTime

        val minute =
            60 * 1000L

        val hour =
            60 * minute

        val day =
            24 * hour

        return when {

            difference <
                    minute -> {

                "Just now"
            }

            difference <
                    hour -> {

                val minutes =
                    difference /
                            minute

                if (
                    minutes == 1L
                ) {

                    "1 minute ago"

                } else {

                    "$minutes minutes ago"
                }
            }

            difference <
                    day -> {

                val hours =
                    difference /
                            hour

                if (
                    hours == 1L
                ) {

                    "1 hour ago"

                } else {

                    "$hours hours ago"
                }
            }

            difference <
                    7 * day -> {

                val days =
                    difference /
                            day

                if (
                    days == 1L
                ) {

                    "Yesterday"

                } else {

                    "$days days ago"
                }
            }

            else -> {

                java.text.SimpleDateFormat(
                    "MMM dd, yyyy",
                    java.util.Locale.getDefault()
                )
                    .format(
                        java.util.Date(
                            createdTime
                        )
                    )
            }
        }
    }

    // =========================================================
    // ERROR
    // =========================================================

    private fun showError(
        message: String
    ) {

        if (
            !isAdded
        ) {

            return
        }

        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // =========================================================
    // DP
    // =========================================================

    private fun Int.dp(): Int {

        return (
                this *
                        resources
                            .displayMetrics
                            .density
                )
            .toInt()
    }

    // =========================================================
    // CLEAN UP
    // =========================================================

    override fun onDestroyView() {

        membershipListener
            ?.remove()

        membershipListener =
            null

        postsListener
            ?.remove()

        postsListener =
            null

        organizationsListener
            ?.remove()

        organizationsListener =
            null

        orgAvatarListener
            ?.remove()

        orgAvatarListener =
            null

        organizationAvatarUrls.clear()

        myOrgsContainer =
            null

        btnJoinOrgShortcut =
            null

        super.onDestroyView()
    }
}
