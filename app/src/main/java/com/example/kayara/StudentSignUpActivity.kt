package com.example.kayara

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.example.kayara.data.SupabaseDatabase

class StudentSignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: SupabaseDatabase

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var isRegistering = false
    private lateinit var registrationFlow: RegistrationFlow


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_student_sign_up
        )
        UniviaInsets.apply(this)


        // =====================================================
        // FIREBASE
        // =====================================================

        auth =
            FirebaseAuth.getInstance()

        db =
            SupabaseDatabase.getInstance()


        // =====================================================
        // VIEWS
        // =====================================================

        val etFullName =
            findViewById<EditText>(
                R.id.etFullName
            )

        val etStudentId =
            findViewById<EditText>(
                R.id.etStudentId
            )

        val spinnerCourse =
            findViewById<Spinner>(
                R.id.spinnerCourse
            )

        val spinnerYearLevel =
            findViewById<Spinner>(
                R.id.spinnerYearLevel
            )

        val etEmail =
            findViewById<EditText>(
                R.id.etEmail
            )

        val etUsername =
            findViewById<EditText>(
                R.id.etUsername
            )

        val etPassword =
            findViewById<EditText>(
                R.id.etPassword
            )

        val etConfirmPassword =
            findViewById<EditText>(
                R.id.etConfirmPassword
            )

        val etInterest =
            findViewById<EditText>(
                R.id.etInterest
            )

        val tvError =
            findViewById<TextView>(
                R.id.tvError
            )

        val btnSignUp =
            findViewById<LinearLayout>(
                R.id.btnSignUp
            )

        val btnBack =
            findViewById<ImageView>(
                R.id.btnBack
            )

        val tvGoToLogin =
            findViewById<TextView>(
                R.id.tvGoToLogin
            )

        val btnTogglePassword =
            findViewById<ImageView>(
                R.id.btnTogglePassword
            )

        val btnToggleConfirmPassword =
            findViewById<ImageView>(
                R.id.btnToggleConfirmPassword
            )


        // =====================================================
        // BACK
        // =====================================================

        btnBack.setOnClickListener {

            finish()
        }


        tvGoToLogin.setOnClickListener {

            finish()
        }


        // =====================================================
        // PASSWORD VISIBILITY
        // =====================================================

        btnTogglePassword.setOnClickListener {

            isPasswordVisible =
                !isPasswordVisible

            togglePasswordVisibility(
                etPassword,
                btnTogglePassword,
                isPasswordVisible
            )
        }


        btnToggleConfirmPassword.setOnClickListener {

            isConfirmPasswordVisible =
                !isConfirmPasswordVisible

            togglePasswordVisibility(
                etConfirmPassword,
                btnToggleConfirmPassword,
                isConfirmPasswordVisible
            )
        }


        // =====================================================
        // SIGN UP
        // =====================================================

        btnSignUp.setOnClickListener {

            if (isRegistering) {
                return@setOnClickListener
            }


            // =================================================
            // GET VALUES
            // =================================================

            val fullName =
                etFullName.text
                    .toString()
                    .trim()

            val studentId =
                etStudentId.text
                    .toString()
                    .trim()

            val course =
                spinnerCourse
                    .selectedItem
                    ?.toString()
                    ?.trim()
                    ?: ""

            val yearLevel =
                spinnerYearLevel
                    .selectedItem
                    ?.toString()
                    ?.trim()
                    ?: ""

            val email =
                etEmail.text
                    .toString()
                    .trim()
                    .lowercase()

            val username =
                etUsername.text
                    .toString()
                    .trim()

            val password =
                etPassword.text
                    .toString()

            val confirmPassword =
                etConfirmPassword.text
                    .toString()

            val interest =
                etInterest.text
                    .toString()
                    .trim()


            // =================================================
            // EMPTY FIELD VALIDATION
            // =================================================

            if (
                fullName.isEmpty() ||
                studentId.isEmpty() ||
                email.isEmpty() ||
                username.isEmpty() ||
                password.isEmpty() ||
                confirmPassword.isEmpty() ||
                interest.isEmpty()
            ) {

                showError(
                    tvError,
                    "Please fill in all fields."
                )

                return@setOnClickListener
            }


            // =================================================
            // COURSE VALIDATION
            // =================================================

            if (
                course.equals(
                    "Select Course",
                    ignoreCase = true
                )
            ) {

                showError(
                    tvError,
                    "Please select your course."
                )

                return@setOnClickListener
            }


            // =================================================
            // YEAR LEVEL VALIDATION
            // =================================================

            if (
                yearLevel.equals(
                    "Select Year Level",
                    ignoreCase = true
                )
            ) {

                showError(
                    tvError,
                    "Please select your year level."
                )

                return@setOnClickListener
            }


            // =================================================
            // SCHOOL ID VALIDATION
            //
            // Example:
            // 203892
            // =================================================

            if (!studentId.matches(Regex("^\\d+$"))) {

                showError(
                    tvError,
                    "School ID must contain numbers only."
                )

                etStudentId.requestFocus()

                return@setOnClickListener
            }


            if (!RegistrationEmailPolicy.isCampusEmail(email)) {
                showError(tvError, getString(R.string.registration_bisu_only))
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (!RegistrationEmailPolicy.matchesStudentId(email, studentId)) {
                showError(tvError, getString(R.string.registration_matching_email, "bal_${studentId}@bisu.edu.ph"))
                etEmail.requestFocus()
                return@setOnClickListener
            }

            // =================================================
            // PASSWORD LENGTH
            // =================================================

            if (
                password.length < 6
            ) {

                showError(
                    tvError,
                    "Password must be at least 6 characters."
                )

                etPassword.requestFocus()

                return@setOnClickListener
            }


            // =================================================
            // PASSWORD MATCH
            // =================================================

            if (
                password !=
                confirmPassword
            ) {

                showError(
                    tvError,
                    "Passwords do not match."
                )

                etConfirmPassword.requestFocus()

                return@setOnClickListener
            }


            // =================================================
            // BEGIN REGISTRATION
            // =================================================

            isRegistering =
                true
            registrationFlow.setBusy(true)

            btnSignUp.isEnabled =
                false

            btnSignUp.alpha =
                0.6f

            tvError.visibility =
                View.GONE


            // =================================================
            // FIREBASE AUTH
            // =================================================

            val studentData = hashMapOf<String, Any?>(
                "fullName" to fullName, "studentId" to studentId, "course" to course,
                "yearLevel" to yearLevel, "email" to email, "username" to username,
                "interest" to interest, "role" to "student", "createdAt" to System.currentTimeMillis())
            createOrSignInForRegistration(email, password).addOnCompleteListener(this) { task ->
                val user = auth.currentUser
                if (!task.isSuccessful || user == null) {
                    resetSignUpButton(btnSignUp)
                    showError(tvError, "Unable to create the account. Check your details, password, and connection, then try again.")
                    return@addOnCompleteListener
                }
                studentData["uid"] = user.uid
                val ref = db.collection("users").document(user.uid)
                ref.get().addOnSuccessListener(this) { existing ->
                    if (existing.exists()) {
                        resetSignUpButton(btnSignUp)
                        if (!user.isEmailVerified) openVerification(false)
                        else {
                            auth.signOut()
                            Toast.makeText(this, "This account already exists. Please sign in.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    } else {
                        ref.create(studentData).addOnSuccessListener(this) {
                            resetSignUpButton(btnSignUp)
                            if (!user.isEmailVerified) openVerification(true)
                            else {
                                auth.signOut()
                                Toast.makeText(this, "Account created. Please sign in.", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }.addOnFailureListener(this) {
                            resetSignUpButton(btnSignUp)
                            val message = if (it.message?.contains("username", ignoreCase = true) == true)
                                "That username is already taken. Go back to Account and choose another username."
                            else "Your sign-in was created, but the profile could not be saved. Check your connection and retry with the same email and password."
                            showError(tvError, message)
                        }
                    }
                }.addOnFailureListener(this) {
                    resetSignUpButton(btnSignUp)
                    showError(tvError, "Unable to check your account profile. Check your connection and retry.")
                }
            }
        }
        registrationFlow = RegistrationFlow(this, savedInstanceState)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        registrationFlow.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun openVerification(send: Boolean) {
        startActivity(android.content.Intent(this, VerifyEmailActivity::class.java)
            .putExtra(VerifyEmailActivity.SEND_ON_OPEN, send)
            .putExtra(VerifyEmailActivity.FROM_REGISTRATION, true))
        finish()
    }

    // Existing Firebase users can create a profile in the fresh Supabase database
    // only after proving ownership with their current Firebase password.
    private fun createOrSignInForRegistration(email: String, password: String) =
        auth.createUserWithEmailAndPassword(email, password).continueWithTask { task ->
            when {
                task.isSuccessful -> Tasks.forResult(task.result)
                task.exception is FirebaseAuthUserCollisionException ->
                    auth.signInWithEmailAndPassword(email, password)
                else -> Tasks.forException(task.exception ?: IllegalStateException("Unable to authenticate this account."))
            }
        }

    // =========================================================
    // PASSWORD VISIBILITY
    // =========================================================

    private fun togglePasswordVisibility(
        editText: EditText,
        icon: ImageView,
        isVisible: Boolean
    ) {

        if (
            isVisible
        ) {

            editText.inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

            icon.setImageResource(
                R.drawable.ic_eye_off
            )

        } else {

            editText.inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD

            icon.setImageResource(
                R.drawable.ic_eye
            )
        }


        editText.setSelection(
            editText.text.length
        )
    }


    // =========================================================
    // SHOW ERROR
    // =========================================================

    private fun showError(
        tvError: TextView,
        message: String
    ) {

        tvError.text =
            message

        tvError.visibility =
            View.VISIBLE
    }


    // =========================================================
    // RESET BUTTON
    // =========================================================

    private fun resetSignUpButton(
        btnSignUp: LinearLayout
    ) {

        isRegistering =
            false
        registrationFlow.setBusy(false)

        btnSignUp.isEnabled =
            true

        btnSignUp.alpha =
            1.0f
    }
}
