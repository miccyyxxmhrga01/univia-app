package com.example.kayara

import java.util.Locale

object SignInIdentity {
    /** Older profiles retain the capitalization entered during registration. */
    fun usernameCandidates(value: String): List<String> {
        val entered = value.trim()
        return listOf(entered, entered.lowercase(Locale.ROOT)).distinct()
    }
}
