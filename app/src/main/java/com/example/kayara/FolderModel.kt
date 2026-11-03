package com.example.kayara

data class FolderModel(
    val orgName: String = "",
    val fileCount: Int = 0,
    val status: String = "clear",
    val pendingCount: Int = 0,
    val folderColor: String = "#7C3AED",

    // Firestore references
    val organizationId: String = "",
    val applicationId: String = ""
)