package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBackNavigationTest {
    @Test
    fun firstHomeBackArmsExitAndRequestsUserMessage() {
        val controller = DoubleBackExitController()

        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(1_000L))
        assertTrue(controller.isArmed(1_001L))
    }

    @Test
    fun secondHomeBackWithinTwoSecondsRequestsActivityExit() {
        val controller = DoubleBackExitController()
        controller.onHomeBack(1_000L)

        assertEquals(RootBackAction.EXIT, controller.onHomeBack(2_999L))
        assertFalse(controller.isArmed(2_999L))
    }

    @Test
    fun backAtTheTwoSecondBoundaryStartsANewExitWindow() {
        val controller = DoubleBackExitController()
        controller.onHomeBack(1_000L)

        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(3_000L))
        assertTrue(controller.isArmed(3_001L))
    }

    @Test
    fun repeatedTimeoutWindowsNeverLeakAnArmedState() {
        val controller = DoubleBackExitController()

        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(0L))
        assertFalse(controller.isArmed(2_000L))
        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(2_001L))
        assertFalse(controller.isArmed(4_001L))
        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(4_002L))
    }

    @Test
    fun navigationAwayFromHomeResetsPendingExit() {
        val controller = DoubleBackExitController()
        controller.onHomeBack(1_000L)

        controller.reset()

        assertEquals(RootBackAction.SHOW_EXIT_MESSAGE, controller.onHomeBack(1_001L))
    }

    @Test
    fun explicitResetAlsoSupportsActivityRecreationSemantics() {
        val controller = DoubleBackExitController()
        controller.onHomeBack(1_000L)

        controller.reset()

        assertFalse(controller.isArmed(1_001L))
    }

    @Test
    fun homeIsTheOnlyRootThatArmsDoubleBackExit() {
        assertTrue(isHomeRoot(AppScreen.HOME))
        assertFalse(isHomeRoot(AppScreen.SETTINGS))
        assertFalse(isHomeRoot(AppScreen.LOGIN))
    }

    @Test
    fun appScreensUseTheExistingParentFlow() {
        assertNull(appScreenBackDestination(AppScreen.HOME, developerModeEnabled = false))
        assertEquals(AppScreen.HOME, appScreenBackDestination(AppScreen.LOGIN, developerModeEnabled = false))
        assertEquals(AppScreen.HOME, appScreenBackDestination(AppScreen.SETTINGS, developerModeEnabled = false))
        assertEquals(
            AppScreen.SETTINGS,
            appScreenBackDestination(AppScreen.DEVELOPER_OPTIONS, developerModeEnabled = true),
        )
        assertEquals(
            AppScreen.DEVELOPER_OPTIONS,
            appScreenBackDestination(AppScreen.DIAGNOSTICS, developerModeEnabled = true),
        )
    }

    @Test
    fun hiddenDeveloperScreensFallBackToSettingsUiParentWithoutExit() {
        assertEquals(
            AppScreen.HOME,
            appScreenBackDestination(AppScreen.DEVELOPER_OPTIONS, developerModeEnabled = false),
        )
        assertEquals(
            AppScreen.HOME,
            appScreenBackDestination(AppScreen.DIAGNOSTICS, developerModeEnabled = false),
        )
    }

    @Test
    fun loginBackCanCancelOnlyBeforeOAuthHandoffStarts() {
        assertTrue(canCancelLoginFlowOnBack(handoffInProgress = false))
        assertFalse(canCancelLoginFlowOnBack(handoffInProgress = true))
    }
}
