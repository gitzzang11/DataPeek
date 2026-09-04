package com.datapeek.ui

internal const val DOUBLE_BACK_EXIT_WINDOW_MILLIS = 2_000L

internal enum class AppScreen {
    HOME,
    LOGIN,
    SETTINGS,
    DEVELOPER_OPTIONS,
    DIAGNOSTICS,
}

internal fun isHomeRoot(screen: AppScreen): Boolean = screen == AppScreen.HOME

internal fun canCancelLoginFlowOnBack(handoffInProgress: Boolean): Boolean = !handoffInProgress

internal fun appScreenBackDestination(
    screen: AppScreen,
    developerModeEnabled: Boolean,
): AppScreen? = when (screen) {
    AppScreen.HOME -> null
    AppScreen.LOGIN,
    AppScreen.SETTINGS,
    -> AppScreen.HOME
    AppScreen.DEVELOPER_OPTIONS -> if (developerModeEnabled) AppScreen.SETTINGS else AppScreen.HOME
    AppScreen.DIAGNOSTICS -> if (developerModeEnabled) AppScreen.DEVELOPER_OPTIONS else AppScreen.HOME
}

internal enum class RootBackAction {
    SHOW_EXIT_MESSAGE,
    EXIT,
}

/** Activity-memory-only state for the Home root double-back exit affordance. */
internal class DoubleBackExitController(
    private val windowMillis: Long = DOUBLE_BACK_EXIT_WINDOW_MILLIS,
) {
    private var armedUntilMillis: Long = 0L

    fun onHomeBack(nowMillis: Long): RootBackAction = if (nowMillis < armedUntilMillis) {
        armedUntilMillis = 0L
        RootBackAction.EXIT
    } else {
        armedUntilMillis = nowMillis + windowMillis
        RootBackAction.SHOW_EXIT_MESSAGE
    }

    fun isArmed(nowMillis: Long): Boolean = nowMillis < armedUntilMillis

    fun reset() {
        armedUntilMillis = 0L
    }
}
