package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.kayara.data.FieldValue
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.SetOptions
import java.util.UUID

/**
 * I-edit ang profile sa organization + recruitment.
 *
 * Firestore: organizations/{organizationId}
 *   orgName, organizationName, category, description, founded
 *   recruitmentInfo          (instructions para sa applicants)
 *   recruitmentFiles         [ { name, url } ]
 *   recruitmentRequirements  [ { id, title, required } ]
 */
class EditOrgProfileActivity : AppCompatActivity() {

    companion object {
        private const val MAX_FILES = 5
        private const val MAX_REQUIREMENTS = 8
    }

    private val db = SupabaseDatabase.getInstance()

    // ---------------------------------------------------------
    // VIEWS
    // ---------------------------------------------------------

    private lateinit var etOrgName: EditText
    private lateinit var etOrgCategory: EditText
    private lateinit var etOrgDescription: EditText
    private lateinit var etOrgFounded: EditText
    private lateinit var etRecruitmentInfo: EditText

    private lateinit var filesContainer: LinearLayout
    private lateinit var tvFilesEmpty: TextView
    private lateinit var tvUploadStatus: TextView
    private lateinit var btnAddRecruitFile: TextView

    private lateinit var requirementsContainer: LinearLayout
    private lateinit var tvRequirementsEmpty: TextView
    private lateinit var btnAddRequirement: TextView

    private lateinit var btnSaveProfile: LinearLayout
    private lateinit var tvSaveLabel: TextView

    // Recruitment status (OPEN / CLOSED) + member limit
    private lateinit var switchRecruitmentOpen: SwitchCompat
    private lateinit var tvRecruitmentStatus: TextView
    private lateinit var tvRecruitmentStatusHint: TextView
    private lateinit var etRecruitmentLimit: EditText
    private lateinit var tvRecruitmentCount: TextView

    private lateinit var etOrgAdviser1: EditText
    private lateinit var etOrgAdviser2: EditText

    // ---------------------------------------------------------
    // STATE
    // ---------------------------------------------------------

    private var organizationId = ""

    // Ngan sa org sa wala pa i-edit (para mahibaw-an kung nausab)
    private var originalName = ""

    // Initial values gikan sa Intent
    private var initialName = ""
    private var initialCategory = ""
    private var initialDescription = ""
    private var initialFounded = ""

    private var initialAdviser1 = ""
    private var initialAdviser2 = ""

    private val recruitmentFiles = mutableListOf<RecruitmentFile>()
    private val requirements = mutableListOf<RecruitmentRequirement>()

    private var isLoaded = false
    private var recruitmentLoaded = false
    private var recruitmentTouched = false
    private var isUploading = false
    private var isSaving = false

    // ---------------------------------------------------------
    // FILE PICKER
    // ---------------------------------------------------------

    private val pickFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                uploadAttachment(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_org_profile)

        etOrgName = findViewById(R.id.etEditOrgName)
        etOrgCategory = findViewById(R.id.etEditOrgCategory)
        etOrgDescription = findViewById(R.id.etEditOrgDescription)
        etOrgFounded = findViewById(R.id.etEditOrgFounded)
        etOrgAdviser1 =
            findViewById(R.id.etEditOrgAdviser1)

        etOrgAdviser2 =
            findViewById(R.id.etEditOrgAdviser2)
        etRecruitmentInfo = findViewById(R.id.etEditRecruitmentInfo)

        filesContainer = findViewById(R.id.filesContainer)
        tvFilesEmpty = findViewById(R.id.tvFilesEmpty)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        btnAddRecruitFile = findViewById(R.id.btnAddRecruitFile)

        requirementsContainer = findViewById(R.id.requirementsContainer)
        tvRequirementsEmpty = findViewById(R.id.tvRequirementsEmpty)
        btnAddRequirement = findViewById(R.id.btnAddRequirement)

        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        tvSaveLabel = findViewById(R.id.tvSaveLabel)

        switchRecruitmentOpen = findViewById(R.id.switchRecruitmentOpen)
        tvRecruitmentStatus = findViewById(R.id.tvRecruitmentStatus)
        tvRecruitmentStatusHint = findViewById(R.id.tvRecruitmentStatusHint)
        etRecruitmentLimit = findViewById(R.id.etRecruitmentLimit)
        tvRecruitmentCount = findViewById(R.id.tvRecruitmentCount)

        findViewById<ImageView>(R.id.btnBack)
            .setOnClickListener { finish() }

        organizationId =
            intent.getStringExtra("organizationId")?.trim() ?: ""

        // =====================================================
        // PREFILL (Intent), dayon i-refresh gikan sa Firestore
        // =====================================================

        initialName = intent.getStringExtra("orgName") ?: ""
        initialCategory = intent.getStringExtra("category") ?: ""
        initialDescription = intent.getStringExtra("description") ?: ""
        initialFounded = intent.getStringExtra("founded") ?: ""
        initialAdviser1 =
            intent.getStringExtra("adviserName1")
                ?: intent.getStringExtra("adviserName")
                        ?: ""

        initialAdviser2 =
            intent.getStringExtra("adviserName2")
                ?: ""

        originalName = initialName

        etOrgName.setText(initialName)
        etOrgCategory.setText(initialCategory)
        etOrgDescription.setText(initialDescription)
        etOrgFounded.setText(initialFounded)
        etOrgAdviser1.setText(initialAdviser1)
        etOrgAdviser2.setText(initialAdviser2)

        renderFiles()
        renderRequirements()

        btnAddRecruitFile.setOnClickListener {
            attachFile()
        }

        btnAddRequirement.setOnClickListener {
            showAddRequirementDialog()
        }

        // OPEN / CLOSED
        switchRecruitmentOpen.setOnCheckedChangeListener { _, isChecked ->

            updateRecruitmentStatusText(isChecked)

            recruitmentTouched = true
        }

        etRecruitmentLimit.addTextChangedListener(
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
                    recruitmentTouched = true
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        updateSaveState()

        loadFromFirestore()

        loadMemberCount()
    }

    // =========================================================
    // LOAD LATEST DATA FROM FIRESTORE
    // =========================================================

    private fun loadFromFirestore() {

        if (organizationId.isBlank()) {

            isLoaded = true
            updateSaveState()

            return
        }

        db.collection("organizations")
            .document(organizationId)
            .get()
            .addOnSuccessListener { document ->

                if (isDestroyed) {
                    return@addOnSuccessListener
                }

                if (document.exists()) {

                    val savedName =
                        document.getString("orgName")
                            ?.takeIf { it.isNotBlank() }
                            ?: document.getString("organizationName")
                                ?.takeIf { it.isNotBlank() }
                            ?: ""

                    if (savedName.isNotBlank()) {
                        originalName = savedName
                    }

                    fillIfUnchanged(etOrgName, initialName, savedName)

                    fillIfUnchanged(
                        etOrgCategory,
                        initialCategory,
                        document.getString("category") ?: ""
                    )

                    fillIfUnchanged(
                        etOrgDescription,
                        initialDescription,
                        document.getString("description") ?: ""
                    )

                    fillIfUnchanged(
                        etOrgFounded,
                        initialFounded,
                        document.getString("founded") ?: ""
                    )

                    val savedAdviser1 =
                        document.getString("adviserName1")
                            ?.takeIf { it.isNotBlank() }
                            ?: document.getString("adviserName")
                                ?.takeIf { it.isNotBlank() }
                            ?: ""

                    val savedAdviser2 =
                        document.getString("adviserName2")
                            ?: ""

                    fillIfUnchanged(
                        etOrgAdviser1,
                        initialAdviser1,
                        savedAdviser1
                    )

                    fillIfUnchanged(
                        etOrgAdviser2,
                        initialAdviser2,
                        savedAdviser2
                    )

                    if (etRecruitmentInfo.text.isNullOrBlank()) {
                        etRecruitmentInfo.setText(
                            document.getString("recruitmentInfo") ?: ""
                        )
                    }

                    recruitmentFiles.clear()
                    recruitmentFiles.addAll(
                        parseRecruitmentFiles(
                            document.get("recruitmentFiles")
                        )
                    )

                    requirements.clear()
                    requirements.addAll(
                        parseRecruitmentRequirements(
                            document.get("recruitmentRequirements")
                        )
                    )

                    renderFiles()
                    renderRequirements()

                    // Recruitment status (default: OPEN)
                    val isOpen =
                        document.getBoolean("recruitmentOpen") ?: true

                    switchRecruitmentOpen.isChecked = isOpen

                    updateRecruitmentStatusText(isOpen)

                    val limit =
                        document.getLong("recruitmentLimit") ?: 0L

                    if (limit > 0L && etRecruitmentLimit.text.isNullOrBlank()) {
                        etRecruitmentLimit.setText(limit.toString())
                    }
                }

                recruitmentLoaded = true
                isLoaded = true
                updateSaveState()
            }
            .addOnFailureListener {

                if (isDestroyed) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    this,
                    "Unable to load saved recruitment details.",
                    Toast.LENGTH_SHORT
                ).show()

                // Pwede gihapon mo-save sa profile, pero dili ma-overwrite
                // ang recruitment details kung wala nimo gi-usab.
                isLoaded = true
                updateSaveState()
            }
    }

    // I-update lang ang field kung wala pa gi-usab sa user
    private fun fillIfUnchanged(
        editText: EditText,
        initialValue: String,
        savedValue: String
    ) {

        if (savedValue.isBlank()) {
            return
        }

        if (editText.text.toString() == initialValue) {
            editText.setText(savedValue)
        }
    }

    // =========================================================
    // FILES FOR APPLICANTS
    // =========================================================

    private fun attachFile() {

        if (isUploading) {
            return
        }

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization not found. Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (recruitmentFiles.size >= MAX_FILES) {

            Toast.makeText(
                this,
                "You can attach up to $MAX_FILES files.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        pickFileLauncher.launch(DOCUMENT_MIME_TYPES)
    }

    private fun uploadAttachment(
        uri: android.net.Uri
    ) {

        val fileName = getFileName(this, uri)

        setUploading(true, "Uploading $fileName...")

        uploadFileToCloudinary(
            uri = uri,
            folder = "kayara/recruitment/$organizationId",

            onSuccess = { url ->

                if (!isDestroyed) {

                    recruitmentFiles.add(
                        RecruitmentFile(
                            name = fileName,
                            url = url
                        )
                    )

                    recruitmentTouched = true

                    renderFiles()

                    setUploading(false, "")

                    Toast.makeText(
                        this,
                        "File attached. Tap Save changes to publish.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },

            onError = { message ->

                if (!isDestroyed) {

                    setUploading(false, "")

                    Toast.makeText(
                        this,
                        "Upload failed: $message",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun setUploading(
        uploading: Boolean,
        message: String
    ) {

        isUploading = uploading

        tvUploadStatus.visibility =
            if (uploading) View.VISIBLE else View.GONE

        tvUploadStatus.text = message

        btnAddRecruitFile.isEnabled = !uploading
        btnAddRecruitFile.alpha = if (uploading) 0.5f else 1f

        updateSaveState()
    }

    private fun renderFiles() {

        filesContainer.removeAllViews()

        tvFilesEmpty.visibility =
            if (recruitmentFiles.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)

        recruitmentFiles.toList().forEach { file ->

            val row =
                inflater.inflate(
                    R.layout.item_recruit_file,
                    filesContainer,
                    false
                )

            row.findViewById<TextView>(R.id.tvFileName).text =
                file.name

            row.setOnClickListener {
                openUrl(this, file.url)
            }

            row.findViewById<TextView>(R.id.btnRemoveFile)
                .setOnClickListener {

                    recruitmentFiles.remove(file)

                    recruitmentTouched = true

                    renderFiles()
                }

            filesContainer.addView(row)
        }
    }

    // =========================================================
    // REQUIREMENTS (unsay i-upload sa student)
    // =========================================================

    private fun showAddRequirementDialog() {

        if (requirements.size >= MAX_REQUIREMENTS) {

            Toast.makeText(
                this,
                "You can add up to $MAX_REQUIREMENTS requirements.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val container = LinearLayout(this)

        container.orientation = LinearLayout.VERTICAL
        container.setPadding(60, 24, 60, 0)

        val input = EditText(this)

        input.hint = "e.g. Application Form, Resume, 2x2 Photo"
        input.setSingleLine(true)
        input.filters = arrayOf(InputFilter.LengthFilter(60))

        val checkRequired = CheckBox(this)

        checkRequired.text = "Required"
        checkRequired.isChecked = true

        container.addView(input)
        container.addView(checkRequired)

        AlertDialog.Builder(this)
            .setTitle("Add requirement")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->

                val title = input.text.toString().trim()

                if (title.isEmpty()) {

                    Toast.makeText(
                        this,
                        "Please enter a name for the requirement.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    requirements.add(
                        RecruitmentRequirement(
                            id = "req_" + UUID.randomUUID().toString().take(8),
                            title = title,
                            required = checkRequired.isChecked
                        )
                    )

                    recruitmentTouched = true

                    renderRequirements()
                }
            }
            .show()
    }

    private fun renderRequirements() {

        requirementsContainer.removeAllViews()

        tvRequirementsEmpty.visibility =
            if (requirements.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)

        requirements.toList().forEach { requirement ->

            val row =
                inflater.inflate(
                    R.layout.item_recruite_requirement,
                    requirementsContainer,
                    false
                )

            row.findViewById<TextView>(R.id.tvReqTitle).text =
                requirement.title

            val tag =
                row.findViewById<TextView>(R.id.tvReqRequired)

            styleRequirementTag(tag, requirement.required)

            // I-tap ang tag aron usbon ang REQUIRED / OPTIONAL
            tag.setOnClickListener {

                val index = requirements.indexOf(requirement)

                if (index >= 0) {

                    requirements[index] =
                        requirement.copy(
                            required = !requirement.required
                        )

                    recruitmentTouched = true

                    renderRequirements()
                }
            }

            row.findViewById<TextView>(R.id.btnRemoveReq)
                .setOnClickListener {

                    requirements.remove(requirement)

                    recruitmentTouched = true

                    renderRequirements()
                }

            requirementsContainer.addView(row)
        }
    }

    // =========================================================
    // RECRUITMENT STATUS
    // =========================================================

    private fun updateRecruitmentStatusText(isOpen: Boolean) {

        if (isOpen) {

            tvRecruitmentStatus.text = "Recruitment is OPEN"

            tvRecruitmentStatusHint.text =
                "Students can send join requests."

            tvRecruitmentStatus.setTextColor(
                getColor(R.color.dark_blue)
            )

        } else {

            tvRecruitmentStatus.text = "Recruitment is CLOSED"

            tvRecruitmentStatusHint.text =
                "Students will see that you are not accepting new members."

            tvRecruitmentStatus.setTextColor(
                getColor(R.color.coral_text)
            )
        }
    }

    // Ihap ang approved members karon (para sa member limit)
    private fun loadMemberCount() {

        if (organizationId.isBlank()) {
            return
        }

        db.collection("memberships")
            .whereEqualTo("organizationId", organizationId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snapshot ->

                if (!isDestroyed) {
                    tvRecruitmentCount.text =
                        "Current members: ${snapshot.size()}"
                }
            }
    }

    // =========================================================
    // SAVE TO FIRESTORE
    // =========================================================

    private fun saveProfile() {

        if (isSaving || isUploading || !isLoaded) {
            return
        }

        val orgName = etOrgName.text.toString().trim()
        val category = etOrgCategory.text.toString().trim()
        val description = etOrgDescription.text.toString().trim()
        val founded = etOrgFounded.text.toString().trim()
        val adviserName1 =
            etOrgAdviser1.text.toString().trim()

        val adviserName2 =
            etOrgAdviser2.text.toString().trim()
        val recruitmentInfo = etRecruitmentInfo.text.toString().trim()

        if (orgName.isEmpty()) {

            Toast.makeText(
                this,
                "Organization name cannot be empty",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization not found. Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        isSaving = true
        updateSaveState()

        val updates =
            hashMapOf<String, Any>(
                "orgName" to orgName,
                "organizationName" to orgName,
                "category" to category,
                "description" to description,
                "founded" to founded,

                "adviserName" to adviserName1,
                "adviserName1" to adviserName1,
                "adviserName2" to adviserName2,

                "updatedAt" to FieldValue.serverTimestamp()
            )

        // Dili ma-overwrite ang karaan nga recruitment details
        // kung napakyas ang pag-load ug wala pa nimo gi-usab.
        if (
            recruitmentLoaded ||
            recruitmentTouched ||
            recruitmentInfo.isNotBlank()
        ) {

            updates["recruitmentInfo"] = recruitmentInfo

            // OPEN / CLOSED + member limit (0 = walay limit)
            updates["recruitmentOpen"] =
                switchRecruitmentOpen.isChecked

            updates["recruitmentLimit"] =
                etRecruitmentLimit.text.toString()
                    .trim()
                    .toLongOrNull()
                    ?: 0L

            updates["recruitmentFiles"] =
                recruitmentFiles.map { file ->
                    mapOf(
                        "name" to file.name,
                        "url" to file.url
                    )
                }

            updates["recruitmentRequirements"] =
                requirements.map { requirement ->
                    mapOf(
                        "id" to requirement.id,
                        "title" to requirement.title,
                        "required" to requirement.required
                    )
                }
        }

        db.collection("organizations")
            .document(organizationId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {

                // Kung nausab ang ngan, i-update pud ang mga kopya niini
                if (orgName != originalName) {
                    syncOrganizationName(orgName)
                }

                Toast.makeText(
                    this,
                    "Profile updated successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                val resultIntent = Intent()
                resultIntent.putExtra("orgName", orgName)
                resultIntent.putExtra("category", category)
                resultIntent.putExtra("description", description)
                resultIntent.putExtra("founded", founded)
                resultIntent.putExtra("recruitmentInfo", recruitmentInfo)

                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .addOnFailureListener { error ->

                isSaving = false
                updateSaveState()

                Toast.makeText(
                    this,
                    "Failed to save profile: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // =========================================================
    // SYNC ORGANIZATION NAME (memberships, posts, users)
    // =========================================================

    private fun syncOrganizationName(newName: String) {

        val orgId = organizationId

        for (collection in listOf("memberships", "posts", "users")) {

            db.collection(collection)
                .whereEqualTo("organizationId", orgId)
                .get()
                .addOnSuccessListener { snapshot ->

                    snapshot.documents
                        .chunked(400)
                        .forEach { chunk ->

                            val batch = db.batch()

                            chunk.forEach { document ->
                                batch.update(
                                    document.reference,
                                    "organizationName",
                                    newName
                                )
                            }

                            batch.commit()
                        }
                }
        }
    }

    // =========================================================
    // SAVE BUTTON STATE
    // =========================================================

    private fun updateSaveState() {

        val enabled =
            isLoaded && !isUploading && !isSaving

        btnSaveProfile.isEnabled = enabled
        btnSaveProfile.alpha = if (enabled) 1f else 0.5f

        tvSaveLabel.text =
            when {
                isSaving -> "Saving..."
                isUploading -> "Uploading file..."
                else -> "Save changes"
            }
    }
}