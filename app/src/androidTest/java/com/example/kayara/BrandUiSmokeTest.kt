package com.example.kayara

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the layout contracts changed by the redesign without creating accounts. */
@RunWith(AndroidJUnit4::class)
class BrandUiSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun launch(type: Class<out Activity>): Activity =
        instrumentation.startActivitySync(Intent(instrumentation.targetContext, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun capture(activity: Activity, name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "design-review")
        directory.mkdirs()
        lateinit var bitmap: Bitmap
        // Draw the native window so emulator system-app ANR dialogs cannot obscure it.
        instrumentation.runOnMainSync {
            val view = activity.window.decorView
            assertTrue("Window not laid out: $name", view.width > 0 && view.height > 0)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val geometry = StringBuilder()
            fun record(child: View, depth: Int) {
                val id = if (child.id == View.NO_ID) "" else runCatching { child.resources.getResourceEntryName(child.id) }.getOrDefault("")
                geometry.append(" ".repeat(depth)).append(child.javaClass.simpleName).append(" #").append(id)
                    .append(" ").append(child.left).append(',').append(child.top).append(' ')
                    .append(child.width).append('x').append(child.height)
                if (child is TextView) geometry.append(" line=").append(child.lineHeight).append(" text=").append(child.text)
                geometry.append('\n')
                if (child is ViewGroup) for (index in 0 until child.childCount) record(child.getChildAt(index), depth + 1)
            }
            record(view, 0)
            File(directory, "$name-layout.txt").writeText(geometry.toString())
        }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun signInLayoutsKeepValidationAndPasswordControls() {
        for ((type, name) in listOf(
            StudentLoginActivity::class.java to "student-login"
        )) {
            val activity = launch(type)
            capture(activity, name)
            instrumentation.runOnMainSync {
                assertEquals("Univia", activity.findViewById<TextView>(R.id.loginCampusTitle).text.toString())
                assertEquals("Univia App", activity.getString(R.string.app_name))
                val password = activity.findViewById<EditText>(R.id.etPassword)
                val previousInputType = password.inputType
                activity.findViewById<View>(R.id.btnTogglePassword).performClick()
                assertNotEquals("Password toggle must still work", previousInputType, password.inputType)
                activity.findViewById<View>(R.id.btnLogin).performClick()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvError).visibility)
                assertTrue(activity.findViewById<TextView>(R.id.tvError).text.isNotBlank())
                activity.finish()
            }
        }
    }

    @Test fun dashboardLayoutsHaveUsableContentAndNavigation() {
        val activity = launch(StudentLoginActivity::class.java)
        instrumentation.runOnMainSync {
            activity.setContentView(R.layout.activity_main)
            UniviaInsets.apply(activity)
        }
        capture(activity, "welcome")
        for ((shell, body, name) in listOf(
            Triple(R.layout.activity_student_dashboard, R.layout.fragment_student_dashboard, "student-dashboard"),
            Triple(R.layout.activity_admin_dashboard, R.layout.fragment_admin_dashboard, "org-manager-dashboard"),
            Triple(R.layout.activity_super_admin_dashboard, R.layout.fragment_dashboard, "coordinator-dashboard")
        )) {
            instrumentation.runOnMainSync {
                activity.setContentView(shell)
                UniviaInsets.apply(activity)
                val container = activity.findViewById<FrameLayout>(R.id.fragmentContainer)
                activity.layoutInflater.inflate(body, container, true)
                val nav = activity.findViewById<ViewGroup>(R.id.navItemsContainer)
                val icons = (0 until nav.childCount).map { nav.getChildAt(it) as ViewGroup }
                    .map { it.getChildAt(0) as ImageView }
                val labels = (0 until nav.childCount).map { nav.getChildAt(it) as ViewGroup }
                    .map { it.getChildAt(1) as TextView }
                UniviaNavigation.update(icons, labels, 0)
            }
            instrumentation.waitForIdleSync()
            capture(activity, name)
            instrumentation.runOnMainSync {
                assertTrue("Dashboard content is clipped: $name",
                    activity.findViewById<View>(R.id.fragmentContainer).height > 100)
            }
        }
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun introductionCompletesAndReturningLaunchSkipsOnboarding() {
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("bbco_intro", android.content.Context.MODE_PRIVATE)
        val original = preferences.all["onboarding_complete_v1"] as? Boolean
        try {
            preferences.edit().remove("onboarding_complete_v1").commit()
            val onboardingMonitor = instrumentation.addMonitor(OnboardingActivity::class.java.name, null, false)
            launch(WelcomeActivity::class.java)
            val onboarding = instrumentation.waitForMonitorWithTimeout(onboardingMonitor, 7000) as? OnboardingActivity
            assertNotNull("First launch must lead to onboarding", onboarding)
            instrumentation.removeMonitor(onboardingMonitor)
            instrumentation.waitForIdleSync()
            val activity = onboarding!!
            capture(activity, "onboarding-discover")
            val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.onboardingPager)
            for (page in 1..2) {
                instrumentation.runOnMainSync { activity.findViewById<View>(R.id.onboardingNext).performClick() }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(400)
                assertEquals(page, pager.currentItem)
                capture(activity, if (page == 1) "onboarding-membership" else "onboarding-community")
            }
            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            assertEquals(1, pager.currentItem)
            instrumentation.runOnMainSync { pager.setCurrentItem(2, false) }
            val loginMonitor = instrumentation.addMonitor(StudentLoginActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.findViewById<View>(R.id.onboardingNext).performClick() }
            val login = instrumentation.waitForMonitorWithTimeout(loginMonitor, 7000)
            instrumentation.removeMonitor(loginMonitor)
            assertNotNull(login)
            assertTrue(IntroPreferences.isComplete(context))
            instrumentation.runOnMainSync { login?.finish() }
            val returningMonitor = instrumentation.addMonitor(StudentLoginActivity::class.java.name, null, false)
            launch(WelcomeActivity::class.java)
            val returning = instrumentation.waitForMonitorWithTimeout(returningMonitor, 7000)
            instrumentation.removeMonitor(returningMonitor)
            assertNotNull("Returning users go straight to sign-in", returning)
            instrumentation.runOnMainSync { returning?.finish() }
        } finally {
            val editor = preferences.edit()
            if (original == null) editor.remove("onboarding_complete_v1") else editor.putBoolean("onboarding_complete_v1", original)
            editor.commit()
        }
    }

    @Test fun skipIsAvailable() {
        val activity = launch(OnboardingActivity::class.java)
        val loginMonitor = instrumentation.addMonitor(StudentLoginActivity::class.java.name, null, false)
        instrumentation.runOnMainSync { activity.findViewById<View>(R.id.onboardingSkip).performClick() }
        val login = instrumentation.waitForMonitorWithTimeout(loginMonitor, 7000)
        instrumentation.removeMonitor(loginMonitor)
        assertNotNull("Skip must reach sign-in", login)
        assertTrue(IntroPreferences.isComplete(instrumentation.targetContext))
        instrumentation.runOnMainSync { login?.finish() }
    }

    @Test fun sharedSignInHasUniviaBrandAndRegistration() {
        val activity = launch(StudentLoginActivity::class.java)
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Organization team access"))
            .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        val monitor = instrumentation.addMonitor(StudentSignUpActivity::class.java.name, null, false)
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.tvGoToSignUp))
            .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click())
        val destination = instrumentation.waitForMonitorWithTimeout(monitor, 7000)
        instrumentation.removeMonitor(monitor)
        assertNotNull("Student registration must remain reachable", destination)
        instrumentation.runOnMainSync { destination?.finish() }
        instrumentation.runOnMainSync { activity.finish() }
    }
}
