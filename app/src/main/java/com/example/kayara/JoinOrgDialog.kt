package com.example.kayara

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase

/**
 * "Join organization" sheet.
 *
 *  - Kung CLOSED ang recruitment (o puno na ang member limit),
 *    ipakita ang notice ug dili makapadala ug request ang student.
 *  - Kung OPEN: ipakita ang recruitment instructions + files, ug ipa-upload
 *    sa student ang mga requirement nga gi-set sa admin.
 *  - Human ma-upload ang files, i-tawag ang onSubmit(...) aron ma-save
 *    ang membership request (kauban ang submittedDocuments).
 *
 * organizations/{id}:
 *   recruitmentOpen   (Boolean, default true)
 *   recruitmentLimit  (Number, 0 = walay limit)
 */
class JoinOrgDialog(
    private val fragment: Fragment,
    private val org: OrganizationModel,

    // Mopili ug file; i-tawag ang callback kung nakapili na
    private val requestFile: ((Uri) -> Unit) -> Unit,

    // (message, documents, dialog, submitButton)
    private val onSubmit: (
        String,
        List<Map<String, Any>>,
        Dialog,
        TextView
    ) -> Unit
) {

    private val db = SupabaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var requirements: List<RecruitmentRequirement> = emptyList()

    // requirementId -> gipili nga file
    private val pickedUris = mutableMapOf<String, Uri>()
    private val pickedNames = mutableMapOf<String, String>()

    private var loaded = false
    private var uploading = false
    private var closed = false

    // Resulta sa availability check
    private data class Availability(
        val allowed: Boolean,
        val title: String = "",
        val message: String = ""
    )

    fun show() {

        val context = fragment.requireContext()

        val dialog = Dialog(context)

        val view =
            LayoutInflater.from(context)
                .inflate(R.layout.dialog_join_org, null)

        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.window?.setGravity(Gravity.BOTTOM)

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        // -----------------------------------------------------
        // VIEWS
        // -----------------------------------------------------

        val tvTitle =
            view.findViewById<TextView>(R.id.tvJoinDialogTitle)

        val closedNotice =
            view.findViewById<LinearLayout>(R.id.closedNotice)

        val tvClosedTitle =
            view.findViewById<TextView>(R.id.tvClosedTitle)

        val tvClosedMessage =
            view.findViewById<TextView>(R.id.tvClosedMessage)

        val banner =
            view.findViewById<LinearLayout>(R.id.recruitmentInfoBanner)

        val tvInfo =
            view.findViewById<TextView>(R.id.tvRecruitmentInfo)

        val filesContainer =
            view.findViewById<LinearLayout>(R.id.recruitFilesContainer)

        val uploadSection =
            view.findViewById<LinearLayout>(R.id.uploadSection)

        val uploadContainer =
            view.findViewById<LinearLayout>(R.id.requirementUploadContainer)

        val tvMessageLabel =
            view.findViewById<TextView>(R.id.tvMessageLabel)

        val etMessage =
            view.findViewById<EditText>(R.id.etJoinMessage)

        val btnCancel =
            view.findViewById<TextView>(R.id.btnCancelJoin)

        val btnSubmit =
            view.findViewById<TextView>(R.id.btnSubmitJoin)

        val submitLabel =
            btnSubmit.text.toString()

        tvTitle.text = "Join ${org.orgName}"

        closedNotice.visibility = View.GONE
        banner.visibility = View.GONE
        uploadSection.visibility = View.GONE

        // Hulat una ang recruitment details
        setButtonEnabled(btnSubmit, false)

        btnCancel.setOnClickListener {

            if (!uploading) {
                dialog.dismiss()
            }
        }

        // -----------------------------------------------------
        // CLOSED / FULL STATE
        // -----------------------------------------------------

        fun showClosedState(
            title: String,
            message: String
        ) {

            closed = true
            loaded = true

            tvClosedTitle.text = title
            tvClosedMessage.text = message

            closedNotice.visibility = View.VISIBLE

            banner.visibility = View.GONE
            uploadSection.visibility = View.GONE
            tvMessageLabel.visibility = View.GONE
            etMessage.visibility = View.GONE

            btnSubmit.text = "Not available"

            setButtonEnabled(btnSubmit, false)

            btnCancel.text = "Close"
        }

        // -----------------------------------------------------
        // OPEN STATE (instructions, files, upload slots)
        // -----------------------------------------------------

        fun showOpenState(document: DocumentSnapshot) {

            val info =
                document.getString("recruitmentInfo")
                    ?.trim()
                    .orEmpty()

            val files =
                parseRecruitmentFiles(
                    document.get("recruitmentFiles")
                )

            requirements =
                parseRecruitmentRequirements(
                    document.get("recruitmentRequirements")
                )

            // Instructions + files
            if (info.isNotBlank() || files.isNotEmpty()) {

                banner.visibility = View.VISIBLE

                tvInfo.visibility =
                    if (info.isBlank()) View.GONE else View.VISIBLE

                tvInfo.text = info

                renderFiles(filesContainer, files)
            }

            // Upload slots
            if (requirements.isNotEmpty()) {

                uploadSection.visibility = View.VISIBLE

                renderUploadSlots(uploadContainer)
            }

            loaded = true

            setButtonEnabled(btnSubmit, true)
        }

        // -----------------------------------------------------
        // LOAD ORGANIZATION (latest gikan sa Firestore)
        // -----------------------------------------------------

        db.collection("organizations")
            .document(org.organizationId)
            .get()
            .addOnSuccessListener { document ->

                if (!fragment.isAdded) {
                    return@addOnSuccessListener
                }

                checkAvailability(document) { availability ->

                    if (!fragment.isAdded) {
                        return@checkAvailability
                    }

                    if (availability.allowed) {
                        showOpenState(document)
                    } else {
                        showClosedState(
                            availability.title,
                            availability.message
                        )
                    }
                }
            }
            .addOnFailureListener {

                if (!fragment.isAdded) {
                    return@addOnFailureListener
                }

                loaded = true

                setButtonEnabled(btnSubmit, true)

                Toast.makeText(
                    fragment.requireContext(),
                    "Unable to load recruitment details.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        // -----------------------------------------------------
        // SUBMIT
        // -----------------------------------------------------

        fun proceedToSubmit() {

            val message =
                etMessage.text.toString().trim()

            // Walay i-upload
            if (pickedUris.isEmpty()) {

                onSubmit(
                    message,
                    emptyList(),
                    dialog,
                    btnSubmit
                )

                return
            }

            uploadAndSubmit(
                dialog,
                btnSubmit,
                submitLabel,
                message
            )
        }

        btnSubmit.setOnClickListener {

            if (uploading || !loaded || closed) {
                return@setOnClickListener
            }

            val missing =
                requirements.firstOrNull {
                    it.required && !pickedUris.containsKey(it.id)
                }

            if (missing != null) {

                Toast.makeText(
                    context,
                    "Please upload: ${missing.title}",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            // I-check balik kung open pa gihapon ang recruitment
            // (basin gi-close o napuno na samtang naghulat ang student)
            setButtonEnabled(btnSubmit, false)

            db.collection("organizations")
                .document(org.organizationId)
                .get()
                .addOnSuccessListener { document ->

                    if (!fragment.isAdded) {
                        return@addOnSuccessListener
                    }

                    checkAvailability(document) { availability ->

                        if (!fragment.isAdded) {
                            return@checkAvailability
                        }

                        if (availability.allowed) {

                            setButtonEnabled(btnSubmit, true)

                            proceedToSubmit()

                        } else {

                            showClosedState(
                                availability.title,
                                availability.message
                            )
                        }
                    }
                }
                .addOnFailureListener {

                    if (!fragment.isAdded) {
                        return@addOnFailureListener
                    }

                    // Dili i-block kung napakyas ang pag-check
                    setButtonEnabled(btnSubmit, true)

                    proceedToSubmit()
                }
        }

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // =========================================================
    // AVAILABILITY: OPEN / CLOSED / FULL
    // =========================================================

    private fun checkAvailability(
        document: DocumentSnapshot,
        onDone: (Availability) -> Unit
    ) {

        val isOpen =
            document.getBoolean("recruitmentOpen") ?: true

        if (!isOpen) {

            onDone(
                Availability(
                    allowed = false,
                    title = "Recruitment is closed",
                    message =
                        "${org.orgName} is not accepting new members " +
                                "right now. Please check again later."
                )
            )

            return
        }

        val limit =
            document.getLong("recruitmentLimit") ?: 0L

        // Walay limit
        if (limit <= 0L) {

            onDone(Availability(allowed = true))

            return
        }

        // Ihap ang approved members
        db.collection("memberships")
            .whereEqualTo("organizationId", org.organizationId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.size() >= limit) {

                    onDone(
                        Availability(
                            allowed = false,
                            title = "This organization is full",
                            message =
                                "${org.orgName} has reached its limit of " +
                                        "$limit members and is no longer accepting applications."
                        )
                    )

                } else {

                    onDone(Availability(allowed = true))
                }
            }
            .addOnFailureListener {

                // Dili i-block kung napakyas ang pag-ihap
                onDone(Availability(allowed = true))
            }
    }

    // =========================================================
    // FILES NGA GI-ATTACH SA ADMIN (i-tap aron ablihan)
    // =========================================================

    private fun renderFiles(
        container: LinearLayout,
        files: List<RecruitmentFile>
    ) {

        container.removeAllViews()

        val context = fragment.requireContext()

        val inflater = LayoutInflater.from(context)

        files.forEach { file ->

            val row =
                inflater.inflate(
                    R.layout.item_recruit_file,
                    container,
                    false
                )

            row.findViewById<TextView>(R.id.tvFileName).text =
                file.name

            // Student dili makakuha sa remove button
            row.findViewById<View>(R.id.btnRemoveFile)
                .visibility = View.GONE

            row.setOnClickListener {
                openUrl(context, file.url)
            }

            container.addView(row)
        }
    }

    // =========================================================
    // UPLOAD SLOTS PARA SA STUDENT
    // =========================================================

    private fun renderUploadSlots(
        container: LinearLayout
    ) {

        container.removeAllViews()

        val context = fragment.requireContext()

        val inflater = LayoutInflater.from(context)

        requirements.forEach { requirement ->

            val row =
                inflater.inflate(
                    R.layout.item_recruite_upload,
                    container,
                    false
                )

            row.findViewById<TextView>(R.id.tvReqTitle).text =
                requirement.title

            styleRequirementTag(
                row.findViewById(R.id.tvReqRequired),
                requirement.required
            )

            val tvFile =
                row.findViewById<TextView>(R.id.tvReqFile)

            val btnUpload =
                row.findViewById<TextView>(R.id.btnReqUpload)

            btnUpload.setOnClickListener {

                if (uploading) {
                    return@setOnClickListener
                }

                requestFile { uri ->

                    if (!fragment.isAdded) {
                        return@requestFile
                    }

                    val name =
                        getFileName(context, uri)

                    pickedUris[requirement.id] = uri
                    pickedNames[requirement.id] = name

                    tvFile.text = name

                    tvFile.setTextColor(
                        context.getColor(R.color.blue_deep)
                    )

                    btnUpload.text = "Change"
                }
            }

            container.addView(row)
        }
    }

    // =========================================================
    // UPLOAD FILES -> SUBMIT REQUEST
    // =========================================================

    private fun uploadAndSubmit(
        dialog: Dialog,
        btnSubmit: TextView,
        submitLabel: String,
        message: String
    ) {

        val context = fragment.requireContext()

        val uid =
            auth.currentUser?.uid ?: ""

        val entries =
            pickedUris.entries.toList()

        val total = entries.size

        val results = mutableMapOf<String, String>()

        var completed = 0
        var failed = false

        uploading = true

        dialog.setCancelable(false)

        setButtonEnabled(btnSubmit, false)

        btnSubmit.text = "Uploading 0/$total"

        entries.forEach { entry ->

            val requirementId = entry.key

            uploadFileToCloudinary(
                uri = entry.value,
                folder = "kayara/applications/${org.organizationId}/$uid",

                onSuccess = { url ->

                    if (!failed) {

                        results[requirementId] = url

                        completed++

                        btnSubmit.text = "Uploading $completed/$total"

                        if (completed == total) {

                            uploading = false

                            dialog.setCancelable(true)

                            btnSubmit.text = submitLabel

                            val documents =
                                requirements
                                    .filter { results.containsKey(it.id) }
                                    .map { requirement ->

                                        mapOf<String, Any>(
                                            "requirementId" to requirement.id,
                                            "title" to requirement.title,
                                            "fileName" to
                                                    (pickedNames[requirement.id] ?: ""),
                                            "url" to
                                                    (results[requirement.id] ?: "")
                                        )
                                    }

                            onSubmit(
                                message,
                                documents,
                                dialog,
                                btnSubmit
                            )
                        }
                    }
                },

                onError = { error ->

                    if (!failed) {

                        failed = true
                        uploading = false

                        dialog.setCancelable(true)

                        btnSubmit.text = submitLabel

                        setButtonEnabled(btnSubmit, true)

                        Toast.makeText(
                            context,
                            "Upload failed: $error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    private fun setButtonEnabled(
        button: TextView,
        enabled: Boolean
    ) {

        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.5f
    }
}