package com.datapeek.ui

import com.datapeek.data.model.UsageError
import java.time.Duration
import java.time.Instant

enum class UsageUiStatus(val label: String) {
    NORMAL("정상"),
    LOGIN_REQUIRED("로그인 필요"),
    UPDATE_FAILED("업데이트 실패"),
}

fun HomeUiState.usageUiStatus(): UsageUiStatus = when {
    sessionStatus == SessionStatus.SESSION_EXPIRED -> UsageUiStatus.LOGIN_REQUIRED
    sessionStatus == SessionStatus.NETWORK_ERROR || sessionStatus == SessionStatus.SERVER_ERROR ->
        UsageUiStatus.UPDATE_FAILED
    errorMessage?.requiresLoginMessage() == true -> UsageUiStatus.LOGIN_REQUIRED
    snapshot == null && !isAuthenticated && sessionStatus != SessionStatus.CHECKING ->
        UsageUiStatus.LOGIN_REQUIRED
    errorMessage != null -> UsageUiStatus.UPDATE_FAILED
    else -> UsageUiStatus.NORMAL
}

internal fun UsageError.requiresLogin(): Boolean = when (this) {
    UsageError.NotAuthenticated,
    UsageError.SessionExpired,
    -> true
    else -> false
}

internal fun String.requiresLoginMessage(): Boolean =
    contains("로그인") || contains("세션")

internal fun lastSuccessfulRefreshAgeLabel(fetchedAt: Instant, now: Instant): String {
    val seconds = Duration.between(fetchedAt, now).seconds.coerceAtLeast(0L)
    return when {
        seconds < 60L -> "방금 전"
        seconds < 3_600L -> "${seconds / 60L}분 전"
        seconds < 86_400L -> "${seconds / 3_600L}시간 전"
        else -> "${seconds / 86_400L}일 전"
    }
}
