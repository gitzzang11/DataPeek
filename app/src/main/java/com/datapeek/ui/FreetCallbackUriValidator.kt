package com.datapeek.ui

import java.net.URI

object FreetCallbackUriValidator {
    private const val HOST = "www.freet.co.kr"
    private const val PATH = "/oauth/google"

    fun isValid(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.path == PATH
    }
}
