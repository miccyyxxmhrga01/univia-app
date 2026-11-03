package com.example.kayara

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle

/** A short, interruptible brand reveal before onboarding or sign-in. */
class WelcomeActivity : AppCompatActivity() {
    private var sequence: AnimatorSet? = null
    private var hasPlayed = false
    private var navigating = false
    private val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val easeInOut = PathInterpolator(0.77f, 0f, 0.175f, 1f)
    private val textIds = listOf(R.id.introTitle, R.id.introSubtitle, R.id.introCampus)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Returning users already know the brand; avoid inflating and replaying the intro.
        if (IntroPreferences.isComplete(this)) {
            continueFlow()
            return
        }
        setContentView(R.layout.activity_main)
        UniviaInsets.apply(this)
        hasPlayed = savedInstanceState?.getBoolean("intro_played") ?: false
        if (!hasPlayed && motionEnabled()) prepareReveal()
    }

    override fun onResume() {
        super.onResume()
        if (navigating) return
        if (hasPlayed || !motionEnabled()) {
            restoreFinalState()
            continueFlow()
        } else {
            hasPlayed = true
            playReveal()
        }
    }

    private fun motionEnabled() =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun prepareReveal() {
        findViewById<View>(R.id.introLogo).apply {
            alpha = 0f; scaleX = 0.94f; scaleY = 0.94f
        }
        findViewById<View>(R.id.introLogoMark).apply {
            scaleX = 0.90f; scaleY = 0.90f
            translationY = 14f * resources.displayMetrics.density
        }
        textIds.forEach { id ->
            findViewById<View>(id).apply {
                alpha = 0f
                translationY = if (id == R.id.introCampus) 0f else 8f * resources.displayMetrics.density
            }
        }
    }

    private fun animate(view: View, property: String, end: Float, duration: Long, delay: Long = 0L) =
        ObjectAnimator.ofFloat(view, property, end).apply {
            this.duration = duration
            startDelay = delay
            interpolator = easeOut
        }

    private fun playReveal() {
        val disc = findViewById<View>(R.id.introLogo)
        val mark = findViewById<View>(R.id.introLogoMark)
        val reveal = mutableListOf<Animator>(
            animate(disc, "alpha", 1f, 320L),
            animate(disc, "scaleX", 1f, 600L),
            animate(disc, "scaleY", 1f, 600L),
            animate(mark, "scaleX", 1.025f, 600L),
            animate(mark, "scaleY", 1.025f, 600L),
            animate(mark, "translationY", 0f, 600L)
        )
        textIds.forEachIndexed { index, id ->
            val text = findViewById<View>(id)
            val delay = 240L + index * 80L
            reveal += animate(text, "alpha", 1f, 300L, delay)
            reveal += animate(text, "translationY", 0f, 300L, delay)
        }
        val entrance = AnimatorSet().apply { playTogether(reveal) }
        val settle = AnimatorSet().apply {
            playTogether(animate(mark, "scaleX", 1f, 200L), animate(mark, "scaleY", 1f, 200L))
            interpolator = easeInOut
        }
        // Keep the brand visible while the destination prepares its first frame.
        // Android crossfades the windows once sign-in or onboarding is ready.
        val hold = ValueAnimator.ofFloat(0f, 1f).apply { duration = 300L }
        sequence = AnimatorSet().apply {
            playSequentially(entrance, settle, hold)
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continueFlow()
                }
            })
            start()
        }
    }

    private fun restoreFinalState() {
        (textIds + listOf(R.id.introRoot, R.id.introLogo, R.id.introLogoMark)).forEach { id ->
            findViewById<View>(id).apply {
                alpha = 1f; translationY = 0f; scaleX = 1f; scaleY = 1f
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun continueFlow() {
        if (navigating || isFinishing || isDestroyed) return
        navigating = true
        val target = if (IntroPreferences.isComplete(this))
            StartupDestinationCache.destination(this) ?: StudentLoginActivity::class.java
        else OnboardingActivity::class.java
        startActivity(Intent(this, target))
        if (motionEnabled()) overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onPause() {
        sequence?.cancel()
        sequence = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("intro_played", hasPlayed)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        sequence?.cancel()
        sequence = null
        super.onDestroy()
    }
}
