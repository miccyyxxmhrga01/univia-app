package com.example.kayara

object VerificationCodePolicy {
    fun isValid(code: String) = code.matches(Regex("^[0-9]{6}$"))
}
