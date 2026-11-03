package com.example.kayara

import java.util.Locale

/** One public registration policy, shared by step validation and final submission. */
object RegistrationEmailPolicy {
    private val campusEmail = Regex("^[a-z0-9]+(?:[._+-][a-z0-9]+)*@bisu\\.edu\\.ph$")
    fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)
    fun isCampusEmail(email: String) = campusEmail.matches(normalize(email))
    fun matchesStudentId(email: String, studentId: String) =
        studentId.matches(Regex("^[0-9]+$")) &&
            normalize(email) == "bal_${studentId}@bisu.edu.ph"
}
