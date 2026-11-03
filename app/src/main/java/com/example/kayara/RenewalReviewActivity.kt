package com.example.kayara

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

class RenewalReviewActivity : AppCompatActivity() {

    private lateinit var tvOrganizationName: TextView
    private lateinit var tvOrganizationCode: TextView
    private lateinit var tvPreviousAcademicYear: TextView

    // IMPORTANT:
    // This now matches activity_renewal_review.xml
    private lateinit var tvRenewingAcademicYear: TextView

    private lateinit var tvCurrentAcademicYear: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etRejectionReason: EditText

    private lateinit var btnApprove: Button
    private lateinit var btnReject: Button

    private lateinit var layoutRenewalRequirements: LinearLayout

    private val db =
        SupabaseDatabase.getInstance()

    private var renewalId: String = ""
    private var organizationId: String = ""
    private var renewingAcademicYear: String = ""
    private var organizationName: String = ""
    private var organizationCode: String = ""

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_renewal_review
        )

        initializeViews()

        getRenewalId()

        setupButtons()

        if (
            renewalId.isNotBlank()
        ) {

            loadRenewalDetails()

        } else {

            Toast.makeText(
                this,
                "Renewal request not found.",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private fun initializeViews() {

        tvOrganizationName =
            findViewById(
                R.id.tvOrganizationName
            )

        tvOrganizationCode =
            findViewById(
                R.id.tvOrganizationCode
            )

        tvPreviousAcademicYear =
            findViewById(
                R.id.tvPreviousAcademicYear
            )

        // IMPORTANT FIX:
        // XML ID is tvRenewingAcademicYear
        tvRenewingAcademicYear =
            findViewById(
                R.id.tvRenewingAcademicYear
            )

        tvCurrentAcademicYear =
            findViewById(
                R.id.tvCurrentAcademicYear
            )

        tvStatus =
            findViewById(
                R.id.tvStatus
            )

        etRejectionReason =
            findViewById(
                R.id.etRejectionReason
            )

        btnApprove =
            findViewById(
                R.id.btnApprove
            )

        btnReject =
            findViewById(
                R.id.btnReject
            )

        layoutRenewalRequirements =
            findViewById(
                R.id.layoutRenewalRequirements
            )
    }

    // ============================================================
    // GET RENEWAL ID
    // ============================================================

    private fun getRenewalId() {

        renewalId =
            intent.getStringExtra(
                "renewalId"
            )
                ?: intent.getStringExtra(
                    "RENEWAL_ID"
                )
                        ?: ""
    }

    // ============================================================
    // BUTTONS
    // ============================================================

    private fun setupButtons() {

        val backButton =
            findViewById<View?>(
                R.id.btnBack
            )

        backButton?.setOnClickListener {

            finish()
        }

        btnApprove.setOnClickListener {

            approveRenewal()
        }

        btnReject.setOnClickListener {

            rejectRenewal()
        }
    }

    // ============================================================
    // LOAD RENEWAL DETAILS
    // ============================================================

    private fun loadRenewalDetails() {

        db.collection(
            "organizationRenewals"
        )
            .document(
                renewalId
            )
            .get()
            .addOnSuccessListener { document ->

                if (
                    !document.exists()
                ) {

                    Toast.makeText(
                        this,
                        "Renewal request does not exist.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()

                    return@addOnSuccessListener
                }

                organizationId =
                    document.getString(
                        "organizationId"
                    ) ?: ""

                organizationName =
                    document.getString(
                        "organizationName"
                    )
                        ?: "Unknown Organization"

                organizationCode =
                    document.getString(
                        "organizationShortCode"
                    ) ?: ""

                val previousAcademicYear =
                    document.getString(
                        "previousAcademicYear"
                    )
                        ?: "N/A"

                renewingAcademicYear =
                    document.getString(
                        "academicYear"
                    ) ?: ""

                val status =
                    document.getString(
                        "status"
                    ) ?: "pending"

                tvOrganizationName.text =
                    organizationName

                tvOrganizationCode.text =
                    if (
                        organizationCode.isNotBlank()
                    ) {

                        organizationCode

                    } else {

                        "No organization code"
                    }

                tvPreviousAcademicYear.text =
                    previousAcademicYear

                // IMPORTANT FIX:
                tvRenewingAcademicYear.text =
                    renewingAcademicYear

                tvStatus.text =
                    status.uppercase()

                updateStatusAppearance(
                    status
                )

                loadCurrentAcademicYear()

                loadRenewalRequirements(
                    organizationId =
                        organizationId,

                    academicYear =
                        renewingAcademicYear
                )

                if (
                    !status.equals(
                        "pending",
                        ignoreCase = true
                    )
                ) {

                    btnApprove.isEnabled =
                        false

                    btnReject.isEnabled =
                        false

                    etRejectionReason.isEnabled =
                        false

                    if (
                        status.equals(
                            "rejected",
                            ignoreCase = true
                        )
                    ) {

                        val rejectionReason =
                            document.getString(
                                "rejectionReason"
                            ) ?: ""

                        if (
                            rejectionReason.isNotBlank()
                        ) {

                            etRejectionReason.setText(
                                rejectionReason
                            )
                        }
                    }
                }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load renewal: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // LOAD CURRENT ACADEMIC YEAR
    // ============================================================

    private fun loadCurrentAcademicYear() {

        if (
            organizationId.isBlank()
        ) {

            tvCurrentAcademicYear.text =
                "N/A"

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

                    val currentAcademicYear =
                        document.getString(
                            "currentAcademicYear"
                        )
                            ?: document.getString(
                                "academicYear"
                            )
                            ?: ""

                    tvCurrentAcademicYear.text =
                        if (
                            currentAcademicYear.isNotBlank()
                        ) {

                            currentAcademicYear

                        } else {

                            "N/A"
                        }

                } else {

                    tvCurrentAcademicYear.text =
                        "N/A"
                }
            }
            .addOnFailureListener {

                tvCurrentAcademicYear.text =
                    "N/A"
            }
    }

    // ============================================================
    // LOAD RENEWAL REQUIREMENTS
    // ============================================================

    private fun loadRenewalRequirements(
        organizationId: String,
        academicYear: String
    ) {

        layoutRenewalRequirements.removeAllViews()

        if (
            organizationId.isBlank() ||
            academicYear.isBlank()
        ) {

            showNoRequirements(
                "No renewal requirements found."
            )

            return
        }

        /*
         * Query only by organizationId.
         *
         * We check academicYear locally so this will not
         * depend on a Firestore composite index.
         */
        db.collection(
            "renewalRequirements"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { documents ->

                layoutRenewalRequirements.removeAllViews()

                val matchingDocuments =
                    documents.documents.filter { document ->

                        val savedAcademicYear =
                            document.getString(
                                "academicYear"
                            ) ?: ""

                        savedAcademicYear.trim()
                            .equals(
                                academicYear.trim(),
                                ignoreCase = true
                            )
                    }

                if (
                    matchingDocuments.isEmpty()
                ) {

                    showNoRequirements(
                        "No renewal files uploaded yet."
                    )

                    return@addOnSuccessListener
                }

                val sortedDocuments =
                    matchingDocuments.sortedBy {

                        it.getLong(
                            "requirementNumber"
                        )
                            ?: Long.MAX_VALUE
                    }

                for (
                document in sortedDocuments
                ) {

                    val requirementNumber =
                        document.getLong(
                            "requirementNumber"
                        )
                            ?.toInt()
                            ?: 0

                    val requirementName =
                        document.getString(
                            "requirementName"
                        )
                            ?: "Renewal Requirement"

                    val fileUrl =
                        document.getString(
                            "fileUrl"
                        )
                            ?: ""

                    addRequirementCard(
                        requirementNumber =
                            requirementNumber,

                        requirementName =
                            requirementName,

                        fileUrl =
                            fileUrl
                    )
                }
            }
            .addOnFailureListener { error ->

                showNoRequirements(
                    "Failed to load renewal files."
                )

                Toast.makeText(
                    this,
                    "Failed to load requirements: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // ADD REQUIREMENT CARD
    // ============================================================

    private fun addRequirementCard(
        requirementNumber: Int,
        requirementName: String,
        fileUrl: String
    ) {

        val adminBlue =
            getColor(R.color.admin_end)

        val darkBlue =
            Color.parseColor("#3B176A")

        val softBlue =
            Color.parseColor("#F1ECF8")

        val grayText =
            Color.parseColor("#6B5C7B")

        val strokeBlue =
            Color.parseColor("#E1E8F2")


        // ========================================================
        // CARD
        // ========================================================

        val card =
            CardView(this)

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.setMargins(
            0,
            dp(6),
            0,
            dp(6)
        )

        card.layoutParams =
            cardParams

        card.radius =
            dp(18).toFloat()

        card.cardElevation =
            dp(2).toFloat()

        card.setCardBackgroundColor(
            Color.WHITE
        )


        // ========================================================
        // MAIN CONTAINER
        // ========================================================

        val container =
            LinearLayout(this)

        container.orientation =
            LinearLayout.HORIZONTAL

        container.gravity =
            Gravity.CENTER_VERTICAL

        container.setPadding(
            dp(14),
            dp(13),
            dp(12),
            dp(13)
        )


        // ========================================================
        // NUMBER BADGE
        // ========================================================

        val numberView =
            TextView(this)

        numberView.text =
            requirementNumber.toString()

        numberView.textSize =
            14f

        numberView.setTextColor(
            Color.WHITE
        )

        numberView.gravity =
            Gravity.CENTER

        val numberSize =
            dp(38)

        val numberParams =
            LinearLayout.LayoutParams(
                numberSize,
                numberSize
            )

        numberParams.setMargins(
            0,
            0,
            dp(12),
            0
        )

        numberView.layoutParams =
            numberParams

        // ADMIN COLOR
        numberView.background =
            createRoundedBackground(
                adminBlue,
                dp(12).toFloat()
            )

        container.addView(
            numberView
        )


        // ========================================================
        // REQUIREMENT TEXT
        // ========================================================

        val textContainer =
            LinearLayout(this)

        textContainer.orientation =
            LinearLayout.VERTICAL

        val textParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        textContainer.layoutParams =
            textParams


        val title =
            TextView(this)

        title.text =
            requirementName

        title.textSize =
            13.5f

        title.setTextColor(
            darkBlue
        )

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        textContainer.addView(
            title
        )


        val uploadedText =
            TextView(this)

        uploadedText.text =
            if (
                fileUrl.isNotBlank()
            ) {
                "File uploaded"
            } else {
                "File unavailable"
            }

        uploadedText.textSize =
            11.5f

        uploadedText.setTextColor(
            grayText
        )

        val uploadedParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        uploadedParams.topMargin =
            dp(3)

        uploadedText.layoutParams =
            uploadedParams

        textContainer.addView(
            uploadedText
        )

        container.addView(
            textContainer
        )


        // ========================================================
        // VIEW BUTTON
        // ========================================================

        val viewButton =
            Button(this)

        viewButton.text =
            "VIEW"

        viewButton.textSize =
            10.5f

        viewButton.setTextColor(
            Color.WHITE
        )

        viewButton.isAllCaps =
            false

        viewButton.setPadding(
            dp(8),
            0,
            dp(8),
            0
        )

        // ADMIN COLOR
        viewButton.background =
            createRoundedBackground(
                adminBlue,
                dp(12).toFloat()
            )

        val buttonParams =
            LinearLayout.LayoutParams(
                dp(72),
                dp(40)
            )

        buttonParams.setMargins(
            dp(8),
            0,
            0,
            0
        )

        viewButton.layoutParams =
            buttonParams

        viewButton.isEnabled =
            fileUrl.isNotBlank()

        viewButton.alpha =
            if (
                fileUrl.isNotBlank()
            ) {
                1f
            } else {
                0.45f
            }

        viewButton.setOnClickListener {

            if (
                fileUrl.isBlank()
            ) {

                Toast.makeText(
                    this,
                    "File URL is unavailable.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            openFile(
                fileUrl
            )
        }

        container.addView(
            viewButton
        )

        card.addView(
            container
        )

        layoutRenewalRequirements.addView(
            card
        )
    }

    // ============================================================
    // NO REQUIREMENTS
    // ============================================================

    private fun showNoRequirements(
        message: String
    ) {

        layoutRenewalRequirements.removeAllViews()

        val text =
            TextView(
                this
            )

        text.text =
            message

        text.textSize =
            13f

        text.setTextColor(
            Color.parseColor(
                "#6B5C7B"
            )
        )

        text.gravity =
            Gravity.CENTER

        text.setPadding(
            dp(20),
            dp(30),
            dp(20),
            dp(30)
        )

        layoutRenewalRequirements.addView(
            text
        )
    }

    // ============================================================
    // OPEN FILE
    // ============================================================

    private fun openFile(
        fileUrl: String
    ) {

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        fileUrl
                    )
                )

            startActivity(
                intent
            )

        } catch (
            e: Exception
        ) {

            Toast.makeText(
                this,
                "Unable to open this file.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ============================================================
    // APPROVE RENEWAL
    // ============================================================

    private fun approveRenewal() {

        if (
            renewalId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Renewal ID is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        btnApprove.isEnabled =
            false

        btnReject.isEnabled =
            false

        db.collection(
            "organizationRenewals"
        )
            .document(
                renewalId
            )
            .get()
            .addOnSuccessListener { renewalDocument ->

                if (
                    !renewalDocument.exists()
                ) {

                    Toast.makeText(
                        this,
                        "Renewal request not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }


                val currentStatus =
                    renewalDocument.getString(
                        "status"
                    )
                        ?: "pending"

                if (
                    !currentStatus.equals(
                        "pending",
                        ignoreCase = true
                    )
                ) {

                    Toast.makeText(
                        this,
                        "This renewal has already been reviewed.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                getCurrentAcademicYearAndApprove()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to verify renewal: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                enableReviewButtons()
            }
    }

    // ============================================================
    // GET CURRENT ACADEMIC YEAR AND APPROVE
    // ============================================================

    private fun getCurrentAcademicYearAndApprove() {

        if (
            organizationId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Organization ID is missing.",
                Toast.LENGTH_LONG
            ).show()

            enableReviewButtons()

            return
        }

        db.collection(
            "organizations"
        )
            .document(
                organizationId
            )
            .get()
            .addOnSuccessListener { organizationDocument ->

                if (
                    !organizationDocument.exists()
                ) {

                    Toast.makeText(
                        this,
                        "Organization not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                val currentAcademicYear =
                    organizationDocument.getString(
                        "currentAcademicYear"
                    )
                        ?: organizationDocument.getString(
                            "academicYear"
                        )
                        ?: ""

                if (
                    currentAcademicYear.isBlank()
                ) {

                    Toast.makeText(
                        this,
                        "Current academic year is missing.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                approveRenewalWithYear(
                    currentAcademicYear
                )
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to get academic year: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                enableReviewButtons()
            }
    }

    // ============================================================
    // APPROVE + MOVE DOCUMENTS
    // ============================================================

    private fun approveRenewalWithYear(
        currentAcademicYear: String
    ) {

        val renewalRef =
            db.collection(
                "organizationRenewals"
            )
                .document(
                    renewalId
                )

        val organizationRef =
            db.collection(
                "organizations"
            )
                .document(
                    organizationId
                )

        /*
         * Load the renewal requirements first.
         *
         * After they are loaded, create one batch and commit
         * everything together.
         */
        db.collection(
            "renewalRequirements"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .get()
            .addOnSuccessListener { requirementSnapshot ->

                val requirements =
                    requirementSnapshot.documents.filter { document ->

                        val academicYear =
                            document.getString(
                                "academicYear"
                            ) ?: ""

                        academicYear.trim()
                            .equals(
                                renewingAcademicYear.trim(),
                                ignoreCase = true
                            )
                    }

                if (
                    requirements.isEmpty()
                ) {

                    Toast.makeText(
                        this,
                        "No renewal requirements were found.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                val batch =
                    db.batch()

                val now =
                    FieldValue.serverTimestamp()

                // =================================================
                // UPDATE RENEWAL
                // =================================================

                batch.update(
                    renewalRef,
                    mapOf(
                        "status" to
                                "approved",

                        "reviewedBy" to
                                getCurrentUserId(),

                        "reviewedAt" to
                                now,

                        "rejectionReason" to
                                "",

                        "documentFolderName" to
                                "Documents $currentAcademicYear",

                        "updatedAt" to
                                now
                    )
                )

                // =================================================
                // UPDATE ORGANIZATION
                // =================================================

                batch.update(
                    organizationRef,
                    mapOf(
                        "renewalStatus" to
                                "approved",

                        /*
                         * Make sure category is also returned
                         * to normal.
                         */
                        "category" to
                                "normal",

                        "documentsFolderName" to
                                "Documents $currentAcademicYear",

                        "updatedAt" to
                                now
                    )
                )

                // =================================================
                // CREATE ORGANIZATION FOLDER
                // =================================================

                val folderId =
                    "${organizationId}_${currentAcademicYear}"

                val folderRef =
                    db.collection(
                        "organizationFolders"
                    )
                        .document(
                            folderId
                        )

                batch.set(
                    folderRef,
                    mapOf(
                        "folderId" to
                                folderId,

                        "organizationId" to
                                organizationId,

                        "organizationName" to
                                organizationName,

                        "folderName" to
                                "Documents $currentAcademicYear",

                        "academicYear" to
                                currentAcademicYear,

                        "type" to
                                "renewal",

                        "createdAt" to
                                now,

                        "updatedAt" to
                                now
                    )
                )

                // =================================================
                // COPY RENEWAL REQUIREMENTS
                // =================================================

                for (
                requirement in requirements
                ) {

                    val requirementId =
                        requirement.getString(
                            "requirementId"
                        )
                            ?: requirement.id

                    val requirementName =
                        requirement.getString(
                            "requirementName"
                        )
                            ?: "Renewal Requirement"

                    val fileUrl =
                        requirement.getString(
                            "fileUrl"
                        )
                            ?: ""

                    val uploadedBy =
                        requirement.getString(
                            "uploadedBy"
                        )
                            ?: ""

                    val cloudinaryPublicId =
                        requirement.getString(
                            "cloudinaryPublicId"
                        )
                            ?: ""

                    val documentId =
                        "${organizationId}_${currentAcademicYear}_${requirementId}"

                    val documentRef =
                        db.collection(
                            "organizationDocuments"
                        )
                            .document(
                                documentId
                            )

                    val documentData =
                        hashMapOf<String, Any?>(
                            "organizationId" to
                                    organizationId,

                            "organizationName" to
                                    organizationName,

                            "folderName" to
                                    "Documents $currentAcademicYear",

                            "fileName" to
                                    requirementName,

                            "fileSize" to
                                    "Renewal Requirement",

                            "fileUrl" to
                                    fileUrl,

                            "publicId" to
                                    cloudinaryPublicId,

                            "uploadedBy" to
                                    uploadedBy,

                            "status" to
                                    "approved",

                            "source" to
                                    "renewal",

                            "renewalId" to
                                    renewalId,

                            "requirementId" to
                                    requirementId,

                            "requirementName" to
                                    requirementName,

                            "academicYear" to
                                    currentAcademicYear,

                            "createdAt" to
                                    now
                        )

                    batch.set(
                        documentRef,
                        documentData
                    )
                }

                // =================================================
                // COMMIT EVERYTHING
                // =================================================

                batch.commit()
                    .addOnSuccessListener {

                        AdminNotificationHelper.create(
                            organizationId =
                                organizationId,

                            organizationName =
                                organizationName,

                            title =
                                "Renewal Approved",

                            message =
                                "Your organization renewal for AY $renewingAcademicYear has been approved.",

                            type =
                                "renewal",

                            referenceId =
                                renewalId
                        )

                        Toast.makeText(
                            this,
                            "Renewal approved successfully.",
                            Toast.LENGTH_LONG
                        ).show()

                        setResult(
                            RESULT_OK
                        )

                        finish()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Failed to approve renewal: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        enableReviewButtons()
                    }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load renewal requirements: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                enableReviewButtons()
            }
    }

    // ============================================================
    // REJECT RENEWAL
    // ============================================================

    private fun rejectRenewal() {

        val reason =
            etRejectionReason.text
                .toString()
                .trim()

        if (
            reason.isEmpty()
        ) {

            etRejectionReason.error =
                "Please enter a rejection reason."

            etRejectionReason.requestFocus()

            return
        }

        if (
            renewalId.isBlank()
        ) {

            Toast.makeText(
                this,
                "Renewal ID is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        btnApprove.isEnabled =
            false

        btnReject.isEnabled =
            false

        db.collection(
            "organizationRenewals"
        )
            .document(
                renewalId
            )
            .get()
            .addOnSuccessListener { document ->

                if (
                    !document.exists()
                ) {

                    Toast.makeText(
                        this,
                        "Renewal request not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                val currentStatus =
                    document.getString(
                        "status"
                    )
                        ?: "pending"

                if (
                    !currentStatus.equals(
                        "pending",
                        ignoreCase = true
                    )
                ) {

                    Toast.makeText(
                        this,
                        "This renewal has already been reviewed.",
                        Toast.LENGTH_LONG
                    ).show()

                    enableReviewButtons()

                    return@addOnSuccessListener
                }

                val updateData =
                    mapOf(
                        "status" to
                                "rejected",

                        "reviewedBy" to
                                getCurrentUserId(),

                        "reviewedAt" to
                                FieldValue.serverTimestamp(),

                        "rejectionReason" to
                                reason,

                        "updatedAt" to
                                FieldValue.serverTimestamp()
                    )

                db.collection(
                    "organizationRenewals"
                )
                    .document(
                        renewalId
                    )
                    .update(
                        updateData
                    )
                    .addOnSuccessListener {

                        AdminNotificationHelper.create(
                            organizationId =
                                organizationId,

                            organizationName =
                                organizationName,

                            title =
                                "Renewal Needs Attention",

                            message =
                                "Your renewal was not approved. Reason: $reason",

                            type =
                                "renewal",

                            referenceId =
                                renewalId
                        )

                        Toast.makeText(
                            this,
                            "Renewal rejected.",
                            Toast.LENGTH_LONG
                        ).show()

                        setResult(
                            RESULT_OK
                        )

                        finish()
                    }
                    .addOnFailureListener { error ->

                        Toast.makeText(
                            this,
                            "Failed to reject renewal: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        enableReviewButtons()
                    }
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to verify renewal: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                enableReviewButtons()
            }
    }

    // ============================================================
    // STATUS APPEARANCE
    // ============================================================

    private fun updateStatusAppearance(
        status: String
    ) {

        when (
            status.lowercase()
        ) {

            "approved" -> {

                tvStatus.setTextColor(
                    Color.parseColor(
                        "#276653"
                    )
                )
            }

            "rejected" -> {

                tvStatus.setTextColor(
                    Color.parseColor(
                        "#AC3C4E"
                    )
                )
            }

            else -> {

                tvStatus.setTextColor(
                    getColor(
                        R.color.admin_end
                    )
                )
            }
        }
    }

    // ============================================================
    // ENABLE BUTTONS
    // ============================================================

    private fun enableReviewButtons() {

        btnApprove.isEnabled =
            true

        btnReject.isEnabled =
            true
    }

    // ============================================================
    // CURRENT USER
    // ============================================================

    private fun getCurrentUserId(): String {

        val currentUser =
            com.google.firebase.auth.FirebaseAuth
                .getInstance()
                .currentUser

        return currentUser?.uid
            ?: "super_admin"
    }

    // ============================================================
    // ROUNDED BACKGROUND
    // ============================================================

    private fun createRoundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.setColor(
            color
        )

        drawable.cornerRadius =
            radius

        return drawable
    }

    // ============================================================
    // DP
    // ============================================================

    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }
}