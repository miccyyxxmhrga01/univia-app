package com.example.kayara

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AdminDashboardFragment : Fragment() {

    companion object {

        private const val ARG_ORGANIZATION_ID =
            "organization_id"

        private const val ARG_ORGANIZATION_NAME =
            "organization_name"

        @JvmStatic
        fun newInstance(
            organizationId: String,
            organizationName: String
        ): AdminDashboardFragment {

            return AdminDashboardFragment().apply {

                arguments = Bundle().apply {

                    putString(
                        ARG_ORGANIZATION_ID,
                        organizationId
                    )

                    putString(
                        ARG_ORGANIZATION_NAME,
                        organizationName
                    )
                }
            }
        }
    }

    // ============================================================
    // VIEWS
    // ============================================================

    private lateinit var etComposeText: EditText
    private lateinit var btnAddPhoto: View
    private lateinit var btnRemovePhoto: View
    private lateinit var btnSubmitPost: View

    private lateinit var photoPreviewContainer: LinearLayout
    private lateinit var photoPreviewList: LinearLayout
    private lateinit var tvPhotoCount: TextView

    private lateinit var rvFeed: RecyclerView

    // Renewal
    private lateinit var renewalBanner: View
    private lateinit var tvRenewalAcademicYear: TextView
    private lateinit var btnRenewNow: TextView

    // ============================================================
    // FIREBASE
    // ============================================================

    private lateinit var feedAdapter: FeedAdapter

    private val feedPosts =
        mutableListOf<FeedPostModel>()

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        SupabaseDatabase.getInstance()

    // Organization profile pictures (organizationId -> avatarUrl)
    private var orgAvatarListener: ListenerRegistration? = null
    private var postsListener: ListenerRegistration? = null

    // ============================================================
    // ORGANIZATION
    // ============================================================

    private var organizationId: String = ""
    private var organizationName: String = ""

    // ============================================================
    // PHOTO DATA
    // ============================================================

    private val selectedImageUris =
        mutableListOf<Uri>()

    private val uploadExecutor =
        Executors.newFixedThreadPool(3)

    private var isUploading = false



    // ============================================================
    // PHOTO PICKER
    // ============================================================

    private val photoPicker =
        registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(100)
        ) { uris ->

            if (uris.isEmpty()) {
                return@registerForActivityResult
            }

            selectedImageUris.clear()
            selectedImageUris.addAll(uris)

            showSelectedPhotos()

            tvPhotoCount.text =
                "${selectedImageUris.size} photo${if (selectedImageUris.size == 1) "" else "s"} selected"
        }

    // ============================================================
    // CREATE VIEW
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_admin_dashboard,
            container,
            false
        )
    }

    // ============================================================
    // VIEW CREATED
    // ============================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        organizationId =
            arguments?.getString(
                ARG_ORGANIZATION_ID
            ) ?: ""

        organizationName =
            arguments?.getString(
                ARG_ORGANIZATION_NAME
            ) ?: ""

        etComposeText =
            view.findViewById(R.id.etComposeText)

        btnAddPhoto =
            view.findViewById(R.id.btnAddPhoto)

        btnRemovePhoto =
            view.findViewById(R.id.btnRemovePhoto)

        btnSubmitPost =
            view.findViewById(R.id.btnSubmitPost)

        photoPreviewContainer =
            view.findViewById(R.id.photoPreviewContainer)

        photoPreviewList =
            view.findViewById(R.id.photoPreviewList)

        tvPhotoCount =
            view.findViewById(R.id.tvPhotoCount)

        rvFeed =
            view.findViewById(R.id.rvFeed)

        renewalBanner =
            view.findViewById(R.id.renewalBanner)

        tvRenewalAcademicYear =
            view.findViewById(R.id.tvRenewalAcademicYear)

        btnRenewNow =
            view.findViewById(R.id.btnRenewNow)

        photoPreviewContainer.visibility =
            View.GONE

        renewalBanner.visibility =
            View.GONE

        setupFeed()

        // Organization profile pictures para sa post avatars
        orgAvatarListener?.remove()

        orgAvatarListener =
            listenToOrgAvatars { avatars ->
                feedAdapter.setOrgAvatars(avatars)
            }

        setupButtons()

        checkRenewalStatus()

        listenToPosts()
    }

    // ============================================================
    // RENEWAL STATUS
    // ============================================================

    private fun checkRenewalStatus() {

        if (organizationId.isBlank()) {
            renewalBanner.visibility =
                View.GONE

            return
        }

        db.collection("organizations")
            .document(organizationId)
            .get()
            .addOnSuccessListener { document ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                if (!document.exists()) {

                    renewalBanner.visibility =
                        View.GONE

                    return@addOnSuccessListener
                }

                val renewalStatus =
                    document.getString("renewalStatus") ?: ""

                val renewalAcademicYear =
                    document.getString("renewalAcademicYear") ?: ""

                if (renewalStatus == "due") {

                    renewalBanner.visibility =
                        View.VISIBLE

                    tvRenewalAcademicYear.text =
                        if (renewalAcademicYear.isNotBlank()) {
                            "Renewal required for AY $renewalAcademicYear"
                        } else {
                            "Your organization requires renewal."
                        }

                } else {

                    renewalBanner.visibility =
                        View.GONE
                }
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }

                renewalBanner.visibility =
                    View.GONE
            }
    }

    // ============================================================
    // BUTTONS
    // ============================================================

    private fun setupButtons() {

        btnAddPhoto.setOnClickListener {

            if (isUploading) {
                return@setOnClickListener
            }

            photoPicker.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }

        btnRemovePhoto.setOnClickListener {

            if (isUploading) {
                return@setOnClickListener
            }

            clearSelectedPhotos()
        }

        btnSubmitPost.setOnClickListener {

            if (isUploading) {
                return@setOnClickListener
            }

            createPost()
        }

        // --------------------------------------------------------
        // RENEW NOW
        // --------------------------------------------------------

        btnRenewNow.setOnClickListener {

            val intent = android.content.Intent(
                requireContext(),
                RenewalRequestActivity::class.java
            )

            intent.putExtra(
                "organizationId",
                organizationId
            )

            intent.putExtra(
                "organizationName",
                organizationName
            )

            intent.putExtra(
                "organizationShortCode",
                ""
            )

            intent.putExtra(
                "adminUid",
                auth.currentUser?.uid ?: ""
            )

            startActivity(intent)
        }
    }

    // ============================================================
    // CLEAR SELECTED PHOTOS
    // ============================================================

    private fun clearSelectedPhotos() {

        selectedImageUris.clear()

        photoPreviewList.removeAllViews()

        photoPreviewContainer.visibility =
            View.GONE

        tvPhotoCount.text =
            "No photos selected"
    }

    // ============================================================
    // FEED
    // ============================================================

    private fun setupFeed() {

        feedAdapter =
            FeedAdapter(
                posts = feedPosts,

                onLikeClick = { post ->
                    toggleLike(post)
                },

                onDeleteClick = { post ->
                    confirmDeletePost(post)
                },

                // NEW: ngalan sa organization imbes nga ngalan sa admin
                showOrganizationNames = true
            )

        rvFeed.layoutManager =
            LinearLayoutManager(requireContext())

        rvFeed.adapter =
            feedAdapter
    }

    // ============================================================
    // LISTEN TO POSTS
    // ============================================================

    private fun listenToPosts() {

        postsListener?.remove()
        postsListener = db.collection("posts")
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .addSnapshotListener(useCache = true) { snapshot, error ->

                if (!isAdded) {
                    return@addSnapshotListener
                }

                if (error != null) {

                    Toast.makeText(
                        requireContext(),
                        "Failed to load posts: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val newPosts =
                    snapshot.documents.map { document ->

                        FeedPostModel(

                            postId =
                                document.id,

                            postType =
                                document.getString("postType") ?: "text",

                            posterName =
                                document.getString("posterName") ?: "",

                            posterRole =
                                document.getString("posterRole") ?: "",

                            organizationId =
                                document.getString("organizationId") ?: "",

                            organizationName =
                                document.getString("organizationName") ?: "",

                            timeAgo =
                                getTimeAgo(
                                    document
                                        .getTimestamp("createdAt")
                                        ?.toDate()
                                        ?.time
                                        ?: System.currentTimeMillis()
                                ),

                            avatarInitials =
                                document.getString("avatarInitials") ?: "",

                            avatarColorStart =
                                document.getString("avatarColorStart") ?: "#3B176A",

                            avatarColorEnd =
                                document.getString("avatarColorEnd") ?: "#7C3AED",

                            audience =
                                document.getString("audience") ?: "everyone",

                            textContent =
                                document.getString("textContent") ?: "",

                            caption =
                                document.getString("caption") ?: "",

                            imageUrls =
                                (document.get("imageUrls") as? List<*>)
                                    ?.filterIsInstance<String>()
                                    ?: emptyList(),

                            likeCount =
                                (document.getLong("likeCount") ?: 0L).toInt(),

                            likedByCurrentUser =
                                false,

                            createdBy =
                                document.getString("createdBy") ?: "",

                            // NEW
                            commentCount =
                                (document.getLong("commentCount") ?: 0L).toInt(),

                            // NEW
                            edited =
                                document.getBoolean("edited") ?: false
                        )
                    }

                feedAdapter.updatePosts(
                    newPosts
                )

                checkCurrentUserLikes(
                    newPosts
                )
            }
    }

    // ============================================================
    // CREATE POST
    // ============================================================

    private fun createPost() {

        val text =
            etComposeText.text
                .toString()
                .trim()

        if (
            text.isEmpty() &&
            selectedImageUris.isEmpty()
        ) {

            Toast.makeText(
                requireContext(),
                "Please write something or add a photo.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

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

        loadUserAndCreatePost(
            uid = currentUser.uid,
            text = text
        )
    }

    // ============================================================
    // LOAD USER
    // ============================================================

    private fun loadUserAndCreatePost(
        uid: String,
        text: String
    ) {

        db.collection("users")
            .document(uid)
            .get()

            .addOnSuccessListener { document ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                val fullName =
                    document.getString("fullName") ?: "Admin"

                val userOrganizationId =
                    organizationId

                val userOrganizationName =
                    organizationName

                val role =
                    "admin"

                val postId =
                    db.collection("posts")
                        .document()
                        .id

                if (selectedImageUris.isNotEmpty()) {

                    uploadPhotosThenCreatePost(

                        postId = postId,

                        uid = uid,

                        fullName = fullName,

                        organizationId = userOrganizationId,

                        organizationName = userOrganizationName,

                        role = role,

                        text = text
                    )

                } else {

                    createTextPost(

                        postId = postId,

                        uid = uid,

                        fullName = fullName,

                        organizationId = userOrganizationId,

                        organizationName = userOrganizationName,

                        role = role,

                        text = text
                    )
                }
            }

            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Failed to get user information: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun notifyOrganizationStudentsAboutPost(
        postId: String,
        organizationId: String,
        organizationName: String,
        postText: String
    ) {

        if (organizationId.isBlank()) {
            return
        }

        db.collection("memberships")
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "status",
                "approved"
            )
            .get()
            .addOnSuccessListener { snapshot ->

                val studentIds =
                    snapshot.documents
                        .mapNotNull { document ->
                            document.getString("studentId")
                        }
                        .filter {
                            it.isNotBlank()
                        }
                        .distinct()

                val preview =
                    if (postText.length > 80) {
                        postText.take(80) + "..."
                    } else {
                        postText
                    }

                studentIds.forEach { studentId ->

                    StudentNotificationHelper.create(
                        studentId = studentId,

                        title =
                            "$organizationName posted",

                        message =
                            if (preview.isBlank()) {
                                "$organizationName shared a new post."
                            } else {
                                preview
                            },

                        type =
                            "organization_post",

                        organizationId =
                            organizationId,

                        organizationName =
                            organizationName,

                        referenceId =
                            postId
                    )
                }
            }
    }

    // ============================================================
    // TEXT POST
    // ============================================================

    private fun createTextPost(
        postId: String,
        uid: String,
        fullName: String,
        organizationId: String,
        organizationName: String,
        role: String,
        text: String
    ) {

        val initials =
            getInitials(fullName)

        val postData =
            hashMapOf(

                "postType" to "text",
                "posterName" to fullName,
                "posterRole" to role,
                "organizationId" to organizationId,
                "organizationName" to organizationName,
                "avatarInitials" to initials,
                "avatarColorStart" to "#3B176A",
                "avatarColorEnd" to "#7C3AED",
                "audience" to "everyone",
                "textContent" to text,
                "caption" to "",
                "imageUrls" to emptyList<String>(),
                "likeCount" to 0,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

        db.collection("posts")
            .document(postId)
            .set(postData)

            .addOnSuccessListener {

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                notifyOrganizationStudentsAboutPost(
                    postId = postId,
                    organizationId = organizationId,
                    organizationName = organizationName,
                    postText = text
                )

                etComposeText.text.clear()

                Toast.makeText(
                    requireContext(),
                    "Post published!",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Failed to publish: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // PHOTO UPLOAD
    // ============================================================

    private fun uploadPhotosThenCreatePost(
        postId: String,
        uid: String,
        fullName: String,
        organizationId: String,
        organizationName: String,
        role: String,
        text: String
    ) {

        if (selectedImageUris.isEmpty()) {
            return
        }

        isUploading = true

        setPostingState(true)

        val totalPhotos =
            selectedImageUris.size

        val completedCount =
            AtomicInteger(0)

        val uploadFailed =
            AtomicBoolean(false)

        val results =
            arrayOfNulls<String>(totalPhotos)

        tvPhotoCount.text =
            "Preparing photos... 0/$totalPhotos"

        for (index in selectedImageUris.indices) {

            val uri =
                selectedImageUris[index]

            uploadExecutor.execute {

                if (
                    !isAdded ||
                    uploadFailed.get()
                ) {
                    return@execute
                }

                val tempFile =
                    createCompressedTempFile(uri)

                if (tempFile == null) {

                    if (
                        uploadFailed.compareAndSet(
                            false,
                            true
                        )
                    ) {

                        runOnUiThreadSafe {

                            this@AdminDashboardFragment.uploadFailed(
                                "Could not prepare photo ${index + 1}."
                            )
                        }
                    }

                    return@execute
                }

                uploadSinglePhoto(

                    tempFile = tempFile,
                    index = index,
                    totalPhotos = totalPhotos,
                    postId = postId,
                    results = results,
                    completedCount = completedCount,
                    failureFlag = uploadFailed,

                    onAllUploaded = {

                        if (uploadFailed.get()) {
                            return@uploadSinglePhoto
                        }

                        createPhotoPost(

                            postId = postId,
                            uid = uid,
                            fullName = fullName,
                            organizationId = organizationId,
                            organizationName = organizationName,
                            role = role,
                            caption = text,
                            imageUrls = results.filterNotNull()
                        )
                    }
                )
            }
        }
    }

    // ============================================================
    // COMPRESS PHOTO
    // ============================================================

    private fun createCompressedTempFile(
        uri: Uri
    ): File? {

        return try {

            val context =
                context ?: return null

            val inputStream =
                context.contentResolver
                    .openInputStream(uri)
                    ?: return null

            val options =
                BitmapFactory.Options()

            options.inJustDecodeBounds =
                true

            BitmapFactory.decodeStream(
                inputStream,
                null,
                options
            )

            inputStream.close()

            val originalWidth =
                options.outWidth

            val originalHeight =
                options.outHeight

            if (
                originalWidth <= 0 ||
                originalHeight <= 0
            ) {
                return null
            }

            val maxSize =
                1600

            var sampleSize =
                1

            while (
                originalWidth / sampleSize > maxSize ||
                originalHeight / sampleSize > maxSize
            ) {
                sampleSize *= 2
            }

            val decodeOptions =
                BitmapFactory.Options()

            decodeOptions.inSampleSize =
                sampleSize

            decodeOptions.inPreferredConfig =
                Bitmap.Config.RGB_565

            val secondInputStream =
                context.contentResolver
                    .openInputStream(uri)
                    ?: return null

            val bitmap =
                BitmapFactory.decodeStream(
                    secondInputStream,
                    null,
                    decodeOptions
                )

            secondInputStream.close()

            if (bitmap == null) {
                return null
            }

            val fileName =
                "kayara_upload_${System.currentTimeMillis()}_${Thread.currentThread().id}.jpg"

            val tempFile =
                File(
                    context.cacheDir,
                    fileName
                )

            val outputStream =
                FileOutputStream(tempFile)

            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                80,
                outputStream
            )

            outputStream.flush()
            outputStream.close()

            bitmap.recycle()

            tempFile

        } catch (e: Exception) {

            null
        }
    }

    // ============================================================
    // SINGLE CLOUDINARY UPLOAD
    // ============================================================

    private fun uploadSinglePhoto(
        tempFile: File,
        index: Int,
        totalPhotos: Int,
        postId: String,
        results: Array<String?>,
        completedCount: AtomicInteger,
        failureFlag: AtomicBoolean,
        onAllUploaded: () -> Unit
    ) {

        val filePath =
            tempFile.absolutePath

        try {

            MediaManager.get()
                .upload(filePath)
                .unsigned("kayara_upload")
                .option(
                    "folder",
                    "kayara/posts/$postId"
                )
                .callback(
                    object : UploadCallback {

                        override fun onStart(
                            requestId: String?
                        ) {

                            runOnUiThreadSafe {

                                updateUploadProgress(
                                    completedCount.get(),
                                    totalPhotos
                                )
                            }
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

                            if (failureFlag.get()) {

                                tempFile.delete()

                                return
                            }

                            val secureUrl =
                                resultData
                                    ?.get("secure_url")
                                    ?.toString()

                            if (secureUrl.isNullOrBlank()) {

                                tempFile.delete()

                                if (
                                    failureFlag.compareAndSet(
                                        false,
                                        true
                                    )
                                ) {

                                    runOnUiThreadSafe {

                                        this@AdminDashboardFragment.uploadFailed(
                                            "Photo ${index + 1} returned an invalid URL."
                                        )
                                    }
                                }

                                return
                            }

                            results[index] =
                                secureUrl

                            tempFile.delete()

                            val finished =
                                completedCount
                                    .incrementAndGet()

                            runOnUiThreadSafe {

                                updateUploadProgress(
                                    finished,
                                    totalPhotos
                                )
                            }

                            if (
                                finished == totalPhotos &&
                                !failureFlag.get()
                            ) {

                                runOnUiThreadSafe {

                                    tvPhotoCount.text =
                                        "Publishing post..."

                                    onAllUploaded()
                                }
                            }
                        }

                        override fun onError(
                            requestId: String?,
                            error: ErrorInfo?
                        ) {

                            tempFile.delete()

                            if (
                                failureFlag.compareAndSet(
                                    false,
                                    true
                                )
                            ) {

                                runOnUiThreadSafe {

                                    this@AdminDashboardFragment.uploadFailed(
                                        "Photo ${index + 1} failed to upload: " +
                                                (error?.description ?: "Unknown error")
                                    )
                                }
                            }
                        }

                        override fun onReschedule(
                            requestId: String?,
                            error: ErrorInfo?
                        ) {

                            runOnUiThreadSafe {

                                if (!failureFlag.get()) {

                                    tvPhotoCount.text =
                                        "Retrying photo ${index + 1}..."
                                }
                            }
                        }
                    }
                )
                .dispatch()

        } catch (e: Exception) {

            tempFile.delete()

            if (
                failureFlag.compareAndSet(
                    false,
                    true
                )
            ) {

                runOnUiThreadSafe {

                    this@AdminDashboardFragment.uploadFailed(
                        "Photo ${index + 1} failed: ${e.message}"
                    )
                }
            }
        }
    }

    // ============================================================
    // SAFE UI THREAD
    // ============================================================

    private fun runOnUiThreadSafe(
        action: () -> Unit
    ) {

        val activity =
            activity ?: return

        activity.runOnUiThread {

            if (
                isAdded &&
                view != null
            ) {
                action()
            }
        }
    }

    // ============================================================
    // PROGRESS
    // ============================================================

    private fun updateUploadProgress(
        completed: Int,
        total: Int
    ) {

        if (
            !isAdded ||
            total <= 0
        ) {
            return
        }

        val percentage =
            (completed * 100) / total

        tvPhotoCount.text =
            "Uploading photos... $completed/$total • $percentage%"

        if (btnSubmitPost is TextView) {

            (btnSubmitPost as TextView).text =
                "Uploading $completed/$total"
        }
    }

    // ============================================================
    // CREATE PHOTO POST
    // ============================================================

    private fun createPhotoPost(
        postId: String,
        uid: String,
        fullName: String,
        organizationId: String,
        organizationName: String,
        role: String,
        caption: String,
        imageUrls: List<String>
    ) {

        if (imageUrls.isEmpty()) {

            uploadFailed(
                "No uploaded photo URLs were received."
            )

            return
        }

        val initials =
            getInitials(fullName)

        val postData =
            hashMapOf(

                "postType" to "photo",
                "posterName" to fullName,
                "posterRole" to role,
                "organizationId" to organizationId,
                "organizationName" to organizationName,
                "avatarInitials" to initials,
                "avatarColorStart" to "#3B176A",
                "avatarColorEnd" to "#7C3AED",
                "audience" to "everyone",
                "textContent" to "",
                "caption" to caption,
                "imageUrls" to imageUrls,
                "likeCount" to 0,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

        db.collection("posts")
            .document(postId)
            .set(postData)

            .addOnSuccessListener {

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                notifyOrganizationStudentsAboutPost(
                    postId = postId,
                    organizationId = organizationId,
                    organizationName = organizationName,
                    postText = caption
                )

                etComposeText.text.clear()

                clearSelectedPhotos()

                setPostingState(false)

                Toast.makeText(
                    requireContext(),
                    "Post published successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                setPostingState(false)

                Toast.makeText(
                    requireContext(),
                    "Photos uploaded but post failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // UPLOAD FAILED
    // ============================================================

    private fun uploadFailed(
        message: String
    ) {

        if (!isAdded) {
            return
        }

        isUploading = false

        setPostingState(false)

        tvPhotoCount.text =
            "Upload failed"

        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // ============================================================
    // POSTING STATE
    // ============================================================

    private fun setPostingState(
        uploading: Boolean
    ) {

        isUploading =
            uploading

        btnAddPhoto.isEnabled =
            !uploading

        btnRemovePhoto.isEnabled =
            !uploading

        btnSubmitPost.isEnabled =
            !uploading

        if (btnSubmitPost is TextView) {

            (btnSubmitPost as TextView).text =
                if (uploading) {
                    "Uploading..."
                } else {
                    "Post"
                }
        }
    }

    // ============================================================
    // SHOW SELECTED PHOTOS
    // ============================================================

    private fun showSelectedPhotos() {

        photoPreviewList.removeAllViews()

        if (selectedImageUris.isEmpty()) {

            photoPreviewContainer.visibility =
                View.GONE

            return
        }

        photoPreviewContainer.visibility =
            View.VISIBLE

        for (uri in selectedImageUris) {

            val imageView =
                ImageView(requireContext())

            val params =
                LinearLayout.LayoutParams(
                    dpToPx(82),
                    dpToPx(82)
                )

            params.marginEnd =
                dpToPx(8)

            imageView.layoutParams =
                params

            imageView.scaleType =
                ImageView.ScaleType.CENTER_CROP

            imageView.contentDescription =
                "Selected photo"

            imageView.background =
                requireContext()
                    .getDrawable(
                        R.drawable.bg_compose_photo_preview
                    )

            imageView.clipToOutline =
                true

            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(imageView)

            photoPreviewList.addView(
                imageView
            )
        }
    }

    // ============================================================
    // LIKES
    // ============================================================

    private fun toggleLike(
        post: FeedPostModel
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return

        val postRef =
            db.collection("posts")
                .document(post.postId)

        val likeRef =
            postRef
                .collection("likes")
                .document(uid)

        db.runTransaction { transaction ->

            val postSnapshot =
                transaction.get(postRef)

            val likeSnapshot =
                transaction.get(likeRef)

            val currentLikes =
                postSnapshot.getLong("likeCount") ?: 0L

            if (likeSnapshot.exists()) {

                transaction.delete(likeRef)

                transaction.update(
                    postRef,
                    "likeCount",
                    maxOf(
                        0L,
                        currentLikes - 1
                    )
                )

            } else {

                transaction.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.update(
                    postRef,
                    "likeCount",
                    currentLikes + 1
                )
            }

            null
        }
    }

    // ============================================================
    // CHECK CURRENT USER LIKES
    // ============================================================

    private fun checkCurrentUserLikes(
        posts: List<FeedPostModel>
    ) {

        val uid =
            auth.currentUser?.uid
                ?: return

        for (post in posts) {

            db.collection("posts")
                .document(post.postId)
                .collection("likes")
                .document(uid)
                .get()

                .addOnSuccessListener { document ->

                    if (!isAdded) {
                        return@addOnSuccessListener
                    }

                    val index =
                        feedPosts.indexOfFirst {
                            it.postId == post.postId
                        }

                    if (index >= 0) {

                        feedPosts[index] =
                            feedPosts[index].copy(
                                likedByCurrentUser = document.exists()
                            )

                        feedAdapter.notifyItemChanged(
                            index
                        )
                    }
                }
        }
    }

    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    private fun confirmDeletePost(
        post: FeedPostModel
    ) {

        if (!isAdded) {
            return
        }

        AlertDialog
            .Builder(requireContext())

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

                deletePost(post)
            }

            .show()
    }

    // ============================================================
    // DELETE POST
    // ============================================================

    private fun deletePost(
        post: FeedPostModel
    ) {

        val postRef =
            db.collection("posts")
                .document(post.postId)

        // NEW: papason pud ang comments
        postRef.collection("comments").get()
            .addOnSuccessListener { commentsSnapshot ->
                commentsSnapshot.documents.forEach {
                    it.reference.delete()
                }
            }

        postRef
            .collection("likes")
            .get()

            .addOnSuccessListener { likesSnapshot ->

                val batch =
                    db.batch()

                for (likeDocument in likesSnapshot.documents) {

                    batch.delete(
                        likeDocument.reference
                    )
                }

                batch.delete(postRef)

                batch.commit()

                    .addOnSuccessListener {

                        if (!isAdded) {
                            return@addOnSuccessListener
                        }

                        Toast.makeText(
                            requireContext(),
                            "Post deleted.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    .addOnFailureListener { error ->

                        if (!isAdded) {
                            return@addOnFailureListener
                        }

                        Toast.makeText(
                            requireContext(),
                            "Failed to delete: ${error.message}",
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
                    "Failed to delete likes: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // GET INITIALS
    // ============================================================

    private fun getInitials(
        name: String
    ): String {

        val parts =
            name.trim()
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }

        return when {

            parts.size >= 2 ->
                "${parts[0].first()}${parts[1].first()}"
                    .uppercase()

            parts.size == 1 ->
                parts[0]
                    .take(2)
                    .uppercase()

            else ->
                "AD"
        }
    }

    // ============================================================
    // TIME AGO
    // ============================================================

    private fun getTimeAgo(
        timeMillis: Long
    ): String {

        val difference =
            System.currentTimeMillis() - timeMillis

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
                "$minutes min ago"

            hours < 24 ->
                "$hours hour${if (hours == 1L) "" else "s"} ago"

            days < 7 ->
                "$days day${if (days == 1L) "" else "s"} ago"

            else ->
                "${days / 7} week${if (days / 7 == 1L) "" else "s"} ago"
        }
    }

    // ============================================================
    // DP TO PX
    // ============================================================

    private fun dpToPx(
        dp: Int
    ): Int {

        return (dp * resources.displayMetrics.density).toInt()
    }

    // ============================================================
    // DESTROY VIEW
    // ============================================================

    override fun onDestroyView() {

        postsListener?.remove()
        postsListener = null
        orgAvatarListener?.remove()
        orgAvatarListener = null

        Glide.with(this)
            .clear(photoPreviewList)

        super.onDestroyView()
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        uploadExecutor.shutdown()

        super.onDestroy()
    }
}
