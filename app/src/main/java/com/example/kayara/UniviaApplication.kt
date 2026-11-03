package com.example.kayara

import android.app.Application
import com.cloudinary.android.MediaManager
import com.example.kayara.data.SupabaseDatabase

class UniviaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        SupabaseDatabase.getInstance().initializeCache(this)

        val config = hashMapOf(
            "cloud_name" to "hq6hiqhq"
        )

        MediaManager.init(this, config)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityResumed(activity: android.app.Activity) {
                val values = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
                val floating = values.getBoolean(0, false)
                values.recycle()
                if (!floating) UniviaInsets.apply(activity)
            }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
                SupabaseDatabase.getInstance().setForeground(true)
            }
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                SupabaseDatabase.getInstance().setForeground(startedActivities > 0)
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}
