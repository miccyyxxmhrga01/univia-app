package com.example.kayara

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase

class OrganizationProfileActivity : AppCompatActivity() {

    private val db = SupabaseDatabase.getInstance()

    private lateinit var tvOrgName: TextView
    private lateinit var tvOrgCode: TextView
    private lateinit var tvAdviser: TextView
    private lateinit var tvMemberCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvRecruitmentInfo: TextView

    private lateinit var approvedMembersContainer: LinearLayout
    private lateinit var pendingMembersContainer: LinearLayout

    private lateinit var tvNoApprovedMembers: TextView
    private lateinit var tvNoPendingMembers: TextView

    // FIX: tinuod nga organization ID (document.id)
    private var organizationId = ""
    private var organizationName = ""
    private var shortCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_organization_profile)

        // FIX: kuhaon ang organizationId
        organizationId =
            intent.getStringExtra("organizationId") ?: ""

        organizationName =
            intent.getStringExtra("organizationName") ?: ""

        shortCode =
            intent.getStringExtra("shortCode") ?: ""

        initializeViews()

        findViewById<TextView>(R.id.backButton)
            .setOnClickListener {
                finish()
            }

        loadOrganization()
        loadMembers()
    }

    private fun initializeViews() {

        tvOrgName =
            findViewById(R.id.tvOrgName)

        tvOrgCode =
            findViewById(R.id.tvOrgCode)

        tvAdviser =
            findViewById(R.id.tvAdviser)

        tvMemberCount =
            findViewById(R.id.tvMemberCount)

        tvPendingCount =
            findViewById(R.id.tvPendingCount)

        tvRecruitmentInfo =
            findViewById(R.id.tvRecruitmentInfo)

        approvedMembersContainer =
            findViewById(
                R.id.approvedMembersContainer
            )

        pendingMembersContainer =
            findViewById(
                R.id.pendingMembersContainer
            )

        tvNoApprovedMembers =
            findViewById(
                R.id.tvNoApprovedMembers
            )

        tvNoPendingMembers =
            findViewById(
                R.id.tvNoPendingMembers
            )

        tvOrgName.text = organizationName
        tvOrgCode.text = shortCode
    }

    // =========================================================
    // FIX: LOAD ORGANIZATION BY REAL ID
    // =========================================================

    private fun loadOrganization() {

        if (organizationId.isNotBlank()) {

            db.collection("organizations")
                .document(organizationId)
                .get()
                .addOnSuccessListener { document ->

                    if (!document.exists()) {
                        return@addOnSuccessListener
                    }

                    showOrganization(document)
                }
                .addOnFailureListener { error ->

                    Toast.makeText(
                        this,
                        "Failed to load organization: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            return
        }

        // Backup: kung wala ang ID, pangitaa pinaagi sa name
        if (organizationName.isBlank()) {
            return
        }

        db.collection("organizations")
            .whereEqualTo("orgName", organizationName)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                val document =
                    result.documents.firstOrNull()
                        ?: return@addOnSuccessListener

                organizationId = document.id

                showOrganization(document)

                // i-reload ang members gamit ang tinuod nga ID
                loadMembers()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load organization: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showOrganization(
        document: DocumentSnapshot
    ) {

        val name =
            document.getString("orgName")
                ?: organizationName

        val code =
            document.getString("shortCode")
                ?.takeIf { it.isNotBlank() }
                ?: shortCode

        val adviser =
            document.getString("adviserName")
                ?: "No adviser assigned"

        val recruitmentInfo =
            document.getString("recruitmentInfo")
                ?: "No recruitment information available."

        tvOrgName.text = name
        tvOrgCode.text = code
        tvAdviser.text = adviser
        tvRecruitmentInfo.text = recruitmentInfo
    }

    private fun loadMembers() {

        db.collection("memberships")
            .get()
            .addOnSuccessListener { result ->

                approvedMembersContainer
                    .removeAllViews()

                pendingMembersContainer
                    .removeAllViews()

                val approvedMembers =
                    result.documents.filter { document ->

                        belongsToOrganization(document) &&
                                document.getString(
                                    "status"
                                ) == "approved"
                    }

                val pendingMembers =
                    result.documents.filter { document ->

                        belongsToOrganization(document) &&
                                document.getString(
                                    "status"
                                ) == "pending"
                    }

                tvMemberCount.text =
                    approvedMembers.size.toString()

                tvPendingCount.text =
                    pendingMembers.size.toString()

                if (approvedMembers.isEmpty()) {

                    tvNoApprovedMembers.visibility =
                        View.VISIBLE

                } else {

                    tvNoApprovedMembers.visibility =
                        View.GONE

                    approvedMembers.forEach { document ->

                        addMemberItem(
                            document,
                            approvedMembersContainer
                        )
                    }
                }

                if (pendingMembers.isEmpty()) {

                    tvNoPendingMembers.visibility =
                        View.VISIBLE

                } else {

                    tvNoPendingMembers.visibility =
                        View.GONE

                    pendingMembers.forEach { document ->

                        addMemberItem(
                            document,
                            pendingMembersContainer
                        )
                    }
                }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load members: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // FIX: MATCH BY REAL organizationId FIRST
    // =========================================================

    private fun belongsToOrganization(
        document: DocumentSnapshot
    ): Boolean {

        val membershipOrganizationId =
            document.getString("organizationId")
                ?: ""

        val membershipShortCode =
            document.getString("shortCode")
                ?: ""

        val membershipOrganizationName =
            document.getString("organizationName")
                ?: ""

        // Tinuod nga ID (gikan sa document.id)
        if (
            organizationId.isNotBlank() &&
            membershipOrganizationId == organizationId
        ) {
            return true
        }

        // Backup para sa daan nga data
        if (
            shortCode.isNotBlank() &&
            (membershipShortCode == shortCode ||
                    membershipOrganizationId == shortCode)
        ) {
            return true
        }

        return organizationName.isNotBlank() &&
                membershipOrganizationName.equals(
                    organizationName,
                    ignoreCase = true
                )
    }

    private fun addMemberItem(
        membershipDocument: DocumentSnapshot,
        container: LinearLayout
    ) {

        val studentId =
            membershipDocument.getString(
                "studentId"
            ) ?: ""

        val studentName =
            membershipDocument.getString(
                "fullName"
            ) ?: ""

        val applicationNote =
            membershipDocument.getString(
                "applicationNote"
            ) ?: ""

        val item = layoutInflater.inflate(
            R.layout.item_organization_member,
            container,
            false
        )

        val tvName =
            item.findViewById<TextView>(
                R.id.tvMemberName
            )

        val tvStudentId =
            item.findViewById<TextView>(
                R.id.tvMemberStudentId
            )

        val tvNote =
            item.findViewById<TextView>(
                R.id.tvMemberNote
            )

        tvName.text =
            if (studentName.isNotBlank()) {
                studentName
            } else {
                "Student"
            }

        tvStudentId.text =
            if (studentId.isNotBlank()) {
                "Student ID: $studentId"
            } else {
                "Student ID unavailable"
            }

        if (applicationNote.isNotBlank()) {

            tvNote.visibility =
                View.VISIBLE

            tvNote.text =
                "Application note: $applicationNote"

        } else {

            tvNote.visibility =
                View.GONE
        }

        container.addView(item)
    }
}