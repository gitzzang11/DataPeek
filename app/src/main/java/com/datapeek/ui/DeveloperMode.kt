package com.datapeek.ui

const val DEVELOPER_MODE_REQUIRED_TAPS = 7

data class DeveloperModeState(
    val enabled: Boolean = false,
    val versionTapCount: Int = 0,
)

data class DeveloperModeTapResult(
    val state: DeveloperModeState,
    val message: String? = null,
)

data class DeveloperToolAccess(
    val developerOptionsVisible: Boolean,
    val mockEntryVisible: Boolean,
    val diagnosticsVisible: Boolean,
    val homeMockEntryVisible: Boolean = false,
)

fun developerToolAccess(developerModeEnabled: Boolean): DeveloperToolAccess =
    DeveloperToolAccess(
        developerOptionsVisible = developerModeEnabled,
        mockEntryVisible = developerModeEnabled,
        diagnosticsVisible = developerModeEnabled,
    )

fun DeveloperModeState.onVersionTapped(): DeveloperModeTapResult {
    if (enabled) {
        return DeveloperModeTapResult(this, "이미 개발자입니다.")
    }

    val tapCount = (versionTapCount + 1).coerceAtMost(DEVELOPER_MODE_REQUIRED_TAPS)
    if (tapCount == DEVELOPER_MODE_REQUIRED_TAPS) {
        return DeveloperModeTapResult(
            state = copy(enabled = true, versionTapCount = 0),
            message = "개발자 옵션이 활성화되었습니다.",
        )
    }

    val remainingTaps = DEVELOPER_MODE_REQUIRED_TAPS - tapCount
    return DeveloperModeTapResult(
        state = copy(versionTapCount = tapCount),
        message = if (tapCount >= 4) {
            "개발자가 되려면 ${remainingTaps}단계 남았습니다."
        } else {
            null
        },
    )
}
