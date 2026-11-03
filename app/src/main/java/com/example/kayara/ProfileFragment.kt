package com.example.kayara

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.example.kayara.data.DocumentSnapshot
import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileFragment : Fragment() {

    // =========================================================
    // FIREBASE
    // =========================================================

    private lateinit var auth: FirebaseAuth
    private lateinit var db: SupabaseDatabase

    private var accessListener: ListenerRegistration? = null

    private var membershipsListener: ListenerRegistration? = null
    private var organizationsListener: ListenerRegistration? = null

    private val maxOrganizations = 3

    // FIX: tinuod nga organization IDs ug latest memberships
    private val validOrganizationIds = mutableSetOf<String>()
    private var membershipDocs: List<DocumentSnapshot> = emptyList()

    private var profileUserId: String = ""
    private var viewOnly: Boolean = false

    // =========================================================
    // PROFILE VIEWS
    // =========================================================

    private lateinit var ivProfile: ShapeableImageView
    private lateinit var tvProfileInitials: TextView

    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileCourse: TextView
    private lateinit var tvProfileStudentId: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvProfileContact: TextView
    private lateinit var tvProfileYearLevel: TextView

    private lateinit var membershipContainer: LinearLayout

    // =========================================================
    // IMAGE PICKER
    // =========================================================

    companion object {

        private const val ARG_PROFILE_USER_ID =
            "profileUserId"

        private const val ARG_VIEW_ONLY =
            "viewOnly"

        fun newInstance(
            studentUid: String,
            viewOnly: Boolean = true
        ): ProfileFragment {

            return ProfileFragment().apply {

                arguments =
                    Bundle().apply {

                        putString(
                            ARG_PROFILE_USER_ID,
                            studentUid
                        )

                        putBoolean(
                            ARG_VIEW_ONLY,
                            viewOnly
                        )
                    }
            }
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        profileUserId =
            arguments
                ?.getString(
                    ARG_PROFILE_USER_ID
                )
                ?.trim()
                .orEmpty()

        viewOnly =
            arguments
                ?.getBoolean(
                    ARG_VIEW_ONLY,
                    false
                )
                ?: false
    }

    private val pickImage =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                Glide.with(this)
                    .load(uri)
                    .into(ivProfile)

                uploadProfilePicture(uri)
            }
        }

    // =========================================================
    // ON CREATE VIEW
    // =========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_profile,
            container,
            false
        )

        auth = FirebaseAuth.getInstance()
        db = SupabaseDatabase.getInstance()

        ivProfile = view.findViewById(R.id.ivProfile)
        tvProfileInitials = view.findViewById(R.id.tvProfileInitials)
        tvProfileName = view.findViewById(R.id.tvProfileName)
        tvProfileCourse = view.findViewById(R.id.tvProfileCourse)
        tvProfileStudentId = view.findViewById(R.id.tvProfileStudentId)
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail)
        tvProfileContact = view.findViewById(R.id.tvProfileContact)
        tvProfileYearLevel = view.findViewById(R.id.tvProfileYearLevel)

        membershipContainer =
            view.findViewById(R.id.membershipContainer)

        // FIX: tinuod nga circle
        ivProfile.shapeAppearanceModel =
            ivProfile.shapeAppearanceModel
                .toBuilder()
                .setAllCornerSizes(ShapeAppearanceModel.PILL)
                .build()

        // FIX: teal circle nga naay initials (fallback)
        val circle = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#7C3AED"),
                Color.parseColor("#3B176A")
            )
        )

        circle.shape = GradientDrawable.OVAL

        tvProfileInitials.background = circle

        loadStudentProfile()

        listenToOrganizations()
        listenToMemberships()

        val btnChangePhoto =
            view.findViewById<LinearLayout>(
                R.id.btnChangePhoto
            )

        val btnEditProfile =
            view.findViewById<LinearLayout>(
                R.id.btnEditProfile
            )

        val btnLogout =
            view.findViewById<LinearLayout>(
                R.id.btnLogout
            )

        val manage = view.findViewById<View>(R.id.btnManageOrganization)
        val application = view.findViewById<View>(R.id.btnOrganizationApplication)
        if (!viewOnly) {
            application.visibility = View.VISIBLE
            application.setOnClickListener { startActivity(Intent(requireContext(), AdminSignUpActivity::class.java)) }
            manage.setOnClickListener { OrganizationAccess.show(requireActivity()) }
            accessListener = db.watchAccountAccess { access, _ ->
                if (this.view !== view) return@watchAccountAccess
                manage.visibility = if (access?.organizations?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
        }

        if (viewOnly) {

            // Admin is only viewing the student's profile.
            btnChangePhoto.visibility =
                View.GONE

            btnEditProfile.visibility =
                View.GONE

            btnLogout.visibility =
                View.GONE

        } else {

            btnChangePhoto.visibility =
                View.VISIBLE

            btnEditProfile.visibility =
                View.VISIBLE

            btnLogout.visibility =
                View.VISIBLE

            btnChangePhoto.setOnClickListener {

                pickImage.launch(
                    "image/*"
                )
            }

            btnEditProfile.setOnClickListener {

                showEditProfileDialog()
            }

            btnLogout.setOnClickListener {

                auth.signOut()

                val intent =
                    Intent(
                        requireContext(),
                        WelcomeActivity::class.java
                    )

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(
                    intent
                )
            }
        }

        return view
    }

    // =========================================================
    // LOAD STUDENT PROFILE
    // =========================================================

    private fun loadStudentProfile() {

        val currentUser =
            auth.currentUser

        val targetUserId =
            if (
                profileUserId.isNotBlank()
            ) {

                profileUserId

            } else {

                currentUser?.uid
                    ?: return
            }

        db.collection(
            "users"
        )
            .document(
                targetUserId
            )
            .get()
            .addOnSuccessListener { document ->

                if (
                    !isAdded ||
                    !document.exists()
                ) {

                    return@addOnSuccessListener
                }

                val fullName =
                    document.getString(
                        "fullName"
                    )
                        ?: "Student"

                val studentId =
                    document.getString(
                        "studentId"
                    )
                        ?: "Not set"

                val course =
                    document.getString(
                        "course"
                    )
                        ?: "Not set"

                val yearLevel =
                    document.getString(
                        "yearLevel"
                    )
                        ?: "Not set"

                val email =
                    document.getString(
                        "email"
                    )
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: if (
                            !viewOnly &&
                            currentUser?.uid ==
                            targetUserId
                        ) {

                            currentUser.email
                                ?: "Not set"

                        } else {

                            "Not set"
                        }

                val contact =
                    document.getString(
                        "contactNumber"
                    )
                        ?: "Not set"

                val profileImageUrl =
                    document.getString(
                        "profileImageUrl"
                    )

                // ================================================
                // BASIC PROFILE
                // ================================================

                tvProfileName.text =
                    fullName

                tvProfileInitials.text =
                    getPersonInitials(
                        fullName
                    )

                tvProfileCourse.text =
                    "$course · BISU Balilihan Campus"

                tvProfileStudentId.text =
                    "Student ID: $studentId"

                tvProfileEmail.text =
                    email

                tvProfileContact.text =
                    contact

                tvProfileYearLevel.text =
                    yearLevel

                // ================================================
                // PROFILE PICTURE
                // ================================================

                if (
                    !profileImageUrl.isNullOrBlank()
                ) {

                    tvProfileInitials.visibility =
                        View.GONE

                    ivProfile.visibility =
                        View.VISIBLE

                    Glide.with(
                        this
                    )
                        .load(
                            profileImageUrl
                        )
                        .circleCrop()
                        .error(
                            android.R.color.transparent
                        )
                        .into(
                            ivProfile
                        )

                } else {

                    Glide.with(
                        this
                    )
                        .clear(
                            ivProfile
                        )

                    ivProfile.setImageDrawable(
                        null
                    )

                    ivProfile.visibility =
                        View.GONE

                    tvProfileInitials.visibility =
                        View.VISIBLE
                }
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Unable to load student profile information.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun getPersonInitials(
        fullName: String
    ): String {

        val parts =
            fullName.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }

        return when {
            parts.size >= 2 ->
                "${parts[0].first()}${parts[1].first()}"
            parts.size == 1 ->
                parts[0].take(2)
            else -> "ST"
        }.uppercase()
    }

    // =========================================================
    // FIX: VALID ORGANIZATIONS
    // =========================================================

    private fun listenToOrganizations() {

        organizationsListener?.remove()

        organizationsListener =
            db.collection("organizations")
                .addSnapshotListener { snapshot, error ->

                    if (!isAdded || error != null || snapshot == null) {
                        return@addSnapshotListener
                    }

                    validOrganizationIds.clear()

                    snapshot.documents.forEach {
                        validOrganizationIds.add(it.id)
                    }

                    renderMemberships()
                }
    }

    // =========================================================
    // LISTEN TO APPROVED MEMBERSHIPS (REAL-TIME)
    // =========================================================

    private fun listenToMemberships() {

        val targetUserId =
            if (
                profileUserId.isNotBlank()
            ) {

                profileUserId

            } else {

                auth.currentUser?.uid
                    ?: return
            }

        membershipsListener?.remove()

        membershipsListener =
            db.collection(
                "memberships"
            )
                .whereEqualTo(
                    "studentId",
                    targetUserId
                )
                .addSnapshotListener { result, error ->

                    if (!isAdded) {
                        return@addSnapshotListener
                    }

                    if (
                        error != null
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "Unable to load memberships.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@addSnapshotListener
                    }

                    membershipDocs =
                        result?.documents
                            ?: emptyList()

                    renderMemberships()
                }
    }

    // =========================================================
    // FIX: RENDER (approved + tinuod nga organization ra, max 3)
    // =========================================================

    private fun renderMemberships() {

        if (!isAdded) {
            return
        }

        membershipContainer.removeAllViews()

        val approvedMemberships =
            membershipDocs
                .filter {
                    it.getString("status") == "approved" &&
                            validOrganizationIds.contains(
                                it.getString("organizationId") ?: ""
                            )
                }
                .sortedBy {
                    it.getTimestamp("approvedAt")
                        ?.toDate()?.time ?: 0L
                }
                .take(maxOrganizations)

        if (approvedMemberships.isEmpty()) {

            val emptyText = TextView(requireContext())

            emptyText.text =
                "You are not yet a member of any organization."

            emptyText.setTextColor(
                resources.getColor(R.color.text_gray, null)
            )

            emptyText.textSize = 12f

            emptyText.setPadding(
                12.dp(), 12.dp(), 12.dp(), 18.dp()
            )

            membershipContainer.addView(emptyText)

            return
        }

        for (document in approvedMemberships) {
            addMembershipCard(document)
        }
    }

    // =========================================================
    // CREATE MEMBERSHIP CARD
    // =========================================================

    private fun addMembershipCard(
        document: DocumentSnapshot
    ) {

        val organizationId =
            document.getString("organizationId") ?: ""

        val organizationName =
            document.getString("organizationName") ?: "Organization"

        val shortCode =
            document.getString("shortCode")
                ?.takeIf { it.isNotBlank() }
                ?: getInitials(organizationName)

        val approvedAt: Timestamp? =
            document.getTimestamp("approvedAt")

        val card = LinearLayout(requireContext())

        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setBackgroundResource(R.drawable.bg_card)
        card.setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.setMargins(0, 0, 0, 9.dp())

        card.layoutParams = cardParams

        card.isClickable = true
        card.isFocusable = true

        card.setOnClickListener {

            val intent = Intent(
                requireContext(),
                OrganizationProfileActivity::class.java
            )

            intent.putExtra("organizationId", organizationId)
            intent.putExtra("organizationName", organizationName)
            intent.putExtra("shortCode", shortCode)

            startActivity(intent)
        }

        val avatar = TextView(requireContext())

        avatar.layoutParams =
            LinearLayout.LayoutParams(36.dp(), 36.dp())

        avatar.gravity = Gravity.CENTER
        avatar.text = shortCode.take(4).uppercase()

        avatar.setTextColor(
            resources.getColor(R.color.white, null)
        )

        avatar.textSize = 9f

        avatar.setTypeface(null, android.graphics.Typeface.BOLD)

        avatar.setBackgroundResource(R.drawable.bg_oc_avatar_teal)

        val info = LinearLayout(requireContext())

        info.orientation = LinearLayout.VERTICAL

        val infoParams =
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        infoParams.setMargins(11.dp(), 0, 0, 0)

        info.layoutParams = infoParams

        val name = TextView(requireContext())

        name.text = organizationName

        name.setTextColor(
            resources.getColor(R.color.dark_blue, null)
        )

        name.textSize = 12.5f

        name.setTypeface(null, android.graphics.Typeface.BOLD)

        val details = TextView(requireContext())

        details.text =
            if (approvedAt != null) {

                val formattedDate =
                    SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.getDefault()
                    ).format(approvedAt.toDate())

                "Member since $formattedDate"

            } else {
                "Active member"
            }

        details.setTextColor(
            resources.getColor(R.color.text_gray, null)
        )

        details.textSize = 10.5f

        info.addView(name)
        info.addView(details)

        card.addView(avatar)
        card.addView(info)

        membershipContainer.addView(card)
    }

    private fun getInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        return when {
            words.isEmpty() -> "ORG"
            words.size == 1 -> words[0].take(3).uppercase()
            else -> words.take(3)
                .map { it.first() }
                .joinToString("")
                .uppercase()
        }
    }

    // =========================================================
    // EDIT PROFILE
    // =========================================================

    private fun showEditProfileDialog() {

        val currentUser = auth.currentUser ?: return

        val uid = currentUser.uid

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                if (!isAdded || !document.exists()) {
                    return@addOnSuccessListener
                }

                val layout = LinearLayout(requireContext())

                layout.orientation = LinearLayout.VERTICAL
                layout.setPadding(45, 10, 45, 10)

                fun field(
                    hint: String,
                    value: String
                ): EditText {

                    val editText = EditText(requireContext())

                    editText.hint = hint
                    editText.setText(value)

                    layout.addView(editText)

                    return editText
                }

                val etFullName =
                    field("Full Name", document.getString("fullName") ?: "")

                val etStudentId =
                    field("Student ID", document.getString("studentId") ?: "")

                val etCourse =
                    field("Course", document.getString("course") ?: "")

                val etYearLevel =
                    field("Year Level", document.getString("yearLevel") ?: "")

                val etInterest =
                    field("Interest", document.getString("interest") ?: "")

                val etContact =
                    field("Contact Number", document.getString("contactNumber") ?: "")

                etContact.inputType = InputType.TYPE_CLASS_PHONE

                val dialog =
                    AlertDialog.Builder(requireContext())
                        .setTitle("Edit Profile")
                        .setView(layout)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", null)
                        .create()

                dialog.setOnShowListener {

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {

                            val updatedName =
                                etFullName.text.toString().trim()

                            val updatedStudentId =
                                etStudentId.text.toString().trim()

                            val updatedCourse =
                                etCourse.text.toString().trim()

                            val updatedYearLevel =
                                etYearLevel.text.toString().trim()

                            val updatedInterest =
                                etInterest.text.toString().trim()

                            val updatedContact =
                                etContact.text.toString().trim()

                            if (
                                updatedName.isEmpty() ||
                                updatedStudentId.isEmpty() ||
                                updatedCourse.isEmpty() ||
                                updatedYearLevel.isEmpty()
                            ) {

                                Toast.makeText(
                                    requireContext(),
                                    "Please fill in the required fields.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@setOnClickListener
                            }

                            val updates =
                                hashMapOf<String, Any>(
                                    "fullName" to updatedName,
                                    "studentId" to updatedStudentId,
                                    "course" to updatedCourse,
                                    "yearLevel" to updatedYearLevel,
                                    "interest" to updatedInterest,
                                    "contactNumber" to updatedContact
                                )

                            db.collection("users")
                                .document(uid)
                                .update(updates)
                                .addOnSuccessListener {

                                    if (!isAdded) {
                                        return@addOnSuccessListener
                                    }

                                    Toast.makeText(
                                        requireContext(),
                                        "Profile updated successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    loadStudentProfile()

                                    dialog.dismiss()
                                }
                                .addOnFailureListener {

                                    if (!isAdded) {
                                        return@addOnFailureListener
                                    }

                                    Toast.makeText(
                                        requireContext(),
                                        "Failed to update profile.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                }

                dialog.show()
            }
            .addOnFailureListener {

                if (!isAdded) {
                    return@addOnFailureListener
                }

                Toast.makeText(
                    requireContext(),
                    "Unable to open profile editor.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // =========================================================
    // UPLOAD PROFILE PICTURE (CLOUDINARY)
    // =========================================================

    private fun uploadProfilePicture(
        uri: Uri
    ) {

        val currentUser = auth.currentUser ?: return

        val uid = currentUser.uid

        Toast.makeText(
            requireContext(),
            "Uploading profile picture...",
            Toast.LENGTH_SHORT
        ).show()

        MediaManager.get()
            .upload(uri)
            .unsigned("kayara_upload")
            .option("folder", "kayara/profiles/$uid")
            .callback(
                object : UploadCallback {

                    override fun onStart(requestId: String?) {}

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
                            resultData?.get("secure_url")?.toString()

                        activity?.runOnUiThread {

                            if (!isAdded) {
                                return@runOnUiThread
                            }

                            if (secureUrl.isNullOrBlank()) {

                                Toast.makeText(
                                    requireContext(),
                                    "Failed to upload profile picture.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@runOnUiThread
                            }

                            db.collection("users")
                                .document(uid)
                                .update("profileImageUrl", secureUrl)
                                .addOnSuccessListener {

                                    if (!isAdded) {
                                        return@addOnSuccessListener
                                    }

                                    Toast.makeText(
                                        requireContext(),
                                        "Profile picture updated!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener {

                                    if (!isAdded) {
                                        return@addOnFailureListener
                                    }

                                    Toast.makeText(
                                        requireContext(),
                                        "Picture uploaded but profile was not updated.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }

                    override fun onError(
                        requestId: String?,
                        error: ErrorInfo?
                    ) {

                        activity?.runOnUiThread {

                            if (!isAdded) {
                                return@runOnUiThread
                            }

                            Toast.makeText(
                                requireContext(),
                                "Failed to upload: ${error?.description ?: "Unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
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

    private fun Int.dp(): Int {

        return (
                this *
                        resources.displayMetrics.density
                ).toInt()
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroyView() {
        accessListener?.remove()
        accessListener = null

        membershipsListener?.remove()
        membershipsListener = null

        organizationsListener?.remove()
        organizationsListener = null

        super.onDestroyView()
    }
}