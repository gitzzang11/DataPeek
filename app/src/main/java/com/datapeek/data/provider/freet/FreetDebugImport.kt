package com.datapeek.data.provider.freet

import java.net.URI
import java.util.Locale

data class FreetTraceSummary(
    val method: String,
    val officialPath: String,
    val hasCookieHeader: Boolean,
    val hasAuthorizationHeader: Boolean,
    val hasRequestBody: Boolean,
) {
    val containsSensitiveData: Boolean
        get() = hasCookieHeader || hasAuthorizationHeader
}

/**
 * Summarizes a copied cURL command without returning or persisting cookies, tokens, or body data.
 */
object FreetDebugImport {
    fun summarizeCurl(curl: String): Result<FreetTraceSummary> {
        if (curl.length > MAX_INPUT_LENGTH) return Result.failure(IllegalArgumentException("입력이 너무 깁니다."))
        val url = URL_PATTERN.find(curl)?.groupValues?.getOrNull(1)
            ?: return Result.failure(IllegalArgumentException("URL을 찾지 못했습니다."))
        val uri = runCatching { URI(url) }.getOrElse {
            return Result.failure(IllegalArgumentException("URL 형식이 아닙니다."))
        }
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "www.freet.co.kr" && host != "freet.co.kr" && !host.endsWith(".freet.co.kr")) {
            return Result.failure(IllegalArgumentException("프리티 공식 도메인만 분석할 수 있습니다."))
        }

        val headers = HEADER_PATTERN.findAll(curl).map { it.groupValues[1] }.toList()
        val normalizedHeaders = headers.map { it.substringBefore(':').trim().lowercase(Locale.US) }.toSet()
        val method = METHOD_PATTERN.find(curl)?.groupValues?.getOrNull(1)?.uppercase(Locale.US) ?: "GET"
        return Result.success(
            FreetTraceSummary(
                method = method,
                officialPath = uri.rawPath.orEmpty().ifBlank { "/" },
                hasCookieHeader = "cookie" in normalizedHeaders,
                hasAuthorizationHeader = "authorization" in normalizedHeaders ||
                    normalizedHeaders.any { it.contains("token") },
                hasRequestBody = DATA_PATTERN.containsMatchIn(curl),
            ),
        )
    }

    private const val MAX_INPUT_LENGTH = 1_000_000
    private val URL_PATTERN = Regex("(?:curl\\s+)?[\\\"']?(https?://[^\\s\\\"']+)", RegexOption.IGNORE_CASE)
    private val METHOD_PATTERN = Regex("(?:--request|-X)\\s+[\\\"']?([A-Za-z]+)", RegexOption.IGNORE_CASE)
    private val HEADER_PATTERN = Regex("(?:--header|-H)\\s+[\\\"']?([^\\\"']+)", RegexOption.IGNORE_CASE)
    private val DATA_PATTERN = Regex("(?:--data(?:-raw|-binary)?|-d)\\s+", RegexOption.IGNORE_CASE)
}
