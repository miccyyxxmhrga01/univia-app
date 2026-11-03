package com.example.kayara

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SignInIdentityTest {
    @Test fun preservesTheUsernameSavedDuringRegistration() {
        assertEquals(listOf("CampusStudent", "campusstudent"), SignInIdentity.usernameCandidates(" CampusStudent "))
    }

    @Test fun lowercaseProfilesCanStillBeFoundWithoutDuplicateLookups() {
        assertEquals(listOf("campusstudent"), SignInIdentity.usernameCandidates("campusstudent"))
    }

    @Test fun phoneLanguageDoesNotChangeTheIdentity() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("BISU", "bisu"), SignInIdentity.usernameCandidates("BISU"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
