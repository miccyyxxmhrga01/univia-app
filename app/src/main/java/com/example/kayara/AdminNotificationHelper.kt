package com.example.kayara

import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

object AdminNotificationHelper {

    private val db =
        SupabaseDatabase.getInstance()

    /**
     * Creates a notification intended for the ADMIN
     * of one specific organization.
     */
    fun create(
        organizationId: String,
        organizationName: String = "",
        title: String,
        message: String,
        type: String,
        referenceId: String = "",
        studentId: String = "",
        postId: String = "",
        actorId: String = "",
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {

        if (organizationId.isBlank()) {
            return
        }

        val notification =
            hashMapOf<String, Any>(
                "organizationId" to organizationId,
                "organizationName" to organizationName,
                "title" to title,
                "message" to message,
                "type" to type,
                "recipientRole" to "admin",
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )

        if (referenceId.isNotBlank()) {
            notification["referenceId"] = referenceId
        }

        if (studentId.isNotBlank()) {
            notification["studentId"] = studentId
        }

        if (postId.isNotBlank()) {
            notification["postId"] = postId
        }

        if (actorId.isNotBlank()) {
            notification["actorId"] = actorId
        }

        db.collection(
            "notifications"
        )
            .add(
                notification
            )
            .addOnSuccessListener {

                onSuccess?.invoke()
            }
            .addOnFailureListener { error ->

                onFailure?.invoke(
                    error
                )
            }
    }
}