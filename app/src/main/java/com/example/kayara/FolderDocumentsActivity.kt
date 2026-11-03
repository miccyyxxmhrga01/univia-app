package com.example.kayara

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase
import java.text.SimpleDateFormat
import java.util.Locale

class FolderDocumentsActivity : AppCompatActivity() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var auth: FirebaseAuth

    private lateinit var db: SupabaseDatabase

    private lateinit var requirementAdapter:
            RequirementAdapter

    private lateinit var subfolderAdapter:
            SubfolderAdapter

    private lateinit var tvFolderEmpty:
            TextView


    // =========================================================
    // CONTENT NI NGA FOLDER
    // =========================================================

    // Mga file
    private val documents =
        mutableListOf<RequirementModel>()

    // Mga folder sa sulod
    private val subfolders =
        mutableListOf<DocumentFolderModel>()


    // =========================================================
    // ORGANIZATION
    // =========================================================

    private var organizationId =
        ""

    private var organizationName =
        ""

    private var applicationId =
        ""

    private var folderName =
        ""

    // Naay value = folder nga gihimo sa admin (organizationFolders/{id})
    private var folderId =
        ""

    // Dalan: Documents › Reports › 2026
    private var folderPath =
        ""


    // =========================================================
    // VIEW ONLY
    // =========================================================

    private var viewOnly =
        false

    // Separate permission:
    // Admin: viewOnly = false, canDelete = true
    // Super Admin: viewOnly = true, canDelete = true
    // Student/viewer: viewOnly = true, canDelete = false
    private var canDelete =
        false


    // =========================================================
    // LOAD STATE (para sa "empty folder" message)
    // =========================================================

    private var filesLoaded =
        false

    private var subfoldersLoaded =
        false

    private var resumedOnce =
        false


    // Susi sa mga subfolder niini nga folder:
    // - folder nga gihimo sa admin: ang iyang ID
    // - system folder: "sys:<folderName>"
    private val folderParentKey: String
        get() =
            if (folderId.isNotBlank()) {
                folderId
            } else {
                "sys:$folderName"
            }


    // =========================================================
    // FILE PICKER
    // =========================================================

    private val pickDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {

                try {

                    contentResolver
                        .takePersistableUriPermission(
                            uri,
                            android.content.Intent
                                .FLAG_GRANT_READ_URI_PERMISSION
                        )

                } catch (
                    e: Exception
                ) {
                    // Some providers do not support
                    // persistable permission.
                }


                handlePickedDocument(
                    uri
                )

            } else {

                Toast.makeText(
                    this,
                    "No document selected.",
                    Toast.LENGTH_SHORT
                ).show()
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
            R.layout.activity_folder_documents
        )


        auth =
            FirebaseAuth.getInstance()


        db =
            SupabaseDatabase.getInstance()


        // =====================================================
        // INTENT
        // =====================================================

        organizationId =
            intent.getStringExtra(
                "organizationId"
            ) ?: ""


        folderName =
            intent.getStringExtra(
                "folderName"
            ) ?: "Documents"


        folderId =
            intent.getStringExtra(
                "folderId"
            ) ?: ""


        folderPath =
            intent.getStringExtra(
                "folderPath"
            ) ?: "Documents › $folderName"


        organizationName =
            intent.getStringExtra(
                "organizationName"
            ) ?: ""


        viewOnly =
            intent.getBooleanExtra(
                "viewOnly",
                false
            )

        canDelete =
            intent.getBooleanExtra(
                "canDelete",
                !viewOnly
            )


        // =====================================================
        // TITLE + PATH
        // =====================================================

        findViewById<TextView>(
            R.id.tvFolderTitle
        ).text =
            folderName


        findViewById<TextView>(
            R.id.tvFolderPath
        ).text =
            folderPath


        tvFolderEmpty =
            findViewById(
                R.id.tvFolderEmpty
            )


        // =====================================================
        // RECYCLER VIEW: [subfolders] + [files]
        // =====================================================

        val rvFolderDocuments =
            findViewById<RecyclerView>(
                R.id.rvFolderDocuments
            )


        rvFolderDocuments.layoutManager =
            LinearLayoutManager(
                this
            )


        requirementAdapter =
            RequirementAdapter(
                documents,

                // Long-press sa file: Delete
                // Admin ug Super Admin ra kung canDelete = true
                onLongClick = { document ->

                    if (canDelete) {

                        confirmDeleteFile(
                            document
                        )
                    }
                }
            )


        subfolderAdapter =
            SubfolderAdapter(
                subfolders,

                onClick = { subfolder ->

                    openSubfolder(
                        subfolder
                    )
                },

                // Long-press sa folder: Rename / Delete (admin ra)
                onLongClick = { subfolder ->

                    if (!viewOnly) {

                        showSubfolderOptions(
                            subfolder
                        )
                    }
                }
            )


        val concatAdapter =
            ConcatAdapter(
                subfolderAdapter,
                requirementAdapter
            )


        concatAdapter.registerAdapterDataObserver(

            object : RecyclerView.AdapterDataObserver() {

                override fun onChanged() {
                    updateEmptyState()
                }

                override fun onItemRangeInserted(
                    positionStart: Int,
                    itemCount: Int
                ) {
                    updateEmptyState()
                }

                override fun onItemRangeRemoved(
                    positionStart: Int,
                    itemCount: Int
                ) {
                    updateEmptyState()
                }
            }
        )


        rvFolderDocuments.adapter =
            concatAdapter


        // =====================================================
        // BACK
        // =====================================================

        findViewById<ImageView>(
            R.id.btnBack
        ).setOnClickListener {

            finish()
        }


        // =====================================================
        // ADD (+): NEW FOLDER o NEW FILE
        // =====================================================

        val fabAddDocument =
            findViewById<ImageView>(
                R.id.fabAddDocument
            )


        if (viewOnly) {

            fabAddDocument.visibility =
                ImageView.GONE

        } else {

            fabAddDocument.visibility =
                ImageView.VISIBLE


            fabAddDocument.setOnClickListener {

                showAddChoiceDialog()
            }
        }


        // =====================================================
        // LOAD ORGANIZATION
        // =====================================================

        loadOrganizationInformation()
    }


    // =========================================================
    // ON RESUME
    // I-refresh ang mga subfolder (ihap sa items) kung mobalik
    // gikan sa sulod sa laing folder.
    // =========================================================

    override fun onResume() {

        super.onResume()

        if (resumedOnce) {

            loadSubfolders()
        }

        resumedOnce =
            true
    }


    // =========================================================
    // EMPTY FOLDER MESSAGE
    // =========================================================

    private fun updateEmptyState() {

        if (
            !subfoldersLoaded ||
            !filesLoaded
        ) {

            tvFolderEmpty.visibility =
                View.GONE

            return
        }


        val empty =
            subfolders.isEmpty() &&
                    documents.isEmpty()


        tvFolderEmpty.visibility =
            if (empty) View.VISIBLE else View.GONE


        tvFolderEmpty.text =
            if (viewOnly) {

                "This folder is empty."

            } else {

                "This folder is empty.\nTap + to add a folder or a file."
            }
    }


    // =========================================================
    // ADD CHOICE: NEW FOLDER / NEW FILE
    // =========================================================

    private fun showAddChoiceDialog() {

        if (
            organizationId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Organization information is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        val dialog =
            Dialog(this)


        val view =
            LayoutInflater.from(this)
                .inflate(
                    R.layout.dialog_add_choice,
                    null
                )


        dialog.setContentView(view)


        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )


        dialog.window?.setGravity(
            Gravity.BOTTOM
        )


        view.findViewById<View>(
            R.id.optionNewFolder
        ).setOnClickListener {

            dialog.dismiss()

            showNewFolderDialog()
        }


        view.findViewById<View>(
            R.id.optionNewFile
        ).setOnClickListener {

            dialog.dismiss()

            openDocumentPicker()
        }


        view.findViewById<TextView>(
            R.id.btnCancelAdd
        ).setOnClickListener {

            dialog.dismiss()
        }


        dialog.show()


        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }


    // =========================================================
    // NEW FOLDER
    // =========================================================

    private fun showNewFolderDialog() {

        FolderOps.showNameDialog(
            context =
                this,

            title =
                "New folder",

            initialValue =
                "",

            positiveLabel =
                "Create"
        ) { name ->

            if (
                subfolders.any {
                    it.folderName.equals(
                        name,
                        ignoreCase = true
                    )
                }
            ) {

                Toast.makeText(
                    this,
                    "A folder named \"$name\" already exists here.",
                    Toast.LENGTH_LONG
                ).show()

                return@showNameDialog
            }


            FolderOps.createFolder(
                db =
                    db,

                organizationId =
                    organizationId,

                name =
                    name,

                parentId =
                    folderParentKey,

                uid =
                    auth.currentUser?.uid ?: "",

                onSuccess = {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        Toast.makeText(
                            this,
                            "Folder created.",
                            Toast.LENGTH_SHORT
                        ).show()

                        loadSubfolders()
                    }
                },

                onError = { message ->

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        Toast.makeText(
                            this,
                            "Failed to create folder: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }


    // =========================================================
    // OPEN SUBFOLDER (parehas nga screen, sa sulod pa)
    // =========================================================

    private fun openSubfolder(
        subfolder: DocumentFolderModel
    ) {

        val intent =
            Intent(
                this,
                FolderDocumentsActivity::class.java
            )


        intent.putExtra(
            "folderName",
            subfolder.folderName
        )


        intent.putExtra(
            "folderId",
            subfolder.folderId
        )


        intent.putExtra(
            "folderPath",
            "$folderPath › ${subfolder.folderName}"
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
            "viewOnly",
            viewOnly
        )

        intent.putExtra(
            "canDelete",
            canDelete
        )


        startActivity(
            intent
        )
    }


    // =========================================================
    // SUBFOLDER OPTIONS (long-press): RENAME / DELETE
    // =========================================================

    private fun showSubfolderOptions(
        subfolder: DocumentFolderModel
    ) {

        AlertDialog.Builder(this)
            .setTitle(subfolder.folderName)
            .setItems(
                arrayOf(
                    "Rename",
                    "Delete"
                )
            ) { _, which ->

                if (which == 0) {

                    showRenameSubfolderDialog(subfolder)

                } else {

                    confirmDeleteSubfolder(subfolder)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showRenameSubfolderDialog(
        subfolder: DocumentFolderModel
    ) {

        FolderOps.showNameDialog(
            context =
                this,

            title =
                "Rename folder",

            initialValue =
                subfolder.folderName,

            positiveLabel =
                "Save"
        ) { newName ->

            if (newName == subfolder.folderName) {

                return@showNameDialog
            }


            val duplicate =
                subfolders.any {
                    it.folderId != subfolder.folderId &&
                            it.folderName.equals(
                                newName,
                                ignoreCase = true
                            )
                }


            if (duplicate) {

                Toast.makeText(
                    this,
                    "A folder named \"$newName\" already exists here.",
                    Toast.LENGTH_LONG
                ).show()

                return@showNameDialog
            }


            FolderOps.renameFolder(
                db =
                    db,

                folderId =
                    subfolder.folderId,

                newName =
                    newName,

                onSuccess = {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        loadSubfolders()
                    }
                },

                onError = { message ->

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        Toast.makeText(
                            this,
                            "Failed to rename: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }


    private fun confirmDeleteSubfolder(
        subfolder: DocumentFolderModel
    ) {

        AlertDialog.Builder(this)
            .setTitle("Delete folder?")
            .setMessage(
                "\"${subfolder.folderName}\" and everything inside it " +
                        "(folders and files) will be deleted."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->

                FolderOps.deleteFolderTree(
                    db =
                        db,

                    organizationId =
                        organizationId,

                    folderId =
                        subfolder.folderId,

                    onSuccess = {

                        if (
                            !isFinishing &&
                            !isDestroyed
                        ) {

                            Toast.makeText(
                                this,
                                "Folder deleted.",
                                Toast.LENGTH_SHORT
                            ).show()

                            loadSubfolders()
                        }
                    },

                    onError = { message ->

                        if (
                            !isFinishing &&
                            !isDestroyed
                        ) {

                            Toast.makeText(
                                this,
                                "Failed to delete: $message",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            .show()
    }


    // =========================================================
    // DELETE FILE (long-press)
    // =========================================================

    private fun confirmDeleteFile(
        document: RequirementModel
    ) {

        if (
            document.docId.isBlank()
        ) {

            Toast.makeText(
                this,
                "This file cannot be deleted here.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        AlertDialog.Builder(this)
            .setTitle("Delete file?")
            .setMessage(
                "\"${document.fileName}\" will be removed from this folder."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->

                db.collection(
                    "organizationDocuments"
                )
                    .document(
                        document.docId
                    )
                    .delete()
                    .addOnSuccessListener {

                        documents.remove(
                            document
                        )

                        requirementAdapter
                            .notifyDataSetChanged()

                        Toast.makeText(
                            this,
                            "File deleted.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Failed to delete file: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }


    // =========================================================
    // LOAD SUBFOLDERS
    // =========================================================

    private fun loadSubfolders() {

        if (
            organizationId.isBlank()
        ) {

            subfoldersLoaded =
                true

            updateEmptyState()

            return
        }


        db.collection(
            "organizationFolders"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { folderResult ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }


                db.collection(
                    "organizationDocuments"
                )
                    .whereEqualTo(
                        "organizationId",
                        organizationId
                    )
                    .get()
                    .addOnSuccessListener { documentResult ->

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {

                            return@addOnSuccessListener
                        }


                        applySubfolders(
                            folderResult.documents,
                            documentResult.documents
                        )
                    }
                    .addOnFailureListener {

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {

                            return@addOnFailureListener
                        }


                        applySubfolders(
                            folderResult.documents,
                            emptyList()
                        )
                    }
            }
            .addOnFailureListener { error ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnFailureListener
                }


                subfoldersLoaded =
                    true

                updateEmptyState()

                Toast.makeText(
                    this,
                    "Failed to load folders: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    private fun applySubfolders(
        folderDocs: List<DocumentSnapshot>,
        fileDocs: List<DocumentSnapshot>
    ) {

        val parentKey =
            folderParentKey


        val children =
            folderDocs
                .filter {
                    (it.getString("parentId") ?: "") == parentKey
                }
                .mapNotNull { document ->

                    val name =
                        document.getString("name")
                            ?.trim()
                            .orEmpty()

                    if (name.isBlank()) {

                        null

                    } else {

                        DocumentFolderModel(

                            folderName =
                                name,

                            fileCount =
                                FolderOps.countItems(
                                    folderDocs,
                                    fileDocs,
                                    document.id
                                ),

                            folderId =
                                document.id
                        )
                    }
                }
                .sortedBy {
                    it.folderName.lowercase()
                }


        subfolders.clear()

        subfolders.addAll(children)

        subfolderAdapter
            .notifyDataSetChanged()


        subfoldersLoaded =
            true

        updateEmptyState()
    }


    // =========================================================
    // OPEN DOCUMENT PICKER
    // =========================================================

    private fun openDocumentPicker() {

        if (
            organizationId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Organization information is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        val mimeTypes =
            arrayOf(

                "application/pdf",

                "application/msword",

                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",

                "application/vnd.ms-excel",

                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",

                "application/vnd.ms-powerpoint",

                "application/vnd.openxmlformats-officedocument.presentationml.presentation",

                "image/*",

                "text/plain"
            )


        pickDocumentLauncher.launch(
            mimeTypes
        )
    }


    // =========================================================
    // LOAD ORGANIZATION INFORMATION
    // =========================================================

    private fun loadOrganizationInformation() {

        if (
            organizationId.isBlank()
        ) {

            loadDocuments()

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

                if (
                    document.exists()
                ) {

                    organizationName =
                        document.getString(
                            "orgName"
                        ) ?: organizationName


                    applicationId =
                        document.getString(
                            "applicationId"
                        ) ?: ""
                }


                loadDocuments()
            }
            .addOnFailureListener {

                loadDocuments()
            }
    }


    // =========================================================
    // LOAD ALL DOCUMENTS
    // =========================================================
    //
    // IMPORTANT:
    //
    // Organization Requirements:
    //     adminApplications.documents
    //
    // Documents 2026-2027:
    //     renewalRequirements
    //
    // Folder nga gihimo sa admin:
    //     organizationDocuments (folderId)
    //
    // Sila separated.
    //

    private fun loadDocuments() {

        documents.clear()

        filesLoaded =
            false


        // Mga folder sa sulod
        loadSubfolders()


        // =====================================================
        // FOLDER NGA GIHIMO SA ADMIN
        // =====================================================

        if (
            folderId.isNotBlank()
        ) {

            loadCreatedFolderFiles()

            return
        }


        // =====================================================
        // ORIGINAL ORGANIZATION REQUIREMENTS
        // =====================================================

        if (
            folderName ==
            "Organization Requirements"
        ) {

            loadOriginalFolderDocuments()

            return
        }


        // =====================================================
        // RENEWAL DOCUMENTS
        // =====================================================

        if (
            folderName.startsWith(
                "Documents "
            )
        ) {

            val academicYear =
                folderName
                    .removePrefix(
                        "Documents "
                    )
                    .trim()


            loadRenewalDocuments(
                academicYear =
                    academicYear
            )

            return
        }


        // =====================================================
        // OTHER ADMIN FOLDER (legacy)
        // =====================================================

        loadUploadedDocuments()
    }


    // =========================================================
    // FILES SA FOLDER NGA GIHIMO SA ADMIN
    // =========================================================

    private fun loadCreatedFolderFiles() {

        db.collection(
            "organizationDocuments"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "folderId",
                folderId
            )
            .get()
            .addOnSuccessListener { result ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }


                addUploadedDocuments(
                    result.documents
                )


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
            .addOnFailureListener { error ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnFailureListener
                }


                Toast.makeText(
                    this,
                    "Failed to load files: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
    }


    // =========================================================
    // ADD organizationDocuments -> documents (pinakabag-o una)
    // =========================================================

    private fun addUploadedDocuments(
        result: List<DocumentSnapshot>
    ) {

        val sorted =
            result.sortedByDescending {

                it.getTimestamp(
                    "createdAt"
                )?.toDate()?.time ?: 0L
            }


        for (
        document in sorted
        ) {

            val fileName =
                document.getString(
                    "fileName"
                ) ?: "Document"


            val fileUrl =
                document.getString(
                    "fileUrl"
                ) ?: ""


            val createdAt =
                document.getTimestamp(
                    "createdAt"
                )


            val status =
                document.getString(
                    "status"
                ) ?: "approved"


            val fileSize =
                document.getString(
                    "fileSize"
                ) ?: "Unknown size"


            documents.add(

                RequirementModel(

                    fileName =
                        fileName,

                    uploadDate =
                        formatDate(
                            createdAt
                        ),

                    fileSize =
                        fileSize,

                    status =
                        status,

                    fileUrl =
                        fileUrl,

                    docId =
                        document.id
                )
            )
        }
    }


    // =========================================================
    // LOAD ORIGINAL FOLDER
    // =========================================================

    private fun loadOriginalFolderDocuments() {

        if (
            applicationId.isBlank()
        ) {

            loadUploadedDocuments()

            return
        }


        db.collection(
            "adminApplications"
        )
            .document(
                applicationId
            )
            .get()
            .addOnSuccessListener { application ->

                if (
                    application.exists()
                ) {

                    loadOriginalApplicationDocuments(
                        application
                    )
                }


                loadUploadedDocuments()
            }
            .addOnFailureListener {

                loadUploadedDocuments()
            }
    }


    // =========================================================
    // ORIGINAL APPLICATION DOCUMENTS
    // =========================================================

    private fun loadOriginalApplicationDocuments(
        application: DocumentSnapshot
    ) {

        val applicationDocuments =
            application.get(
                "documents"
            ) as? List<*>


        if (
            applicationDocuments != null
        ) {

            for (
            item in applicationDocuments
            ) {

                val map =
                    item as? Map<*, *>
                        ?: continue


                val label =
                    map["label"]
                        ?.toString()
                        ?: "Document"


                val fileName =
                    map["fileName"]
                        ?.toString()
                        ?: ""


                val fileUrl =
                    map["url"]
                        ?.toString()
                        ?: ""


                val displayName =
                    if (
                        fileName.isNotBlank()
                    ) {

                        "$label\n$fileName"

                    } else {

                        label
                    }


                documents.add(

                    RequirementModel(

                        fileName =
                            displayName,

                        uploadDate =
                            formatDate(
                                application.getTimestamp(
                                    "createdAt"
                                )
                            ),

                        fileSize =
                            "Organization requirement",

                        status =
                            "approved",

                        fileUrl =
                            fileUrl
                    )
                )
            }
        }


        // =====================================================
        // OLD DOCUMENT STRUCTURE
        // =====================================================

        if (
            applicationDocuments == null ||
            applicationDocuments.isEmpty()
        ) {

            val oldUrl =
                application.getString(
                    "documentUrl"
                ) ?: ""


            if (
                oldUrl.isNotBlank()
            ) {

                documents.add(

                    RequirementModel(

                        fileName =
                            "Submitted Document",

                        uploadDate =
                            formatDate(
                                application.getTimestamp(
                                    "createdAt"
                                )
                            ),

                        fileSize =
                            "Organization requirement",

                        status =
                            "approved",

                        fileUrl =
                            oldUrl
                    )
                )
            }
        }
    }


    // =========================================================
    // LOAD RENEWAL DOCUMENTS
    // =========================================================
    //
    // SOURCE:
    //
    // renewalRequirements
    //
    // The query is filtered by organizationId.
    //
    // academicYear is checked locally so that:
    //
    // Documents 2026-2027
    //
    // only receives:
    //
    // academicYear = 2026-2027
    //

    private fun loadRenewalDocuments(
        academicYear: String
    ) {

        if (
            academicYear.isBlank()
        ) {

            Toast.makeText(
                this,
                "Academic year is missing.",
                Toast.LENGTH_LONG
            ).show()

            filesLoaded =
                true

            updateEmptyState()

            return
        }


        db.collection(
            "renewalRequirements"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { result ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }


                // =================================================
                // READ RENEWAL FILES
                // =================================================

                for (
                document in result.documents
                ) {

                    val savedAcademicYear =
                        document.getString(
                            "academicYear"
                        ) ?: ""


                    // ONLY CURRENT RENEWAL YEAR
                    if (
                        savedAcademicYear !=
                        academicYear
                    ) {

                        continue
                    }


                    val requirementName =
                        document.getString(
                            "requirementName"
                        ) ?: "Renewal Requirement"


                    val requirementNumber =
                        document.getString(
                            "requirementNumber"
                        ) ?: ""


                    val fileUrl =
                        document.getString(
                            "fileUrl"
                        ) ?: ""


                    val status =
                        document.getString(
                            "status"
                        ) ?: "approved"


                    val uploadedAt =
                        document.getTimestamp(
                            "uploadedAt"
                        )


                    // =================================================
                    // DISPLAY NAME
                    // =================================================

                    val displayName =
                        if (
                            requirementNumber.isNotBlank()
                        ) {

                            "$requirementNumber. $requirementName"

                        } else {

                            requirementName
                        }


                    documents.add(

                        RequirementModel(

                            fileName =
                                displayName,

                            uploadDate =
                                formatDate(
                                    uploadedAt
                                ),

                            fileSize =
                                "Renewal requirement",

                            status =
                                status,

                            fileUrl =
                                fileUrl
                        )
                    )
                }


                // =================================================
                // LOAD ADDITIONAL ADMIN FILES
                // =================================================

                loadFolderUploadedDocumentsOnly()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load renewal files: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()


                loadFolderUploadedDocumentsOnly()
            }
    }


    // =========================================================
    // LOAD ADMIN FILES FOR CURRENT FOLDER ONLY
    // =========================================================

    private fun loadFolderUploadedDocumentsOnly() {

        db.collection(
            "organizationDocuments"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "folderName",
                folderName
            )
            .get()
            .addOnSuccessListener { result ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }


                addUploadedDocuments(
                    result.documents
                )


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load folder files: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
    }


    // =========================================================
    // LOAD UPLOADED DOCUMENTS
    // =========================================================
    //
    // Used for:
    //
    // - Legacy admin-created folders
    // - Organization Requirements additional files
    //
    // IMPORTANT:
    //
    // Always filter by folderName.
    //

    private fun loadUploadedDocuments() {

        db.collection(
            "organizationDocuments"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "folderName",
                folderName
            )
            .get()
            .addOnSuccessListener { result ->

                if (
                    isFinishing ||
                    isDestroyed
                ) {

                    return@addOnSuccessListener
                }


                addUploadedDocuments(
                    result.documents
                )


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load documents: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()


                requirementAdapter
                    .notifyDataSetChanged()


                filesLoaded =
                    true

                updateEmptyState()
            }
    }


    // =========================================================
    // HANDLE PICKED DOCUMENT
    // =========================================================

    private fun handlePickedDocument(
        uri: Uri
    ) {

        val currentUser =
            auth.currentUser


        if (
            currentUser == null
        ) {

            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        val fileInfo =
            getFileInfo(
                uri
            )


        val fileName =
            fileInfo.first


        val sizeBytes =
            fileInfo.second


        if (
            fileName.isBlank()
        ) {

            Toast.makeText(
                this,
                "Unable to read the selected document.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        Toast.makeText(
            this,
            "Uploading $fileName...",
            Toast.LENGTH_SHORT
        ).show()


        // =====================================================
        // CLOUDINARY
        // =====================================================

        MediaManager
            .get()
            .upload(uri)
            .unsigned(
                "kayara_upload"
            )
            .option(
                "resource_type",
                "raw"
            )
            .option(
                "folder",
                "kayara/organization_documents/$organizationId"
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

                        if (
                            resultData == null
                        ) {

                            Toast.makeText(
                                this@FolderDocumentsActivity,
                                "Upload failed.",
                                Toast.LENGTH_LONG
                            ).show()

                            return
                        }


                        val secureUrl =
                            resultData[
                                "secure_url"
                            ]?.toString()
                                ?: ""


                        val publicId =
                            resultData[
                                "public_id"
                            ]?.toString()
                                ?: ""


                        if (
                            secureUrl.isBlank()
                        ) {

                            Toast.makeText(
                                this@FolderDocumentsActivity,
                                "Upload finished but no file URL was returned.",
                                Toast.LENGTH_LONG
                            ).show()

                            return
                        }


                        saveDocumentToFirestore(

                            fileName =
                                fileName,

                            fileSize =
                                formatFileSize(
                                    sizeBytes
                                ),

                            fileUrl =
                                secureUrl,

                            publicId =
                                publicId,

                            uid =
                                currentUser.uid
                        )
                    }


                    override fun onError(
                        requestId: String?,
                        error: ErrorInfo?
                    ) {

                        Toast.makeText(
                            this@FolderDocumentsActivity,
                            "Upload failed: ${
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
    // SAVE ADMIN-UPLOADED DOCUMENT
    //
    // - Folder nga gihimo sa admin: folderId = ID niini, folderName = ""
    //   (aron dili mo-gawas ingon ug bulag nga folder sa main list)
    // - System / legacy folder: folderName = ngan sa folder (sama sa una)
    // =========================================================

    private fun saveDocumentToFirestore(
        fileName: String,
        fileSize: String,
        fileUrl: String,
        publicId: String,
        uid: String
    ) {

        val documentData =
            hashMapOf<String, Any>(

                "organizationId" to
                        organizationId,

                "organizationName" to
                        organizationName,

                "folderId" to
                        folderId,

                "folderName" to
                        (
                                if (folderId.isNotBlank()) {
                                    ""
                                } else {
                                    folderName
                                }
                                ),

                "fileName" to
                        fileName,

                "fileSize" to
                        fileSize,

                "fileUrl" to
                        fileUrl,

                "publicId" to
                        publicId,

                "uploadedBy" to
                        uid,

                "status" to
                        "approved",

                "createdAt" to
                        Timestamp.now()
            )


        db.collection(
            "organizationDocuments"
        )
            .add(
                documentData
            )
            .addOnSuccessListener { reference ->

                val newDocument =
                    RequirementModel(

                        fileName =
                            fileName,

                        uploadDate =
                            "Just now",

                        fileSize =
                            fileSize,

                        status =
                            "approved",

                        fileUrl =
                            fileUrl,

                        docId =
                            reference.id
                    )


                requirementAdapter
                    .addDocument(
                        newDocument
                    )


                Toast.makeText(
                    this,
                    "$fileName added successfully.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "File uploaded, but database save failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }


    // =========================================================
    // GET FILE INFORMATION
    // =========================================================

    private fun getFileInfo(
        uri: Uri
    ): Pair<String, Long> {

        var name =
            "Document"


        var size =
            0L


        val cursor =
            contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )


        cursor?.use {

            if (
                it.moveToFirst()
            ) {

                val nameIndex =
                    it.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )


                val sizeIndex =
                    it.getColumnIndex(
                        OpenableColumns.SIZE
                    )


                if (
                    nameIndex != -1
                ) {

                    name =
                        it.getString(
                            nameIndex
                        )
                }


                if (
                    sizeIndex != -1
                ) {

                    size =
                        it.getLong(
                            sizeIndex
                        )
                }
            }
        }


        return Pair(
            name,
            size
        )
    }


    // =========================================================
    // FORMAT FILE SIZE
    // =========================================================

    private fun formatFileSize(
        bytes: Long
    ): String {

        if (
            bytes <= 0
        ) {

            return "Unknown size"
        }


        val kb =
            bytes / 1024.0


        return if (
            kb < 1024
        ) {

            "%.0f KB".format(
                kb
            )

        } else {

            "%.1f MB".format(
                kb / 1024.0
            )
        }
    }


    // =========================================================
    // FORMAT DATE
    // =========================================================

    private fun formatDate(
        timestamp: Timestamp?
    ): String {

        if (
            timestamp == null
        ) {

            return "Unknown date"
        }


        val formatter =
            SimpleDateFormat(
                "MMM d, yyyy",
                Locale.getDefault()
            )


        return formatter.format(
            timestamp.toDate()
        )
    }
}
