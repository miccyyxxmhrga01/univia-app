package com.example.kayara

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Passwords survive rotation in memory only. They are never written to saved state or preferences. */
class RegistrationCredentials : ViewModel() {
    var password = ""
    var confirmation = ""
    override fun onCleared() { password = ""; confirmation = "" }
}

/** Presentation only: the existing activities remain responsible for the final registration request. */
class RegistrationFlow(
    private val activity: AppCompatActivity,
    savedState: Bundle?,
    private val organization: Boolean = false,
    private val documents: () -> List<String?> = { emptyList() }
) {
    private val steps = listOf(
        if (organization) R.string.registration_org else R.string.registration_profile,
        R.string.registration_account,
        if (organization) R.string.registration_documents else R.string.registration_interests,
        R.string.registration_review
    ) + if (organization) emptyList() else listOf(R.string.registration_verify)
    private val titles = listOf(
        if (organization) R.string.registration_org_title else R.string.registration_profile_title,
        R.string.registration_account_title,
        if (organization) R.string.registration_documents_title else R.string.registration_interests_title,
        R.string.registration_review_title
    )
    private val descriptions = listOf(
        if (organization) R.string.registration_org_description else R.string.registration_profile_description,
        R.string.registration_account_description,
        if (organization) R.string.registration_documents_description else R.string.registration_interests_description,
        R.string.registration_review_description
    )
    private val groups = listOf(R.id.registrationStep1, R.id.registrationStep2, R.id.registrationStep3, R.id.registrationStep4)
    private val error = activity.findViewById<TextView>(R.id.tvError)
    private val scroll = activity.findViewById<NestedScrollView>(R.id.signUpScroll)
    private val next = activity.findViewById<TextView>(R.id.registrationNext)
    private val submit = activity.findViewById<View>(R.id.btnSignUp)
    private val track = activity.findViewById<LinearLayout>(R.id.registrationStepTrack)
    private var step = (savedState?.getInt(STATE_STEP) ?: 0).coerceIn(0, 3)
    private var busy = false
    private val credentials = ViewModelProvider(activity)[RegistrationCredentials::class.java]

    init {
        retainPassword(R.id.etPassword, credentials.password) { credentials.password = it }
        retainPassword(R.id.etConfirmPassword, credentials.confirmation) { credentials.confirmation = it }
        val inputIds = mutableListOf(R.id.etFullName, R.id.etEmail, R.id.etUsername, R.id.etPassword, R.id.etConfirmPassword)
        inputIds += if (organization) listOf(R.id.etOrgName, R.id.etDescription) else listOf(R.id.etStudentId, R.id.etInterest)
        inputIds.forEach { id ->
            activity.findViewById<EditText>(id).addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!busy) error.visibility = View.GONE
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        if (!organization) {
            for ((id, array) in listOf(R.id.spinnerCourse to R.array.course_list, R.id.spinnerYearLevel to R.array.year_level_list)) {
                activity.findViewById<Spinner>(id).adapter = ArrayAdapter.createFromResource(
                    activity, array, R.layout.registration_spinner_item
                ).apply { setDropDownViewResource(R.layout.registration_spinner_item) }
            }
        }
        next.setOnClickListener {
            if (!busy && validate(step)) showStep(step + 1)
        }
        val back = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy) return
                if (step > 0) showStep(step - 1) else activity.finish()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, back)
        activity.findViewById<View>(R.id.btnBack).setOnClickListener { back.handleOnBackPressed() }
        activity.findViewById<View>(R.id.tvGoToLogin).setOnClickListener { if (!busy) activity.finish() }
        // Keep fields usable above the IME and in landscape without replacing native inset handling.
        val root = activity.findViewById<View>(R.id.registrationScene)
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val availableDp = (root.height - root.paddingTop - root.paddingBottom) / activity.resources.displayMetrics.density
            // Large text needs the same extra editing room as a short window or an open keyboard.
            val compact = availableDp < 540 * activity.resources.configuration.fontScale.coerceAtLeast(1f)
            activity.findViewById<View>(R.id.registrationHeader).visibility = if (compact) View.GONE else View.VISIBLE
            activity.findViewById<View>(R.id.registrationSubtitle).visibility = if (compact) View.GONE else View.VISIBLE
            activity.findViewById<View>(R.id.tvGoToLogin).visibility = if (compact) View.GONE else View.VISIBLE
        }
        render()
        // Framework view-state restoration happens after onCreate. Never review stale restored data.
        root.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (step > 1 && value(R.id.etPassword).isEmpty()) showStep(1) else render()
        }
    }

    fun saveState(out: Bundle) { out.putInt(STATE_STEP, step) }

    fun refreshDocuments() {
        if (organization) activity.findViewById<TextView>(R.id.registrationDocumentCount).text =
            activity.getString(R.string.registration_documents_count, documents().count { it != null })
    }

    fun setBusy(value: Boolean) {
        busy = value
        activity.findViewById<View>(R.id.btnBack).isEnabled = !value
        activity.findViewById<View>(R.id.tvGoToLogin).isEnabled = !value
        for (index in 0 until track.childCount) track.getChildAt(index).isEnabled = !value && index < step
    }

    private fun retainPassword(id: Int, restored: String, update: (String) -> Unit) {
        activity.findViewById<EditText>(id).apply {
            setText(restored)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
    }

    private fun showStep(target: Int) {
        if (busy) return
        activity.currentFocus?.clearFocus()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        step = target.coerceIn(0, 3)
        error.visibility = View.GONE
        render()
        scroll.post { scroll.scrollTo(0, 0) }
        ViewCompat.setAccessibilityPaneTitle(scroll, activity.getString(titles[step]))
    }

    private fun render() {
        groups.forEachIndexed { index, id -> activity.findViewById<View>(id).visibility = if (index == step) View.VISIBLE else View.GONE }
        activity.findViewById<TextView>(R.id.registrationProgress).text = activity.getString(
            R.string.registration_progress, step + 1, steps.size, activity.getString(steps[step]))
        activity.findViewById<TextView>(R.id.registrationTitle).setText(titles[step])
        activity.findViewById<TextView>(R.id.registrationSubtitle).setText(descriptions[step])
        activity.findViewById<View>(R.id.btnBack).contentDescription = activity.getString(
            if (step == 0) R.string.registration_exit else R.string.registration_back)
        next.visibility = if (step < 3) View.VISIBLE else View.GONE
        submit.visibility = if (step == 3) View.VISIBLE else View.GONE
        renderTrack()
        if (organization) activity.findViewById<TextView>(R.id.registrationDocumentCount).text =
            activity.getString(R.string.registration_documents_count, documents().count { it != null })
        if (step == 3) renderReview()
    }

    private fun renderTrack() {
        track.removeAllViews()
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        for (index in steps.indices) {
            val selected = index == step
            val completed = index < step
            val color = activity.getColor(if (selected) R.color.white else if (completed) R.color.action_blue_dark else R.color.text_secondary)
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                minimumHeight = dp(58)
                contentDescription = activity.getString(R.string.registration_step_accessibility,
                    activity.getString(steps[index]), index + 1, steps.size,
                    activity.getString(if (selected) R.string.registration_current else if (completed) R.string.registration_completed else R.string.registration_upcoming))
                isClickable = completed
                isFocusable = completed
                isEnabled = completed && !busy
                if (completed) setOnClickListener { showStep(index) }
            }
            tile.addView(TextView(activity).apply {
                text = (index + 1).toString()
                gravity = android.view.Gravity.CENTER
                textSize = 12f
                setTextColor(color)
                typeface = ResourcesCompat.getFont(activity, R.font.poppins_semibold)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(activity.getColor(if (selected) R.color.action_blue else if (completed) R.color.action_blue_soft else R.color.surface_card))
                    if (!selected) setStroke(dp(1), activity.getColor(if (completed) R.color.action_blue else R.color.outline))
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            tile.addView(TextView(activity).apply {
                setText(if (organization && index == 0) R.string.registration_org_short
                    else if (organization && index == 2) R.string.registration_documents_short else steps[index])
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setTextColor(activity.getColor(if (selected || completed) R.color.action_blue_dark else R.color.text_secondary))
                typeface = ResourcesCompat.getFont(activity, if (selected) R.font.poppins_semibold else R.font.poppins_regular)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            track.addView(tile, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun value(id: Int) = activity.findViewById<EditText>(id).text.toString()
    private fun text(id: Int) = value(id).trim()

    private fun fail(message: String, field: Int? = null): Boolean {
        error.setTextColor(activity.getColor(R.color.error))
        error.text = message
        error.visibility = View.VISIBLE
        field?.let { activity.findViewById<View>(it).requestFocus() }
        return false
    }

    private fun required(id: Int, label: Int): Boolean = text(id).isNotEmpty() ||
        fail(activity.getString(R.string.registration_required, activity.getString(label).lowercase()), id)

    private fun validate(index: Int): Boolean {
        if (index == 0) {
            if (!required(R.id.etFullName, R.string.registration_full_name)) return false
            if (organization) return required(R.id.etOrgName, R.string.registration_org_name) && required(R.id.etDescription, R.string.registration_description)
            if (!required(R.id.etStudentId, R.string.registration_student_id)) return false
            if (!text(R.id.etStudentId).matches(Regex("^\\d+$"))) return fail(activity.getString(R.string.registration_invalid_id), R.id.etStudentId)
            if (activity.findViewById<Spinner>(R.id.spinnerCourse).selectedItemPosition <= 0) return fail(activity.getString(R.string.registration_choose_course), R.id.spinnerCourse)
            if (activity.findViewById<Spinner>(R.id.spinnerYearLevel).selectedItemPosition <= 0) return fail(activity.getString(R.string.registration_choose_year), R.id.spinnerYearLevel)
        } else if (index == 1) {
            if (!required(R.id.etEmail, if (organization) R.string.registration_org_email else R.string.registration_email)) return false
            val email = text(R.id.etEmail)
            if (organization) {
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return fail(activity.getString(R.string.registration_invalid_email), R.id.etEmail)
            } else {
                val expected = "bal_${text(R.id.etStudentId)}@bisu.edu.ph"
                if (!RegistrationEmailPolicy.isCampusEmail(email)) return fail(activity.getString(R.string.registration_bisu_only), R.id.etEmail)
                if (!RegistrationEmailPolicy.matchesStudentId(email, text(R.id.etStudentId))) return fail(activity.getString(R.string.registration_matching_email, expected), R.id.etEmail)
            }
            if (!required(R.id.etUsername, R.string.registration_username)) return false
            if (organization && text(R.id.etUsername).contains(" ")) return fail(activity.getString(R.string.registration_username_spaces), R.id.etUsername)
            val password = if (organization) text(R.id.etPassword) else value(R.id.etPassword)
            val confirmation = if (organization) text(R.id.etConfirmPassword) else value(R.id.etConfirmPassword)
            if (password.length < 6) return fail(activity.getString(R.string.registration_short_password), R.id.etPassword)
            if (password != confirmation) return fail(activity.getString(R.string.registration_password_mismatch), R.id.etConfirmPassword)
        } else if (index == 2) {
            if (!organization) return required(R.id.etInterest, R.string.registration_interest_label)
            val missing = documents().indexOfFirst { it == null }
            if (missing >= 0) return fail(activity.getString(R.string.registration_missing_document, activity.getString(DOCUMENT_LABELS[missing])))
        }
        error.visibility = View.GONE
        return true
    }

    private fun renderReview() {
        val container = activity.findViewById<LinearLayout>(R.id.registrationReviewDetails)
        container.removeAllViews()
        val rows = mutableListOf<Pair<String, String>>()
        fun row(label: Int, value: String) { rows.add(activity.getString(label) to value) }
        row(R.string.registration_full_name, text(R.id.etFullName))
        if (organization) {
            row(R.string.registration_org_name, text(R.id.etOrgName))
            row(R.string.registration_description, text(R.id.etDescription))
        } else {
            row(R.string.registration_student_id, text(R.id.etStudentId))
            row(R.string.registration_course, activity.findViewById<Spinner>(R.id.spinnerCourse).selectedItem.toString())
            row(R.string.registration_year, activity.findViewById<Spinner>(R.id.spinnerYearLevel).selectedItem.toString())
        }
        row(if (organization) R.string.registration_org_email else R.string.registration_email, text(R.id.etEmail))
        row(R.string.registration_username, text(R.id.etUsername))
        row(R.string.registration_password, activity.getString(R.string.registration_password_ready))
        if (organization) documents().forEachIndexed { index, name -> row(DOCUMENT_LABELS[index], name.orEmpty()) }
        else row(R.string.registration_interest_label, text(R.id.etInterest))
        for ((label, value) in rows) {
            container.addView(TextView(activity).apply {
                text = label
                textSize = 12f
                typeface = ResourcesCompat.getFont(activity, R.font.poppins_semibold)
                setTextColor(activity.getColor(R.color.text_primary))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * activity.resources.displayMetrics.density).toInt() })
            container.addView(TextView(activity).apply {
                text = value
                textSize = 14f
                typeface = ResourcesCompat.getFont(activity, R.font.poppins_regular)
                setTextColor(activity.getColor(R.color.login_secondary))
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    companion object {
        private const val STATE_STEP = "registration.currentStep"
        private val DOCUMENT_LABELS = listOf(R.string.registration_doc1, R.string.registration_doc2, R.string.registration_doc3, R.string.registration_doc4, R.string.registration_doc5)
    }
}
