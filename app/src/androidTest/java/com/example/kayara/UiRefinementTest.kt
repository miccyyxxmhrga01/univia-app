package com.example.kayara

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local rendering and input checks only; never submits credentials or changes server data. */
@RunWith(AndroidJUnit4::class)
class UiRefinementTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun configuredContext(activity: android.app.Activity, configuration: Configuration): ContextThemeWrapper {
        val context = ContextThemeWrapper(activity, R.style.Theme_Univia).apply {
            applyOverrideConfiguration(configuration)
        }
        val activityRipple = android.util.TypedValue()
        val configuredRipple = android.util.TypedValue()
        activity.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackground, activityRipple, true)
        val resolved = context.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackground, configuredRipple, true)
        assertTrue("Configured theme must resolve ripple: activity=$activityRipple configured=$configuredRipple",
            resolved && configuredRipple.type != android.util.TypedValue.TYPE_ATTRIBUTE)
        return context
    }

    private fun draw(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height,
        Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun capture(view: View, name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null),
            "ui-refinement").apply { mkdirs() }
        val bitmap = draw(view)
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val geometry = StringBuilder()
        fun record(child: View, depth: Int) {
            val id = if (child.id == View.NO_ID) "" else
                runCatching { child.resources.getResourceEntryName(child.id) }.getOrDefault("")
            geometry.append(" ".repeat(depth)).append(child.javaClass.simpleName)
                .append(" #").append(id).append(" ").append(child.left).append(',').append(child.top)
                .append(' ').append(child.width).append('x').append(child.height)
                .append(" visibility=").append(child.visibility).append('\n')
            if (child is ViewGroup) for (index in 0 until child.childCount) record(child.getChildAt(index), depth + 1)
        }
        record(view, 0)
        File(directory, "$name-layout.txt").writeText(geometry.toString())
        bitmap.recycle()
    }

    private fun measure(view: View, width: Int, height: Int? = null) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            if (height == null) View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            else View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun compactDashboardsKeepContentAboveAccessibleNavigation() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for ((widthDp, heightDp, fontScale) in listOf(
                    Triple(360, 640, 1f), Triple(360, 640, 1.3f),
                    Triple(800, 1280, 1f), Triple(800, 360, 1.3f))) {
                    val config = Configuration(activity.resources.configuration).apply {
                        this.fontScale = fontScale
                        screenWidthDp = widthDp
                        screenHeightDp = heightDp
                    }
                    val context = configuredContext(activity, config)
                    val inflater = activity.layoutInflater.cloneInContext(context)
                    val dp = context.resources.displayMetrics.density
                    for ((layout, body, name) in listOf(
                        Triple(R.layout.activity_student_dashboard, R.layout.fragment_student_dashboard, "student"),
                        Triple(R.layout.activity_admin_dashboard, R.layout.fragment_admin_dashboard, "manager"),
                        Triple(R.layout.activity_super_admin_dashboard, R.layout.fragment_dashboard, "coordinator"))) {
                        val root = inflater.inflate(layout, null) as ViewGroup
                        inflater.inflate(body, root.findViewById<ViewGroup>(R.id.fragmentContainer), true)
                        measure(root, (widthDp * dp).toInt(), (heightDp * dp).toInt())
                        val nav = root.findViewById<ViewGroup>(R.id.navItemsContainer)
                        val tabs = (0 until nav.childCount).map { nav.getChildAt(it) as ViewGroup }
                        val icons = tabs.map { it.getChildAt(0) as ImageView }
                        val labels = tabs.map { it.getChildAt(1) as TextView }
                        UniviaNavigation.update(icons, labels, 0)
                        measure(root, (widthDp * dp).toInt(), (heightDp * dp).toInt())
                        capture(root, "$name-${widthDp}x${heightDp}dp-${fontScale}")
                        if (name == "manager" && widthDp == 360 && fontScale == 1.3f) {
                            val members = tabs.first { it.id == R.id.navMembers }.getChildAt(1) as TextView
                            assertEquals("Members must not split its final letter onto a second line", 1, members.lineCount)
                        }
                        val content = root.findViewById<View>(R.id.mainContentSurface)
                        val bottom = root.findViewById<View>(R.id.customBottomNav)
                        assertTrue("$name content must end above navigation", content.bottom <= bottom.top)
                        assertTrue("$name ${widthDp}x${heightDp}dp scale=$fontScale fragment must retain usable space",
                            root.findViewById<View>(R.id.fragmentContainer).height >= 70 * dp)
                        tabs.forEachIndexed { index, tab ->
                            assertTrue("$name navigation target too small", tab.height >= 48 * dp)
                            assertTrue("$name tab must be focusable", tab.isFocusable)
                            assertFalse("$name tab needs a spoken label", tab.contentDescription.isNullOrBlank())
                            val node = tab.createAccessibilityNodeInfo()
                            assertEquals("android.widget.Button", node.className.toString())
                            assertEquals(index == 0, node.isSelected)
                            assertNotNull(node.collectionItemInfo)
                            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, icons[index].importantForAccessibility)
                            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, labels[index].importantForAccessibility)
                        }
                    }
                }
            }
        }
    }

    @Test fun discoveryFiltersStayReachableAtLargeText() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply {
                    fontScale = 1.3f
                    screenWidthDp = 360
                    screenHeightDp = 640
                }
                val context = configuredContext(activity, config)
                val dp = context.resources.displayMetrics.density
                val root = activity.layoutInflater.cloneInContext(context).inflate(R.layout.fragment_browse_orgs, null)
                measure(root, (360 * dp).toInt(), (640 * dp).toInt())
                assertTrue(root.findViewById<View>(R.id.etSearchOrgStudent).height >= 48 * dp)
                val chips = listOf(R.id.chipAllStudentOrgs, R.id.chipJoined, R.id.chipPendingOrgs)
                    .map { root.findViewById<TextView>(it) }
                chips.forEach { chip ->
                    assertTrue(chip.isClickable && chip.isFocusable)
                    assertTrue(chip.height >= 48 * dp)
                    assertFalse(chip.text.isBlank())
                }
                val row = chips.first().parent as ViewGroup
                val scroll = row.parent as android.widget.HorizontalScrollView
                scroll.scrollTo(row.width, 0)
                assertTrue("Last filter must remain scrollable into view",
                    chips.last().right - scroll.scrollX <= scroll.width)
                capture(root, "discovery-360x640dp-1.3-filters-end")
                scroll.scrollTo(0, 0)
                capture(root, "discovery-360x640dp-1.3")
            }
        }
    }

    @Test fun feedRowsKeepLongContentAndActionsInsideTheCard() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply { fontScale = 1.3f }
                val context = configuredContext(activity, config)
                val dp = context.resources.displayMetrics.density
                for ((layout, name) in listOf(R.layout.item_feed_text_post to "text",
                    R.layout.item_feed_photo_post to "photo")) {
                    val card = activity.layoutInflater.cloneInContext(context).inflate(layout, null) as NeoCardView
                    card.findViewById<TextView>(R.id.tvPosterName).text =
                        "BISU Balilihan Campus Student Technology and Community Volunteers Organization"
                    card.findViewById<TextView>(if (name == "text") R.id.tvTextContent else R.id.tvCaption).text =
                        "Campus organization updates and application details. ".repeat(30)
                    listOf(R.id.tvLikeCount, R.id.tvCommentCount, R.id.tvShareCount).forEach {
                        card.findViewById<TextView>(it).text = "9,999"
                    }
                    card.findViewById<View>(R.id.tvSeeMore).visibility = View.VISIBLE
                    measure(card, (320 * dp).toInt())
                    val actions = listOf(R.id.btnLike, R.id.btnComment, R.id.btnShare)
                        .map { card.findViewById<ViewGroup>(it) }
                    assertTrue(actions.maxOf { it.width } - actions.minOf { it.width } <= 1)
                    actions.forEach { action ->
                        assertTrue(action.height >= 48 * dp)
                        assertTrue(action.width >= 48 * dp)
                        for (index in 0 until action.childCount) {
                            val child = action.getChildAt(index)
                            assertTrue("$name action content overflows", child.left >= 0 && child.right <= action.width)
                        }
                    }
                    assertTrue(card.findViewById<View>(R.id.tvSeeMore).height >= 48 * dp)
                    assertEquals(16 * dp, card.radius, 0.1f)
                    capture(card, "feed-$name-320dp-1.3")
                }
            }
        }
    }

    @Test fun runtimeCardShapeAndInteractionStatesRemainDistinct() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val card = activity.layoutInflater.inflate(R.layout.item_feed_text_post, null) as NeoCardView
                val dp = activity.resources.displayMetrics.density
                measure(card, (320 * dp).toInt())
                assertEquals(16 * dp, card.radius, 0.1f)
                val background = card.background
                background.state = intArrayOf(android.R.attr.state_enabled)
                val original = draw(card)
                card.radius = 4 * dp
                val square = draw(card)
                assertFalse("Runtime corner change must affect rendering", original.sameAs(square))
                original.recycle(); square.recycle()
                background.state = intArrayOf(android.R.attr.state_enabled)
                val resting = draw(card)
                background.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
                val focused = draw(card)
                background.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected)
                val selected = draw(card)
                background.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
                val pressed = draw(card)
                background.state = intArrayOf()
                val disabled = draw(card)
                assertFalse("Focus needs visible feedback", resting.sameAs(focused))
                assertFalse("Selection must differ from focus", focused.sameAs(selected))
                assertFalse("Press needs visible feedback", resting.sameAs(pressed))
                assertFalse("Disabled state needs visible feedback", resting.sameAs(disabled))
                listOf(resting, focused, selected, pressed, disabled).forEach { it.recycle() }
            }
        }
    }

    @Test fun signInExposesButtonRolesAndPasswordVisibilityWithoutSubmitting() {
        ActivityScenario.launch(StudentLoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val identity = activity.findViewById<EditText>(R.id.etUsername)
                val password = activity.findViewById<EditText>(R.id.etPassword)
                val toggle = activity.findViewById<View>(R.id.btnTogglePassword)
                val submit = activity.findViewById<View>(R.id.btnLogin)
                assertEquals("android.widget.Button", submit.createAccessibilityNodeInfo().className.toString())
                assertEquals(EditorInfo.IME_ACTION_DONE, password.imeOptions and EditorInfo.IME_MASK_ACTION)
                assertEquals(activity.getString(R.string.ui_show_password), toggle.contentDescription.toString())
                toggle.performClick()
                assertEquals(activity.getString(R.string.ui_hide_password), toggle.contentDescription.toString())
                identity.setText(""); password.setText("")
                submit.performClick()
                assertTrue(identity.hasFocus())
                val error = activity.findViewById<TextView>(R.id.tvError)
                assertEquals(View.VISIBLE, error.visibility)
                assertFalse(error.text.isBlank())
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, error.accessibilityLiveRegion)
            }
        }
    }
}
