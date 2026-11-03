package com.example.kayara

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity() {
    private lateinit var pager: ViewPager2
    private lateinit var next: TextView
    private lateinit var progress: TextView
    private lateinit var dots: List<ImageView>
    private val titles = intArrayOf(R.string.onboard_discover_title, R.string.onboard_join_title, R.string.onboard_connect_title)
    private val descriptions = intArrayOf(R.string.onboard_discover_body, R.string.onboard_join_body, R.string.onboard_connect_body)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        UniviaInsets.apply(this)
        pager = findViewById(R.id.onboardingPager)
        next = findViewById(R.id.onboardingNext)
        progress = findViewById(R.id.onboardingProgress)
        dots = listOf(findViewById(R.id.onboardingDot1), findViewById(R.id.onboardingDot2), findViewById(R.id.onboardingDot3))
        pager.adapter = object : RecyclerView.Adapter<PageHolder>() {
            override fun getItemCount() = 3
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false))
            override fun onBindViewHolder(holder: PageHolder, position: Int) {
                holder.itemView.findViewById<TextView>(R.id.onboardingTitle).setText(titles[position])
                androidx.core.view.ViewCompat.setAccessibilityHeading(holder.itemView.findViewById(R.id.onboardingTitle), true)
                holder.itemView.findViewById<TextView>(R.id.onboardingBody).setText(descriptions[position])
                holder.itemView.findViewById<OnboardingArtView>(R.id.onboardingArt).page = position
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateControls(position) }
        })
        next.setOnClickListener {
            if (pager.currentItem == 2) complete() else changePage(pager.currentItem + 1)
        }
        findViewById<View>(R.id.onboardingSkip).setOnClickListener { complete() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pager.currentItem > 0) changePage(pager.currentItem - 1) else finish()
            }
        })
        pager.setCurrentItem(savedInstanceState?.getInt("page") ?: 0, false)
        updateControls(pager.currentItem)
    }

    private fun changePage(position: Int) {
        val motion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        pager.setCurrentItem(position.coerceIn(0, 2), motion)
    }

    private fun updateControls(position: Int) {
        next.setText(if (position == 2) R.string.onboard_get_started else R.string.onboard_next)
        progress.text = getString(R.string.onboard_progress, position + 1, 3)
        dots.forEachIndexed { index, dot ->
            dot.setImageResource(if (index == position) R.drawable.neo_dot_active else R.drawable.neo_dot_inactive)
            dot.layoutParams = dot.layoutParams.apply {
                width = ((if (index == position) 20 else 8) * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun complete() {
        IntroPreferences.complete(this)
        if (!intent.getBooleanExtra(EXTRA_REPLAY, false)) {
            startActivity(Intent(this, StudentLoginActivity::class.java))
        }
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", pager.currentItem)
        super.onSaveInstanceState(outState)
    }

    private class PageHolder(view: View) : RecyclerView.ViewHolder(view)
    companion object { const val EXTRA_REPLAY = "replay_onboarding" }
}
