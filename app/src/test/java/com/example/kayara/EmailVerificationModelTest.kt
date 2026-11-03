package com.example.kayara

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.example.kayara.data.DatabaseException
import com.example.kayara.data.EmailCodeReply
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/** All delivery and verification operations are fake; these tests never send email. */
class EmailVerificationModelTest {
    private val direct = Executor { it.run() }
    private val gateway = FakeGateway()
    private var time = 1000L
    private lateinit var model: EmailVerificationModel

    @Before fun setup() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        model = EmailVerificationModel(gateway, { time }, direct)
    }

    @After fun cleanup() { ArchTaskExecutor.getInstance().setDelegate(null) }

    @Test fun alreadyVerifiedSignupNeverSendsVerificationAgain() {
        gateway.firebaseVerified = true
        model.initialize(true)
        assertTrue(model.state.value!!.verified)
        assertEquals(0, gateway.linkSends)
        assertEquals(0, gateway.codeSends)
    }

    @Test fun signupAutomaticallySendsLinkWhenSenderIsNotConfigured() {
        model.initialize(true)
        assertEquals(1, gateway.linkSends)
        assertEquals(0, gateway.codeSends)
        assertEquals(EmailVerificationMethod.LINK, model.state.value!!.method)
        assertEquals(R.string.verification_link_sent, model.state.value!!.message)
        assertFalse(model.state.value!!.verified)
        assertEquals(60, model.remainingSeconds)
    }

    @Test fun olderDeployedApiFallsBackToFirebaseLinks() {
        gateway.status = Tasks.forException(DatabaseException("Invalid database request.", DatabaseException.Code.INVALID_ARGUMENT))
        model.initialize(true)
        assertEquals(1, gateway.linkSends)
        assertFalse(model.state.value!!.error)
    }

    @Test fun networkFailureDoesNotMasqueradeAsMissingSender() {
        gateway.status = Tasks.forException(DatabaseException("Offline", DatabaseException.Code.UNAVAILABLE))
        model.initialize(true)
        assertEquals(0, gateway.linkSends)
        assertFalse(model.state.value!!.verified)
        assertEquals(R.string.verification_network_error, model.state.value!!.message)
    }

    @Test fun restoredScreenAndRepeatedInitializationNeverSendAutomatically() {
        model.initialize(false)
        model.initialize(true)
        model.check()
        assertEquals(0, gateway.linkSends)
        model.resend()
        assertEquals(1, gateway.linkSends)
    }

    @Test fun resendCannotSendTwiceUntilCooldownEnds() {
        model.initialize(true)
        model.resend()
        time += 59_001
        assertEquals(1, model.remainingSeconds)
        model.resend()
        assertEquals(1, gateway.linkSends)
        time += 999
        model.resend()
        assertEquals(2, gateway.linkSends)
    }

    @Test fun checkingAnUnverifiedLinkNeverGrantsAccessOrSendsAnotherEmail() {
        model.initialize(true)
        model.verify("")
        assertFalse(model.state.value!!.verified)
        assertEquals(1, gateway.linkSends)
        assertEquals(R.string.verification_link_not_yet, model.state.value!!.message)
    }

    @Test fun verifiedFirebaseLinkContinuesWithoutOptionalOtpBackend() {
        model.initialize(true)
        gateway.firebaseVerified = true
        gateway.status = Tasks.forException(IllegalStateException("OTP backend unavailable"))
        model.check()
        assertTrue(model.state.value!!.verified)
        assertEquals(1, gateway.linkSends)
    }

    @Test fun configuredSenderUsesCodesAndOnlyServerVerificationCompletes() {
        gateway.status = Tasks.forResult(EmailCodeReply(false, true, false, 0))
        model.initialize(true)
        assertEquals(EmailVerificationMethod.CODE, model.state.value!!.method)
        assertEquals(1, gateway.codeSends)
        assertEquals(0, gateway.linkSends)
        model.verify("123")
        assertEquals(0, gateway.verifications)
        model.verify("123456")
        assertEquals(1, gateway.verifications)
        assertFalse(model.state.value!!.verified)
        gateway.codeVerified = true
        model.verify("123456")
        assertTrue(model.state.value!!.verified)
    }

    @Test fun activeCodeCanStillBeVerifiedWhenSenderIsDisconnected() {
        gateway.status = Tasks.forResult(EmailCodeReply(false, false, true, 30))
        gateway.codeVerified = true
        model.initialize(false)
        assertEquals(EmailVerificationMethod.CODE, model.state.value!!.method)
        model.verify("123456")
        assertTrue(model.state.value!!.verified)
    }

    @Test fun failedCodeDeliveryFallsBackToALinkAndStaysInLinkMode() {
        gateway.status = Tasks.forResult(EmailCodeReply(false, true, false, 0))
        gateway.codeSend = Tasks.forException(DatabaseException("Delivery failed", DatabaseException.Code.UNAVAILABLE,
            serverCode = "otp_delivery_failed"))
        model.initialize(true)
        assertEquals(1, gateway.linkSends)
        model.check()
        assertEquals(EmailVerificationMethod.LINK, model.state.value!!.method)
        assertEquals(1, gateway.codeSends)
    }

    @Test fun failedLinkDeliveryShowsAnErrorAndDoesNotClaimSuccess() {
        gateway.linkSend = Tasks.forException(IllegalStateException("Offline"))
        model.initialize(true)
        assertTrue(model.state.value!!.error)
        assertEquals(R.string.verification_link_send_error, model.state.value!!.message)
        assertFalse(model.state.value!!.verified)
    }

    @Test fun firebaseRateLimitDisablesResendTemporarily() {
        gateway.linkSend = Tasks.forException(EmailVerificationRateLimited())
        model.initialize(true)
        assertEquals(R.string.verification_too_many, model.state.value!!.message)
        assertEquals(60, model.remainingSeconds)
        model.resend()
        assertEquals(1, gateway.linkSends)
    }

    @Test fun switchingAccountDiscardsOutstandingVerificationResults() {
        val pending = TaskCompletionSource<EmailCodeReply>()
        gateway.status = Tasks.forResult(EmailCodeReply(false, true, false, 0))
        model.initialize(false)
        gateway.verification = pending.task
        model.verify("123456")
        gateway.identity = VerificationIdentity("another-user", "bal_654321@bisu.edu.ph")
        pending.setResult(EmailCodeReply(true, true, false, 0))
        assertFalse(model.state.value!!.verified)
        assertEquals(R.string.verification_session_expired, model.state.value!!.message)
        model.resend()
        assertEquals(0, gateway.codeSends)
    }

    private class FakeGateway : EmailVerificationGateway {
        override var identity: VerificationIdentity? = VerificationIdentity("student", "bal_123456@bisu.edu.ph")
        var firebaseVerified = false
        var codeVerified = false
        var status: Task<EmailCodeReply> = Tasks.forResult(EmailCodeReply(false, false, false, 0))
        var codeSend: Task<EmailCodeReply> = Tasks.forResult(EmailCodeReply(false, true, true, 60))
        var linkSend: Task<Unit> = Tasks.forResult(Unit)
        var verification: Task<EmailCodeReply>? = null
        var linkSends = 0
        var codeSends = 0
        var verifications = 0
        override fun refreshVerified() = Tasks.forResult(firebaseVerified)
        override fun codeStatus() = status
        override fun sendCode(): Task<EmailCodeReply> { codeSends++; return codeSend }
        override fun sendLink(): Task<Unit> { linkSends++; return linkSend }
        override fun verifyCode(code: String): Task<EmailCodeReply> {
            verifications++
            return verification ?: Tasks.forResult(EmailCodeReply(codeVerified, true, true, 0))
        }
    }
}
