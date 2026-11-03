package com.example.kayara

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local UI checks only. Never tap final submit or create Firebase/Supabase records. */
@RunWith(AndroidJUnit4::class)
class RegistrationFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val password = "Example-Only-123"

    private fun Activity.type(id: Int, text: String) = findViewById<EditText>(id).setText(text)
    private fun Activity.click(id: Int) { findViewById<View>(id).performClick() }
    private fun Activity.assertStep(number: Int) {
        assertTrue(findViewById<TextView>(R.id.registrationProgress).text.startsWith("Step $number of 5"))
        assertEquals(View.VISIBLE, findViewById<View>(listOf(R.id.registrationStep1, R.id.registrationStep2, R.id.registrationStep3, R.id.registrationStep4)[number - 1]).visibility)
        assertEquals(if (number == 4) View.VISIBLE else View.GONE, findViewById<View>(R.id.btnSignUp).visibility)
    }
    private fun Activity.fillStudentProfile() {
        type(R.id.etFullName, "Sample Student")
        type(R.id.etStudentId, "123456")
        findViewById<Spinner>(R.id.spinnerCourse).setSelection(2)
        findViewById<Spinner>(R.id.spinnerYearLevel).setSelection(1)
    }
    private fun Activity.fillAccount(organization: Boolean = false) {
        type(R.id.etEmail, if (organization) "sample@example.org" else "bal_123456@bisu.edu.ph")
        type(R.id.etUsername, "sample_user")
        type(R.id.etPassword, password)
        type(R.id.etConfirmPassword, password)
    }
    private fun allText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { allText(view.getChildAt(it)) }
        else -> ""
    }

    private fun capture(activity: Activity, name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(180)
        val directory = File(activity.getExternalFilesDir(null), "design-review").apply { mkdirs() }
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            val root = activity.window.decorView
            assertTrue(root.width > 0 && root.height > 0)
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            assertTrue("Scrollable fields need usable space", activity.findViewById<View>(R.id.signUpScroll).height > 70 * activity.resources.displayMetrics.density)
        }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun studentValidatesEachStepReviewsAndRestoresDraft() {
        ActivityScenario.launch(StudentSignUpActivity::class.java).use { scenario ->
            lateinit var current: StudentSignUpActivity
            scenario.onActivity {
                current = it
                it.assertStep(1)
                val track = it.findViewById<ViewGroup>(R.id.registrationStepTrack)
                assertEquals(5, track.childCount)
                assertFalse("Email verification cannot be skipped", track.getChildAt(4).isEnabled)
            }
            capture(current, "registration-student-profile")
            scenario.onActivity {
                it.click(R.id.registrationNext)
                it.assertStep(1)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.tvError).visibility)
                it.type(R.id.etFullName, "Sample Student")
                it.type(R.id.etStudentId, "bad-id")
                it.click(R.id.registrationNext)
                it.assertStep(1)
                it.type(R.id.etStudentId, "123456")
                it.click(R.id.registrationNext)
                assertTrue(it.findViewById<TextView>(R.id.tvError).text.contains("course"))
                it.fillStudentProfile()
                it.click(R.id.registrationNext)
                it.assertStep(2)
                it.fillAccount()
                for (invalidEmail in listOf("student@gmail.com", "bal_123456@bisu.edu.ph.attacker.com", "bal_123456@sub.bisu.edu.ph")) {
                    it.type(R.id.etEmail, invalidEmail)
                    it.click(R.id.registrationNext)
                    it.assertStep(2)
                    assertTrue(it.findViewById<TextView>(R.id.tvError).text.contains("@bisu.edu.ph"))
                }
                it.type(R.id.etEmail, "bal_000000@bisu.edu.ph")
                it.click(R.id.registrationNext)
                it.assertStep(2)
                assertTrue(it.findViewById<TextView>(R.id.tvError).text.contains("bal_123456"))
                it.type(R.id.etEmail, "bal_123456@bisu.edu.ph")
                it.type(R.id.etConfirmPassword, "different")
                it.click(R.id.registrationNext)
                it.assertStep(2)
                it.type(R.id.etConfirmPassword, password)
                val previousType = it.findViewById<EditText>(R.id.etPassword).inputType
                it.click(R.id.btnTogglePassword)
                assertNotEquals(previousType, it.findViewById<EditText>(R.id.etPassword).inputType)
                it.click(R.id.btnTogglePassword)
            }
            capture(current, "registration-student-account")
            scenario.onActivity {
                it.click(R.id.registrationNext)
                it.assertStep(3)
                it.click(R.id.registrationNext)
                it.assertStep(3)
                it.type(R.id.etInterest, "Technology, volunteering and campus activities")
            }
            capture(current, "registration-student-interests")
            scenario.onActivity { it.click(R.id.registrationNext); it.assertStep(4) }
            capture(current, "registration-student-review")
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                current = it
                it.assertStep(4)
                assertEquals("Sample Student", it.findViewById<EditText>(R.id.etFullName).text.toString())
                assertEquals(password, it.findViewById<EditText>(R.id.etPassword).text.toString())
                val review = allText(it.findViewById(R.id.registrationReviewDetails))
                assertTrue(review.contains("bal_123456@bisu.edu.ph"))
                assertTrue(review.contains("Technology"))
                assertFalse("Review must never reveal a password", review.contains(password))
                it.findViewById<ViewGroup>(R.id.registrationStepTrack).getChildAt(0).performClick()
                it.assertStep(1)
                assertEquals("123456", it.findViewById<EditText>(R.id.etStudentId).text.toString())
                it.click(R.id.registrationNext)
                it.assertStep(2)
                it.onBackPressedDispatcher.onBackPressed()
                it.assertStep(1)
            }
        }
    }

    @Test fun organizationRequestsUseExistingAccountsWithoutCredentialFields() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.setContentView(R.layout.activity_organization_application)
                assertNull(it.findViewById<View>(R.id.etPassword))
                assertNull(it.findViewById<View>(R.id.etUsername))
                assertNull(it.findViewById<View>(R.id.etEmail))
                assertNotNull(it.findViewById<View>(R.id.etOrgName))
                assertEquals(5, OrganizationApplicationModel.labels.size)
                assertEquals(5, OrganizationApplicationModel.keys.distinct().size)
                assertTrue(allText(it.window.decorView).contains("assigns management access separately"))
            }
        }
    }
}
