package com.example.kayara

import com.example.kayara.data.EmailCodeReply
import com.example.kayara.data.SupabaseDatabase
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseTooManyRequestsException

data class VerificationIdentity(val uid: String, val email: String?)
class EmailVerificationRateLimited(cause: Throwable? = null) : Exception("Too many verification requests.", cause)

interface EmailVerificationGateway {
    val identity: VerificationIdentity?
    fun refreshVerified(): Task<Boolean>
    fun codeStatus(): Task<EmailCodeReply>
    fun sendCode(): Task<EmailCodeReply>
    fun verifyCode(code: String): Task<EmailCodeReply>
    fun sendLink(): Task<Unit>
}

/** Firebase links work even before an optional verification-code sender is connected. */
class FirebaseEmailVerificationGateway : EmailVerificationGateway {
    private val auth = FirebaseAuth.getInstance()
    private val db = SupabaseDatabase.getInstance()
    override val identity: VerificationIdentity?
        get() = auth.currentUser?.let { VerificationIdentity(it.uid, it.email) }

    override fun refreshVerified(): Task<Boolean> {
        val user = requireNotNull(auth.currentUser)
        val expected = identity
        return user.reload().continueWithTask { reloaded ->
            if (!reloaded.isSuccessful) throw reloaded.exception ?: IllegalStateException("Unable to refresh sign-in.")
            check(identity == expected) { "Sign-in changed." }
            user.getIdToken(true)
        }.continueWith { refreshed ->
            if (!refreshed.isSuccessful) throw refreshed.exception ?: IllegalStateException("Unable to refresh token.")
            check(identity == expected) { "Sign-in changed." }
            user.isEmailVerified
        }
    }

    override fun codeStatus() = db.emailCodeStatus()
    override fun sendCode() = db.sendEmailCode()
    override fun verifyCode(code: String) = db.verifyEmailCode(code)
    override fun sendLink(): Task<Unit> = requireNotNull(auth.currentUser).sendEmailVerification().continueWith { task ->
        if (!task.isSuccessful) {
            val error = task.exception ?: IllegalStateException("Unable to send verification email.")
            if (error is FirebaseTooManyRequestsException) throw EmailVerificationRateLimited(error)
            throw error
        }
        Unit
    }
}
