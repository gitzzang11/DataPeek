package com.datapeek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.datapeek.data.model.Carrier
import com.datapeek.data.model.UsageSnapshot
import com.datapeek.data.provider.freet.FreetSktProvider
import com.datapeek.data.provider.freet.FreetHtmlUsageParser
import com.datapeek.data.provider.freet.FreetUsageDataEntryDiagnostics
import com.datapeek.data.provider.freet.FreetUsageParser
import com.datapeek.data.provider.freet.UsageParseStatus
import com.datapeek.data.provider.tworld.TWorldProvider
import com.datapeek.ui.FreetLoginNavigationAction
import com.datapeek.ui.FreetLoginNavigationPolicy
import com.datapeek.ui.FreetCallbackUriValidator
import com.datapeek.ui.FreetUsageApiProbeResult
import com.datapeek.ui.decodeEvaluateJavascriptString
import com.datapeek.ui.FreetUsageRefreshCoordinator
import com.datapeek.ui.FreetUsageRefreshDiagnostics
import com.datapeek.ui.FreetUsageRefreshTarget
import com.datapeek.ui.FreetUsageRefreshFailureReason
import com.datapeek.ui.AppScreen
import com.datapeek.ui.canCancelLoginFlowOnBack
import com.datapeek.ui.DoubleBackExitController
import com.datapeek.ui.RootBackAction
import com.datapeek.ui.appScreenBackDestination
import com.datapeek.ui.isSuccessfulUsage
import com.datapeek.ui.isHomeRoot
import com.datapeek.ui.requiresLogin
import com.datapeek.ui.toUsageErrorOrNull
import com.datapeek.ui.inspectFreetUsageApiBody
import com.datapeek.ui.inspectFreetUsageHtmlBody
import com.datapeek.ui.FreetLoginLayoutPolicy
import com.datapeek.ui.developerToolAccess
import com.datapeek.ui.FreetOAuthPocCoordinator
import com.datapeek.ui.FreetWebViewIssue
import com.datapeek.ui.HomeScreen
import com.datapeek.ui.UsageHistoryScreen
import com.datapeek.ui.SettingsScreen
import com.datapeek.ui.DeveloperOptionsScreen
import com.datapeek.ui.DataPeekTheme
import com.datapeek.ui.OneUiTopAppBar
import com.datapeek.ui.OneUiBackButton
import com.datapeek.ui.OAuthPocState
import com.datapeek.ui.CallbackHandoffResult
import com.datapeek.ui.PocStatus
import com.datapeek.ui.UsageApiResponseStatus
import com.datapeek.ui.UsageViewModel
import com.datapeek.ui.SnapshotDeliveryStatus
import com.datapeek.widget.WidgetSizeDiagnosticsStore
import com.datapeek.widget.formatWidgetObservedSize
import org.json.JSONArray
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {
    private val pocCoordinator = FreetOAuthPocCoordinator()
    private var freetWebView: WebView? = null
    private var freetWebViewAttemptId: Long = -1L
    private val callbackTimeoutHandler = Handler(Looper.getMainLooper())
    private val freetUsageRefreshCoordinator = FreetUsageRefreshCoordinator()
    private var usageViewModel: UsageViewModel? = null
    private var hasStartedOnce = false
    private val openLoginRequests = MutableStateFlow(0L)
    private val widgetRefreshRequests = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialScreen = if (intent.getBooleanExtra(EXTRA_OPEN_LOGIN, false)) {
            AppScreen.LOGIN
        } else {
            AppScreen.HOME
        }
        handleIncomingIntent(intent)
        if (intent.getBooleanExtra(EXTRA_TRIGGER_REFRESH, false)) {
            widgetRefreshRequests.update { it + 1L }
        }
        val repository = (application as DataPeekApplication).repository
        setContent {
            val viewModel = remember {
                UsageViewModel(
                    repository = repository,
                    scope = lifecycleScope,
                    freetUsageRefreshCoordinator = freetUsageRefreshCoordinator,
                ).also { usageViewModel = it }
            }
            DataPeekApp(
                viewModel = viewModel,
                pocCoordinator = pocCoordinator,
                initialScreen = initialScreen,
                openLoginRequests = openLoginRequests,
                widgetRefreshRequests = widgetRefreshRequests,
                onFreetWebViewCreated = { webView, attemptId ->
                    if (pocCoordinator.isCurrentAttempt(attemptId)) {
                        if (webView == null) {
                            if (attemptId == freetWebViewAttemptId) freetWebView = null
                        } else {
                            freetWebView = webView
                            freetWebViewAttemptId = attemptId
                        }
                        pocCoordinator.onFreetLoginWebViewCreated(webView != null, attemptId)
                    }
                },
                onResetOAuth = ::resetOAuthForRetry,
                onUsageApiRequestStarted = ::startUsageApiRequest,
                onUsageApiResponse = ::handleUsageApiResponse,
                onSnapshotDeliveryStatus = { status ->
                    pocCoordinator.onSnapshotDeliveryUpdated(status)
                },
                onOpenLinkSettings = ::openLinkSettings,
                freetUsageRefreshCoordinator = freetUsageRefreshCoordinator,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_LOGIN, false)) {
            openLoginRequests.update { it + 1L }
        }
        if (intent.getBooleanExtra(EXTRA_TRIGGER_REFRESH, false)) {
            widgetRefreshRequests.update { it + 1L }
        }
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (pocCoordinator.onActivityResumed(freetWebView != null)) {
            val attemptId = pocCoordinator.currentAttemptId()
            callbackTimeoutHandler.postDelayed({
                if (pocCoordinator.isCurrentAttempt(attemptId)) {
                    pocCoordinator.markCallbackNotDispatchedToApp(attemptId)
                }
            }, CALLBACK_WAIT_MILLIS)
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasStartedOnce && !pocCoordinator.isHandoffInProgress()) {
            usageViewModel?.refreshIfAuthenticated()
        }
        hasStartedOnce = true
    }

    override fun onDestroy() {
        callbackTimeoutHandler.removeCallbacksAndMessages(null)
        freetWebView?.let { view ->
            view.stopLoading()
            view.removeAllViews()
            view.destroy()
        }
        freetWebView = null
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val callbackUri = intent.data ?: return
        val valid = FreetCallbackUriValidator.isValid(callbackUri.toString())
        if (!valid) {
            clearConsumedCallbackIntent()
            return
        }
        val attemptId = pocCoordinator.currentAttemptId()
        val handedOff = pocCoordinator.onCallbackIntentReceived(
            valid = true,
            existingWebViewAvailable = freetWebView != null,
            attemptId = attemptId,
        )
        if (!handedOff) {
            clearConsumedCallbackIntent()
            return
        }

        freetWebView?.loadUrl(callbackUri.toString())
        pocCoordinator.onCallbackHandedToWebView(attemptId)
        callbackTimeoutHandler.removeCallbacksAndMessages(null)
        callbackTimeoutHandler.postDelayed({
            if (pocCoordinator.isCurrentAttempt(attemptId)) {
                pocCoordinator.markHandoffTimeout(attemptId)
            }
        }, HANDOFF_WAIT_MILLIS)
        clearConsumedCallbackIntent()
    }

    private fun clearConsumedCallbackIntent() {
        setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
    }

    private fun openLinkSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            },
        )
    }

    private fun resetOAuthForRetry(autoRecovery: Boolean = false): Long {
        callbackTimeoutHandler.removeCallbacksAndMessages(null)
        freetWebView?.let { view ->
            view.stopLoading()
        }
        freetWebView = null
        clearConsumedCallbackIntent()
        return pocCoordinator.resetForRetry(
            freetLoginWebViewAvailable = false,
            autoRecovery = autoRecovery,
        )
    }

    private fun startUsageApiRequest(attemptId: Long): Boolean {
        val started = pocCoordinator.onUsageApiRequestStarted(attemptId)
        if (started) {
            callbackTimeoutHandler.removeCallbacksAndMessages(null)
            callbackTimeoutHandler.postDelayed({
                if (pocCoordinator.isCurrentAttempt(attemptId)) {
                    pocCoordinator.onUsageApiTimeout(attemptId)
                }
            }, USAGE_API_WAIT_MILLIS)
        }
        return started
    }

    private fun handleUsageApiResponse(
        attemptId: Long,
        probe: FreetUsageApiProbeResult,
    ): UsageApiHandling {
        callbackTimeoutHandler.removeCallbacksAndMessages(null)
        val shouldFallback = pocCoordinator.onUsageApiResponse(
            response = probe.response,
            jsonParserStatus = probe.parserStatus,
            httpStatus = probe.httpStatus,
            parseDiagnostics = probe.parseDiagnostics,
            jsonStructure = probe.jsonStructure,
            snapshotCreated = if (probe.snapshot != null) PocStatus.SUCCESS else PocStatus.NOT_TESTED,
            attemptId = attemptId,
        )
        val result = pocCoordinator.state.value.finalResult
        if (
            result == CallbackHandoffResult.CALLBACK_HANDOFF_WORKS ||
            result == CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED
        ) {
            callbackTimeoutHandler.removeCallbacksAndMessages(null)
        }
        return UsageApiHandling(
            shouldFallbackToHtml = shouldFallback,
            snapshot = probe.snapshot.takeIf { result == CallbackHandoffResult.CALLBACK_HANDOFF_WORKS },
        )
    }

    companion object {
        const val EXTRA_OPEN_LOGIN = "com.datapeek.extra.OPEN_LOGIN"
        const val EXTRA_TRIGGER_REFRESH = "com.datapeek.extra.TRIGGER_REFRESH"
        private const val CALLBACK_WAIT_MILLIS = 30_000L
        private const val HANDOFF_WAIT_MILLIS = 45_000L
        private const val USAGE_API_WAIT_MILLIS = 15_000L
    }
}

private const val API_WEBVIEW_TIMEOUT_MILLIS = 15_000L
private const val API_WEBVIEW_ATTACH_GRACE_MILLIS = 500L

private data class UsageApiHandling(
    val shouldFallbackToHtml: Boolean,
    val snapshot: UsageSnapshot? = null,
)

@Composable
private fun DataPeekApp(
    viewModel: UsageViewModel,
    pocCoordinator: FreetOAuthPocCoordinator,
    initialScreen: AppScreen,
    openLoginRequests: kotlinx.coroutines.flow.StateFlow<Long>,
    widgetRefreshRequests: kotlinx.coroutines.flow.StateFlow<Long>,
    onFreetWebViewCreated: (WebView?, Long) -> Unit,
    onResetOAuth: (Boolean) -> Long,
    onUsageApiRequestStarted: (Long) -> Boolean,
    onUsageApiResponse: (Long, FreetUsageApiProbeResult) -> UsageApiHandling,
    onSnapshotDeliveryStatus: (SnapshotDeliveryStatus) -> Unit,
    onOpenLinkSettings: () -> Unit,
    freetUsageRefreshCoordinator: FreetUsageRefreshCoordinator,
) {
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    val state by viewModel.state.collectAsState()
    val openLoginRequest by openLoginRequests.collectAsState()
    val widgetRefreshRequest by widgetRefreshRequests.collectAsState()
    val developerAccess = developerToolAccess(state.developerModeEnabled)
    val pocState by pocCoordinator.state.collectAsState()
    val pendingFreetUsageRequest by freetUsageRefreshCoordinator.pendingRequest.collectAsState()
    val refreshDiagnostics by freetUsageRefreshCoordinator.diagnostics.collectAsState()
    val context = LocalContext.current
    val exitController = remember { DoubleBackExitController() }
    val exitToast = remember { mutableStateOf<Toast?>(null) }

    DisposableEffect(Unit) {
        onDispose { exitToast.value?.cancel() }
    }
    LaunchedEffect(screen) {
        exitController.reset()
        exitToast.value?.cancel()
        exitToast.value = null
    }
    LaunchedEffect(openLoginRequest) {
        if (openLoginRequest > 0L) {
            onResetOAuth(false)
            screen = AppScreen.LOGIN
        }
    }
    LaunchedEffect(widgetRefreshRequest) {
        if (widgetRefreshRequest > 0L && screen != AppScreen.LOGIN) {
            screen = AppScreen.HOME
            viewModel.refreshFromWidget()
        }
    }

    val showExitMessage = {
        exitToast.value?.cancel()
        exitToast.value = Toast.makeText(context, "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT)
        exitToast.value?.show()
    }
    val navigateTo: (AppScreen) -> Unit = { destination ->
        exitController.reset()
        exitToast.value?.cancel()
        exitToast.value = null
        screen = destination
    }
    val navigateFromLogin = {
        if (canCancelLoginFlowOnBack(pocCoordinator.isHandoffInProgress())) {
            onResetOAuth(false)
            navigateTo(AppScreen.HOME)
        }
    }
    BackHandler {
        if (isHomeRoot(screen)) {
            when (exitController.onHomeBack(SystemClock.elapsedRealtime())) {
                RootBackAction.SHOW_EXIT_MESSAGE -> showExitMessage()
                RootBackAction.EXIT -> {
                    exitToast.value?.cancel()
                    (context as? Activity)?.takeIf { !it.isFinishing }?.finish()
                }
            }
        } else {
            exitController.reset()
            exitToast.value?.cancel()
            if (screen != AppScreen.LOGIN || canCancelLoginFlowOnBack(pocCoordinator.isHandoffInProgress())) {
                appScreenBackDestination(screen, state.developerModeEnabled)?.let { destination ->
                    if (screen == AppScreen.LOGIN) onResetOAuth(false)
                    navigateTo(destination)
                }
            }
        }
    }

    val handleFreetUsageProbe: (Long, FreetUsageApiProbeResult) -> Unit = probeHandler@{ requestId, probe ->
        val request = freetUsageRefreshCoordinator.pendingRequest.value
        probe.bodyDiagnostics?.let { diagnostics ->
            freetUsageRefreshCoordinator.onBodyDecoded(requestId, diagnostics)
        }
        probe.jsonStructure?.let { structure ->
            freetUsageRefreshCoordinator.onJsonStructure(requestId, structure)
        }
        if (
            request?.target == FreetUsageRefreshTarget.JSON_API &&
            probe.parserStatus != PocStatus.NOT_TESTED &&
            probe.response in setOf(
                UsageApiResponseStatus.HTTP_200,
                UsageApiResponseStatus.JSON_PARSE_FAILED,
                UsageApiResponseStatus.NON_JSON,
            )
        ) {
            freetUsageRefreshCoordinator.onJsonParser(
                requestId = requestId,
                status = probe.parserStatus,
                parseDiagnostics = probe.parseDiagnostics,
                jsonStructure = probe.jsonStructure,
            )
        }
        val terminalHostFailure = probe.failureReason in setOf(
            FreetUsageRefreshFailureReason.REFRESH_HOST_UNAVAILABLE,
            FreetUsageRefreshFailureReason.API_WEBVIEW_CREATE_FAILED,
            FreetUsageRefreshFailureReason.API_WEBVIEW_ATTACH_FAILED,
            FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT,
            FreetUsageRefreshFailureReason.REFRESH_SUPERSEDED,
        )
        if (
            request?.id == requestId &&
            request.target == FreetUsageRefreshTarget.JSON_API &&
            !probe.isSuccessfulUsage() &&
            probe.toUsageErrorOrNull()?.requiresLogin() != true &&
            !terminalHostFailure &&
            freetUsageRefreshCoordinator.fallbackToHtml(requestId)
        ) {
            return@probeHandler
        }
        viewModel.handleFreetApiResult(
            requestId = requestId,
            probe = probe,
            onDeliveryStatus = onSnapshotDeliveryStatus,
        )
    }

    DataPeekTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onLogin = {
                        onResetOAuth(false)
                        navigateTo(AppScreen.LOGIN)
                    },
                    onSettings = { navigateTo(AppScreen.SETTINGS) },
                    onHistory = { navigateTo(AppScreen.HISTORY) },
                )
                AppScreen.HISTORY -> UsageHistoryScreen(
                    state = state,
                    onBack = { navigateTo(AppScreen.HOME) },
                    onOpenSettings = { navigateTo(AppScreen.SETTINGS) },
                )
                AppScreen.LOGIN -> LoginScreen(
                    carrier = state.carrier,
                    onBack = navigateFromLogin,
                    onLoginComplete = {
                        viewModel.loginCompleted()
                        navigateTo(AppScreen.HOME)
                    },
                    onPocAuthenticated = { snapshot ->
                        viewModel.loginCompletedWithSnapshot(snapshot, onSnapshotDeliveryStatus)
                        navigateTo(AppScreen.HOME)
                    },
                    pocCoordinator = pocCoordinator,
                    onFreetWebViewCreated = onFreetWebViewCreated,
                    onResetOAuth = onResetOAuth,
                    onUsageApiRequestStarted = onUsageApiRequestStarted,
                    onUsageApiResponse = onUsageApiResponse,
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    state = state,
                    onBack = { navigateTo(AppScreen.HOME) },
                    onLogout = {
                        onResetOAuth(false)
                        viewModel.logout()
                        navigateTo(AppScreen.HOME)
                    },
                    onLogin = {
                        onResetOAuth(false)
                        navigateTo(AppScreen.LOGIN)
                    },
                    onIntervalChanged = viewModel::setRefreshMinutes,
                    onAutoRefreshChanged = viewModel::setAutoRefreshEnabled,
                    onOpenDeveloperOptions = { navigateTo(AppScreen.DEVELOPER_OPTIONS) },
                    onDeveloperModeChanged = viewModel::setDeveloperModeEnabled,
                    onResetDayChanged = viewModel::setResetDay,
                    onClearHistory = viewModel::clearUsageHistory,
                )
                AppScreen.DEVELOPER_OPTIONS -> if (developerAccess.developerOptionsVisible) {
                    DeveloperOptionsScreen(
                        state = state,
                        refreshDiagnostics = refreshDiagnostics,
                        onBack = { navigateTo(AppScreen.SETTINGS) },
                        onOpenDiagnostics = { navigateTo(AppScreen.DIAGNOSTICS) },
                        onUseMock = {
                            viewModel.useMockData()
                            navigateTo(AppScreen.HOME)
                        },
                        onClearMock = viewModel::clearMockData,
                        onClearCache = viewModel::clearCachedSnapshot,
                        onDisableDeveloperMode = {
                            viewModel.setDeveloperModeEnabled(false)
                            navigateTo(AppScreen.SETTINGS)
                        },
                    )
                } else {
                    SettingsScreen(
                        state = state,
                        onBack = { navigateTo(AppScreen.HOME) },
                        onLogout = {
                            onResetOAuth(false)
                            viewModel.logout()
                            navigateTo(AppScreen.HOME)
                        },
                        onLogin = {
                            onResetOAuth(false)
                            navigateTo(AppScreen.LOGIN)
                        },
                        onIntervalChanged = viewModel::setRefreshMinutes,
                        onAutoRefreshChanged = viewModel::setAutoRefreshEnabled,
                        onOpenDeveloperOptions = { navigateTo(AppScreen.DEVELOPER_OPTIONS) },
                        onDeveloperModeChanged = viewModel::setDeveloperModeEnabled,
                    onResetDayChanged = viewModel::setResetDay,
                    onClearHistory = viewModel::clearUsageHistory,
                    )
                }
                AppScreen.DIAGNOSTICS -> if (developerAccess.diagnosticsVisible) {
                    OAuthPocDiagnosticsScreen(
                        state = pocState,
                        refreshState = refreshDiagnostics,
                        onBack = { navigateTo(AppScreen.DEVELOPER_OPTIONS) },
                        onOpenLinkSettings = onOpenLinkSettings,
                    )
                } else {
                    SettingsScreen(
                        state = state,
                        onBack = { navigateTo(AppScreen.HOME) },
                        onLogout = {
                            onResetOAuth(false)
                            viewModel.logout()
                            navigateTo(AppScreen.HOME)
                        },
                        onLogin = {
                            onResetOAuth(false)
                            navigateTo(AppScreen.LOGIN)
                        },
                        onIntervalChanged = viewModel::setRefreshMinutes,
                        onAutoRefreshChanged = viewModel::setAutoRefreshEnabled,
                        onOpenDeveloperOptions = { navigateTo(AppScreen.DEVELOPER_OPTIONS) },
                        onDeveloperModeChanged = viewModel::setDeveloperModeEnabled,
                    onResetDayChanged = viewModel::setResetDay,
                    onClearHistory = viewModel::clearUsageHistory,
                    )
                }
            }

            pendingFreetUsageRequest?.let { request ->
                key("freet-usage-refresh-${request.id}-${request.target}") {
                    val activityAvailable = context is Activity &&
                        !context.isFinishing &&
                        !context.isDestroyed
                    LaunchedEffect(request.id) {
                        freetUsageRefreshCoordinator.onActivityAvailable(request.id, activityAvailable)
                        if (!activityAvailable) {
                            handleFreetUsageProbe(
                                request.id,
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_REQUEST_FAILED,
                                    parserStatus = PocStatus.NOT_TESTED,
                                    failureReason = FreetUsageRefreshFailureReason.REFRESH_HOST_UNAVAILABLE,
                                ),
                            )
                        }
                    }
                    if (activityAvailable) FreetUsageApiWebView(
                        attemptId = request.id,
                        target = request.target,
                        onWebViewCreated = { requestId ->
                            freetUsageRefreshCoordinator.onApiWebViewCreated(requestId, created = true)
                        },
                        onNavigationStarted = { true },
                        onPageFinished = freetUsageRefreshCoordinator::onApiPageFinished,
                        onBodyReceived = { requestId ->
                            freetUsageRefreshCoordinator.onApiBodyReceived(requestId, received = true)
                        },
                        onBodyReadFailed = { requestId ->
                            freetUsageRefreshCoordinator.onApiBodyReceived(requestId, received = false)
                        },
                        onWebViewCleaned = freetUsageRefreshCoordinator::onApiWebViewCleaned,
                        onResult = { requestId, probe ->
                            handleFreetUsageProbe(requestId, probe)
                        },
                        onHttpError = { requestId, status ->
                            handleFreetUsageProbe(
                                requestId,
                                FreetUsageApiProbeResult(
                                    response = when (status) {
                                        401 -> UsageApiResponseStatus.HTTP_401
                                        403 -> UsageApiResponseStatus.HTTP_403
                                        else -> UsageApiResponseStatus.OTHER_ERROR
                                    },
                                    parserStatus = if (status == 401 || status == 403) {
                                        PocStatus.FAIL
                                    } else {
                                        PocStatus.NOT_TESTED
                                    },
                                    httpStatus = status,
                                    failureReason = when (status) {
                                        401 -> FreetUsageRefreshFailureReason.API_HTTP_401
                                        403 -> FreetUsageRefreshFailureReason.API_HTTP_403
                                        else -> FreetUsageRefreshFailureReason.API_HTTP_ERROR
                                    },
                                ),
                            )
                        },
                        onWebViewAttached = { requestId, attached ->
                            freetUsageRefreshCoordinator.onApiWebViewAttached(requestId, attached)
                        },
                        onNavigationRequested = freetUsageRefreshCoordinator::onApiNavigationRequested,
                        onPageStarted = freetUsageRefreshCoordinator::onApiPageStarted,
                        onHttpStatus = freetUsageRefreshCoordinator::onApiHttpStatus,
                        onBodyReadStarted = freetUsageRefreshCoordinator::onApiBodyReadStarted,
                        onNavigationTimeout = { requestId ->
                            handleFreetUsageProbe(
                                requestId,
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_TIMEOUT,
                                    parserStatus = PocStatus.NOT_TESTED,
                                    failureReason = FreetUsageRefreshFailureReason.API_NAVIGATION_TIMEOUT,
                                ),
                            )
                        },
                        onBodyTimeout = { requestId ->
                            handleFreetUsageProbe(
                                requestId,
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_BODY_READ_FAILED,
                                    parserStatus = PocStatus.FAIL,
                                    failureReason = FreetUsageRefreshFailureReason.API_BODY_TIMEOUT,
                                ),
                            )
                        },
                        internalTimeoutEnabled = true,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    carrier: Carrier,
    onBack: () -> Unit,
    onLoginComplete: () -> Unit,
    onPocAuthenticated: (UsageSnapshot) -> Unit,
    pocCoordinator: FreetOAuthPocCoordinator,
    onFreetWebViewCreated: (WebView?, Long) -> Unit,
    onResetOAuth: (Boolean) -> Long,
    onUsageApiRequestStarted: (Long) -> Boolean,
    onUsageApiResponse: (Long, FreetUsageApiProbeResult) -> UsageApiHandling,
) {
    val url = when (carrier) {
        Carrier.SKT_TWORLD -> TWorldProvider.LOGIN_URL
        Carrier.FREET_SKT -> FreetSktProvider.LOGIN_URL
    }
    val pocState by pocCoordinator.state.collectAsState()
    var googleLoginMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var verificationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pocAuthenticatedNotified by remember { mutableStateOf(false) }
    var usageApiProbeRequested by remember { mutableStateOf(false) }
    var recoveryMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val retryRequired = when {
        pocState.webViewIssue != FreetWebViewIssue.NONE -> true
        pocState.finalResult == CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP -> true
        pocState.finalResult == CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED -> true
        pocState.finalResult == CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT -> true
        pocState.finalResult == CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED -> true
        else -> false
    }

    val retryLogin = {
        webView?.stopLoading()
        usageApiProbeRequested = false
        onResetOAuth(false)
        googleLoginMessage = null
        verificationMessage = null
        recoveryMessage = null
        pocAuthenticatedNotified = false
        Unit
    }

    val recoverLoginWebView: (Long) -> Unit = { attemptId ->
        if (pocCoordinator.onLoginWebViewRecoveryRequested(attemptId)) {
            webView?.stopLoading()
            usageApiProbeRequested = false
            onResetOAuth(true)
            googleLoginMessage = null
            verificationMessage = null
            pocAuthenticatedNotified = false
            recoveryMessage = "로그인 화면을 다시 불러오는 중…"
        }
    }

    val launchCustomTab: (String, Long) -> Unit = { observedUrl, attemptId ->
        val urlToOpen = pocCoordinator.onOAuthDetected(observedUrl, attemptId)
        if (urlToOpen != null) {
            val launched = runCatching {
                val context = webView?.context ?: return@runCatching false
                CustomTabsIntent.Builder().build().launchUrl(context, urlToOpen.toUri())
                true
            }.getOrElse { false }
            pocCoordinator.onCustomTabLaunchSucceeded(launched, attemptId)
            if (!launched) {
                googleLoginMessage = FreetLoginNavigationPolicy.GOOGLE_OAUTH_LAUNCH_FAILED_MESSAGE
            }
        }
    }

    Scaffold(
        topBar = {
            OneUiTopAppBar(
                title = { Text("${carrier.label} 로그인") },
                navigationIcon = { OneUiBackButton(onBack) },
                actions = {
                    TextButton(onClick = onLoginComplete) { Text("완료") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            key(pocState.attemptId) {
                WebViewLogin(
                    url = url,
                    attemptId = pocState.attemptId,
                    onGoogleOAuthDetected = if (carrier == Carrier.FREET_SKT) launchCustomTab else { _, _ -> },
                    onFreetCallbackRedirectStarted = pocCoordinator::onFreetCallbackRedirectStarted,
                    onFreetCallbackProcessed = pocCoordinator::onFreetCallbackProcessed,
                    onFreetSnsLoginReached = pocCoordinator::onFreetSnsLoginReached,
                    onFreetSessionAppearsEstablished = { callbackAttemptId ->
                        val established = pocCoordinator.onFreetSessionAppearsEstablished(callbackAttemptId)
                        if (established) usageApiProbeRequested = true
                        established
                    },
                    onFreetAuthenticationFailed = pocCoordinator::onFreetAuthenticationFailed,
                    onUsageVerification = { pagePath, loginPageDetected, parserStatus, snapshot, callbackAttemptId ->
                        if (pocCoordinator.isCurrentAttempt(callbackAttemptId)) {
                            pocCoordinator.recordUsageVerification(
                                pagePath = pagePath,
                                loginPageDetected = loginPageDetected,
                                parserStatus = parserStatus,
                                usageApiStatus = PocStatus.NOT_TESTED,
                                snapshotCreated = if (snapshot != null) PocStatus.SUCCESS else PocStatus.FAIL,
                                attemptId = callbackAttemptId,
                            )
                            val result = pocCoordinator.state.value.finalResult
                            verificationMessage = callbackHandoffMessage(result)
                            if (result == CallbackHandoffResult.CALLBACK_HANDOFF_WORKS &&
                                snapshot != null &&
                                !pocAuthenticatedNotified
                            ) {
                                pocAuthenticatedNotified = true
                                onPocAuthenticated(snapshot)
                            }
                        }
                    },
                    onWebViewIssue = pocCoordinator::onWebViewIssue,
                    onWebViewContentVisible = pocCoordinator::onWebViewContentVisible,
                    onLoginWebViewRecoveryRequested = recoverLoginWebView,
                    onLoginWebViewPageStarted = pocCoordinator::onLoginWebViewPageStarted,
                    onLoginPageFinished = pocCoordinator::onLoginWebViewPageFinished,
                    onFreetLoginPageLoaded = { callbackAttemptId ->
                        if (pocCoordinator.isCurrentAttempt(callbackAttemptId)) {
                            pocCoordinator.onFreetLoginPageLoaded(callbackAttemptId)
                            recoveryMessage = null
                        }
                    },
                    onWebViewCreated = { created, callbackAttemptId ->
                        if (pocCoordinator.isCurrentAttempt(callbackAttemptId)) {
                            webView = created
                            if (carrier == Carrier.FREET_SKT) onFreetWebViewCreated(created, callbackAttemptId)
                        }
                    },
                    modifier = if (FreetLoginLayoutPolicy.webViewUsesFullAvailableSpace) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                    },
                )
            }
            val usageApiProbeActive = usageApiProbeRequested && when (pocState.usageApiResponse) {
                UsageApiResponseStatus.NOT_TESTED,
                UsageApiResponseStatus.IN_PROGRESS,
                -> true
                else -> false
            }
            if (carrier == Carrier.FREET_SKT && usageApiProbeActive) {
                val apiAttemptId = pocState.attemptId
                key("api-$apiAttemptId") {
                    FreetUsageApiWebView(
                    attemptId = apiAttemptId,
                    onWebViewCreated = pocCoordinator::onUsageApiWebViewCreated,
                    onNavigationStarted = { callbackAttemptId ->
                        if (onUsageApiRequestStarted(callbackAttemptId)) {
                            pocCoordinator.onUsageApiNavigationStarted(callbackAttemptId)
                            true
                        } else {
                            false
                        }
                    },
                    onPageFinished = pocCoordinator::onUsageApiPageFinished,
                    onBodyReceived = pocCoordinator::onUsageApiBodyReceived,
                    onBodyReadFailed = pocCoordinator::onUsageApiBodyReadFailed,
                    onWebViewCleaned = pocCoordinator::onUsageApiWebViewCleaned,
                    onResult = { callbackAttemptId, probe ->
                        if (!pocCoordinator.isCurrentAttempt(callbackAttemptId)) return@FreetUsageApiWebView
                        val handling = onUsageApiResponse(callbackAttemptId, probe)
                        usageApiProbeRequested = false
                        val result = pocCoordinator.state.value.finalResult
                        verificationMessage = callbackHandoffMessage(result)
                        if (handling.shouldFallbackToHtml) {
                            pocCoordinator.onHtmlUsageFallbackStarted(callbackAttemptId)
                            webView?.loadUrl(FreetSktProvider.USAGE_URL)
                        }
                        if (result == CallbackHandoffResult.CALLBACK_HANDOFF_WORKS && !pocAuthenticatedNotified) {
                            pocAuthenticatedNotified = true
                            handling.snapshot?.let(onPocAuthenticated)
                        }
                    },
                    onHttpError = { callbackAttemptId, status ->
                        if (!pocCoordinator.isCurrentAttempt(callbackAttemptId)) return@FreetUsageApiWebView
                        val response = when (status) {
                            401 -> UsageApiResponseStatus.HTTP_401
                            403 -> UsageApiResponseStatus.HTTP_403
                            else -> UsageApiResponseStatus.OTHER_ERROR
                        }
                        val handling = onUsageApiResponse(
                            callbackAttemptId,
                            FreetUsageApiProbeResult(
                                response = response,
                                parserStatus = if (status == 401 || status == 403) PocStatus.FAIL else PocStatus.NOT_TESTED,
                                httpStatus = status,
                            ),
                        )
                        usageApiProbeRequested = false
                        val result = pocCoordinator.state.value.finalResult
                        verificationMessage = callbackHandoffMessage(result)
                        if (handling.shouldFallbackToHtml) {
                            pocCoordinator.onHtmlUsageFallbackStarted(callbackAttemptId)
                            webView?.loadUrl(FreetSktProvider.USAGE_URL)
                        }
                    },
                    )
                }
            }
            if (retryRequired) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("프리티 로그인이 완료되지 않았습니다.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "진단 정보에서 callback 수신 여부와 실패 단계를 확인할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = retryLogin, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
                    }
                }
            } else {
                val progressMessage = if (
                    pocState.callbackHandedToWebView == PocStatus.SUCCESS &&
                    pocState.finalResult == CallbackHandoffResult.NOT_TESTED
                ) {
                    "프리티 인증 확인 중…"
                } else {
                    null
                }
                val message = recoveryMessage ?: verificationMessage ?: progressMessage ?: googleLoginMessage
                if (message != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            color = if (googleLoginMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun WebViewLogin(
    url: String,
    attemptId: Long,
    onGoogleOAuthDetected: (String, Long) -> Unit,
    onFreetCallbackRedirectStarted: (Long) -> Unit,
    onFreetCallbackProcessed: (Long) -> Unit,
    onFreetSnsLoginReached: (Long) -> Unit,
    onFreetSessionAppearsEstablished: (Long) -> Boolean,
    onFreetAuthenticationFailed: (Long) -> Unit,
    onUsageVerification: (String, Boolean, PocStatus, UsageSnapshot?, Long) -> Unit,
    onWebViewIssue: (FreetWebViewIssue, Long) -> Unit,
    onWebViewContentVisible: (Boolean, Long) -> Unit,
    onLoginWebViewRecoveryRequested: (Long) -> Unit,
    onLoginWebViewPageStarted: (Long) -> Unit,
    onLoginPageFinished: (Long) -> Unit,
    onFreetLoginPageLoaded: (Long) -> Unit,
    onWebViewCreated: (WebView?, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val usageParser = remember { FreetHtmlUsageParser() }
    val loginWebView = remember(attemptId) { mutableStateOf<WebView?>(null) }
    DisposableEffect(attemptId) {
        onDispose {
            loginWebView.value?.let { view ->
                view.stopLoading()
                (view.parent as? ViewGroup)?.removeView(view)
                view.removeAllViews()
                view.destroy()
            }
            loginWebView.value = null
            onWebViewCreated(null, attemptId)
        }
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                var loginNavigationRequested = false

                fun requestRecovery(issue: FreetWebViewIssue) {
                    onWebViewIssue(issue, attemptId)
                    onLoginWebViewRecoveryRequested(attemptId)
                }

                loginWebView.value = this
                onWebViewCreated(this, attemptId)
                isSaveEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return when (FreetLoginNavigationPolicy.classify(request.url.toString())) {
                                FreetLoginNavigationAction.OPEN_CUSTOM_TAB -> {
                                    onGoogleOAuthDetected(request.url.toString(), attemptId)
                                    true
                                }
                                FreetLoginNavigationAction.ALLOW_OFFICIAL -> {
                                    false
                                }
                                FreetLoginNavigationAction.BLOCK_EXTERNAL -> true
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            onLoginWebViewPageStarted(attemptId)
                            if (url.equals("about:blank", ignoreCase = true) && loginNavigationRequested) {
                                requestRecovery(FreetWebViewIssue.ABOUT_BLANK)
                                return
                            }
                            val uri = url.toUri()
                            when {
                                uri.host.equals("www.freet.co.kr", ignoreCase = true) && uri.path == "/oauth/google" -> {
                                    onFreetCallbackRedirectStarted(attemptId)
                                }
                                uri.host.equals("api.freet.co.kr", ignoreCase = true) && uri.path == "/session/snsLogin" -> {
                                    onFreetSnsLoginReached(attemptId)
                                }
                            }
                            when (FreetLoginNavigationPolicy.classify(url)) {
                                FreetLoginNavigationAction.OPEN_CUSTOM_TAB -> {
                                    onGoogleOAuthDetected(url, attemptId)
                                    view.stopLoading()
                                }
                                FreetLoginNavigationAction.ALLOW_OFFICIAL -> Unit
                                FreetLoginNavigationAction.BLOCK_EXTERNAL -> Unit
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            val uri = url.toUri()
                            val path = uri.path ?: "/"
                            if (url.equals("about:blank", ignoreCase = true)) {
                                requestRecovery(FreetWebViewIssue.ABOUT_BLANK)
                                return
                            }
                            onLoginPageFinished(attemptId)
                            if (uri.host.equals("www.freet.co.kr", ignoreCase = true) && path == "/login") {
                                onFreetLoginPageLoaded(attemptId)
                            }
                            if (uri.host.equals("www.freet.co.kr", ignoreCase = true) && path == "/oauth/google") {
                                onFreetCallbackProcessed(attemptId)
                            }
                            if (uri.host.equals("api.freet.co.kr", ignoreCase = true) && path == "/session/snsLogin") {
                                onFreetSnsLoginReached(attemptId)
                            }
                            if (
                                uri.host.equals("www.freet.co.kr", ignoreCase = true) &&
                                (path == "/" || path == "/main")
                            ) {
                                onFreetSessionAppearsEstablished(attemptId)
                            }
                            if (uri.host.equals("www.freet.co.kr", ignoreCase = true) && path == "/login") {
                                onFreetAuthenticationFailed(attemptId)
                            }
                            if (FreetLoginNavigationPolicy.classify(url) == FreetLoginNavigationAction.ALLOW_OFFICIAL) {
                                view.postDelayed({
                                    if (view.url == url) {
                                        checkVisibleWebViewContent(view) { visible ->
                                            onWebViewContentVisible(visible, attemptId)
                                            if (!visible) onLoginWebViewRecoveryRequested(attemptId)
                                        }
                                    }
                                }, CONTENT_CHECK_DELAY_MILLIS)
                            }
                            when (path) {
                                "/oauth/google" -> Unit
                                "/login", "/mypage/usage" -> {
                                    verifyUsagePage(view, url, usageParser, attemptId, onUsageVerification)
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                requestRecovery(FreetWebViewIssue.NAVIGATION_ERROR)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame) {
                                requestRecovery(FreetWebViewIssue.HTTP_ERROR)
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: android.webkit.RenderProcessGoneDetail,
                        ): Boolean {
                            requestRecovery(FreetWebViewIssue.NAVIGATION_ERROR)
                            return true
                        }
                }
                loginNavigationRequested = true
                loadUrl(url)
            }
        },
    )
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun FreetUsageApiWebView(
    attemptId: Long,
    target: FreetUsageRefreshTarget = FreetUsageRefreshTarget.JSON_API,
    onWebViewCreated: (Long) -> Unit,
    onNavigationStarted: (Long) -> Boolean,
    onPageFinished: (Long) -> Unit,
    onBodyReceived: (Long) -> Unit,
    onBodyReadFailed: (Long) -> Unit,
    onWebViewCleaned: (Long) -> Unit,
    onResult: (Long, FreetUsageApiProbeResult) -> Unit,
    onHttpError: (Long, Int) -> Unit,
    onWebViewAttached: (Long, Boolean) -> Unit = { _, _ -> },
    onNavigationRequested: (Long) -> Unit = {},
    onPageStarted: (Long) -> Unit = {},
    onHttpStatus: (Long, Int) -> Unit = { _, _ -> },
    onBodyReadStarted: (Long) -> Unit = {},
    onNavigationTimeout: (Long) -> Unit = {},
    onBodyTimeout: (Long) -> Unit = {},
    internalTimeoutEnabled: Boolean = false,
) {
    val context = LocalContext.current
    val usageParser = remember { FreetUsageParser() }
    val htmlParser = remember { FreetHtmlUsageParser() }
    val apiWebView = remember { mutableStateOf<WebView?>(null) }
    val navigationStarted = remember(attemptId) { mutableStateOf(false) }
    val pageStarted = remember(attemptId) { mutableStateOf(false) }
    val pageFinished = remember(attemptId) { mutableStateOf(false) }
    val bodyReadStarted = remember(attemptId) { mutableStateOf(false) }
    val delivered = remember(attemptId) { mutableStateOf(false) }

    LaunchedEffect(attemptId, navigationStarted.value, pageFinished.value, delivered.value) {
        if (internalTimeoutEnabled && navigationStarted.value && !pageFinished.value && !delivered.value) {
            kotlinx.coroutines.delay(API_WEBVIEW_TIMEOUT_MILLIS)
            if (navigationStarted.value && !pageFinished.value && !delivered.value) {
                onNavigationTimeout(attemptId)
            }
        }
    }
    LaunchedEffect(attemptId, bodyReadStarted.value, delivered.value) {
        if (internalTimeoutEnabled && bodyReadStarted.value && !delivered.value) {
            kotlinx.coroutines.delay(API_WEBVIEW_TIMEOUT_MILLIS)
            if (bodyReadStarted.value && !delivered.value) {
                onBodyTimeout(attemptId)
            }
        }
    }

    DisposableEffect(attemptId) {
        onDispose {
            apiWebView.value?.let { view ->
                view.stopLoading()
                (view.parent as? ViewGroup)?.removeView(view)
                view.clearHistory()
                view.removeAllViews()
                view.destroy()
            }
            apiWebView.value = null
            onWebViewCleaned(attemptId)
        }
    }

    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0f),
        factory = { viewContext ->
            CookieManager.getInstance().setAcceptCookie(true)
            WebView(viewContext).apply {
                alpha = 0f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false

                fun deliver(result: FreetUsageApiProbeResult) {
                    if (delivered.value) return
                    delivered.value = true
                    onResult(attemptId, result)
                }

                var navigationRequestPending = false
                fun startNavigation() {
                    if (navigationStarted.value || navigationRequestPending || !isAttachedToWindow) return
                    onWebViewAttached(attemptId, true)
                    if (onNavigationStarted(attemptId)) {
                        navigationRequestPending = true
                        navigationStarted.value = true
                        runCatching {
                            loadUrl(
                                if (target == FreetUsageRefreshTarget.JSON_API) {
                                    FreetSktProvider.USAGE_API_URL
                                } else {
                                    FreetSktProvider.USAGE_URL
                                },
                            )
                        }.onSuccess {
                            onNavigationRequested(attemptId)
                        }.onFailure {
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_REQUEST_FAILED,
                                    parserStatus = PocStatus.NOT_TESTED,
                                    failureReason = FreetUsageRefreshFailureReason.API_NAVIGATION_FAILED,
                                ),
                            )
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        pageStarted.value = true
                        onPageStarted(attemptId)
                        if (url.equals("about:blank", ignoreCase = true) && navigationStarted.value) {
                            onBodyReadFailed(attemptId)
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_BODY_READ_FAILED,
                                    parserStatus = PocStatus.FAIL,
                                    failureReason = FreetUsageRefreshFailureReason.API_BODY_TIMEOUT,
                                ),
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (delivered.value) return
                        if (url.equals("about:blank", ignoreCase = true) && !navigationStarted.value) return
                        onHttpStatus(attemptId, 200)
                        pageStarted.value = true
                        pageFinished.value = true
                        onPageFinished(attemptId)
                        val uri = url.toUri()
                        val path = uri.path ?: "/"
                        if (url.equals("about:blank", ignoreCase = true)) {
                            onBodyReadFailed(attemptId)
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_BODY_READ_FAILED,
                                    parserStatus = PocStatus.FAIL,
                                    failureReason = FreetUsageRefreshFailureReason.API_BODY_TIMEOUT,
                                ),
                            )
                            return
                        }
                        if (uri.host.equals("www.freet.co.kr", ignoreCase = true) && path == "/login") {
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.AUTH_ERROR,
                                    parserStatus = PocStatus.FAIL,
                                ),
                            )
                            return
                        }

                        fun deliverBody(encodedBody: String?) {
                            val decoded = decodeEvaluateJavascriptString(encodedBody)
                            val body = decoded.body
                            if (body == null) {
                                onBodyReadFailed(attemptId)
                                deliver(
                                    FreetUsageApiProbeResult(
                                        response = UsageApiResponseStatus.API_BODY_READ_FAILED,
                                        parserStatus = PocStatus.FAIL,
                                        bodyDiagnostics = decoded.diagnostics,
                                    ),
                                )
                                return
                            }
                            onBodyReceived(attemptId)
                            val result = if (target == FreetUsageRefreshTarget.JSON_API) {
                                    inspectFreetUsageApiBody(
                                        body = body,
                                        parser = usageParser,
                                        httpStatus = 200,
                                    )
                                } else {
                                    inspectFreetUsageHtmlBody(
                                        body = body,
                                        parser = htmlParser,
                                        httpStatus = 200,
                                    )
                                }
                            deliver(result.copy(bodyDiagnostics = decoded.diagnostics))
                        }

                        runCatching {
                            bodyReadStarted.value = true
                            onBodyReadStarted(attemptId)
                            view.evaluateJavascript(
                                if (target == FreetUsageRefreshTarget.JSON_API) {
                                    "document.body && document.body.innerText || ''"
                                } else {
                                    "document.documentElement && document.documentElement.outerHTML || ''"
                                },
                                ::deliverBody,
                            )
                        }.onFailure {
                            onBodyReadFailed(attemptId)
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_BODY_READ_FAILED,
                                    parserStatus = PocStatus.FAIL,
                                    failureReason = FreetUsageRefreshFailureReason.API_BODY_TIMEOUT,
                                ),
                            )
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            deliver(
                                FreetUsageApiProbeResult(
                                    response = UsageApiResponseStatus.API_REQUEST_FAILED,
                                    parserStatus = PocStatus.NOT_TESTED,
                                    failureReason = FreetUsageRefreshFailureReason.API_NAVIGATION_FAILED,
                                ),
                            )
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && !delivered.value) {
                            delivered.value = true
                            onHttpStatus(attemptId, errorResponse.statusCode)
                            onHttpError(attemptId, errorResponse.statusCode)
                        }
                    }
                }
                apiWebView.value = this
                onWebViewCreated(attemptId)
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        onWebViewAttached(attemptId, true)
                        startNavigation()
                    }

                    override fun onViewDetachedFromWindow(view: View) = Unit
                })
                postDelayed({
                    if (!isAttachedToWindow && !navigationStarted.value && !delivered.value) {
                        onWebViewAttached(attemptId, false)
                        deliver(
                            FreetUsageApiProbeResult(
                                response = UsageApiResponseStatus.API_REQUEST_FAILED,
                                parserStatus = PocStatus.NOT_TESTED,
                                failureReason = FreetUsageRefreshFailureReason.API_WEBVIEW_ATTACH_FAILED,
                            ),
                        )
                    } else {
                        startNavigation()
                    }
                }, API_WEBVIEW_ATTACH_GRACE_MILLIS)
            }
        },
        update = { view ->
            if (view.isAttachedToWindow) {
                onWebViewAttached(attemptId, true)
            }
        },
    )
}

private const val CONTENT_CHECK_DELAY_MILLIS = 750L

private fun checkVisibleWebViewContent(view: WebView, onResult: (Boolean) -> Unit) {
    view.evaluateJavascript(
        "Boolean(document.body && (document.body.innerText.trim().length > 0 || document.querySelector('input,button,form,main') !== null))",
        { result -> onResult(result == "true") },
    )
}

private fun verifyUsagePage(
    view: WebView,
    rawUrl: String,
    parser: FreetHtmlUsageParser,
    attemptId: Long,
    onUsageVerification: (String, Boolean, PocStatus, UsageSnapshot?, Long) -> Unit,
) {
    val path = rawUrl.toUri().path ?: "/"
    if (path == "/login") {
        onUsageVerification(path, true, PocStatus.FAIL, null, attemptId)
        return
    }
    if (path != "/mypage/usage") return
    view.evaluateJavascript("document.documentElement.outerHTML") { encodedHtml ->
        val html = runCatching { JSONArray("[$encodedHtml]").getString(0) }.getOrNull()
        val snapshot = html?.let { runCatching { parser.parse(it) }.getOrNull() }
        onUsageVerification(
            path,
            false,
            if (snapshot != null) PocStatus.SUCCESS else PocStatus.FAIL,
            snapshot,
            attemptId,
        )
    }
}

private fun callbackHandoffMessage(result: CallbackHandoffResult): String = when (result) {
    CallbackHandoffResult.CALLBACK_HANDOFF_WORKS -> "로그인이 완료되었습니다. 사용량을 확인했습니다."
    CallbackHandoffResult.CALLBACK_NOT_DISPATCHED_TO_APP -> "로그인을 완료하지 못했습니다. 다시 시도해 주세요."
    CallbackHandoffResult.CALLBACK_HANDOFF_AUTH_FAILED -> "프리티 로그인 상태를 확인할 수 없습니다. 다시 시도해 주세요."
    CallbackHandoffResult.CALLBACK_HANDOFF_TIMEOUT -> "사용량 확인 시간이 초과되었습니다. 다시 시도해 주세요."
    CallbackHandoffResult.CALLBACK_HANDOFF_NOT_SUPPORTED -> "이 기기에서 로그인을 완료하지 못했습니다. 다시 시도해 주세요."
    CallbackHandoffResult.NOT_TESTED -> "로그인 확인 중…"
}

@Composable
private fun OAuthPocDiagnosticCard(state: OAuthPocState, onOpenLinkSettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("OAuth callback handoff POC 진단", style = MaterialTheme.typography.titleSmall)
            Text("Login attempt ID: ${state.attemptId}", style = MaterialTheme.typography.bodySmall)
            Text("Attempt phase: ${state.attemptPhase}", style = MaterialTheme.typography.bodySmall)
            DiagnosticRow("Login WebView created", state.loginWebViewCreated)
            DiagnosticRow("Login page started", state.loginPageStarted)
            DiagnosticRow("Login page finished", state.loginPageFinished)
            DiagnosticRow("Blank recovery triggered", state.blankRecoveryTriggered)
            DiagnosticRow("Retry reset completed", state.retryResetCompleted)
            DiagnosticRow("Freet login WebView", state.freetLoginWebView)
            DiagnosticRow("Google OAuth detected", state.oauthDetected)
            DiagnosticRow("Custom Tab opened", state.customTabOpened)
            DiagnosticRow("App resumed", state.returnedFromBrowser)
            DiagnosticRow("Callback intent received", state.callbackIntentReceived)
            DiagnosticRow("Callback URI validated", state.callbackUriValidated)
            DiagnosticRow("Existing WebView alive", state.existingWebViewAlive)
            DiagnosticRow("Callback handed to WebView", state.callbackHandedToWebView)
            DiagnosticRow("Freet callback redirect started", state.freetCallbackRedirectStarted)
            DiagnosticRow("Freet callback processed", state.freetCallbackProcessed)
            DiagnosticRow("Freet SNS login reached", state.freetSnsLoginReached)
            DiagnosticRow("Freet session appears established", state.freetSessionAppearsEstablished)
            DiagnosticRow("Freet authentication completed", state.freetAuthenticationCompleted)
            DiagnosticRow("Freet auth failed", state.freetAuthFailed)
            DiagnosticRow("Usage HTML page loaded", state.usagePageLoaded)
            DiagnosticRow("Usage page authenticated", state.usagePageAuthenticated)
            DiagnosticRow("Usage API request started", state.usageApiRequestStarted)
            DiagnosticRow("Usage API WebView created", state.usageApiWebViewCreated)
            DiagnosticRow("Usage API navigation started", state.usageApiNavigationStarted)
            DiagnosticRow("Usage API page finished", state.usageApiPageFinished)
            DiagnosticRow("Usage API body received", state.usageApiBodyReceived)
            DiagnosticRow("API WebView cleaned", state.apiWebViewCleaned)
            DiagnosticRow("Usage API response received", state.usageApiResponseReceived)
            Text("Usage API response: ${state.usageApiResponse}", style = MaterialTheme.typography.bodySmall)
            Text("Usage API HTTP: ${state.usageApiHttpStatus ?: "NOT_TESTED"}", style = MaterialTheme.typography.bodySmall)
            DiagnosticRow("Usage API authenticated", state.usageApiAuthenticated)
            DiagnosticRow("Usage JSON parser", state.usageJsonParser)
            DiagnosticRow("JSON syntax", state.usageJsonSyntax)
            DiagnosticRow("Schema matched", state.usageSchemaMatched)
            DiagnosticRow("Usage fields found", state.usageFieldsFound)
            DiagnosticRow("Usage values valid", state.usageValuesValid)
            DiagnosticRow("UsageSnapshot created", state.usageSnapshotCreated)
            state.usageDataEntry?.let { FreetUsageDataEntryDiagnostic(it) }
            DiagnosticRow("Repository cache updated", state.repositoryCacheUpdated)
            DiagnosticRow("Home UI updated", state.homeUiUpdated)
            DiagnosticRow("Widget update requested", state.widgetUpdateRequested)
            state.usageJsonStructure?.let { structure ->
                var showStructure by rememberSaveable { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showStructure = !showStructure },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showStructure) "응답 구조 닫기" else "응답 구조 보기")
                }
                if (showStructure) {
                    Text(
                        "Root: ${structure.rootType} · JSON syntax: ${structure.jsonSyntax} · 값은 표시하지 않음",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    structure.nodes.forEach { node ->
                        val arrayInfo = node.arrayLength?.let { " · length=$it" } ?: ""
                        val candidateInfo = if (node.isUsageCandidate) " · usage candidate" else ""
                        Text(
                            "${node.path}: ${node.type}$arrayInfo$candidateInfo",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            DiagnosticRow("Usage HTML parser", state.parserStatus)
            Text("WebView issue: ${state.webViewIssue}", style = MaterialTheme.typography.bodySmall)
            if (state.callbackHandedToWebView == PocStatus.SUCCESS && state.finalResult == CallbackHandoffResult.NOT_TESTED) {
                Text("프리티 인증 확인 중…", style = MaterialTheme.typography.bodySmall)
            }
            Text("Final result: ${state.finalResult}", fontWeight = FontWeight.Bold)
            Text(
                "이 HTTPS intent-filter는 검증된 Android App Link가 아닙니다. 기기에 따라 브라우저에서 앱을 선택하거나 Android 설정에서 지원되는 링크를 허용해야 할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenLinkSettings, modifier = Modifier.fillMaxWidth()) {
                Text("앱 링크 설정 열기")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OAuthPocDiagnosticsScreen(
    state: OAuthPocState,
    refreshState: FreetUsageRefreshDiagnostics,
    onBack: () -> Unit,
    onOpenLinkSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            OneUiTopAppBar(
                title = { Text("진단 정보") },
                navigationIcon = { OneUiBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            FreetUsageRefreshDiagnosticCard(refreshState)
            WidgetSizeDiagnosticsCard(LocalContext.current)
            OAuthPocDiagnosticCard(state, onOpenLinkSettings)
        }
    }
}

@Composable
private fun WidgetSizeDiagnosticsCard(context: android.content.Context) {
    val observedSize = remember { WidgetSizeDiagnosticsStore.readLatest(context) }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Widget 크기 진단", style = MaterialTheme.typography.titleSmall)
            Text(formatWidgetObservedSize(observedSize), style = MaterialTheme.typography.bodySmall)
            Text("셀 수가 아닌 실제 Glance dp 크기 기준", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FreetUsageRefreshDiagnosticCard(state: FreetUsageRefreshDiagnostics) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("사용량 새로고침 pipeline", style = MaterialTheme.typography.titleSmall)
            Text("Refresh ID: ${state.refreshId}", style = MaterialTheme.typography.bodySmall)
            Text("Origin: ${state.origin ?: "NOT_TESTED"}", style = MaterialTheme.typography.bodySmall)
            DiagnosticRow("Refresh requested", state.refreshRequested)
            DiagnosticRow("Refresh lock acquired", state.refreshLockAcquired)
            DiagnosticRow("Activity available", state.activityAvailable)
            DiagnosticRow("API WebView created", state.apiWebViewCreated)
            DiagnosticRow("API WebView attached", state.apiWebViewAttached)
            DiagnosticRow("API navigation requested", state.apiNavigationRequested)
            DiagnosticRow("API page started", state.apiPageStarted)
            DiagnosticRow("API page finished", state.apiPageFinished)
            DiagnosticRow("API HTTP status", state.apiHttpStatus)
            Text("API HTTP code: ${state.httpStatus ?: "NOT_TESTED"}", style = MaterialTheme.typography.bodySmall)
            DiagnosticRow("API body read started", state.apiBodyReadStarted)
            DiagnosticRow("API body received", state.apiBodyReceived)
            state.bodyDiagnostics?.let { body ->
                DiagnosticRow("JSON callback decoded", body.bodyDecoded)
                Text("Callback value type: ${body.callbackValueType}", style = MaterialTheme.typography.bodySmall)
                Text("Raw callback length: ${body.rawCallbackLength}", style = MaterialTheme.typography.bodySmall)
                Text("Raw first character: ${body.rawFirstNonWhitespace ?: "NONE"}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "JSON string wrapper detected: ${if (body.jsonStringWrapperDetected) "YES" else "NO"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Decoded body length: ${body.decodedBodyLength}", style = MaterialTheme.typography.bodySmall)
                Text("Decoded first character: ${body.decodedFirstNonWhitespace ?: "NONE"}", style = MaterialTheme.typography.bodySmall)
                Text("Decoded root type: ${body.decodedRootType}", style = MaterialTheme.typography.bodySmall)
            }
            DiagnosticRow("JSON syntax valid", state.parseDiagnostics?.jsonSyntax.toDiagnosticStatus())
            DiagnosticRow("Root type supported", state.rootTypeSupported)
            DiagnosticRow("Schema matched", state.parseDiagnostics?.schemaMatched.toDiagnosticStatus())
            DiagnosticRow("Usage fields found", state.parseDiagnostics?.usageFieldsFound.toDiagnosticStatus())
            DiagnosticRow("Usage values parsed", state.parseDiagnostics?.usageValuesValid.toDiagnosticStatus())
            DiagnosticRow("UsageSnapshot created", state.parseDiagnostics?.snapshotCreated.toDiagnosticStatus())
            state.parseDiagnostics?.dataEntry?.let { FreetUsageDataEntryDiagnostic(it) }
            DiagnosticRow("JSON parser", state.jsonParser)
            DiagnosticRow("HTML fallback", state.htmlFallback)
            state.jsonStructure?.let { structure ->
                var showStructure by rememberSaveable { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showStructure = !showStructure },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showStructure) "실제 응답 구조 닫기" else "실제 응답 구조 보기")
                }
                if (showStructure) {
                    Text(
                        "Root: ${structure.rootType} · JSON syntax: ${structure.jsonSyntax} · 값은 표시하지 않음",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    structure.nodes.forEach { node ->
                        val arrayInfo = node.arrayLength?.let { " · length=$it" } ?: ""
                        val candidateInfo = if (node.isUsageCandidate) " · usage candidate" else ""
                        Text(
                            "${node.path}: ${node.type}$arrayInfo$candidateInfo",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            DiagnosticRow("Repository updated", state.repositoryUpdated)
            DiagnosticRow("API WebView cleanup", state.apiWebViewCleanup)
            DiagnosticRow("Final refresh result", state.finalRefreshResult)
            Text("Final outcome: ${state.finalOutcome}", fontWeight = FontWeight.Bold)
            Text("Failure: ${state.failure}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, status: PocStatus) {
    Text("$label: $status", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FreetUsageDataEntryDiagnostic(data: FreetUsageDataEntryDiagnostics) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("DATA[0] 단위 진단 (DEBUG)", style = MaterialTheme.typography.labelLarge)
        Text("total: ${data.total ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("totalText: ${data.totalText ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("use: ${data.use ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("useText: ${data.useText ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("remain: ${data.remain ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("remainText: ${data.remainText ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("remainRate: ${data.remainRate ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text("dataType: ${data.dataType ?: "NOT_FOUND"}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Raw numeric unit: KiB · " +
                "Parsed source units: total=${data.totalUnit?.displayLabel ?: "NONE"}, " +
                "used=${data.usedUnit?.displayLabel ?: "NONE"}, " +
                "remaining=${data.remainingUnit?.displayLabel ?: "NONE"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun UsageParseStatus?.toDiagnosticStatus(): PocStatus = when (this) {
    UsageParseStatus.SUCCESS -> PocStatus.SUCCESS
    UsageParseStatus.FAIL -> PocStatus.FAIL
    UsageParseStatus.NOT_TESTED,
    null,
    -> PocStatus.NOT_TESTED
}

private fun java.time.Instant.toLocalTimeLabel(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.getDefault()))
