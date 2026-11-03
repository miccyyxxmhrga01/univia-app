package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.kayara.data.SupabaseDatabase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/** Shared sign-in: account permissions come from the authenticated server. */
class StudentLoginActivity : AppCompatActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val db = SupabaseDatabase.getInstance()
    private lateinit var error: TextView
    private lateinit var login: View
    private lateinit var loginLabel: TextView
    private var signInLabel: CharSequence = ""
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_login)
        UniviaInsets.apply(this)
        CampusLoginStyling.apply(this)
        error = findViewById(R.id.tvError)
        login = findViewById(R.id.btnLogin)
        loginLabel = (login as ViewGroup).getChildAt(0) as TextView
        signInLabel = loginLabel.text
        login.contentDescription = signInLabel
        loginLabel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        login.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        ViewCompat.setAccessibilityDelegate(login, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        })
        val identity = findViewById<EditText>(R.id.etUsername)
        val password = findViewById<EditText>(R.id.etPassword)
        intent.getStringExtra(VERIFIED_REGISTRATION_EMAIL)?.let { email ->
            identity.setText(email)
            error.setText(R.string.registration_verified_sign_in)
            error.setTextColor(getColor(R.color.text_primary))
            error.visibility = View.VISIBLE
            password.requestFocus()
        }
        findViewById<View>(R.id.tvGoToSignUp).setOnClickListener {
            if (!busy) startActivity(Intent(this, StudentSignUpActivity::class.java))
        }
        var visible = false
        val toggle = findViewById<ImageView>(R.id.btnTogglePassword)
        fun describePasswordVisibility() {
            toggle.setContentDescription(getString(if (visible) R.string.ui_hide_password else R.string.ui_show_password))
            ViewCompat.setStateDescription(toggle, getString(if (visible) R.string.ui_password_visible else R.string.ui_password_hidden))
        }
        describePasswordVisibility()
        ViewCompat.setAccessibilityDelegate(toggle, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        })
        toggle.setOnClickListener {
            visible = !visible
            password.inputType = InputType.TYPE_CLASS_TEXT or if (visible)
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            (it as ImageView).setImageResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye)
            password.setSelection(password.text.length)
            describePasswordVisibility()
        }
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        password.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event?.action == KeyEvent.ACTION_DOWN)) {
                if (!busy) login.performClick()
                true
            } else false
        }
        login.setOnClickListener {
            if (busy) return@setOnClickListener
            error.visibility = View.GONE
            val value = identity.text.toString().trim()
            val secret = password.text.toString()
            if (value.isBlank()) {
                fail(getString(R.string.sign_in_identity_required)); identity.requestFocus()
            } else if (secret.isEmpty()) {
                fail("Please enter your password."); password.requestFocus()
            } else if ('@' in value && !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                fail("Please enter a valid email address."); identity.requestFocus()
            } else {
                setBusy(true)
                // Username resolution is deliberately limited to the public identity projection.
                auth.signOut()
                // This alias only resolves the email; it grants no coordinator privileges.
                if (value.equals("superadmin", ignoreCase = true)) signIn("superadmin@gmail.com", secret)
                else if ('@' in value) signIn(RegistrationEmailPolicy.normalize(value), secret)
                else if (value.length < 3) fail("Use your email address to sign in to this account.")
                else signInWithUsername(SignInIdentity.usernameCandidates(value), secret)
            }
        }
    }

    private fun signInWithUsername(candidates: List<String>, password: String) {
        db.collection("users").whereEqualTo("username", candidates.first()).limit(1).get()
            .addOnSuccessListener(this) { result ->
                val email = result.documents.firstOrNull()?.getString("email")
                when {
                    !email.isNullOrBlank() -> signIn(email, password)
                    candidates.size > 1 -> signInWithUsername(candidates.drop(1), password)
                    else -> fail("Invalid email, username, or password.")
                }
            }.addOnFailureListener(this) { fail(it.message ?: "Unable to find your account. Try again.") }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener(this) {
            db.accountAccess().addOnSuccessListener(this) accessLoaded@{ access ->
                if (!access.coordinator && !access.hasProfile) {
                    auth.signOut()
                    fail("Your account profile was not found. Complete Student registration using your existing password.")
                    return@accessLoaded
                }
                if (!access.coordinator && !access.emailVerified) {
                    // This account still has an unfinished registration.
                    startActivity(Intent(this, VerifyEmailActivity::class.java)
                        .putExtra(VerifyEmailActivity.FROM_REGISTRATION, true))
                    setBusy(false)
                    return@accessLoaded
                }
                StartupDestinationCache.remember(this, access)
                startActivity(Intent(this, if (access.coordinator) SuperAdminDashboardActivity::class.java
                    else StudentDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }.addOnFailureListener(this) {
                auth.signOut()
                fail(it.message ?: "Unable to load account access. Please try again.")
            }
        }.addOnFailureListener(this) {
            fail(when (it) {
                is FirebaseNetworkException -> "No network connection. Please check your network connection."
                is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Invalid email, username, or password."
                else -> "Sign-in failed. Please try again."
            })
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        login.isEnabled = !value
        login.alpha = 1f
        loginLabel.text = if (value) getString(R.string.ui_signing_in) else signInLabel
        login.contentDescription = loginLabel.text
        ViewCompat.setStateDescription(login, if (value) getString(R.string.ui_in_progress) else null)
        findViewById<View>(R.id.tvGoToSignUp).isEnabled = !value
    }

    private fun fail(message: String) {
        setBusy(false)
        error.setTextColor(getColor(R.color.error))
        error.text = message
        error.visibility = View.VISIBLE
    }

    companion object { const val VERIFIED_REGISTRATION_EMAIL = "registration.verifiedEmail" }
}
