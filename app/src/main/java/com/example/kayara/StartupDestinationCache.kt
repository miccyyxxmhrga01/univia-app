package com.example.kayara

import android.content.Context
import com.example.kayara.data.AccountAccess
import com.google.firebase.auth.FirebaseAuth

/** A navigation hint only. Each dashboard still checks current access with the server. */
internal object StartupDestinationCache {
    private const val FILE = "startup_destination"

    fun remember(context: Context, access: AccountAccess) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (!access.coordinator && (!access.hasProfile || !access.emailVerified)) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("user_id", uid).putBoolean("coordinator", access.coordinator).apply()
    }

    fun destination(context: Context): Class<*>? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (saved.getString("user_id", null) != uid) return null
        return if (saved.getBoolean("coordinator", false)) SuperAdminDashboardActivity::class.java
            else StudentDashboardActivity::class.java
    }
}
