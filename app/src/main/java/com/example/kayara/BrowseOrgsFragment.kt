package com.example.kayara

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import android.app.Activity
import android.content.Intent


class BrowseOrgsFragment : Fragment() {

    private lateinit var orgAdapter: BrowseOrgAdapter

    private lateinit var chipAll: TextView
    private lateinit var chipJoined: TextView
    private lateinit var chipPending: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: SupabaseDatabase

    // Maximum number of organizations a student can join
    private val MAX_ORGANIZATIONS = 3

    // FIX: gikan sa Firestore (parehas sa Super Admin)
    private var baseOrganizations: List<OrganizationModel> = emptyList()

    // FIX: organizationId -> status ("approved" / "pending" / "rejected")
    private val membershipStatus = mutableMapOf<String, String>()

    private var organizationsListener: ListenerRegistration? = null
    private var membershipsListener: ListenerRegistration? = null

    // organizationId -> pila ka approved members (para sa FULL status)
    private val approvedCounts = mutableMapOf<String, Int>()
    private var approvedCountsListener: ListenerRegistration? = null

    // =========================================================
    // FILE PICKER (para sa documents nga gi-require sa organization)
    // =========================================================

    private var pendingPickCallback: ((Uri) -> Unit)? = null

    private val pickDocumentLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                pendingPickCallback?.invoke(uri)
            }

            pendingPickCallback = null
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(
            R.layout.fragment_browse_orgs,
            container,
            false
        )


        // OPEN AI ORGANIZATION MATCHER

        view.findViewById<View>(
            R.id.btnSmartMatch
        ).setOnClickListener {

            val intent = Intent(
                requireContext(),
                SmartMatchActivity::class.java
            )

            smartMatchLauncher.launch(intent)
        }


        auth = FirebaseAuth.getInstance()
        db = SupabaseDatabase.getInstance()

        val rvBrowseOrgs =
            view.findViewById<RecyclerView>(R.id.rvBrowseOrgs)

        val etSearch =
            view.findViewById<EditText>(R.id.etSearchOrgStudent)

        chipAll = view.findViewById(R.id.chipAllStudentOrgs)
        chipJoined = view.findViewById(R.id.chipJoined)
        chipPending = view.findViewById(R.id.chipPendingOrgs)
        setActiveChip(chipAll)

        rvBrowseOrgs.layoutManager =
            LinearLayoutManager(requireContext())

        orgAdapter = BrowseOrgAdapter(
            fullList = emptyList(),
            onJoinClick = { org ->
                showJoinDialog(org)
            },
            onOrgClick = { org ->
                openOrganizationProfile(org)
            }
        )

        rvBrowseOrgs.adapter = orgAdapter

        listenForOrganizations()
        listenForMemberships()
        listenForApprovedCounts()

        chipAll.setOnClickListener {
            setActiveChip(chipAll)
            orgAdapter.filter("all")
        }

        chipJoined.setOnClickListener {
            setActiveChip(chipJoined)
            orgAdapter.filter("joined")
        }

        chipPending.setOnClickListener {
            setActiveChip(chipPending)
            orgAdapter.filter("pending")
        }

        etSearch.addTextChangedListener(
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
                    orgAdapter.search(s.toString())
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )

        return view
    }


    // ==========================================
    // AI ORGANIZATION MATCHER
    // ==========================================

    private val smartMatchLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode == Activity.RESULT_OK &&
                isAdded
            ) {

                val selectedOrganizationId =
                    result.data?.getStringExtra(
                        SmartMatchActivity.RESULT_ORGANIZATION_ID
                    )

                val selectedOrganization =
                    baseOrganizations.firstOrNull {
                        it.organizationId == selectedOrganizationId
                    }

                if (selectedOrganization != null) {

                    openOrganizationProfile(
                        selectedOrganization
                    )

                } else {

                    Toast.makeText(
                        requireContext(),
                        "Organization not found. Please refresh.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


    private fun openOrganizationProfile(org: OrganizationModel) {

        val fragment = OrganizationProfileFragment().apply {
            arguments = Bundle().apply {
                putString("organizationId", org.organizationId)
                putString("organizationName", org.orgName)
                putString("shortCode", org.shortCode)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    // =========================================================
    // FIX: LISTEN TO ORGANIZATIONS (same source as Super Admin)
    // =========================================================

    private fun listenForOrganizations() {

        organizationsListener?.remove()

        organizationsListener =
            db.collection("organizations")
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

                    val loaded =
                        mutableListOf<OrganizationModel>()

                    for (document in snapshot.documents) {

                        val orgName =
                            document.getString("orgName")
                                ?.trim()
                                ?: ""

                        if (orgName.isBlank()) {
                            continue
                        }

                        val shortCode =
                            document.getString("shortCode")
                                ?.trim()
                                ?: ""

                        val avatarUrl =
                            document.getString(
                                "avatarUrl"
                            )
                                ?.trim()
                                ?: ""

                        loaded.add(
                            OrganizationModel(

                                orgName =
                                    orgName,

                                shortCode =
                                    if (shortCode.isBlank()) {
                                        getShortCode(orgName)
                                    } else {
                                        shortCode
                                    },

                                memberCount =
                                    document.getLong("memberCount")
                                        ?.toInt()
                                        ?: 0,

                                adviserName =
                                    document.getString("adviserName")
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "Organization Adviser",

                                category =
                                    "available",

                                colorStart =
                                    document.getString("colorStart")
                                        ?: "#7C3AED",

                                colorEnd =
                                    document.getString("colorEnd")
                                        ?: "#7C3AED",

                                recruitmentInfo =
                                    document.getString("recruitmentInfo")
                                        ?: "",

                                // OPEN / CLOSED + member limit (gi-set sa admin)
                                recruitmentOpen =
                                    document.getBoolean("recruitmentOpen")
                                        ?: true,

                                recruitmentLimit =
                                    (
                                            document.getLong(
                                                "recruitmentLimit"
                                            )
                                                ?: 0L
                                            ).toInt(),

                                organizationId =
                                    document.id,

                                avatarUrl =
                                    avatarUrl


                            )
                        )
                    }

                    baseOrganizations =
                        loaded.sortedBy {
                            it.orgName.lowercase()
                        }

                    refreshOrganizationList()
                }
    }

    // =========================================================
    // FIX: LISTEN TO STUDENT MEMBERSHIPS
    // =========================================================

    private fun listenForMemberships() {

        val currentUser = auth.currentUser ?: return

        membershipsListener?.remove()

        membershipsListener =
            db.collection("memberships")
                .whereEqualTo("studentId", currentUser.uid)
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load memberships.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }

                    membershipStatus.clear()

                    snapshot?.documents?.forEach { document ->

                        val orgId =
                            document.getString("organizationId")

                        val status =
                            document.getString("status")

                        if (
                            !orgId.isNullOrEmpty() &&
                            !status.isNullOrEmpty()
                        ) {
                            membershipStatus[orgId] = status
                        }
                    }

                    refreshOrganizationList()
                }
    }

    // =========================================================
    // APPROVED MEMBERS PER ORGANIZATION (para sa FULL status)
    // =========================================================

    private fun listenForApprovedCounts() {

        approvedCountsListener?.remove()

        approvedCountsListener =
            db.collection("memberships")
                .whereEqualTo("status", "approved")
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded || error != null || snapshot == null) {
                        return@addSnapshotListener
                    }

                    approvedCounts.clear()

                    snapshot.documents.forEach { document ->

                        val orgId =
                            document.getString("organizationId")

                        if (!orgId.isNullOrBlank()) {
                            approvedCounts[orgId] =
                                (approvedCounts[orgId] ?: 0) + 1
                        }
                    }

                    refreshOrganizationList()
                }
    }

    // =========================================================
    // FIX: COMBINE ORGANIZATIONS + MEMBERSHIP STATUS
    // =========================================================

    private fun refreshOrganizationList() {

        if (!isAdded) {
            return
        }

        val updatedOrganizations =
            baseOrganizations.map { org ->

                when (membershipStatus[org.organizationId]) {

                    "approved" ->
                        org.copy(category = "joined")

                    "pending" ->
                        org.copy(category = "pending")

                    else -> {

                        val approved =
                            approvedCounts[org.organizationId] ?: 0

                        val status =
                            when {
                                !org.recruitmentOpen ->
                                    "closed"

                                org.recruitmentLimit > 0 &&
                                        approved >= org.recruitmentLimit ->
                                    "full"

                                else ->
                                    "open"
                            }

                        org.copy(
                            category = "available",
                            recruitmentStatus = status
                        )
                    }
                }
            }

        orgAdapter.updateList(updatedOrganizations)

        updateChipCounts(updatedOrganizations)
    }

    // =========================================================
    // UPDATE CHIP COUNTS
    // =========================================================

    private fun updateChipCounts(
        list: List<OrganizationModel>
    ) {

        val joinedCount =
            list.count { it.category == "joined" }

        val pendingCount =
            list.count { it.category == "pending" }

        chipAll.text = "All (${list.size})"
        chipJoined.text = "Joined ($joinedCount)"
        chipPending.text = "Pending ($pendingCount)"
    }

    // =========================================================
    // CHIP DESIGN
    // =========================================================

    private fun setActiveChip(
        activeChip: TextView
    ) {

        val allChips =
            listOf(chipAll, chipJoined, chipPending)

        allChips.forEach { chip ->
            val selected = chip == activeChip
            chip.isSelected = selected
            chip.setBackgroundResource(if (selected) R.drawable.bg_chip_active else R.drawable.bg_chip)
            // Both chip surfaces are pale recesses; keep their labels in readable semantic ink.
            chip.setTextColor(resources.getColor(if (selected) R.color.brand_navy else R.color.text_secondary, null))
            chip.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(),
                if (selected) R.font.poppins_semibold else R.font.poppins_regular)
        }
    }

    // =========================================================
    // JOIN DIALOG
    // Ipakita ang recruitment info/files sa org ug ipa-upload
    // ang documents nga gi-require (tan-awa ang JoinOrgDialog).
    // =========================================================

    private fun showJoinDialog(
        org: OrganizationModel
    ) {

        JoinOrgDialog(
            fragment = this,
            org = org,

            requestFile = { onPicked ->

                pendingPickCallback = onPicked

                pickDocumentLauncher.launch(DOCUMENT_MIME_TYPES)
            },

            onSubmit = { message, documents, dialog, button ->

                submitJoinRequest(
                    org = org,
                    message = message,
                    dialog = dialog,
                    button = button,
                    documents = documents
                )
            }
        ).show()
    }

    // =========================================================
    // SAVE JOIN REQUEST TO FIRESTORE
    // =========================================================

    private fun submitJoinRequest(
        org: OrganizationModel,
        message: String,
        dialog: Dialog,
        button: TextView,
        documents: List<Map<String, Any>> = emptyList()
    ) {

        val currentUser = auth.currentUser

        if (currentUser == null) {

            Toast.makeText(
                requireContext(),
                "Please login again.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (org.organizationId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Organization ID not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val uid = currentUser.uid

        // FIX: tinuod nga organization ID (document.id)
        val realOrgId = org.organizationId

        button.isEnabled = false
        button.alpha = 0.6f

        db.collection("memberships")
            .whereEqualTo("studentId", uid)
            .get()
            .addOnSuccessListener { snapshot ->

                // approved AND pending ang giihap
                val activeCount =
                    snapshot.documents.count { document ->

                        val status = document.getString("status")

                        status == "approved" || status == "pending"
                    }

                if (activeCount >= MAX_ORGANIZATIONS) {

                    button.isEnabled = true
                    button.alpha = 1f

                    Toast.makeText(
                        requireContext(),
                        "You can only join or request a maximum of 3 organizations.",
                        Toast.LENGTH_LONG
                    ).show()

                    dialog.dismiss()

                    return@addOnSuccessListener
                }

                val membershipId = "${uid}_$realOrgId"

                db.collection("memberships")
                    .document(membershipId)
                    .get()
                    .addOnSuccessListener { existingDocument ->

                        if (existingDocument.exists()) {

                            val existingStatus =
                                existingDocument.getString("status")

                            if (existingStatus == "approved") {

                                button.isEnabled = true
                                button.alpha = 1f

                                Toast.makeText(
                                    requireContext(),
                                    "You are already a member of ${org.orgName}.",
                                    Toast.LENGTH_LONG
                                ).show()

                                dialog.dismiss()

                                return@addOnSuccessListener
                            }

                            if (existingStatus == "pending") {

                                button.isEnabled = true
                                button.alpha = 1f

                                Toast.makeText(
                                    requireContext(),
                                    "Your request to ${org.orgName} is still pending.",
                                    Toast.LENGTH_LONG
                                ).show()

                                dialog.dismiss()

                                return@addOnSuccessListener
                            }
                            // "rejected" = allowed to apply again
                        }

                        db.collection("users")
                            .document(uid)
                            .get()
                            .addOnSuccessListener { userDocument ->

                                val fullName =
                                    userDocument.getString("fullName")
                                        ?: currentUser.displayName
                                        ?: "A student"

                                val membershipData =
                                    hashMapOf<String, Any>(
                                        "studentId" to uid,
                                        "fullName" to fullName,
                                        "organizationId" to realOrgId,
                                        "organizationName" to org.orgName,
                                        "shortCode" to org.shortCode,
                                        "adviserName" to org.adviserName,
                                        "status" to "pending",
                                        "applicationNote" to message,
                                        "submittedDocuments" to documents,
                                        "dateApplied" to FieldValue.serverTimestamp()
                                    )

                                db.collection("memberships")
                                    .document(membershipId)
                                    .set(membershipData)
                                    .addOnSuccessListener {

                                        createAdminNotification(
                                            organizationId = realOrgId,

                                            studentName = fullName,

                                            organizationName = org.orgName,

                                            membershipId = membershipId,

                                            studentId = uid
                                        )

                                        Toast.makeText(
                                            requireContext(),
                                            "Request to join ${org.orgName} sent!",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        dialog.dismiss()
                                        // wala na kinahanglan i-reload:
                                        // real-time na ang membership listener
                                    }
                                    .addOnFailureListener {

                                        button.isEnabled = true
                                        button.alpha = 1f

                                        Toast.makeText(
                                            requireContext(),
                                            "Failed to send join request.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .addOnFailureListener {

                                button.isEnabled = true
                                button.alpha = 1f

                                Toast.makeText(
                                    requireContext(),
                                    "Unable to load your profile.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener {

                        button.isEnabled = true
                        button.alpha = 1f

                        Toast.makeText(
                            requireContext(),
                            "Unable to check existing membership.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener {

                button.isEnabled = true
                button.alpha = 1f

                Toast.makeText(
                    requireContext(),
                    "Unable to check your organization limit.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // CREATE ADMIN NOTIFICATION
    // =========================================================


    private fun createAdminNotification(
        organizationId: String,
        studentName: String,
        organizationName: String,
        membershipId: String = "",
        studentId: String = ""
    ) {

        if (organizationId.isBlank()) {
            return
        }

        AdminNotificationHelper.create(
            organizationId = organizationId,

            organizationName = organizationName,

            title = "New Membership Application",

            message = "$studentName applied to join $organizationName.",

            type = "membership_application",

            referenceId = membershipId,

            studentId = studentId
        )
    }

    // =========================================================
    // SHORT CODE (parehas sa Super Admin)
    // =========================================================

    private fun getShortCode(
        organizationName: String
    ): String {

        val words =
            organizationName
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return "ORG"
        }

        if (words.size == 1) {
            return words[0].take(3).uppercase()
        }

        return words
            .take(3)
            .map { it.first() }
            .joinToString("")
            .uppercase()
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroyView() {

        organizationsListener?.remove()
        organizationsListener = null

        membershipsListener?.remove()
        membershipsListener = null

        approvedCountsListener?.remove()
        approvedCountsListener = null

        super.onDestroyView()
    }
}
