package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

class RequirementsFragment : Fragment() {

    // =========================================================
    // FIRESTORE
    // =========================================================

    private val db =
        SupabaseDatabase.getInstance()


    // =========================================================
    // LISTENER
    // =========================================================

    private var organizationListener:
            ListenerRegistration? = null


    // =========================================================
    // ORGANIZATION
    // =========================================================

    private var organizationId: String = ""


    // =========================================================
    // VIEW ONLY
    // =========================================================

    private var viewOnly: Boolean = false

    // Delete permission can be enabled separately from viewOnly.
    // Admin: viewOnly = false, canDelete = true
    // Super Admin: viewOnly = true, canDelete = true
    // Student/viewer: viewOnly = true, canDelete = false
    private var canDelete: Boolean = false


    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var folderAdapter:
            DocumentFolderAdapter

    private lateinit var rvDocumentFolders:
            RecyclerView

    private lateinit var tvEmptyDocuments:
            TextView

    private lateinit var fabAddFolder:
            ImageView


    // =========================================================
    // FOLDERS
    // =========================================================

    private val folders =
        mutableListOf<DocumentFolderModel>()

    // Para dili mo-reload dayon human sa unang pag-abli
    private var skipNextResumeRefresh = false


    // =========================================================
    // ARGUMENTS
    // =========================================================

    companion object {

        private const val ARG_ORGANIZATION_ID =
            "organizationId"

        private const val ARG_VIEW_ONLY =
            "viewOnly"

        private const val ARG_CAN_DELETE =
            "canDelete"


        fun newInstance(
            organizationId: String,
            viewOnly: Boolean = false,
            canDelete: Boolean = !viewOnly
        ): RequirementsFragment {

            val fragment =
                RequirementsFragment()

            val bundle =
                Bundle()

            bundle.putString(
                ARG_ORGANIZATION_ID,
                organizationId
            )

            bundle.putBoolean(
                ARG_VIEW_ONLY,
                viewOnly
            )

            fragment.arguments =
                bundle

            return fragment
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

        organizationId =
            arguments?.getString(
                ARG_ORGANIZATION_ID
            ) ?: ""

        viewOnly =
            arguments?.getBoolean(
                ARG_VIEW_ONLY,
                false
            ) ?: false

        canDelete =
            arguments?.getBoolean(
                ARG_CAN_DELETE,
                !viewOnly
            ) ?: !viewOnly
    }


    // =========================================================
    // ON CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_requirements,
            container,
            false
        )
    }


    // =========================================================
    // ON VIEW CREATED
    // =========================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        // =====================================================
        // RECYCLER VIEW
        // =====================================================

        rvDocumentFolders =
            view.findViewById(
                R.id.rvDocumentFolders
            )

        rvDocumentFolders.layoutManager =
            LinearLayoutManager(
                requireContext()
            )


        // =====================================================
        // EMPTY STATE
        // =====================================================

        tvEmptyDocuments =
            view.findViewById(
                R.id.tvEmptyDocuments
            )


        // =====================================================
        // ADD FOLDER
        // =====================================================

        fabAddFolder =
            view.findViewById(
                R.id.fabAddFolder
            )


        if (viewOnly) {

            fabAddFolder.visibility =
                View.GONE

        } else {

            fabAddFolder.visibility =
                View.VISIBLE

            // + = himo ug bag-ong folder
            fabAddFolder.setOnClickListener {

                showCreateFolderDialog()
            }
        }


        // =====================================================
        // ADAPTER
        // =====================================================

        folderAdapter =
            DocumentFolderAdapter(
                folders = folders,

                onFolderClick = { folder ->

                    openFolder(
                        folder
                    )
                },

                // Long-press sa folder nga gihimo sa admin: Rename / Delete
                onFolderLongClick = { folder ->

                    if (
                        !viewOnly &&
                        folder.folderId.isNotBlank()
                    ) {

                        showFolderOptions(
                            folder
                        )
                    }
                }
            )


        rvDocumentFolders.adapter =
            folderAdapter


        // =====================================================
        // LOAD
        // =====================================================

        if (
            organizationId.isBlank()
        ) {

            showEmptyState(
                "Organization information is missing."
            )

        } else {

            listenToOrganization()
        }

        skipNextResumeRefresh =
            true
    }


    // =========================================================
    // ON RESUME
    // I-refresh ang listahan human mobalik gikan sa sulod sa folder
    // (aron update ang ihap sa items)
    // =========================================================

    override fun onResume() {

        super.onResume()

        if (skipNextResumeRefresh) {

            skipNextResumeRefresh =
                false

            return
        }

        if (
            organizationId.isNotBlank() &&
            view != null
        ) {

            listenToOrganization()
        }
    }


    // =========================================================
    // LISTEN TO ORGANIZATION
    // =========================================================

    private fun listenToOrganization() {

        organizationListener?.remove()


        organizationListener =
            db.collection(
                "organizations"
            )
                .document(
                    organizationId
                )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }


                    // =================================================
                    // ERROR
                    // =================================================

                    if (error != null) {

                        showEmptyState(
                            "Unable to load organization documents."
                        )

                        Toast.makeText(
                            requireContext(),
                            "Failed to load requirements.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }


                    // =================================================
                    // NOT FOUND
                    // =================================================

                    if (
                        snapshot == null ||
                        !snapshot.exists()
                    ) {

                        showEmptyState(
                            "Organization record not found."
                        )

                        return@addSnapshotListener
                    }


                    // =================================================
                    // APPLICATION ID
                    // =================================================

                    val applicationId =
                        snapshot.getString(
                            "applicationId"
                        ) ?: ""


                    // =================================================
                    // RENEWAL FOLDER
                    // =================================================

                    val renewalFolderName =
                        snapshot.getString(
                            "documentsFolderName"
                        ) ?: ""


                    // =================================================
                    // LOAD
                    // =================================================

                    if (
                        applicationId.isBlank()
                    ) {

                        loadRenewalFolder(
                            renewalFolderName =
                                renewalFolderName
                        )

                    } else {

                        loadApplicationDocuments(
                            applicationId =
                                applicationId,

                            renewalFolderName =
                                renewalFolderName
                        )
                    }
                }
    }


    // =========================================================
    // LOAD APPLICATION DOCUMENTS
    // =========================================================

    private fun loadApplicationDocuments(
        applicationId: String,
        renewalFolderName: String
    ) {

        db.collection(
            "adminApplications"
        )
            .document(
                applicationId
            )
            .get()
            .addOnSuccessListener { applicationSnapshot ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }


                var originalCount =
                    0


                // =================================================
                // ORIGINAL DOCUMENTS
                // =================================================

                if (
                    applicationSnapshot.exists()
                ) {

                    val status =
                        applicationSnapshot.getString(
                            "status"
                        ) ?: ""


                    if (
                        status == "approved" ||
                        status == "active"
                    ) {

                        val documents =
                            applicationSnapshot.get(
                                "documents"
                            ) as? List<*>


                        originalCount =
                            documents?.size ?: 0


                        // Old structure
                        if (
                            originalCount == 0
                        ) {

                            val oldUrl =
                                applicationSnapshot.getString(
                                    "documentUrl"
                                ) ?: ""


                            if (
                                oldUrl.isNotBlank()
                            ) {

                                originalCount =
                                    1
                            }
                        }
                    }
                }


                loadOrganizationDocuments(
                    originalCount =
                        originalCount,

                    renewalFolderName =
                        renewalFolderName
                )
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                loadOrganizationDocuments(
                    originalCount =
                        0,

                    renewalFolderName =
                        renewalFolderName
                )
            }
    }


    // =========================================================
    // LOAD ORGANIZATION DOCUMENTS
    // =========================================================

    private fun loadOrganizationDocuments(
        originalCount: Int,
        renewalFolderName: String
    ) {

        db.collection(
            "organizationDocuments"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { result ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }


                folders.clear()


                // =================================================
                // ORIGINAL 3 FILES
                // =================================================

                if (
                    originalCount > 0
                ) {

                    folders.add(

                        DocumentFolderModel(

                            folderName =
                                "Organization Requirements",

                            fileCount =
                                originalCount
                        )
                    )
                }


                // =================================================
                // RENEWAL FOLDER
                // =================================================

                if (
                    renewalFolderName.isNotBlank()
                ) {

                    loadRenewalFolderCount(
                        renewalFolderName =
                            renewalFolderName,

                        existingOrganizationDocuments =
                            result.documents
                    )

                } else {

                    addOtherOrganizationFolders(
                        result.documents
                    )

                    finishFolderDisplay()
                }
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                folders.clear()


                if (
                    originalCount > 0
                ) {

                    folders.add(

                        DocumentFolderModel(

                            folderName =
                                "Organization Requirements",

                            fileCount =
                                originalCount
                        )
                    )
                }


                if (
                    renewalFolderName.isNotBlank()
                ) {

                    loadRenewalFolderCount(
                        renewalFolderName =
                            renewalFolderName,

                        existingOrganizationDocuments =
                            emptyList()
                    )

                } else {

                    finishFolderDisplay()
                }
            }
    }


    // =========================================================
    // LOAD RENEWAL FOLDER COUNT
    // =========================================================
    //
    // IMPORTANT:
    //
    // Documents 2026-2027
    //
    // is counted from:
    //
    // renewalRequirements
    //
    // NOT from adminApplications.documents
    //

    private fun loadRenewalFolderCount(
        renewalFolderName: String,
        existingOrganizationDocuments:
        List<com.example.kayara.data.DocumentSnapshot>
    ) {

        val academicYear =
            renewalFolderName
                .removePrefix(
                    "Documents "
                )
                .trim()


        if (
            academicYear.isBlank()
        ) {

            addOtherOrganizationFolders(
                existingOrganizationDocuments
            )

            finishFolderDisplay()

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
            .addOnSuccessListener { renewalResult ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }


                // =================================================
                // COUNT RENEWAL FILES
                // =================================================

                val renewalCount =
                    renewalResult.documents.count { document ->

                        val savedYear =
                            document.getString(
                                "academicYear"
                            ) ?: ""


                        savedYear == academicYear
                    }


                // =================================================
                // COUNT ADMIN-UPLOADED FILES
                // =================================================

                val adminUploadedCount =
                    existingOrganizationDocuments.count { document ->

                        document.getString(
                            "folderName"
                        ) == renewalFolderName
                    }


                val totalRenewalCount =
                    renewalCount +
                            adminUploadedCount


                // =================================================
                // ADD RENEWAL FOLDER
                // =================================================

                folders.add(

                    DocumentFolderModel(

                        folderName =
                            renewalFolderName,

                        fileCount =
                            totalRenewalCount
                    )
                )


                // =================================================
                // OTHER FOLDERS
                // =================================================

                addOtherOrganizationFolders(
                    existingOrganizationDocuments
                )


                finishFolderDisplay()
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                // Still show the folder even if
                // renewal count cannot be loaded.

                folders.add(

                    DocumentFolderModel(

                        folderName =
                            renewalFolderName,

                        fileCount =
                            0
                    )
                )


                addOtherOrganizationFolders(
                    existingOrganizationDocuments
                )


                finishFolderDisplay()
            }
    }


    // =========================================================
    // LOAD RENEWAL FOLDER WHEN NO APPLICATION ID
    // =========================================================

    private fun loadRenewalFolder(
        renewalFolderName: String
    ) {

        folders.clear()


        // Wala'y system folder: ipakita gihapon ang mga folder
        // nga gihimo sa admin (kung naa).
        if (
            renewalFolderName.isBlank()
        ) {

            finishFolderDisplay()

            return
        }


        loadRenewalFolderCount(
            renewalFolderName =
                renewalFolderName,

            existingOrganizationDocuments =
                emptyList()
        )
    }


    // =========================================================
    // OTHER ORGANIZATION FOLDERS
    // (legacy: gikan sa folderName sa organizationDocuments)
    // =========================================================

    private fun addOtherOrganizationFolders(
        documents:
        List<com.example.kayara.data.DocumentSnapshot>
    ) {

        val existingFolderNames =
            folders.map {
                it.folderName
            }.toMutableSet()


        val otherFolderNames =
            documents
                .mapNotNull {

                    it.getString(
                        "folderName"
                    )
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()


        for (
        folderName in otherFolderNames
        ) {

            if (
                !existingFolderNames.contains(
                    folderName
                )
            ) {

                val count =
                    documents.count { document ->

                        document.getString(
                            "folderName"
                        ) == folderName
                    }


                folders.add(

                    DocumentFolderModel(

                        folderName =
                            folderName,

                        fileCount =
                            count
                    )
                )


                existingFolderNames.add(
                    folderName
                )
            }
        }
    }


    // =========================================================
    // FINISH DISPLAY
    //
    // Idugang una ang mga folder nga gihimo sa admin
    // (organizationFolders, parentId = ""), dayon ipakita.
    // =========================================================

    private fun finishFolderDisplay() {

        if (!isAdded) {
            return
        }


        if (
            organizationId.isBlank()
        ) {

            displayFolders()

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

                if (!isAdded) {
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

                        if (!isAdded) {
                            return@addOnSuccessListener
                        }


                        mergeCreatedFolders(
                            folderResult.documents,
                            documentResult.documents
                        )

                        displayFolders()
                    }
                    .addOnFailureListener {

                        if (!isAdded) {
                            return@addOnFailureListener
                        }


                        mergeCreatedFolders(
                            folderResult.documents,
                            emptyList()
                        )

                        displayFolders()
                    }
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                displayFolders()
            }
    }


    // =========================================================
    // MERGE FOLDERS NGA GIHIMO SA ADMIN
    // =========================================================

    private fun mergeCreatedFolders(
        folderDocs:
        List<com.example.kayara.data.DocumentSnapshot>,

        fileDocs:
        List<com.example.kayara.data.DocumentSnapshot>
    ) {

        // Tangtangon una ang karaan aron walay doble
        folders.removeAll {
            it.folderId.isNotBlank()
        }


        folderDocs
            .filter {
                (it.getString("parentId") ?: "") == ""
            }
            .forEach { document ->

                val name =
                    document.getString("name")
                        ?.trim()
                        .orEmpty()

                if (name.isNotBlank()) {

                    folders.add(

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
                    )
                }
            }
    }


    // =========================================================
    // DISPLAY FOLDERS
    // =========================================================

    private fun displayFolders() {

        if (!isAdded) {
            return
        }


        if (
            folders.isEmpty()
        ) {

            showEmptyState(

                if (viewOnly) {

                    "No organization requirements were submitted."

                } else {

                    "No folders yet.\nTap + to create a folder."
                }
            )

            return
        }


        folders.sortBy {

            it.folderName.lowercase()
        }


        folderAdapter
            .notifyDataSetChanged()


        rvDocumentFolders.visibility =
            View.VISIBLE


        tvEmptyDocuments.visibility =
            View.GONE
    }


    // =========================================================
    // CREATE FOLDER (+)
    // =========================================================

    private fun showCreateFolderDialog() {

        if (!isAdded) {
            return
        }


        if (
            organizationId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Organization information is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        FolderOps.showNameDialog(
            context =
                requireContext(),

            title =
                "New folder",

            initialValue =
                "",

            positiveLabel =
                "Create"
        ) { name ->

            if (
                folders.any {
                    it.folderName.equals(
                        name,
                        ignoreCase = true
                    )
                }
            ) {

                Toast.makeText(
                    requireContext(),
                    "A folder named \"$name\" already exists.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                val uid =
                    FirebaseAuth.getInstance()
                        .currentUser?.uid ?: ""


                FolderOps.createFolder(
                    db =
                        db,

                    organizationId =
                        organizationId,

                    name =
                        name,

                    parentId =
                        "",

                    uid =
                        uid,

                    onSuccess = {

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Folder created.",
                                Toast.LENGTH_SHORT
                            ).show()

                            finishFolderDisplay()
                        }
                    },

                    onError = { message ->

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Failed to create folder: $message",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }


    // =========================================================
    // FOLDER OPTIONS (long-press): RENAME / DELETE
    // =========================================================

    private fun showFolderOptions(
        folder: DocumentFolderModel
    ) {

        if (!isAdded) {
            return
        }


        AlertDialog.Builder(requireContext())
            .setTitle(folder.folderName)
            .setItems(
                arrayOf(
                    "Rename",
                    "Delete"
                )
            ) { _, which ->

                if (which == 0) {

                    showRenameFolderDialog(folder)

                } else {

                    confirmDeleteFolder(folder)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showRenameFolderDialog(
        folder: DocumentFolderModel
    ) {

        FolderOps.showNameDialog(
            context =
                requireContext(),

            title =
                "Rename folder",

            initialValue =
                folder.folderName,

            positiveLabel =
                "Save"
        ) { newName ->

            if (newName == folder.folderName) {

                return@showNameDialog
            }


            val duplicate =
                folders.any {
                    it.folderId != folder.folderId &&
                            it.folderName.equals(
                                newName,
                                ignoreCase = true
                            )
                }


            if (duplicate) {

                Toast.makeText(
                    requireContext(),
                    "A folder named \"$newName\" already exists.",
                    Toast.LENGTH_LONG
                ).show()

                return@showNameDialog
            }


            FolderOps.renameFolder(
                db =
                    db,

                folderId =
                    folder.folderId,

                newName =
                    newName,

                onSuccess = {

                    if (isAdded) {

                        finishFolderDisplay()
                    }
                },

                onError = { message ->

                    if (isAdded) {

                        Toast.makeText(
                            requireContext(),
                            "Failed to rename: $message",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }


    private fun confirmDeleteFolder(
        folder: DocumentFolderModel
    ) {

        if (!isAdded) {
            return
        }


        AlertDialog.Builder(requireContext())
            .setTitle("Delete folder?")
            .setMessage(
                "\"${folder.folderName}\" and everything inside it " +
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
                        folder.folderId,

                    onSuccess = {

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
                                "Folder deleted.",
                                Toast.LENGTH_SHORT
                            ).show()

                            finishFolderDisplay()
                        }
                    },

                    onError = { message ->

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),
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
    // OPEN FOLDER
    // =========================================================

    private fun openFolder(
        folder: DocumentFolderModel
    ) {

        val intent =
            Intent(
                requireContext(),
                FolderDocumentsActivity::class.java
            )


        intent.putExtra(
            "folderName",
            folder.folderName
        )


        // Naay value = folder nga gihimo sa admin
        intent.putExtra(
            "folderId",
            folder.folderId
        )


        intent.putExtra(
            "folderPath",
            "Documents › ${folder.folderName}"
        )


        intent.putExtra(
            "organizationId",
            organizationId
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
    // EMPTY STATE
    // =========================================================

    private fun showEmptyState(
        message: String
    ) {

        folders.clear()


        if (
            ::folderAdapter.isInitialized
        ) {

            folderAdapter
                .notifyDataSetChanged()
        }


        if (
            ::rvDocumentFolders.isInitialized
        ) {

            rvDocumentFolders.visibility =
                View.GONE
        }


        if (
            ::tvEmptyDocuments.isInitialized
        ) {

            tvEmptyDocuments.visibility =
                View.VISIBLE

            tvEmptyDocuments.text =
                message
        }
    }


    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroyView() {

        organizationListener?.remove()

        organizationListener =
            null

        super.onDestroyView()
    }
}
