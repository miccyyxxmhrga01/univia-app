package com.example.kayara

import android.content.Context

object IntroPreferences {
    private const val FILE = "bbco_intro"
    private const val COMPLETE = "onboarding_complete_v1"
    fun isComplete(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(COMPLETE, false)
    fun complete(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(COMPLETE, true).apply()
    }
}
