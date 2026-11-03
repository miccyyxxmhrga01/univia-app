package com.example.kayara.data

import org.json.JSONObject

data class ManagedOrganization(val id: String, val name: String)

/** Returned by the authenticated server. Local preferences never grant permissions. */
data class AccountAccess(val coordinator: Boolean, val hasProfile: Boolean, val organizations: List<ManagedOrganization>, val emailVerified: Boolean = false) {
    companion object {
        fun fromJson(value: JSONObject): AccountAccess {
            val rows = value.getJSONArray("organizations")
            return AccountAccess(value.getBoolean("coordinator"), value.getBoolean("hasProfile"),
                (0 until rows.length()).map { index ->
                    val row = rows.getJSONObject(index)
                    ManagedOrganization(row.getString("id"), row.getString("name"))
                }, value.opt("emailVerified") == true)
        }
    }
}
