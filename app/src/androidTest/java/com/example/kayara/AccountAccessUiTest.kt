package com.example.kayara

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Render new access surfaces without creating profiles, signing in, or changing assignments. */
@RunWith(AndroidJUnit4::class)
class AccountAccessUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun capture(activity: Activity, name: String) {
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val directory = File(activity.getExternalFilesDir(null), "account-access-review").apply { mkdirs() }
        instrumentation.runOnMainSync {
            val view = activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun sharedSignInAndAssignmentLayoutsAreUsable() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, StudentLoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        capture(activity, "shared-sign-in")
        instrumentation.runOnMainSync {
            assertEquals(activity.getString(R.string.sign_in_identity_hint), activity.findViewById<TextView>(R.id.etUsername).hint.toString())
        }
        for ((layout, name) in listOf(R.layout.activity_manage_org_access to "coordinator-access",
            R.layout.activity_organization_application to "organization-application")) {
            instrumentation.runOnMainSync {
                activity.setContentView(layout)
                UniviaInsets.apply(activity)
                if (layout == R.layout.activity_organization_application) {
                    val holder = activity.findViewById<android.widget.LinearLayout>(R.id.applicationDocumentButtons)
                    OrganizationApplicationModel.labels.forEach { label ->
                        holder.addView(com.google.android.material.button.MaterialButton(activity, null,
                            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            isAllCaps = false
                            minHeight = (54 * resources.displayMetrics.density).toInt()
                            text = label + "\nAttach document"
                        }, android.widget.LinearLayout.LayoutParams(-1, -2))
                    }
                }
            }
            capture(activity, name)
            if (layout == R.layout.activity_organization_application) {
                instrumentation.runOnMainSync {
                    val scroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.applicationScroll)
                    assertEquals("Application should open at its heading", 0, scroll.scrollY)
                    scroll.fullScroll(View.FOCUS_DOWN)
                }
                capture(activity, "organization-application-documents")
            }
            instrumentation.runOnMainSync {
                if (layout == R.layout.activity_manage_org_access) {
                    assertTrue(activity.findViewById<View>(R.id.managerSearch).height >= 48 * activity.resources.displayMetrics.density)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.btnAssignManager).visibility)
                } else {
                    assertNull(activity.findViewById<View>(R.id.etPassword))
                    assertNotNull(activity.findViewById<View>(R.id.applicationDocumentButtons))
                }
            }
        }
        instrumentation.runOnMainSync { activity.finish() }
    }
}
