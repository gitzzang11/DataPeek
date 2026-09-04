package com.datapeek.ui

import com.datapeek.data.model.UsageError
import com.datapeek.data.provider.freet.FreetHtmlUsageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetUsageApiResultTest {
    @Test
    fun successfulJsonSnapshotIsNotClassifiedAsAParseError() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_200,
            parserStatus = PocStatus.SUCCESS,
            httpStatus = 200,
            snapshot = testSnapshot(),
        )

        assertTrue(result.isSuccessfulUsage())
        assertFalse(result.toUsageErrorOrNull() is UsageError.ParseError)
    }

    @Test
    fun unauthorizedApiResponseIsClassifiedAsSessionExpired() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_401,
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = 401,
        )

        assertEquals(UsageError.SessionExpired, result.toUsageErrorOrNull())
        assertEquals(FreetUsageRefreshFailureReason.SESSION_EXPIRED, result.toRefreshFailureReason())
    }

    @Test
    fun forbiddenApiResponseIsClassifiedAsSessionExpired() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_403,
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = 403,
        )

        assertEquals(UsageError.SessionExpired, result.toUsageErrorOrNull())
        assertEquals(FreetUsageRefreshFailureReason.SESSION_EXPIRED, result.toRefreshFailureReason())
    }

    @Test
    fun loginHtmlResponseIsClassifiedAsSessionExpired() {
        val result = inspectFreetUsageApiBody(
            body = "<html><body>로그인이 필요합니다.</body></html>",
            parser = com.datapeek.data.provider.freet.FreetUsageParser(),
        )

        assertEquals(UsageApiResponseStatus.AUTH_ERROR, result.response)
        assertEquals(UsageError.SessionExpired, result.toUsageErrorOrNull())
    }

    @Test
    fun invalidJsonIsClassifiedAsParseError() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            parserStatus = PocStatus.FAIL,
            httpStatus = 200,
        )

        assertTrue(result.toUsageErrorOrNull() is UsageError.ParseError)
        assertEquals(FreetUsageRefreshFailureReason.JSON_PARSE_FAILED, result.toRefreshFailureReason())
    }

    @Test
    fun http200WithParserFailureCannotBeTreatedAsARequestSuccess() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_200,
            parserStatus = PocStatus.FAIL,
            httpStatus = 200,
        )

        assertFalse(result.isSuccessfulUsage())
        assertTrue(result.toUsageErrorOrNull() is UsageError.ParseError)
        assertEquals(FreetUsageRefreshFailureReason.JSON_PARSE_FAILED, result.toRefreshFailureReason())
    }

    @Test
    fun navigationTimeoutRemainsDistinctFromBodyAndParserFailures() {
        val result = FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.API_TIMEOUT,
            parserStatus = PocStatus.NOT_TESTED,
            failureReason = FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT,
        )

        assertEquals(UsageError.ApiTimeout, result.toUsageErrorOrNull())
        assertEquals(
            FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT,
            result.toRefreshFailureReason(),
        )
    }

    @Test
    fun htmlFallbackUsesTheSameUsageSnapshotContract() {
        val result = inspectFreetUsageHtmlBody(
            body = """
                <html><body><table><tr><th>Data</th><td>24.2 GB</td><td>1.5 GB</td><td>22.7 GB</td></tr></table></body></html>
            """.trimIndent(),
            parser = FreetHtmlUsageParser(),
        )

        assertTrue(result.isSuccessfulUsage())
    }

    private fun testSnapshot() = com.datapeek.data.model.UsageSnapshot(
        carrier = com.datapeek.data.model.Carrier.FREET_SKT,
        totalBytes = 10L,
        usedBytes = 2L,
        remainingBytes = 8L,
        fetchedAt = java.time.Instant.parse("2026-09-02T00:00:00Z"),
        source = com.datapeek.data.model.UsageSource.CARRIER_SERVER,
    )
}
