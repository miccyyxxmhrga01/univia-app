package com.example.kayara

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase

class FeedAdapter(
    private val posts: MutableList<FeedPostModel>,
    private val onLikeClick: (FeedPostModel) -> Unit,
    private val onDeleteClick: ((FeedPostModel) -> Unit)? = null,

    // Super Admin side:
    // show organization name instead of admin name
    private val showOrganizationNames: Boolean = false,

    // Optional click handler for organization profile
    private val onOrganizationClick: ((FeedPostModel) -> Unit)? = null

) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {

        private const val TYPE_TEXT = 1
        private const val TYPE_PHOTO = 2

        private const val SUPER_ADMIN_ORG_ID =
            "SUPER_ADMIN"
    }

    private val auth =
        FirebaseAuth.getInstance()

    private val db =
        SupabaseDatabase.getInstance()

    // =========================================================
    // SEE MORE / SEE LESS STATE
    // =========================================================

    private val expandedPostIds =
        mutableSetOf<String>()

    // =========================================================
    // ORGANIZATION DATA
    // =========================================================

    private var orgAvatars:
            Map<String, String> =
        emptyMap()

    private val orgNames =
        mutableMapOf<String, String>()

    private val orgNameRequested =
        mutableSetOf<String>()

    // =========================================================
    // CHECK ORGANIZATION POST
    // =========================================================

    private fun isOrgPost(
        post: FeedPostModel
    ): Boolean {

        return post.organizationId.isNotBlank() &&
                post.organizationId != SUPER_ADMIN_ORG_ID
    }

    // =========================================================
    // LOAD ORGANIZATION NAME
    // =========================================================

    private fun loadOrgName(
        organizationId: String
    ) {

        if (
            organizationId.isBlank() ||
            !orgNameRequested.add(
                organizationId
            )
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

                val name =
                    document.getString(
                        "orgName"
                    )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: document.getString(
                            "organizationName"
                        )
                            ?.takeIf {
                                it.isNotBlank()
                            }

                if (
                    name != null
                ) {

                    orgNames[
                        organizationId
                    ] = name.trim()

                    notifyDataSetChanged()
                }
            }
            .addOnFailureListener {

                orgNameRequested.remove(
                    organizationId
                )
            }
    }

    // =========================================================
    // DISPLAY NAME
    // =========================================================

    private fun displayName(
        post: FeedPostModel
    ): String {

        if (
            !showOrganizationNames ||
            !isOrgPost(post)
        ) {

            return post.posterName
        }

        loadOrgName(
            post.organizationId
        )

        return orgNames[
            post.organizationId
        ]
            ?.takeIf {
                it.isNotBlank()
            }
            ?: post.organizationName
                .ifBlank {
                    post.posterName
                }
    }

    // =========================================================
    // INITIALS
    // =========================================================

    private fun initialsOf(
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

                words.take(3)
                    .map {
                        it.first()
                    }
                    .joinToString("")
                    .uppercase()
            }
        }
    }

    // =========================================================
    // TEXT POST VIEW HOLDER
    // =========================================================

    inner class TextPostViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(
        itemView
    ) {

        val posterAvatar: TextView =
            itemView.findViewById(
                R.id.tvPosterAvatar
            )

        val avatarImage: ImageView =
            itemView.findViewById(
                R.id.imgPosterAvatar
            )

        val posterName: TextView =
            itemView.findViewById(
                R.id.tvPosterName
            )

        val timeAgo: TextView =
            itemView.findViewById(
                R.id.tvTimeAgo
            )

        val audienceBadge: TextView =
            itemView.findViewById(
                R.id.tvAudienceBadge
            )

        val textContent: TextView =
            itemView.findViewById(
                R.id.tvTextContent
            )

        val seeMore: TextView =
            itemView.findViewById(
                R.id.tvSeeMore
            )

        val heart: ImageView =
            itemView.findViewById(
                R.id.imgHeart
            )

        val likeCount: TextView =
            itemView.findViewById(
                R.id.tvLikeCount
            )

        val postMenu: ImageView =
            itemView.findViewById(
                R.id.btnPostMenu
            )

        val btnComment: LinearLayout =
            itemView.findViewById(
                R.id.btnComment
            )

        val commentCount: TextView =
            itemView.findViewById(
                R.id.tvCommentCount
            )

        // Instagram-style share button
        val btnShare: LinearLayout =
            itemView.findViewById(
                R.id.btnShare
            )
    }

    // =========================================================
    // PHOTO POST VIEW HOLDER
    // =========================================================

    inner class PhotoPostViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(
        itemView
    ) {

        val posterAvatar: TextView =
            itemView.findViewById(
                R.id.tvPosterAvatar
            )

        val avatarImage: ImageView =
            itemView.findViewById(
                R.id.imgPosterAvatar
            )

        val posterName: TextView =
            itemView.findViewById(
                R.id.tvPosterName
            )

        val timeAgo: TextView =
            itemView.findViewById(
                R.id.tvTimeAgo
            )

        val caption: TextView =
            itemView.findViewById(
                R.id.tvCaption
            )

        val seeMore: TextView =
            itemView.findViewById(
                R.id.tvSeeMore
            )

        val photoViewPager: ViewPager2 =
            itemView.findViewById(
                R.id.photoViewPager
            )

        /*
         * OLD 1/5 counter.
         *
         * We keep it because the XML still has the ID,
         * but it will always be hidden.
         */
        val photoCounter: TextView =
            itemView.findViewById(
                R.id.tvPhotoCounter
            )

        /*
         * NEW Instagram-style dots.
         */
        val dotIndicator: LinearLayout =
            itemView.findViewById(
                R.id.layoutDotIndicator
            )

        val heart: ImageView =
            itemView.findViewById(
                R.id.imgHeart
            )

        val likeCount: TextView =
            itemView.findViewById(
                R.id.tvLikeCount
            )

        val postMenu: ImageView =
            itemView.findViewById(
                R.id.btnPostMenu
            )

        val btnComment: LinearLayout =
            itemView.findViewById(
                R.id.btnComment
            )

        val commentCount: TextView =
            itemView.findViewById(
                R.id.tvCommentCount
            )

        val btnShare: LinearLayout =
            itemView.findViewById(
                R.id.btnShare
            )

        /*
         * Keep callback reference so it can be removed
         * when RecyclerView reuses the ViewHolder.
         */
        var pageChangeCallback:
                ViewPager2.OnPageChangeCallback? =
            null
    }

    // =========================================================
    // DOT INDICATOR
    // =========================================================

    private fun setupDots(
        indicatorLayout: LinearLayout,
        count: Int,
        currentPosition: Int
    ) {

        indicatorLayout.removeAllViews()

        /*
         * One photo does not need dots.
         */
        if (
            count <= 1
        ) {

            indicatorLayout.visibility =
                View.GONE

            return
        }

        indicatorLayout.visibility =
            View.VISIBLE

        for (
        i in 0 until count
        ) {

            val dot =
                View(
                    indicatorLayout.context
                )

            /*
             * 7dp dots.
             */
            val size =
                dp(
                    indicatorLayout,
                    7
                )

            val params =
                LinearLayout.LayoutParams(
                    size,
                    size
                )

            params.marginStart =
                dp(
                    indicatorLayout,
                    3
                )

            params.marginEnd =
                dp(
                    indicatorLayout,
                    3
                )

            dot.layoutParams =
                params

            dot.setBackgroundResource(
                if (
                    i == currentPosition
                ) {

                    R.drawable.bg_dot_active

                } else {

                    R.drawable.bg_dot_inactive
                }
            )

            indicatorLayout.addView(
                dot
            )
        }
    }

    // =========================================================
    // DP
    // =========================================================

    private fun dp(
        view: View,
        value: Int
    ): Int {

        return (
                value *
                        view.resources
                            .displayMetrics
                            .density
                ).toInt()
    }

    // =========================================================
    // VIEW TYPE
    // =========================================================

    override fun getItemViewType(
        position: Int
    ): Int {

        return if (
            posts[
                position
            ].postType == "photo"
        ) {

            TYPE_PHOTO

        } else {

            TYPE_TEXT
        }
    }

    // =========================================================
    // CREATE VIEW HOLDER
    // =========================================================

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        return if (
            viewType == TYPE_PHOTO
        ) {

            val view =
                LayoutInflater.from(
                    parent.context
                )
                    .inflate(
                        R.layout.item_feed_photo_post,
                        parent,
                        false
                    )

            PhotoPostViewHolder(
                view
            )

        } else {

            val view =
                LayoutInflater.from(
                    parent.context
                )
                    .inflate(
                        R.layout.item_feed_text_post,
                        parent,
                        false
                    )

            TextPostViewHolder(
                view
            )
        }
    }

    // =========================================================
    // BIND VIEW HOLDER
    // =========================================================

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

        val post =
            posts[
                position
            ]

        when (
            holder
        ) {

            is TextPostViewHolder -> {

                bindTextPost(
                    holder,
                    post
                )
            }

            is PhotoPostViewHolder -> {

                bindPhotoPost(
                    holder,
                    post
                )
            }
        }
    }

    // =========================================================
    // SET ORGANIZATION AVATARS
    // =========================================================

    fun setOrgAvatars(
        avatars: Map<String, String>
    ) {

        if (
            avatars ==
            orgAvatars
        ) {

            return
        }

        orgAvatars =
            avatars

        notifyDataSetChanged()
    }

    // =========================================================
    // BIND AVATAR
    // =========================================================

    private fun bindAvatar(
        initialsView: TextView,
        imageView: ImageView,
        post: FeedPostModel
    ) {

        initialsView.text =
            if (
                showOrganizationNames &&
                isOrgPost(post)
            ) {

                initialsOf(
                    displayName(post)
                )

            } else {

                post.avatarInitials
                    .ifBlank {
                        "ORG"
                    }
            }


        // =========================================================
        // SUPER ADMIN PROFILE PICTURE
        // =========================================================

        if (
            post.organizationId ==
            SUPER_ADMIN_ORG_ID
        ) {

            val superAdminUid =
                post.createdBy

            if (
                superAdminUid.isBlank()
            ) {

                showInitials(
                    initialsView,
                    imageView
                )

                return
            }


            db.collection(
                "users"
            )
                .document(
                    superAdminUid
                )
                .get()
                .addOnSuccessListener { document ->

                    val profileImageUrl =
                        document.getString(
                            "profileImageUrl"
                        )
                            ?.trim()
                            .orEmpty()


                    if (
                        profileImageUrl.isNotBlank()
                    ) {

                        imageView.visibility =
                            View.VISIBLE

                        initialsView.visibility =
                            View.GONE


                        Glide.with(
                            imageView.context
                        )
                            .load(
                                profileImageUrl
                            )
                            .circleCrop()
                            .into(
                                imageView
                            )

                    } else {

                        showInitials(
                            initialsView,
                            imageView
                        )
                    }
                }
                .addOnFailureListener {

                    showInitials(
                        initialsView,
                        imageView
                    )
                }


            return
        }


        // =========================================================
        // NORMAL ORGANIZATION AVATAR
        // =========================================================

        val avatarUrl =
            orgAvatars[
                post.organizationId
            ]
                .orEmpty()


        if (
            avatarUrl.isNotBlank()
        ) {

            imageView.visibility =
                View.VISIBLE

            initialsView.visibility =
                View.GONE


            Glide.with(
                imageView.context
            )
                .load(
                    avatarUrl
                )
                .circleCrop()
                .into(
                    imageView
                )

        } else {

            showInitials(
                initialsView,
                imageView
            )
        }
    }

    private fun showInitials(
        initialsView: TextView,
        imageView: ImageView
    ) {

        Glide.with(
            imageView.context
        )
            .clear(
                imageView
            )

        imageView.setImageDrawable(
            null
        )

        imageView.visibility =
            View.GONE

        initialsView.visibility =
            View.VISIBLE
    }

    // =========================================================
    // ORGANIZATION PROFILE CLICK
    // =========================================================

    private fun bindProfileClick(
        post: FeedPostModel,
        vararg clickableViews: View
    ) {

        val orgId =
            post.organizationId

        val customHandler =
            onOrganizationClick


        clickableViews.forEach { clickable ->


            // =====================================================
            // SUPER ADMIN PROFILE
            // =====================================================

            if (
                orgId ==
                SUPER_ADMIN_ORG_ID
            ) {

                clickable.setOnClickListener { view ->

                    val intent =
                        Intent(
                            view.context,
                            SuperAdminProfileActivity::class.java
                        )

                    intent.putExtra(
                        "viewOnly",
                        true
                    )

                    intent.putExtra(
                        "superAdminUid",
                        post.createdBy
                    )

                    view.context.startActivity(
                        intent
                    )
                }

                clickable.isClickable =
                    true

                return@forEach
            }


            // =====================================================
            // ORGANIZATION PROFILE
            // =====================================================

            val canOpenOrganization =
                orgId.isNotBlank()


            if (
                canOpenOrganization
            ) {

                if (
                    customHandler != null
                ) {

                    clickable.setOnClickListener {

                        customHandler.invoke(
                            post
                        )
                    }

                } else {

                    clickable.setOnClickListener { view ->

                        val intent =
                            Intent(
                                view.context,
                                OrganizationProfileActivity::class.java
                            )

                        intent.putExtra(
                            "organizationId",
                            orgId
                        )

                        intent.putExtra(
                            "organizationName",
                            post.organizationName
                        )

                        view.context.startActivity(
                            intent
                        )
                    }
                }

                clickable.isClickable =
                    true

            } else {

                clickable.setOnClickListener(
                    null
                )

                clickable.isClickable =
                    false
            }
        }
    }

    // =========================================================
    // SEE MORE / SEE LESS
    // =========================================================

    private fun bindExpandableText(
        textView: TextView,
        seeMoreView: TextView,
        text: String,
        postId: String
    ) {

        textView.text =
            text

        if (
            text.isBlank()
        ) {

            seeMoreView.visibility =
                View.GONE

            return
        }

        val isExpanded =
            expandedPostIds.contains(
                postId
            )

        textView.maxLines =
            if (isExpanded) {
                Int.MAX_VALUE
            } else {
                5
            }

        textView.ellipsize =
            if (isExpanded) {
                null
            } else {
                TextUtils.TruncateAt.END
            }

        seeMoreView.text =
            if (isExpanded) {
                "See less"
            } else {
                "See more"
            }

        // Hide first while Android calculates the actual text layout.
        seeMoreView.visibility =
            View.GONE

        textView.post {

            val layout =
                textView.layout

            val hasMoreThanFiveLines =
                if (isExpanded) {

                    textView.lineCount > 5

                } else {

                    val lastLine =
                        textView.lineCount - 1

                    textView.lineCount >= 5 &&
                            layout != null &&
                            lastLine >= 0 &&
                            layout.getEllipsisCount(
                                lastLine
                            ) > 0
                }

            seeMoreView.visibility =
                if (hasMoreThanFiveLines) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        seeMoreView.setOnClickListener {

            val currentlyExpanded =
                expandedPostIds.contains(
                    postId
                )

            if (currentlyExpanded) {

                expandedPostIds.remove(
                    postId
                )

                textView.maxLines =
                    5

                textView.ellipsize =
                    TextUtils.TruncateAt.END

                seeMoreView.text =
                    "See more"

            } else {

                expandedPostIds.add(
                    postId
                )

                textView.maxLines =
                    Int.MAX_VALUE

                textView.ellipsize =
                    null

                seeMoreView.text =
                    "See less"
            }
        }
    }

    // =========================================================
    // TEXT POST
    // =========================================================

    private fun bindTextPost(
        holder: TextPostViewHolder,
        post: FeedPostModel
    ) {

        bindAvatar(
            holder.posterAvatar,
            holder.avatarImage,
            post
        )

        bindProfileClick(
            post,
            holder.posterAvatar,
            holder.avatarImage,
            holder.posterName
        )

        holder.posterName.text =
            displayName(
                post
            )

        holder.timeAgo.text =
            if (
                post.edited
            ) {

                "${post.timeAgo} · Edited"

            } else {

                post.timeAgo
            }

        bindExpandableText(
            textView =
                holder.textContent,

            seeMoreView =
                holder.seeMore,

            text =
                post.textContent,

            postId =
                post.postId
        )

        holder.likeCount.text =
            post.likeCount
                .toString()

        holder.audienceBadge.text =
            post.audience
                .replace(
                    "_",
                    " "
                )
                .uppercase()

        updateHeartAppearance(
            holder.heart,
            post.likedByCurrentUser
        )

        // =====================================================
        // LIKE
        // =====================================================

        holder.heart.setOnClickListener {

            onLikeClick(
                post
            )
        }

        /*
         * Make the entire small Like area work too
         * if parent XML captures the tap.
         */
        val likeParent =
            holder.heart.parent

        if (
            likeParent is View
        ) {

            likeParent.setOnClickListener {

                onLikeClick(
                    post
                )
            }
        }

        // =====================================================
        // COMMENT
        // =====================================================

        holder.commentCount.text =
            post.commentCount
                .toString()

        holder.btnComment
            .setOnClickListener { view ->

                PostInteractions.showComments(
                    view.context,
                    post
                )
            }

        // =====================================================
        // SHARE
        // =====================================================

        holder.btnShare
            .setOnClickListener { view ->

                sharePost(
                    view,
                    post
                )
            }

        // =====================================================
        // MENU
        // =====================================================

        setupPostMenu(
            holder.postMenu,
            post
        )
    }

    // =========================================================
    // PHOTO POST
    // =========================================================

    private fun bindPhotoPost(
        holder: PhotoPostViewHolder,
        post: FeedPostModel
    ) {

        bindAvatar(
            holder.posterAvatar,
            holder.avatarImage,
            post
        )

        bindProfileClick(
            post,
            holder.posterAvatar,
            holder.avatarImage,
            holder.posterName
        )

        holder.posterName.text =
            displayName(
                post
            )

        holder.timeAgo.text =
            if (
                post.edited
            ) {

                "${post.timeAgo} · Edited"

            } else {

                post.timeAgo
            }

        // =====================================================
        // CAPTION
        // =====================================================

        holder.caption.visibility =
            if (
                post.caption.isBlank()
            ) {

                holder.seeMore.visibility =
                    View.GONE

                View.GONE

            } else {

                bindExpandableText(
                    textView =
                        holder.caption,

                    seeMoreView =
                        holder.seeMore,

                    text =
                        post.caption,

                    postId =
                        post.postId
                )

                View.VISIBLE
            }

        // =====================================================
        // LIKE COUNT
        // =====================================================

        holder.likeCount.text =
            post.likeCount
                .toString()

        updateHeartAppearance(
            holder.heart,
            post.likedByCurrentUser
        )

        // =====================================================
        // REMOVE OLD VIEWPAGER CALLBACK
        // =====================================================

        holder.pageChangeCallback
            ?.let { callback ->

                holder.photoViewPager
                    .unregisterOnPageChangeCallback(
                        callback
                    )
            }

        holder.pageChangeCallback =
            null

        // =====================================================
        // PHOTO CAROUSEL
        // =====================================================

        val photoList =
            post.imageUrls

        val photoAdapter =
            PhotoCarouselAdapter(
                photoList
            )

        holder.photoViewPager.adapter =
            photoAdapter

        // =====================================================
        // REMOVE 1/5 COUNTER
        // =====================================================

        holder.photoCounter.visibility =
            View.GONE

        // =====================================================
        // INITIAL DOTS
        // =====================================================

        setupDots(
            indicatorLayout =
                holder.dotIndicator,

            count =
                photoList.size,

            currentPosition =
                0
        )

        // =====================================================
        // VIEWPAGER DOT CALLBACK
        // =====================================================

        val pageChangeCallback =
            object :
                ViewPager2.OnPageChangeCallback() {

                override fun onPageSelected(
                    position: Int
                ) {

                    super.onPageSelected(
                        position
                    )

                    setupDots(
                        indicatorLayout =
                            holder.dotIndicator,

                        count =
                            photoList.size,

                        currentPosition =
                            position
                    )
                }
            }

        holder.pageChangeCallback =
            pageChangeCallback

        holder.photoViewPager
            .registerOnPageChangeCallback(
                pageChangeCallback
            )

        // =====================================================
        // LIKE
        // =====================================================

        holder.heart
            .setOnClickListener {

                onLikeClick(
                    post
                )
            }

        val likeParent =
            holder.heart.parent

        if (
            likeParent is View
        ) {

            likeParent.setOnClickListener {

                onLikeClick(
                    post
                )
            }
        }

        // =====================================================
        // COMMENT
        // =====================================================

        holder.commentCount.text =
            post.commentCount
                .toString()

        holder.btnComment
            .setOnClickListener { view ->

                PostInteractions.showComments(
                    view.context,
                    post
                )
            }

        // =====================================================
        // SHARE
        // =====================================================

        holder.btnShare
            .setOnClickListener { view ->

                sharePost(
                    view,
                    post
                )
            }

        // =====================================================
        // MENU
        // =====================================================

        setupPostMenu(
            holder.postMenu,
            post
        )
    }

    // =========================================================
    // SHARE POST
    // =========================================================

    private fun sharePost(
        view: View,
        post: FeedPostModel
    ) {

        val organization =
            displayName(
                post
            )

        val content =
            if (
                post.postType == "photo"
            ) {

                post.caption

            } else {

                post.textContent
            }

        val shareText =
            buildString {

                append(
                    organization
                )

                if (
                    content.isNotBlank()
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        content
                    )
                }
            }

        val shareIntent =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    "text/plain"

                putExtra(
                    Intent.EXTRA_TEXT,
                    shareText
                )
            }

        try {

            view.context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share post"
                )
            )

        } catch (_: Exception) {

            // Do nothing if no share app is available.
        }
    }

    // =========================================================
    // HEART APPEARANCE
    // =========================================================

    private fun updateHeartAppearance(
        heart: ImageView,
        liked: Boolean
    ) {

        if (
            liked
        ) {

            heart.setImageResource(
                R.drawable.ic_heart_filled
            )

            heart.setColorFilter(
                Color.RED,
                PorterDuff.Mode.SRC_IN
            )

        } else {

            heart.setImageResource(
                R.drawable.ic_heart
            )

            heart.clearColorFilter()
        }
    }

    // =========================================================
    // POST OWNER MENU
    // =========================================================

    private fun setupPostMenu(
        menuButton: ImageView,
        post: FeedPostModel
    ) {

        val currentUserId =
            auth.currentUser
                ?.uid

        val isPostOwner =
            !currentUserId.isNullOrBlank() &&
                    post.createdBy.isNotBlank() &&
                    post.createdBy == currentUserId

        if (
            onDeleteClick == null ||
            !isPostOwner
        ) {

            menuButton.visibility =
                View.GONE

            menuButton.isClickable =
                false

            menuButton.isFocusable =
                false

            menuButton.setOnClickListener(
                null
            )

            return
        }

        menuButton.visibility =
            View.VISIBLE

        menuButton.isClickable =
            true

        menuButton.isFocusable =
            true

        menuButton.alpha =
            1f

        val outValue =
            TypedValue()

        menuButton.context
            .theme
            .resolveAttribute(
                android.R.attr
                    .selectableItemBackgroundBorderless,
                outValue,
                true
            )

        menuButton.setBackgroundResource(
            outValue.resourceId
        )

        menuButton
            .setOnClickListener { view ->

                PostInteractions
                    .showPostMenu(
                        context =
                            view.context,

                        onEdit = {

                            PostInteractions
                                .showEditDialog(
                                    view.context,
                                    post
                                )
                        },

                        onDelete = {

                            onDeleteClick.invoke(
                                post
                            )
                        }
                    )
            }
    }

    // =========================================================
    // UPDATE POSTS
    // =========================================================

    fun updatePosts(
        newPosts: List<FeedPostModel>
    ) {

        posts.clear()

        posts.addAll(
            newPosts
        )

        notifyDataSetChanged()
    }

    // =========================================================
    // ITEM COUNT
    // =========================================================

    override fun getItemCount():
            Int {

        return posts.size
    }

    // =========================================================
    // RECYCLE PHOTO HOLDER
    // =========================================================

    override fun onViewRecycled(
        holder: RecyclerView.ViewHolder
    ) {

        if (
            holder is PhotoPostViewHolder
        ) {

            holder.pageChangeCallback
                ?.let { callback ->

                    holder.photoViewPager
                        .unregisterOnPageChangeCallback(
                            callback
                        )
                }

            holder.pageChangeCallback =
                null
        }

        super.onViewRecycled(
            holder
        )
    }
}
