package com.datapeek.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreetUsageRefreshCoordinatorTest {
    @Test
    fun requestsAreUniqueAndOnlyTheCurrentRequestCanBeCompleted() {
        val coordinator = FreetUsageRefreshCoordinator()

        val first = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("first request was not created")
        val second = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)

        assertEquals(null, second)
        assertEquals(first.id, coordinator.pendingRequest.value?.id)
        assertEquals(FreetUsageRefreshOrigin.MANUAL, coordinator.pendingRequest.value?.origin)
        assertEquals(FreetUsageRefreshTarget.JSON_API, coordinator.pendingRequest.value?.target)

        assertTrue(coordinator.fallbackToHtml(first.id))
        assertEquals(FreetUsageRefreshTarget.HTML, coordinator.pendingRequest.value?.target)

        coordinator.complete(first.id)
        assertEquals(null, coordinator.pendingRequest.value)

        val replacement = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)
            ?: error("replacement request was not created")
        coordinator.complete(replacement.id)
        assertNull(coordinator.pendingRequest.value)
    }

    @Test
    fun loginRequestCarriesThePocAttemptWithoutExposingCallbackValues() {
        val coordinator = FreetUsageRefreshCoordinator()

        val request = coordinator.request(
            origin = FreetUsageRefreshOrigin.LOGIN,
            pocAttemptId = 42L,
        ) ?: error("login request was not created")

        assertEquals(request.id, coordinator.pendingRequest.value?.id)
        assertEquals(FreetUsageRefreshOrigin.LOGIN, coordinator.pendingRequest.value?.origin)
        assertEquals(42L, coordinator.pendingRequest.value?.pocAttemptId)
    }

    @Test
    fun recordsTheCompleteManualRefreshLifecycleBeforeReleasingTheRequest() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("request was not created")

        coordinator.onActivityAvailable(request.id, available = true)
        coordinator.onApiWebViewCreated(request.id, created = true)
        coordinator.onApiWebViewAttached(request.id, attached = true)
        coordinator.onApiNavigationRequested(request.id)
        coordinator.onApiPageStarted(request.id)
        coordinator.onApiPageFinished(request.id)
        coordinator.onApiBodyReadStarted(request.id)
        coordinator.onApiBodyReceived(request.id, received = true)
        coordinator.onJsonParser(request.id, PocStatus.SUCCESS)
        coordinator.onRepositoryUpdated(request.id, updated = true)
        coordinator.onFinalRefreshResult(request.id, succeeded = true)
        coordinator.onApiHttpStatus(request.id, 200)

        val diagnostics = coordinator.diagnostics.value
        assertEquals(PocStatus.SUCCESS, diagnostics.refreshRequested)
        assertEquals(PocStatus.SUCCESS, diagnostics.refreshLockAcquired)
        assertEquals(PocStatus.SUCCESS, diagnostics.activityAvailable)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiWebViewCreated)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiWebViewAttached)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiNavigationRequested)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiPageStarted)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiPageFinished)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiBodyReadStarted)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiBodyReceived)
        assertEquals(PocStatus.SUCCESS, diagnostics.apiHttpStatus)
        assertEquals(200, diagnostics.httpStatus)
        assertEquals(PocStatus.SUCCESS, diagnostics.jsonParser)
        assertEquals(PocStatus.SUCCESS, diagnostics.repositoryUpdated)
        assertEquals(PocStatus.SUCCESS, diagnostics.finalRefreshResult)

        coordinator.complete(request.id)
        coordinator.onApiWebViewCleaned(request.id)
        assertEquals(PocStatus.SUCCESS, coordinator.diagnostics.value.apiWebViewCleanup)
        coordinator.onApiPageFinished(request.id)
        assertEquals(null, coordinator.pendingRequest.value)
    }

    @Test
    fun activityFailureIsRecordedImmediatelyAndDoesNotBecomeAnApiTimeout() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)
            ?: error("request was not created")

        coordinator.onActivityAvailable(request.id, available = false)
        coordinator.onFinalRefreshResult(
            request.id,
            succeeded = false,
            failure = FreetUsageRefreshFailureReason.REFRESH_HOST_UNAVAILABLE,
        )

        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.activityAvailable)
        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.finalRefreshResult)
        assertEquals(
            FreetUsageRefreshFailureReason.REFRESH_HOST_UNAVAILABLE,
            coordinator.diagnostics.value.failure,
        )
    }

    @Test
    fun attachFailureIsImmediateAndDoesNotBecomeAnApiTimeout() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("request was not created")

        coordinator.onApiWebViewCreated(request.id, created = true)
        coordinator.onApiWebViewAttached(request.id, attached = false)
        coordinator.onFinalRefreshResult(
            request.id,
            succeeded = false,
            failure = FreetUsageRefreshFailureReason.API_WEBVIEW_ATTACH_FAILED,
        )

        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.apiWebViewAttached)
        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.finalRefreshResult)
        assertEquals(
            FreetUsageRefreshFailureReason.API_WEBVIEW_ATTACH_FAILED,
            coordinator.diagnostics.value.failure,
        )
    }

    @Test
    fun parserFailureCannotBecomeARegularRefreshSuccess() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("request was not created")

        coordinator.onApiBodyReceived(request.id, received = true)
        coordinator.onJsonParser(request.id, PocStatus.FAIL)
        coordinator.onFinalRefreshResult(
            request.id,
            succeeded = false,
            failure = FreetUsageRefreshFailureReason.JSON_PARSE_FAILED,
        )

        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.jsonParser)
        assertEquals(PocStatus.NOT_TESTED, coordinator.diagnostics.value.repositoryUpdated)
        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.finalRefreshResult)
        assertEquals(FreetUsageRefreshFinalOutcome.FAIL, coordinator.diagnostics.value.finalOutcome)
        assertEquals(FreetUsageRefreshFailureReason.JSON_PARSE_FAILED, coordinator.diagnostics.value.failure)
    }

    @Test
    fun jsonParserFailureIsReportedAsFallbackSuccessOnlyWhenHtmlSucceeds() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)
            ?: error("request was not created")

        coordinator.onJsonParser(request.id, PocStatus.FAIL)
        assertTrue(coordinator.fallbackToHtml(request.id))
        coordinator.onFinalRefreshResult(request.id, succeeded = true)

        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.jsonParser)
        assertEquals(PocStatus.SUCCESS, coordinator.diagnostics.value.htmlFallback)
        assertEquals(FreetUsageRefreshFinalOutcome.SUCCESS_FALLBACK, coordinator.diagnostics.value.finalOutcome)
    }

    @Test
    fun failedHtmlFallbackLeavesTheRefreshFailed() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)
            ?: error("request was not created")

        coordinator.onJsonParser(request.id, PocStatus.FAIL)
        assertTrue(coordinator.fallbackToHtml(request.id))
        coordinator.onFinalRefreshResult(
            request.id,
            succeeded = false,
            failure = FreetUsageRefreshFailureReason.JSON_PARSE_FAILED,
        )

        assertEquals(PocStatus.FAIL, coordinator.diagnostics.value.htmlFallback)
        assertEquals(FreetUsageRefreshFinalOutcome.FAIL, coordinator.diagnostics.value.finalOutcome)
    }

    @Test
    fun threeSequentialRefreshesEachGetTheirOwnGeneration() {
        val coordinator = FreetUsageRefreshCoordinator()

        repeat(3) { index ->
            val request = coordinator.request(
                if (index == 0) FreetUsageRefreshOrigin.LOGIN else FreetUsageRefreshOrigin.MANUAL,
            ) ?: error("request $index was not created")
            coordinator.onActivityAvailable(request.id, available = true)
            coordinator.onApiWebViewCreated(request.id, created = true)
            coordinator.onApiWebViewAttached(request.id, attached = true)
            coordinator.onApiNavigationRequested(request.id)
            coordinator.onApiPageStarted(request.id)
            coordinator.onApiPageFinished(request.id)
            coordinator.onApiBodyReadStarted(request.id)
            coordinator.onApiBodyReceived(request.id, received = true)
            coordinator.onJsonParser(request.id, PocStatus.SUCCESS)
            coordinator.onRepositoryUpdated(request.id, updated = true)
            coordinator.onFinalRefreshResult(request.id, succeeded = true)
            coordinator.complete(request.id)
        }

        assertEquals(3L, coordinator.diagnostics.value.refreshId)
        assertEquals(PocStatus.SUCCESS, coordinator.diagnostics.value.finalRefreshResult)
    }

    @Test
    fun staleCallbacksFromAnOlderRefreshCannotChangeTheNewRefreshDiagnostics() {
        val coordinator = FreetUsageRefreshCoordinator()
        val first = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("first request was not created")
        coordinator.complete(first.id)
        val second = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
            ?: error("second request was not created")

        coordinator.onApiPageFinished(first.id)
        coordinator.onApiBodyReceived(first.id, received = false)
        coordinator.onFinalRefreshResult(
            first.id,
            succeeded = false,
            failure = FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT,
        )
        coordinator.onApiWebViewCleaned(first.id)

        assertEquals(second.id, coordinator.diagnostics.value.refreshId)
        assertEquals(PocStatus.NOT_TESTED, coordinator.diagnostics.value.apiPageFinished)
        assertEquals(PocStatus.NOT_TESTED, coordinator.diagnostics.value.apiBodyReceived)
        assertEquals(PocStatus.IN_PROGRESS, coordinator.diagnostics.value.finalRefreshResult)
        assertEquals(PocStatus.NOT_TESTED, coordinator.diagnostics.value.apiWebViewCleanup)
    }

    @Test
    fun logoutCancellationReleasesTheRequestAndMarksItSuperseded() {
        val coordinator = FreetUsageRefreshCoordinator()
        val request = coordinator.request(FreetUsageRefreshOrigin.FOREGROUND)
            ?: error("request was not created")

        coordinator.cancel(request.id)

        assertEquals(null, coordinator.pendingRequest.value)
        assertEquals(FreetUsageRefreshFinalOutcome.FAIL, coordinator.diagnostics.value.finalOutcome)
        assertEquals(FreetUsageRefreshFailureReason.REFRESH_SUPERSEDED, coordinator.diagnostics.value.failure)

        val replacement = coordinator.request(FreetUsageRefreshOrigin.MANUAL)
        assertTrue(replacement != null)
    }
}
