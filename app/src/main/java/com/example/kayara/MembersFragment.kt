package com.example.kayara

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

class MembersFragment : Fragment() {

    private lateinit var memberAdapter: MemberAdapter

    private lateinit var chipAll: TextView
    private lateinit var chipOfficers: TextView
    private lateinit var chipPending: TextView

    private val db =
        SupabaseDatabase.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    private val members =
        mutableListOf<MemberModel>()

    private var currentFilter =
        "all"

    private val officerPositions =
        listOf(
            "President",
            "Vice President",
            "Secretary",
            "Treasurer",
            "Auditor",
            "Public Information Officer"
        )

    // =========================================================
    // ORGANIZATION
    // =========================================================

    private var organizationId: String =
        ""

    // =========================================================
    // NEW INSTANCE
    // =========================================================

    companion object {

        private const val ARG_ORGANIZATION_ID =
            "organizationId"

        private const val ARG_FILTER =
            "initialFilter"

        fun newInstance(
            organizationId: String,
            initialFilter: String = "all"
        ): MembersFragment {

            val fragment =
                MembersFragment()

            val bundle =
                Bundle()

            bundle.putString(
                ARG_ORGANIZATION_ID,
                organizationId
            )

            bundle.putString(
                ARG_FILTER,
                initialFilter
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

        currentFilter =
            arguments?.getString(
                ARG_FILTER
            ) ?: "all"
    }

    // =========================================================
// OPEN REAL STUDENT PROFILE
// =========================================================

    private fun openStudentProfile(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        if (
            member.studentId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Student profile ID is missing.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val profileFragment =
            ProfileFragment.newInstance(
                studentUid =
                    member.studentId,

                viewOnly =
                    true
            )

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.fragmentContainer,
                profileFragment
            )
            .addToBackStack(
                "student_profile"
            )
            .commit()
    }

    // =========================================================
    // CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(
                R.layout.fragment_members,
                container,
                false
            )

        val rvMembers =
            view.findViewById<RecyclerView>(
                R.id.rvMembers
            )

        val etSearchMember =
            view.findViewById<EditText>(
                R.id.etSearchMember
            )

        chipAll =
            view.findViewById(
                R.id.chipAllMembers
            )

        chipOfficers =
            view.findViewById(
                R.id.chipOfficers
            )

        chipPending =
            view.findViewById(
                R.id.chipPendingMembers
            )

        // =====================================================
        // RECYCLER VIEW
        // =====================================================

        rvMembers.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        memberAdapter =
            MemberAdapter(
                fullList = members,

                onApprove = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        approveMember(
                            member
                        )
                    }
                },

                onReject = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        rejectMember(
                            member
                        )
                    }
                },

                onAssignOfficer = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        showAssignPositionDialog(
                            member
                        )
                    }
                },

                onViewDocuments = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        showDocumentsDialog(
                            member
                        )
                    }
                },

                onItemClick = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        if (
                            member.role == "pending"
                        ) {

                            // Pending applicant:
                            // keep existing application-details screen.
                            showApplicantDetails(
                                member
                            )

                        } else {

                            // Approved member/officer:
                            // open their REAL student profile.
                            openStudentProfile(
                                member
                            )
                        }
                    }
                },



                // =================================================
                // NEW: REMOVE ACTIVE MEMBER / OFFICER
                // =================================================

                onRemoveMember = { position ->

                    val member =
                        memberAdapter.getMemberAt(
                            position
                        )

                    if (member != null) {

                        showRemoveMemberConfirmation(
                            member
                        )
                    }
                }
            )

        rvMembers.adapter =
            memberAdapter

        // =====================================================
        // INITIAL FILTER
        // =====================================================

        setActiveChip(
            when (
                currentFilter
            ) {

                "officer" ->
                    chipOfficers

                "pending" ->
                    chipPending

                else ->
                    chipAll
            }
        )

        loadMembers()

        // =====================================================
        // ALL
        // =====================================================

        chipAll.setOnClickListener {

            currentFilter =
                "all"

            setActiveChip(
                chipAll
            )

            memberAdapter.filter(
                "all"
            )
        }

        // =====================================================
        // OFFICERS
        // =====================================================

        chipOfficers.setOnClickListener {

            currentFilter =
                "officer"

            setActiveChip(
                chipOfficers
            )

            memberAdapter.filter(
                "officer"
            )
        }

        // =====================================================
        // PENDING
        // =====================================================

        chipPending.setOnClickListener {

            currentFilter =
                "pending"

            setActiveChip(
                chipPending
            )

            memberAdapter.filter(
                "pending"
            )
        }

        // =====================================================
        // SEARCH
        // =====================================================

        etSearchMember
            .addTextChangedListener(
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

                        memberAdapter.search(
                            s.toString()
                        )
                    }

                    override fun afterTextChanged(
                        s: Editable?
                    ) {
                    }
                }
            )

        return view
    }

    // =========================================================
    // REFRESH
    // =========================================================

    private fun refreshList() {

        memberAdapter.updateList(
            members
        )

        memberAdapter.filter(
            currentFilter
        )

        updateChipCounts()
    }

    // =========================================================
    // LOAD MEMBERS
    // =========================================================

    private fun loadMembers() {

        if (
            organizationId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Organization assignment is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        db.collection(
            "memberships"
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

                members.clear()

                if (
                    result.documents.isEmpty()
                ) {

                    refreshList()

                    return@addOnSuccessListener
                }

                var completed =
                    0

                val total =
                    result.documents.size

                fun finishOne() {

                    completed++

                    if (
                        completed >= total &&
                        isAdded
                    ) {

                        refreshList()
                    }
                }

                for (
                document in result.documents
                ) {

                    val studentId =
                        document.getString(
                            "studentId"
                        )

                    val status =
                        document.getString(
                            "status"
                        )
                            ?.trim()
                            ?.lowercase()
                            ?: "pending"

                    // =================================================
                    // IMPORTANT:
                    // REMOVED MEMBERS ARE NOT ACTIVE ANYMORE
                    // =================================================

                    if (
                        status == "removed" ||
                        status == "rejected"
                    ) {

                        finishOne()

                        continue
                    }

                    val organizationName =
                        document.getString(
                            "organizationName"
                        )
                            ?: "Organization"

                    val applicationNote =
                        document.getString(
                            "applicationNote"
                        )
                            ?: ""

                    val membershipRole =
                        document.getString(
                            "role"
                        )
                            ?: ""

                    val position =
                        document.getString(
                            "position"
                        )
                            ?: ""

                    val submittedDocs =
                        parseSubmittedDocuments(
                            document.get(
                                "submittedDocuments"
                            )
                        )

                    val membershipId =
                        document.id

                    if (
                        studentId.isNullOrBlank()
                    ) {

                        finishOne()

                        continue
                    }

                    db.collection(
                        "users"
                    )
                        .document(
                            studentId
                        )
                        .get()
                        .addOnSuccessListener { userDocument ->

                            if (!isAdded) {
                                return@addOnSuccessListener
                            }

                            val fullName =
                                userDocument.getString(
                                    "fullName"
                                )
                                    ?: "Unknown Student"

                            val course =
                                userDocument.getString(
                                    "course"
                                )
                                    ?: ""

                            val yearLevel =
                                userDocument.getString(
                                    "yearLevel"
                                )
                                    ?: ""

                            val profileImageUrl =
                                userDocument.getString(
                                    "profileImageUrl"
                                )
                                    ?: ""

                            // =========================================
                            // DETAILS
                            // =========================================

                            val details =
                                when (
                                    status
                                ) {

                                    "pending" -> {

                                        "$organizationName • Application Pending"
                                    }

                                    "approved" -> {

                                        if (
                                            course.isNotBlank() &&
                                            yearLevel.isNotBlank()
                                        ) {

                                            "$course-$yearLevel • $organizationName"

                                        } else {

                                            "$organizationName • Member"
                                        }
                                    }

                                    else -> {

                                        organizationName
                                    }
                                }

                            // =========================================
                            // ROLE
                            // =========================================

                            val role =
                                when (
                                    status
                                ) {

                                    "pending" -> {

                                        "pending"
                                    }

                                    "approved" -> {

                                        if (
                                            membershipRole.equals(
                                                "officer",
                                                ignoreCase = true
                                            )
                                        ) {

                                            "officer"

                                        } else {

                                            "member"
                                        }
                                    }

                                    else -> {

                                        "member"
                                    }
                                }

                            // =========================================
                            // ADD
                            // =========================================

                            members.add(
                                MemberModel(
                                    fullName =
                                        fullName,

                                    details =
                                        details,

                                    role =
                                        role,

                                    avatarColorStart =
                                        if (
                                            role == "pending"
                                        ) {

                                            "#A38ACB"

                                        } else {

                                            "#3B176A"
                                        },

                                    avatarColorEnd =
                                        if (
                                            role == "pending"
                                        ) {

                                            "#69508D"

                                        } else {

                                            "#3B176A"
                                        },

                                    applicationNote =
                                        applicationNote,

                                    membershipId =
                                        membershipId,

                                    organizationName =
                                        organizationName,

                                    position =
                                        if (
                                            role == "officer"
                                        ) {

                                            position

                                        } else {

                                            ""
                                        },

                                    avatarUrl =
                                        profileImageUrl,

                                    documents =
                                        submittedDocs,

                                    studentId =
                                        studentId
                                )
                            )

                            finishOne()
                        }
                        .addOnFailureListener {

                            finishOne()
                        }
                }
            }
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Failed to load members: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // DETAILS
    // =========================================================

    private fun showApplicantDetails(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        ApplicantDetailsDialog(
            fragment = this,
            member = member,
            onApprove = {
                approveMember(
                    member
                )
            },
            onReject = {
                rejectMember(
                    member
                )
            }
        ).show()
    }

    // =========================================================
    // DOCUMENTS
    // =========================================================

    private fun showDocumentsDialog(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        if (
            member.documents.isEmpty()
        ) {

            Toast.makeText(
                requireContext(),
                "No documents submitted.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val labels =
            member.documents
                .map { document ->

                    if (
                        document.fileName.isBlank()
                    ) {

                        document.title

                    } else {

                        "${document.title}\n${document.fileName}"
                    }
                }
                .toTypedArray()

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "${member.fullName}'s documents"
            )
            .setItems(
                labels
            ) { _, which ->

                openUrl(
                    requireContext(),
                    member.documents[
                        which
                    ].url
                )
            }
            .setNegativeButton(
                "Close",
                null
            )
            .show()
    }

    // =========================================================
    // REMOVE MEMBER CONFIRMATION
    // =========================================================

    private fun showRemoveMemberConfirmation(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        if (
            member.role == "pending"
        ) {

            Toast.makeText(
                requireContext(),
                "Pending applicants cannot be removed as active members.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Remove Member?"
            )
            .setMessage(
                "Are you sure you want to remove ${member.fullName} from this organization?\n\n" +
                        "The membership record will be kept for history, but the student will no longer appear as an active member."
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Remove"
            ) { _, _ ->

                removeMember(
                    member
                )
            }
            .show()
    }

    // =========================================================
    // REMOVE MEMBER
    // =========================================================

    private fun removeMember(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        if (
            member.membershipId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Membership record not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val membershipRef =
            db.collection(
                "memberships"
            )
                .document(
                    member.membershipId
                )

        membershipRef
            .get()
            .addOnSuccessListener { membershipDoc ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                if (
                    !membershipDoc.exists()
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Membership record not found.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                // =================================================
                // SECURITY CHECK:
                // SAME ORGANIZATION ONLY
                // =================================================

                val membershipOrganizationId =
                    membershipDoc.getString(
                        "organizationId"
                    )

                if (
                    membershipOrganizationId !=
                    organizationId
                ) {

                    Toast.makeText(
                        requireContext(),
                        "You can only remove members of your organization.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                // =================================================
                // ONLY ACTIVE APPROVED MEMBERS
                // =================================================

                val status =
                    membershipDoc.getString(
                        "status"
                    )
                        ?.trim()
                        ?.lowercase()
                        ?: ""

                if (
                    status != "approved"
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Only active members can be removed.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val studentId =
                    membershipDoc.getString(
                        "studentId"
                    )
                        ?: member.studentId

                val organizationName =
                    membershipDoc.getString(
                        "organizationName"
                    )
                        ?: member.organizationName

                val currentAdminUid =
                    auth.currentUser?.uid
                        ?: ""

                // =================================================
                // SOFT REMOVE
                // =================================================

                val updates =
                    hashMapOf<String, Any>(
                        "status" to
                                "removed",

                        "role" to
                                "member",

                        "removedAt" to
                                FieldValue.serverTimestamp(),

                        "updatedAt" to
                                FieldValue.serverTimestamp()
                    )

                if (
                    currentAdminUid.isNotBlank()
                ) {

                    updates[
                        "removedBy"
                    ] = currentAdminUid
                }

                membershipRef
                    .update(
                        updates
                    )
                    .addOnSuccessListener {

                        // =========================================
                        // NOTIFY STUDENT
                        // =========================================

                        if (
                            studentId.isNotBlank()
                        ) {

                            StudentNotificationHelper.create(
                                studentId = studentId,

                                title = "Organization Membership Update",

                                message =
                                    "You are no longer an active member of $organizationName.",

                                type = "membership_removed",

                                organizationId = organizationId,

                                organizationName = organizationName,

                                referenceId = member.membershipId
                            )
                        }

                        if (
                            isAdded
                        ) {

                            Toast.makeText(
                                requireContext(),
                                "${member.fullName} has been removed from the organization.",
                                Toast.LENGTH_LONG
                            ).show()

                            loadMembers()
                        }
                    }
                    .addOnFailureListener { error ->

                        if (!isAdded) {
                            return@addOnFailureListener
                        }

                        Toast.makeText(
                            requireContext(),
                            "Failed to remove member: ${error.message}",
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
                    "Failed to load membership: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // ASSIGN OFFICER POSITION
    // =========================================================

    private fun showAssignPositionDialog(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        if (
            member.role == "pending"
        ) {

            Toast.makeText(
                requireContext(),
                "Approve the member first before assigning a position.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val options =
            mutableListOf<String>()

        options.addAll(
            officerPositions
        )

        options.add(
            "Other position..."
        )

        if (
            member.role == "officer"
        ) {

            options.add(
                "Remove officer role"
            )
        }

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Assign position: ${member.fullName}"
            )
            .setItems(
                options.toTypedArray()
            ) { _, which ->

                when {

                    which <
                            officerPositions.size -> {

                        saveOfficerPosition(
                            member,
                            officerPositions[
                                which
                            ]
                        )
                    }

                    which ==
                            officerPositions.size -> {

                        showCustomPositionDialog(
                            member
                        )
                    }

                    else -> {

                        saveOfficerPosition(
                            member,
                            null
                        )
                    }
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // CUSTOM POSITION
    // =========================================================

    private fun showCustomPositionDialog(
        member: MemberModel
    ) {

        if (!isAdded) {
            return
        }

        val container =
            LinearLayout(
                requireContext()
            )

        container.setPadding(
            60,
            20,
            60,
            0
        )

        val input =
            EditText(
                requireContext()
            )

        input.hint =
            "Position (e.g. Peace Officer)"

        input.setSingleLine(
            true
        )

        container.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Position for ${member.fullName}"
            )
            .setView(
                container
            )
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Save"
            ) { _, _ ->

                val customPosition =
                    input.text
                        .toString()
                        .trim()

                if (
                    customPosition.isEmpty()
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Please enter a position.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    saveOfficerPosition(
                        member,
                        customPosition
                    )
                }
            }
            .show()
    }

    // =========================================================
    // SAVE OFFICER POSITION
    // =========================================================

    private fun saveOfficerPosition(
        member: MemberModel,
        position: String?
    ) {

        if (member.membershipId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Membership record not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val membershipRef =
            db.collection("memberships")
                .document(member.membershipId)

        membershipRef.get()
            .addOnSuccessListener { membershipDoc ->

                if (!isAdded) {
                    return@addOnSuccessListener
                }

                if (!membershipDoc.exists()) {

                    Toast.makeText(
                        requireContext(),
                        "Membership record not found.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                // =================================================
                // SECURITY CHECK
                // =================================================

                if (
                    membershipDoc.getString("organizationId")
                    != organizationId
                ) {

                    Toast.makeText(
                        requireContext(),
                        "You can only manage members of your organization.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                if (
                    membershipDoc.getString("status")
                    != "approved"
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Only approved members can be assigned a position.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val studentId =
                    membershipDoc.getString("studentId")
                        ?: ""

                val organizationName =
                    membershipDoc.getString("organizationName")
                        ?: member.organizationName

                // =================================================
                // CURRENT STATE
                // =================================================

                val currentRole =
                    membershipDoc.getString("role")
                        ?: "member"

                val currentPosition =
                    membershipDoc.getString("position")
                        ?.trim()
                        ?: ""

                // =================================================
                // PREVENT DUPLICATE ACTION
                // =================================================

                if (position == null) {

                    if (
                        currentRole == "member" &&
                        currentPosition.isBlank()
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "${member.fullName} is already a regular member.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addOnSuccessListener
                    }

                } else {

                    if (
                        currentRole == "officer" &&
                        currentPosition.equals(
                            position,
                            ignoreCase = true
                        )
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "${member.fullName} is already $position.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addOnSuccessListener
                    }
                }

                // =================================================
                // UPDATE MEMBERSHIP
                // =================================================

                val updates: Map<String, Any> =
                    if (position == null) {

                        mapOf(
                            "role" to "member",
                            "position" to FieldValue.delete()
                        )

                    } else {

                        mapOf(
                            "role" to "officer",
                            "position" to position
                        )
                    }

                membershipRef.update(updates)
                    .addOnSuccessListener {

                        // =========================================
                        // STUDENT NOTIFICATION
                        // =========================================

                        if (studentId.isNotBlank()) {

                            StudentNotificationHelper.create(
                                studentId = studentId,

                                title =
                                    if (position == null) {
                                        "Organization Position Update"
                                    } else if (
                                        currentRole == "officer" &&
                                        currentPosition.isNotBlank()
                                    ) {
                                        "Organization Position Changed"
                                    } else {
                                        "Organization Position Update"
                                    },

                                message =
                                    if (position == null) {

                                        "You are now a regular member of $organizationName."

                                    } else if (
                                        currentRole == "officer" &&
                                        currentPosition.isNotBlank()
                                    ) {

                                        "Your position in $organizationName was changed from $currentPosition to $position."

                                    } else {

                                        "You have been assigned as $position of $organizationName."
                                    },

                                type = "position",

                                organizationId =
                                    organizationId,

                                organizationName =
                                    organizationName,

                                referenceId =
                                    member.membershipId
                            )
                        }

                        if (isAdded) {

                            Toast.makeText(
                                requireContext(),

                                if (position == null) {
                                    "${member.fullName} is now a regular member."
                                } else {
                                    "${member.fullName} is now $position."
                                },

                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        loadMembers()
                    }
                    .addOnFailureListener { error ->

                        if (!isAdded) {
                            return@addOnFailureListener
                        }

                        Toast.makeText(
                            requireContext(),
                            "Failed to assign position: ${error.message}",
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
                    "Failed to load membership: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // APPROVE MEMBER
    // =========================================================

    private fun approveMember(
        member: MemberModel
    ) {

        val membershipId =
            member.membershipId

        if (
            membershipId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Invalid membership record.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val membershipRef =
            db.collection(
                "memberships"
            )
                .document(
                    membershipId
                )

        membershipRef
            .get()
            .addOnSuccessListener { membershipDoc ->

                if (
                    !membershipDoc.exists()
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Membership record not found.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                val studentId =
                    membershipDoc.getString(
                        "studentId"
                    )

                val membershipOrganizationId =
                    membershipDoc.getString(
                        "organizationId"
                    )

                val organizationName =
                    membershipDoc.getString(
                        "organizationName"
                    )
                        ?: member.organizationName

                if (
                    membershipOrganizationId !=
                    organizationId
                ) {

                    Toast.makeText(
                        requireContext(),
                        "You can only manage members of your organization.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                if (
                    studentId.isNullOrBlank()
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Student information is missing.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                // =================================================
                // MAXIMUM 3 ORGANIZATIONS
                // =================================================

                db.collection(
                    "memberships"
                )
                    .whereEqualTo(
                        "studentId",
                        studentId
                    )
                    .get()
                    .addOnSuccessListener { result ->

                        val approvedCount =
                            result.documents.count { document ->

                                document.getString(
                                    "status"
                                ) == "approved"
                            }

                        if (
                            approvedCount >= 3
                        ) {

                            Toast.makeText(
                                requireContext(),
                                "Cannot approve. This student already belongs to 3 organizations.",
                                Toast.LENGTH_LONG
                            ).show()

                            return@addOnSuccessListener
                        }

                        membershipRef
                            .update(
                                mapOf(
                                    "status" to
                                            "approved",

                                    "approvedAt" to
                                            FieldValue.serverTimestamp()
                                )
                            )
                            .addOnSuccessListener {

                                StudentNotificationHelper.create(
                                    studentId = studentId,

                                    title =
                                        "Organization Membership Approved",

                                    message =
                                        "Your membership request for $organizationName has been approved.",

                                    type =
                                        "membership_approved",

                                    organizationId =
                                        organizationId,

                                    organizationName =
                                        organizationName,

                                    referenceId =
                                        membershipId
                                )

                                createAdminNotification(
                                    title =
                                        "Member Approved",

                                    message =
                                        "$organizationName approved a membership request.",

                                    type =
                                        "signup"
                                )

                                Toast.makeText(
                                    requireContext(),
                                    "Member approved.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                loadMembers()
                            }
                            .addOnFailureListener { error ->

                                Toast.makeText(
                                    requireContext(),
                                    "Failed to approve member: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            requireContext(),
                            "Failed to check memberships: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    requireContext(),
                    "Failed to load membership: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // REJECT
    // =========================================================

    private fun rejectMember(
        member: MemberModel
    ) {

        if (
            member.membershipId.isBlank()
        ) {

            Toast.makeText(
                requireContext(),
                "Membership record not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val membershipRef =
            db.collection(
                "memberships"
            )
                .document(
                    member.membershipId
                )

        membershipRef
            .get()
            .addOnSuccessListener { membershipDocument ->

                val studentId =
                    membershipDocument.getString(
                        "studentId"
                    )

                val membershipOrganizationId =
                    membershipDocument.getString(
                        "organizationId"
                    )

                val organizationName =
                    membershipDocument.getString(
                        "organizationName"
                    )
                        ?: member.organizationName

                if (
                    membershipOrganizationId !=
                    organizationId
                ) {

                    Toast.makeText(
                        requireContext(),
                        "You can only manage members of your organization.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                if (
                    studentId.isNullOrBlank()
                ) {

                    Toast.makeText(
                        requireContext(),
                        "Student ID not found.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                if (
                    membershipDocument.getString("status") == "rejected"
                ) {

                    Toast.makeText(
                        requireContext(),
                        "This application is already rejected.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@addOnSuccessListener
                }

                membershipRef
                    .update(
                        "status",
                        "rejected"
                    )
                    .addOnSuccessListener {
                        StudentNotificationHelper.create(
                            studentId = studentId,

                            title =
                                "Membership Application Update",

                            message =
                                "Your application to $organizationName was not approved.",

                            type =
                                "membership_rejected",

                            organizationId =
                                organizationId,

                            organizationName =
                                organizationName,

                            referenceId =
                                member.membershipId
                        )

                        createAdminNotification(
                            title =
                                "Membership Application Rejected",

                            message =
                                "${member.fullName}'s membership application was rejected.",

                            type =
                                "info"
                        )

                        Toast.makeText(
                            requireContext(),
                            "${member.fullName}'s application rejected.",
                            Toast.LENGTH_SHORT
                        ).show()

                        loadMembers()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            requireContext(),
                            "Failed to reject: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    requireContext(),
                    "Failed to get membership: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // STUDENT NOTIFICATION
    // =========================================================

    private fun createNotification(
        studentId: String,
        title: String,
        message: String,
        type: String
    ) {

        val notification =
            hashMapOf(
                "userId" to
                        studentId,

                "title" to
                        title,

                "message" to
                        message,

                "type" to
                        type,

                "read" to
                        false,

                "createdAt" to
                        FieldValue.serverTimestamp()
            )

        db.collection(
            "notifications"
        )
            .add(
                notification
            )
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Notification could not be created: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // ADMIN NOTIFICATION
    // =========================================================

    private fun createAdminNotification(
        title: String,
        message: String,
        type: String
    ) {

        if (
            organizationId.isBlank()
        ) {
            return
        }

        val notification =
            hashMapOf(
                "organizationId" to
                        organizationId,

                "title" to
                        title,

                "message" to
                        message,

                "type" to
                        type,

                "read" to
                        false,

                "recipientRole" to
                        "admin",

                "createdAt" to
                        FieldValue.serverTimestamp()
            )

        db.collection(
            "notifications"
        )
            .add(
                notification
            )
            .addOnFailureListener { error ->

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Org Manager notification could not be created: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // CHIP STYLE
    // =========================================================

    private fun setActiveChip(
        activeChip: TextView
    ) {

        val allChips =
            listOf(
                chipAll,
                chipOfficers,
                chipPending
            )

        allChips.forEach { chip ->

            if (
                chip == activeChip
            ) {

                chip.setBackgroundResource(
                    R.drawable.bg_chip_active
                )

                chip.setTextColor(
                    resources.getColor(
                        R.color.white,
                        null
                    )
                )

            } else {

                chip.setBackgroundResource(
                    R.drawable.bg_chip
                )

                chip.setTextColor(
                    resources.getColor(
                        R.color.text_gray,
                        null
                    )
                )
            }
        }
    }

    // =========================================================
    // CHIP COUNTS
    // =========================================================

    private fun updateChipCounts() {

        chipAll.text =
            "All (${members.size})"

        chipOfficers.text =
            "Officers (${members.count { it.role == "officer" }})"

        chipPending.text =
            "Pending (${members.count { it.role == "pending" }})"
    }
}