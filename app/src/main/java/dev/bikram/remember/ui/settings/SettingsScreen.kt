@file:Suppress("ConfigurationScreenWidthHeight")

package dev.bikram.remember.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPreferencesState
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.QuickCaptureState
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.di.SettingsDependenciesEntryPoint
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.ui.common.AppBottomSheetDragHandle
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.components.rememberResponsiveActionIconSize
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.nav.DEV_OPTIONS_SHARED_BOUNDS_KEY
import dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope
import dev.bikram.remember.ui.nav.LocalSharedTransitionScope
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import dev.bikram.remember.update.RememberUpdateInfo
import dev.bikram.remember.update.RememberUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BackupFolderTarget {
    Local,
    Cloud,
}

private data class PendingRestore(
    val uri: Uri,
    val mediaSummary: BackupIo.RestoreMediaSummary,
)

private const val SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS = 900L

enum class SettingsSectionKey(
    val routeKey: String,
    val iconName: String,
    @param:StringRes val titleRes: Int,
) {
    Appearance("appearance", "palette", R.string.settings_section_appearance),
    Notifications("notifications", "notifications", R.string.settings_notifications_section),
    Swipe("swipe", "swipe_left", R.string.settings_swipe_section),
    Security("security", "security", R.string.settings_section_security),
    Backup("backup", "save", R.string.settings_backup_section),
    Updates("updates", "system_update", R.string.settings_updates_section),
    About("about", "info", R.string.settings_section_about),
    DevOptions("dev_options", "developer_board", R.string.dev_options_title),
}

val settingsPaneSections: List<SettingsSectionKey>
    get() =
        SettingsSectionKey.entries.filter { sectionKey ->
            sectionKey != SettingsSectionKey.DevOptions
        }

fun settingsSectionKeyForHighlight(highlightSectionKey: String?): SettingsSectionKey? =
    when (highlightSectionKey?.substringBefore(".")) {
        SettingsSectionKey.Appearance.routeKey -> SettingsSectionKey.Appearance
        SettingsSectionKey.Notifications.routeKey -> SettingsSectionKey.Notifications
        SettingsSectionKey.Swipe.routeKey,
        "swipe_actions",
        -> SettingsSectionKey.Swipe
        SettingsSectionKey.Security.routeKey -> SettingsSectionKey.Security
        SettingsSectionKey.Backup.routeKey -> SettingsSectionKey.Backup
        SettingsSectionKey.Updates.routeKey -> SettingsSectionKey.Updates
        SettingsSectionKey.About.routeKey -> SettingsSectionKey.About
        else -> null
    }

private object SettingsScreenSessionState {
    var collapsedSectionKeys: Set<String> = emptySet()
    var listFirstVisibleItemIndex: Int = 0
    var listFirstVisibleItemScrollOffset: Int = 0
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Suppress("CyclomaticComplexMethod")
@Composable
fun SettingsRoute(
    onOpenIntro: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenDevOptions: () -> Unit = {},
    updateVm: RememberUpdateViewModel = hiltViewModel(),
    onUpdateCheckStarted: () -> Unit = {},
    selectedSectionKey: SettingsSectionKey? = null,
    showTopActions: Boolean = true,
    showSectionHeaders: Boolean = true,
    showAboutHeader: Boolean = true,
    showAboutHeaderTitle: Boolean = true,
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val resources = LocalResources.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val devModePrefs = settingsDependencies.devModePrefs()
    val lockPrefs = settingsDependencies.lockPrefs()
    val interactionPrefs = settingsDependencies.interactionPrefs()
    val quickCapturePrefs = settingsDependencies.quickCapturePrefs()
    val reminderPrefs = settingsDependencies.reminderPrefs()
    val backupPrefs = settingsDependencies.backupPrefs()
    val backupIo = settingsDependencies.backupIo()
    val themePrefs = settingsDependencies.themePrefs()
    val viewOptionsPrefs = settingsDependencies.viewOptionsPrefs()
    val noteRepository = settingsDependencies.noteRepository()
    val updatePrefs = settingsDependencies.updatePrefs()
    val playInAppUpdateProgressController = settingsDependencies.playInAppUpdateProgressController()
    val rememberUpdateState: RememberUpdateState = settingsDependencies.rememberUpdateState()
    val updateCheckWorkScheduler = settingsDependencies.updateCheckWorkScheduler()
    val appReviewLauncher = settingsDependencies.appReviewLauncher()
    val scope = rememberCoroutineScope()

    val devModeEnabled by devModePrefs.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = LockPrefs.State(),
    )
    val themeState = LocalThemeState.current
    val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )
    val quickCaptureState by quickCapturePrefs.state.collectAsStateWithLifecycle(
        initialValue = QuickCaptureState(),
    )
    val reminderState by reminderPrefs.state.collectAsStateWithLifecycle(
        initialValue = ReminderPreferencesState(),
    )

    val biometricAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    val deviceCredentialAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    val snackbarHostState = remember { SnackbarHostState() }
    val backupState by backupPrefs.state.collectAsStateWithLifecycle(
        initialValue = BackupPreferencesState(),
    )
    val updateState by updatePrefs.state.collectAsStateWithLifecycle(
        initialValue = UpdatePreferencesState(),
    )
    val viewOptionsState by viewOptionsPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val globalUpdateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val realPlayBannerState by playInAppUpdateProgressController.bannerUiState.collectAsStateWithLifecycle()
    val devReleaseMockPlayBannerState by rememberUpdateState.devReleasePlayBannerMockUiState.collectAsStateWithLifecycle()
    val playBannerState =
        if (devReleaseMockPlayBannerState != PlayInAppUpdateBannerUiState.Hidden) {
            devReleaseMockPlayBannerState
        } else {
            realPlayBannerState
        }
    val configuration = LocalConfiguration.current
    val isLandscape = isLandscape()
    val heightFraction = if (isLandscape) 0.95f else 0.85f
    val maxUpdateSheetHeight = (configuration.screenHeightDp * heightFraction).dp

    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    val showUpdateSheet by updateVm.showUpdateSheet.collectAsStateWithLifecycle()
    val isCheckingUpdate by updateVm.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateCheckFinishedWithoutResult by updateVm.updateCheckFinishedWithoutResult.collectAsStateWithLifecycle()
    val downloadProgress by updateVm.downloadProgress.collectAsStateWithLifecycle()
    val updateInfo by updateVm.updateInfo.collectAsStateWithLifecycle()
    val updateSheetChangelog by updateVm.updateSheetChangelog.collectAsStateWithLifecycle()
    val openSheetRequested by updateVm.openSheetRequested.collectAsStateWithLifecycle()

    val playInAppUpdateLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
            } else {
                Toast
                    .makeText(
                        context,
                        resources.getString(R.string.settings_play_in_app_update_canceled),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    LaunchedEffect(globalUpdateInfo) {
        updateVm.adoptGlobalUpdateIfNone(globalUpdateInfo)
    }

    var pendingBackupFolderTarget by remember { mutableStateOf<BackupFolderTarget?>(null) }
    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val target = pendingBackupFolderTarget
            pendingBackupFolderTarget = null
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                scope.launch {
                    when (target) {
                        BackupFolderTarget.Cloud -> backupPrefs.setCloudExportFolderUri(uri.toString())
                        BackupFolderTarget.Local,
                        null,
                        -> backupPrefs.setExportFolderUri(uri.toString())
                    }
                    RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                }
            }
        }

    val cloudBackupDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                scope.launch {
                    backupPrefs.setCloudExportFolderUri(uri.toString())
                    RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                    val exportedCount = backupIo.exportTo(uri)
                    val message =
                        when {
                            exportedCount < 0 -> resources.getString(R.string.toast_export_failed)
                            else -> resources.getQuantityString(R.plurals.toast_exported_notes, exportedCount, exportedCount)
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importMergeLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val count = backupIo.importFrom(uri, preserveIdsForNotes = false)
                    Toast
                        .makeText(
                            context,
                            resources.getQuantityString(R.plurals.toast_imported_notes, count, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    val importReplaceLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    pendingRestore =
                        PendingRestore(
                            uri = uri,
                            mediaSummary = backupIo.inspectRestoreMedia(uri),
                        )
                }
            }
        }

    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var pendingEnableUpdateNotificationsAfterPermission by rememberSaveable { mutableStateOf(false) }
    val settingsExpandableSectionKeys =
        remember {
            settingsPaneSections
                .filter { sectionKey -> sectionKey != SettingsSectionKey.About }
                .map { sectionKey -> sectionKey.routeKey }
                .toSet()
        }
    var collapsedSettingsSectionKeys by rememberSaveable {
        mutableStateOf<Set<String>?>(null)
    }
    val currentCollapsedSectionKeys =
        collapsedSettingsSectionKeys
            ?: viewOptionsState
                ?.settingsCollapsedSectionKeys
                ?.filter { it in settingsExpandableSectionKeys }
                ?.toSet()
            ?: SettingsScreenSessionState.collapsedSectionKeys
    LaunchedEffect(viewOptionsState?.settingsCollapsedSectionKeys, settingsExpandableSectionKeys) {
        val keys = viewOptionsState?.settingsCollapsedSectionKeys ?: return@LaunchedEffect
        collapsedSettingsSectionKeys =
            keys
                .filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }
                .toSet()
    }

    fun updateCollapsedSettingsSectionKeys(sectionKeys: Set<String>) {
        val filteredSectionKeys = sectionKeys.filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }.toSet()
        collapsedSettingsSectionKeys = filteredSectionKeys
        scope.launch {
            viewOptionsPrefs.setSettingsCollapsedSectionKeys(filteredSectionKeys)
        }
    }
    val selectedSectionRouteKey = selectedSectionKey?.routeKey
    val visibleCollapsedSectionKeys =
        selectedSectionRouteKey?.let { sectionKey -> currentCollapsedSectionKeys - sectionKey }
            ?: currentCollapsedSectionKeys
    val includeSettingsSection: (SettingsSectionKey) -> Boolean =
        remember(selectedSectionKey) {
            { sectionKey -> selectedSectionKey == null || selectedSectionKey == sectionKey }
        }
    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in currentCollapsedSectionKeys
        }
    val settingsListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = SettingsScreenSessionState.listFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = SettingsScreenSessionState.listFirstVisibleItemScrollOffset,
        )
    var notificationsHighlight by rememberSaveable { mutableStateOf(false) }
    var notificationsHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var backupHighlight by rememberSaveable { mutableStateOf(false) }
    var backupHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var securityHighlight by rememberSaveable { mutableStateOf(false) }
    var securityHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (pendingEnableUpdateNotificationsAfterPermission) {
                pendingEnableUpdateNotificationsAfterPermission = false
                if (notificationsGranted && updateState.updateCheckSchedule != UpdateCheckSchedule.NEVER) {
                    scope.launch { updatePrefs.setNotifyOnNewUpdates(true) }
                }
            }
        }
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val permissionLinked = remember { isPermissionLinked() }
    var canScheduleExactAlarms by remember {
        mutableStateOf(alarmManager.canScheduleExactAlarms())
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    androidx.compose.runtime.DisposableEffect(settingsListState) {
        onDispose {
            SettingsScreenSessionState.collapsedSectionKeys = currentCollapsedSectionKeys
            SettingsScreenSessionState.listFirstVisibleItemIndex = settingsListState.firstVisibleItemIndex
            SettingsScreenSessionState.listFirstVisibleItemScrollOffset = settingsListState.firstVisibleItemScrollOffset
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, alarmManager, powerManager) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                    isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = settingsListState,
            additionalScrollEnabled = true,
        )
    val blurStyle = rememberProgressiveBlurStyle(blurTop = false)
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    LaunchedEffect(showUpdateSheet) {
        if (showUpdateSheet) {
            updateVm.loadChangelog()
        }
    }

    val beginUpdateCheck: (Boolean) -> Unit = { redisplayAvailableAlert ->
        if (redisplayAvailableAlert) onUpdateCheckStarted()
        updateVm.openSheetAndCheck()
    }
    val downloadUpdate = { availableUpdate: RememberUpdateInfo ->
        updateVm.downloadOrInstall(availableUpdate, context as? ComponentActivity, playInAppUpdateLauncher)
    }
    LaunchedEffect(openSheetRequested) {
        if (openSheetRequested) {
            updateVm.markOpenSheetHandled()
            updateVm.openSheetAndCheck()
        }
    }
    LaunchedEffect(playBannerState, showUpdateSheet) {
        if (!showUpdateSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        when (playBannerState) {
            is PlayInAppUpdateBannerUiState.Downloading,
            PlayInAppUpdateBannerUiState.ReadyToInstall,
            -> {
                updateVm.closeSheetForPlayProgress()
            }
            PlayInAppUpdateBannerUiState.Hidden -> Unit
        }
    }
    val highlightSection = highlightSectionKey?.substringBefore(".")
    val highlightItem = highlightSectionKey?.substringAfter(".", "")?.takeIf { it.isNotEmpty() }
    var activeHighlightItem by remember { mutableStateOf<String?>(null) }
    var activeHighlightItemRequestId by remember { mutableIntStateOf(0) }
    LaunchedEffect(highlightSectionKey) {
        val key = highlightSection ?: return@LaunchedEffect
        activeHighlightItem = null
        val wasCollapsed = key in currentCollapsedSectionKeys
        updateCollapsedSettingsSectionKeys(currentCollapsedSectionKeys - key)
        // Wait for expandVertically to finish before scrolling or starting item-level highlights.
        if (wasCollapsed) delay(SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS)
        val index =
            settingsSectionScrollIndex[key] ?: run {
                onHighlightHandled()
                return@LaunchedEffect
            }
        settingsListState.animateScrollToItem(index)
        if (highlightItem == null) {
            val highlightExpiresAtMillis = SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
            when (key) {
                "notifications" -> {
                    notificationsHighlight = true
                    notificationsHighlightExpiresAtMillis = highlightExpiresAtMillis
                }
                "backup" -> {
                    backupHighlight = true
                    backupHighlightExpiresAtMillis = highlightExpiresAtMillis
                }
                "security" -> {
                    securityHighlight = true
                    securityHighlightExpiresAtMillis = highlightExpiresAtMillis
                }
            }
            onHighlightHandled()
        } else {
            activeHighlightItem = highlightItem
            activeHighlightItemRequestId += 1
            onHighlightHandled()
        }
    }
    LaunchedEffect(notificationsHighlight, notificationsHighlightExpiresAtMillis) {
        if (!notificationsHighlight) return@LaunchedEffect
        val remainingHighlightMillis = notificationsHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        notificationsHighlight = false
        notificationsHighlightExpiresAtMillis = 0L
    }
    LaunchedEffect(backupHighlight, backupHighlightExpiresAtMillis) {
        if (!backupHighlight) return@LaunchedEffect
        val remainingHighlightMillis = backupHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        backupHighlight = false
        backupHighlightExpiresAtMillis = 0L
    }
    LaunchedEffect(securityHighlight, securityHighlightExpiresAtMillis) {
        if (!securityHighlight) return@LaunchedEffect
        val remainingHighlightMillis = securityHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        securityHighlight = false
        securityHighlightExpiresAtMillis = 0L
    }
    val highlightNowMillis = SystemClock.elapsedRealtime()
    val notificationsHighlightActive = notificationsHighlight && notificationsHighlightExpiresAtMillis > highlightNowMillis
    val backupHighlightActive = backupHighlight && backupHighlightExpiresAtMillis > highlightNowMillis
    val securityHighlightActive = securityHighlight && securityHighlightExpiresAtMillis > highlightNowMillis
    val notificationsHighlightAlpha = rememberSectionHighlightPulseAlpha(notificationsHighlightActive)
    val backupHighlightAlpha = rememberSectionHighlightPulseAlpha(backupHighlightActive)
    val securityHighlightAlpha = rememberSectionHighlightPulseAlpha(securityHighlightActive)

    if (showUpdateSheet) {
        val updateSheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Expanded,
                confirmValueChange = { true },
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        val currentOrientation = LocalConfiguration.current.orientation
        LaunchedEffect(currentOrientation) {
            updateSheetState.expand()
        }
        // Deliberately a raw ModalBottomSheet rather than the shared AppBottomSheet wrapper:
        // 1. AppBottomSheet always wraps its body in its own verticalScroll. The changelog needs
        //    orientation-specific scrolling - a single outer scroll in landscape (so a downward
        //    drag still bubbles up to dismiss the sheet) vs. an inner-scrolled changelog box in
        //    portrait. A nested inner scroll under AppBottomSheet's outer scroll swallows the
        //    drag-to-dismiss delta in landscape, which is exactly the "won't drag down" bug.
        // 2. It needs a height cap (maxUpdateSheetHeight) and to re-expand on rotation below.
        // The shared AppBottomSheetDragHandle is still reused so the handle stays consistent.
        ModalBottomSheet(
            onDismissRequest = { updateVm.dismissSheet() },
            sheetState = updateSheetState,
            dragHandle = { AppBottomSheetDragHandle() },
        ) {
            UpdateCheckBottomSheetContent(
                maxSheetHeight = maxUpdateSheetHeight,
                isCheckingUpdate = isCheckingUpdate,
                updateInfo = updateInfo,
                updateCheckFinishedWithoutResult = updateCheckFinishedWithoutResult,
                downloadProgress = downloadProgress,
                changelogState = updateSheetChangelog,
                showGithubExtraUi = BuildConfig.FLAVOR == "github",
                usePlayInAppUpdates = BuildConfig.USE_PLAY_IN_APP_UPDATES,
                onDownloadClick = downloadUpdate,
                onSkipVersionClick = { updateInfo?.let { availableUpdate -> updateVm.skipVersion(availableUpdate) } },
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
    ) { _ ->
        // Pane mode (selectedSectionKey set) has no floating pill over this list, so the
        // bottom blur band that exists to sit under the pill is dropped.
        val paneHosted = selectedSectionKey != null
        val blurMod =
            remember(blurStyle, paneHosted) {
                blurStyle
                    ?.applyToScrollableList(bottomAlphaMultiplier = if (paneHosted) 0f else 1f)
                    ?: Modifier
            }
        val topInset = statusBarInset + if (showTopActions) 68.dp else 24.dp
        val bottomPadding = pillInset + 24.dp
        val listContentPadding =
            remember(topInset, bottomPadding) {
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topInset,
                    bottom = bottomPadding,
                )
            }
        if (viewOptionsState == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = settingsListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(blurMod),
                    contentPadding = listContentPadding,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    userScrollEnabled = settingsScrollEnabled,
                ) {
                    if (includeSettingsSection(SettingsSectionKey.Appearance)) {
                        item(key = "appearance") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Appearance.routeKey,
                                materialSymbolName = SettingsSectionKey.Appearance.iconName,
                                title = stringResource(SettingsSectionKey.Appearance.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                            ) {
                                AppearanceSection(
                                    prefs = themePrefs,
                                    state = themeState,
                                    snackbarHostState = snackbarHostState,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Notifications)) {
                        item(key = "notifications") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .pulsingSectionHighlightOutline(
                                            active = notificationsHighlightActive,
                                            outlineColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = notificationsHighlightAlpha,
                                                ),
                                        ),
                            ) {
                                SettingsExpandableSection(
                                    sectionKey = SettingsSectionKey.Notifications.routeKey,
                                    materialSymbolName = SettingsSectionKey.Notifications.iconName,
                                    title = stringResource(SettingsSectionKey.Notifications.titleRes),
                                    collapsedSectionKeys = visibleCollapsedSectionKeys,
                                    onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                    showHeader = showSectionHeaders,
                                ) {
                                    RemindersSection(
                                        reminderState = reminderState,
                                        reminderPrefs = reminderPrefs,
                                        quickCaptureState = quickCaptureState,
                                        quickCapturePrefs = quickCapturePrefs,
                                        noteRepository = noteRepository,
                                        notificationsGranted = notificationsGranted,
                                        notificationPermissionLauncher = notificationPermissionLauncher,
                                        permissionLinked = permissionLinked,
                                        canScheduleExactAlarms = canScheduleExactAlarms,
                                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                                        scope = scope,
                                        highlightItemKey = activeHighlightItem,
                                        highlightItemRequestId = activeHighlightItemRequestId,
                                    )
                                }
                            } // notifications Column
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Swipe)) {
                        item(key = "swipe") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Swipe.routeKey,
                                materialSymbolName = SettingsSectionKey.Swipe.iconName,
                                title = stringResource(SettingsSectionKey.Swipe.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                            ) {
                                GroupedListColumn {
                                    GroupedListItem(position = GroupPosition.ONLY) {
                                        SwipeGestureSettingsPanel(
                                            currentMode = interactionState.swipeGestureMode,
                                            onModeChange = { mode ->
                                                scope.launch { interactionPrefs.setSwipeGestureMode(mode) }
                                            },
                                            startAction = interactionState.swipeStartToEnd,
                                            endAction = interactionState.swipeEndToStart,
                                            onStartActionChange = { action ->
                                                scope.launch { interactionPrefs.setSwipeStartToEnd(action) }
                                            },
                                            onEndActionChange = { action ->
                                                scope.launch { interactionPrefs.setSwipeEndToStart(action) }
                                            },
                                            startActions = interactionState.swipeStartToEndRevealActions,
                                            endActions = interactionState.swipeEndToStartRevealActions,
                                            onRevealActionsChange = { startActions, endActions ->
                                                scope.launch {
                                                    interactionPrefs.setSwipeRevealActions(startActions, endActions)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Security)) {
                        item(key = "security") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .pulsingSectionHighlightOutline(
                                            active = securityHighlightActive,
                                            outlineColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = securityHighlightAlpha,
                                                ),
                                        ),
                            ) {
                                SettingsExpandableSection(
                                    sectionKey = SettingsSectionKey.Security.routeKey,
                                    materialSymbolName = SettingsSectionKey.Security.iconName,
                                    title = stringResource(SettingsSectionKey.Security.titleRes),
                                    collapsedSectionKeys = visibleCollapsedSectionKeys,
                                    onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                    showHeader = showSectionHeaders,
                                ) {
                                    LockSection(
                                        lockState = lockState,
                                        lockPrefs = lockPrefs,
                                        biometricAvailable = biometricAvailable,
                                        deviceCredentialAvailable = deviceCredentialAvailable,
                                        snackbarHostState = snackbarHostState,
                                        scope = scope,
                                    )
                                }
                            } // security Column
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Backup)) {
                        item(key = "backup") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .pulsingSectionHighlightOutline(
                                            active = backupHighlightActive,
                                            outlineColor =
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = backupHighlightAlpha,
                                                ),
                                        ),
                            ) {
                                SettingsExpandableSection(
                                    sectionKey = SettingsSectionKey.Backup.routeKey,
                                    materialSymbolName = SettingsSectionKey.Backup.iconName,
                                    title = stringResource(SettingsSectionKey.Backup.titleRes),
                                    collapsedSectionKeys = visibleCollapsedSectionKeys,
                                    onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                    showHeader = showSectionHeaders,
                                ) {
                                    BackupSection(
                                        backupState = backupState,
                                        backupPrefs = backupPrefs,
                                        backupIo = backupIo,
                                        snackbarHostState = snackbarHostState,
                                        scope = scope,
                                        onPickLocalFolder = {
                                            pendingBackupFolderTarget = BackupFolderTarget.Local
                                            folderLauncher.launch(null)
                                        },
                                        onPickCloudFolder = {
                                            cloudBackupDocumentLauncher.launch("remember_cloud_backup.zip")
                                        },
                                        onLaunchImportMerge = {
                                            importMergeLauncher.launch(
                                                arrayOf("application/zip", "application/json"),
                                            )
                                        },
                                        onLaunchImportReplace = {
                                            importReplaceLauncher.launch(
                                                arrayOf("application/zip", "application/json"),
                                            )
                                        },
                                    )
                                }
                            } // backup Column
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Updates)) {
                        item(key = "updates") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Updates.routeKey,
                                materialSymbolName = SettingsSectionKey.Updates.iconName,
                                title = stringResource(SettingsSectionKey.Updates.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                            ) {
                                GroupedListColumn {
                                    GroupedListItem(position = GroupPosition.FIRST) {
                                        UpdateCheckScheduleDropdown(
                                            selected = updateState.updateCheckSchedule,
                                            onSelect = { schedule ->
                                                scope.launch {
                                                    updatePrefs.setUpdateCheckSchedule(schedule)
                                                    updateCheckWorkScheduler.syncFromPreferences()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    if (BuildConfig.FLAVOR == "github") {
                                        GroupedListItem(position = GroupPosition.MIDDLE) {
                                            UpdateSettingsToggleItem(
                                                title = stringResource(R.string.settings_save_update_apk_to_downloads),
                                                checked = updateState.saveUpdateApkToDownloads,
                                                onCheckedChange = { enabled ->
                                                    scope.launch { updatePrefs.setSaveUpdateApkToDownloads(enabled) }
                                                },
                                            )
                                        }
                                    }
                                    GroupedListItem(position = GroupPosition.MIDDLE) {
                                        UpdateSettingsToggleItem(
                                            title = stringResource(R.string.settings_notify_new_updates),
                                            checked = updateState.notifyOnNewUpdates,
                                            onCheckedChange = { enabled ->
                                                when {
                                                    !enabled -> {
                                                        pendingEnableUpdateNotificationsAfterPermission = false
                                                        scope.launch { updatePrefs.setNotifyOnNewUpdates(false) }
                                                    }
                                                    updateState.updateCheckSchedule == UpdateCheckSchedule.NEVER -> {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                resources.getString(R.string.settings_notify_updates_need_auto_check),
                                                            )
                                                        }
                                                    }
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                        ContextCompat.checkSelfPermission(
                                                            context,
                                                            Manifest.permission.POST_NOTIFICATIONS,
                                                        ) != PackageManager.PERMISSION_GRANTED -> {
                                                        pendingEnableUpdateNotificationsAfterPermission = true
                                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    }
                                                    !NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                resources.getString(R.string.settings_notify_updates_enable_notifications),
                                                            )
                                                        }
                                                        context.startActivity(notificationsAppSettingsIntent(context))
                                                    }
                                                    else -> scope.launch { updatePrefs.setNotifyOnNewUpdates(true) }
                                                }
                                            },
                                        )
                                    }
                                    GroupedListItem(position = GroupPosition.LAST) {
                                        val availableUpdate = updateInfo
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .tapSoundClickable {
                                                        beginUpdateCheck(true)
                                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RememberMaterialRoundedSymbol(
                                                name = "new_releases",
                                                tint = MaterialTheme.colorScheme.primary,
                                                weight = FontWeight.Medium,
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text =
                                                        if (availableUpdate != null) {
                                                            stringResource(
                                                                R.string.settings_update_available_button,
                                                                availableUpdate.versionName,
                                                            )
                                                        } else {
                                                            stringResource(R.string.settings_check_for_updates)
                                                        },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                Text(
                                                    text =
                                                        stringResource(
                                                            R.string.settings_update_current_version,
                                                            BuildConfig.VERSION_NAME,
                                                        ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (devModeEnabled && selectedSectionKey == null) {
                        item(key = "dev_options_entry") {
                            DevOptionsSettingsEntry(
                                onClick = onOpenDevOptions,
                            )
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.About)) {
                        item(key = "about") {
                            AboutSection(
                                modifier =
                                    if (devModeEnabled || selectedSectionKey != null) {
                                        Modifier
                                    } else {
                                        Modifier.padding(top = 24.dp)
                                    },
                                onOpenIntro = onOpenIntro,
                                devModeEnabled = devModeEnabled,
                                onDevModeActivated = {
                                    scope.launch { devModePrefs.setEnabled(true) }
                                    onOpenDevOptions()
                                },
                                onLaunchPlayReview = { onFlowFinished ->
                                    val hostActivity = context as? ComponentActivity
                                    if (hostActivity != null) {
                                        appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
                                    } else {
                                        onFlowFinished()
                                    }
                                },
                                showHeader = showAboutHeader,
                                showHeaderTitle = showAboutHeaderTitle,
                            )
                        }
                    }
                }
                if (showTopActions) {
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 8.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val actionButtonSize = rememberResponsiveActionButtonSize()
                        val actionIconSize = rememberResponsiveActionIconSize()
                        val openHelpLabel = stringResource(R.string.settings_open_help_cd)
                        RememberFilledTonalIconButton(
                            onClick = onOpenHelp,
                            modifier =
                                Modifier
                                    .size(actionButtonSize)
                                    .semantics {
                                        contentDescription = openHelpLabel
                                    },
                            tooltipLabel = openHelpLabel,
                        ) {
                            Text(
                                text = "?",
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = actionIconSize.value.sp,
                                        lineHeight = actionIconSize.value.sp,
                                    ),
                            )
                        }
                        val expandCollapseAllLabel =
                            stringResource(
                                if (allSettingsSectionsCollapsed) {
                                    R.string.settings_expand_all_sections_cd
                                } else {
                                    R.string.settings_collapse_all_sections_cd
                                },
                            )
                        RememberFilledTonalIconButton(
                            onClick = {
                                updateCollapsedSettingsSectionKeys(
                                    if (allSettingsSectionsCollapsed) {
                                        currentCollapsedSectionKeys - settingsExpandableSectionKeys
                                    } else {
                                        currentCollapsedSectionKeys + settingsExpandableSectionKeys
                                    },
                                )
                            },
                            modifier =
                                Modifier
                                    .size(actionButtonSize)
                                    .semantics {
                                        contentDescription = expandCollapseAllLabel
                                    },
                            tooltipLabel = expandCollapseAllLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = if (allSettingsSectionsCollapsed) "unfold_more" else "unfold_less",
                                size = actionIconSize,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { restore ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_restore_confirm_body))
                    if (restore.mediaSummary.hasMissingMedia) {
                        Text(
                            text = stringResource(R.string.settings_restore_media_missing_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                RememberTextButton(
                    onClick = {
                        pendingRestore = null
                        scope.launch {
                            val count = backupIo.restoreFullReplace(restore.uri)
                            Toast
                                .makeText(
                                    context,
                                    resources.getQuantityString(R.plurals.toast_imported_notes, count, count),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                ) {
                    // Restore replaces existing data, so keep this low-emphasis + error-colored.
                    Text(
                        text = stringResource(R.string.settings_restore_go_ahead),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { pendingRestore = null }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DevOptionsSettingsEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Rect>())
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val sharedModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = DEV_OPTIONS_SHARED_BOUNDS_KEY),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .then(sharedModifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .tapSoundClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "developer_board",
                size = 21.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = stringResource(R.string.dev_options_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "arrow_outward",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}

private fun notificationsAppSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

private val settingsSectionScrollIndex =
    mapOf(
        "notifications" to 1,
        "security" to 4,
        "backup" to 5,
    )
