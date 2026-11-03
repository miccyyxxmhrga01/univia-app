package com.example.kayara

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

class AcademicYearFragment : Fragment() {

    private lateinit var tvCurrentAcademicYear: TextView
    private lateinit var tvCurrentStatus: TextView
    private lateinit var etAcademicYear: EditText
    private lateinit var etStartDate: EditText
    private lateinit var etEndDate: EditText
    private lateinit var btnSaveAcademicYear: TextView

    private val db = SupabaseDatabase.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_academic_year,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentAcademicYear =
            view.findViewById(R.id.tvCurrentAcademicYear)

        tvCurrentStatus =
            view.findViewById(R.id.tvCurrentStatus)

        etAcademicYear =
            view.findViewById(R.id.etAcademicYear)

        etStartDate =
            view.findViewById(R.id.etStartDate)

        etEndDate =
            view.findViewById(R.id.etEndDate)

        btnSaveAcademicYear =
            view.findViewById(R.id.btnSaveAcademicYear)

        loadCurrentAcademicYear()

        btnSaveAcademicYear.setOnClickListener {
            saveAcademicYear()
        }
    }

    private fun loadCurrentAcademicYear() {

        db.collection("settings")
            .document("academicYear")
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    tvCurrentAcademicYear.text = "Not set"
                    tvCurrentStatus.text =
                        "No active academic year"

                    return@addOnSuccessListener
                }

                val academicYear =
                    document.getString("academicYear") ?: ""

                val startDate =
                    document.getString("startDate") ?: ""

                val endDate =
                    document.getString("endDate") ?: ""

                val isActive =
                    document.getBoolean("isActive") ?: false

                tvCurrentAcademicYear.text =
                    academicYear.ifBlank {
                        "Not set"
                    }

                tvCurrentStatus.text =
                    if (isActive) {
                        "Active • $startDate to $endDate"
                    } else {
                        "Inactive"
                    }

                etAcademicYear.setText(academicYear)
                etStartDate.setText(startDate)
                etEndDate.setText(endDate)
            }
            .addOnFailureListener {

                Toast.makeText(
                    requireContext(),
                    "Failed to load academic year.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun saveAcademicYear() {

        val academicYear =
            etAcademicYear.text.toString().trim()

        val startDate =
            etStartDate.text.toString().trim()

        val endDate =
            etEndDate.text.toString().trim()

        if (academicYear.isBlank()) {

            etAcademicYear.error =
                "Enter the academic year."

            etAcademicYear.requestFocus()

            return
        }

        if (startDate.isBlank()) {

            etStartDate.error =
                "Enter the start date."

            etStartDate.requestFocus()

            return
        }

        if (endDate.isBlank()) {

            etEndDate.error =
                "Enter the end date."

            etEndDate.requestFocus()

            return
        }

        btnSaveAcademicYear.isEnabled = false

        db.collection("settings")
            .document("academicYear")
            .get()
            .addOnSuccessListener { currentDocument ->

                val oldAcademicYear =
                    currentDocument.getString(
                        "academicYear"
                    ) ?: ""

                // SAME ACADEMIC YEAR
                if (
                    oldAcademicYear.isNotBlank() &&
                    oldAcademicYear == academicYear
                ) {

                    saveAcademicYearSettings(
                        academicYear = academicYear,
                        startDate = startDate,
                        endDate = endDate,
                        showSuccessMessage = false
                    )

                    return@addOnSuccessListener
                }

                // FIRST ACADEMIC YEAR
                if (oldAcademicYear.isBlank()) {

                    saveAcademicYearSettings(
                        academicYear = academicYear,
                        startDate = startDate,
                        endDate = endDate,
                        showSuccessMessage = false
                    )

                    return@addOnSuccessListener
                }

                // NEW ACADEMIC YEAR
                markOrganizationsForRenewal(
                    oldAcademicYear = oldAcademicYear,
                    newAcademicYear = academicYear,
                    startDate = startDate,
                    endDate = endDate
                )
            }
            .addOnFailureListener {

                Toast.makeText(
                    requireContext(),
                    "Failed to check current academic year.",
                    Toast.LENGTH_SHORT
                ).show()

                btnSaveAcademicYear.isEnabled = true
            }
    }

    private fun markOrganizationsForRenewal(
        oldAcademicYear: String,
        newAcademicYear: String,
        startDate: String,
        endDate: String
    ) {

        db.collection("organizations")
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.isEmpty) {

                    saveAcademicYearSettings(
                        academicYear = newAcademicYear,
                        startDate = startDate,
                        endDate = endDate,
                        showSuccessMessage = true
                    )

                    return@addOnSuccessListener
                }

                val batch = db.batch()

                var approvedCount = 0

                for (organization in snapshot.documents) {

                    val status =
                        organization.getString("status") ?: ""

                    val organizationRef =
                        db.collection("organizations")
                            .document(organization.id)

                    /*
                     * Existing approved organizations
                     * created by the approval workflow may
                     * not have a "status" field yet.
                     *
                     * So we also check for adminUid
                     * or applicationId.
                     */
                    val isApprovedOrganization =
                        status == "approved" ||
                                status == "active" ||
                                (
                                        status.isBlank() &&
                                                (
                                                        organization.contains("adminUid") ||
                                                                organization.contains("applicationId")
                                                        )
                                        )

                    if (isApprovedOrganization) {

                        batch.update(
                            organizationRef,
                            mapOf(

                                // Previous academic year
                                "academicYear" to oldAcademicYear,

                                // Renewal information
                                "renewalStatus" to "due",
                                "renewalAcademicYear" to newAcademicYear,
                                "renewalStartDate" to startDate,
                                "renewalEndDate" to endDate,

                                // IMPORTANT:
                                // This makes the existing
                                // Organizations/Documents UI
                                // recognize the organization
                                // as Renewal Due.
                                "category" to "renewal_due",

                                "updatedAt" to
                                        FieldValue.serverTimestamp()
                            )
                        )

                        approvedCount++
                    }
                }

                batch.commit()
                    .addOnSuccessListener {

                        saveAcademicYearSettings(
                            academicYear = newAcademicYear,
                            startDate = startDate,
                            endDate = endDate,
                            showSuccessMessage = true
                        )

                        Toast.makeText(
                            requireContext(),
                            "$approvedCount organizations marked for renewal.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener {

                        Toast.makeText(
                            requireContext(),
                            "Failed to update organization renewal status.",
                            Toast.LENGTH_LONG
                        ).show()

                        btnSaveAcademicYear.isEnabled = true
                    }
            }
            .addOnFailureListener {

                Toast.makeText(
                    requireContext(),
                    "Failed to load organizations.",
                    Toast.LENGTH_LONG
                ).show()

                btnSaveAcademicYear.isEnabled = true
            }
    }

    private fun saveAcademicYearSettings(
        academicYear: String,
        startDate: String,
        endDate: String,
        showSuccessMessage: Boolean
    ) {

        val data = hashMapOf(
            "academicYear" to academicYear,
            "startDate" to startDate,
            "endDate" to endDate,
            "isActive" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("settings")
            .document("academicYear")
            .set(data)
            .addOnSuccessListener {

                tvCurrentAcademicYear.text =
                    academicYear

                tvCurrentStatus.text =
                    "Active • $startDate to $endDate"

                Toast.makeText(
                    requireContext(),
                    if (showSuccessMessage) {
                        "Academic year updated successfully."
                    } else {
                        "Academic year saved successfully."
                    },
                    Toast.LENGTH_SHORT
                ).show()

                btnSaveAcademicYear.isEnabled = true
            }
            .addOnFailureListener {

                Toast.makeText(
                    requireContext(),
                    "Failed to save academic year.",
                    Toast.LENGTH_LONG
                ).show()

                btnSaveAcademicYear.isEnabled = true
            }
    }
}