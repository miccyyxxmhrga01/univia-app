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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.concurrent.atomic.AtomicInteger

class DashboardFragment : Fragment() {

    private lateinit var feedAdapter: FeedAdapter
    private lateinit var rvFeed: RecyclerView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: SupabaseDatabase

    private var postsListener: ListenerRegistration? = null
    private var feedRequestId = 0

    // Organization profile pictures (organizationId -> avatarUrl)
    private var orgAvatarListener: ListenerRegistration? = null

    private val feedPosts = mutableListOf<FeedPostModel>()

    // ============================================================
    // COMPOSE VIEWS
    // ============================================================

    private lateinit var etComposeText: EditText
    private lateinit var btnAddPhoto: View
    private lateinit var btnRemovePhoto: View
    private lateinit var btnSubmitPost: View

    private lateinit var photoPreviewContainer: LinearLayout
    private lateinit var photoPreviewList: LinearLayout
    private lateinit var tvPhotoCount: TextView

    // ============================================================
    // PHOTO DATA
    // ============================================================

    private val selectedImageUris = mutableListOf<Uri>()

    private val uploadExecutor = Executors.newFixedThreadPool(3)

    private var isUploading = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = SupabaseDatabase.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_dashboard,
            container,
            false
        )

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

        photoPreviewContainer.visibility =
            View.GONE

        rvFeed.layoutManager =
            LinearLayoutManager(requireContext())

        feedAdapter = FeedAdapter(
            feedPosts,
            onLikeClick = { post ->
                toggleLike(post)
            },
            onDeleteClick = { post ->
                confirmDeletePost(post)
            },
            // NEW: ngalan sa organization + click -> organization profile
            showOrganizationNames = true,
            onOrganizationClick = { post ->
                openOrganizationProfile(post)
            }
        )

        rvFeed.adapter = feedAdapter

        // Organization profile pictures para sa post avatars
        orgAvatarListener?.remove()

        orgAvatarListener =
            listenToOrgAvatars { avatars ->
                feedAdapter.setOrgAvatars(avatars)
            }

        // --------------------------------------------------------
        // ADD PHOTO
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // POST
        // --------------------------------------------------------

        btnSubmitPost.setOnClickListener {

            if (isUploading) {
                return@setOnClickListener
            }

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

                return@setOnClickListener
            }

            createSuperAdminPost(text)
        }

        loadGlobalPosts()

        view.post {
            if (!isAdded) {
                return@post
            }
        }

        return view
    }

    // ============================================================
    // CREATE POST (text o photos)
    // ============================================================

    private fun createSuperAdminPost(
        text: String
    ) {

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

        val hasPhotos =
            selectedImageUris.isNotEmpty()

        setPostingState(
            true,
            if (hasPhotos) "Uploading..." else "Posting..."
        )

        val uid =
            currentUser.uid

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                val fullName =
                    document.getString("fullName")
                        ?: currentUser.displayName
                        ?: "Coordinator"

                val postId =
                    db.collection("posts")
                        .document()
                        .id

                if (hasPhotos) {

                    uploadPhotosThenCreatePost(
                        postId = postId,
                        uid = uid,
                        fullName = fullName,
                        text = text
                    )

                } else {

                    createTextPost(
                        postId = postId,
                        uid = uid,
                        fullName = fullName,
                        text = text
                    )
                }
            }
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                setPostingState(false)

                Toast.makeText(
                    requireContext(),
                    "Failed to load profile: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // TEXT POST
    // ============================================================

    private fun createTextPost(
        postId: String,
        uid: String,
        fullName: String,
        text: String
    ) {

        val postData =
            hashMapOf<String, Any>(

                "postType" to "text",

                "posterName" to fullName,

                "posterRole" to "Coordinator",

                "organizationId" to "SUPER_ADMIN",

                "organizationName" to "Coordinator",

                "avatarInitials" to getInitials(fullName),

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

                notifyAllOrganizationAdminsAboutPost(
                    postId = postId,
                    postText = text
                )

                etComposeText.text.clear()

                setPostingState(false)

                Toast.makeText(
                    requireContext(),
                    "Posted successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                rvFeed.scrollToPosition(0)
            }
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                setPostingState(false)

                Toast.makeText(
                    requireContext(),
                    "Failed to post: ${error.message}",
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
        text: String
    ) {

        if (selectedImageUris.isEmpty()) {
            return
        }

        val totalPhotos =
            selectedImageUris.size

        val completedCount =
            AtomicInteger(0)

        val failed =
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
                    failed.get()
                ) {
                    return@execute
                }

                val tempFile =
                    createCompressedTempFile(uri)

                if (tempFile == null) {

                    if (failed.compareAndSet(false, true)) {

                        runOnUiThreadSafe {

                            handleUploadFailed(
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
                    failureFlag = failed,

                    onAllUploaded = {

                        if (failed.get()) {
                            return@uploadSinglePhoto
                        }

                        createPhotoPost(

                            postId = postId,
                            uid = uid,
                            fullName = fullName,
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

                                if (failureFlag.compareAndSet(false, true)) {

                                    runOnUiThreadSafe {

                                        handleUploadFailed(
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

                            if (failureFlag.compareAndSet(false, true)) {

                                runOnUiThreadSafe {

                                    handleUploadFailed(
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

            if (failureFlag.compareAndSet(false, true)) {

                runOnUiThreadSafe {

                    handleUploadFailed(
                        "Photo ${index + 1} failed: ${e.message}"
                    )
                }
            }
        }
    }

    // ============================================================
    // CREATE PHOTO POST
    // ============================================================

    private fun createPhotoPost(
        postId: String,
        uid: String,
        fullName: String,
        caption: String,
        imageUrls: List<String>
    ) {

        if (imageUrls.isEmpty()) {

            handleUploadFailed(
                "No uploaded photo URLs were received."
            )

            return
        }

        val postData =
            hashMapOf<String, Any>(

                "postType" to "photo",

                "posterName" to fullName,

                "posterRole" to "Coordinator",

                "organizationId" to "SUPER_ADMIN",

                "organizationName" to "Coordinator",

                "avatarInitials" to getInitials(fullName),

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

                notifyAllOrganizationAdminsAboutPost(
                    postId = postId,
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

                rvFeed.scrollToPosition(0)
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
    // UPLOAD HELPERS
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

    private fun handleUploadFailed(
        message: String
    ) {

        if (!isAdded) {
            return
        }

        setPostingState(false)

        tvPhotoCount.text =
            "Upload failed"

        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setPostingState(
        busy: Boolean,
        busyLabel: String = "Uploading..."
    ) {

        isUploading =
            busy

        btnAddPhoto.isEnabled =
            !busy

        btnRemovePhoto.isEnabled =
            !busy

        btnSubmitPost.isEnabled =
            !busy

        btnSubmitPost.alpha =
            if (busy) 0.5f else 1f

        if (btnSubmitPost is TextView) {

            (btnSubmitPost as TextView).text =
                if (busy) busyLabel else "Post"
        }
    }

    private fun clearSelectedPhotos() {

        selectedImageUris.clear()

        photoPreviewList.removeAllViews()

        photoPreviewContainer.visibility =
            View.GONE

        tvPhotoCount.text =
            "No photos selected"
    }

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

    private fun dpToPx(
        dp: Int
    ): Int {

        return (dp * resources.displayMetrics.density).toInt()
    }

    // ============================================================
    // FEED
    // ============================================================

    private fun loadGlobalPosts() {

        postsListener?.remove()

        postsListener =
            db.collection("posts")
                .orderBy(
                    "createdAt",
                    Query.Direction.DESCENDING
                )
                .addSnapshotListener(useCache = true) { snapshot, error ->

                    if (error != null) {

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Failed to load posts: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    feedRequestId++

                    val documents =
                        snapshot.documents

                    if (documents.isEmpty()) {

                        feedPosts.clear()

                        feedAdapter.updatePosts(
                            emptyList()
                        )

                        return@addSnapshotListener
                    }

                    loadPostsWithLikeStatus(
                        documents
                    )
                }
    }

    private fun loadPostsWithLikeStatus(
        documents: List<DocumentSnapshot>
    ) {

        val currentUser =
            auth.currentUser

        val uid =
            currentUser?.uid

        val requestId = feedRequestId
        // Show cached posts immediately; the like lookups can finish afterwards.
        val previousLikes = feedPosts.filter { it.likedByCurrentUser }.map { it.postId }.toSet()
        val initialPosts = documents.map { documentToFeedPost(it, it.id in previousLikes) }
        feedPosts.clear()
        feedPosts.addAll(initialPosts)
        feedAdapter.updatePosts(initialPosts)

        val loadedPosts =
            mutableListOf<FeedPostModel>()

        // FIX: postId -> oras, para husto ang sorting (latest una)
        val timeById =
            documents.associate {
                it.id to (
                        it.getTimestamp("createdAt")
                            ?.toDate()?.time
                            ?: Long.MAX_VALUE
                        )
            }

        if (uid == null) {

            for (document in documents) {

                loadedPosts.add(
                    documentToFeedPost(
                        document,
                        false
                    )
                )
            }

            feedAdapter.updatePosts(
                loadedPosts
            )

            return
        }

        var completed = 0

        for (document in documents) {

            val postId =
                document.id

            db.collection("posts")
                .document(postId)
                .collection("likes")
                .document(uid)
                .get()
                .addOnSuccessListener { likeDocument ->

                    val liked =
                        likeDocument.exists()

                    loadedPosts.add(
                        documentToFeedPost(
                            document,
                            liked
                        )
                    )
                }
                .addOnFailureListener {

                    loadedPosts.add(
                        documentToFeedPost(
                            document,
                            false
                        )
                    )
                }
                .addOnCompleteListener {

                    if (requestId != feedRequestId || view == null || auth.currentUser?.uid != uid) {
                        return@addOnCompleteListener
                    }

                    completed++

                    if (completed == documents.size) {

                        loadedPosts.sortByDescending {
                            timeById[it.postId] ?: 0L
                        }

                        feedPosts.clear()

                        feedPosts.addAll(
                            loadedPosts
                        )

                        feedAdapter.updatePosts(
                            loadedPosts
                        )
                    }
                }
        }
    }

    private fun documentToFeedPost(
        document: DocumentSnapshot,
        liked: Boolean
    ): FeedPostModel {

        val createdAt =
            document.getTimestamp(
                "createdAt"
            )

        return FeedPostModel(

            postId = document.id,

            postType =
                document.getString("postType") ?: "text",

            posterName =
                document.getString("posterName") ?: "Unknown",

            posterRole =
                document.getString("posterRole") ?: "",

            organizationId =
                document.getString("organizationId") ?: "",

            organizationName =
                document.getString("organizationName") ?: "",

            timeAgo =
                formatTimeAgo(createdAt),

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
                document.getLong("likeCount")?.toInt() ?: 0,

            likedByCurrentUser =
                liked,

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

    private fun toggleLike(
        post: FeedPostModel
    ) {

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

        if (post.postId.isBlank()) {
            return
        }

        val uid =
            currentUser.uid

        val postRef =
            db.collection("posts")
                .document(post.postId)

        val likeRef =
            postRef
                .collection("likes")
                .document(uid)

        db.runTransaction { transaction ->

            val likeSnapshot =
                transaction.get(likeRef)

            val postSnapshot =
                transaction.get(postRef)

            val currentLikeCount =
                postSnapshot
                    .getLong("likeCount")
                    ?.toInt()
                    ?: 0

            if (likeSnapshot.exists()) {

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
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
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

            if (isAdded) {

                Toast.makeText(
                    requireContext(),
                    "Unable to update like: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmDeletePost(
        post: FeedPostModel
    ) {

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle("Delete Post?")
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

    private fun deletePost(
        post: FeedPostModel
    ) {

        if (post.postId.isBlank()) {
            return
        }

        val postReference =
            db.collection("posts")
                .document(post.postId)

        // NEW: papason pud ang comments
        postReference.collection("comments").get()
            .addOnSuccessListener { commentsSnapshot ->
                commentsSnapshot.documents.forEach {
                    it.reference.delete()
                }
            }

        postReference
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

                batch.delete(
                    postReference
                )

                batch.commit()
                    .addOnSuccessListener {

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Post deleted successfully.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { error ->

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Failed to delete post: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
            .addOnFailureListener { error ->

                if (isAdded) {

                    Toast.makeText(
                        requireContext(),
                        "Failed to delete post: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun notifyAllOrganizationAdminsAboutPost(
        postId: String,
        postText: String
    ) {

        db.collection("organizations")
            .get()
            .addOnSuccessListener { snapshot ->

                snapshot.documents.forEach { document ->

                    val orgId =
                        document.id

                    val orgName =
                        document.getString("orgName")
                            ?: document.getString("organizationName")
                            ?: "Organization"

                    val preview =
                        if (postText.length > 80) {
                            postText.take(80) + "..."
                        } else {
                            postText
                        }

                    AdminNotificationHelper.create(
                        organizationId = orgId,

                        organizationName = orgName,

                        title = "New Coordinator Announcement",

                        message =
                            if (preview.isBlank()) {
                                "The Coordinator posted a new announcement."
                            } else {
                                "Coordinator posted: $preview"
                            },

                        type = "announcement",

                        referenceId = postId,

                        postId = postId
                    )
                }
            }
    }

    // NEW: abli ang profile sa organization
    private fun openOrganizationProfile(
        post: FeedPostModel
    ) {

        if (!isAdded) {
            return
        }

        // Hide only the large greeting section.
        // The compact BBCo / Campus Organization header remains visible.
        requireActivity()
            .findViewById<View>(R.id.headerGreetingSection)
            ?.visibility = View.GONE

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                OrganizationProfileFragment.newInstance(
                    post.organizationId,
                    post.organizationName
                )
            )
            .addToBackStack(null)
            .commit()
    }

    private fun getInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split("\\s+".toRegex())
                .filter {
                    it.isNotBlank()
                }

        return when {

            words.size >= 2 ->
                "${words[0].first()}${words[1].first()}"
                    .uppercase()

            words.size == 1 ->
                words[0]
                    .take(2)
                    .uppercase()

            else ->
                "SA"
        }
    }

    private fun formatTimeAgo(
        timestamp: Timestamp?
    ): String {

        if (timestamp == null) {
            return "Just now"
        }

        val createdTime =
            timestamp.toDate().time

        val currentTime =
            System.currentTimeMillis()

        val difference =
            currentTime - createdTime

        val minute =
            60 * 1000L

        val hour =
            60 * minute

        val day =
            24 * hour

        return when {

            difference < minute ->
                "Just now"

            difference < hour -> {

                val minutes =
                    difference / minute

                if (minutes == 1L)
                    "1 minute ago"
                else
                    "$minutes minutes ago"
            }

            difference < day -> {

                val hours =
                    difference / hour

                if (hours == 1L)
                    "1 hour ago"
                else
                    "$hours hours ago"
            }

            difference < 7 * day -> {

                val days =
                    difference / day

                if (days == 1L)
                    "Yesterday"
                else
                    "$days days ago"
            }

            else -> {

                val dateFormat =
                    java.text.SimpleDateFormat(
                        "MMM dd, yyyy",
                        java.util.Locale.getDefault()
                    )

                dateFormat.format(
                    java.util.Date(
                        createdTime
                    )
                )
            }
        }
    }

    override fun onDestroyView() {

        feedRequestId++

        super.onDestroyView()

        postsListener?.remove()

        postsListener = null

        orgAvatarListener?.remove()

        orgAvatarListener = null
    }

    override fun onDestroy() {

        uploadExecutor.shutdown()

        super.onDestroy()
    }
}
