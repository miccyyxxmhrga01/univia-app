package com.example.kayara

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.SupabaseDatabase

class RenewalRequestActivity : AppCompatActivity() {

    companion object {

        /*
         * IMPORTANT:
         * Replace this with your ACTUAL Cloudinary Cloud Name.
         *
         * Example:
         * private const val CLOUDINARY_CLOUD_NAME = "dxxxxx"
         */
        private const val CLOUDINARY_CLOUD_NAME = "hq6hiqhq"
        private const val CLOUDINARY_UPLOAD_PRESET = "kayara_upload"

        private const val REQ_1 =
            "Student Organization Accreditation Form"

        private const val REQ_2 =
            "Accomplishment Report"

        private const val REQ_3 =
            "Copy of Constitutional and By Laws"

        private const val REQ_4 =
            "Updated List of Members"

        private const val REQ_5 =
            "Annual Work Plan of Activities"

        private const val REQ_6 =
            "Financial Statement"
    }

    private lateinit var db: SupabaseDatabase
    private lateinit var auth: FirebaseAuth

    private var organizationId = ""
    private var organizationName = ""
    private var organizationShortCode = ""
    private var adminUid = ""

    private lateinit var tvOrganizationName: TextView
    private lateinit var tvOrganizationCode: TextView
    private lateinit var tvCurrentAcademicYear: TextView
    private lateinit var tvRenewingAcademicYear: TextView
    private lateinit var tvStatus: TextView

    private lateinit var btnSubmitRenewal: Button
    private lateinit var btnBack: ImageView

    private lateinit var btnUpload1: Button
    private lateinit var btnUpload2: Button
    private lateinit var btnUpload3: Button
    private lateinit var btnUpload4: Button
    private lateinit var btnUpload5: Button
    private lateinit var btnUpload6: Button

    /*
     * We store the uploaded Cloudinary URLs.
     *
     * These are more useful than storing the local Uri,
     * because the actual files are now in Cloudinary.
     */
    private var file1Url: String? = null
    private var file2Url: String? = null
    private var file3Url: String? = null
    private var file4Url: String? = null
    private var file5Url: String? = null
    private var file6Url: String? = null

    /*
     * Current requirement being selected.
     */
    private var selectedRequirementNumber = 0
    private var selectedRequirementName = ""

    /*
     * Prevents the user from selecting another requirement
     * while the current file is being uploaded.
     */
    private var isUploading = false

    /*
     * Android document picker.
     *
     * This is better than the old startActivityForResult().
     */
    private val documentPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {

                Toast.makeText(
                    this,
                    "No document selected.",
                    Toast.LENGTH_SHORT
                ).show()

                resetUploadButton()
                return@registerForActivityResult
            }

            uploadSelectedFile(
                uri = uri,
                requirementNumber = selectedRequirementNumber,
                requirementName = selectedRequirementName
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_renewal_request)

        db = SupabaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        /*
         * Cloudinary must be initialized before MediaManager.get()
         * is used.
         *
         * If your project already initializes MediaManager in
         * Application.onCreate(), you can remove this call.
         */
        initializeCloudinary()

        organizationId =
            intent.getStringExtra("organizationId") ?: ""

        organizationName =
            intent.getStringExtra("organizationName") ?: ""

        organizationShortCode =
            intent.getStringExtra("organizationShortCode") ?: ""

        adminUid =
            intent.getStringExtra("adminUid") ?: ""

        initializeViews()

        tvOrganizationName.text =
            organizationName.ifBlank {
                "Organization Name"
            }

        tvOrganizationCode.text =
            organizationShortCode.ifBlank {
                "ORG"
            }

        btnBack.setOnClickListener {
            finish()
        }

        setupUploadButtons()

        loadOrganization()

        updateSubmitButton()
    }

    // ============================================================
    // CLOUDINARY INITIALIZATION
    // ============================================================

    private fun initializeCloudinary() {

        /*
         * Do not initialize if your Cloudinary cloud name
         * has not yet been placed in this file.
         */
        if (
            CLOUDINARY_CLOUD_NAME.isBlank() ||
            CLOUDINARY_CLOUD_NAME == "YOUR_CLOUD_NAME"
        ) {

            Toast.makeText(
                this,
                "Cloudinary cloud name is not configured.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        try {

            val config =
                HashMap<String, String>()

            config["cloud_name"] =
                CLOUDINARY_CLOUD_NAME

            /*
             * MediaManager.init() should only be called once
             * during the application's lifecycle.
             *
             * If it has already been initialized elsewhere,
             * this catch prevents the app from crashing.
             */
            MediaManager.init(
                applicationContext,
                config
            )

        } catch (e: Exception) {

            /*
             * If MediaManager was already initialized,
             * we simply continue using it.
             */
        }
    }

    // ============================================================
    // VIEW INITIALIZATION
    // ============================================================

    private fun initializeViews() {

        tvOrganizationName =
            findViewById(R.id.tvOrganizationName)

        tvOrganizationCode =
            findViewById(R.id.tvOrganizationCode)

        tvCurrentAcademicYear =
            findViewById(R.id.tvCurrentAcademicYear)

        tvRenewingAcademicYear =
            findViewById(R.id.tvRenewingAcademicYear)

        tvStatus =
            findViewById(R.id.tvStatus)

        btnSubmitRenewal =
            findViewById(R.id.btnSubmitRenewal)

        btnBack =
            findViewById(R.id.btnBack)

        btnUpload1 =
            findViewById(R.id.btnUpload1)

        btnUpload2 =
            findViewById(R.id.btnUpload2)

        btnUpload3 =
            findViewById(R.id.btnUpload3)

        btnUpload4 =
            findViewById(R.id.btnUpload4)

        btnUpload5 =
            findViewById(R.id.btnUpload5)

        btnUpload6 =
            findViewById(R.id.btnUpload6)
    }

    // ============================================================
    // UPLOAD BUTTONS
    // ============================================================

    private fun setupUploadButtons() {

        btnUpload1.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    1,
                    REQ_1
                )
            }
        }

        btnUpload2.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    2,
                    REQ_2
                )
            }
        }

        btnUpload3.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    3,
                    REQ_3
                )
            }
        }

        btnUpload4.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    4,
                    REQ_4
                )
            }
        }

        btnUpload5.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    5,
                    REQ_5
                )
            }
        }

        btnUpload6.setOnClickListener {

            if (!isUploading) {

                selectRequirement(
                    6,
                    REQ_6
                )
            }
        }

        btnSubmitRenewal.setOnClickListener {

            if (!isUploading) {
                submitRenewalRequest()
            }
        }
    }

    // ============================================================
    // SELECT REQUIREMENT
    // ============================================================

    private fun selectRequirement(
        requirementNumber: Int,
        requirementName: String
    ) {

        selectedRequirementNumber =
            requirementNumber

        selectedRequirementName =
            requirementName

        /*
         * Accept common document formats.
         *
         * Images are also accepted because some organizations
         * may submit scanned documents.
         */
        documentPicker.launch(
            arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain",
                "image/*"
            )
        )
    }

    // ============================================================
    // UPLOAD TO CLOUDINARY
    // ============================================================

    private fun uploadSelectedFile(
        uri: Uri,
        requirementNumber: Int,
        requirementName: String
    ) {

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization information is missing.",
                Toast.LENGTH_LONG
            ).show()

            resetUploadButton()
            return
        }

        val currentUser =
            auth.currentUser

        val userUid =
            currentUser?.uid
                ?: adminUid

        if (userUid.isBlank()) {

            Toast.makeText(
                this,
                "Org Manager account information is missing.",
                Toast.LENGTH_LONG
            ).show()

            resetUploadButton()
            return
        }

        if (
            CLOUDINARY_CLOUD_NAME.isBlank() ||
            CLOUDINARY_CLOUD_NAME == "YOUR_CLOUD_NAME"
        ) {

            Toast.makeText(
                this,
                "Cloudinary cloud name is missing.",
                Toast.LENGTH_LONG
            ).show()

            resetUploadButton()
            return
        }

        isUploading = true

        val button =
            getButton(requirementNumber)

        button.isEnabled = false
        button.text = "UPLOADING..."

        disableOtherUploadButtons(
            requirementNumber
        )

        Toast.makeText(
            this,
            "Uploading $requirementName...",
            Toast.LENGTH_SHORT
        ).show()

        val currentYear =
            tvCurrentAcademicYear.text.toString()

        val renewingYear =
            tvRenewingAcademicYear.text.toString()

        /*
         * Folder inside Cloudinary.
         *
         * Example:
         *
         * kayara/renewals/organizationId/2027-2028
         */
        val cloudinaryFolder =
            "kayara/renewals/" +
                    organizationId +
                    "/" +
                    renewingYear

        /*
         * IMPORTANT:
         *
         * We do NOT use FirebaseStorage here.
         *
         * We upload directly to Cloudinary using the
         * unsigned preset.
         *
         * The folder parameter is supported for unsigned
         * uploads.
         */
        try {

            MediaManager.get()
                .upload(uri)
                .unsigned(
                    CLOUDINARY_UPLOAD_PRESET
                )
                .option(
                    "resource_type",
                    "auto"
                )
                .option(
                    "folder",
                    cloudinaryFolder
                )
                .callback(
                    object : UploadCallback {

                        override fun onStart(
                            requestId: String
                        ) {

                            runOnUiThread {

                                button.text =
                                    "UPLOADING..."

                            }
                        }

                        override fun onProgress(
                            requestId: String,
                            bytes: Long,
                            totalBytes: Long
                        ) {

                            if (totalBytes > 0) {

                                val progress =
                                    (
                                            bytes * 100
                                            ) / totalBytes

                                runOnUiThread {

                                    button.text =
                                        "UPLOADING $progress%"

                                }
                            }
                        }

                        override fun onSuccess(
                            requestId: String,
                            resultData: Map<*, *>
                        ) {

                            /*
                             * Cloudinary returns secure_url.
                             */
                            val secureUrl =
                                resultData[
                                    "secure_url"
                                ]?.toString()

                            if (
                                secureUrl.isNullOrBlank()
                            ) {

                                runOnUiThread {

                                    uploadFailed(
                                        button,
                                        "Cloudinary did not return a file URL."
                                    )
                                }

                                return
                            }

                            /*
                             * Now save the Cloudinary URL
                             * into Firestore.
                             */
                            saveRequirementToFirestore(
                                uri = uri,
                                downloadUrl = secureUrl,
                                cloudinaryPublicId =
                                    resultData[
                                        "public_id"
                                    ]?.toString() ?: "",
                                requirementNumber =
                                    requirementNumber,
                                requirementName =
                                    requirementName,
                                userUid =
                                    userUid,
                                currentYear =
                                    currentYear,
                                renewingYear =
                                    renewingYear,
                                button =
                                    button
                            )
                        }

                        override fun onError(
                            requestId: String,
                            error: ErrorInfo
                        ) {

                            runOnUiThread {

                                uploadFailed(
                                    button,
                                    error.description
                                        ?: "Unknown Cloudinary error."
                                )
                            }
                        }

                        override fun onReschedule(
                            requestId: String,
                            error: ErrorInfo
                        ) {

                            runOnUiThread {

                                button.text =
                                    "RETRYING..."
                            }
                        }
                    }
                )
                .dispatch()

        } catch (e: Exception) {

            uploadFailed(
                button,
                e.message
                    ?: "Cloudinary initialization error."
            )
        }
    }

    // ============================================================
    // SAVE REQUIREMENT TO FIRESTORE
    // ============================================================

    private fun saveRequirementToFirestore(
        uri: Uri,
        downloadUrl: String,
        cloudinaryPublicId: String,
        requirementNumber: Int,
        requirementName: String,
        userUid: String,
        currentYear: String,
        renewingYear: String,
        button: Button
    ) {

        val requirementRef =
            db.collection(
                "renewalRequirements"
            ).document()

        val requirementData =
            hashMapOf(
                "requirementId"
                        to requirementRef.id,

                "organizationId"
                        to organizationId,

                "organizationName"
                        to organizationName,

                "organizationShortCode"
                        to organizationShortCode,

                "academicYear"
                        to renewingYear,

                "previousAcademicYear"
                        to currentYear,

                "requirementName"
                        to requirementName,

                "requirementNumber"
                        to requirementNumber,

                /*
                 * Cloudinary URL
                 */
                "fileUrl"
                        to downloadUrl,

                /*
                 * Cloudinary public ID
                 */
                "cloudinaryPublicId"
                        to cloudinaryPublicId,

                /*
                 * Firebase Storage path is NO LONGER USED.
                 */
                "storageProvider"
                        to "cloudinary",

                "uploadedBy"
                        to userUid,

                "uploadedAt"
                        to Timestamp.now(),

                "status"
                        to "uploaded"
            )

        requirementRef
            .set(requirementData)
            .addOnSuccessListener {

                runOnUiThread {

                    setRequirementUrl(
                        requirementNumber,
                        downloadUrl
                    )

                    button.isEnabled =
                        true

                    button.text =
                        "UPLOADED"

                    isUploading =
                        false

                    enableAllUploadButtons()

                    updateSubmitButton()

                    Toast.makeText(
                        this,
                        "$requirementName uploaded successfully.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { error ->

                runOnUiThread {

                    button.isEnabled =
                        true

                    button.text =
                        "UPLOAD"

                    isUploading =
                        false

                    enableAllUploadButtons()

                    Toast.makeText(
                        this,
                        "File uploaded, but saving its details failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // ============================================================
    // SAVE URL IN MEMORY
    // ============================================================

    private fun setRequirementUrl(
        requirementNumber: Int,
        url: String
    ) {

        when (requirementNumber) {

            1 -> file1Url = url

            2 -> file2Url = url

            3 -> file3Url = url

            4 -> file4Url = url

            5 -> file5Url = url

            6 -> file6Url = url
        }
    }

    // ============================================================
    // GET BUTTON
    // ============================================================

    private fun getButton(
        requirementNumber: Int
    ): Button {

        return when (requirementNumber) {

            1 -> btnUpload1

            2 -> btnUpload2

            3 -> btnUpload3

            4 -> btnUpload4

            5 -> btnUpload5

            else -> btnUpload6
        }
    }

    // ============================================================
    // DISABLE OTHER BUTTONS WHILE UPLOADING
    // ============================================================

    private fun disableOtherUploadButtons(
        selectedNumber: Int
    ) {

        val buttons =
            listOf(
                btnUpload1,
                btnUpload2,
                btnUpload3,
                btnUpload4,
                btnUpload5,
                btnUpload6
            )

        buttons.forEachIndexed { index, button ->

            if (index + 1 != selectedNumber) {
                button.isEnabled = false
            }
        }
    }

    // ============================================================
    // ENABLE ALL UPLOAD BUTTONS
    // ============================================================

    private fun enableAllUploadButtons() {

        btnUpload1.isEnabled = true
        btnUpload2.isEnabled = true
        btnUpload3.isEnabled = true
        btnUpload4.isEnabled = true
        btnUpload5.isEnabled = true
        btnUpload6.isEnabled = true
    }

    // ============================================================
    // RESET BUTTON AFTER CANCEL/ERROR
    // ============================================================

    private fun resetUploadButton() {

        isUploading = false

        enableAllUploadButtons()

        if (selectedRequirementNumber != 0) {

            val button =
                getButton(
                    selectedRequirementNumber
                )

            /*
             * If it was already uploaded, don't change
             * the text back to UPLOAD.
             */
            val alreadyUploaded =
                when (
                    selectedRequirementNumber
                ) {

                    1 -> file1Url != null

                    2 -> file2Url != null

                    3 -> file3Url != null

                    4 -> file4Url != null

                    5 -> file5Url != null

                    6 -> file6Url != null

                    else -> false
                }

            button.text =
                if (alreadyUploaded) {
                    "UPLOADED"
                } else {
                    "UPLOAD"
                }
        }
    }

    // ============================================================
    // UPLOAD ERROR
    // ============================================================

    private fun uploadFailed(
        button: Button,
        message: String
    ) {

        button.isEnabled =
            true

        button.text =
            "UPLOAD"

        isUploading =
            false

        enableAllUploadButtons()

        Toast.makeText(
            this,
            "Upload failed: $message",
            Toast.LENGTH_LONG
        ).show()
    }

    // ============================================================
    // SUBMIT BUTTON
    // ============================================================

    private fun updateSubmitButton() {

        val allUploaded =
            allRequirementsUploaded()

        btnSubmitRenewal.isEnabled =
            allUploaded

        if (allUploaded) {

            btnSubmitRenewal.text =
                "SUBMIT RENEWAL REQUEST"

        } else {

            btnSubmitRenewal.text =
                "UPLOAD ALL REQUIREMENTS"
        }
    }

    // ============================================================
    // CHECK ALL REQUIREMENTS
    // ============================================================

    private fun allRequirementsUploaded(): Boolean {

        return file1Url != null &&
                file2Url != null &&
                file3Url != null &&
                file4Url != null &&
                file5Url != null &&
                file6Url != null
    }

    // ============================================================
    // LOAD ORGANIZATION
    // ============================================================

    private fun loadOrganization() {

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization information is missing.",
                Toast.LENGTH_LONG
            ).show()

            btnSubmitRenewal.isEnabled =
                false

            return
        }

        db.collection("organizations")
            .document(organizationId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Organization not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                organizationName =
                    document.getString(
                        "orgName"
                    ) ?: organizationName

                organizationShortCode =
                    document.getString(
                        "shortCode"
                    ) ?: organizationShortCode

                adminUid =
                    document.getString(
                        "adminUid"
                    ) ?: adminUid

                val currentAcademicYear =
                    document.getString(
                        "academicYear"
                    ) ?: "2026-2027"

                tvOrganizationName.text =
                    organizationName

                tvOrganizationCode.text =
                    organizationShortCode

                tvCurrentAcademicYear.text =
                    currentAcademicYear

                tvRenewingAcademicYear.text =
                    getNextAcademicYear(
                        currentAcademicYear
                    )

                checkExistingRenewal(
                    currentAcademicYear,
                    tvRenewingAcademicYear
                        .text
                        .toString()
                )

                /*
                 * Load previously uploaded requirements
                 * from Firestore.
                 */
                loadUploadedRequirements(
                    tvRenewingAcademicYear
                        .text
                        .toString()
                )
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Failed to load organization: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // LOAD EXISTING UPLOADS
    // ============================================================

    private fun loadUploadedRequirements(
        academicYear: String
    ) {

        db.collection(
            "renewalRequirements"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "academicYear",
                academicYear
            )
            .get()
            .addOnSuccessListener { result ->

                for (
                document in result.documents
                ) {

                    val number =
                        document.getLong(
                            "requirementNumber"
                        )?.toInt()
                            ?: continue

                    val url =
                        document.getString(
                            "fileUrl"
                        )

                    if (
                        url.isNullOrBlank()
                    ) {
                        continue
                    }

                    setRequirementUrl(
                        number,
                        url
                    )

                    val button =
                        getButton(number)

                    button.text =
                        "UPLOADED"
                }

                updateSubmitButton()
            }
    }

    // ============================================================
    // CHECK EXISTING RENEWAL
    // ============================================================

    private fun checkExistingRenewal(
        currentAcademicYear: String,
        nextAcademicYear: String
    ) {

        db.collection(
            "organizationRenewals"
        )
            .whereEqualTo(
                "organizationId",
                organizationId
            )
            .whereEqualTo(
                "academicYear",
                nextAcademicYear
            )
            .limit(1)
            .get()
            .addOnSuccessListener { result ->

                if (result.isEmpty) {

                    tvStatus.visibility =
                        View.GONE

                    updateSubmitButton()

                    return@addOnSuccessListener
                }

                val renewal =
                    result.documents[0]

                val status =
                    renewal.getString(
                        "status"
                    ) ?: "pending"

                when (status) {

                    "pending" -> {

                        tvStatus.text =
                            "RENEWAL REQUEST UNDER REVIEW"

                        tvStatus.visibility =
                            View.VISIBLE

                        btnSubmitRenewal.text =
                            "REQUEST ALREADY SUBMITTED"

                        btnSubmitRenewal.isEnabled =
                            false
                    }

                    "approved" -> {

                        tvStatus.text =
                            "RENEWAL APPROVED"

                        tvStatus.visibility =
                            View.VISIBLE

                        btnSubmitRenewal.text =
                            "RENEWAL APPROVED"

                        btnSubmitRenewal.isEnabled =
                            false
                    }

                    "rejected" -> {

                        tvStatus.text =
                            "PREVIOUS REQUEST WAS REJECTED"

                        tvStatus.visibility =
                            View.VISIBLE

                        updateSubmitButton()
                    }

                    else -> {

                        tvStatus.visibility =
                            View.GONE

                        updateSubmitButton()
                    }
                }
            }
    }

    // ============================================================
    // SUBMIT RENEWAL
    // ============================================================

    private fun submitRenewalRequest() {

        if (!allRequirementsUploaded()) {

            Toast.makeText(
                this,
                "Please upload all 6 requirements first.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val currentUser =
            auth.currentUser

        val submittedBy =
            currentUser?.uid
                ?: adminUid

        if (submittedBy.isBlank()) {

            Toast.makeText(
                this,
                "Org Manager account information is missing.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val currentYear =
            tvCurrentAcademicYear
                .text
                .toString()

        val renewingYear =
            tvRenewingAcademicYear
                .text
                .toString()

        btnSubmitRenewal.isEnabled =
            false

        btnSubmitRenewal.text =
            "SUBMITTING..."

        val renewalRef =
            db.collection(
                "organizationRenewals"
            ).document()

        val renewalData =
            hashMapOf(
                "renewalId"
                        to renewalRef.id,

                "organizationId"
                        to organizationId,

                "organizationName"
                        to organizationName,

                "organizationShortCode"
                        to organizationShortCode,

                "adminUid"
                        to submittedBy,

                "academicYear"
                        to renewingYear,

                "previousAcademicYear"
                        to currentYear,

                "status"
                        to "pending",

                "submittedBy"
                        to submittedBy,

                "submittedAt"
                        to Timestamp.now(),

                "reviewedBy"
                        to "",

                "reviewedAt"
                        to null,

                "rejectionReason"
                        to "",

                "updatedAt"
                        to Timestamp.now()
            )

        renewalRef
            .set(renewalData)
            .addOnSuccessListener {

                tvStatus.text =
                    "RENEWAL REQUEST UNDER REVIEW"

                tvStatus.visibility =
                    View.VISIBLE

                btnSubmitRenewal.text =
                    "REQUEST ALREADY SUBMITTED"

                btnSubmitRenewal.isEnabled =
                    false

                Toast.makeText(
                    this,
                    "Renewal request submitted successfully.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { error ->

                updateSubmitButton()

                Toast.makeText(
                    this,
                    "Failed to submit renewal: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // NEXT ACADEMIC YEAR
    // ============================================================

    private fun getNextAcademicYear(
        academicYear: String
    ): String {

        return try {

            val parts =
                academicYear.split("-")

            if (parts.size == 2) {

                val startYear =
                    parts[0]
                        .trim()
                        .toInt()

                val nextStart =
                    startYear + 1

                val nextEnd =
                    nextStart + 1

                "$nextStart-$nextEnd"

            } else {

                "2027-2028"
            }

        } catch (e: Exception) {

            "2027-2028"
        }
    }
}
