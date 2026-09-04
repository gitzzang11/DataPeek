package com.datapeek.ui

import com.datapeek.data.provider.freet.FreetUsageJsonStructure
import com.datapeek.data.provider.freet.FreetUsageParseDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The UI-owned request channel for the Freet usage API WebView. */
internal class FreetUsageRefreshCoordinator {
    private val _pendingRequest = MutableStateFlow<FreetUsageRefreshRequest?>(null)
    val pendingRequest: StateFlow<FreetUsageRefreshRequest?> = _pendingRequest.asStateFlow()
    private val _diagnostics = MutableStateFlow(FreetUsageRefreshDiagnostics())
    val diagnostics: StateFlow<FreetUsageRefreshDiagnostics> = _diagnostics.asStateFlow()

    private var nextRequestId = 0L

    @Synchronized
    fun request(
        origin: FreetUsageRefreshOrigin,
        pocAttemptId: Long? = null,
    ): FreetUsageRefreshRequest? {
        if (_pendingRequest.value != null) return null
        val request = FreetUsageRefreshRequest(
            id = ++nextRequestId,
            origin = origin,
            pocAttemptId = pocAttemptId,
        )
        _pendingRequest.value = request
        _diagnostics.value = FreetUsageRefreshDiagnostics(
            refreshId = request.id,
            origin = origin,
            refreshRequested = PocStatus.SUCCESS,
            refreshLockAcquired = PocStatus.SUCCESS,
            activityAvailable = PocStatus.IN_PROGRESS,
            finalRefreshResult = PocStatus.IN_PROGRESS,
            finalOutcome = FreetUsageRefreshFinalOutcome.IN_PROGRESS,
        )
        return request
    }

    @Synchronized
    fun complete(requestId: Long) {
        if (_pendingRequest.value?.id == requestId) {
            _pendingRequest.value = null
        }
    }

    /** Cancels only the currently owned request; late WebView callbacks cannot complete it. */
    @Synchronized
    fun cancel(requestId: Long) {
        if (_pendingRequest.value?.id != requestId) return
        _diagnostics.update { current ->
            if (current.refreshId == requestId) {
                current.copy(
                    repositoryUpdated = PocStatus.FAIL,
                    finalRefreshResult = PocStatus.FAIL,
                    finalOutcome = FreetUsageRefreshFinalOutcome.FAIL,
                    failure = FreetUsageRefreshFailureReason.REFRESH_SUPERSEDED,
                )
            } else {
                current
            }
        }
        _pendingRequest.value = null
    }

    @Synchronized
    fun fallbackToHtml(requestId: Long): Boolean {
        val current = _pendingRequest.value ?: return false
        if (current.id != requestId || current.target != FreetUsageRefreshTarget.JSON_API) return false
        _pendingRequest.value = current.copy(target = FreetUsageRefreshTarget.HTML)
        update(requestId) { it.copy(htmlFallback = PocStatus.IN_PROGRESS) }
        return true
    }

    fun onActivityAvailable(requestId: Long, available: Boolean) {
        update(requestId) {
            it.copy(
                activityAvailable = available.toPocStatus(),
                failure = if (available) it.failure else FreetUsageRefreshFailureReason.REFRESH_HOST_UNAVAILABLE,
            )
        }
    }

    fun onApiWebViewCreated(requestId: Long, created: Boolean) {
        update(requestId) {
            it.copy(
                apiWebViewCreated = created.toPocStatus(),
                apiWebViewCleanup = if (created) PocStatus.IN_PROGRESS else PocStatus.NOT_TESTED,
                failure = if (created) it.failure else FreetUsageRefreshFailureReason.API_WEBVIEW_CREATE_FAILED,
            )
        }
    }

    fun onApiWebViewAttached(requestId: Long, attached: Boolean) {
        update(requestId) {
            it.copy(
                apiWebViewAttached = attached.toPocStatus(),
                failure = if (attached) it.failure else FreetUsageRefreshFailureReason.API_WEBVIEW_ATTACH_FAILED,
            )
        }
    }

    fun onApiNavigationRequested(requestId: Long) {
        update(requestId) { it.copy(apiNavigationRequested = PocStatus.SUCCESS) }
    }

    fun onApiPageStarted(requestId: Long) {
        update(requestId) { it.copy(apiPageStarted = PocStatus.SUCCESS) }
    }

    fun onApiPageFinished(requestId: Long) {
        update(requestId) { it.copy(apiPageFinished = PocStatus.SUCCESS) }
    }

    fun onApiHttpStatus(requestId: Long, status: Int) {
        update(requestId) {
            it.copy(
                apiHttpStatus = PocStatus.SUCCESS,
                httpStatus = status,
                failure = when (status) {
                    401 -> FreetUsageRefreshFailureReason.API_HTTP_401
                    403 -> FreetUsageRefreshFailureReason.API_HTTP_403
                    in 200..299 -> it.failure
                    else -> FreetUsageRefreshFailureReason.API_HTTP_ERROR
                },
            )
        }
    }

    fun onApiBodyReadStarted(requestId: Long) {
        update(requestId) { it.copy(apiBodyReadStarted = PocStatus.SUCCESS) }
    }

    fun onApiBodyReceived(requestId: Long, received: Boolean) {
        update(requestId) {
            it.copy(
                apiBodyReceived = received.toPocStatus(),
                failure = if (received) it.failure else FreetUsageRefreshFailureReason.API_BODY_TIMEOUT,
            )
        }
    }

    fun onJsonParser(requestId: Long, status: PocStatus) {
        onJsonParser(requestId, status, null, null)
    }

    fun onJsonParser(
        requestId: Long,
        status: PocStatus,
        parseDiagnostics: FreetUsageParseDiagnostics?,
        jsonStructure: FreetUsageJsonStructure?,
    ) {
        update(requestId) {
            it.copy(
                jsonParser = status,
                rootTypeSupported = jsonStructure?.let { structure ->
                    (structure.rootType == com.datapeek.data.provider.freet.UsageJsonNodeType.OBJECT).toPocStatus()
                } ?: it.rootTypeSupported,
                parseDiagnostics = parseDiagnostics ?: it.parseDiagnostics,
                jsonStructure = jsonStructure ?: it.jsonStructure,
                failure = if (status == PocStatus.FAIL) {
                    FreetUsageRefreshFailureReason.JSON_PARSE_FAILED
                } else {
                    it.failure
                },
            )
        }
    }

    fun onBodyDecoded(requestId: Long, diagnostics: EvaluateJavascriptBodyDiagnostics) {
        update(requestId) {
            if (_pendingRequest.value?.target == FreetUsageRefreshTarget.JSON_API || it.bodyDiagnostics == null) {
                it.copy(bodyDiagnostics = diagnostics)
            } else {
                it
            }
        }
    }

    fun onJsonStructure(requestId: Long, structure: FreetUsageJsonStructure) {
        update(requestId) {
            it.copy(
                jsonStructure = structure,
                rootTypeSupported = (structure.rootType == com.datapeek.data.provider.freet.UsageJsonNodeType.OBJECT)
                    .toPocStatus(),
            )
        }
    }

    fun onRepositoryUpdated(requestId: Long, updated: Boolean) {
        update(requestId) { it.copy(repositoryUpdated = updated.toPocStatus()) }
    }

    @Synchronized
    fun onApiWebViewCleaned(requestId: Long) {
        if (_diagnostics.value.refreshId != requestId) return
        _diagnostics.update { current ->
            if (current.refreshId == requestId) {
                current.copy(apiWebViewCleanup = PocStatus.SUCCESS)
            } else {
                current
            }
        }
    }

    fun onFinalRefreshResult(
        requestId: Long,
        succeeded: Boolean,
        failure: FreetUsageRefreshFailureReason = FreetUsageRefreshFailureReason.NONE,
    ) {
        update(requestId) {
            val fallback = _pendingRequest.value?.target == FreetUsageRefreshTarget.HTML
            it.copy(
                finalRefreshResult = succeeded.toPocStatus(),
                htmlFallback = if (fallback) succeeded.toPocStatus() else it.htmlFallback,
                finalOutcome = when {
                    !succeeded -> FreetUsageRefreshFinalOutcome.FAIL
                    fallback -> FreetUsageRefreshFinalOutcome.SUCCESS_FALLBACK
                    else -> FreetUsageRefreshFinalOutcome.SUCCESS
                },
                failure = if (succeeded) FreetUsageRefreshFailureReason.NONE else failure,
            )
        }
    }

    @Synchronized
    private fun update(
        requestId: Long,
        transform: (FreetUsageRefreshDiagnostics) -> FreetUsageRefreshDiagnostics,
    ) {
        if (_pendingRequest.value?.id != requestId) return
        _diagnostics.update { current ->
            if (current.refreshId == requestId) transform(current) else current
        }
    }
}

internal data class FreetUsageRefreshRequest(
    val id: Long,
    val origin: FreetUsageRefreshOrigin,
    val pocAttemptId: Long? = null,
    val target: FreetUsageRefreshTarget = FreetUsageRefreshTarget.JSON_API,
)

internal enum class FreetUsageRefreshOrigin {
    LOGIN,
    MANUAL,
    FOREGROUND,
    WIDGET,
    BACKGROUND,
    SESSION_RECHECK,
}

internal enum class FreetUsageRefreshTarget {
    JSON_API,
    HTML,
}

internal data class FreetUsageRefreshDiagnostics(
    val refreshId: Long = 0L,
    val origin: FreetUsageRefreshOrigin? = null,
    val refreshRequested: PocStatus = PocStatus.NOT_TESTED,
    val refreshLockAcquired: PocStatus = PocStatus.NOT_TESTED,
    val activityAvailable: PocStatus = PocStatus.NOT_TESTED,
    val apiWebViewCreated: PocStatus = PocStatus.NOT_TESTED,
    val apiWebViewAttached: PocStatus = PocStatus.NOT_TESTED,
    val apiNavigationRequested: PocStatus = PocStatus.NOT_TESTED,
    val apiPageStarted: PocStatus = PocStatus.NOT_TESTED,
    val apiPageFinished: PocStatus = PocStatus.NOT_TESTED,
    val apiHttpStatus: PocStatus = PocStatus.NOT_TESTED,
    val httpStatus: Int? = null,
    val apiBodyReadStarted: PocStatus = PocStatus.NOT_TESTED,
    val apiBodyReceived: PocStatus = PocStatus.NOT_TESTED,
    val jsonParser: PocStatus = PocStatus.NOT_TESTED,
    val rootTypeSupported: PocStatus = PocStatus.NOT_TESTED,
    val parseDiagnostics: FreetUsageParseDiagnostics? = null,
    val jsonStructure: FreetUsageJsonStructure? = null,
    val bodyDiagnostics: EvaluateJavascriptBodyDiagnostics? = null,
    val htmlFallback: PocStatus = PocStatus.NOT_TESTED,
    val repositoryUpdated: PocStatus = PocStatus.NOT_TESTED,
    val apiWebViewCleanup: PocStatus = PocStatus.NOT_TESTED,
    val finalRefreshResult: PocStatus = PocStatus.NOT_TESTED,
    val finalOutcome: FreetUsageRefreshFinalOutcome = FreetUsageRefreshFinalOutcome.NOT_TESTED,
    val failure: FreetUsageRefreshFailureReason = FreetUsageRefreshFailureReason.NONE,
)

internal enum class FreetUsageRefreshFinalOutcome {
    NOT_TESTED,
    IN_PROGRESS,
    SUCCESS,
    SUCCESS_FALLBACK,
    FAIL,
}

internal enum class FreetUsageRefreshFailureReason {
    NONE,
    REFRESH_HOST_UNAVAILABLE,
    API_WEBVIEW_CREATE_FAILED,
    API_WEBVIEW_ATTACH_FAILED,
    API_NAVIGATION_TIMEOUT,
    API_HTTP_401,
    API_HTTP_403,
    API_HTTP_ERROR,
    API_BODY_TIMEOUT,
    API_NAVIGATION_FAILED,
    API_REQUEST_FAILED,
    JSON_PARSE_FAILED,
    SESSION_EXPIRED,
    REFRESH_SUPERSEDED,
}

private fun Boolean.toPocStatus(): PocStatus = if (this) PocStatus.SUCCESS else PocStatus.FAIL
