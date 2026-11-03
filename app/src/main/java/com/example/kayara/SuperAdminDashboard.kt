package com.example.kayara

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment

class SuperAdminDashboardActivity : AppCompatActivity() {

    private lateinit var navHome: LinearLayout
    private lateinit var navOrganizations: LinearLayout
    private lateinit var navNotifications: LinearLayout
    private lateinit var navDocuments: LinearLayout

    private lateinit var iconHome: ImageView
    private lateinit var iconOrganizations: ImageView
    private lateinit var iconNotifications: ImageView
    private lateinit var iconDocuments: ImageView

    private lateinit var labelHome: TextView
    private lateinit var labelOrganizations: TextView
    private lateinit var labelNotifications: TextView
    private lateinit var labelDocuments: TextView

    private var selectedPosition = 0

    private var backgroundAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // =====================================================
        // SYSTEM BAR
        // =====================================================

        window.statusBarColor =
            Color.parseColor("#7C3AED")

        window.navigationBarColor =
            Color.parseColor("#F1ECF8")

        WindowCompat
            .getInsetsController(
                window,
                window.decorView
            ).apply {

                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = true
            }

        setupSystemBars(
            Color.parseColor("#7C3AED"),
            Color.parseColor("#F1ECF8")
        )

        setContentView(
            R.layout.activity_super_admin_dashboard
        )
        UniviaInsets.apply(this)
        AccountAccessGuard.attach(this) { it.coordinator }

        // =====================================================
        // NAVIGATION
        // =====================================================

        navHome =
            findViewById(R.id.navHome)

        navOrganizations =
            findViewById(R.id.navOrganizations)

        navNotifications =
            findViewById(R.id.navNotifications)

        navDocuments =
            findViewById(R.id.navDocuments)

        iconHome =
            findViewById(R.id.iconHome)

        iconOrganizations =
            findViewById(R.id.iconOrganizations)

        iconNotifications =
            findViewById(R.id.iconNotifications)

        iconDocuments =
            findViewById(R.id.iconDocuments)

        labelHome =
            findViewById(R.id.labelHome)

        labelOrganizations =
            findViewById(R.id.labelOrganizations)

        labelNotifications =
            findViewById(R.id.labelNotifications)

        labelDocuments =
            findViewById(R.id.labelDocuments)

        // =====================================================
        // BACKGROUND
        // =====================================================

        // The brand app bar stays still while the user works.

        // =====================================================
        // NAV CLICKS
        // =====================================================

        navHome.setOnClickListener {

            if (selectedPosition != 0) {
                showHome()
            }
        }

        navOrganizations.setOnClickListener {

            if (selectedPosition != 1) {
                showOrganizations()
            }
        }

        navNotifications.setOnClickListener {

            if (selectedPosition != 2) {
                showNotifications()
            }
        }

        navDocuments.setOnClickListener {

            if (selectedPosition != 3) {
                showDocuments()
            }
        }

        // =====================================================
        // CHAT
        // =====================================================

        findViewById<View>(
            R.id.btnChat
        ).setOnClickListener {

            openAdminsGroupChat(
                this,
                true
            )
        }

        // =====================================================
        // PROFILE
        // =====================================================

        findViewById<View>(
            R.id.btnProfile
        ).setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SuperAdminProfileActivity::class.java
                )
            )
        }

        // =====================================================
        // FIRST SCREEN
        // =====================================================

        if (savedInstanceState == null) {

            showHome()

        } else {

            updateBottomNav(
                selectedPosition
            )
        }

        setupBackButton()
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun showHome() {

        openFragment(
            DashboardFragment()
        )

        updateBottomNav(0)
    }

    private fun setupSystemBars(
        statusBarColor: Int,
        navigationBarColor: Int
    ) {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            true
        )

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )

        window.statusBarColor =
            statusBarColor

        window.navigationBarColor =
            navigationBarColor

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {

            isAppearanceLightStatusBars =
                false

            isAppearanceLightNavigationBars =
                true
        }
    }

    // =========================================================
    // ORGANIZATIONS
    // =========================================================

    private fun showOrganizations() {

        openFragment(
            OrganizationsFragment()
        )

        updateBottomNav(1)
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    private fun showNotifications() {

        openFragment(
            NotificationsFragment()
        )

        updateBottomNav(2)
    }

    // =========================================================
    // DOCUMENTS
    // =========================================================

    private fun showDocuments() {

        openFragment(
            DocumentsFragment()
        )

        updateBottomNav(3)
    }

    // =========================================================
    // FRAGMENT TRANSITION
    // =========================================================

    private fun openFragment(
        fragment: Fragment
    ) {

        if (
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(
                R.id.fragmentContainer,
                fragment
            )
            .commit()
    }

    // =========================================================
    // BOTTOM NAV TRANSITION
    // =========================================================

    private fun updateBottomNav(selectedPosition: Int) {
        this.selectedPosition = selectedPosition
        UniviaNavigation.update(listOf(iconHome, iconOrganizations, iconNotifications, iconDocuments), listOf(labelHome, labelOrganizations, labelNotifications, labelDocuments), selectedPosition)
    }

    // =========================================================
    // ANIMATED BLUE BACKGROUND
    // =========================================================

    private fun startAnimatedBackground() {

        val root =
            findViewById<View>(
                R.id.rootDashboard
            )

        val header =
            findViewById<View>(
                R.id.headerLayout
            )

        val rootDrawable =
            AnimatedPaletteDrawable()

        val headerDrawable =
            AnimatedPaletteDrawable()

        // IMPORTANT:
        // Same animated palette sa root ug header.
        // Mao ni para dili lahi ang color makita sa curved edges.

        root.background =
            rootDrawable

        header.background =
            headerDrawable

        backgroundAnimator?.cancel()

        backgroundAnimator =
            ValueAnimator
                .ofFloat(
                    0f,
                    1f
                )
                .apply {

                    duration =
                        9000L

                    repeatCount =
                        ValueAnimator.INFINITE

                    repeatMode =
                        ValueAnimator.REVERSE

                    addUpdateListener {
                            animator ->

                        val phase =
                            animator
                                .animatedValue
                                    as Float

                        rootDrawable.phase =
                            phase

                        headerDrawable.phase =
                            phase

                        rootDrawable.invalidateSelf()
                        headerDrawable.invalidateSelf()
                    }

                    start()
                }
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    private fun setupBackButton() {

        onBackPressedDispatcher
            .addCallback(
                this,
                object :
                    OnBackPressedCallback(true) {

                    override fun handleOnBackPressed() {

                        val currentFragment =
                            supportFragmentManager
                                .findFragmentById(
                                    R.id.fragmentContainer
                                )

                        if (
                            currentFragment is
                                    SuperAdminApplicationReviewFragment
                        ) {

                            supportFragmentManager
                                .popBackStack()

                            return
                        }

                        if (
                            currentFragment is
                                    OrganizationsFragment
                        ) {

                            showHome()
                            return
                        }

                        if (
                            currentFragment is
                                    NotificationsFragment
                        ) {

                            showHome()
                            return
                        }

                        if (
                            currentFragment is
                                    DocumentsFragment
                        ) {

                            showHome()
                            return
                        }

                        if (
                            currentFragment is
                                    DashboardFragment
                        ) {

                            finish()
                            return
                        }

                        finish()
                    }
                }
            )
    }

    // =========================================================
    // DRAWABLE
    // =========================================================

    private class AnimatedPaletteDrawable :
        Drawable() {

        private val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        var phase:
                Float = 0f

        override fun draw(
            canvas: Canvas
        ) {

            val width =
                bounds
                    .width()
                    .toFloat()

            val height =
                bounds
                    .height()
                    .toFloat()

            if (
                width <= 0f ||
                height <= 0f
            ) {
                return
            }

            val shift =
                width *
                        0.35f *
                        phase

            paint.shader =
                LinearGradient(
                    -shift,
                    0f,
                    width + shift,
                    height,
                    intArrayOf(
                        Color.parseColor("#3B176A"),
                        Color.parseColor("#7C3AED"),
                        Color.parseColor("#7C3AED")
                    ),
                    floatArrayOf(
                        0f,
                        0.55f,
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )

            canvas.drawRect(
                bounds,
                paint
            )
        }

        override fun setAlpha(
            alpha: Int
        ) {

            paint.alpha =
                alpha
        }

        override fun setColorFilter(
            colorFilter:
            android.graphics.ColorFilter?
        ) {

            paint.colorFilter =
                colorFilter
        }

        @Deprecated(
            "Deprecated in Java"
        )
        override fun getOpacity():
                Int {

            return PixelFormat.TRANSLUCENT
        }
    }

    override fun onDestroy() {

        backgroundAnimator
            ?.cancel()

        backgroundAnimator =
            null

        super.onDestroy()
    }
}
