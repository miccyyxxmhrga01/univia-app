package com.example.kayara

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

class AdminDashboardActivity :
    AppCompatActivity() {

    private var organizationId: String = ""
    private var organizationName: String = ""

    private lateinit var tvOrgName: TextView
    private lateinit var tvOrgInitials: TextView

    private lateinit var navHome: LinearLayout
    private lateinit var navMembers: LinearLayout
    private lateinit var navNotifications: LinearLayout
    private lateinit var navRequirements: LinearLayout
    private lateinit var navProfile: LinearLayout

    private lateinit var iconHome: ImageView
    private lateinit var iconMembers: ImageView
    private lateinit var iconNotifications: ImageView
    private lateinit var iconRequirements: ImageView
    private lateinit var iconProfile: ImageView

    private lateinit var labelHome: TextView
    private lateinit var labelMembers: TextView
    private lateinit var labelNotifications: TextView
    private lateinit var labelRequirements: TextView
    private lateinit var labelProfile: TextView

    private var selectedPosition = 0

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        // =====================================================
        // SYSTEM BAR COLORS
        // =====================================================

        window.statusBarColor =
            getColor(R.color.admin_end)

        window.navigationBarColor =
            getColor(R.color.bg_light)

        WindowCompat
            .getInsetsController(
                window,
                window.decorView
            )
            .apply {

                isAppearanceLightStatusBars =
                    false

                isAppearanceLightNavigationBars =
                    true
            }

        setupSystemBars(
            getColor(R.color.admin_end),
            getColor(R.color.bg_light)
        )

        // =====================================================
        // CONTENT
        // IMPORTANT: ONLY ONCE
        // =====================================================

        setContentView(
            R.layout.activity_admin_dashboard
        )
        UniviaInsets.apply(this)

        // =====================================================
        // GET ORGANIZATION FROM LOGIN
        // =====================================================

        organizationId =
            intent.getStringExtra(
                "organizationId"
            )?.trim()
                ?: ""

        organizationName =
            intent.getStringExtra(
                "organizationName"
            )?.trim()
                ?: ""

        if (organizationName.isBlank()) {
            organizationName =
                "Organization"
        }

        // =====================================================
        // DO NOT REDIRECT / SIGN OUT HERE
        // =====================================================

        if (organizationId.isBlank()) {

            Toast.makeText(
                this,
                "Organization information was not found.",
                Toast.LENGTH_LONG
            ).show()

            /*
             * IMPORTANT:
             * Do NOT call auth.signOut()
             * Do NOT start MainActivity
             * Do NOT automatically finish()
             *
             * This prevents the dashboard from immediately
             * returning to the Welcome screen.
             */
        }

        findViewById<View>(R.id.btnStudentView).setOnClickListener {
            startActivity(android.content.Intent(this, StudentDashboardActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        AccountAccessGuard.attach(this) { access -> access.organizations.any { it.id == organizationId } }

        // =====================================================
        // HEADER
        // =====================================================

        tvOrgName =
            findViewById(
                R.id.tvOrgName
            )

        tvOrgInitials =
            findViewById(
                R.id.tvOrgInitials
            )

        tvOrgName.text =
            organizationName

        tvOrgInitials.text =
            getOrganizationInitials(
                organizationName
            )

        // =====================================================
        // NAVIGATION VIEWS
        // =====================================================

        navHome =
            findViewById(
                R.id.navHome
            )

        navMembers =
            findViewById(
                R.id.navMembers
            )

        navNotifications =
            findViewById(
                R.id.navNotifications
            )

        navRequirements =
            findViewById(
                R.id.navRequirements
            )

        navProfile =
            findViewById(
                R.id.navProfile
            )

        iconHome =
            findViewById(
                R.id.iconHome
            )

        iconMembers =
            findViewById(
                R.id.iconMembers
            )

        iconNotifications =
            findViewById(
                R.id.iconNotifications
            )

        iconRequirements =
            findViewById(
                R.id.iconRequirements
            )

        iconProfile =
            findViewById(
                R.id.iconProfile
            )

        labelHome =
            findViewById(
                R.id.labelHome
            )

        labelMembers =
            findViewById(
                R.id.labelMembers
            )

        labelNotifications =
            findViewById(
                R.id.labelNotifications
            )

        labelRequirements =
            findViewById(
                R.id.labelRequirements
            )

        labelProfile =
            findViewById(
                R.id.labelProfile
            )

        // =====================================================
        // CHAT
        // =====================================================

        findViewById<FrameLayout>(
            R.id.btnChat
        ).setOnClickListener {

            if (
                organizationId
                    .isBlank()
            ) {

                Toast.makeText(
                    this,
                    "Organization not found.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            showAdminChatChooser(
                this,
                organizationId,
                organizationName
            )
        }

        // =====================================================
        // NAVIGATION CLICKS
        // =====================================================

        navHome.setOnClickListener {

            if (
                selectedPosition != 0
            ) {

                showHome()
            }
        }

        navMembers.setOnClickListener {

            if (
                selectedPosition != 1
            ) {

                showMembers()
            }
        }

        navNotifications
            .setOnClickListener {

                if (
                    selectedPosition != 2
                ) {

                    showNotifications()
                }
            }

        navRequirements
            .setOnClickListener {

                if (
                    selectedPosition != 3
                ) {

                    showRequirements()
                }
            }

        navProfile.setOnClickListener {

            if (
                selectedPosition != 4
            ) {

                showProfile()
            }
        }

        // =====================================================
        // FIRST SCREEN
        // =====================================================

        if (
            savedInstanceState ==
            null
        ) {

            showHome()

        } else {

            updateBottomNav(
                selectedPosition
            )
        }
    }

    // =========================================================
    // SYSTEM BARS
    // =========================================================

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
    // HOME
    // =========================================================

    private fun showHome() {

        loadFragment(
            AdminDashboardFragment
                .newInstance(
                    organizationId,
                    organizationName
                )
        )

        updateBottomNav(0)
    }

    // =========================================================
    // MEMBERS
    // =========================================================

    private fun showMembers() {

        loadFragment(
            MembersFragment
                .newInstance(
                    organizationId
                )
        )

        updateBottomNav(1)
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    private fun showNotifications() {

        loadFragment(
            AdminNotificationsFragment
                .newInstance(
                    organizationId
                )
        )

        updateBottomNav(2)
    }

    // =========================================================
    // REQUIREMENTS
    // =========================================================

    private fun showRequirements() {

        loadFragment(
            RequirementsFragment
                .newInstance(
                    organizationId
                )
        )

        updateBottomNav(3)
    }

    // =========================================================
    // PROFILE
    // =========================================================

    private fun showProfile() {

        loadFragment(
            OrganizationProfileFragment
                .newInstance(
                    organizationId,
                    organizationName
                )
        )

        updateBottomNav(4)
    }

    // =========================================================
    // OPEN MEMBERS TAB
    // =========================================================

    fun openMembersTab(
        filter: String = "pending"
    ) {

        loadFragment(
            MembersFragment
                .newInstance(
                    organizationId,
                    filter
                )
        )

        updateBottomNav(1)
    }

    // =========================================================
    // LOAD FRAGMENT
    // =========================================================

    private fun loadFragment(
        fragment: Fragment
    ) {

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
    // BOTTOM NAV
    // =========================================================

    private fun updateBottomNav(selectedPosition: Int) {
        this.selectedPosition = selectedPosition
        UniviaNavigation.update(listOf(iconHome, iconMembers, iconNotifications, iconRequirements, iconProfile), listOf(labelHome, labelMembers, labelNotifications, labelRequirements, labelProfile), selectedPosition)
    }

    // =========================================================
    // ORGANIZATION INITIALS
    // =========================================================

    private fun getOrganizationInitials(
        name: String
    ): String {

        val words =
            name.trim()
                .split(
                    "\\s+".toRegex()
                )
                .filter {
                    it.isNotBlank()
                }

        return when {

            words.size >= 2 ->

                words
                    .take(3)
                    .joinToString(
                        ""
                    ) {

                        it.first()
                            .uppercase()
                    }

            name.length >= 3 ->

                name.take(3)
                    .uppercase()

            else ->

                name.uppercase()
        }
    }
}