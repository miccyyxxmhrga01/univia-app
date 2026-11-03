package com.example.kayara

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.kayara.data.SupabaseDatabase
import com.google.firebase.auth.FirebaseAuth

class VerifyEmailActivity : AppCompatActivity() {
    private val model: EmailVerificationModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())
    private var navigating = false
    private val countdown = object : Runnable {
        override fun run() { renderButtons(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { signOut(); return }
        setContentView(R.layout.activity_verify_email)
        UniviaInsets.apply(this)
        findViewById<TextView>(R.id.verificationEmail).text = user.email
        if (intent.getBooleanExtra(FROM_REGISTRATION, false)) {
            findViewById<TextView>(R.id.verificationRegistrationProgress).apply {
                visibility = View.VISIBLE
                text = getString(R.string.registration_progress, 5, 5, getString(R.string.registration_verify))
            }
            findViewById<TextView>(R.id.verificationTitle).setText(R.string.registration_verification_title)
        }
        val code = findViewById<EditText>(R.id.verificationCode)
        findViewById<View>(R.id.checkVerification).setOnClickListener { model.verify(code.text.toString().trim()) }
        code.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { model.verify(code.text.toString().trim()); true } else false
        }
        findViewById<View>(R.id.resendVerification).setOnClickListener { code.text.clear(); model.resend() }
        findViewById<View>(R.id.verificationSignOut).setOnClickListener { signOut() }
        findViewById<View>(R.id.openVerificationEmail).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.verification_mail_missing, Toast.LENGTH_LONG).show() }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { signOut() }
        })
        model.state.observe(this) { state ->
            val useCode = state.method == EmailVerificationMethod.CODE
            findViewById<View>(R.id.verificationCodeLabel).visibility = if (useCode) View.VISIBLE else View.GONE
            code.visibility = if (useCode) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.verificationInstruction).setText(
                if (useCode) R.string.verification_instruction else R.string.verification_link_instruction)
            findViewById<TextView>(R.id.checkVerification).setText(
                if (useCode) R.string.verification_check else R.string.verification_link_check)
            findViewById<TextView>(R.id.verificationStatus).apply {
                setText(state.message)
                setTextColor(getColor(if (state.error) R.color.error else R.color.text_primary))
            }
            renderButtons()
            if (state.verified && !navigating) openAccount()
        }
        // A restored screen resumes the pending verification; only fresh signup sends automatically.
        model.initialize(savedInstanceState == null && intent.getBooleanExtra(SEND_ON_OPEN, false))
    }

    override fun onStart() { super.onStart(); if (!isFinishing) handler.post(countdown) }
    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            if (model.state.value?.verified == true && !navigating) openAccount() else model.check()
        }
    }
    override fun onStop() { handler.removeCallbacks(countdown); navigating = false; super.onStop() }

    private fun renderButtons() {
        val resend = findViewById<TextView>(R.id.resendVerification) ?: return
        val busy = model.state.value?.busy == true || navigating
        val remaining = model.remainingSeconds
        resend.isEnabled = !busy && remaining == 0
        resend.text = if (remaining > 0) getString(R.string.verification_resend_wait, remaining)
            else getString(if (model.state.value?.method == EmailVerificationMethod.CODE)
                R.string.verification_resend else R.string.verification_link_resend)
        findViewById<View>(R.id.checkVerification).isEnabled = !busy
        findViewById<View>(R.id.verificationCode).isEnabled = !busy
    }

    private fun openAccount() {
        navigating = true
        SupabaseDatabase.getInstance().accountAccess().addOnSuccessListener(this) { access ->
            if (!access.coordinator && !access.emailVerified) {
                navigating = false; model.report(R.string.verification_network_error); return@addOnSuccessListener
            }
            if (intent.getBooleanExtra(FROM_REGISTRATION, false)) {
                if (!access.hasProfile || !access.emailVerified) {
                    navigating = false; model.report(R.string.verification_network_error); return@addOnSuccessListener
                }
                val email = FirebaseAuth.getInstance().currentUser?.email
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, StudentLoginActivity::class.java)
                    .putExtra(StudentLoginActivity.VERIFIED_REGISTRATION_EMAIL, email)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
                return@addOnSuccessListener
            }
            val target = when {
                access.coordinator -> SuperAdminDashboardActivity::class.java
                access.hasProfile -> StudentDashboardActivity::class.java
                else -> StudentSignUpActivity::class.java
            }
            StartupDestinationCache.remember(this, access)
            startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }.addOnFailureListener(this) {
            navigating = false
            model.report(R.string.verification_network_error)
        }
    }

    private fun signOut() {
        FirebaseAuth.getInstance().signOut()
        startActivity(Intent(this, StudentLoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        const val SEND_ON_OPEN = "verification.sendOnOpen"
        const val FROM_REGISTRATION = "verification.fromRegistration"
    }
}
