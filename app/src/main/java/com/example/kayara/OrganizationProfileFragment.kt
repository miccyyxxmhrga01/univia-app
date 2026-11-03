package com.example.kayara

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.SetOptions
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog


class OrganizationProfileFragment : Fragment() {

    // =========================================================
    // ORGANIZATION
    // =========================================================

    private var organizationId = ""
    private var organizationName = ""

    // =========================================================
    // ACCOUNT / ADVISER
    // =========================================================

    private var adviserName = ""
    private var adviserName2 = ""
    private var adviserEmail = ""

    // =========================================================
    // ORGANIZATION INFORMATION
    // =========================================================

    private var organizationCategory =
        "Student Organization"

    private var organizationDescription =
        "Organization profile and activities."

    private var organizationFounded =
        "Not specified"

    // =========================================================
    // PROFILE IMAGES
    // =========================================================

    private var imgCoverPhoto: ImageView? = null
    private var imgOrgAvatarPhoto: ImageView? = null
    private var tvOrgAvatarInitials: TextView? = null

    // =========================================================
    // POSTS
    // =========================================================

    private lateinit var rvOrgProfileFeed: RecyclerView
    private lateinit var feedAdapter: FeedAdapter

    private val ownPosts =
        mutableListOf<FeedPostModel>()

    private var postsListener: ListenerRegistration? = null
    private var permissionListener: ListenerRegistration? = null

    // Organization profile pictures (organizationId -> avatarUrl)
    private var orgAvatarListener: ListenerRegistration? = null

    // =========================================================
    // ORGANIZATION (real-time) + MEMBERS
    // =========================================================

    private var organizationListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null

    private var eventsCountListener: ListenerRegistration? = null

    private val orgMembers =
        mutableListOf<OrgMemberItem>()

    private lateinit var memberAdapter: OrgMemberAdapter

    private var tvMembersSummary: TextView? = null

    // NEW: real-time nga profile sa matag member
    private val memberUserListeners =
        mutableMapOf<String, ListenerRegistration>()

    private val memberUsers =
        mutableMapOf<String, DocumentSnapshot>()

    private var memberDocs: List<DocumentSnapshot> =
        emptyList()

    // =========================================================
    // TABS
    // =========================================================

    private lateinit var contentPosts: LinearLayout
    private lateinit var contentAbout: LinearLayout
    private lateinit var contentMembers: LinearLayout

    private lateinit var tabPosts: LinearLayout
    private lateinit var tabAbout: LinearLayout
    private lateinit var tabMembers: LinearLayout

    private lateinit var underlinePosts: View
    private lateinit var underlineAbout: View
    private lateinit var underlineMembers: View

    // =========================================================
    // FIREBASE
    // =========================================================

    private val db =
        SupabaseDatabase.getInstance()

    private val auth =
        FirebaseAuth.getInstance()
        private var btnEditProfile: LinearLayout? = null


    // ========================================================
    // AVATAR PICKER
    // =========================================================

    private val pickAvatarLauncher =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {
                saveOrganizationImage(
                    uri,
                    "avatar"
                )
            }
        }

    // =========================================================
    // COVER PICKER
    // =========================================================

    private val pickCoverLauncher =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {
                saveOrganizationImage(
                    uri,
                    "cover"
                )
            }
        }

    // =========================================================
    // EDIT PROFILE RESULT
    // =========================================================

    private val editProfileLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

                val data =
                    result.data

                if (data != null) {

                    organizationName =
                        data.getStringExtra("orgName")
                            ?: organizationName

                    organizationCategory =
                        data.getStringExtra("category")
                            ?: organizationCategory

                    organizationDescription =
                        data.getStringExtra("description")
                            ?: organizationDescription

                    organizationFounded =
                        data.getStringExtra("founded")
                            ?: organizationFounded

                    adviserName =
                        data.getStringExtra("adviserName1")
                            ?: data.getStringExtra("adviserName")
                                    ?: adviserName

                    adviserName2 =
                        data.getStringExtra("adviserName2")
                            ?: adviserName2

                    updateProfileInformation()

                    saveProfileInformation()

                    updateFirestoreProfileInformation()
                }
            }
        }

    // =========================================================
    // INSTANCE
    // =========================================================

    companion object {

        private const val ARG_ORGANIZATION_ID =
            "organizationId"

        private const val ARG_ORGANIZATION_NAME =
            "organizationName"

        // mga value sa "category" nga status ra, dili tinuod nga category
        private val STATUS_CATEGORIES =
            setOf(
                "renewal_due",
                "new_request",
                "normal",
                "available",
                "joined",
                "pending"
            )

        fun newInstance(
            organizationId: String,
            organizationName: String
        ): OrganizationProfileFragment {

            val fragment =
                OrganizationProfileFragment()

            val bundle =
                Bundle()

            bundle.putString(
                ARG_ORGANIZATION_ID,
                organizationId
            )

            bundle.putString(
                ARG_ORGANIZATION_NAME,
                organizationName
            )

            fragment.arguments =
                bundle

            return fragment
        }
    }

    private fun isStatusCategory(value: String): Boolean {
        return STATUS_CATEGORIES.contains(
            value.trim().lowercase()
        )
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
            arguments?.getString(ARG_ORGANIZATION_ID)?.trim()
                ?: ""

        organizationName =
            arguments?.getString(ARG_ORGANIZATION_NAME)?.trim()
                ?: ""

        adviserEmail = ""

        loadSavedProfileInformation()
    }

    // =========================================================
    // CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view =
            inflater.inflate(
                R.layout.fragment_organization_profile,
                container,
                false
            )

        setupViews(view)

        initializeOrganization()

        return view
    }

    // =========================================================
    // INITIALIZE ORGANIZATION
    // =========================================================

    private fun initializeOrganization() {

        if (organizationId.isNotBlank()) {

            Log.d(
                "OrgProfile",
                "Using organization ID: [$organizationId]"
            )

            loadOrganizationFromFirestore()

            loadSavedImages()

            listenToOrganizationPosts()

            listenToMembers()

            listenToEventCount()

            return
        }

        val currentUser =
            auth.currentUser

        if (currentUser == null) {

            updateProfileInformation()

            return
        }

        db.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                val userOrganizationId =
                    document.getString("organizationId")?.trim()
                        ?: ""

                val userOrganizationName =
                    document.getString("organizationName")?.trim()
                        ?: ""

                if (userOrganizationId.isNotBlank()) {

                    organizationId =
                        userOrganizationId
                }

                if (
                    organizationName.isBlank() &&
                    userOrganizationName.isNotBlank()
                ) {

                    organizationName =
                        userOrganizationName
                }


                Log.d(
                    "OrgProfile",
                    "Loaded user organizationId = [$organizationId]"
                )

                Log.d(
                    "OrgProfile",
                    "Loaded user organizationName = [$organizationName]"
                )

                loadSavedProfileInformation()

                loadOrganizationFromFirestore()

                loadSavedImages()

                listenToOrganizationPosts()

                listenToMembers()

                listenToEventCount()

                updateProfileInformation()
            }
            .addOnFailureListener { error ->

                Log.e(
                    "OrgProfile",
                    "Unable to load current user.",
                    error
                )

                updateProfileInformation()
            }
    }

    // =========================================================
    // SETUP VIEWS
    // =========================================================

    private fun setupViews(
        view: View
    ) {

        // =====================================================
        // PROFILE IMAGES
        // =====================================================

        imgCoverPhoto =
            view.findViewById(R.id.imgCoverPhoto)

        imgOrgAvatarPhoto =
            view.findViewById(R.id.imgOrgAvatarPhoto)

        tvOrgAvatarInitials =
            view.findViewById(R.id.tvOrgAvatarInitials)

        // =====================================================
        // CHANGE COVER
        // =====================================================

        view.findViewById<FrameLayout>(
            R.id.btnChangeCover
        ).setOnClickListener {

            pickCoverLauncher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts
                        .PickVisualMedia
                        .ImageOnly
                )
            )
        }

        // =====================================================
        // CHANGE AVATAR
        // =====================================================

        view.findViewById<FrameLayout>(
            R.id.btnChangeAvatar
        ).setOnClickListener {

            pickAvatarLauncher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts
                        .PickVisualMedia
                        .ImageOnly
                )
            )
        }

        // =====================================================
// EDIT PROFILE
// =====================================================

        btnEditProfile =
            view.findViewById(R.id.btnEditProfile)

// Hide first while checking permission
        btnEditProfile?.visibility =
            View.GONE

        btnEditProfile?.isClickable =
            false

        btnEditProfile?.isFocusable =
            false

        checkEditPermission()

        btnEditProfile?.setOnClickListener {

            val intent =
                Intent(
                    requireContext(),
                    EditOrgProfileActivity::class.java
                )

            intent.putExtra(
                "organizationId",
                organizationId
            )

            intent.putExtra(
                "orgName",
                organizationName
            )

            intent.putExtra(
                "category",
                organizationCategory
            )

            intent.putExtra(
                "description",
                organizationDescription
            )

            intent.putExtra(
                "founded",
                organizationFounded
            )

            intent.putExtra(
                "adviserName",
                adviserName
            )

            intent.putExtra(
                "adviserName1",
                adviserName
            )

            intent.putExtra(
                "adviserName2",
                adviserName2
            )

            editProfileLauncher.launch(
                intent
            )
        }

        // =====================================================
        // TABS
        // =====================================================

        contentPosts =
            view.findViewById(R.id.contentPosts)

        contentAbout =
            view.findViewById(R.id.contentAbout)

        contentMembers =
            view.findViewById(R.id.contentMembers)

        tabPosts =
            view.findViewById(R.id.tabPosts)

        tabAbout =
            view.findViewById(R.id.tabAbout)

        tabMembers =
            view.findViewById(R.id.tabMembers)

        underlinePosts =
            view.findViewById(R.id.underlinePosts)

        underlineAbout =
            view.findViewById(R.id.underlineAbout)

        underlineMembers =
            view.findViewById(R.id.underlineMembers)

        tabPosts.setOnClickListener {
            showTab("posts")
        }

        tabAbout.setOnClickListener {
            showTab("about")
        }

        tabMembers.setOnClickListener {
            showTab("members")
        }

        // =====================================================
        // POSTS RECYCLER VIEW
        // =====================================================

        rvOrgProfileFeed =
            view.findViewById(R.id.rvOrgProfileFeed)

        rvOrgProfileFeed.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        feedAdapter =
            FeedAdapter(
                posts = ownPosts,

                onLikeClick = { post ->

                    handleLocalLike(
                        post
                    )
                },

                onDeleteClick = { post ->

                    confirmDeletePost(
                        post
                    )
                }
            )

        rvOrgProfileFeed.adapter =
            feedAdapter

        // Organization profile pictures para sa post avatars
        orgAvatarListener?.remove()

        orgAvatarListener =
            listenToOrgAvatars { avatars ->
                feedAdapter.setOrgAvatars(avatars)
            }

        // =====================================================
        // MEMBERS
        // =====================================================

        val rvMembers =
            view.findViewById<RecyclerView>(
                R.id.rvOrgProfileMembers
            )

        rvMembers.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        memberAdapter =
            OrgMemberAdapter(orgMembers)


        rvMembers.adapter =
            memberAdapter

        // "Members (N) · X officers" sa ibabaw sa listahan
        val summary = TextView(requireContext())

        summary.text = "Members (0)"
        summary.textSize = 13f
        summary.setTypeface(null, android.graphics.Typeface.BOLD)
        summary.setTextColor(
            resources.getColor(R.color.dark_blue, null)
        )

        val density = resources.displayMetrics.density

        summary.setPadding(
            (4 * density).toInt(),
            (14 * density).toInt(),
            (4 * density).toInt(),
            (6 * density).toInt()
        )

        contentMembers.addView(summary, 0)

        tvMembersSummary = summary

        // =====================================================
// EVENTS
// =====================================================

        view.findViewById<LinearLayout>(
            R.id.layoutStatEvents
        ).setOnClickListener {

            if (organizationId.isBlank()) {

                Toast.makeText(
                    requireContext(),
                    "Organization not found.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }


            val intent =
                Intent(
                    requireContext(),
                    EventsActivity::class.java
                )


            intent.putExtra(
                "organizationId",
                organizationId
            )


            intent.putExtra(
                "organizationName",
                organizationName
            )


            startActivity(
                intent
            )
        }

        // =====================================================
        // DEFAULT TAB
        // =====================================================

        showTab("posts")

        updateProfileInformation()
    }

    // =========================================================
    // NEW: LISTEN TO MEMBERS (approved ra, real-time)
    // Matag member naay listener sa iyang "users" document,
    // aron mausab dayon ang ngalan, course, year level.
    // =========================================================

    private fun listenToMembers() {
        membersListener?.remove()
        clearMemberUserListeners()
        memberDocs = emptyList()
        if (organizationId.isBlank()) return
        membersListener = db.collection("memberships")
            .whereEqualTo("organizationId", organizationId)
            .whereEqualTo("status", "approved")
            .addSnapshotListener members@{ snapshot, error ->
                if (view == null) return@members
                if (error != null) {
                    Log.e("OrgProfile", "Unable to load members", error)
                    return@members
                }
                memberDocs = snapshot?.documents ?: return@members
                val ids = memberDocs.mapNotNull { it.getString("studentId")?.takeIf(String::isNotBlank) }.toSet()
                memberUserListeners.keys.filter { it !in ids }.forEach { id ->
                    memberUserListeners.remove(id)?.remove()
                    memberUsers.remove(id)
                }
                ids.filter { it !in memberUserListeners }.forEach { id ->
                    memberUserListeners[id] = db.collection("users").document(id)
                        .addSnapshotListener profile@{ profile, profileError ->
                            if (view == null || id !in memberUserListeners) return@profile
                            if (profileError != null || profile == null || !profile.exists()) memberUsers.remove(id)
                            else memberUsers[id] = profile
                            rebuildMembers()
                        }
                }
                rebuildMembers()
            }
    }

    private fun rebuildMembers() {
        if (view == null) return
        val items = memberDocs.map { membership ->
            val user = memberUsers[membership.getString("studentId")]
            val position = membership.getString("position").orEmpty().trim()
            val officer = membership.getString("role").equals("officer", ignoreCase = true) || position.isNotBlank()
            val label = if (officer) position.ifBlank { "Officer" } else "Member"
            val info = listOfNotNull(user?.getString("course")?.takeIf(String::isNotBlank),
                user?.getString("yearLevel")?.takeIf(String::isNotBlank)).joinToString(" · ")
            OrgMemberItem(
                name = user?.getString("fullName")?.takeIf(String::isNotBlank)
                    ?: membership.getString("fullName") ?: "Student",
                subtitle = if (info.isBlank()) label else "$info - $label",
                isOfficer = officer,
                photoUrl = user?.getString("profileImageUrl").orEmpty(),
                rank = officerRank(position, officer), position = position)
        }.sortedWith(compareBy<OrgMemberItem> { it.rank }.thenBy { it.name.lowercase() })
        orgMembers.clear()
        orgMembers.addAll(items)
        memberAdapter.notifyDataSetChanged()
        val officers = items.count { it.isOfficer }
        tvMembersSummary?.text = "Members (${items.size})" + if (officers == 0) ""
            else " · $officers " + if (officers == 1) "officer" else "officers"
        view?.findViewById<TextView>(R.id.tvStatMembers)?.text = items.size.toString()
    }

    private fun clearMemberUserListeners() {
        memberUserListeners.values.forEach { it.remove() }
        memberUserListeners.clear()
        memberUsers.clear()
    }

    private fun checkEditPermission() {
        permissionListener?.remove()
        btnEditProfile?.visibility = View.GONE
        btnEditProfile?.isClickable = false
        if (auth.currentUser == null) return
        permissionListener = db.watchAccountAccess { access, _ ->
            if (view == null) return@watchAccountAccess
            val allowed = access?.organizations?.any { it.id == organizationId } == true
            btnEditProfile?.visibility = if (allowed) View.VISIBLE else View.GONE
            btnEditProfile?.isClickable = allowed
        }
    }


    // =========================================================
    // LOAD ORGANIZATION (real-time)
    // =========================================================

    private fun loadOrganizationFromFirestore() {

        organizationListener?.remove()

        if (organizationId.isBlank()) {

            updateProfileInformation()

            return
        }

        organizationListener =
            db.collection("organizations")
                .document(organizationId)
                .addSnapshotListener { document, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Log.e(
                            "OrgProfile",
                            "Organization database error",
                            error
                        )

                        updateProfileInformation()

                        return@addSnapshotListener
                    }

                    if (document == null || !document.exists()) {

                        Log.w(
                            "OrgProfile",
                            "Organization does not exist: $organizationId"
                        )

                        updateProfileInformation()

                        return@addSnapshotListener
                    }

                    // =============================================
                    // ORGANIZATION NAME
                    // "orgName" ang tinuod nga field
                    // =============================================

                    (
                            document.getString("orgName")
                                ?.takeIf { it.isNotBlank() }
                                ?: document.getString("organizationName")
                                    ?.takeIf { it.isNotBlank() }
                            )
                        ?.let {

                            organizationName =
                                it.trim()
                        }

                    // =============================================
                    // CATEGORY
                    // ayaw ipakita ang status (renewal_due ug uban pa)
                    // =============================================

                    (
                            document.getString("profileCategory")
                                ?.takeIf { it.isNotBlank() }
                                ?: document.getString("category")
                                    ?.takeIf {
                                        it.isNotBlank() && !isStatusCategory(it)
                                    }
                            )
                        ?.let {

                            organizationCategory =
                                it
                        }

                    // =============================================
                    // DESCRIPTION
                    // =============================================

                    document.getString("description")
                        ?.takeIf { it.isNotBlank() }
                        ?.let {

                            organizationDescription =
                                it
                        }

                    // =============================================
// ADVISER 1
// adviserName1 = new field
// adviserName = old compatibility field
// =============================================

                    adviserName =
                        document.getString("adviserName1")
                            ?.takeIf { it.isNotBlank() }
                            ?: document.getString("adviserName")
                                ?.takeIf { it.isNotBlank() }
                                    ?: ""

// =============================================
// ADVISER 2
// =============================================

                    adviserName2 =
                        document.getString("adviserName2")
                            ?.takeIf { it.isNotBlank() }
                            ?: ""

                    // =============================================
                    // ADVISER EMAIL
                    // =============================================

                    document.getString("adviserEmail")
                        ?.takeIf { it.isNotBlank() }
                        ?.let {

                            adviserEmail =
                                it
                        }

                    // =============================================
                    // FOUNDED
                    // =============================================

                    document.getString("founded")
                        ?.takeIf { it.isNotBlank() }
                        ?.let {

                            organizationFounded =
                                it
                        }

                    // =============================================
                    // PROFILE PICTURE + COVER
                    // =============================================

                    document.getString("avatarUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { url ->

                            imgOrgAvatarPhoto?.let { img ->

                                Glide.with(this)
                                    .load(url)
                                    .circleCrop()
                                    .into(img)

                                img.visibility =
                                    View.VISIBLE

                                tvOrgAvatarInitials?.visibility =
                                    View.GONE
                            }
                        }

                    document.getString("coverUrl")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { url ->

                            imgCoverPhoto?.let { img ->

                                Glide.with(this)
                                    .load(url)
                                    .centerCrop()
                                    .into(img)

                                img.visibility =
                                    View.VISIBLE
                            }
                        }

                    updateProfileInformation()

                    saveProfileInformation()
                }
    }

    // =========================================================
// LISTEN TO EVENT COUNT
// Count actual documents inside "events" collection.
// =========================================================

    private fun listenToEventCount() {

        eventsCountListener?.remove()

        eventsCountListener =
            null


        if (organizationId.isBlank()) {

            view?.findViewById<TextView>(
                R.id.tvStatEvents
            )?.text =
                "0"

            return
        }


        eventsCountListener =
            db.collection("events")
                .whereEqualTo(
                    "organizationId",
                    organizationId
                )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }


                    if (error != null) {

                        Log.e(
                            "OrgProfile",
                            "Unable to load event count.",
                            error
                        )

                        return@addSnapshotListener
                    }


                    val eventCount =
                        snapshot?.documents?.size
                            ?: 0


                    view?.findViewById<TextView>(
                        R.id.tvStatEvents
                    )?.text =
                        eventCount.toString()
                }
    }

    // =========================================================
    // LISTEN TO ORGANIZATION POSTS
    //
    // Match organizationId OR organizationName
    // OR createdBy (the logged-in user's uid).
    // =========================================================

    private fun listenToOrganizationPosts() {

        postsListener?.remove()

        if (
            organizationId.isBlank() &&
            organizationName.isBlank() &&
            auth.currentUser == null
        ) {

            Log.e(
                "OrgProfile",
                "Cannot load posts because organizationId, organizationName and user are empty."
            )

            return
        }

        Log.d("OrgProfile", "======================================")
        Log.d("OrgProfile", "START LOADING ORGANIZATION POSTS")
        Log.d("OrgProfile", "Profile organizationId = [$organizationId]")
        Log.d("OrgProfile", "Profile organizationName = [$organizationName]")
        Log.d("OrgProfile", "======================================")

        postsListener =
            db.collection("posts")
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Log.e(
                            "OrgProfile",
                            "Failed to load posts.",
                            error
                        )

                        Toast.makeText(
                            requireContext(),
                            "Failed to load posts.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    Log.d(
                        "OrgProfile",
                        "TOTAL POSTS IN FIRESTORE = ${snapshot.documents.size}"
                    )

                    val currentUid =
                        auth.currentUser?.uid
                            ?: ""

                    val matchingDocuments =
                        snapshot.documents
                            .filter { document ->

                                val postOrganizationId =
                                    document.getString("organizationId")?.trim()
                                        ?: ""

                                val postOrganizationName =
                                    document.getString("organizationName")?.trim()
                                        ?: ""

                                val postCreatedBy =
                                    document.getString("createdBy")
                                        ?: ""

                                val idMatches =
                                    organizationId.isNotBlank() &&
                                            postOrganizationId == organizationId

                                val nameMatches =
                                    organizationName.isNotBlank() &&
                                            postOrganizationName.equals(
                                                organizationName,
                                                ignoreCase = true
                                            )

                                val createdByMatches =
                                    currentUid.isNotBlank() &&
                                            postCreatedBy == currentUid &&
                                            postOrganizationId != "SUPER_ADMIN"

                                Log.d(
                                    "OrgProfile",
                                    "POST ${document.id} | idMatch=$idMatches nameMatch=$nameMatches createdByMatch=$createdByMatches"
                                )

                                idMatches ||
                                        nameMatches ||
                                        createdByMatches
                            }
                            .sortedByDescending { document ->

                                document
                                    .getTimestamp("createdAt")
                                    ?.toDate()
                                    ?.time
                                    ?: 0L
                            }

                    Log.d(
                        "OrgProfile",
                        "MATCHING POSTS = ${matchingDocuments.size}"
                    )

                    val newPosts =
                        matchingDocuments.map { document ->

                            val createdAt =
                                document
                                    .getTimestamp("createdAt")
                                    ?.toDate()
                                    ?.time
                                    ?: 0L

                            val savedOrganizationName =
                                document.getString("organizationName")?.trim()
                                    ?: organizationName

                            FeedPostModel(

                                postId =
                                    document.id,

                                postType =
                                    document.getString("postType") ?: "text",

                                posterName =
                                    document.getString("posterName")
                                        ?: savedOrganizationName,

                                posterRole =
                                    document.getString("posterRole") ?: "Admin",

                                organizationId =
                                    document.getString("organizationId")
                                        ?: organizationId,

                                organizationName =
                                    savedOrganizationName,

                                timeAgo =
                                    getTimeAgo(createdAt),

                                avatarInitials =
                                    document.getString("avatarInitials")
                                        ?: getOrganizationInitials(
                                            savedOrganizationName
                                        ),

                                avatarColorStart =
                                    document.getString("avatarColorStart")
                                        ?: "#3B176A",

                                avatarColorEnd =
                                    document.getString("avatarColorEnd")
                                        ?: "#7C3AED",

                                audience =
                                    document.getString("audience") ?: "everyone",

                                textContent =
                                    document.getString("textContent")
                                        ?: document.getString("text")
                                        ?: "",

                                caption =
                                    document.getString("caption") ?: "",

                                imageUrls =
                                    getImageUrls(document),

                                likeCount =
                                    (document.getLong("likeCount") ?: 0L).toInt(),

                                likedByCurrentUser =
                                    false,

                                createdBy =
                                    document.getString("createdBy") ?: "",

                                commentCount =
                                    (document.getLong("commentCount") ?: 0L).toInt(),

                                edited =
                                    document.getBoolean("edited") ?: false
                            )
                        }

                    ownPosts.clear()

                    ownPosts.addAll(
                        newPosts
                    )

                    feedAdapter.updatePosts(
                        ownPosts.toList()
                    )

                    view?.findViewById<TextView>(
                        R.id.tvStatPosts
                    )?.text =
                        ownPosts.size.toString()

                    Log.d(
                        "OrgProfile",
                        "POST COUNT DISPLAYED = ${ownPosts.size}"
                    )
                }
    }

    // =========================================================
    // GET IMAGE URLS
    // =========================================================

    private fun getImageUrls(
        document: DocumentSnapshot
    ): List<String> {

        val imageUrls =
            document.get("imageUrls") as? List<*>

        if (imageUrls != null) {

            return imageUrls
                .filterIsInstance<String>()
        }

        val photoUrls =
            document.get("photoUrls") as? List<*>

        if (photoUrls != null) {

            return photoUrls
                .filterIsInstance<String>()
        }

        val imageUrl =
            document.getString("imageUrl")

        if (!imageUrl.isNullOrBlank()) {

            return listOf(
                imageUrl
            )
        }

        return emptyList()
    }

    // =========================================================
    // TIME AGO
    // =========================================================

    private fun getTimeAgo(
        timestamp: Long
    ): String {

        if (timestamp <= 0L) {
            return "Just now"
        }

        val difference =
            System.currentTimeMillis() - timestamp

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
                if (minutes == 1L) {
                    "1 min ago"
                } else {
                    "$minutes mins ago"
                }

            hours < 24 ->
                if (hours == 1L) {
                    "1 hour ago"
                } else {
                    "$hours hours ago"
                }

            days < 7 ->
                if (days == 1L) {
                    "Yesterday"
                } else {
                    "$days days ago"
                }

            else ->
                "Earlier"
        }
    }

    // =========================================================
    // UPDATE PROFILE INFORMATION
    // =========================================================

    private fun updateProfileInformation() {

        val rootView =
            view ?: return

        val displayName =
            if (organizationName.isNotBlank()) {
                organizationName
            } else {
                "Organization"
            }

        rootView.findViewById<TextView>(
            R.id.tvOrgName
        )?.text =
            displayName

        rootView.findViewById<TextView>(
            R.id.tvOrgCategory
        )?.text =
            organizationCategory

        rootView.findViewById<TextView>(
            R.id.tvOrgDescription
        )?.text =
            organizationDescription

        rootView.findViewById<TextView>(
            R.id.tvAboutDescription
        )?.text =
            organizationDescription

        rootView.findViewById<TextView>(
            R.id.tvAboutCategory
        )?.text =
            organizationCategory

        val tvAdviser1 =
            rootView.findViewById<TextView>(
                R.id.tvAboutAdviser
            )

        val tvAdviser2 =
            rootView.findViewById<TextView>(
                R.id.tvAboutAdviser2
            )

        val layoutAdviser1 =
            rootView.findViewById<LinearLayout>(
                R.id.layoutAdviser1
            )

        val layoutAdviser2 =
            rootView.findViewById<LinearLayout>(
                R.id.layoutAdviser2
            )


// =====================================================
// ADVISER 1
// =====================================================

        if (adviserName.isNotBlank()) {

            tvAdviser1.text =
                adviserName

            layoutAdviser1.visibility =
                View.VISIBLE

        } else {

            tvAdviser1.text =
                "Not specified"

            layoutAdviser1.visibility =
                View.VISIBLE
        }


// =====================================================
// ADVISER 2
// =====================================================

        if (adviserName2.isNotBlank()) {

            tvAdviser2.text =
                adviserName2

            layoutAdviser2.visibility =
                View.VISIBLE

        } else {

            layoutAdviser2.visibility =
                View.GONE
        }


// Adviser email is currently not being collected separately.
// Hide it instead of showing the Admin account email.
        rootView.findViewById<TextView>(
            R.id.tvAboutAdviserEmail
        )?.visibility =
            View.GONE

        rootView.findViewById<TextView>(
            R.id.tvAboutFounded
        )?.text =
            organizationFounded

        tvOrgAvatarInitials?.text =
            getOrganizationInitials(
                displayName
            )
    }

    // =========================================================
    // ORGANIZATION INITIALS
    // =========================================================

    private fun getOrganizationInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return "ORG"
        }

        if (words.size == 1) {

            return words[0]
                .take(3)
                .uppercase()
        }

        return words
            .take(3)
            .joinToString("") {

                it.first()
                    .uppercase()
            }
    }

    // =========================================================
    // SAVE PROFILE INFORMATION LOCALLY
    // =========================================================

    private fun saveProfileInformation() {

        if (organizationId.isBlank()) {
            return
        }

        val prefs =
            requireContext()
                .getSharedPreferences(
                    "organization_profile_$organizationId",
                    Context.MODE_PRIVATE
                )

        prefs.edit()
            .putString("organizationName", organizationName)
            .putString("category", organizationCategory)
            .putString("description", organizationDescription)
            .putString("adviserName", adviserName)
            .putString("adviserName1", adviserName)
            .putString("adviserName2", adviserName2)
            .putString("adviserEmail", adviserEmail)
            .putString("founded", organizationFounded)
            .apply()
    }

    // =========================================================
    // SAVE PROFILE INFORMATION TO FIRESTORE
    // i-save pud ang "orgName" (gigamit sa Browse ug Super
    // Admin) ug "profileCategory" (dili na masulatan ang
    // "category" nga status)
    // =========================================================

    private fun updateFirestoreProfileInformation() {

        if (organizationId.isBlank()) {
            return
        }

        val data =
            hashMapOf<String, Any>(

                "orgName" to organizationName,

                "organizationName" to organizationName,

                "profileCategory" to organizationCategory,

                "description" to organizationDescription,

                "adviserName" to adviserName,

                // Old compatibility field
                "adviserName" to adviserName,

// New two-adviser fields
                "adviserName1" to adviserName,

                "adviserName2" to adviserName2,

                "adviserEmail" to adviserEmail,

                "founded" to organizationFounded,

                "updatedAt" to FieldValue.serverTimestamp()
            )

        db.collection("organizations")
            .document(organizationId)
            .set(
                data,
                SetOptions.merge()
            )
            .addOnFailureListener { error ->

                Log.e(
                    "OrgProfile",
                    "Unable to save organization information.",
                    error
                )
            }
    }

    // =========================================================
    // LOAD SAVED PROFILE INFORMATION
    // =========================================================

    private fun loadSavedProfileInformation() {

        if (organizationId.isBlank()) {
            return
        }

        val prefs =
            requireContext()
                .getSharedPreferences(
                    "organization_profile_$organizationId",
                    Context.MODE_PRIVATE
                )

        organizationName =
            prefs.getString("organizationName", organizationName)
                ?: organizationName

        // ayaw gamita ang naka-cache nga status (renewal_due)
        val savedCategory =
            prefs.getString("category", organizationCategory)
                ?: organizationCategory

        organizationCategory =
            if (isStatusCategory(savedCategory)) {
                "Student Organization"
            } else {
                savedCategory
            }

        organizationDescription =
            prefs.getString("description", organizationDescription)
                ?: organizationDescription

        adviserName =
            prefs.getString(
                "adviserName1",
                prefs.getString(
                    "adviserName",
                    adviserName
                )
            ) ?: adviserName

        adviserName2 =
            prefs.getString(
                "adviserName2",
                adviserName2
            ) ?: adviserName2

        adviserEmail =
            prefs.getString("adviserEmail", adviserEmail)
                ?: adviserEmail

        organizationFounded =
            prefs.getString("founded", organizationFounded)
                ?: organizationFounded
    }

    // =========================================================
    // SAVE ORGANIZATION IMAGE
    // =========================================================

    private fun saveOrganizationImage(
        uri: Uri,
        type: String
    ) {

        if (organizationId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Organization ID is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val resolver =
                requireContext()
                    .contentResolver

            val inputStream =
                resolver.openInputStream(uri)

            if (inputStream == null) {

                Toast.makeText(
                    requireContext(),
                    "Unable to open selected image.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            val fileName =
                "organization_${organizationId}_$type.jpg"

            val file =
                File(
                    requireContext().filesDir,
                    fileName
                )

            FileOutputStream(file).use { output ->

                inputStream.use { input ->

                    input.copyTo(output)
                }
            }

            val bitmap =
                BitmapFactory.decodeFile(
                    file.absolutePath
                )

            if (bitmap == null) {

                Toast.makeText(
                    requireContext(),
                    "Unable to decode image.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            if (type == "avatar") {

                imgOrgAvatarPhoto
                    ?.setImageBitmap(bitmap)

                imgOrgAvatarPhoto
                    ?.visibility =
                    View.VISIBLE

                tvOrgAvatarInitials
                    ?.visibility =
                    View.GONE

            } else if (type == "cover") {

                imgCoverPhoto
                    ?.setImageBitmap(bitmap)

                imgCoverPhoto
                    ?.visibility =
                    View.VISIBLE
            }

            // I-upload sa Cloudinary ug i-save ang URL sa Firestore
            // aron makita sa tanang users (students, admins, super admin).
            uploadOrganizationImage(
                file,
                type
            )

            Toast.makeText(
                requireContext(),
                if (type == "avatar") {
                    "Organization profile picture updated."
                } else {
                    "Organization cover photo updated."
                },
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            Log.e(
                "OrgProfile",
                "Image save failed.",
                e
            )

            Toast.makeText(
                requireContext(),
                "Unable to save image.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // =========================================================
    // UPLOAD ORGANIZATION IMAGE (Cloudinary -> organizations/{id})
    // =========================================================

    private fun uploadOrganizationImage(
        file: File,
        type: String
    ) {

        val field =
            if (type == "avatar") "avatarUrl" else "coverUrl"

        val orgId =
            organizationId

        try {

            MediaManager.get()
                .upload(file.absolutePath)
                .unsigned("kayara_upload")
                .option(
                    "folder",
                    "kayara/organizations/$orgId"
                )
                .callback(
                    object : UploadCallback {

                        override fun onStart(
                            requestId: String?
                        ) {
                        }

                        override fun onProgress(
                            requestId: String?,
                            bytes: Long,
                            totalBytes: Long
                        ) {
                        }

                        override fun onSuccess(
                            requestId: String?,
                            resultData: Map<*, *>?
                        ) {

                            val url =
                                resultData
                                    ?.get("secure_url")
                                    ?.toString()

                            if (url.isNullOrBlank()) {
                                return
                            }

                            db.collection("organizations")
                                .document(orgId)
                                .set(
                                    mapOf(field to url),
                                    SetOptions.merge()
                                )
                                .addOnFailureListener { error ->

                                    Log.e(
                                        "OrgProfile",
                                        "Unable to save $field",
                                        error
                                    )
                                }
                        }

                        override fun onError(
                            requestId: String?,
                            error: ErrorInfo?
                        ) {

                            Log.e(
                                "OrgProfile",
                                "Upload failed: ${error?.description}"
                            )
                        }

                        override fun onReschedule(
                            requestId: String?,
                            error: ErrorInfo?
                        ) {
                        }
                    }
                )
                .dispatch()

        } catch (e: Exception) {

            Log.e(
                "OrgProfile",
                "Upload error",
                e
            )
        }
    }

    // =========================================================
    // LOAD SAVED IMAGES
    // =========================================================

    private fun loadSavedImages() {

        if (organizationId.isBlank()) {
            return
        }

        // =====================================================
        // AVATAR
        // =====================================================

        val avatarFile =
            File(
                requireContext().filesDir,
                "organization_${organizationId}_avatar.jpg"
            )

        if (avatarFile.exists()) {

            val bitmap =
                BitmapFactory.decodeFile(
                    avatarFile.absolutePath
                )

            if (bitmap != null) {

                imgOrgAvatarPhoto
                    ?.setImageBitmap(bitmap)

                imgOrgAvatarPhoto
                    ?.visibility =
                    View.VISIBLE

                tvOrgAvatarInitials
                    ?.visibility =
                    View.GONE
            }
        }

        // =====================================================
        // COVER
        // =====================================================

        val coverFile =
            File(
                requireContext().filesDir,
                "organization_${organizationId}_cover.jpg"
            )

        if (coverFile.exists()) {

            val bitmap =
                BitmapFactory.decodeFile(
                    coverFile.absolutePath
                )

            if (bitmap != null) {

                imgCoverPhoto
                    ?.setImageBitmap(bitmap)

                imgCoverPhoto
                    ?.visibility =
                    View.VISIBLE
            }
        }
    }

    // =========================================================
// CONFIRM DELETE POST
// =========================================================

    private fun confirmDeletePost(
        post: FeedPostModel
    ) {

        if (!isAdded) {
            return
        }

        if (post.postId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Post information is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Delete Post?"
            )
            .setMessage(
                "Are you sure you want to delete this post? This action cannot be undone."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                deletePost(
                    post
                )
            }
            .show()
    }


// =========================================================
// DELETE POST
// =========================================================

    private fun deletePost(
        post: FeedPostModel
    ) {

        if (!isAdded) {
            return
        }

        if (post.postId.isBlank()) {
            return
        }

        val currentUser =
            auth.currentUser

        if (currentUser == null) {

            Toast.makeText(
                requireContext(),
                "Please log in again.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val postReference =
            db.collection(
                "posts"
            )
                .document(
                    post.postId
                )

        // =====================================================
        // VERIFY OWNER FIRST
        // =====================================================

        postReference
            .get()
            .addOnSuccessListener { postDocument ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                if (!postDocument.exists()) {

                    Toast.makeText(
                        requireContext(),
                        "Post no longer exists.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                val createdBy =
                    postDocument.getString(
                        "createdBy"
                    )
                        ?: ""

                val postOrganizationId =
                    postDocument.getString(
                        "organizationId"
                    )
                        ?: ""

                // =================================================
                // SECURITY CHECK
                // =================================================

                if (
                    createdBy != currentUser.uid ||
                    (
                            organizationId.isNotBlank() &&
                                    postOrganizationId != organizationId
                            )
                ) {

                    Toast.makeText(
                        requireContext(),
                        "You can only delete posts created by your organization account.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                // =================================================
                // DELETE COMMENTS FIRST
                // =================================================

                postReference
                    .collection(
                        "comments"
                    )
                    .get()
                    .addOnSuccessListener { commentsSnapshot ->

                        val commentsBatch =
                            db.batch()

                        for (
                        commentDocument in commentsSnapshot.documents
                        ) {

                            commentsBatch.delete(
                                commentDocument.reference
                            )
                        }

                        commentsBatch
                            .commit()
                            .addOnCompleteListener {

                                // =========================================
                                // DELETE LIKES
                                // =========================================

                                postReference
                                    .collection(
                                        "likes"
                                    )
                                    .get()
                                    .addOnSuccessListener { likesSnapshot ->

                                        val finalBatch =
                                            db.batch()

                                        for (
                                        likeDocument in likesSnapshot.documents
                                        ) {

                                            finalBatch.delete(
                                                likeDocument.reference
                                            )
                                        }

                                        // Delete actual post
                                        finalBatch.delete(
                                            postReference
                                        )

                                        finalBatch
                                            .commit()
                                            .addOnSuccessListener {

                                                if (!isAdded) {
                                                    return@addOnSuccessListener
                                                }

                                                Toast.makeText(
                                                    requireContext(),
                                                    "Post deleted successfully.",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                /*
                                                 * No manual removal required.
                                                 * listenToOrganizationPosts()
                                                 * is a real-time listener, so
                                                 * mawala automatically ang post.
                                                 */
                                            }
                                            .addOnFailureListener { error ->

                                                if (!isAdded) {
                                                    return@addOnFailureListener
                                                }

                                                Toast.makeText(
                                                    requireContext(),
                                                    "Failed to delete post: ${error.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                    }
                                    .addOnFailureListener { error ->

                                        if (!isAdded) {
                                            return@addOnFailureListener
                                        }

                                        Toast.makeText(
                                            requireContext(),
                                            "Unable to remove post likes: ${error.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                    }
                    .addOnFailureListener { error ->

                        if (!isAdded) {
                            return@addOnFailureListener
                        }

                        Toast.makeText(
                            requireContext(),
                            "Unable to remove post comments: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Unable to verify post: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // HANDLE LOCAL LIKE
    // =========================================================

    private fun handleLocalLike(
        post: FeedPostModel
    ) {

        val index =
            ownPosts.indexOfFirst {
                it.postId == post.postId
            }

        if (index < 0) {
            return
        }

        val oldPost =
            ownPosts[index]

        val newPost =
            oldPost.copy(

                likeCount =
                    if (oldPost.likedByCurrentUser) {

                        maxOf(
                            0,
                            oldPost.likeCount - 1
                        )

                    } else {

                        oldPost.likeCount + 1
                    },

                likedByCurrentUser =
                    !oldPost.likedByCurrentUser
            )

        ownPosts[index] =
            newPost

        feedAdapter.updatePosts(
            ownPosts.toList()
        )
    }

    // =========================================================
    // TAB SWITCHING
    // =========================================================

    private fun showTab(
        tab: String
    ) {

        contentPosts.visibility =
            if (tab == "posts") View.VISIBLE else View.GONE

        contentAbout.visibility =
            if (tab == "about") View.VISIBLE else View.GONE

        contentMembers.visibility =
            if (tab == "members") View.VISIBLE else View.GONE

        setTabState(
            tabPosts,
            underlinePosts,
            tab == "posts"
        )

        setTabState(
            tabAbout,
            underlineAbout,
            tab == "about"
        )

        setTabState(
            tabMembers,
            underlineMembers,
            tab == "members"
        )
    }

    // =========================================================
    // TAB STATE
    // =========================================================

    private fun setTabState(
        tabLayout: LinearLayout,
        underline: View,
        active: Boolean
    ) {

        val textView =
            tabLayout
                .getChildAt(0)
                    as? TextView

        if (active) {

            textView?.setTextColor(
                resources.getColor(
                    R.color.dark_blue,
                    null
                )
            )

            underline.setBackgroundResource(
                R.drawable.bg_tab_underline_active
            )

        } else {

            textView?.setTextColor(
                resources.getColor(
                    R.color.text_gray,
                    null
                )
            )

            underline.setBackgroundColor(
                resources.getColor(
                    R.color.card_border,
                    null
                )
            )
        }
    }

    // =========================================================
// CLEANUP
// =========================================================

    override fun onDestroyView() {
        permissionListener?.remove()
        permissionListener = null

        postsListener?.remove()

        postsListener =
            null

        orgAvatarListener?.remove()

        orgAvatarListener =
            null

        organizationListener?.remove()

        organizationListener =
            null

        membersListener?.remove()

        membersListener =
            null

        eventsCountListener?.remove()

        eventsCountListener =
            null

        clearMemberUserListeners()

        tvMembersSummary =
            null

        imgCoverPhoto =
            null

        imgOrgAvatarPhoto =
            null

        tvOrgAvatarInitials =
            null

        btnEditProfile =
            null

        super.onDestroyView()
    }
}
