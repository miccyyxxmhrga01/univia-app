package com.example.kayara

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

class DocumentsFragment : Fragment() {

    private lateinit var folderAdapter: FolderAdapter

    private lateinit var tvTotalFolders: TextView
    private lateinit var tvTotalPending: TextView
    private lateinit var tvEmptyDocuments: TextView
    private lateinit var btnAcademicYear: View

    private val db =
        SupabaseDatabase.getInstance()

    private val folders =
        mutableListOf<FolderModel>()

    private var organizationsListener:
            ListenerRegistration? = null


    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_documents,
            container,
            false
        )
    }


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        tvTotalFolders =
            view.findViewById(
                R.id.tvTotalFolders
            )

        tvTotalPending =
            view.findViewById(
                R.id.tvTotalPending
            )

        tvEmptyDocuments =
            view.findViewById(
                R.id.tvEmptyDocuments
            )

        btnAcademicYear =
            view.findViewById(
                R.id.btnAcademicYear
            )


        val rvFolders =
            view.findViewById<RecyclerView>(
                R.id.rvFolders
            )


        // =====================================================
        // FOLDER ADAPTER
        // =====================================================

        folderAdapter =
            FolderAdapter(
                folders
            ) { position ->

                if (
                    position >= 0 &&
                    position < folders.size
                ) {

                    val selectedFolder =
                        folders[position]

                    openOrganizationDocuments(
                        selectedFolder
                    )
                }
            }


        rvFolders.layoutManager =
            GridLayoutManager(
                requireContext(),
                2
            )

        rvFolders.adapter =
            folderAdapter


        // =====================================================
        // ACADEMIC YEAR
        // =====================================================

        btnAcademicYear.setOnClickListener {

            if (!isAdded) {
                return@setOnClickListener
            }


            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    AcademicYearFragment()
                )
                .addToBackStack(null)
                .commit()
        }


        // =====================================================
        // LOAD ORGANIZATIONS
        // =====================================================

        listenToOrganizations()
    }


    // =========================================================
    // SAFE TOAST
    // =========================================================

    private fun showToast(
        message: String
    ) {

        if (!isAdded) {
            return
        }


        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }


    // =========================================================
    // LISTEN TO ORGANIZATIONS
    // =========================================================

    private fun listenToOrganizations() {

        organizationsListener?.remove()


        organizationsListener =
            db.collection(
                "organizations"
            )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }


                    if (
                        error != null
                    ) {

                        showToast(
                            "Failed to load organizations."
                        )

                        return@addSnapshotListener
                    }


                    if (
                        snapshot == null
                    ) {

                        return@addSnapshotListener
                    }


                    folders.clear()


                    // =================================================
                    // READ ORGANIZATIONS
                    // =================================================

                    for (
                    document in snapshot.documents
                    ) {

                        val organizationId =
                            document.id


                        val organizationName =
                            document.getString(
                                "orgName"
                            )
                                ?: "Unnamed Organization"


                        val applicationId =
                            document.getString(
                                "applicationId"
                            )
                                ?: ""


                        val category =
                            document.getString(
                                "category"
                            )
                                ?: "normal"


                        val colorStart =
                            document.getString(
                                "colorStart"
                            )
                                ?: "#7C3AED"


                        // =================================================
                        // FOLDER STATUS
                        // =================================================

                        val folderStatus: String

                        val pendingCount: Int


                        when (category) {

                            "new_request" -> {

                                folderStatus =
                                    "pending"

                                pendingCount =
                                    1
                            }


                            "renewal_due" -> {

                                folderStatus =
                                    "overdue"

                                pendingCount =
                                    1
                            }


                            else -> {

                                folderStatus =
                                    "clear"

                                pendingCount =
                                    0
                            }
                        }


                        folders.add(

                            FolderModel(

                                orgName =
                                    organizationName,

                                fileCount =
                                    0,

                                status =
                                    folderStatus,

                                pendingCount =
                                    pendingCount,

                                folderColor =
                                    colorStart,

                                organizationId =
                                    organizationId,

                                applicationId =
                                    applicationId
                            )
                        )
                    }


                    // =================================================
                    // SORT
                    // =================================================

                    folders.sortBy {

                        it.orgName.lowercase()
                    }


                    // =================================================
                    // UPDATE UI
                    // =================================================

                    if (isAdded) {

                        folderAdapter
                            .notifyDataSetChanged()

                        updateFolderSummary()

                        loadDocumentCounts()
                    }
                }
    }


    // =========================================================
    // LOAD DOCUMENT COUNTS
    // =========================================================

    private fun loadDocumentCounts() {

        if (!isAdded) {
            return
        }


        if (
            folders.isEmpty()
        ) {

            updateFolderSummary()

            return
        }


        var completed =
            0


        val totalFolders =
            folders.size


        for (
        index in folders.indices
        ) {

            val folder =
                folders[index]


            loadCountForOrganization(
                folder =
                    folder,

                index =
                    index
            ) {

                if (!isAdded) {
                    return@loadCountForOrganization
                }


                completed++


                if (
                    completed >=
                    totalFolders
                ) {

                    folderAdapter
                        .notifyDataSetChanged()

                    updateFolderSummary()
                }
            }
        }
    }


    // =========================================================
    // LOAD COUNT FOR ORGANIZATION
    // =========================================================

    private fun loadCountForOrganization(
        folder: FolderModel,
        index: Int,
        onComplete: () -> Unit
    ) {

        var originalCount =
            0


        if (
            folder.applicationId.isBlank()
        ) {

            loadSharedDocumentCount(
                folder =
                    folder,

                index =
                    index,

                originalCount =
                    0,

                onComplete =
                    onComplete
            )

            return
        }


        db.collection(
            "adminApplications"
        )
            .document(
                folder.applicationId
            )
            .get()
            .addOnSuccessListener { document ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }


                if (
                    document.exists()
                ) {

                    val documents =
                        document.get(
                            "documents"
                        )


                    if (
                        documents is List<*>
                    ) {

                        originalCount =
                            documents.size

                    } else {

                        val oldDocumentUrl =
                            document.getString(
                                "documentUrl"
                            )


                        if (
                            !oldDocumentUrl
                                .isNullOrBlank()
                        ) {

                            originalCount =
                                1
                        }
                    }
                }


                loadSharedDocumentCount(
                    folder =
                        folder,

                    index =
                        index,

                    originalCount =
                        originalCount,

                    onComplete =
                        onComplete
                )
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                loadSharedDocumentCount(
                    folder =
                        folder,

                    index =
                        index,

                    originalCount =
                        0,

                    onComplete =
                        onComplete
                )
            }
    }


    // =========================================================
    // LOAD SHARED DOCUMENT COUNT
    // =========================================================

    private fun loadSharedDocumentCount(
        folder: FolderModel,
        index: Int,
        originalCount: Int,
        onComplete: () -> Unit
    ) {

        db.collection(
            "organizationDocuments"
        )
            .whereEqualTo(
                "organizationId",
                folder.organizationId
            )
            .get()
            .addOnSuccessListener { result ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }


                val sharedCount =
                    result.size()


                val totalCount =
                    originalCount +
                            sharedCount


                if (
                    index >= 0 &&
                    index < folders.size
                ) {

                    folders[index] =
                        folders[index].copy(

                            fileCount =
                                totalCount
                        )
                }


                onComplete()
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }


                if (
                    index >= 0 &&
                    index < folders.size
                ) {

                    folders[index] =
                        folders[index].copy(

                            fileCount =
                                originalCount
                        )
                }


                onComplete()
            }
    }


    // =========================================================
    // SUMMARY
    // =========================================================

    private fun updateFolderSummary() {

        if (!isAdded) {
            return
        }


        tvTotalFolders.text =
            folders.size.toString()


        val pendingTotal =
            folders.count {

                it.status == "pending" ||
                        it.status == "overdue"
            }


        tvTotalPending.text =
            pendingTotal.toString()


        if (
            folders.isEmpty()
        ) {

            tvEmptyDocuments.visibility =
                View.VISIBLE

        } else {

            tvEmptyDocuments.visibility =
                View.GONE
        }
    }


    // =========================================================
    // OPEN ORGANIZATION
    //
    // IMPORTANT:
    //
    // We DON'T open the files directly anymore.
    //
    // We open the SAME RequirementsFragment
    // that the Admin uses.
    // =========================================================

    private fun openOrganizationDocuments(
        folder: FolderModel
    ) {

        if (!isAdded) {
            return
        }


        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,

                RequirementsFragment.newInstance(
                    organizationId =
                        folder.organizationId,

                    viewOnly =
                        true,

                    canDelete =
                        true
                )
            )
            .addToBackStack(null)
            .commit()
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroyView() {

        organizationsListener?.remove()

        organizationsListener =
            null

        super.onDestroyView()
    }
}
