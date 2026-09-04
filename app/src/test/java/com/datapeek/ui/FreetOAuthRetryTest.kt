package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetOAuthRetryTest {
    private val oauthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?code=redacted&state=redacted"

    @Test
    fun retryResetsTransientOAuthStateAndAllowsASecondAttempt() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onActivityResumed()
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.resetForRetry()

        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.oauthDetected)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.customTabOpened)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.callbackIntentReceived)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.callbackHandedToWebView)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.returnedFromBrowser)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.freetSessionAppearsEstablished)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.freetAuthenticationCompleted)
        assertEquals(oauthUrl, coordinator.onOAuthDetected(oauthUrl))
        assertNull(coordinator.onOAuthDetected(oauthUrl))
        coordinator.onCustomTabLaunchSucceeded(true)
        assertTrue(coordinator.onActivityResumed())
        assertFalse(coordinator.onActivityResumed())
    }

    @Test
    fun callbackStagesAreRecordedSeparatelyFromAuthenticationResult() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()
        coordinator.onFreetCallbackRedirectStarted()
        coordinator.onFreetSnsLoginReached()
        coordinator.onFreetSessionAppearsEstablished()

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackIntentReceived)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.callbackHandedToWebView)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetCallbackRedirectStarted)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetSnsLoginReached)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetSessionAppearsEstablished)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.freetAuthenticationCompleted)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun usageVerificationInProgressDoesNotBecomeAuthenticationFailure() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.recordUsageVerification(
            pagePath = "/mypage/usage",
            loginPageDetected = false,
            parserStatus = PocStatus.IN_PROGRESS,
            usageApiStatus = PocStatus.IN_PROGRESS,
        )

        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.usagePageAuthenticated)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun handoffTimeoutIsDistinctFromAuthenticationFailure() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.markHandoffTimeout()

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT, coordinator.state.value.finalResult)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.freetAuthenticationCompleted)
    }

    @Test
    fun authenticationFailureCannotCoexistWithCompletedAuthentication() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.recordUsageVerification(
            pagePath = "/login",
            loginPageDetected = true,
            parserStatus = PocStatus.FAIL,
            usageApiStatus = PocStatus.NOT_TESTED,
        )

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED, coordinator.state.value.finalResult)
        assertEquals(PocStatus.FAIL, coordinator.state.value.freetAuthenticationCompleted)
    }

    @Test
    fun blankPageIssueIsVisibleAndRetryClearsIt() {
        val coordinator = FreetOAuthPocCoordinator()

        coordinator.onWebViewIssue(FreetWebViewIssue.ABOUT_BLANK)

        assertEquals(FreetWebViewIssue.ABOUT_BLANK, coordinator.state.value.webViewIssue)
        assertFalse(coordinator.isLoginReady())

        coordinator.resetForRetry()

        assertEquals(FreetWebViewIssue.NONE, coordinator.state.value.webViewIssue)
        assertTrue(coordinator.isLoginReady())
    }

    @Test
    fun retryDoesNotCarryAStaleWebViewFailureIntoTheNewAttempt() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onFreetLoginWebViewCreated(available = false)

        coordinator.resetForRetry(freetLoginWebViewAvailable = true)

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.freetLoginWebView)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun webViewIssueDoesNotPrematurelyBecomeAuthenticationFailure() {
        val coordinator = FreetOAuthPocCoordinator()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()

        coordinator.onWebViewIssue(FreetWebViewIssue.NAVIGATION_ERROR)

        assertEquals(FreetWebViewIssue.NAVIGATION_ERROR, coordinator.state.value.webViewIssue)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun failedAttemptRetryStartsNewLoginLoadingAttemptAndClearsDiagnostics() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onOAuthDetected(oauthUrl, firstAttempt)
        coordinator.onCustomTabLaunchSucceeded(false, firstAttempt)

        val secondAttempt = coordinator.resetForRetry()

        assertTrue(secondAttempt > firstAttempt)
        assertEquals(secondAttempt, coordinator.state.value.attemptId)
        assertEquals(FreetLoginAttemptPhase.LOGIN_LOADING, coordinator.state.value.attemptPhase)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.retryResetCompleted)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.oauthDetected)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun timeoutFromPreviousAttemptCannotChangeTheNewAttempt() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onOAuthDetected(oauthUrl, firstAttempt)
        coordinator.onCustomTabLaunchSucceeded(true, firstAttempt)
        coordinator.onCallbackIntentReceived(true, true, firstAttempt)
        coordinator.onCallbackHandedToWebView(firstAttempt)
        coordinator.onFreetCallbackProcessed(firstAttempt)
        coordinator.onFreetSessionAppearsEstablished(firstAttempt)
        coordinator.onUsageApiRequestStarted(firstAttempt)

        val secondAttempt = coordinator.resetForRetry()
        coordinator.onUsageApiTimeout(firstAttempt)

        assertEquals(secondAttempt, coordinator.state.value.attemptId)
        assertEquals(FreetLoginAttemptPhase.LOGIN_LOADING, coordinator.state.value.attemptPhase)
        assertEquals(UsageApiResponseStatus.NOT_TESTED, coordinator.state.value.usageApiResponse)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun handledCallbackFromPreviousAttemptCannotBeReplayedAfterRetry() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onOAuthDetected(oauthUrl, firstAttempt)
        coordinator.onCustomTabLaunchSucceeded(true, firstAttempt)
        coordinator.onCallbackIntentReceived(true, true, firstAttempt)

        val secondAttempt = coordinator.resetForRetry()

        assertFalse(coordinator.onCallbackIntentReceived(true, true, firstAttempt))
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.callbackIntentReceived)
        coordinator.onOAuthDetected(oauthUrl, secondAttempt)
        coordinator.onCustomTabLaunchSucceeded(true, secondAttempt)
        assertTrue(coordinator.onCallbackIntentReceived(true, true, secondAttempt))
    }

    @Test
    fun blankRecoveryRecreatesLoginAttemptOnlyOnceUntilManualRetry() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()

        assertTrue(coordinator.onLoginWebViewRecoveryRequested(firstAttempt))
        val recoveredAttempt = coordinator.resetForRetry(autoRecovery = true)
        assertEquals(FreetLoginAttemptPhase.LOGIN_LOADING, coordinator.state.value.attemptPhase)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.blankRecoveryTriggered)
        assertFalse(coordinator.onLoginWebViewRecoveryRequested(recoveredAttempt))

        val manualRetryAttempt = coordinator.resetForRetry()
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.blankRecoveryTriggered)
        assertTrue(coordinator.onLoginWebViewRecoveryRequested(manualRetryAttempt))
    }

    @Test
    fun loginWebViewDestroyedThenRecreatedStartsTheNewLoginPage() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onFreetLoginWebViewCreated(true, firstAttempt)
        coordinator.onLoginWebViewPageStarted(firstAttempt)
        coordinator.onFreetLoginWebViewCreated(false, firstAttempt)

        val secondAttempt = coordinator.resetForRetry()
        coordinator.onFreetLoginWebViewCreated(true, secondAttempt)
        coordinator.onLoginWebViewPageStarted(secondAttempt)
        coordinator.onFreetLoginPageLoaded(secondAttempt)

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.loginWebViewCreated)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.loginPageStarted)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.loginPageFinished)
        assertEquals(FreetLoginAttemptPhase.LOGIN_READY, coordinator.state.value.attemptPhase)
    }

    @Test
    fun retryResetsApiWebViewCleanupStateForTheNextAttempt() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onOAuthDetected(oauthUrl, firstAttempt)
        coordinator.onCustomTabLaunchSucceeded(true, firstAttempt)
        coordinator.onCallbackIntentReceived(true, true, firstAttempt)
        coordinator.onCallbackHandedToWebView(firstAttempt)
        coordinator.onFreetCallbackProcessed(firstAttempt)
        coordinator.onFreetSessionAppearsEstablished(firstAttempt)
        coordinator.onUsageApiWebViewCreated(firstAttempt)
        coordinator.onUsageApiWebViewCleaned(firstAttempt)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.apiWebViewCleaned)

        val secondAttempt = coordinator.resetForRetry()

        assertEquals(secondAttempt, coordinator.state.value.attemptId)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.apiWebViewCleaned)
    }

    @Test
    fun staleWebViewCallbacksCannotOverwriteTheNewAttempt() {
        val coordinator = FreetOAuthPocCoordinator()
        val firstAttempt = coordinator.currentAttemptId()
        coordinator.onFreetLoginWebViewCreated(true, firstAttempt)
        val secondAttempt = coordinator.resetForRetry()

        coordinator.onFreetLoginWebViewCreated(false, firstAttempt)
        coordinator.onWebViewIssue(FreetWebViewIssue.ABOUT_BLANK, firstAttempt)

        assertEquals(secondAttempt, coordinator.state.value.attemptId)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.loginWebViewCreated)
        assertEquals(FreetWebViewIssue.NONE, coordinator.state.value.webViewIssue)
    }

    @Test
    fun repeatedManualRetriesAlwaysReturnToLoginLoadingWithoutBlankState() {
        val coordinator = FreetOAuthPocCoordinator()

        repeat(3) {
            val attemptId = coordinator.currentAttemptId()
            coordinator.onWebViewIssue(FreetWebViewIssue.ABOUT_BLANK, attemptId)
            val nextAttemptId = coordinator.resetForRetry()

            assertTrue(nextAttemptId > attemptId)
            assertEquals(FreetLoginAttemptPhase.LOGIN_LOADING, coordinator.state.value.attemptPhase)
            assertEquals(FreetWebViewIssue.NONE, coordinator.state.value.webViewIssue)
            assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
        }
    }
}
