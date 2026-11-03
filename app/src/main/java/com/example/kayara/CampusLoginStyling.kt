package com.example.kayara

import android.app.Activity
import android.widget.TextView

object CampusLoginStyling {
    fun apply(activity: Activity) {
        activity.findViewById<TextView>(R.id.loginCampusTitle).setText(R.string.campus_login_name)
    }
}
