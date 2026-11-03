package com.example.kayara

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase

class AdminResubmitActivity : AppCompatActivity() {

    private lateinit var tvDocument1: TextView
    private lateinit var tvDocument2: TextView
    private lateinit var tvDocument3: TextView

    private lateinit var btnSubmit: Button
    private lateinit var btnBack: TextView

    private val db =
        SupabaseDatabase.getInstance()

    private val auth =
        FirebaseAuth.getInstance()

    private var applicationId = ""
    private var uid = ""

    private var document1Uri: Uri? = null
    private var document2Uri: Uri? = null
    private var document3Uri: Uri? = null

    private var currentDocumentSlot = 0

    // =========================================================
    // FILE PICKER
    // =========================================================

    private val documentPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            when (currentDocumentSlot) {

                1 -> {
                    document1Uri = uri
                    tvDocument1.text =
                        "Document 1 selected"
                }

                2 -> {
                    document2Uri = uri
                    tvDocument2.text =
                        "Document 2 selected"
                }

                3 -> {
                    document3Uri = uri
                    tvDocument3.text =
                        "Document 3 selected"
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_admin_resubmit
        )

        applicationId =
            intent.getStringExtra(
                "applicationId"
            ) ?: ""

        uid =
            intent.getStringExtra(
                "uid"
            ) ?: ""

        initializeViews()
        setupButtons()
    }

    // =========================================================
    // VIEWS
    // =========================================================

    private fun initializeViews() {

        tvDocument1 =
            findViewById(R.id.tvDocument1)

        tvDocument2 =
            findViewById(R.id.tvDocument2)

        tvDocument3 =
            findViewById(R.id.tvDocument3)

        btnSubmit =
            findViewById(R.id.btnSubmitResubmit)

        btnBack =
            findViewById(R.id.btnBack)

        if (
            applicationId.isBlank() ||
            uid.isBlank()
        ) {

            Toast.makeText(
                this,
                "Application information is missing.",
                Toast.LENGTH_LONG
            ).show()

            finish()

            return
        }

        // Make sure Firebase Auth is still active
        if (auth.currentUser == null) {

            Toast.makeText(
                this,
                "Your login session has expired. Please login again.",
                Toast.LENGTH_LONG
            ).show()

            finish()
        }
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private fun setupButtons() {

        btnBack.setOnClickListener {
            finish()
        }

        findViewById<Button>(
            R.id.btnChooseDocument1
        ).setOnClickListener {

            currentDocumentSlot = 1

            documentPicker.launch("*/*")
        }

        findViewById<Button>(
            R.id.btnChooseDocument2
        ).setOnClickListener {

            currentDocumentSlot = 2

            documentPicker.launch("*/*")
        }

        findViewById<Button>(
            R.id.btnChooseDocument3
        ).setOnClickListener {

            currentDocumentSlot = 3

            documentPicker.launch("*/*")
        }

        btnSubmit.setOnClickListener {

            validateAndResubmit()
        }
    }

    // =========================================================
    // VALIDATE
    // =========================================================

    private fun validateAndResubmit() {

        if (auth.currentUser == null) {

            Toast.makeText(
                this,
                "Your login session has expired. Please login again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (
            document1Uri == null ||
            document2Uri == null ||
            document3Uri == null
        ) {

            Toast.makeText(
                this,
                "Please select all 3 required documents.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = "Uploading..."

        uploadDocuments()
    }

    // =========================================================
    // UPLOAD ALL 3 DOCUMENTS
    // =========================================================

    private fun uploadDocuments() {

        val uri1 = document1Uri!!
        val uri2 = document2Uri!!
        val uri3 = document3Uri!!

        val results =
            arrayOfNulls<UploadResult>(3)

        uploadToCloudinary(
            uri1,
            1,
            results
        ) {

            uploadToCloudinary(
                uri2,
                2,
                results
            ) {

                uploadToCloudinary(
                    uri3,
                    3,
                    results
                ) {

                    saveResubmission(
                        results
                    )
                }
            }
        }
    }

    // =========================================================
    // CLOUDINARY UPLOAD
    // =========================================================

    private fun uploadToCloudinary(
        uri: Uri,
        documentNumber: Int,
        results: Array<UploadResult?>,
        onComplete: () -> Unit
    ) {

        val publicId =
            "${applicationId}_resubmission_document$documentNumber"

        MediaManager.get()
            .upload(uri)
            .unsigned("kayara_upload")
            .option(
                "folder",
                "kayara/admin_applications"
            )
            .option(
                "public_id",
                publicId
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
                        resultData: Map<*, *>?
                    ) {

                        val secureUrl =
                            resultData
                                ?.get("secure_url")
                                ?.toString()

                        if (
                            secureUrl.isNullOrBlank()
                        ) {

                            uploadFailed(
                                "Cloudinary did not return an image URL."
                            )

                            return
                        }

                        results[
                            documentNumber - 1
                        ] =
                            UploadResult(
                                secureUrl,
                                publicId
                            )

                        onComplete()
                    }

                    override fun onError(
                        requestId: String?,
                        error: ErrorInfo?
                    ) {

                        uploadFailed(
                            error?.description
                                ?: "Cloudinary upload failed."
                        )
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
    // SAVE TO FIRESTORE
    // =========================================================

    private fun saveResubmission(
        results: Array<UploadResult?>
    ) {

        val document1 =
            results[0]

        val document2 =
            results[1]

        val document3 =
            results[2]

        if (
            document1 == null ||
            document2 == null ||
            document3 == null
        ) {

            uploadFailed(
                "One or more documents failed to upload."
            )

            return
        }

        // =====================================================
        // IMPORTANT AUTH CHECK
        // =====================================================

        if (auth.currentUser == null) {

            uploadFailed(
                "Your login session has expired. Please login again."
            )

            return
        }

        btnSubmit.text =
            "Submitting..."

        val documents =
            listOf(

                hashMapOf(
                    "key" to "document1",
                    "label" to "Required Document 1",
                    "url" to document1.url,
                    "publicId" to document1.publicId
                ),

                hashMapOf(
                    "key" to "document2",
                    "label" to "Required Document 2",
                    "url" to document2.url,
                    "publicId" to document2.publicId
                ),

                hashMapOf(
                    "key" to "document3",
                    "label" to "Required Document 3",
                    "url" to document3.url,
                    "publicId" to document3.publicId
                )
            )

        val applicationUpdates =
            hashMapOf<String, Any>(

                "status" to "pending",

                "rejectionReason" to "",

                "documents" to documents,

                // Compatibility with old records
                "documentUrl" to document1.url,

                "documentPublicId" to document1.publicId,

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        val userUpdates =
            hashMapOf<String, Any>(

                "status" to "pending",

                "rejectionReason" to "",

                "updatedAt" to
                        FieldValue.serverTimestamp()
            )

        val batch =
            db.batch()

        val applicationRef =
            db.collection("adminApplications")
                .document(applicationId)

        val userRef =
            db.collection("users")
                .document(uid)

        batch.update(
            applicationRef,
            applicationUpdates
        )

        batch.update(
            userRef,
            userUpdates
        )

        // =====================================================
        // COMMIT
        // =====================================================

        batch.commit()
            .addOnSuccessListener {

                /*
                 * Only sign out AFTER Firestore succeeds.
                 */

                auth.signOut()

                Toast.makeText(
                    this,
                    "Application resubmitted successfully. Please wait for Coordinator approval.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
            .addOnFailureListener { error ->

                btnSubmit.isEnabled = true

                btnSubmit.text =
                    "Submit Application Again"

                Toast.makeText(
                    this,
                    "Failed to resubmit: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // UPLOAD ERROR
    // =========================================================

    private fun uploadFailed(
        message: String =
            "Failed to upload documents."
    ) {

        runOnUiThread {

            btnSubmit.isEnabled = true

            btnSubmit.text =
                "Submit Application Again"

            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // UPLOAD RESULT
    // =========================================================

    data class UploadResult(
        val url: String,
        val publicId: String
    )
}