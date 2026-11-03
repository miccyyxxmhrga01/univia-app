package com.example.kayara

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

class SuperAdminApplicationReviewFragment : Fragment() {

    private lateinit var db: SupabaseDatabase

    private var applicationId: String = ""

    // =========================================================
    // DOCUMENT URLS
    // =========================================================

    private var document1Url: String = ""
    private var document2Url: String = ""
    private var document3Url: String = ""
    private var document4Url: String = ""
    private var document5Url: String = ""

    // =========================================================
    // UI
    // =========================================================

    private lateinit var tvOrganizationName: TextView
    private lateinit var tvApplicantName: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvStatus: TextView

    private lateinit var tvDocumentStatus: TextView

    private lateinit var tvDocument1Label: TextView
    private lateinit var tvDocument2Label: TextView
    private lateinit var tvDocument3Label: TextView

    private lateinit var tvDocument4Label: TextView

    private lateinit var tvDocument5Label: TextView



    private lateinit var tvDocument1Status: TextView
    private lateinit var tvDocument2Status: TextView
    private lateinit var tvDocument3Status: TextView

    private lateinit var tvDocument4Status: TextView

    private lateinit var tvDocument5Status: TextView

    private lateinit var btnViewDocument1: TextView
    private lateinit var btnViewDocument2: TextView
    private lateinit var btnViewDocument3: TextView

    private lateinit var btnViewDocument4: TextView

    private lateinit var btnViewDocument5: TextView

    private lateinit var btnApprove: TextView
    private lateinit var btnReject: TextView
    private lateinit var btnBack: TextView

    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        db =
            SupabaseDatabase.getInstance()

        applicationId =
            arguments?.getString(
                ARG_APPLICATION_ID
            ) ?: ""
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
                R.layout.fragment_super_admin_application_review,
                container,
                false
            )

        initializeViews(view)
        setupListeners()

        if (applicationId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Application ID not found.",
                Toast.LENGTH_LONG
            ).show()

            return view
        }

        loadApplication()

        return view
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private fun initializeViews(
        view: View
    ) {

        tvOrganizationName =
            view.findViewById(
                R.id.tvOrganizationName
            )

        tvApplicantName =
            view.findViewById(
                R.id.tvApplicantName
            )

        tvUsername =
            view.findViewById(
                R.id.tvUsername
            )

        tvEmail =
            view.findViewById(
                R.id.tvEmail
            )

        tvDescription =
            view.findViewById(
                R.id.tvDescription
            )

        tvStatus =
            view.findViewById(
                R.id.tvStatus
            )

        tvDocumentStatus =
            view.findViewById(
                R.id.tvDocumentStatus
            )

        tvDocument1Label =
            view.findViewById(
                R.id.tvDocument1Label
            )

        tvDocument2Label =
            view.findViewById(
                R.id.tvDocument2Label
            )

        tvDocument3Label =
            view.findViewById(
                R.id.tvDocument3Label
            )

        tvDocument4Label =
            view.findViewById(R.id.tvDocument4Label)

        tvDocument5Label =
            view.findViewById(R.id.tvDocument5Label)

        tvDocument1Status =
            view.findViewById(
                R.id.tvDocument1Status
            )

        tvDocument2Status =
            view.findViewById(
                R.id.tvDocument2Status
            )

        tvDocument3Status =
            view.findViewById(
                R.id.tvDocument3Status
            )

        tvDocument4Status =
            view.findViewById(R.id.tvDocument4Status)

        tvDocument5Status =
            view.findViewById(R.id.tvDocument5Status)

        btnViewDocument1 =
            view.findViewById(
                R.id.btnViewDocument1
            )

        btnViewDocument2 =
            view.findViewById(
                R.id.btnViewDocument2
            )

        btnViewDocument3 =
            view.findViewById(
                R.id.btnViewDocument3
            )

        btnViewDocument4 =
            view.findViewById(R.id.btnViewDocument4)

        btnViewDocument5 =
            view.findViewById(R.id.btnViewDocument5)

        btnApprove =
            view.findViewById(
                R.id.btnApprove
            )

        btnReject =
            view.findViewById(
                R.id.btnReject
            )

        btnBack =
            view.findViewById(
                R.id.btnBack
            )
    }

    // =========================================================
    // LISTENERS
    // =========================================================

    private fun setupListeners() {

        btnBack.setOnClickListener {

            parentFragmentManager
                .popBackStack()
        }

        btnViewDocument1.setOnClickListener {

            openDocument(
                document1Url
            )
        }

        btnViewDocument2.setOnClickListener {

            openDocument(
                document2Url
            )
        }

        btnViewDocument3.setOnClickListener {

            openDocument(
                document3Url
            )
        }

        btnViewDocument4.setOnClickListener {
            openDocument(document4Url)
        }

        btnViewDocument5.setOnClickListener {
            openDocument(document5Url)
        }

        btnApprove.setOnClickListener {

            approveApplication()
        }

        btnReject.setOnClickListener {

            showRejectDialog()
        }
    }

    // =========================================================
    // LOAD APPLICATION
    // =========================================================

    private fun loadApplication() {

        db.collection("adminApplications")
            .document(applicationId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        requireContext(),
                        "Application not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                val application =
                    document.toObject(
                        AdminApplicationModel::class.java
                    )

                if (application == null) {

                    Toast.makeText(
                        requireContext(),
                        "Unable to read application.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                displayApplication(
                    application
                )
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    requireContext(),
                    "Failed to load application: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // DISPLAY APPLICATION
    // =========================================================

    private fun displayApplication(
        application: AdminApplicationModel
    ) {

        tvOrganizationName.text =
            application.organizationName
                .ifBlank {
                    "Unnamed Organization"
                }

        tvApplicantName.text =
            application.fullName
                .ifBlank {
                    "Unknown Applicant"
                }

        tvUsername.text =
            "Username: " +
                    application.username
                        .ifBlank {
                            "Not provided"
                        }

        tvEmail.text =
            "Email: " +
                    application.email
                        .ifBlank {
                            "Not provided"
                        }

        tvDescription.text =
            application.organizationDescription
                .ifBlank {
                    "No organization description provided."
                }

        tvStatus.text =
            application.status.uppercase()

        // =====================================================
        // DOCUMENTS
        // =====================================================

        document1Url = ""
        document2Url = ""
        document3Url = ""
        document4Url = ""
        document5Url = ""

        val documents =
            application.documents

        if (documents.isNotEmpty()) {

            for (document in documents) {

                when (document.key) {

                    "police_clearance_lead_organizer",
                    "document1" -> {

                        document1Url =
                            document.url

                        tvDocument1Label.text =
                            document.label.ifBlank {
                                "Police Clearance of Lead Organizer"
                            }
                    }

                    "proposed_constitution_bylaws",
                    "document2" -> {

                        document2Url =
                            document.url

                        tvDocument2Label.text =
                            document.label.ifBlank {
                                "Proposed Constitution and By-Laws"
                            }
                    }

                    "list_of_members",
                    "document3" -> {

                        document3Url =
                            document.url

                        tvDocument3Label.text =
                            document.label.ifBlank {
                                "List of Members (Minimum of 30 Members)"
                            }
                    }

                    "adviser_nominees",
                    "document4" -> {

                        document4Url =
                            document.url

                        tvDocument4Label.text =
                            document.label.ifBlank {
                                "Names of Two (2) Nominees for Organization Adviser"
                            }
                    }

                    "annual_work_plan",
                    "document5" -> {

                        document5Url =
                            document.url

                        tvDocument5Label.text =
                            document.label.ifBlank {
                                "Annual Work Plan of Activities"
                            }
                    }
                }
            }

        } else {

            // compatibility sa very old application
            document1Url =
                application.documentUrl

            if (document1Url.isNotBlank()) {

                tvDocument1Label.text =
                    "Submitted Document"
            }
        }

        updateDocumentUI()

        // =====================================================
        // UPDATE DOCUMENT UI
        // =====================================================

        updateDocumentUI()

        // =====================================================
        // IF ALREADY PROCESSED
        // =====================================================

        if (application.status != "pending") {

            btnApprove.visibility =
                View.GONE

            btnReject.visibility =
                View.GONE
        }
    }

    // =========================================================
    // UPDATE DOCUMENT UI
    // =========================================================

    private fun updateDocumentUI() {

        var uploadedCount = 0


        // DOCUMENT 1
        if (document1Url.isBlank()) {

            tvDocument1Status.text =
                "Not submitted"

            btnViewDocument1.isEnabled =
                false

            btnViewDocument1.alpha =
                0.5f

        } else {

            uploadedCount++

            tvDocument1Status.text =
                "Document submitted"

            btnViewDocument1.isEnabled =
                true

            btnViewDocument1.alpha =
                1f
        }


        // DOCUMENT 2
        if (document2Url.isBlank()) {

            tvDocument2Status.text =
                "Not submitted"

            btnViewDocument2.isEnabled =
                false

            btnViewDocument2.alpha =
                0.5f

        } else {

            uploadedCount++

            tvDocument2Status.text =
                "Document submitted"

            btnViewDocument2.isEnabled =
                true

            btnViewDocument2.alpha =
                1f
        }


        // DOCUMENT 3
        if (document3Url.isBlank()) {

            tvDocument3Status.text =
                "Not submitted"

            btnViewDocument3.isEnabled =
                false

            btnViewDocument3.alpha =
                0.5f

        } else {

            uploadedCount++

            tvDocument3Status.text =
                "Document submitted"

            btnViewDocument3.isEnabled =
                true

            btnViewDocument3.alpha =
                1f
        }


        // DOCUMENT 4
        if (document4Url.isBlank()) {

            tvDocument4Status.text =
                "Not submitted"

            btnViewDocument4.isEnabled =
                false

            btnViewDocument4.alpha =
                0.5f

        } else {

            uploadedCount++

            tvDocument4Status.text =
                "Document submitted"

            btnViewDocument4.isEnabled =
                true

            btnViewDocument4.alpha =
                1f
        }


        // DOCUMENT 5
        if (document5Url.isBlank()) {

            tvDocument5Status.text =
                "Not submitted"

            btnViewDocument5.isEnabled =
                false

            btnViewDocument5.alpha =
                0.5f

        } else {

            uploadedCount++

            tvDocument5Status.text =
                "Document submitted"

            btnViewDocument5.isEnabled =
                true

            btnViewDocument5.alpha =
                1f
        }


        tvDocumentStatus.text =
            "$uploadedCount of 5 documents submitted"
    }

    // =========================================================
    // OPEN DOCUMENT
    // =========================================================

    private fun openDocument(
        url: String
    ) {

        if (url.isBlank()) {

            Toast.makeText(
                requireContext(),
                "No document is available.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )

            startActivity(intent)

        } catch (error: Exception) {

            Toast.makeText(
                requireContext(),
                "Unable to open the document.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // APPROVE
    // =========================================================

    private fun approveApplication() {
        if (applicationId.isBlank()) return
        btnApprove.isEnabled = false
        btnReject.isEnabled = false
        val applicationRef = db.collection("adminApplications").document(applicationId)
        val organizationId = "org_$applicationId"
        val organizationRef = db.collection("organizations").document(organizationId)
        db.runTransaction { transaction ->
            val application = transaction.get(applicationRef)
            check(application.exists()) { "Application not found." }
            check(application.getString("status") == "pending") { "This application has already been processed." }
            check(!application.getString("uid").isNullOrBlank()) { "Applicant user ID is missing." }
            val organization = transaction.get(organizationRef)
            check(!organization.exists()) { "An organization already exists for this application. Reload before reviewing." }
            val name = application.getString("organizationName").orEmpty()
            transaction.update(applicationRef, mapOf("status" to "approved",
                "organizationId" to organizationId, "rejectionReason" to "",
                "updatedAt" to FieldValue.serverTimestamp()))
            transaction.set(organizationRef, mapOf(
                "organizationId" to organizationId, "orgName" to name,
                "shortCode" to getShortCode(name), "memberCount" to 0,
                "adviserName" to application.getString("fullName").orEmpty(),
                "category" to "normal", "colorStart" to "#7C3AED", "colorEnd" to "#7C3AED",
                "recruitmentInfo" to "", "adminUid" to "", "applicationId" to applicationId,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        }.addOnSuccessListener {
            if (!isAdded || view == null) return@addOnSuccessListener
            Toast.makeText(requireContext(),
                "Organization approved. Assign its manager from Manage organization access.",
                Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }.addOnFailureListener { showActionError(it.message ?: "Unable to approve the application. Try again.") }
    }

    // =========================================================
    // SHORT CODE
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

        if (words.isEmpty()) {
            return "ORG"
        }

        return if (words.size == 1) {

            words[0]
                .take(3)
                .uppercase()

        } else {

            words
                .take(3)
                .joinToString("") {
                    it.first()
                        .uppercase()
                }
        }
    }

    // =========================================================
    // REJECT DIALOG
    // =========================================================

    private fun showRejectDialog() {

        if (applicationId.isBlank()) {

            Toast.makeText(
                requireContext(),
                "Application ID not found.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val input =
            EditText(
                requireContext()
            )

        input.hint =
            "Enter reason for rejection"

        input.setPadding(
            40,
            20,
            40,
            20
        )

        input.minLines = 3
        input.maxLines = 5

        val dialog =
            AlertDialog.Builder(
                requireContext()
            )
                .setTitle(
                    "Reject Application"
                )
                .setMessage(
                    "Please provide a reason for rejecting this organization application."
                )
                .setView(input)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Reject",
                    null
                )
                .create()

        dialog.setOnShowListener {

            val rejectButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            rejectButton.setOnClickListener {

                val reason =
                    input.text
                        .toString()
                        .trim()

                if (reason.isBlank()) {

                    input.error =
                        "Please enter a rejection reason."

                    return@setOnClickListener
                }

                dialog.dismiss()

                rejectApplication(
                    reason
                )
            }
        }

        dialog.show()
    }

    // =========================================================
    // REJECT
    // =========================================================

    private fun rejectApplication(rejectionReason: String) {
        if (applicationId.isBlank()) return
        btnApprove.isEnabled = false
        btnReject.isEnabled = false
        val ref = db.collection("adminApplications").document(applicationId)
        db.runTransaction { transaction ->
            val application = transaction.get(ref)
            check(application.exists()) { "Application not found." }
            check(application.getString("status") == "pending") { "This application has already been processed." }
            transaction.update(ref, mapOf("status" to "rejected", "rejectionReason" to rejectionReason,
                "updatedAt" to FieldValue.serverTimestamp()))
        }.addOnSuccessListener {
            if (!isAdded || view == null) return@addOnSuccessListener
            Toast.makeText(requireContext(), "Application rejected.", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }.addOnFailureListener { showActionError(it.message ?: "Unable to reject the application. Try again.") }
    }

    // =========================================================
    // ERROR
    // =========================================================

    private fun showActionError(
        message: String
    ) {

        if (!isAdded) {
            return
        }

        btnApprove.isEnabled = true
        btnReject.isEnabled = true

        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // =========================================================
    // NEW INSTANCE
    // =========================================================

    companion object {

        private const val ARG_APPLICATION_ID =
            "applicationId"

        fun newInstance(
            applicationId: String
        ): SuperAdminApplicationReviewFragment {

            val fragment =
                SuperAdminApplicationReviewFragment()

            val bundle =
                Bundle()

            bundle.putString(
                ARG_APPLICATION_ID,
                applicationId
            )

            fragment.arguments =
                bundle

            return fragment
        }
    }
}
