package com.example.kayara

import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.kayara.data.DatabaseException
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.firebase.FirebaseTooManyRequestsException
import java.util.concurrent.Executor

enum class EmailVerificationMethod { LINK, CODE }

data class EmailVerificationState(
    val busy: Boolean = false,
    val message: Int = R.string.verification_link_pending,
    val error: Boolean = false,
    val verified: Boolean = false,
    val method: EmailVerificationMethod = EmailVerificationMethod.LINK
)

/** Only Firebase or the authenticated server can establish email ownership. */
class EmailVerificationModel(
    private val gateway: EmailVerificationGateway = FirebaseEmailVerificationGateway(),
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val callbackExecutor: Executor = TaskExecutors.MAIN_THREAD
) : ViewModel() {
    val state = MutableLiveData(EmailVerificationState())
    private val identity = gateway.identity
    private var initialized = false
    private var cleared = false
    private var linkSelected = false
    private var resendAfter = 0L
    val remainingSeconds: Int
        get() = ((resendAfter - now()).coerceAtLeast(0) + 999L).div(1000L).toInt()

    fun initialize(send: Boolean) {
        if (initialized) return
        initialized = true
        refresh(send)
    }

    fun check() {
        if (!initialized || state.value?.busy == true || state.value?.verified == true) return
        refresh(false)
    }

    private fun refresh(sendIfNeeded: Boolean) {
        if (!hasSession()) return
        busy(R.string.verification_checking)
        observe(gateway.refreshVerified(), success = { verified ->
            if (verified) ready()
            else if (linkSelected) useLink(sendIfNeeded)
            else observe(gateway.codeStatus(), failure = { error ->
                if (codesUnsupported(error)) useLink(sendIfNeeded) else fail(error)
            }) { result ->
                if (result.verified) ready()
                else if (!result.available && !result.active) useLink(sendIfNeeded)
                else {
                    state.value = EmailVerificationState(method = EmailVerificationMethod.CODE,
                        message = if (result.active) R.string.verification_not_yet else R.string.verification_pending)
                    waitFor(result.retryAfter)
                    if (sendIfNeeded && remainingSeconds == 0) sendRequest()
                }
            }
        })
    }

    private fun useLink(send: Boolean) {
        linkSelected = true
        state.value = EmailVerificationState(message = if (send) R.string.verification_link_pending
            else R.string.verification_link_not_yet)
        if (send && remainingSeconds == 0) sendRequest()
    }

    fun resend() {
        if (state.value?.busy == true || state.value?.verified == true || remainingSeconds > 0) return
        sendRequest()
    }

    private fun sendRequest() {
        if (!hasSession()) return
        if (state.value?.method == EmailVerificationMethod.CODE) {
            busy(R.string.verification_sending)
            observe(gateway.sendCode(), failure = { error ->
                val code = (error as? DatabaseException)?.serverCode
                if (codesUnsupported(error) || code == "otp_unavailable" || code == "otp_delivery_failed") useLink(true)
                else fail(error)
            }) { result ->
                waitFor(result.retryAfter)
                if (result.verified) ready() else state.value = current().copy(busy = false, message = R.string.verification_sent)
            }
        } else {
            busy(R.string.verification_link_sending)
            observe(gateway.sendLink()) {
                waitFor(60)
                state.value = current().copy(busy = false, message = R.string.verification_link_sent)
            }
        }
    }

    fun verify(code: String) {
        if (state.value?.busy == true || state.value?.verified == true || !hasSession()) return
        if (state.value?.method == EmailVerificationMethod.LINK) { check(); return }
        if (!VerificationCodePolicy.isValid(code)) { report(R.string.verification_code_invalid_format); return }
        busy(R.string.verification_checking)
        observe(gateway.verifyCode(code)) { result ->
            if (result.verified) ready() else report(R.string.verification_code_incorrect)
        }
    }

    private fun current() = state.value ?: EmailVerificationState()
    private fun busy(message: Int) { state.value = current().copy(busy = true, message = message, error = false) }
    private fun ready() { state.value = current().copy(busy = true, message = R.string.verification_checking, error = false, verified = true) }
    private fun waitFor(seconds: Int) {
        if (seconds > 0) resendAfter = maxOf(resendAfter, now() + seconds.coerceAtMost(86_400) * 1000L)
    }
    fun report(message: Int) { state.value = current().copy(busy = false, message = message, error = true, verified = false) }

    private fun hasSession(): Boolean {
        if (cleared) return false
        if (identity != null && identity == gateway.identity) return true
        report(R.string.verification_session_expired)
        return false
    }

    private fun <T> observe(task: Task<T>, failure: (Exception?) -> Unit = ::fail, success: (T) -> Unit) {
        task.addOnCompleteListener(callbackExecutor) {
            if (!hasSession()) return@addOnCompleteListener
            if (it.isSuccessful) success(it.result) else failure(it.exception)
        }
    }

    private fun codesUnsupported(error: Exception?): Boolean {
        val failure = error as? DatabaseException ?: return false
        // Older deployed APIs reject the optional OTP action; Firebase links still work.
        return (failure.code == DatabaseException.Code.INVALID_ARGUMENT && failure.serverCode.isNullOrBlank()) ||
            failure.serverCode in listOf("PGRST202", "42883")
    }

    private fun fail(error: Exception?) {
        val failure = error as? DatabaseException
        val rateLimited = error is FirebaseTooManyRequestsException || error is EmailVerificationRateLimited
        waitFor(failure?.retryAfterSeconds ?: if (rateLimited) 60 else 0)
        report(when {
            rateLimited -> R.string.verification_too_many
            failure?.serverCode == "otp_code_format" -> R.string.verification_code_invalid_format
            failure?.serverCode == "otp_invalid_code" -> R.string.verification_code_incorrect
            failure?.serverCode == "otp_expired" -> R.string.verification_code_expired
            failure?.serverCode == "otp_attempts_exhausted" -> R.string.verification_code_locked
            failure?.serverCode == "otp_replaced" -> R.string.verification_code_replaced
            failure?.serverCode == "otp_no_code" -> R.string.verification_pending
            failure?.serverCode == "otp_resend_limited" -> R.string.verification_too_many
            failure?.serverCode == "otp_unavailable" -> R.string.verification_unavailable
            failure?.serverCode == "otp_delivery_failed" -> R.string.verification_send_error
            current().message == R.string.verification_link_sending -> R.string.verification_link_send_error
            else -> R.string.verification_network_error
        })
    }

    override fun onCleared() { cleared = true; super.onCleared() }
}
