package com.datapeek.ui

enum class FreetLoginDiagnosticsPlacement {
    SEPARATE_SCREEN,
}

object FreetLoginLayoutPolicy {
    const val webViewUsesFullAvailableSpace = true
    const val inlineDiagnostics = false
    val diagnosticsPlacement = FreetLoginDiagnosticsPlacement.SEPARATE_SCREEN
}
