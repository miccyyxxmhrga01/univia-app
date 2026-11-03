package com.example.kayara

import android.app.Activity
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

/** Keep branded screens and forms clear of system bars and the keyboard. */
object UniviaInsets {
    fun apply(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) ?: return
        val campusScene = root.background is CampusBackdropDrawable
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = if (campusScene) android.graphics.Color.TRANSPARENT else androidx.core.content.ContextCompat.getColor(activity, R.color.surface)
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = if (campusScene && android.os.Build.VERSION.SDK_INT >= 26) android.graphics.Color.WHITE
            else androidx.core.content.ContextCompat.getColor(activity,
                if (android.os.Build.VERSION.SDK_INT >= 26) R.color.surface else R.color.brand_navy)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !campusScene
            isAppearanceLightNavigationBars = true
        }
        if (root.getTag(R.id.neoInsetsApplied) == true) return
        root.setTag(R.id.neoInsetsApplied, true)
        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(start + bars.left, top + bars.top, end + bars.right,
                bottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
