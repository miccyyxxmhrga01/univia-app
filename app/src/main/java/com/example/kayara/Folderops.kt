package com.example.kayara

import android.content.Context
import android.text.InputFilter
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.Timestamp
import com.example.kayara.data.DocumentReference
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase

/**
 * Mga folder nga gihimo sa admin.
 *
 * organizationFolders/{folderId}
 *   organizationId : String
 *   name           : String
 *   parentId       : ""            -> root (makita sa Documents list)
 *                    "sys:<name>"  -> sulod sa system folder (pananglitan "sys:Organization Requirements")
 *                    "<folderId>"  -> sulod sa lain nga folder nga gihimo sa admin
 *   createdBy, createdAt
 *
 * Mga file sa sulod sa gihimo nga folder:
 * organizationDocuments/{id}  ->  folderId = <folderId>, folderName = ""
 * (parehas gihapon nga collection, mao nga ang Super Admin makita ug maihap gihapon)
 */
object FolderOps {

    fun createFolder(
        db: SupabaseDatabase,
        organizationId: String,
        name: String,
        parentId: String,
        uid: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        val data =
            hashMapOf<String, Any>(
                "organizationId" to organizationId,
                "name" to name,
                "parentId" to parentId,
                "createdBy" to uid,
                "createdAt" to Timestamp.now()
            )

        db.collection("organizationFolders")
            .add(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Unknown error") }
    }

    fun renameFolder(
        db: SupabaseDatabase,
        folderId: String,
        newName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        db.collection("organizationFolders")
            .document(folderId)
            .update("name", newName)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Unknown error") }
    }

    // Ihap ang direct items sa usa ka folder: mga subfolder + mga file
    fun countItems(
        folderDocs: List<DocumentSnapshot>,
        fileDocs: List<DocumentSnapshot>,
        folderId: String
    ): Int {

        val subfolders =
            folderDocs.count { it.getString("parentId") == folderId }

        val files =
            fileDocs.count { it.getString("folderId") == folderId }

        return subfolders + files
    }

    // I-delete ang folder ug TANANG naa sa sulod niini (subfolders + files)
    fun deleteFolderTree(
        db: SupabaseDatabase,
        organizationId: String,
        folderId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {

        db.collection("organizationFolders")
            .whereEqualTo("organizationId", organizationId)
            .get()
            .addOnSuccessListener { folderResult ->

                db.collection("organizationDocuments")
                    .whereEqualTo("organizationId", organizationId)
                    .get()
                    .addOnSuccessListener { docResult ->

                        // Pangitaa ang tanang subfolders (BFS)
                        val childrenByParent =
                            folderResult.documents.groupBy {
                                it.getString("parentId") ?: ""
                            }

                        val idsToDelete = mutableSetOf(folderId)

                        val queue = ArrayDeque<String>()

                        queue.add(folderId)

                        while (queue.isNotEmpty()) {

                            val current = queue.removeFirst()

                            childrenByParent[current]?.forEach { child ->

                                if (idsToDelete.add(child.id)) {
                                    queue.add(child.id)
                                }
                            }
                        }

                        val refs = mutableListOf<DocumentReference>()

                        folderResult.documents
                            .filter { idsToDelete.contains(it.id) }
                            .forEach { refs.add(it.reference) }

                        docResult.documents
                            .filter {
                                idsToDelete.contains(it.getString("folderId") ?: "")
                            }
                            .forEach { refs.add(it.reference) }

                        val chunks = refs.chunked(400)

                        if (chunks.isEmpty()) {
                            onSuccess()
                            return@addOnSuccessListener
                        }

                        var done = 0
                        var failed = false

                        chunks.forEach { chunk ->

                            val batch = db.batch()

                            chunk.forEach { batch.delete(it) }

                            batch.commit()
                                .addOnSuccessListener {

                                    done++

                                    if (done == chunks.size && !failed) {
                                        onSuccess()
                                    }
                                }
                                .addOnFailureListener {

                                    if (!failed) {
                                        failed = true
                                        onError(it.message ?: "Unknown error")
                                    }
                                }
                        }
                    }
                    .addOnFailureListener {
                        onError(it.message ?: "Unknown error")
                    }
            }
            .addOnFailureListener {
                onError(it.message ?: "Unknown error")
            }
    }

    // =========================================================
    // NAME DIALOG (New folder / Rename)
    // =========================================================

    fun showNameDialog(
        context: Context,
        title: String,
        initialValue: String,
        positiveLabel: String,
        onConfirm: (String) -> Unit
    ) {

        val container = LinearLayout(context)

        container.setPadding(60, 24, 60, 0)

        val input = EditText(context)

        input.hint = "Folder name"
        input.setSingleLine(true)
        input.filters = arrayOf(InputFilter.LengthFilter(60))
        input.setText(initialValue)
        input.setSelection(input.text.length)

        container.addView(
            input,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positiveLabel) { _, _ ->

                val name = input.text.toString().trim()

                if (name.isEmpty()) {

                    Toast.makeText(
                        context,
                        "Please enter a folder name.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    onConfirm(name)
                }
            }
            .show()
    }
}