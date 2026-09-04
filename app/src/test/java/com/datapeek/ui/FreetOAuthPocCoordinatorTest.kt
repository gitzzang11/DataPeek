package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetOAuthPocCoordinatorTest {
    private val oauthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?code=<redacted>&state=<redacted>"

    @Test
    fun detectsObservedOAuthAndPreventsDuplicateCustomTabAttempts() {
        val coordinator = FreetOAuthPocCoordinator()

        assertEquals(oauthUrl, coordinator.onOAuthDetected(oauthUrl))
        assertNull(coordinator.onOAuthDetected(oauthUrl))
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.oauthDetected)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.customTabOpened)
    }

    @Test
    fun marksCustomTabLaunchFailureAsUnsupportedWithoutLoginSuccess() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)

        coordinator.onCustomTabLaunchSucceeded(false)

        assertEquals(PocStatus.FAIL, coordinator.state.value.customTabOpened)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED, coordinator.state.value.finalResult)
    }

    @Test
    fun handlesOnlyTheResumeAfterCustomTabAsTheBrowserReturn() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)

        assertTrue(coordinator.onActivityResumed())
        assertFalse(coordinator.onActivityResumed())
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.returnedFromBrowser)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.existingWebViewAlive)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun marksOAuthHandoffAsInProgressUntilTheAttemptReachesAStableResult() {
        val coordinator = FreetOAuthPocCoordinator()

        assertFalse(coordinator.isHandoffInProgress())
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        assertTrue(coordinator.isHandoffInProgress())

        coordinator.resetForRetry()

        assertFalse(coordinator.isHandoffInProgress())
    }

    @Test
    fun reportsCallbackNotDispatchedAfterBrowserReturn() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed()

        coordinator.markCallbackNotDispatchedToApp()

        assertEquals(PocStatus.FAIL, coordinator.state.value.callbackIntentReceived)
        assertEquals(CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP, coordinator.state.value.finalResult)
    }

    @Test
    fun reportsNotSupportedWhenExistingWebViewIsUnavailable() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)

        assertTrue(coordinator.onActivityResumed(existingWebViewAvailable = false))
        assertEquals(PocStatus.FAIL, coordinator.state.value.existingWebViewAlive)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED, coordinator.state.value.finalResult)
    }

    @Test
    fun reportsAuthenticationFailureWhenWebViewReturnsToLogin() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed()
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
