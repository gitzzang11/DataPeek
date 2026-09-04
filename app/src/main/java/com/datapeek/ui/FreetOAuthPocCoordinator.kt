package com.datapeek.ui

import com.datapeek.data.provider.freet.FreetUsageJsonStructure
import com.datapeek.data.provider.freet.FreetUsageDataEntryDiagnostics
import com.datapeek.data.provider.freet.FreetUsageParseDiagnostics
import com.datapeek.data.provider.freet.UsageParseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PocStatus {
    NOT_TESTED,
    IN_PROGRESS,
    SUCCESS,
    FAIL,
}

enum class FreetWebViewIssue {
    NONE,
    ABOUT_BLANK,
    EMPTY_CONTENT,
    NAVIGATION_ERROR,
    HTTP_ERROR,
}

enum class UsageApiResponseStatus {
    NOT_TESTED,
    IN_PROGRESS,
    HTTP_200,
    HTTP_401,
    HTTP_403,
    AUTH_ERROR,
    NON_JSON,
    API_REQUEST_FAILED,
    API_WEBVIEW_FETCH_FAILED,
    API_BODY_READ_FAILED,
    JSON_PARSE_FAILED,
    API_TIMEOUT,
    OTHER_ERROR,
}

enum class CallbackHandoffResult {
    NOT_TESTED,
    CALLBACK_HANDOFF_WORKS,
    CALLBACK_NOT_DISPATCHED_TO_APP,
    CALLBACK_HANDOFF_AUTH_FAILED,
    CALLBACK_HANDOFF_TIMEOUT,
    CALLBACK_HANDOFF_NOT_SUPPORTED,
}

enum class FreetLoginAttemptPhase {
    IDLE,
    LOGIN_LOADING,
    LOGIN_READY,
    OAUTH_LAUNCHED,
    WAITING_CALLBACK,
    CALLBACK_RECEIVED,
    CALLBACK_HANDOFF,
    SESSION_ESTABLISHING,
    USAGE_PROBING,
    SUCCESS,
    FAILED,
}

data class OAuthPocState(
    val attemptId: Long = 0L,
    val attemptPhase: FreetLoginAttemptPhase = FreetLoginAttemptPhase.IDLE,
    val loginWebViewCreated: PocStatus = PocStatus.NOT_TESTED,
    val loginPageStarted: PocStatus = PocStatus.NOT_TESTED,
    val loginPageFinished: PocStatus = PocStatus.NOT_TESTED,
    val blankRecoveryTriggered: PocStatus = PocStatus.NOT_TESTED,
    val retryResetCompleted: PocStatus = PocStatus.NOT_TESTED,
    val apiWebViewCleaned: PocStatus = PocStatus.NOT_TESTED,
    val freetLoginWebView: PocStatus = PocStatus.NOT_TESTED,
    val oauthDetected: PocStatus = PocStatus.NOT_TESTED,
    val customTabOpened: PocStatus = PocStatus.NOT_TESTED,
    val returnedFromBrowser: PocStatus = PocStatus.NOT_TESTED,
    val callbackIntentReceived: PocStatus = PocStatus.NOT_TESTED,
    val callbackUriValidated: PocStatus = PocStatus.NOT_TESTED,
    val existingWebViewAlive: PocStatus = PocStatus.NOT_TESTED,
    val callbackHandedToWebView: PocStatus = PocStatus.NOT_TESTED,
    val freetCallbackRedirectStarted: PocStatus = PocStatus.NOT_TESTED,
    val freetCallbackProcessed: PocStatus = PocStatus.NOT_TESTED,
    val freetSnsLoginReached: PocStatus = PocStatus.NOT_TESTED,
    val freetSessionAppearsEstablished: PocStatus = PocStatus.NOT_TESTED,
    val freetAuthenticationCompleted: PocStatus = PocStatus.NOT_TESTED,
    val freetAuthFailed: PocStatus = PocStatus.NOT_TESTED,
    val usagePageLoaded: PocStatus = PocStatus.NOT_TESTED,
    val usagePageAuthenticated: PocStatus = PocStatus.NOT_TESTED,
    val usageApiRequestStarted: PocStatus = PocStatus.NOT_TESTED,
    val usageApiWebViewCreated: PocStatus = PocStatus.NOT_TESTED,
    val usageApiNavigationStarted: PocStatus = PocStatus.NOT_TESTED,
    val usageApiPageFinished: PocStatus = PocStatus.NOT_TESTED,
    val usageApiBodyReceived: PocStatus = PocStatus.NOT_TESTED,
    val usageApiResponseReceived: PocStatus = PocStatus.NOT_TESTED,
    val usageApiResponse: UsageApiResponseStatus = UsageApiResponseStatus.NOT_TESTED,
    val usageApiHttpStatus: Int? = null,
    val usageApiAuthenticated: PocStatus = PocStatus.NOT_TESTED,
    val usageJsonParser: PocStatus = PocStatus.NOT_TESTED,
    val usageApiStatus: PocStatus = PocStatus.NOT_TESTED,
    val parserStatus: PocStatus = PocStatus.NOT_TESTED,
    val usageJsonSyntax: PocStatus = PocStatus.NOT_TESTED,
    val usageSchemaMatched: PocStatus = PocStatus.NOT_TESTED,
    val usageFieldsFound: PocStatus = PocStatus.NOT_TESTED,
    val usageValuesValid: PocStatus = PocStatus.NOT_TESTED,
    val usageSnapshotCreated: PocStatus = PocStatus.NOT_TESTED,
    val repositoryCacheUpdated: PocStatus = PocStatus.NOT_TESTED,
    val homeUiUpdated: PocStatus = PocStatus.NOT_TESTED,
    val widgetUpdateRequested: PocStatus = PocStatus.NOT_TESTED,
    val usageJsonStructure: FreetUsageJsonStructure? = null,
    val usageDataEntry: FreetUsageDataEntryDiagnostics? = null,
    val webViewIssue: FreetWebViewIssue = FreetWebViewIssue.NONE,
    val finalResult: CallbackHandoffResult = CallbackHandoffResult.NOT_TESTED,
)

/** Keeps only non-sensitive OAuth callback handoff state; callback values are never retained. */
class FreetOAuthPocCoordinator {
    private val _state = MutableStateFlow(OAuthPocState())
    val state: StateFlow<OAuthPocState> = _state.asStateFlow()

    private var oauthAttemptStarted = false
    private var customTabLaunchSucceeded = false
    private var returnedHandled = false
    private var callbackIntentHandled = false
    private var callbackHandoffStarted = false
    private var callbackProcessed = false
    private var usageApiRequestStartedFlag = false
    private var usageApiRequestDispatchStartedFlag = false
    private var usageLoadRequested = false
    private var autoRecoveryUsed = false

    fun currentAttemptId(): Long = _state.value.attemptId

    fun isCurrentAttempt(attemptId: Long): Boolean = _state.value.attemptId == attemptId

    fun isHandoffInProgress(): Boolean = when (_state.value.attemptPhase) {
        FreetLoginAttemptPhase.OAUTH_LAUNCHED,
        FreetLoginAttemptPhase.WAITING_CALLBACK,
        FreetLoginAttemptPhase.CALLBACK_RECEIVED,
        FreetLoginAttemptPhase.CALLBACK_HANDOFF,
        FreetLoginAttemptPhase.SESSION_ESTABLISHING,
        FreetLoginAttemptPhase.USAGE_PROBING,
        -> true
        else -> false
    }

    fun onFreetLoginWebViewCreated(
        available: Boolean,
        attemptId: Long = currentAttemptId(),
    ) {
        if (!isCurrentAttempt(attemptId)) return
        _state.update {
            it.copy(
                freetLoginWebView = if (available) PocStatus.SUCCESS else PocStatus.FAIL,
                loginWebViewCreated = if (available) PocStatus.SUCCESS else PocStatus.FAIL,
                attemptPhase = if (available && it.attemptPhase == FreetLoginAttemptPhase.IDLE) {
                    FreetLoginAttemptPhase.LOGIN_LOADING
                } else {
                    it.attemptPhase
                },
            )
        }
    }

    fun onLoginWebViewPageStarted(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId)) return
        _state.update {
            it.copy(
                loginPageStarted = PocStatus.SUCCESS,
                attemptPhase = when (it.attemptPhase) {
                    FreetLoginAttemptPhase.IDLE,
                    FreetLoginAttemptPhase.LOGIN_LOADING,
                    FreetLoginAttemptPhase.LOGIN_READY,
                    -> FreetLoginAttemptPhase.LOGIN_LOADING
                    else -> it.attemptPhase
                },
            )
        }
    }

    fun onLoginWebViewPageFinished(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId)) return
        _state.update { it.copy(loginPageFinished = PocStatus.SUCCESS) }
    }

    fun onLoginWebViewRecoveryRequested(attemptId: Long = currentAttemptId()): Boolean {
        if (!isCurrentAttempt(attemptId) || autoRecoveryUsed) return false
        autoRecoveryUsed = true
        _state.update { it.copy(blankRecoveryTriggered = PocStatus.SUCCESS) }
        return true
    }

    /** Returns the observed URL once; callers must pass it directly to Custom Tabs. */
    fun onOAuthDetected(observedUrl: String, attemptId: Long = currentAttemptId()): String? {
        if (!isCurrentAttempt(attemptId)) return null
        if (oauthAttemptStarted) return null
        oauthAttemptStarted = true
        _state.update {
            it.copy(
                oauthDetected = PocStatus.SUCCESS,
                customTabOpened = PocStatus.IN_PROGRESS,
                attemptPhase = FreetLoginAttemptPhase.OAUTH_LAUNCHED,
            )
        }
        return observedUrl
    }

    fun onCustomTabLaunchSucceeded(
        succeeded: Boolean,
        attemptId: Long = currentAttemptId(),
    ) {
        if (!isCurrentAttempt(attemptId)) return
        if (!oauthAttemptStarted) return
        customTabLaunchSucceeded = succeeded
        _state.update {
            it.copy(
                customTabOpened = if (succeeded) PocStatus.SUCCESS else PocStatus.FAIL,
                attemptPhase = if (succeeded) {
                    FreetLoginAttemptPhase.WAITING_CALLBACK
                } else {
                    FreetLoginAttemptPhase.FAILED
                },
                finalResult = if (succeeded) {
                    it.finalResult
                } else {
                    CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED
                },
            )
        }
    }

    /** Records the first activity resume after Custom Tabs was opened. */
    fun onActivityResumed(
        existingWebViewAvailable: Boolean = true,
        attemptId: Long = currentAttemptId(),
    ): Boolean {
        if (
            !isCurrentAttempt(attemptId) ||
            !oauthAttemptStarted ||
            !customTabLaunchSucceeded ||
            _state.value.attemptPhase != FreetLoginAttemptPhase.WAITING_CALLBACK ||
            returnedHandled ||
            callbackIntentHandled
        ) {
            return false
        }
        returnedHandled = true
        _state.update {
            it.copy(
                returnedFromBrowser = PocStatus.SUCCESS,
                existingWebViewAlive = if (existingWebViewAvailable) PocStatus.SUCCESS else PocStatus.FAIL,
                finalResult = if (existingWebViewAvailable) {
                    it.finalResult
                } else {
                    CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED
                },
            )
        }
        return true
    }

    /** Accepts only a URI already validated by scheme/host/path; no query value is inspected. */
    fun onCallbackIntentReceived(
        valid: Boolean,
        existingWebViewAvailable: Boolean,
        attemptId: Long = currentAttemptId(),
    ): Boolean {
        if (
            !isCurrentAttempt(attemptId) ||
            !oauthAttemptStarted ||
            !customTabLaunchSucceeded ||
            callbackIntentHandled
        ) return false
        callbackIntentHandled = true
        _state.update {
            it.copy(
                callbackIntentReceived = PocStatus.SUCCESS,
                callbackUriValidated = if (valid) PocStatus.SUCCESS else PocStatus.FAIL,
                existingWebViewAlive = if (existingWebViewAvailable) PocStatus.SUCCESS else PocStatus.FAIL,
                returnedFromBrowser = if (valid) PocStatus.SUCCESS else it.returnedFromBrowser,
                finalResult = if (valid && existingWebViewAvailable) {
                    it.finalResult
                } else {
                    CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED
                },
                attemptPhase = if (valid && existingWebViewAvailable) {
                    FreetLoginAttemptPhase.CALLBACK_RECEIVED
                } else {
                    FreetLoginAttemptPhase.FAILED
                },
            )
        }
        if (!valid || !existingWebViewAvailable) return false
        callbackHandoffStarted = true
        return true
    }

    fun onCallbackHandedToWebView(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted) return
        _state.update {
            it.copy(
                callbackHandedToWebView = PocStatus.SUCCESS,
                freetCallbackProcessed = PocStatus.IN_PROGRESS,
                freetAuthenticationCompleted = PocStatus.IN_PROGRESS,
                usagePageLoaded = PocStatus.IN_PROGRESS,
                usagePageAuthenticated = PocStatus.IN_PROGRESS,
                parserStatus = PocStatus.IN_PROGRESS,
                attemptPhase = FreetLoginAttemptPhase.CALLBACK_HANDOFF,
            )
        }
    }

    fun onFreetCallbackRedirectStarted(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted) return
        _state.update { it.copy(freetCallbackRedirectStarted = PocStatus.SUCCESS) }
    }

    fun onFreetCallbackProcessed(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted) return
        callbackProcessed = true
        _state.update {
            it.copy(
                freetCallbackProcessed = PocStatus.SUCCESS,
                attemptPhase = FreetLoginAttemptPhase.SESSION_ESTABLISHING,
            )
        }
    }

    fun onFreetSnsLoginReached(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted) return
        _state.update { it.copy(freetSnsLoginReached = PocStatus.SUCCESS) }
    }

    /**
     * Records a stable Freet page after the callback handoff. The dedicated
     * API WebView is created by the UI after this event.
     */
    fun onFreetSessionAppearsEstablished(attemptId: Long = currentAttemptId()): Boolean {
        if (
            !isCurrentAttempt(attemptId) ||
            !callbackHandoffStarted ||
            isTerminal() ||
            _state.value.freetSessionAppearsEstablished == PocStatus.SUCCESS
        ) {
            return false
        }
        _state.update {
            it.copy(
                freetSessionAppearsEstablished = PocStatus.SUCCESS,
                attemptPhase = FreetLoginAttemptPhase.SESSION_ESTABLISHING,
            )
        }
        return true
    }

    fun onUsageApiWebViewCreated(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update {
            it.copy(
                usageApiWebViewCreated = PocStatus.SUCCESS,
                apiWebViewCleaned = PocStatus.NOT_TESTED,
            )
        }
    }

    fun onUsageApiNavigationStarted(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update { it.copy(usageApiNavigationStarted = PocStatus.SUCCESS) }
    }

    fun onUsageApiPageFinished(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update { it.copy(usageApiPageFinished = PocStatus.SUCCESS) }
    }

    fun onUsageApiBodyReceived(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update { it.copy(usageApiBodyReceived = PocStatus.SUCCESS) }
    }

    fun onUsageApiBodyReadFailed(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update { it.copy(usageApiBodyReceived = PocStatus.FAIL) }
    }

    fun onUsageApiWebViewCleaned(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId)) return
        _state.update { it.copy(apiWebViewCleaned = PocStatus.SUCCESS) }
    }

    fun onFreetAuthenticationFailed(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update {
            it.copy(
                freetAuthFailed = PocStatus.SUCCESS,
                freetAuthenticationCompleted = PocStatus.FAIL,
                usagePageAuthenticated = PocStatus.FAIL,
                finalResult = CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED,
                attemptPhase = FreetLoginAttemptPhase.FAILED,
            )
        }
    }

    fun onWebViewIssue(issue: FreetWebViewIssue, attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId)) return
        if (issue == FreetWebViewIssue.NONE) return
        _state.update {
            it.copy(
                webViewIssue = issue,
            )
        }
    }

    fun onWebViewContentVisible(
        visible: Boolean,
        attemptId: Long = currentAttemptId(),
    ) {
        if (!isCurrentAttempt(attemptId)) return
        if (!visible) {
            onWebViewIssue(FreetWebViewIssue.EMPTY_CONTENT, attemptId)
        }
    }

    fun onFreetLoginPageLoaded(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId)) return
        _state.update {
            it.copy(
                webViewIssue = FreetWebViewIssue.NONE,
                loginPageFinished = PocStatus.SUCCESS,
                attemptPhase = if (it.attemptPhase == FreetLoginAttemptPhase.LOGIN_LOADING) {
                    FreetLoginAttemptPhase.LOGIN_READY
                } else {
                    it.attemptPhase
                },
            )
        }
    }

    fun isLoginReady(): Boolean = _state.value.webViewIssue == FreetWebViewIssue.NONE

    fun resetForRetry(
        freetLoginWebViewAvailable: Boolean = false,
        autoRecovery: Boolean = false,
    ): Long {
        val nextAttemptId = currentAttemptId() + 1L
        if (!autoRecovery) autoRecoveryUsed = false
        oauthAttemptStarted = false
        customTabLaunchSucceeded = false
        returnedHandled = false
        callbackIntentHandled = false
        callbackHandoffStarted = false
        callbackProcessed = false
        usageApiRequestStartedFlag = false
        usageApiRequestDispatchStartedFlag = false
        usageLoadRequested = false
        _state.value = OAuthPocState(
            attemptId = nextAttemptId,
            attemptPhase = FreetLoginAttemptPhase.LOGIN_LOADING,
            retryResetCompleted = PocStatus.SUCCESS,
            blankRecoveryTriggered = if (autoRecovery && autoRecoveryUsed) {
                PocStatus.SUCCESS
            } else {
                PocStatus.NOT_TESTED
            },
            freetLoginWebView = if (freetLoginWebViewAvailable) PocStatus.SUCCESS else PocStatus.NOT_TESTED,
        )
        return nextAttemptId
    }

    fun isUsageVerificationRequested(): Boolean = callbackHandoffStarted

    fun shouldLoadUsagePage(): Boolean = callbackProcessed

    fun onUsageApiRequestStarted(attemptId: Long = currentAttemptId()): Boolean {
        if (
            !isCurrentAttempt(attemptId) ||
            !callbackHandoffStarted ||
            _state.value.freetSessionAppearsEstablished != PocStatus.SUCCESS ||
            isTerminal()
        ) {
            return false
        }
        if (!usageApiRequestStartedFlag) {
            usageApiRequestStartedFlag = true
            _state.update {
                it.copy(
                    usageApiRequestStarted = PocStatus.IN_PROGRESS,
                    usageApiResponseReceived = PocStatus.IN_PROGRESS,
                    usageApiResponse = UsageApiResponseStatus.IN_PROGRESS,
                    usageApiAuthenticated = PocStatus.IN_PROGRESS,
                    usageJsonParser = PocStatus.IN_PROGRESS,
                    usageApiStatus = PocStatus.IN_PROGRESS,
                    attemptPhase = FreetLoginAttemptPhase.USAGE_PROBING,
                )
            }
        }
        if (usageApiRequestDispatchStartedFlag) return false
        usageApiRequestDispatchStartedFlag = true
        return true
    }

    /** Returns true when the static HTML page should be tried as a fallback. */
    fun onUsageApiResponse(
        response: UsageApiResponseStatus,
        jsonParserStatus: PocStatus,
        httpStatus: Int? = null,
        parseDiagnostics: FreetUsageParseDiagnostics? = null,
        jsonStructure: FreetUsageJsonStructure? = null,
        snapshotCreated: PocStatus = PocStatus.NOT_TESTED,
        attemptId: Long = currentAttemptId(),
    ): Boolean {
        if (!isCurrentAttempt(attemptId) || !usageApiRequestStartedFlag || isTerminal()) return false

        val authenticationStatus = when {
            response == UsageApiResponseStatus.HTTP_200 &&
                jsonParserStatus == PocStatus.SUCCESS &&
                snapshotCreated == PocStatus.SUCCESS -> PocStatus.SUCCESS
            response == UsageApiResponseStatus.HTTP_401 ||
                response == UsageApiResponseStatus.HTTP_403 ||
                response == UsageApiResponseStatus.AUTH_ERROR -> PocStatus.FAIL
            else -> PocStatus.NOT_TESTED
        }
        val authenticationFailure = authenticationStatus == PocStatus.FAIL
        val apiSuccess = authenticationStatus == PocStatus.SUCCESS
        val shouldFallback = !apiSuccess && !authenticationFailure
        val responseReceived = when (response) {
            UsageApiResponseStatus.API_REQUEST_FAILED,
            UsageApiResponseStatus.API_WEBVIEW_FETCH_FAILED,
            UsageApiResponseStatus.API_BODY_READ_FAILED,
            UsageApiResponseStatus.API_TIMEOUT,
            -> PocStatus.FAIL
            else -> PocStatus.SUCCESS
        }
        val resolvedHttpStatus = httpStatus ?: when (response) {
            UsageApiResponseStatus.HTTP_200 -> 200
            UsageApiResponseStatus.HTTP_401 -> 401
            UsageApiResponseStatus.HTTP_403 -> 403
            else -> null
        }

        _state.update {
            it.copy(
                usageApiRequestStarted = PocStatus.SUCCESS,
                usageApiResponseReceived = responseReceived,
                usageApiResponse = response,
                usageApiHttpStatus = resolvedHttpStatus,
                usageApiAuthenticated = authenticationStatus,
                usageApiStatus = authenticationStatus,
                usageJsonParser = jsonParserStatus,
                usageJsonSyntax = parseDiagnostics?.jsonSyntax?.toPocStatus() ?: it.usageJsonSyntax,
                usageSchemaMatched = parseDiagnostics?.schemaMatched?.toPocStatus() ?: it.usageSchemaMatched,
                usageFieldsFound = parseDiagnostics?.usageFieldsFound?.toPocStatus() ?: it.usageFieldsFound,
                usageValuesValid = parseDiagnostics?.usageValuesValid?.toPocStatus() ?: it.usageValuesValid,
                usageSnapshotCreated = parseDiagnostics?.snapshotCreated?.toPocStatus() ?: snapshotCreated,
                usageJsonStructure = jsonStructure ?: it.usageJsonStructure,
                usageDataEntry = parseDiagnostics?.dataEntry ?: it.usageDataEntry,
                usagePageAuthenticated = when {
                    apiSuccess -> PocStatus.SUCCESS
                    authenticationFailure -> PocStatus.FAIL
                    else -> it.usagePageAuthenticated
                },
                freetAuthenticationCompleted = when {
                    apiSuccess -> PocStatus.SUCCESS
                    authenticationFailure -> PocStatus.FAIL
                    else -> it.freetAuthenticationCompleted
                },
                finalResult = when {
                    apiSuccess -> CallbackHandoffResult.CALLBACK_HANDOFF_WORKS
                    authenticationFailure -> CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED
                    else -> CallbackHandoffResult.NOT_TESTED
                },
                attemptPhase = when {
                    apiSuccess -> FreetLoginAttemptPhase.SUCCESS
                    authenticationFailure -> FreetLoginAttemptPhase.FAILED
                    else -> it.attemptPhase
                },
            )
        }
        return shouldFallback
    }

    fun onUsageApiTimeout(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !usageApiRequestStartedFlag || isTerminal()) return
        _state.update {
            it.copy(
                usageApiRequestStarted = PocStatus.SUCCESS,
                usageApiResponseReceived = PocStatus.FAIL,
                usageApiResponse = UsageApiResponseStatus.API_TIMEOUT,
                finalResult = CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT,
                attemptPhase = FreetLoginAttemptPhase.FAILED,
            )
        }
    }

    fun onHtmlUsageFallbackStarted(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        _state.update {
            it.copy(
                parserStatus = PocStatus.IN_PROGRESS,
                usagePageLoaded = PocStatus.IN_PROGRESS,
                usagePageAuthenticated = PocStatus.IN_PROGRESS,
                attemptPhase = FreetLoginAttemptPhase.USAGE_PROBING,
            )
        }
    }

    fun onSnapshotDeliveryUpdated(status: SnapshotDeliveryStatus) {
        _state.update {
            it.copy(
                repositoryCacheUpdated = status.repositoryCacheUpdated.toPocStatus(),
                homeUiUpdated = status.homeUiUpdated.toPocStatus(),
                widgetUpdateRequested = status.widgetUpdateRequested.toPocStatus(),
            )
        }
    }

    fun markUsageLoadRequested(): Boolean {
        if (!callbackProcessed || usageLoadRequested) return false
        usageLoadRequested = true
        return true
    }

    fun markCallbackNotDispatchedToApp(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !returnedHandled || callbackIntentHandled || isTerminal()) return
        _state.update {
            it.copy(
                callbackIntentReceived = PocStatus.FAIL,
                finalResult = CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP,
                attemptPhase = FreetLoginAttemptPhase.FAILED,
            )
        }
    }

    fun markHandoffTimeout(attemptId: Long = currentAttemptId()) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return
        if (_state.value.usageApiRequestStarted != PocStatus.NOT_TESTED) return
        _state.update {
            it.copy(
                finalResult = CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT,
                attemptPhase = FreetLoginAttemptPhase.FAILED,
            )
        }
    }

    fun recordUsageVerification(
        pagePath: String,
        loginPageDetected: Boolean,
        parserStatus: PocStatus,
        usageApiStatus: PocStatus,
        snapshotCreated: PocStatus = PocStatus.NOT_TESTED,
        attemptId: Long = currentAttemptId(),
    ) {
        if (!isCurrentAttempt(attemptId) || !callbackHandoffStarted || isTerminal()) return

        val isLoginPage = loginPageDetected || pagePath == "/login"
        val usagePageLoaded = pagePath == "/mypage/usage" && !isLoginPage
        val authenticated = when {
            isLoginPage -> PocStatus.FAIL
            (parserStatus == PocStatus.SUCCESS && snapshotCreated == PocStatus.SUCCESS) ||
                usageApiStatus == PocStatus.SUCCESS -> PocStatus.SUCCESS
            parserStatus == PocStatus.IN_PROGRESS || usageApiStatus == PocStatus.IN_PROGRESS -> PocStatus.IN_PROGRESS
            else -> PocStatus.FAIL
        }
        val finalResult = when {
            usagePageLoaded && authenticated == PocStatus.SUCCESS -> CallbackHandoffResult.CALLBACK_HANDOFF_WORKS
            authenticated == PocStatus.IN_PROGRESS -> CallbackHandoffResult.NOT_TESTED
            else -> CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED
        }
        val authenticationCompleted = when {
            usagePageLoaded && authenticated == PocStatus.SUCCESS -> PocStatus.SUCCESS
            isLoginPage || authenticated == PocStatus.FAIL -> PocStatus.FAIL
            authenticated == PocStatus.IN_PROGRESS -> PocStatus.IN_PROGRESS
            else -> PocStatus.NOT_TESTED
        }

        _state.update {
            it.copy(
                usagePageLoaded = if (usagePageLoaded) PocStatus.SUCCESS else PocStatus.FAIL,
                usagePageAuthenticated = authenticated,
                freetAuthenticationCompleted = authenticationCompleted,
                usageApiStatus = usageApiStatus.takeIf { it != PocStatus.NOT_TESTED } ?: it.usageApiStatus,
                parserStatus = parserStatus,
                usageSnapshotCreated = snapshotCreated.takeIf { it != PocStatus.NOT_TESTED }
                    ?: it.usageSnapshotCreated,
                finalResult = finalResult,
                attemptPhase = when {
                    finalResult == CallbackHandoffResult.CALLBACK_HANDOFF_WORKS -> FreetLoginAttemptPhase.SUCCESS
                    finalResult == CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED -> FreetLoginAttemptPhase.FAILED
                    else -> it.attemptPhase
                },
            )
        }
    }

    private fun isTerminal(): Boolean = when (_state.value.finalResult) {
        CallbackHandoffResult.NOT_TESTED -> false
        CallbackHandoffResult.CALLBACK_HANDOFF_WORKS,
        CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP,
        CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED,
        CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT,
        CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED,
        -> true
    }
}

private fun UsageParseStatus?.toPocStatus(): PocStatus = when (this) {
    UsageParseStatus.SUCCESS -> PocStatus.SUCCESS
    UsageParseStatus.FAIL -> PocStatus.FAIL
    null,
    UsageParseStatus.NOT_TESTED,
    -> PocStatus.NOT_TESTED
}

private fun Boolean.toPocStatus(): PocStatus = if (this) PocStatus.SUCCESS else PocStatus.FAIL
