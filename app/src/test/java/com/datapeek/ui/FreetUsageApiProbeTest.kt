package com.datapeek.ui

import com.datapeek.data.provider.freet.FreetUsageParser
import com.datapeek.data.provider.freet.UsageParseStatus
import com.datapeek.data.util.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FreetUsageApiProbeTest {
    @Test
    fun parsesConfirmedGalaxyRawKibResponseThroughTheDirectApiPath() {
        val result = inspectFreetUsageApiBody(
            body = javaClass.getResource("/freet_usage_api_real_shape.json")!!.readText(),
            parser = FreetUsageParser(),
            httpStatus = 200,
            fetchedAt = Instant.parse("2026-08-29T12:00:00Z"),
        )

        assertEquals(UsageApiResponseStatus.HTTP_200, result.response)
        assertEquals(PocStatus.SUCCESS, result.parserStatus)
        assertEquals(UsageParseStatus.SUCCESS, result.parseDiagnostics?.snapshotCreated)
        assertEquals("308.33 MB", formatBytes(result.snapshot?.usedBytes))
        assertEquals("19.7 GB", formatBytes(result.snapshot?.remainingBytes))
    }

    @Test
    fun parsesDirectApiWebViewBodyWithoutUsingFetchEnvelope() {
        val result = inspectFreetUsageApiBody(
            body = """
                {"data":{"DATA":[{"totalText":"24.2 GB","useText":"1.5 GB","remainText":"22.7 GB"}]}}
            """.trimIndent(),
            parser = FreetUsageParser(),
            httpStatus = 200,
            fetchedAt = Instant.parse("2026-09-02T00:00:00Z"),
        )

        assertEquals(UsageApiResponseStatus.HTTP_200, result.response)
        assertEquals(PocStatus.SUCCESS, result.parserStatus)
        assertEquals(200, result.httpStatus)
        assertNotNull(result.snapshot)
        assertEquals(UsageParseStatus.SUCCESS, result.parseDiagnostics?.snapshotCreated)
        assertNotNull(result.jsonStructure)
    }

    @Test
    fun classifiesDirectApiLoginHtmlAsAuthenticationFailure() {
        val result = inspectFreetUsageApiBody(
            body = "<html><title>로그인</title></html>",
            parser = FreetUsageParser(),
            httpStatus = 200,
        )

        assertEquals(UsageApiResponseStatus.AUTH_ERROR, result.response)
        assertEquals(PocStatus.FAIL, result.parserStatus)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun classifiesMissingDirectApiBodyAsBodyReadFailure() {
        val result = inspectFreetUsageApiBody(
            body = "",
            parser = FreetUsageParser(),
            httpStatus = 200,
        )

        assertEquals(UsageApiResponseStatus.API_BODY_READ_FAILED, result.response)
        assertEquals(PocStatus.FAIL, result.parserStatus)
    }

    @Test
    fun parsesHttp200JsonBridgeResponseWithoutExposingTheBodyToState() {
        val result = inspectFreetUsageApiBridgePayload(
            payload = """
                {
                  "ok": true,
                  "status": 200,
                  "contentType": "application/json; charset=utf-8",
                  "body": "{\"data\":{\"DATA\":[{\"totalText\":\"24.2 GB\",\"useText\":\"1.5 GB\",\"remainText\":\"22.7 GB\"}]}}"
                }
            """.trimIndent(),
            parser = FreetUsageParser(),
            fetchedAt = Instant.parse("2026-09-02T00:00:00Z"),
        )

        assertEquals(UsageApiResponseStatus.HTTP_200, result.response)
        assertEquals(PocStatus.SUCCESS, result.parserStatus)
        assertEquals(200, result.httpStatus)
        assertNotNull(result.snapshot)
    }

    @Test
    fun classifiesUnauthorizedBridgeResponsesWithoutTryingTheJsonParser() {
        val result = inspectFreetUsageApiBridgePayload(
            payload = """{"ok":false,"status":403}""",
            parser = FreetUsageParser(),
        )

        assertEquals(UsageApiResponseStatus.HTTP_403, result.response)
        assertEquals(PocStatus.NOT_TESTED, result.parserStatus)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun classifiesWebViewFetchFailuresSeparatelyFromHttpFailures() {
        val result = inspectFreetUsageApiBridgePayload(
            payload = """{"ok":false,"status":0,"error":"webview_fetch"}""",
            parser = FreetUsageParser(),
        )

        assertEquals(UsageApiResponseStatus.API_WEBVIEW_FETCH_FAILED, result.response)
        assertEquals(PocStatus.NOT_TESTED, result.parserStatus)
    }

    @Test
    fun classifiesChangedJsonShapeAsParserFailure() {
        val result = inspectFreetUsageApiBridgePayload(
            payload = """
                {
                  "ok": true,
                  "status": 200,
                  "contentType": "application/json",
                  "body": "{\"message\":\"unexpected shape\"}"
                }
            """.trimIndent(),
            parser = FreetUsageParser(),
        )

        assertEquals(UsageApiResponseStatus.JSON_PARSE_FAILED, result.response)
        assertEquals(PocStatus.FAIL, result.parserStatus)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun doesNotReportParserSuccessWhenJsonHasNoUsableSnapshot() {
        val result = inspectFreetUsageApiBody(
            body = "{\"data\":{\"DATA\":[{\"total\":\"20G\",\"use\":null,\"remain\":\"19G\"}]}}",
            parser = FreetUsageParser(),
            httpStatus = 200,
        )

        assertEquals(UsageApiResponseStatus.JSON_PARSE_FAILED, result.response)
        assertEquals(PocStatus.FAIL, result.parserStatus)
        assertNull(result.snapshot)
        assertEquals(UsageParseStatus.FAIL, result.parseDiagnostics?.snapshotCreated)
        assertNotNull(result.jsonStructure)
    }
}
