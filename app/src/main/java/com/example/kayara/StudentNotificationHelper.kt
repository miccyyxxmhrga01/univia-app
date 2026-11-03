package com.example.kayara

import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

object StudentNotificationHelper {

    private val db =
        SupabaseDatabase.getInstance()

    fun create(
        studentId: String,
        title: String,
        message: String,
        type: String,
        organizationId: String = "",
        organizationName: String = "",
        referenceId: String = ""
    ) {

        if (studentId.isBlank()) {
            return
        }

        val data =
            hashMapOf<String, Any>(
                "userId" to studentId,
                "title" to title,
                "message" to message,
                "type" to type,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )

        if (organizationId.isNotBlank()) {
            data["organizationId"] =
                organizationId
        }

        if (organizationName.isNotBlank()) {
            data["organizationName"] =
                organizationName
        }

        if (referenceId.isNotBlank()) {
            data["referenceId"] =
                referenceId
        }

        db.collection("notifications")
            .add(data)
    }
}