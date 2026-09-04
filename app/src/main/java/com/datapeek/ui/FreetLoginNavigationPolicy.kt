package com.datapeek.ui

import java.net.URI
import java.util.Locale

enum class FreetLoginNavigationAction {
    ALLOW_OFFICIAL,
    OPEN_CUSTOM_TAB,
    BLOCK_EXTERNAL,
}

/**
 * Navigation policy for the official Freet login WebView.
 *
 * The Google host and path below were observed in Freet's public login-page
 * OAuth configuration. They are intentionally narrower than a generic
 * Google-host allowlist so an unrelated Google page is never opened in the
 * embedded user-agent.
 */
object FreetLoginNavigationPolicy {
    const val GOOGLE_OAUTH_LAUNCH_FAILED_MESSAGE =
        "Google 로그인 창을 열 수 없습니다. 기본 브라우저가 설치되어 있는지 확인해 주세요."

    private const val OBSERVED_GOOGLE_AUTH_HOST = "accounts.google.com"
    private const val OBSERVED_GOOGLE_AUTH_PATH = "/o/oauth2/v2/auth"

    fun classify(rawUrl: String): FreetLoginNavigationAction {
        val uri = runCatching { URI(rawUrl) }.getOrNull()
            ?: return FreetLoginNavigationAction.BLOCK_EXTERNAL
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.path.orEmpty()

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return FreetLoginNavigationAction.BLOCK_EXTERNAL
        }

        if (host == OBSERVED_GOOGLE_AUTH_HOST && path == OBSERVED_GOOGLE_AUTH_PATH) {
            return FreetLoginNavigationAction.OPEN_CUSTOM_TAB
        }

        return if (isOfficialHost(host)) {
            FreetLoginNavigationAction.ALLOW_OFFICIAL
        } else {
            FreetLoginNavigationAction.BLOCK_EXTERNAL
        }
    }

    private fun isOfficialHost(host: String): Boolean =
        host == "www.freet.co.kr" || host.endsWith(".freet.co.kr") ||
            host == "www.tworld.co.kr" || host.endsWith(".tworld.co.kr")
}
