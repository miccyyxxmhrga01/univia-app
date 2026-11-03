package com.example.kayara

data class MemberModel(
    val fullName: String,
    val details: String,
    val role: String,
    val avatarColorStart: String = "#3B176A",
    val avatarColorEnd: String = "#3B176A",
    val applicationNote: String = "",
    val membershipId: String = "",
    val organizationName: String = "",

    // Officer position (President, Vice President, etc.)
    val position: String = "",

    // Profile picture sa student (users/{uid}.profileImageUrl)
    val avatarUrl: String = "",

    // Documents nga gi-submit sa student kung nag-apply
    val documents: List<SubmittedDocument> = emptyList(),

    // UID sa student (users/{studentId})
    val studentId: String = ""
)