package com.example.kayara

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

/** Verification layout checks only: no real verification emails or accounts are created. */
@RunWith(AndroidJUnit4::class)
class EmailVerificationUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun completedRegistrationReturnsToSignInWithVerifiedEmail() {
        val email = "bal_123456@bisu.edu.ph"
        val intent = Intent(instrumentation.targetContext, StudentLoginActivity::class.java)
            .putExtra(StudentLoginActivity.VERIFIED_REGISTRATION_EMAIL, email)
        ActivityScenario.launch<StudentLoginActivity>(intent).use { scenario ->
            scenario.onActivity {
                assertEquals(email, it.findViewById<TextView>(R.id.etUsername).text.toString())
                assertEquals("", it.findViewById<TextView>(R.id.etPassword).text.toString())
                assertEquals(it.getString(R.string.registration_verified_sign_in),
                    it.findViewById<TextView>(R.id.tvError).text.toString())
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.tvError).visibility)
            }
        }
    }

    @Test fun verificationShowsTheAddressAndClearRecoveryActions() {
        ActivityScenario.launch<StudentLoginActivity>(Intent(instrumentation.targetContext, StudentLoginActivity::class.java)).use { scenario ->
            lateinit var activity: StudentLoginActivity
            scenario.onActivity {
                activity = it
                it.setContentView(R.layout.activity_verify_email)
                UniviaInsets.apply(it)
                it.findViewById<TextView>(R.id.verificationEmail).text = "bal_123456@bisu.edu.ph"
                it.findViewById<TextView>(R.id.verificationStatus).setText(R.string.verification_sent)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            scenario.onActivity {
                for (id in listOf(R.id.checkVerification, R.id.resendVerification, R.id.openVerificationEmail, R.id.verificationSignOut)) {
                    val button = it.findViewById<TextView>(id)
                    assertTrue(button.text.isNotBlank())
                    assertTrue("${it.resources.getResourceEntryName(id)} has height ${button.height}",
                        button.height >= (48 * it.resources.displayMetrics.density).roundToInt())
                }
                assertEquals("bal_123456@bisu.edu.ph", it.findViewById<TextView>(R.id.verificationEmail).text.toString())
                assertNull(it.findViewById<View>(R.id.etPassword))
                val root = activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val output = File(activity.getExternalFilesDir(null), "email-verification-review").apply { mkdirs() }
                File(output, "verify-email.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                bitmap.recycle()
                it.findViewById<TextView>(R.id.resendVerification).apply {
                    text = it.getString(R.string.verification_resend_wait, 60)
                    isEnabled = false
                }
                assertFalse(it.findViewById<View>(R.id.resendVerification).isEnabled)
            }
        }
    }
}
