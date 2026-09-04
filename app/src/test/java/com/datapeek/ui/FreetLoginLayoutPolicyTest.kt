package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetLoginLayoutPolicyTest {
    @Test
    fun loginWebViewUsesAllAvailableContentSpace() {
        assertTrue(FreetLoginLayoutPolicy.webViewUsesFullAvailableSpace)
    }

    @Test
    fun diagnosticsAreNotPlacedBelowTheLoginWebView() {
        assertEquals(
            FreetLoginDiagnosticsPlacement.SEPARATE_SCREEN,
            FreetLoginLayoutPolicy.diagnosticsPlacement,
        )
        assertFalse(FreetLoginLayoutPolicy.inlineDiagnostics)
    }
}
