package com.example.kayara

data class FeedPostModel(
    val postId: String = "",
    val postType: String = "text",
    val posterName: String = "",
    val posterRole: String = "",
    val organizationId: String = "",
    val organizationName: String = "",
    val timeAgo: String = "",
    val avatarInitials: String = "",
    val avatarColorStart: String = "#3B176A",
    val avatarColorEnd: String = "#7C3AED",
    val audience: String = "everyone",
    val textContent: String = "",
    val caption: String = "",
    val imageUrls: List<String> = emptyList(),
    val likeCount: Int = 0,
    val likedByCurrentUser: Boolean = false,

    // UID sa user nga naghimo sa post
    val createdBy: String = "",

    // NEW
    val commentCount: Int = 0,
    val edited: Boolean = false
)