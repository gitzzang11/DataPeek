package com.datapeek.ui

import com.datapeek.data.model.UsageError

/** The last known authentication/refresh condition used by the Home UI. */
enum class SessionStatus {
    UNKNOWN,
    AUTHENTICATED,
    CHECKING,
    SESSION_EXPIRED,
    NETWORK_ERROR,
    SERVER_ERROR,
}

internal fun UsageError.toSessionStatus(): SessionStatus = when (this) {
    UsageError.NotAuthenticated,
    UsageError.SessionExpired,
    -> SessionStatus.SESSION_EXPIRED

    UsageError.NetworkUnavailable,
    UsageError.ApiTimeout,
    UsageError.ApiBodyReadFailed,
    UsageError.ApiRequestFailed,
    -> SessionStatus.NETWORK_ERROR

    is UsageError.HttpError,
    is UsageError.ParseError,
    UsageError.UnsupportedResponse,
    UsageError.RateLimited,
    -> SessionStatus.SERVER_ERROR
}

internal fun String?.toSessionStatusFromMessage(): SessionStatus? = when {
    this == null -> null
    contains("로그인") || contains("세션") -> SessionStatus.SESSION_EXPIRED
    contains("네트워크") || contains("시간이 초과") || contains("연결") || contains("읽지 못") ->
        SessionStatus.NETWORK_ERROR
    else -> SessionStatus.SERVER_ERROR
}

internal fun shouldProbeSession(hasCachedSnapshot: Boolean, locallyAuthenticated: Boolean): Boolean =
    hasCachedSnapshot || locallyAuthenticated

internal fun initialSessionStatus(hasCachedSnapshot: Boolean, locallyAuthenticated: Boolean): SessionStatus =
    if (shouldProbeSession(hasCachedSnapshot, locallyAuthenticated)) {
        SessionStatus.CHECKING
    } else {
        SessionStatus.UNKNOWN
    }

internal fun shouldStartForegroundRefresh(
    lastStartedAt: Long,
    now: Long,
    minimumIntervalMillis: Long,
): Boolean = lastStartedAt == Long.MIN_VALUE || now < lastStartedAt ||
    now - lastStartedAt >= minimumIntervalMillis
