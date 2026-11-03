package com.example.kayara

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.kayara.data.SupabaseDatabase

/**
 * Detalye sa usa ka member / applicant (gi-abli kung i-click ang row sa Members tab).
 *
 *  - Profile picture, ngan, status
 *  - Student information (gikan sa users/{studentId})
 *  - Message nga gi-send sa student
 *  - Documents nga gi-submit (i-tap aron ablihan)
 *  - Approve / Reject (kung pending pa)
 */
class ApplicantDetailsDialog(
    private val fragment: Fragment,
    private val member: MemberModel,
    private val onApprove: () -> Unit,
    private val onReject: () -> Unit
) {

    private val db = SupabaseDatabase.getInstance()

    fun show() {

        val context = fragment.requireContext()

        val dialog = Dialog(context)

        val view =
            LayoutInflater.from(context)
                .inflate(R.layout.dialog_applicant_details, null)

        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.window?.setGravity(Gravity.BOTTOM)

        // -----------------------------------------------------
        // VIEWS
        // -----------------------------------------------------

        val tvAvatar =
            view.findViewById<TextView>(R.id.tvApplicantAvatar)

        val imgAvatar =
            view.findViewById<ImageView>(R.id.imgApplicantAvatar)

        val tvName =
            view.findViewById<TextView>(R.id.tvApplicantName)

        val tvSubtitle =
            view.findViewById<TextView>(R.id.tvApplicantSubtitle)

        val tvTag =
            view.findViewById<TextView>(R.id.tvApplicantTag)

        val tvInfo =
            view.findViewById<TextView>(R.id.tvApplicantInfo)

        val tvMessage =
            view.findViewById<TextView>(R.id.tvApplicantMessage)

        val docsContainer =
            view.findViewById<LinearLayout>(R.id.applicantDocsContainer)

        val tvNoDocs =
            view.findViewById<TextView>(R.id.tvApplicantNoDocs)

        val btnReject =
            view.findViewById<TextView>(R.id.btnApplicantReject)

        val btnApprove =
            view.findViewById<TextView>(R.id.btnApplicantApprove)

        val btnClose =
            view.findViewById<TextView>(R.id.btnApplicantClose)

        // -----------------------------------------------------
        // HEADER
        // -----------------------------------------------------

        tvName.text = member.fullName

        tvSubtitle.text = member.details

        tvAvatar.text = getInitials(member.fullName)

        if (member.avatarUrl.isNotBlank()) {

            imgAvatar.visibility = View.VISIBLE
            tvAvatar.visibility = View.GONE

            Glide.with(context)
                .load(member.avatarUrl)
                .circleCrop()
                .into(imgAvatar)
        }

        // -----------------------------------------------------
        // STATUS TAG
        // -----------------------------------------------------

        val isPending = member.role == "pending"

        val isRejected =
            !isPending && member.details.contains("Rejected")

        when {

            isPending -> {

                tvTag.text = "PENDING"

                tvTag.setBackgroundResource(R.drawable.bg_tag_pending)

                tvTag.setTextColor(context.getColor(R.color.amber_text))
            }

            isRejected -> {

                tvTag.text = "REJECTED"

                tvTag.setBackgroundResource(R.drawable.bg_role_member)

                tvTag.setTextColor(context.getColor(R.color.coral_text))
            }

            member.role == "officer" -> {

                tvTag.text =
                    member.position
                        .ifBlank { "OFFICER" }
                        .uppercase()

                tvTag.setBackgroundResource(R.drawable.bg_role_officer)

                tvTag.setTextColor(context.getColor(R.color.blue_deep))
            }

            else -> {

                tvTag.text = "MEMBER"

                tvTag.setBackgroundResource(R.drawable.bg_role_member)

                tvTag.setTextColor(context.getColor(R.color.text_gray))
            }
        }

        // -----------------------------------------------------
        // MESSAGE
        // -----------------------------------------------------

        tvMessage.text =
            if (member.applicationNote.isBlank()) {
                "No message."
            } else {
                member.applicationNote
            }

        // -----------------------------------------------------
        // DOCUMENTS
        // -----------------------------------------------------

        renderDocuments(docsContainer, tvNoDocs)

        // -----------------------------------------------------
        // STUDENT INFORMATION (users/{studentId})
        // -----------------------------------------------------

        tvInfo.text = "Loading..."

        loadStudentInformation(tvInfo)

        // -----------------------------------------------------
        // ACTIONS
        // -----------------------------------------------------

        if (isPending) {

            btnReject.visibility = View.VISIBLE
            btnApprove.visibility = View.VISIBLE
            btnClose.visibility = View.GONE

        } else {

            btnReject.visibility = View.GONE
            btnApprove.visibility = View.GONE
            btnClose.visibility = View.VISIBLE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnApprove.setOnClickListener {

            dialog.dismiss()

            onApprove()
        }

        btnReject.setOnClickListener {

            AlertDialog.Builder(context)
                .setTitle("Reject application?")
                .setMessage(
                    "${member.fullName} will be notified that the application was not approved."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reject") { _, _ ->

                    dialog.dismiss()

                    onReject()
                }
                .show()
        }

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // =========================================================
    // DOCUMENTS
    // =========================================================

    private fun renderDocuments(
        container: LinearLayout,
        tvNoDocs: TextView
    ) {

        container.removeAllViews()

        if (member.documents.isEmpty()) {

            tvNoDocs.visibility = View.VISIBLE

            return
        }

        tvNoDocs.visibility = View.GONE

        val context = fragment.requireContext()

        val inflater = LayoutInflater.from(context)

        member.documents.forEach { document ->

            val row =
                inflater.inflate(
                    R.layout.item_recruit_file,
                    container,
                    false
                )

            // "Requirement title - file name"
            row.findViewById<TextView>(R.id.tvFileName).text =
                if (document.fileName.isBlank()) {
                    document.title
                } else {
                    "${document.title} · ${document.fileName}"
                }

            row.findViewById<View>(R.id.btnRemoveFile)
                .visibility = View.GONE

            row.setOnClickListener {
                openUrl(context, document.url)
            }

            container.addView(row)
        }
    }

    // =========================================================
    // STUDENT INFORMATION
    // =========================================================

    private fun loadStudentInformation(
        tvInfo: TextView
    ) {

        if (member.studentId.isBlank()) {

            tvInfo.text = "No additional information."

            return
        }

        db.collection("users")
            .document(member.studentId)
            .get()
            .addOnSuccessListener { document ->

                if (!fragment.isAdded) {
                    return@addOnSuccessListener
                }

                val studentNumber =
                    document.getString("studentId") ?: "Not set"

                val course =
                    document.getString("course") ?: ""

                val yearLevel =
                    document.getString("yearLevel") ?: ""

                val email =
                    document.getString("email") ?: "Not set"

                val contact =
                    document.getString("contactNumber") ?: "Not set"

                val courseYear =
                    listOf(course, yearLevel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Not set" }

                tvInfo.text =
                    "Student ID:  $studentNumber\n" +
                            "Course:  $courseYear\n" +
                            "Email:  $email\n" +
                            "Contact:  $contact"
            }
            .addOnFailureListener {

                if (fragment.isAdded) {
                    tvInfo.text = "Unable to load student information."
                }
            }
    }

    private fun getInitials(name: String): String {

        val parts =
            name.trim()
                .split(" ")
                .filter { it.isNotBlank() }

        return if (parts.size >= 2) {
            "${parts[0][0]}${parts[1][0]}".uppercase()
        } else {
            name.take(2).uppercase()
        }
    }
}