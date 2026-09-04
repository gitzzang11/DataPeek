package com.datapeek.ui

import com.datapeek.data.model.UsageError
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.provider.freet.FreetHtmlUsageParser
import com.datapeek.data.provider.freet.FreetUsageJsonStructure
import com.datapeek.data.provider.freet.FreetUsageParser
import com.datapeek.data.provider.freet.FreetUsageParseDiagnostics
import com.datapeek.data.provider.freet.UsageParseStatus
import com.datapeek.data.provider.freet.inspectFreetUsageJsonStructure
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant

internal enum class EvaluateJavascriptCallbackValueType {
    STRING,
    OBJECT,
    ARRAY,
    NULL,
    UNKNOWN,
}

internal enum class EvaluateJavascriptBodyRootType {
    OBJECT,
    ARRAY,
    STRING,
    HTML,
    UNKNOWN,
}

internal data class EvaluateJavascriptBodyDiagnostics(
    val callbackValueType: EvaluateJavascriptCallbackValueType,
    val rawCallbackLength: Int,
    val rawFirstNonWhitespace: String?,
    val jsonStringWrapperDetected: Boolean,
    val bodyDecoded: PocStatus,
    val decodedBodyLength: Int,
    val decodedFirstNonWhitespace: String?,
    val decodedRootType: EvaluateJavascriptBodyRootType,
)

internal data class EvaluateJavascriptDecodeResult(
    val body: String?,
    val diagnostics: EvaluateJavascriptBodyDiagnostics,
)

/**
 * WebView's evaluateJavascript callback is a JSON-encoded JavaScript value.
 * Decode a quoted callback exactly once; never alter the body with ad-hoc
 * backslash replacements.
 */
internal fun decodeEvaluateJavascriptString(callbackValue: String?): EvaluateJavascriptDecodeResult {
    val raw = callbackValue.orEmpty()
    val trimmedRaw = raw.trim()
    val callbackJsonValue = parseCompleteJsonValue(trimmedRaw)
    val callbackValueType = callbackJsonValue.toCallbackValueType()
    val wrapperDetected = callbackJsonValue is String && trimmedRaw.startsWith('"')
    val decodedBody = when {
        callbackValue == null -> null
        callbackJsonValue == JSONObject.NULL -> null
        wrapperDetected -> callbackJsonValue as String
        callbackJsonValue is JSONObject || callbackJsonValue is org.json.JSONArray -> raw
        else -> callbackValue
    }
    val decodedTrimmed = decodedBody?.trim().orEmpty()
    return EvaluateJavascriptDecodeResult(
        body = decodedBody,
        diagnostics = EvaluateJavascriptBodyDiagnostics(
            callbackValueType = callbackValueType,
            rawCallbackLength = callbackValue?.length ?: 0,
            rawFirstNonWhitespace = trimmedRaw.firstOrNull()?.toString(),
            jsonStringWrapperDetected = wrapperDetected,
            bodyDecoded = if (decodedBody != null) PocStatus.SUCCESS else PocStatus.FAIL,
            decodedBodyLength = decodedBody?.length ?: 0,
            decodedFirstNonWhitespace = decodedTrimmed.firstOrNull()?.toString(),
            decodedRootType = decodedBody.classifyBodyRootType(),
        ),
    )
}

internal data class FreetUsageApiProbeResult(
    val response: UsageApiResponseStatus,
    val parserStatus: PocStatus,
    val httpStatus: Int? = null,
    val snapshot: UsageSnapshot? = null,
    val parseDiagnostics: FreetUsageParseDiagnostics? = null,
    val jsonStructure: FreetUsageJsonStructure? = null,
    val bodyDiagnostics: EvaluateJavascriptBodyDiagnostics? = null,
    val failureReason: FreetUsageRefreshFailureReason = FreetUsageRefreshFailureReason.NONE,
)

internal fun FreetUsageApiProbeResult.isSuccessfulUsage(): Boolean =
    response == UsageApiResponseStatus.HTTP_200 &&
        parserStatus == PocStatus.SUCCESS &&
        snapshot != null

internal fun FreetUsageApiProbeResult.toUsageErrorOrNull(): UsageError? = when {
    isSuccessfulUsage() -> null
    failureReason != FreetUsageRefreshFailureReason.NONE -> when (failureReason) {
        FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT -> UsageError.ApiTimeout
        FreetUsageRefreshFailureReason.API_BODY_TIMEOUT -> UsageError.ApiBodyReadFailed
        FreetUsageRefreshFailureReason.API_HTTP_401,
        FreetUsageRefreshFailureReason.API_HTTP_403,
        FreetUsageRefreshFailureReason.SESSION_EXPIRED,
        -> UsageError.SessionExpired
        FreetUsageRefreshFailureReason.JSON_PARSE_FAILED ->
            UsageError.ParseError("사용량 응답을 해석하지 못했습니다.")
        FreetUsageRefreshFailureReason.NONE -> UsageError.ApiRequestFailed
        else -> UsageError.ApiRequestFailed
    }
    response == UsageApiResponseStatus.HTTP_401 ||
        response == UsageApiResponseStatus.HTTP_403 ||
        response == UsageApiResponseStatus.AUTH_ERROR -> UsageError.SessionExpired
    response == UsageApiResponseStatus.JSON_PARSE_FAILED ||
        response == UsageApiResponseStatus.NON_JSON ||
        (response == UsageApiResponseStatus.HTTP_200 && parserStatus == PocStatus.FAIL) ->
        UsageError.ParseError("사용량 JSON 응답을 해석하지 못했습니다.")
    response == UsageApiResponseStatus.API_BODY_READ_FAILED -> UsageError.ApiBodyReadFailed
    response == UsageApiResponseStatus.API_TIMEOUT -> UsageError.ApiTimeout
    response == UsageApiResponseStatus.API_REQUEST_FAILED ||
        response == UsageApiResponseStatus.API_WEBVIEW_FETCH_FAILED -> UsageError.ApiRequestFailed
    response == UsageApiResponseStatus.OTHER_ERROR && httpStatus != null -> UsageError.HttpError(httpStatus)
    else -> UsageError.ApiRequestFailed
}

internal fun FreetUsageApiProbeResult.toRefreshFailureReason(): FreetUsageRefreshFailureReason =
    if (isSuccessfulUsage()) {
        FreetUsageRefreshFailureReason.NONE
    } else if (failureReason != FreetUsageRefreshFailureReason.NONE) {
        failureReason
    } else {
        when (response) {
            UsageApiResponseStatus.HTTP_401,
            UsageApiResponseStatus.HTTP_403,
            UsageApiResponseStatus.AUTH_ERROR,
            -> FreetUsageRefreshFailureReason.SESSION_EXPIRED
            UsageApiResponseStatus.JSON_PARSE_FAILED,
            UsageApiResponseStatus.NON_JSON,
            -> FreetUsageRefreshFailureReason.JSON_PARSE_FAILED
            UsageApiResponseStatus.HTTP_200 -> FreetUsageRefreshFailureReason.JSON_PARSE_FAILED
            UsageApiResponseStatus.API_TIMEOUT -> FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT
            UsageApiResponseStatus.API_BODY_READ_FAILED -> FreetUsageRefreshFailureReason.API_BODY_TIMEOUT
            UsageApiResponseStatus.OTHER_ERROR -> FreetUsageRefreshFailureReason.API_HTTP_ERROR
            else -> FreetUsageRefreshFailureReason.API_NAVIGATION_FAILED
        }
    }

internal fun inspectFreetUsageHtmlBody(
    body: String,
    parser: FreetHtmlUsageParser,
    httpStatus: Int? = 200,
    fetchedAt: Instant = Instant.now(),
): FreetUsageApiProbeResult {
    if (body.trim().isEmpty()) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.API_BODY_READ_FAILED,
            parserStatus = PocStatus.FAIL,
            httpStatus = httpStatus,
        )
    }
    return try {
        FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_200,
            parserStatus = PocStatus.SUCCESS,
            httpStatus = httpStatus,
            snapshot = parser.parse(body, fetchedAt),
        )
    } catch (error: com.datapeek.data.model.UsageException) {
        FreetUsageApiProbeResult(
            response = if (error.error == UsageError.SessionExpired) {
                UsageApiResponseStatus.AUTH_ERROR
            } else {
                UsageApiResponseStatus.JSON_PARSE_FAILED
            },
            parserStatus = PocStatus.FAIL,
            httpStatus = httpStatus,
        )
    } catch (_: Exception) {
        FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            parserStatus = PocStatus.FAIL,
            httpStatus = httpStatus,
        )
    }
}

/** Parses the body of a completed top-level API WebView navigation. */
internal fun inspectFreetUsageApiBody(
    body: String,
    parser: FreetUsageParser,
    httpStatus: Int? = 200,
    fetchedAt: Instant = Instant.now(),
): FreetUsageApiProbeResult {
    if (httpStatus == 401 || httpStatus == 403) {
        return FreetUsageApiProbeResult(
            response = if (httpStatus == 401) {
                UsageApiResponseStatus.HTTP_401
            } else {
                UsageApiResponseStatus.HTTP_403
            },
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = httpStatus,
        )
    }
    if (httpStatus != null && httpStatus !in 200..299) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.OTHER_ERROR,
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = httpStatus,
        )
    }

    val trimmed = body.trim()
    val structure = inspectFreetUsageJsonStructure(trimmed)
    if (trimmed.isEmpty()) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.API_BODY_READ_FAILED,
            parserStatus = PocStatus.FAIL,
            httpStatus = httpStatus,
            jsonStructure = structure,
        )
    }
    if (trimmed.startsWith("<") || trimmed.contains("로그인", ignoreCase = true)) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.AUTH_ERROR,
            parserStatus = PocStatus.FAIL,
            httpStatus = httpStatus,
            jsonStructure = structure,
        )
    }

    val parsed = parser.parseDetailed(trimmed, fetchedAt)
    return if (parsed.snapshot != null && parsed.diagnostics.snapshotCreated == UsageParseStatus.SUCCESS) {
        FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.HTTP_200,
            parserStatus = PocStatus.SUCCESS,
            httpStatus = httpStatus,
            snapshot = parsed.snapshot,
            parseDiagnostics = parsed.diagnostics,
            jsonStructure = structure,
        )
    } else {
        if (parsed.error == UsageError.SessionExpired) {
            FreetUsageApiProbeResult(
                response = UsageApiResponseStatus.AUTH_ERROR,
                parserStatus = PocStatus.FAIL,
                httpStatus = httpStatus,
                parseDiagnostics = parsed.diagnostics,
                jsonStructure = structure,
            )
        } else {
            FreetUsageApiProbeResult(
                response = UsageApiResponseStatus.JSON_PARSE_FAILED,
                parserStatus = PocStatus.FAIL,
                httpStatus = httpStatus,
                parseDiagnostics = parsed.diagnostics,
                jsonStructure = structure,
            )
        }
    }
}

private fun parseCompleteJsonValue(payload: String): Any? {
    if (payload.isEmpty()) return null
    return runCatching {
        val tokener = JSONTokener(payload)
        val value = tokener.nextValue()
        check(tokener.nextClean() == '\u0000')
        value
    }.getOrNull()
}

private fun Any?.toCallbackValueType(): EvaluateJavascriptCallbackValueType = when (this) {
    is String -> EvaluateJavascriptCallbackValueType.STRING
    is JSONObject -> EvaluateJavascriptCallbackValueType.OBJECT
    is org.json.JSONArray -> EvaluateJavascriptCallbackValueType.ARRAY
    null,
    JSONObject.NULL,
    -> EvaluateJavascriptCallbackValueType.NULL
    else -> EvaluateJavascriptCallbackValueType.UNKNOWN
}

private fun String?.classifyBodyRootType(): EvaluateJavascriptBodyRootType {
    val body = this?.trim().orEmpty()
    if (body.startsWith("<")) return EvaluateJavascriptBodyRootType.HTML
    return when (val value = parseCompleteJsonValue(body)) {
        is JSONObject -> EvaluateJavascriptBodyRootType.OBJECT
        is org.json.JSONArray -> EvaluateJavascriptBodyRootType.ARRAY
        is String -> EvaluateJavascriptBodyRootType.STRING
        else -> EvaluateJavascriptBodyRootType.UNKNOWN
    }
}

/**
 * Decodes the non-sensitive bridge envelope and parses the response body in memory.
 * The body is intentionally not returned from this function or written to logs.
 */
internal fun inspectFreetUsageApiBridgePayload(
    payload: String,
    parser: FreetUsageParser,
    fetchedAt: Instant = Instant.now(),
): FreetUsageApiProbeResult {
    val envelope = runCatching { JSONObject(payload) }.getOrNull()
        ?: return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            parserStatus = PocStatus.FAIL,
        )

    if (!envelope.has("ok") || !envelope.has("status")) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            parserStatus = PocStatus.FAIL,
        )
    }

    val status = envelope.optInt("status", -1)
    if (status == 401 || status == 403) {
        return FreetUsageApiProbeResult(
            response = if (status == 401) {
                UsageApiResponseStatus.HTTP_401
            } else {
                UsageApiResponseStatus.HTTP_403
            },
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = status,
        )
    }

    if (!envelope.optBoolean("ok", false)) {
        return FreetUsageApiProbeResult(
            response = if (envelope.optString("error") == "webview_fetch") {
                UsageApiResponseStatus.API_WEBVIEW_FETCH_FAILED
            } else {
                UsageApiResponseStatus.API_REQUEST_FAILED
            },
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = status.takeIf { it >= 0 },
        )
    }

    if (status != 200) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.OTHER_ERROR,
            parserStatus = PocStatus.NOT_TESTED,
            httpStatus = status.takeIf { it >= 0 },
        )
    }

    val contentType = envelope.optString("contentType").trim()
    if (!contentType.contains("json", ignoreCase = true)) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.NON_JSON,
            parserStatus = PocStatus.FAIL,
            httpStatus = status,
        )
    }

    val body = envelope.optString("body").trim()
    if (body.isEmpty()) {
        return FreetUsageApiProbeResult(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            parserStatus = PocStatus.FAIL,
            httpStatus = status,
        )
    }

    return inspectFreetUsageApiBody(
        body = body,
        parser = parser,
        httpStatus = status,
        fetchedAt = fetchedAt,
    )
}
