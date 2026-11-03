package com.example.kayara.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountAccessTest {
    @Test fun studentsKeepTheirProfileWithoutManagementPermissions() {
        val access = AccountAccess.fromJson(JSONObject("""{"coordinator":false,"hasProfile":true,"emailVerified":true,"organizations":[]}"""))
        assertTrue(access.hasProfile)
        assertFalse(access.coordinator)
        assertTrue(access.organizations.isEmpty())
    }

    @Test fun assignmentsIdentifyTheirOrganizationWithoutChangingStudentAccess() {
        val access = AccountAccess.fromJson(JSONObject("""{"coordinator":false,"hasProfile":true,"emailVerified":true,
            "organizations":[{"id":"org-a","name":"First"},{"id":"org-b","name":"Second"}]}"""))
        assertTrue(access.hasProfile)
        assertFalse(access.coordinator)
        assertEquals(listOf("org-a", "org-b"), access.organizations.map { it.id })
    }

    @Test fun coordinatorDoesNotNeedAPublicStudentProfile() {
        val access = AccountAccess.fromJson(JSONObject("""{"coordinator":true,"hasProfile":false,"organizations":[]}"""))
        assertTrue(access.coordinator)
        assertFalse(access.hasProfile)
    }

    @Test fun missingOrUntrustedVerificationFlagsCannotGrantAccess() {
        for (value in listOf("null", "false", "\"true\"", "1")) {
            val access = AccountAccess.fromJson(JSONObject("""{"coordinator":false,"hasProfile":true,"organizations":[],"emailVerified":$value}"""))
            assertFalse(access.emailVerified)
        }
        assertFalse(AccountAccess.fromJson(JSONObject("""{"coordinator":false,"hasProfile":true,"organizations":[]}""")).emailVerified)
    }

    @Test(expected = org.json.JSONException::class)
    fun incompleteResponsesCannotDefaultToElevatedAccess() {
        AccountAccess.fromJson(JSONObject("""{"hasProfile":true,"organizations":[]}"""))
    }
}
