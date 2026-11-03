package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

class OrganizationsFragment : Fragment() {

    private lateinit var rvOrganizations: RecyclerView
    private lateinit var tvEmptyState: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipNewRequests: TextView
    private lateinit var chipRenewalDue: TextView
    private lateinit var etSearchOrg: EditText

    private lateinit var organizationAdapter: OrganizationAdapter

    private val db =
        SupabaseDatabase.getInstance()

    // =========================================================
    // DATA LISTS
    // =========================================================

    private val organizations =
        mutableListOf<OrganizationModel>()

    private val renewalDueOrganizations =
        mutableListOf<OrganizationModel>()

    private val pendingApplications =
        mutableListOf<AdminApplicationModel>()

    /*
     * Organization IDs that already have
     * a pending renewal request.
     */
    private val pendingRenewalOrganizationIds =
        mutableSetOf<String>()

    private var currentFilter =
        "all"

    // =========================================================
    // FIRESTORE LISTENERS
    // =========================================================

    private var applicationsListener:
            ListenerRegistration? = null

    private var organizationsListener:
            ListenerRegistration? = null

    private var memberCountsListener:
            ListenerRegistration? = null

    private var renewalRequestsListener:
            ListenerRegistration? = null

    // =========================================================
    // CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_organizations,
            container,
            false
        )
    }

    // =========================================================
    // VIEW CREATED
    // =========================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        rvOrganizations =
            view.findViewById(
                R.id.rvOrganizations
            )

        tvEmptyState =
            view.findViewById(
                R.id.tvEmptyState
            )

        chipAll =
            view.findViewById(
                R.id.chipAll
            )

        chipNewRequests =
            view.findViewById(
                R.id.chipNewRequests
            )

        chipRenewalDue =
            view.findViewById(
                R.id.chipRenewalDue
            )

        view.findViewById<View>(R.id.btnManageOrganizationAccess).setOnClickListener {
            startActivity(Intent(requireContext(), ManageAdminsActivity::class.java))
        }

        etSearchOrg =
            view.findViewById(
                R.id.etSearchOrg
            )

        setupRecyclerView()

        setupFilters()

        setupSearch()

        listenForPendingRenewals()

        listenForOrganizations()

        listenForNewRequests()

        listenForOrganizationMemberCounts()
    }

    // =========================================================
    // RECYCLER VIEW
    // =========================================================

    private fun setupRecyclerView() {

        organizationAdapter =
            OrganizationAdapter(
                fullList = organizations,

                onOrgClick = { organization ->

                    handleOrganizationClick(
                        organization
                    )
                }
            )

        rvOrganizations.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        rvOrganizations.adapter =
            organizationAdapter
    }

    // =========================================================
    // FILTER BUTTONS
    // =========================================================

    private fun setupFilters() {

        chipAll.setOnClickListener {

            currentFilter =
                "all"

            updateChipAppearance()

            applyCurrentFilter()
        }

        chipNewRequests.setOnClickListener {

            currentFilter =
                "new_request"

            updateChipAppearance()

            applyCurrentFilter()
        }

        chipRenewalDue.setOnClickListener {

            currentFilter =
                "renewal_due"

            updateChipAppearance()

            applyCurrentFilter()
        }

        updateChipAppearance()
    }

    // =========================================================
    // CHIP APPEARANCE
    // =========================================================

    private fun updateChipAppearance() {

        if (
            currentFilter ==
            "all"
        ) {

            chipAll.setBackgroundResource(
                R.drawable.bg_chip_active
            )

            chipAll.setTextColor(
                resources.getColor(
                    R.color.white,
                    null
                )
            )

        } else {

            chipAll.setBackgroundResource(
                R.drawable.bg_chip
            )

            chipAll.setTextColor(
                resources.getColor(
                    R.color.text_gray,
                    null
                )
            )
        }

        if (
            currentFilter ==
            "new_request"
        ) {

            chipNewRequests.setBackgroundResource(
                R.drawable.bg_chip_active
            )

            chipNewRequests.setTextColor(
                resources.getColor(
                    R.color.white,
                    null
                )
            )

        } else {

            chipNewRequests.setBackgroundResource(
                R.drawable.bg_chip
            )

            chipNewRequests.setTextColor(
                resources.getColor(
                    R.color.text_gray,
                    null
                )
            )
        }

        if (
            currentFilter ==
            "renewal_due"
        ) {

            chipRenewalDue.setBackgroundResource(
                R.drawable.bg_chip_active
            )

            chipRenewalDue.setTextColor(
                resources.getColor(
                    R.color.white,
                    null
                )
            )

        } else {

            chipRenewalDue.setBackgroundResource(
                R.drawable.bg_chip
            )

            chipRenewalDue.setTextColor(
                resources.getColor(
                    R.color.text_gray,
                    null
                )
            )
        }
    }

    // =========================================================
    // SEARCH
    // =========================================================

    private fun setupSearch() {

        etSearchOrg.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    applyCurrentFilter()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )
    }

    // =========================================================
    // LISTEN FOR PENDING RENEWALS
    // =========================================================

    private fun listenForPendingRenewals() {

        renewalRequestsListener?.remove()

        renewalRequestsListener =
            db.collection(
                "organizationRenewals"
            )
                .whereEqualTo(
                    "status",
                    "pending"
                )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load renewal requests.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }

                    pendingRenewalOrganizationIds.clear()

                    snapshot?.documents?.forEach { document ->

                        val organizationId =
                            document.getString(
                                "organizationId"
                            ) ?: ""

                        if (
                            organizationId.isNotBlank()
                        ) {

                            pendingRenewalOrganizationIds.add(
                                organizationId
                            )
                        }
                    }

                    refreshOrganizationCategories()
                }
    }

    // =========================================================
    // LOAD ORGANIZATIONS
    // =========================================================

    private fun listenForOrganizations() {

        organizationsListener?.remove()

        organizationsListener =
            db.collection(
                "organizations"
            )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load organizations: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    organizations.clear()

                    renewalDueOrganizations.clear()

                    for (
                    document in snapshot.documents
                    ) {

                        try {

                            val orgName =
                                document.getString(
                                    "orgName"
                                ) ?: ""

                            if (
                                orgName.isBlank()
                            ) {
                                continue
                            }

                            val shortCode =
                                document.getString(
                                    "shortCode"
                                ) ?: ""

                            val adviserName =
                                document.getString("adviserName")
                                    ?: ""

                            val adviserName1 =
                                document.getString("adviserName1")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: adviserName

                            val adviserName2 =
                                document.getString("adviserName2")
                                    ?: ""

                            val originalCategory =
                                document.getString(
                                    "category"
                                ) ?: "normal"

                            val renewalStatus =
                                document.getString(
                                    "renewalStatus"
                                ) ?: ""

                            val colorStart =
                                document.getString(
                                    "colorStart"
                                ) ?: "#7C3AED"

                            val colorEnd =
                                document.getString(
                                    "colorEnd"
                                ) ?: "#7C3AED"

                            val recruitmentInfo =
                                document.getString(
                                    "recruitmentInfo"
                                ) ?: ""

                            val memberCount =
                                document.getLong(
                                    "memberCount"
                                )?.toInt()
                                    ?: 0

                            val avatarUrl =
                                document.getString(
                                    "avatarUrl"
                                ) ?: ""

                            /*
                             * REAL organization document ID
                             */
                            val organizationId =
                                document.id

                            // =====================================
                            // DETERMINE DISPLAY CATEGORY
                            // =====================================

                            val displayCategory =
                                when {

                                    renewalStatus.equals(
                                        "due",
                                        ignoreCase = true
                                    ) -> {

                                        if (
                                            pendingRenewalOrganizationIds.contains(
                                                organizationId
                                            )
                                        ) {

                                            "reviewing_renewal"

                                        } else {

                                            "renewal_due"
                                        }
                                    }

                                    renewalStatus.equals(
                                        "approved",
                                        ignoreCase = true
                                    ) -> {

                                        "normal"
                                    }

                                    else -> {

                                        originalCategory
                                    }
                                }

                            val organization =
                                OrganizationModel(
                                    orgName =
                                        orgName,

                                    shortCode =
                                        if (
                                            shortCode.isBlank()
                                        ) {

                                            getShortCode(
                                                orgName
                                            )

                                        } else {

                                            shortCode
                                        },

                                    memberCount =
                                        memberCount,

                                    adviserName =
                                        adviserName,

                                    adviserName1 =
                                        adviserName1,

                                    adviserName2 =
                                        adviserName2,

                                    category =
                                        displayCategory,

                                    colorStart =
                                        colorStart,

                                    colorEnd =
                                        colorEnd,

                                    recruitmentInfo =
                                        recruitmentInfo,

                                    organizationId =
                                        organizationId,

                                    avatarUrl =
                                        avatarUrl
                                )

                            // =====================================
                            // LIST PLACEMENT
                            // =====================================

                            if (
                                renewalStatus.equals(
                                    "due",
                                    ignoreCase = true
                                )
                            ) {

                                /*
                                 * Renewal Due and Reviewing Renewal
                                 * appear ONLY inside Renewal Due.
                                 */
                                renewalDueOrganizations.add(
                                    organization
                                )

                            } else {

                                /*
                                 * Normal and approved organizations
                                 * appear in ALL.
                                 */
                                organizations.add(
                                    organization
                                )
                            }

                        } catch (_: Exception) {

                            // Ignore invalid organization document
                        }
                    }

                    organizations.sortBy {

                        it.orgName.lowercase()
                    }

                    renewalDueOrganizations.sortBy {

                        it.orgName.lowercase()
                    }

                    updateAllCount()

                    updateRenewalCount()

                    applyCurrentFilter()
                }
    }

    // =========================================================
    // REFRESH RENEWAL CATEGORY
    // =========================================================

    private fun refreshOrganizationCategories() {

        if (
            renewalDueOrganizations.isEmpty()
        ) {
            return
        }

        val updatedList =
            renewalDueOrganizations.map { organization ->

                val newCategory =
                    if (
                        pendingRenewalOrganizationIds.contains(
                            organization.organizationId
                        )
                    ) {

                        "reviewing_renewal"

                    } else {

                        "renewal_due"
                    }

                organization.copy(
                    category =
                        newCategory
                )
            }

        renewalDueOrganizations.clear()

        renewalDueOrganizations.addAll(
            updatedList
        )

        updateRenewalCount()

        applyCurrentFilter()
    }

    // =========================================================
    // LOAD NEW REQUESTS
    // =========================================================

    private fun listenForNewRequests() {

        applicationsListener?.remove()

        applicationsListener =
            db.collection(
                "adminApplications"
            )
                .whereEqualTo(
                    "status",
                    "pending"
                )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load new requests: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addSnapshotListener
                    }

                    pendingApplications.clear()

                    snapshot
                        ?.documents
                        ?.forEach { document ->

                            try {

                                val application =
                                    document.toObject(
                                        AdminApplicationModel::class.java
                                    )

                                if (
                                    application != null
                                ) {

                                    val fixedApplication =
                                        application.copy(
                                            applicationId =
                                                if (
                                                    application
                                                        .applicationId
                                                        .isBlank()
                                                ) {

                                                    document.id

                                                } else {

                                                    application
                                                        .applicationId
                                                }
                                        )

                                    pendingApplications.add(
                                        fixedApplication
                                    )
                                }

                            } catch (_: Exception) {

                                // Ignore invalid application
                            }
                        }

                    pendingApplications.sortByDescending {

                        it.createdAt?.seconds
                            ?: 0L
                    }

                    updateNewRequestCount()

                    if (
                        currentFilter ==
                        "new_request"
                    ) {

                        showNewRequests()
                    }
                }
    }

    // =========================================================
    // MEMBER COUNTS
    // =========================================================

    private fun listenForOrganizationMemberCounts() {

        memberCountsListener?.remove()

        memberCountsListener =
            db.collection(
                "memberships"
            )
                .whereEqualTo(
                    "status",
                    "approved"
                )
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    val counts =
                        mutableMapOf<String, Int>()

                    for (
                    document in snapshot.documents
                    ) {

                        val organizationId =
                            document.getString(
                                "organizationId"
                            ) ?: ""

                        if (
                            organizationId.isBlank()
                        ) {
                            continue
                        }

                        counts[organizationId] =
                            (
                                    counts[organizationId]
                                        ?: 0
                                    ) + 1
                    }

                    val updatedOrganizations =
                        organizations.map { organization ->

                            organization.copy(
                                memberCount =
                                    counts[
                                        organization.organizationId
                                    ] ?: 0
                            )
                        }

                    organizations.clear()

                    organizations.addAll(
                        updatedOrganizations
                    )

                    val updatedRenewalOrganizations =
                        renewalDueOrganizations.map { organization ->

                            organization.copy(
                                memberCount =
                                    counts[
                                        organization.organizationId
                                    ] ?: 0
                            )
                        }

                    renewalDueOrganizations.clear()

                    renewalDueOrganizations.addAll(
                        updatedRenewalOrganizations
                    )

                    applyCurrentFilter()
                }
    }

    // =========================================================
    // COUNTS
    // =========================================================

    private fun updateAllCount() {

        chipAll.text =
            "All (${organizations.size})"
    }

    private fun updateNewRequestCount() {

        chipNewRequests.text =
            "New Requests (${pendingApplications.size})"
    }

    private fun updateRenewalCount() {

        chipRenewalDue.text =
            "Renewal Due (${renewalDueOrganizations.size})"
    }

    // =========================================================
    // APPLY FILTER
    // =========================================================

    private fun applyCurrentFilter() {

        when (
            currentFilter
        ) {

            "all" -> {

                showOrganizations()
            }

            "new_request" -> {

                showNewRequests()
            }

            "renewal_due" -> {

                showRenewalDue()
            }
        }
    }

    // =========================================================
    // SHOW ALL
    // =========================================================

    private fun showOrganizations() {

        val query =
            etSearchOrg.text
                .toString()
                .trim()

        val filteredList =
            if (
                query.isBlank()
            ) {

                organizations.toList()

            } else {

                organizations.filter {

                    it.orgName.contains(
                        query,
                        ignoreCase = true
                    ) ||

                            it.shortCode.contains(
                                query,
                                ignoreCase = true
                            )
                }
            }

        organizationAdapter.updateList(
            filteredList
        )

        updateEmptyState(
            filteredList.isEmpty(),
            "No organizations found"
        )
    }

    // =========================================================
    // SHOW NEW REQUESTS
    // =========================================================

    private fun showNewRequests() {

        val query =
            etSearchOrg.text
                .toString()
                .trim()

        val filteredApplications =
            if (
                query.isBlank()
            ) {

                pendingApplications

            } else {

                pendingApplications.filter {

                    it.organizationName.contains(
                        query,
                        ignoreCase = true
                    ) ||

                            it.fullName.contains(
                                query,
                                ignoreCase = true
                            ) ||

                            it.username.contains(
                                query,
                                ignoreCase = true
                            )
                }
            }

        val requestOrganizations =
            filteredApplications.map {

                OrganizationModel(
                    orgName =
                        it.organizationName.ifBlank {
                            "Unnamed Organization"
                        },

                    shortCode =
                        getShortCode(
                            it.organizationName
                        ),

                    memberCount =
                        0,

                    adviserName = "",
                    adviserName1 = "",
                    adviserName2 = "",

                    category =
                        "new_request",

                    colorStart =
                        "#7C3AED",

                    colorEnd =
                        "#7C3AED",

                    recruitmentInfo =
                        it.applicationId,

                    organizationId =
                        ""
                )
            }

        organizationAdapter.updateList(
            requestOrganizations
        )

        updateEmptyState(
            requestOrganizations.isEmpty(),
            "No new organization requests"
        )
    }

    // =========================================================
    // SHOW RENEWAL DUE
    // =========================================================

    private fun showRenewalDue() {

        val query =
            etSearchOrg.text
                .toString()
                .trim()

        val filteredList =
            if (
                query.isBlank()
            ) {

                renewalDueOrganizations.toList()

            } else {

                renewalDueOrganizations.filter {

                    it.orgName.contains(
                        query,
                        ignoreCase = true
                    ) ||

                            it.shortCode.contains(
                                query,
                                ignoreCase = true
                            )
                }
            }

        organizationAdapter.updateList(
            filteredList
        )

        updateEmptyState(
            filteredList.isEmpty(),
            "No organizations due for renewal"
        )
    }

    // =========================================================
    // ORGANIZATION CLICK
    // =========================================================

    private fun handleOrganizationClick(
        organization: OrganizationModel
    ) {

        // =====================================================
        // NEW REQUEST
        // =====================================================

        if (
            organization.category ==
            "new_request"
        ) {

            val applicationId =
                organization.recruitmentInfo

            if (
                applicationId.isBlank()
            ) {

                Toast.makeText(
                    requireContext(),
                    "Application ID not found.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    SuperAdminApplicationReviewFragment
                        .newInstance(
                            applicationId
                        )
                )
                .addToBackStack(null)
                .commit()

            return
        }

        // =====================================================
        // RENEWAL DUE
        // NOT YET SUBMITTED
        // =====================================================

        if (
            organization.category ==
            "renewal_due"
        ) {

            Toast.makeText(
                requireContext(),
                "Waiting for the organization admin to submit the renewal requirements.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        // =====================================================
        // REVIEWING RENEWAL
        // READY FOR SUPER ADMIN REVIEW
        // =====================================================

        if (
            organization.category ==
            "reviewing_renewal"
        ) {

            if (
                organization.organizationId.isBlank()
            ) {

                Toast.makeText(
                    requireContext(),
                    "Organization ID not found.",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            Toast.makeText(
                requireContext(),
                "Opening renewal review...",
                Toast.LENGTH_SHORT
            ).show()

            db.collection(
                "organizationRenewals"
            )
                .whereEqualTo(
                    "organizationId",
                    organization.organizationId
                )
                .get()
                .addOnSuccessListener { snapshot ->

                    if (!isAdded) {
                        return@addOnSuccessListener
                    }

                    val pendingRenewal =
                        snapshot.documents.firstOrNull { document ->

                            val status =
                                document.getString(
                                    "status"
                                )
                                    ?.trim()
                                    ?.lowercase()
                                    ?: ""

                            status ==
                                    "pending"
                        }

                    if (
                        pendingRenewal == null
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "Pending renewal request not found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    /*
                     * IMPORTANT:
                     *
                     * Use the renewalId field saved inside Firestore.
                     *
                     * If it does not exist, fallback to
                     * the Firestore document ID.
                     */
                    val renewalId =
                        pendingRenewal
                            .getString(
                                "renewalId"
                            )
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: pendingRenewal.id

                    if (
                        renewalId.isBlank()
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "Renewal ID not found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    try {

                        val intent =
                            Intent(
                                requireActivity(),
                                RenewalReviewActivity::class.java
                            )

                        /*
                         * Send both names.
                         *
                         * RenewalReviewActivity currently accepts
                         * renewalId OR RENEWAL_ID.
                         */
                        intent.putExtra(
                            "renewalId",
                            renewalId
                        )

                        intent.putExtra(
                            "RENEWAL_ID",
                            renewalId
                        )

                        startActivity(
                            intent
                        )

                    } catch (
                        e: Exception
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to open renewal review: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { error ->

                    if (!isAdded) {
                        return@addOnFailureListener
                    }

                    Toast.makeText(
                        requireContext(),
                        "Unable to load renewal request: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            return
        }

        // =====================================================
        // NORMAL ORGANIZATION
        // =====================================================

        if (
            organization.organizationId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Organization ID not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                OrganizationProfileFragment
                    .newInstance(
                        organizationId =
                            organization.organizationId,

                        organizationName =
                            organization.orgName
                    )
            )
            .addToBackStack(null)
            .commit()
    }

    // =========================================================
    // EMPTY STATE
    // =========================================================

    private fun updateEmptyState(
        isEmpty: Boolean,
        message: String
    ) {

        if (
            isEmpty
        ) {

            tvEmptyState.text =
                message

            tvEmptyState.visibility =
                View.VISIBLE

            rvOrganizations.visibility =
                View.GONE

        } else {

            tvEmptyState.visibility =
                View.GONE

            rvOrganizations.visibility =
                View.VISIBLE
        }
    }

    // =========================================================
    // GET SHORT CODE
    // =========================================================

    private fun getShortCode(
        organizationName: String
    ): String {

        val words =
            organizationName
                .trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (
            words.isEmpty()
        ) {

            return "ORG"
        }

        if (
            words.size == 1
        ) {

            return words[0]
                .take(3)
                .uppercase()
        }

        return words
            .take(3)
            .map {
                it.first()
            }
            .joinToString("")
            .uppercase()
    }

    // =========================================================
    // DESTROY VIEW
    // =========================================================

    override fun onDestroyView() {

        applicationsListener?.remove()
        applicationsListener = null

        organizationsListener?.remove()
        organizationsListener = null

        memberCountsListener?.remove()
        memberCountsListener = null

        renewalRequestsListener?.remove()
        renewalRequestsListener = null

        super.onDestroyView()
    }
}