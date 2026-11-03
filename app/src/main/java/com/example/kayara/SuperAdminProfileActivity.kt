package com.example.kayara

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import java.util.Locale

class SuperAdminProfileActivity : AppCompatActivity() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        SupabaseDatabase.getInstance()
    }


    // =========================================================
    // PROFILE VIEWS
    // =========================================================

    private lateinit var imgProfilePhoto: ImageView

    private lateinit var tvProfileInitials: TextView

    private lateinit var tvProfileName: TextView

    private lateinit var tvProfileUsername: TextView

    private lateinit var tvProfileEmail: TextView

    private lateinit var tvAccountUsername: TextView

    private lateinit var tvStatPosts: TextView

    private lateinit var tvPostCountLabel: TextView

    private lateinit var tvEmptyPosts: TextView

    private lateinit var rvMyPosts: RecyclerView


    // =========================================================
    // PROFILE DATA
    // =========================================================

    private var currentFullName =
        "Coordinator"

    private var currentUsername =
        "superadmin"

    private var currentEmail =
        ""

    private var currentProfileImageUrl =
        ""

    private var viewOnly =
        false

    private var profileUid =
        ""


    // =========================================================
    // POSTS
    // =========================================================

    private val ownPosts =
        mutableListOf<FeedPostModel>()

    private lateinit var feedAdapter:
            FeedAdapter

    private var postsListener:
            ListenerRegistration? = null

    private var profileListener:
            ListenerRegistration? = null


    // =========================================================
    // PROFILE PHOTO PICKER
    // =========================================================

    private val pickProfilePhotoLauncher =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->

            if (uri != null) {

                uploadProfilePhoto(
                    uri
                )
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
        setContentView(
            R.layout.activity_super_admin_profile
        )

        viewOnly =
            intent.getBooleanExtra(
                "viewOnly",
                false
            )

        profileUid =
            intent.getStringExtra(
                "superAdminUid"
            )
                ?.trim()
                .orEmpty()


        if (
            profileUid.isBlank()
        ) {

            profileUid =
                auth.currentUser?.uid
                    .orEmpty()
        }


        if (auth.currentUser == null) {

            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }


        setupViews()

        applyViewOnlyMode()

        setupPosts()

        listenToProfile()

        listenToOwnPosts()
    }

    private fun applyViewOnlyMode() {

        if (
            !viewOnly
        ) {

            return
        }


        // =========================================================
        // HIDE EDIT BUTTONS
        // =========================================================

        findViewById<View>(
            R.id.btnEditAccount
        ).visibility =
            View.GONE


        findViewById<View>(
            R.id.btnEditAccountDetails
        ).visibility =
            View.GONE


        // =========================================================
        // DISABLE PROFILE PHOTO EDIT
        // =========================================================

        val changePhoto =
            findViewById<FrameLayout>(
                R.id.btnChangeProfilePhoto
            )

        changePhoto.isClickable =
            false

        changePhoto.isFocusable =
            false


        // =========================================================
        // HIDE LOGOUT
        // =========================================================

        findViewById<View>(
            R.id.btnLogout
        ).visibility =
            View.GONE
    }


    // =========================================================
    // SETUP VIEWS
    // =========================================================

    private fun setupViews() {

        imgProfilePhoto =
            findViewById(
                R.id.imgProfilePhoto
            )


        tvProfileInitials =
            findViewById(
                R.id.tvProfileInitials
            )


        tvProfileName =
            findViewById(
                R.id.tvProfileName
            )


        tvProfileUsername =
            findViewById(
                R.id.tvProfileUsername
            )


        tvProfileEmail =
            findViewById(
                R.id.tvProfileEmail
            )


        tvAccountUsername =
            findViewById(
                R.id.tvAccountUsername
            )


        tvStatPosts =
            findViewById(
                R.id.tvStatPosts
            )


        tvPostCountLabel =
            findViewById(
                R.id.tvPostCountLabel
            )


        tvEmptyPosts =
            findViewById(
                R.id.tvEmptyPosts
            )


        rvMyPosts =
            findViewById(
                R.id.rvMyPosts
            )


        // =====================================================
        // BACK
        // =====================================================

        findViewById<ImageView>(
            R.id.btnBack
        ).setOnClickListener {

            finish()
        }


        // =====================================================
        // EDIT PROFILE
        // =====================================================

        findViewById<TextView>(
            R.id.btnEditAccount
        ).setOnClickListener {

            showEditAccountDialog()
        }


        findViewById<TextView>(
            R.id.btnEditAccountDetails
        ).setOnClickListener {

            showEditAccountDialog()
        }


        // =====================================================
        // CHANGE PROFILE PHOTO
        // =====================================================

        findViewById<FrameLayout>(
            R.id.btnChangeProfilePhoto
        ).setOnClickListener {

            pickProfilePhotoLauncher.launch(

                PickVisualMediaRequest(

                    ActivityResultContracts
                        .PickVisualMedia
                        .ImageOnly
                )
            )
        }


        // =====================================================
        // LOGOUT
        // =====================================================

        findViewById<LinearLayout>(
            R.id.btnLogout
        ).setOnClickListener {

            confirmLogout()
        }
    }


    // =========================================================
    // LOAD SUPER ADMIN PROFILE
    // =========================================================

    private fun listenToProfile() {

        val uid =
            profileUid

        if (
            uid.isBlank()
        ) {

            return
        }


        profileListener?.remove()


        profileListener =
            db.collection(
                "users"
            )
                .document(
                    uid
                )
                .addSnapshotListener { document, error ->


                    if (
                        isFinishing ||
                        isDestroyed
                    ) {

                        return@addSnapshotListener
                    }


                    if (error != null) {

                        Toast.makeText(
                            this,
                            "Unable to load profile: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }


                    if (
                        document == null ||
                        !document.exists()
                    ) {

                        currentEmail =
                            auth.currentUser
                                ?.email
                                ?: ""

                        updateProfileUi()

                        return@addSnapshotListener
                    }


                    // =================================================
                    // NAME
                    // =================================================

                    currentFullName =
                        document.getString(
                            "fullName"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: document.getString(
                                "name"
                            )
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                    ?: "Coordinator"


                    // =================================================
                    // USERNAME
                    // =================================================

                    currentUsername =
                        document.getString(
                            "username"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: "superadmin"


                    // =================================================
                    // EMAIL
                    // =================================================

                    currentEmail =
                        document.getString(
                            "email"
                        )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: auth.currentUser
                                ?.email
                                    ?: ""


                    // =================================================
                    // PROFILE IMAGE
                    // =================================================

                    currentProfileImageUrl =
                        document.getString(
                            "profileImageUrl"
                        )
                            ?.trim()
                            .orEmpty()


                    updateProfileUi()
                }
    }


    // =========================================================
    // UPDATE PROFILE UI
    // =========================================================

    private fun updateProfileUi() {

        tvProfileName.text =
            currentFullName


        tvProfileUsername.text =
            currentUsername


        tvAccountUsername.text =
            currentUsername


        tvProfileEmail.text =
            currentEmail.ifBlank {
                "No email available"
            }


        tvProfileInitials.text =
            getInitials(
                currentFullName
            )


        if (
            currentProfileImageUrl.isNotBlank()
        ) {

            imgProfilePhoto.visibility =
                View.VISIBLE

            tvProfileInitials.visibility =
                View.GONE


            Glide.with(this)
                .load(
                    currentProfileImageUrl
                )
                .circleCrop()
                .into(
                    imgProfilePhoto
                )


        } else {

            imgProfilePhoto.visibility =
                View.GONE

            tvProfileInitials.visibility =
                View.VISIBLE
        }
    }


    // =========================================================
    // EDIT NAME + USERNAME
    // =========================================================

    private fun showEditAccountDialog() {

        val container =
            LinearLayout(this)


        container.orientation =
            LinearLayout.VERTICAL


        val padding =
            (
                    20 *
                            resources
                                .displayMetrics
                                .density
                    ).toInt()


        container.setPadding(
            padding,
            0,
            padding,
            0
        )


        // =====================================================
        // NAME
        // =====================================================

        val etName =
            EditText(this)


        etName.hint =
            "Full name"


        etName.setText(
            currentFullName
        )


        etName.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS


        // =====================================================
        // USERNAME
        // =====================================================

        val etUsername =
            EditText(this)


        etUsername.hint =
            "Username"


        etUsername.setText(
            currentUsername
        )


        etUsername.inputType =
            InputType.TYPE_CLASS_TEXT


        container.addView(
            etName
        )


        container.addView(
            etUsername
        )


        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Edit account"
                )
                .setMessage(
                    "You can update your name and username."
                )
                .setView(
                    container
                )
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Save",
                    null
                )
                .create()


        dialog.setOnShowListener {


            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {


                val newName =
                    etName.text
                        .toString()
                        .trim()


                val newUsername =
                    etUsername.text
                        .toString()
                        .trim()
                        .lowercase(
                            Locale.getDefault()
                        )


                if (newName.isBlank()) {

                    etName.error =
                        "Name is required."

                    return@setOnClickListener
                }


                if (newUsername.isBlank()) {

                    etUsername.error =
                        "Username is required."

                    return@setOnClickListener
                }


                if (
                    newUsername.contains(
                        " "
                    )
                ) {

                    etUsername.error =
                        "Username cannot contain spaces."

                    return@setOnClickListener
                }


                checkUsernameAndSave(
                    newName =
                        newName,

                    newUsername =
                        newUsername,

                    dialog =
                        dialog
                )
            }
        }


        dialog.show()
    }


    // =========================================================
    // CHECK USERNAME
    // =========================================================

    private fun checkUsernameAndSave(
        newName: String,
        newUsername: String,
        dialog: AlertDialog
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return


        // If unchanged, no need to check other accounts first.
        if (
            newUsername.equals(
                currentUsername,
                ignoreCase = true
            )
        ) {

            saveAccountChanges(
                uid =
                    uid,

                newName =
                    newName,

                newUsername =
                    newUsername,

                dialog =
                    dialog
            )

            return
        }


        db.collection(
            "users"
        )
            .whereEqualTo(
                "username",
                newUsername
            )
            .get()
            .addOnSuccessListener { result ->


                val usedByAnotherUser =
                    result.documents.any { document ->

                        document.id !=
                                uid
                    }


                if (usedByAnotherUser) {

                    Toast.makeText(
                        this,
                        "That username is already in use.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }


                saveAccountChanges(
                    uid =
                        uid,

                    newName =
                        newName,

                    newUsername =
                        newUsername,

                    dialog =
                        dialog
                )
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Unable to check username: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // SAVE ACCOUNT CHANGES
    // =========================================================

    private fun saveAccountChanges(
        uid: String,
        newName: String,
        newUsername: String,
        dialog: AlertDialog
    ) {

        val data =
            hashMapOf<String, Any>(

                "fullName" to
                        newName,

                "username" to
                        newUsername
            )


        db.collection(
            "users"
        )
            .document(
                uid
            )
            .set(
                data,
                SetOptions.merge()
            )
            .addOnSuccessListener {


                currentFullName =
                    newName


                currentUsername =
                    newUsername


                updateProfileUi()


                updateOwnPostProfile(
                    newName
                )


                dialog.dismiss()


                Toast.makeText(
                    this,
                    "Profile updated.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Unable to update profile: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // PROFILE PHOTO UPLOAD
    // =========================================================

    private fun uploadProfilePhoto(
        uri: Uri
    ) {

        Toast.makeText(
            this,
            "Updating profile photo...",
            Toast.LENGTH_SHORT
        ).show()


        MediaManager
            .get()
            .upload(
                uri
            )
            .unsigned(
                "kayara_upload"
            )
            .option(
                "resource_type",
                "image"
            )
            .option(
                "folder",
                "kayara/profile_images"
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
                        resultData:
                        MutableMap<Any?, Any?>?
                    ) {

                        val secureUrl =
                            resultData
                                ?.get(
                                    "secure_url"
                                )
                                ?.toString()
                                .orEmpty()


                        if (
                            secureUrl.isBlank()
                        ) {

                            Toast.makeText(
                                this@SuperAdminProfileActivity,
                                "Profile photo upload failed.",
                                Toast.LENGTH_LONG
                            ).show()

                            return
                        }


                        saveProfilePhotoUrl(
                            secureUrl
                        )
                    }


                    override fun onError(
                        requestId: String?,
                        error: ErrorInfo?
                    ) {

                        Toast.makeText(
                            this@SuperAdminProfileActivity,
                            "Unable to upload photo: ${
                                error?.description
                                    ?: "Unknown error"
                            }",
                            Toast.LENGTH_LONG
                        ).show()
                    }


                    override fun onReschedule(
                        requestId: String?,
                        error: ErrorInfo?
                    ) {
                    }
                }
            )
            .dispatch()
    }


    // =========================================================
    // SAVE PROFILE PHOTO URL
    // =========================================================

    private fun saveProfilePhotoUrl(
        url: String
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return


        db.collection(
            "users"
        )
            .document(
                uid
            )
            .set(

                mapOf(
                    "profileImageUrl" to
                            url
                ),

                SetOptions.merge()
            )
            .addOnSuccessListener {


                currentProfileImageUrl =
                    url


                updateProfileUi()


                Toast.makeText(
                    this,
                    "Profile photo updated.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Photo uploaded, but profile update failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // SETUP POSTS
    // =========================================================

    private fun setupPosts() {

        rvMyPosts.layoutManager =
            LinearLayoutManager(
                this
            )


        feedAdapter =
            FeedAdapter(

                posts =
                    ownPosts,

                onLikeClick = { post ->

                    toggleLike(
                        post
                    )
                },

                onDeleteClick =
                    if (
                        viewOnly
                    ) {

                        null

                    } else {

                        { post ->

                            confirmDeletePost(
                                post
                            )
                        }
                    },

                showOrganizationNames =
                    false
            )


        rvMyPosts.adapter =
            feedAdapter
    }


    // =========================================================
    // LOAD ONLY SUPER ADMIN'S OWN POSTS
    // =========================================================

    private fun listenToOwnPosts() {

        val uid =
            profileUid

        if (
            uid.isBlank()
        ) {

            return
        }


        postsListener?.remove()


        postsListener =
            db.collection(
                "posts"
            )
                .whereEqualTo(
                    "createdBy",
                    uid
                )
                .addSnapshotListener { snapshot, error ->


                    if (
                        isFinishing ||
                        isDestroyed
                    ) {

                        return@addSnapshotListener
                    }


                    if (error != null) {

                        Toast.makeText(
                            this,
                            "Unable to load your posts: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }


                    if (snapshot == null) {

                        return@addSnapshotListener
                    }


                    val postDocuments =
                        snapshot.documents
                            .sortedByDescending { document ->

                                document
                                    .getTimestamp(
                                        "createdAt"
                                    )
                                    ?.toDate()
                                    ?.time
                                    ?: 0L
                            }


                    val loadedPosts =
                        postDocuments.map { document ->

                            mapPost(
                                document
                            )
                        }


                    ownPosts.clear()


                    ownPosts.addAll(
                        loadedPosts
                    )


                    feedAdapter.updatePosts(
                        ownPosts.toList()
                    )


                    updatePostSummary()
                }
    }


    // =========================================================
    // FIRESTORE POST -> FEED MODEL
    // =========================================================

    private fun mapPost(
        document: DocumentSnapshot
    ): FeedPostModel {


        val createdAt =
            document
                .getTimestamp(
                    "createdAt"
                )
                ?.toDate()
                ?.time
                ?: System.currentTimeMillis()


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
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: currentFullName,


            posterRole =
                document.getString(
                    "posterRole"
                )
                    ?: "Coordinator",


            organizationId =
                document.getString(
                    "organizationId"
                )
                    ?: "SUPER_ADMIN",


            organizationName =
                document.getString(
                    "organizationName"
                )
                    ?: "Coordinator",


            timeAgo =
                getTimeAgo(
                    createdAt
                ),


            avatarInitials =
                document.getString(
                    "avatarInitials"
                )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: getInitials(
                        currentFullName
                    ),


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
                    ?: document.getString(
                        "text"
                    )
                    ?: "",


            caption =
                document.getString(
                    "caption"
                )
                    ?: "",


            imageUrls =
                getImageUrls(
                    document
                ),


            likeCount =
                (
                        document.getLong(
                            "likeCount"
                        )
                            ?: 0L
                        ).toInt(),


            likedByCurrentUser =
                false,


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
                        ).toInt(),


            edited =
                document.getBoolean(
                    "edited"
                )
                    ?: false
        )
    }


    // =========================================================
    // POST COUNT + EMPTY STATE
    // =========================================================

    private fun updatePostSummary() {

        val count =
            ownPosts.size


        tvStatPosts.text =
            count.toString()


        tvPostCountLabel.text =
            if (count == 1) {

                "1 post"

            } else {

                "$count posts"
            }


        tvEmptyPosts.visibility =
            if (count == 0) {

                View.VISIBLE

            } else {

                View.GONE
            }


        rvMyPosts.visibility =
            if (count == 0) {

                View.GONE

            } else {

                View.VISIBLE
            }
    }


    // =========================================================
    // LIKE POST
    // =========================================================

    private fun toggleLike(
        post: FeedPostModel
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return


        if (
            post.postId.isBlank()
        ) {

            return
        }


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


            val currentCount =
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
                        currentCount - 1
                    )
                )


            } else {


                transaction.set(
                    likeRef,

                    mapOf(
                        "userId" to
                                uid,

                        "createdAt" to
                                FieldValue.serverTimestamp()
                    )
                )


                transaction.update(
                    postRef,
                    "likeCount",
                    currentCount + 1
                )
            }


            null
        }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Unable to update like: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    // =========================================================
    // DELETE OWN POST
    // =========================================================

    private fun confirmDeletePost(
        post: FeedPostModel
    ) {

        if (
            post.postId.isBlank()
        ) {

            return
        }


        AlertDialog.Builder(this)
            .setTitle(
                "Delete post?"
            )
            .setMessage(
                "Are you sure you want to delete this post?"
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Delete"
            ) { _, _ ->


                db.collection(
                    "posts"
                )
                    .document(
                        post.postId
                    )
                    .delete()
                    .addOnSuccessListener {


                        Toast.makeText(
                            this,
                            "Post deleted.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { error ->


                        Toast.makeText(
                            this,
                            "Unable to delete post: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }


    // =========================================================
    // UPDATE OLD POSTS WHEN NAME CHANGES
    // =========================================================

    private fun updateOwnPostProfile(
        newName: String
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return


        db.collection(
            "posts"
        )
            .whereEqualTo(
                "createdBy",
                uid
            )
            .get()
            .addOnSuccessListener { result ->


                if (
                    result.documents.isEmpty()
                ) {

                    return@addOnSuccessListener
                }


                val batch =
                    db.batch()


                for (
                document in result.documents
                ) {


                    batch.update(

                        document.reference,

                        mapOf(
                            "posterName" to
                                    newName,

                            "avatarInitials" to
                                    getInitials(
                                        newName
                                    )
                        )
                    )
                }


                batch.commit()
            }
    }


    // =========================================================
    // GET POST IMAGES
    // =========================================================

    private fun getImageUrls(
        document: DocumentSnapshot
    ): List<String> {


        val imageUrls =
            document.get(
                "imageUrls"
            ) as? List<*>


        if (
            imageUrls != null
        ) {

            return imageUrls
                .filterIsInstance<String>()
        }


        val photoUrls =
            document.get(
                "photoUrls"
            ) as? List<*>


        if (
            photoUrls != null
        ) {

            return photoUrls
                .filterIsInstance<String>()
        }


        val singleImage =
            document.getString(
                "imageUrl"
            )
                ?.trim()
                .orEmpty()


        return if (
            singleImage.isNotBlank()
        ) {

            listOf(
                singleImage
            )

        } else {

            emptyList()
        }
    }


    // =========================================================
    // TIME AGO
    // =========================================================

    private fun getTimeAgo(
        time: Long
    ): String {


        val difference =
            System.currentTimeMillis() -
                    time


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


            difference < day -> {

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


            difference < 7 * day -> {

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

                val days =
                    difference /
                            day


                "$days days ago"
            }
        }
    }


    // =========================================================
    // INITIALS
    // =========================================================

    private fun getInitials(
        name: String
    ): String {


        val parts =
            name.trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }


        return when {


            parts.isEmpty() -> {

                "SA"
            }


            parts.size == 1 -> {

                parts
                    .first()
                    .take(2)
                    .uppercase(
                        Locale.getDefault()
                    )
            }


            else -> {

                (
                        parts
                            .first()
                            .take(1) +

                                parts
                                    .last()
                                    .take(1)
                        )
                    .uppercase(
                        Locale.getDefault()
                    )
            }
        }
    }


    // =========================================================
    // LOGOUT
    // =========================================================

    private fun confirmLogout() {


        AlertDialog.Builder(this)
            .setTitle(
                "Log out?"
            )
            .setMessage(
                "Are you sure you want to log out?"
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Log Out"
            ) { _, _ ->


                auth.signOut()


                /*
                 * We do not directly reference MainActivity here.
                 * This avoids an unresolved-class error if your
                 * launcher activity uses a different class name.
                 */

                val launchIntent =
                    packageManager
                        .getLaunchIntentForPackage(
                            packageName
                        )


                if (
                    launchIntent != null
                ) {

                    launchIntent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK


                    startActivity(
                        launchIntent
                    )
                }


                finishAffinity()
            }
            .show()
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroy() {


        postsListener?.remove()


        postsListener =
            null


        profileListener?.remove()


        profileListener =
            null


        super.onDestroy()
    }
}