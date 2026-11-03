package com.example.kayara

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.kayara.data.AccountAccess
import com.example.kayara.data.ListenerRegistration
import com.example.kayara.data.SupabaseDatabase

/** Block privileged controls until the server confirms access, and react to revocation. */
object AccountAccessGuard {
    fun attach(activity: AppCompatActivity, allowed: (AccountAccess) -> Boolean) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val cover = TextView(activity).apply {
            text = "Checking account access…"
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(activity.getColor(R.color.surface))
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 16f
            isClickable = true
            isFocusable = true
            elevation = 100f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(cover, FrameLayout.LayoutParams(-1, -1))
        var listener: ListenerRegistration? = null
        var leaving = false
        fun watch() {
            listener?.remove()
            cover.text = "Checking account access…"
            cover.visibility = View.VISIBLE
            listener = SupabaseDatabase.getInstance().watchAccountAccess { access, error ->
                if (activity.isFinishing || activity.isDestroyed || leaving) return@watchAccountAccess
                if (error != null || access == null) {
                    cover.visibility = View.VISIBLE
                    cover.text = activity.getString(R.string.access_load_failed) + "\n\nTap to retry."
                } else if (!access.coordinator && !access.emailVerified) {
                    leaving = true
                    activity.startActivity(Intent(activity, VerifyEmailActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    activity.finish()
                } else if (allowed(access)) {
                    StartupDestinationCache.remember(activity, access)
                    cover.visibility = View.GONE
                } else {
                    leaving = true
                    Toast.makeText(activity, if (access.hasProfile) R.string.manager_access_missing
                        else R.string.verification_profile_missing, Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(activity, if (access.hasProfile) StudentDashboardActivity::class.java
                        else StudentLoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    activity.finish()
                }
            }
        }
        cover.setOnClickListener { watch() }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { watch() }
            override fun onStop(owner: LifecycleOwner) {
                listener?.remove(); listener = null
                cover.visibility = View.VISIBLE
            }
            override fun onDestroy(owner: LifecycleOwner) { listener?.remove() }
        })
    }
}
