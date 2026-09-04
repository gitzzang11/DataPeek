package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetCallbackHandoffTest {
    private val oauthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?code=<redacted>&state=<redacted>"

    @Test
    fun acceptsOnlyTheObservedHttpsFreetCallbackShape() {
        assertTrue(FreetCallbackUriValidator.isValid("https://www.freet.co.kr/oauth/google?code=redacted&state=redacted"))
        assertFalse(FreetCallbackUriValidator.isValid("http://www.freet.co.kr/oauth/google?code=redacted"))
        assertFalse(FreetCallbackUriValidator.isValid("https://evil.example/oauth/google?code=redacted"))
        assertFalse(FreetCallbackUriValidator.isValid("https://www.freet.co.kr/oauth/other?code=redacted"))
    }

    @Test
    fun completesOnlyAfterCallbackIsHandedToExistingWebViewAndUsageIsAuthenticated() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed(existingWebViewAvailable = true)

        assertTrue(coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true))
        coordinator.onCallbackHandedToWebView()
        coordinator.onFreetCallbackProcessed()
        coordinator.recordUsageVerification(
            pagePath = "/mypage/usage",
            loginPageDetected = false,
            parserStatus = PocStatus.SUCCESS,
            usageApiStatus = PocStatus.SUCCESS,
        )

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackIntentReceived)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackUriValidated)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackHandedToWebView)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetCallbackProcessed)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetAuthenticationCompleted)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usagePageAuthenticated)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_WORKS, coordinator.state.value.finalResult)
    }

    @Test
    fun reportsCallbackNotDispatchedWhenBrowserReturnsWithoutCallback() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed(existingWebViewAvailable = true)

        coordinator.markCallbackNotDispatchedToApp()

        assertEquals(CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP, coordinator.state.value.finalResult)
    }

    @Test
    fun reportsNotSupportedWhenCallbackArrivesWithoutExistingWebView() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)

        assertFalse(coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = false))

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackIntentReceived)
        assertEquals(PocStatus.FAIL, coordinator.state.value.existingWebViewAlive)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED, coordinator.state.value.finalResult)
    }

    @Test
    fun reportsAuthenticationFailureWhenCallbackEndsAtLoginPage() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed(existingWebViewAvailable = true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.recordUsageVerification(
            pagePath = "/login",
            loginPageDetected = true,
            parserStatus = PocStatus.FAIL,
            usageApiStatus = PocStatus.NOT_TESTED,
        )

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED, coordinator.state.value.finalResult)
    }
}
