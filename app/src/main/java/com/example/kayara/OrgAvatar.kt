package com.example.kayara

import com.example.kayara.data.SupabaseDatabase
import com.example.kayara.data.ListenerRegistration

/**
 * Nagabantay sa tanang organizations ug nagahatag ug map:
 * organizationId -> avatarUrl (profile picture sa org).
 *
 * Gamiton sa tanang feed (Admin, Super Admin, Student, Org Profile).
 * Ayaw kalimot i-remove ang ListenerRegistration sa onDestroyView.
 */
fun listenToOrgAvatars(
    onChange: (Map<String, String>) -> Unit
): ListenerRegistration {

    return SupabaseDatabase.getInstance()
        .collection("organizations")
        .addSnapshotListener(useCache = true) { snapshot, error ->

            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }

            val avatars = mutableMapOf<String, String>()

            snapshot.documents.forEach { document ->

                val url = document.getString("avatarUrl")

                if (!url.isNullOrBlank()) {
                    avatars[document.id] = url
                }
            }

            onChange(avatars)
        }
}
