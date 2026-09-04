package com.datapeek.data.provider.freet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetDebugImportTest {
    @Test
    fun summarizesRequestWithoutExposingSecrets() {
        val result = FreetDebugImport.summarizeCurl(
            """curl 'https://www.freet.co.kr/member/usage' -X POST -H 'Cookie: SESSION=fixture-cookie' -H 'Authorization: Bearer fixture-token' --data-raw '{"line":"fixture-line"}'""",
        ).getOrThrow()

        assertEquals("POST", result.method)
        assertEquals("/member/usage", result.officialPath)
        assertTrue(result.hasCookieHeader)
        assertTrue(result.hasAuthorizationHeader)
        assertTrue(result.hasRequestBody)
        assertTrue(result.containsSensitiveData)
        assertFalse(result.toString().contains("fixture-cookie"))
        assertFalse(result.toString().contains("fixture-token"))
        assertFalse(result.toString().contains("fixture-line"))
    }

    @Test
    fun rejectsNonOfficialHosts() {
        assertTrue(FreetDebugImport.summarizeCurl("curl https://evil.example/usage").isFailure)
    }
}
