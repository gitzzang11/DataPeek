package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetUsageApiCoordinatorTest {
    private val oauthUrl =
        "https://accounts.google.com/o/oauth2/v2/auth?code=redacted&state=redacted"

    @Test
    fun sessionEstablishedWaitsForDedicatedWebViewBeforeStartingUsageApi() {
        val coordinator = callbackHandoffCoordinator()

        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.usageApiRequestStarted)
        assertTrue(coordinator.onFreetSessionAppearsEstablished())
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.usageApiRequestStarted)

        assertTrue(coordinator.onUsageApiRequestStarted())
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.usageApiRequestStarted)
        assertFalse(coordinator.onUsageApiRequestStarted())
    }

    @Test
    fun dedicatedApiWebViewLifecycleIsRecordedBeforeBodyParsing() {
        val coordinator = handoffCoordinator()

        coordinator.onUsageApiWebViewCreated()
        assertTrue(coordinator.onUsageApiRequestStarted())
        coordinator.onUsageApiNavigationStarted()
        coordinator.onUsageApiPageFinished()
        coordinator.onUsageApiBodyReceived()

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiWebViewCreated)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiNavigationStarted)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiPageFinished)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiBodyReceived)
    }

    @Test
    fun sessionEstablishedCannotFinishWithAnUntestedUsageApi() {
        val coordinator = callbackHandoffCoordinator()

        coordinator.onFreetSessionAppearsEstablished()
        coordinator.markHandoffTimeout()

        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.usageApiRequestStarted)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT, coordinator.state.value.finalResult)
    }

    @Test
    fun sessionEstablishedStartsTheUsageApiRequestOnce() {
        val coordinator = handoffCoordinator()

        assertTrue(coordinator.onUsageApiRequestStarted())
        assertFalse(coordinator.onUsageApiRequestStarted())

        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.usageApiRequestStarted)
        assertEquals(UsageApiResponseStatus.IN_PROGRESS, coordinator.state.value.usageApiResponse)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.usageApiAuthenticated)
    }

    @Test
    fun http200JsonSuccessWorksEvenWhenHtmlParserIsStillInProgress() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        val shouldFallback = coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.HTTP_200,
            jsonParserStatus = PocStatus.SUCCESS,
            httpStatus = 200,
            snapshotCreated = PocStatus.SUCCESS,
        )

        assertFalse(shouldFallback)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiRequestStarted)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiResponseReceived)
        assertEquals(UsageApiResponseStatus.HTTP_200, coordinator.state.value.usageApiResponse)
        assertEquals(200, coordinator.state.value.usageApiHttpStatus)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageApiAuthenticated)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageJsonParser)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.usageSnapshotCreated)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.state.value.parserStatus)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_WORKS, coordinator.state.value.finalResult)
    }

    @Test
    fun http200JsonSyntaxAloneCannotCompleteWithoutSnapshot() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        val shouldFallback = coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.HTTP_200,
            jsonParserStatus = PocStatus.SUCCESS,
            httpStatus = 200,
        )

        assertTrue(shouldFallback)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.usageApiAuthenticated)
        assertEquals(PocStatus.NOT_TESTED, coordinator.state.value.usageSnapshotCreated)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun recordsSnapshotDeliveryStagesSeparately() {
        val coordinator = FreetOAuthPocCoordinator()

        coordinator.onSnapshotDeliveryUpdated(
            SnapshotDeliveryStatus(
                repositoryCacheUpdated = true,
                homeUiUpdated = true,
                widgetUpdateRequested = false,
            ),
        )

        assertEquals(PocStatus.SUCCESS, coordinator.state.value.repositoryCacheUpdated)
        assertEquals(PocStatus.SUCCESS, coordinator.state.value.homeUiUpdated)
        assertEquals(PocStatus.FAIL, coordinator.state.value.widgetUpdateRequested)
    }

    @Test
    fun invalidApiBodyAllowsHtmlParserFallbackWithoutPrematureFailure() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        val shouldFallback = coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.JSON_PARSE_FAILED,
            jsonParserStatus = PocStatus.FAIL,
        )

        assertTrue(shouldFallback)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)

        coordinator.onHtmlUsageFallbackStarted()
        coordinator.recordUsageVerification(
            pagePath = "/mypage/usage",
            loginPageDetected = false,
            parserStatus = PocStatus.SUCCESS,
            usageApiStatus = PocStatus.NOT_TESTED,
            snapshotCreated = PocStatus.SUCCESS,
        )

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_WORKS, coordinator.state.value.finalResult)
    }

    @Test
    fun bodyReadFailureIsDiagnosedAndAllowsHtmlFallback() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiWebViewCreated()
        coordinator.onUsageApiRequestStarted()
        coordinator.onUsageApiNavigationStarted()
        coordinator.onUsageApiPageFinished()
        coordinator.onUsageApiBodyReadFailed()

        val shouldFallback = coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.API_BODY_READ_FAILED,
            jsonParserStatus = PocStatus.FAIL,
        )

        assertTrue(shouldFallback)
        assertEquals(PocStatus.FAIL, coordinator.state.value.usageApiBodyReceived)
        assertEquals(UsageApiResponseStatus.API_BODY_READ_FAILED, coordinator.state.value.usageApiResponse)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun apiRequestFailureAllowsHtmlParserFallbackWithoutAuthFailure() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        val shouldFallback = coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.API_REQUEST_FAILED,
            jsonParserStatus = PocStatus.NOT_TESTED,
        )

        assertTrue(shouldFallback)
        assertEquals(CallbackHandoffResult.NOT_TESTED, coordinator.state.value.finalResult)
    }

    @Test
    fun unauthorizedUsageApiResponseIsAuthenticationFailure() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.HTTP_401,
            jsonParserStatus = PocStatus.FAIL,
        )

        assertEquals(PocStatus.FAIL, coordinator.state.value.usageApiAuthenticated)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED, coordinator.state.value.finalResult)
        assertEquals(PocStatus.FAIL, coordinator.state.value.freetAuthenticationCompleted)
    }

    @Test
    fun forbiddenUsageApiResponseIsAuthenticationFailure() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.HTTP_403,
            jsonParserStatus = PocStatus.FAIL,
        )

        assertEquals(PocStatus.FAIL, coordinator.state.value.usageApiAuthenticated)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED, coordinator.state.value.finalResult)
    }

    @Test
    fun usageApiTimeoutIsDistinctFromAuthenticationFailure() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        coordinator.onUsageApiTimeout()

        assertEquals(UsageApiResponseStatus.API_TIMEOUT, coordinator.state.value.usageApiResponse)
        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT, coordinator.state.value.finalResult)
    }

    @Test
    fun usageApiSuccessRemainsWorksAfterParentTimeoutCallbackRuns() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()

        coordinator.onUsageApiResponse(
            response = UsageApiResponseStatus.HTTP_200,
            jsonParserStatus = PocStatus.SUCCESS,
            httpStatus = 200,
            snapshotCreated = PocStatus.SUCCESS,
        )
        coordinator.markHandoffTimeout()

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_WORKS, coordinator.state.value.finalResult)
    }

    @Test
    fun resetAfterUsageApiTimeoutAllowsASecondApiAttempt() {
        val coordinator = handoffCoordinator()
        coordinator.onUsageApiRequestStarted()
        coordinator.onUsageApiTimeout()

        coordinator.resetForRetry()
        coordinator.onOAuthDetected(oauthUrl)
        coordinator.onCustomTabLaunchSucceeded(true)
        coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
        coordinator.onCallbackHandedToWebView()
        coordinator.onFreetCallbackProcessed()
        coordinator.onFreetSessionAppearsEstablished()

        assertTrue(coordinator.onUsageApiRequestStarted())
        coordinator.onUsageApiResponse(
            UsageApiResponseStatus.HTTP_200,
            PocStatus.SUCCESS,
            snapshotCreated = PocStatus.SUCCESS,
        )

        assertEquals(CallbackHandoffResult.CALLBACK_HANDOFF_WORKS, coordinator.state.value.finalResult)
    }

    private fun callbackHandoffCoordinator(): FreetOAuthPocCoordinator {
        return FreetOAuthPocCoordinator().also { coordinator ->
            coordinator.onOAuthDetected(oauthUrl)
            coordinator.onCustomTabLaunchSucceeded(true)
            coordinator.onCallbackIntentReceived(valid = true, existingWebViewAvailable = true)
            coordinator.onCallbackHandedToWebView()
        }
    }

    private fun handoffCoordinator(): FreetOAuthPocCoordinator {
        return callbackHandoffCoordinator().also { it.onFreetSessionAppearsEstablished() }
    }
}
