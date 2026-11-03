package com.example.kayara

import org.junit.Assert.*
import org.junit.Test

class RegistrationEmailPolicyTest {
    @Test fun exactBisuDomainAndStudentIdAreAccepted() {
        assertTrue(RegistrationEmailPolicy.isCampusEmail("bal_123456@bisu.edu.ph"))
        assertTrue(RegistrationEmailPolicy.matchesStudentId(" BAL_123456@BISU.EDU.PH ", "123456"))
    }

    @Test fun gmailAndDomainImpersonationAreRejected() {
        for (email in listOf("student@gmail.com", "bal_123456@bisu.edu.ph.attacker.com",
            "bal_123456@sub.bisu.edu.ph", "bal_123456@bisu.edu.ph@gmail.com", "student@bisu.edu.ph ".trim() + ".evil",
            "Student <bal_123456@bisu.edu.ph>", "bal_123456 @bisu.edu.ph", "@bisu.edu.ph")) {
            assertFalse(email, RegistrationEmailPolicy.isCampusEmail(email))
        }
    }

    @Test fun schoolIdCannotBeImpersonatedWithAnotherCampusAddress() {
        assertFalse(RegistrationEmailPolicy.matchesStudentId("bal_654321@bisu.edu.ph", "123456"))
        assertFalse(RegistrationEmailPolicy.matchesStudentId("staff@bisu.edu.ph", "123456"))
        assertFalse(RegistrationEmailPolicy.matchesStudentId("bal_123456@bisu.edu.ph", "not-an-id"))
    }
}
