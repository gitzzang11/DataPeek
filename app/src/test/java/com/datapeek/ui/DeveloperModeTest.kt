package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperModeTest {
    @Test
    fun developerModeDefaultsToDisabled() {
        assertFalse(DeveloperModeState().enabled)
    }

    @Test
    fun sixVersionTapsDoNotEnableDeveloperMode() {
        var state = DeveloperModeState()

        repeat(6) { state = state.onVersionTapped().state }

        assertFalse(state.enabled)
        assertEquals(6, state.versionTapCount)
    }

    @Test
    fun seventhVersionTapEnablesDeveloperMode() {
        var state = DeveloperModeState()
        repeat(6) { state = state.onVersionTapped().state }

        val result = state.onVersionTapped()

        assertTrue(result.state.enabled)
        assertEquals(0, result.state.versionTapCount)
        assertEquals("개발자 옵션이 활성화되었습니다.", result.message)
    }

    @Test
    fun progressMessageStartsAtFourthTap() {
        var state = DeveloperModeState()
        repeat(3) { state = state.onVersionTapped().state }

        val fourth = state.onVersionTapped()
        val fifth = fourth.state.onVersionTapped()
        val sixth = fifth.state.onVersionTapped()

        assertEquals("개발자가 되려면 3단계 남았습니다.", fourth.message)
        assertEquals("개발자가 되려면 2단계 남았습니다.", fifth.message)
        assertEquals("개발자가 되려면 1단계 남았습니다.", sixth.message)
    }

    @Test
    fun enabledStateSurvivesStateRestorationAndReportsAlreadyDeveloper() {
        var state = DeveloperModeState()
        repeat(DEVELOPER_MODE_REQUIRED_TAPS) { state = state.onVersionTapped().state }
        val restored = DeveloperModeState(enabled = state.enabled)

        val result = restored.onVersionTapped()

        assertTrue(restored.enabled)
        assertEquals("이미 개발자입니다.", result.message)
    }

    @Test
    fun disabledStateHidesDeveloperOptionsMockAndDiagnostics() {
        val access = developerToolAccess(developerModeEnabled = false)

        assertFalse(access.developerOptionsVisible)
        assertFalse(access.mockEntryVisible)
        assertFalse(access.diagnosticsVisible)
        assertFalse(access.homeMockEntryVisible)
    }

    @Test
    fun enabledStateAllowsDeveloperOptionsMockAndDiagnostics() {
        val access = developerToolAccess(developerModeEnabled = true)

        assertTrue(access.developerOptionsVisible)
        assertTrue(access.mockEntryVisible)
        assertTrue(access.diagnosticsVisible)
        assertFalse(access.homeMockEntryVisible)
    }
}
