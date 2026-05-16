package dev.bikram.remember.ui.settings

import android.Manifest
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
import androidx.biometric.BiometricManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import dev.bikram.remember.di.SettingsDependenciesEntryPoint
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.ui.common.AppBottomSheetDragHandle
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import dev.bikram.remember.update.RememberUpdateInfo
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.notificationDedupeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private enum class BackupFolderTarget {
    Local,
    Cloud,
}

private data class PendingRestore(
    val uri: Uri,
    val mediaSummary: BackupIo.RestoreMediaSummary,
)

private const val SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS = 900L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsRoute(
    onOpenIntro: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    openUpdateSheetRequest: Int = 0,
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val lockPrefs = settingsDependencies.lockPrefs()
    val interactionPrefs = settingsDependencies.interactionPrefs()
    val quickCapturePrefs = settingsDependencies.quickCapturePrefs()
    val reminderPrefs = settingsDependencies.reminderPrefs()
    val backupPrefs = settingsDependencies.backupPrefs()
    val backupIo = settingsDependencies.backupIo()
    val themePrefs = settingsDependencies.themePrefs()
    val noteRepository = settingsDependencies.noteRepository()
    val updatePrefs = settingsDependencies.updatePrefs()
    val rememberUpdateChecker = settingsDependencies.rememberUpdateChecker()
    val playStoreUpdateChecker = settingsDependencies.playStoreUpdateChecker()
    val playInAppUpdateStarter = settingsDependencies.playInAppUpdateStarter()
    val playInAppUpdateProgressController = settingsDependencies.playInAppUpdateProgressController()
    val playUpdateSessionHandle = settingsDependencies.playUpdateSessionHandle()
    val rememberUpdateState: RememberUpdateState = settingsDependencies.rememberUpdateState()
    val updateAvailableNotifier = settingsDependencies.updateAvailableNotifier()
    val updateCheckWorkScheduler = settingsDependencies.updateCheckWorkScheduler()
    val appReviewLauncher = settingsDependencies.appReviewLauncher()
    val scope = rememberCoroutineScope()

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
    val globalUpdateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val maxUpdateSheetHeight = (configuration.screenHeightDp * 0.85f).dp

    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var showUpdateSheet by rememberSaveable { mutableStateOf(false) }
    var isCheckingUpdate by rememberSaveable { mutableStateOf(false) }
    var updateCheckFinishedWithoutResult by rememberSaveable { mutableStateOf(false) }
    var downloadProgress by rememberSaveable { mutableStateOf<Float?>(null) }
    var updateInfo by remember { mutableStateOf<RememberUpdateInfo?>(null) }
    var updateSheetChangelog by remember { mutableStateOf<ChangelogUiState>(ChangelogUiState.Hidden) }
    val updateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
        if (globalUpdateInfo != null && updateInfo == null) {
            updateInfo = globalUpdateInfo
        }
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
    var collapsedSettingsSectionKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val settingsExpandableSectionKeys =
        remember {
            setOf(
                "appearance",
                "notifications",
                "swipe",
                "haptics",
                "security",
                "backup",
                "updates",
            ) + if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "devRelease") setOf("dev_release_mocks") else emptySet()
        }
    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in collapsedSettingsSectionKeys
        }
    val settingsListState = rememberLazyListState()
    var notificationsHighlight by rememberSaveable { mutableStateOf(false) }
    var notificationsHighlightExpiresAtMillis by rememberSaveable { mutableStateOf(0L) }
    var backupHighlight by rememberSaveable { mutableStateOf(false) }
    var backupHighlightExpiresAtMillis by rememberSaveable { mutableStateOf(0L) }
    var securityHighlight by rememberSaveable { mutableStateOf(false) }
    var securityHighlightExpiresAtMillis by rememberSaveable { mutableStateOf(0L) }
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

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val settingsScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = settingsListState,
            additionalScrollEnabled = topBarState.collapsedFraction > 0f,
        )
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    val fetchRawChangelog = {
        val repo = BuildConfig.CHANGELOG_GITHUB_REPO
        val branch = BuildConfig.CHANGELOG_GITHUB_BRANCH
        val connection =
            URL("https://raw.githubusercontent.com/$repo/$branch/docs/CHANGELOG.md").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        try {
            connection.connect()
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }
    val loadUpdateSheetChangelog = {
        if (BuildConfig.CHANGELOG_GITHUB_REPO.isBlank()) {
            updateSheetChangelog =
                ChangelogUiState.Failed(
                    resources.getString(R.string.settings_changelog_load_failed),
                )
        } else {
            updateSheetChangelog = ChangelogUiState.Loading
            scope.launch {
                val loaded =
                    withContext(Dispatchers.IO) {
                        runCatching { fetchRawChangelog() }
                    }
                updateSheetChangelog =
                    loaded.fold(
                        onSuccess = { markdown -> ChangelogUiState.Ready(markdown) },
                        onFailure = {
                            ChangelogUiState.Failed(
                                resources.getString(R.string.settings_changelog_load_failed),
                            )
                        },
                    )
            }
        }
        Unit
    }
    val beginUpdateCheck = {
        showUpdateSheet = true
        loadUpdateSheetChangelog()
        isCheckingUpdate = true
        updateCheckFinishedWithoutResult = false
        downloadProgress = null
        if (BuildConfig.USE_PLAY_IN_APP_UPDATES) {
            scope.launch {
                val checkedUpdate =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            playStoreUpdateChecker.checkForUpdate()
                        }
                    }
                isCheckingUpdate = false
                checkedUpdate.fold(
                    onSuccess = { availableUpdate ->
                        updateInfo = availableUpdate
                        rememberUpdateState.showUpdate(availableUpdate)
                        if (availableUpdate != null && availableUpdate.isPlayStoreUpdateInProgress) {
                            playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
                            updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                        } else if (availableUpdate != null) {
                            updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                        }
                        updateCheckFinishedWithoutResult = availableUpdate == null
                    },
                    onFailure = { throwable ->
                        DiagnosticLog.record(context, "Play Store update check failed from Settings", throwable)
                        updateInfo = null
                        updateCheckFinishedWithoutResult = true
                        Toast
                            .makeText(
                                context,
                                resources.getString(R.string.settings_update_check_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                )
            }
        } else {
            scope.launch {
                val checkedUpdate =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            rememberUpdateChecker.checkGithubReleaseForUpdate(
                                repositoryName = BuildConfig.GITHUB_REPO,
                                currentVersionName = BuildConfig.VERSION_NAME,
                            )
                        }
                    }
                isCheckingUpdate = false
                checkedUpdate.fold(
                    onSuccess = { availableUpdate ->
                        updateInfo = availableUpdate
                        rememberUpdateState.showUpdate(availableUpdate)
                        if (availableUpdate != null) {
                            updateAvailableNotifier.notifyIfNewUpdateAvailable(availableUpdate, updatePrefs.snapshot())
                        }
                        updateCheckFinishedWithoutResult = availableUpdate == null
                    },
                    onFailure = { throwable ->
                        DiagnosticLog.record(context, "GitHub update check failed from Settings", throwable)
                        updateInfo = null
                        updateCheckFinishedWithoutResult = true
                        Toast
                            .makeText(
                                context,
                                resources.getString(R.string.settings_update_check_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                )
            }
        }
        Unit
    }
    val downloadUpdate = { availableUpdate: RememberUpdateInfo ->
        scope.launch {
            if (BuildConfig.USE_PLAY_IN_APP_UPDATES && availableUpdate.downloadUrl.isBlank()) {
                val hostActivity = context as? ComponentActivity
                val started =
                    hostActivity != null &&
                        playInAppUpdateStarter.startUpdateIfPending(hostActivity, playInAppUpdateLauncher)
                if (started) {
                    showUpdateSheet = false
                    playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
                } else {
                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.settings_play_in_app_update_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return@launch
            }
            downloadProgress = 0f
            val downloadResult =
                withContext(Dispatchers.IO) {
                    runCatching {
                        updatePrefs.clearUpdateApkDownloadsCopySucceeded()
                        downloadUpdateApk(
                            context = context,
                            updateInfo = availableUpdate,
                            onProgress = { progress ->
                                withContext(Dispatchers.Main) {
                                    downloadProgress = progress
                                }
                            },
                        )
                    }
                }
            downloadResult.fold(
                onSuccess = { apkFile ->
                    if (BuildConfig.FLAVOR == "github" && updateState.saveUpdateApkToDownloads) {
                        withContext(Dispatchers.IO) {
                            copyUpdateApkToMediaStoreDownloads(
                                context = context,
                                cacheApkFile = apkFile,
                                displayName =
                                    availableUpdate.remoteApkFileName.ifBlank {
                                        "Remember-${availableUpdate.versionName}.apk"
                                    },
                            )
                        }.onFailure { throwable ->
                            DiagnosticLog.record(context, "Saving update APK to Downloads failed", throwable)
                            Toast
                                .makeText(
                                    context,
                                    resources.getString(R.string.settings_update_apk_save_to_downloads_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }.onSuccess {
                            updatePrefs.markUpdateApkDownloadsCopySucceeded()
                        }
                    }
                    downloadProgress = -1f
                    val apkUri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile,
                        )
                    val installIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                    runCatching { context.startActivity(installIntent) }
                        .onFailure { throwable ->
                            DiagnosticLog.record(context, "Launching update APK installer failed", throwable)
                            Toast
                                .makeText(
                                    context,
                                    resources.getString(R.string.settings_update_download_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    if (BuildConfig.FLAVOR == "github" && availableUpdate.remoteApkAssetUpdatedAt.isNotBlank()) {
                        updatePrefs.writeGithubReleaseAck(
                            fingerprint = availableUpdate.notificationDedupeKey(),
                            installedVersionName = BuildConfig.VERSION_NAME,
                        )
                    }
                    downloadProgress = null
                },
                onFailure = { throwable ->
                    DiagnosticLog.record(context, "Update APK download failed", throwable)
                    downloadProgress = null
                    Toast
                        .makeText(
                            context,
                            resources.getString(R.string.settings_update_download_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                },
            )
        }
        Unit
    }
    LaunchedEffect(openUpdateSheetRequest) {
        if (openUpdateSheetRequest > 0) {
            beginUpdateCheck()
        }
    }
    val highlightSection = highlightSectionKey?.substringBefore(".")
    val highlightItem = highlightSectionKey?.substringAfter(".", "")?.takeIf { it.isNotEmpty() }
    var activeHighlightItem by remember { mutableStateOf<String?>(null) }
    var activeHighlightItemRequestId by remember { mutableStateOf(0) }
    LaunchedEffect(highlightSectionKey) {
        val key = highlightSection ?: return@LaunchedEffect
        activeHighlightItem = null
        val wasCollapsed = key in collapsedSettingsSectionKeys
        collapsedSettingsSectionKeys = collapsedSettingsSectionKeys - key
        // Wait for expandVertically to finish before scrolling or starting item-level highlights.
        if (wasCollapsed) delay(SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS)
        val index =
            settingsSectionScrollIndex[key] ?: run {
                onHighlightHandled()
                return@LaunchedEffect
            }
        topBarState.heightOffset = topBarState.heightOffsetLimit
        settingsListState.animateScrollToItem(index)
        topBarState.heightOffset = topBarState.heightOffsetLimit
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
        ModalBottomSheet(
            onDismissRequest = {
                showUpdateSheet = false
                downloadProgress = null
                updateSheetChangelog = ChangelogUiState.Hidden
                val playBannerState = playInAppUpdateProgressController.bannerUiState.value
                val blocksPendingPlayClear =
                    playBannerState is PlayInAppUpdateBannerUiState.Downloading ||
                        playBannerState is PlayInAppUpdateBannerUiState.ReadyToInstall
                if (!blocksPendingPlayClear) {
                    playUpdateSessionHandle.clearPendingPlayUpdate()
                }
            },
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
                onCheckAgain = beginUpdateCheck,
                onDownloadClick = downloadUpdate,
                onSkipVersionClick = skipVersion@{
                    val availableUpdate = updateInfo ?: return@skipVersion
                    if (availableUpdate.remoteApkAssetUpdatedAt.isBlank()) return@skipVersion
                    scope.launch {
                        updatePrefs.writeGithubReleaseAck(
                            fingerprint = availableUpdate.notificationDedupeKey(),
                            installedVersionName = BuildConfig.VERSION_NAME,
                        )
                        updateInfo = null
                        rememberUpdateState.showUpdate(null)
                        showUpdateSheet = false
                        updateSheetChangelog = ChangelogUiState.Hidden
                    }
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                LargeTopAppBar(
                    colors = transparentLargeTopAppBarColors(),
                    title = {
                        Text(
                            stringResource(R.string.settings_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        val openHelpLabel = stringResource(R.string.settings_open_help_cd)
                        RememberFilledTonalIconButton(
                            onClick = onOpenHelp,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = openHelpLabel
                                },
                            tooltipLabel = openHelpLabel,
                        ) {
                            Text(
                                text = "?",
                                style =
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = MaterialTheme.typography.headlineSmall.fontSize,
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
                                collapsedSettingsSectionKeys =
                                    if (allSettingsSectionsCollapsed) {
                                        collapsedSettingsSectionKeys - settingsExpandableSectionKeys
                                    } else {
                                        collapsedSettingsSectionKeys + settingsExpandableSectionKeys
                                    }
                            },
                            modifier =
                                Modifier.semantics {
                                    contentDescription = expandCollapseAllLabel
                                },
                            tooltipLabel = expandCollapseAllLabel,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = if (allSettingsSectionsCollapsed) "unfold_more" else "unfold_less",
                                size = 22.dp,
                                weight = FontWeight.Medium,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }
        val topInset = padding.calculateTopPadding() + 8.dp
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
            item(key = "appearance") {
                SettingsExpandableSection(
                    sectionKey = "appearance",
                    materialSymbolName = "palette",
                    title = stringResource(R.string.settings_section_appearance),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    AppearanceSection(
                        prefs = themePrefs,
                        state = themeState,
                        snackbarHostState = snackbarHostState,
                    )
                }
            }

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
                        sectionKey = "notifications",
                        materialSymbolName = "notifications",
                        title = stringResource(R.string.settings_notifications_section),
                        collapsedSectionKeys = collapsedSettingsSectionKeys,
                        onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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

            item(key = "swipe") {
                SettingsExpandableSection(
                    sectionKey = "swipe",
                    materialSymbolName = "swipe_left",
                    title = stringResource(R.string.settings_swipe_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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

            item(key = "haptics") {
                SettingsExpandableSection(
                    sectionKey = "haptics",
                    materialSymbolName = "vibration",
                    title = stringResource(R.string.settings_haptics_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.ONLY) {
                            SettingsToggleRow(
                                materialSymbolName = "vibration",
                                title = stringResource(R.string.settings_haptic_feedback),
                                subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                checked = interactionState.hapticFeedbackEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { interactionPrefs.setHapticFeedbackEnabled(enabled) }
                                },
                            )
                        }
                    }
                }
            }

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
                        sectionKey = "security",
                        materialSymbolName = "security",
                        title = stringResource(R.string.settings_section_security),
                        collapsedSectionKeys = collapsedSettingsSectionKeys,
                        onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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
                        sectionKey = "backup",
                        materialSymbolName = "save",
                        title = stringResource(R.string.settings_backup_section),
                        collapsedSectionKeys = collapsedSettingsSectionKeys,
                        onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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

            item(key = "updates") {
                SettingsExpandableSection(
                    sectionKey = "updates",
                    materialSymbolName = "system_update",
                    title = stringResource(R.string.settings_updates_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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
                                            beginUpdateCheck()
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

            if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "devRelease") {
                item(key = "dev_release_mocks") {
                    Column(modifier = Modifier.padding(top = 24.dp)) {
                        SettingsExpandableSection(
                            sectionKey = "dev_release_mocks",
                            materialSymbolName = "bug_report",
                            title = stringResource(R.string.settings_dev_release_mocks_section),
                            collapsedSectionKeys = collapsedSettingsSectionKeys,
                            onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RememberOutlinedButton(
                                    onClick = {
                                        rememberUpdateState.devReleaseMockArmUpdatePromoBanner()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.settings_dev_release_mock_update_banner))
                                }
                                RememberOutlinedButton(
                                    onClick = {
                                        rememberUpdateState.devReleaseMockStartPlayUpdateBannerSequence()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.settings_dev_release_mock_play_banner))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "about") {
                AboutSection(
                    onOpenIntro = onOpenIntro,
                    onLaunchPlayReview = { onFlowFinished ->
                        val hostActivity = context as? ComponentActivity
                        if (hostActivity != null) {
                            appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
                        } else {
                            onFlowFinished()
                        }
                    },
                )
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
                    Text(stringResource(R.string.settings_restore_go_ahead))
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
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
