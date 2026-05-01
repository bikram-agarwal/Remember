package dev.bikram.remember.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.di.SettingsDependenciesEntryPoint
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.AboutAuthorPhoto
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import dev.bikram.remember.update.RememberUpdateInfo
import dev.bikram.remember.update.RememberUpdateState
import kotlinx.coroutines.Dispatchers
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsRoute(
    onOpenIntro: () -> Unit = {},
    openUpdateSheetRequest: Int = 0,
) {
    val context = LocalContext.current
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
    val rememberUpdateState: RememberUpdateState = settingsDependencies.rememberUpdateState()
    val updateCheckWorkScheduler = settingsDependencies.updateCheckWorkScheduler()
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

    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var showUpdateSheet by rememberSaveable { mutableStateOf(false) }
    var isCheckingUpdate by rememberSaveable { mutableStateOf(false) }
    var updateCheckFinishedWithoutResult by rememberSaveable { mutableStateOf(false) }
    var downloadProgress by rememberSaveable { mutableStateOf<Float?>(null) }
    var updateInfo by remember { mutableStateOf<RememberUpdateInfo?>(null) }
    val updateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playInAppUpdateLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) {
            playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
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
                            exportedCount < 0 -> context.getString(R.string.toast_export_failed)
                            else -> context.getString(R.string.toast_exported_notes, exportedCount)
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
                    Toast.makeText(context, context.getString(R.string.toast_imported_notes, count), Toast.LENGTH_SHORT).show()
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
            ) + if (BuildConfig.BUILD_TYPE == "devRelease") setOf("dev_release_mocks") else emptySet()
        }
    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in collapsedSettingsSectionKeys
        }
    val settingsListState = rememberLazyListState()
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
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        )
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
                    canScheduleExactAlarms =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        alarmManager.canScheduleExactAlarms()
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
    val beginUpdateCheck = {
        showUpdateSheet = true
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
                        if (availableUpdate != null) {
                            playInAppUpdateProgressController.ensureInstallStateListenerRegistered()
                        }
                        updateCheckFinishedWithoutResult = availableUpdate == null
                    },
                    onFailure = {
                        updateInfo = null
                        updateCheckFinishedWithoutResult = true
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.settings_update_check_failed),
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
                        updateCheckFinishedWithoutResult = availableUpdate == null
                    },
                    onFailure = {
                        updateInfo = null
                        updateCheckFinishedWithoutResult = true
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.settings_update_check_failed),
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
                            context.getString(R.string.settings_update_check_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return@launch
            }
            downloadProgress = 0f
            val downloadResult =
                withContext(Dispatchers.IO) {
                    runCatching {
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
                                displayName = "Remember-${availableUpdate.versionName}.apk",
                            )
                        }.onFailure {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.settings_update_apk_save_to_downloads_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                        .onFailure {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.settings_update_download_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    downloadProgress = null
                },
                onFailure = {
                    downloadProgress = null
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.settings_update_download_failed),
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

    if (showUpdateSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showUpdateSheet = false
                downloadProgress = null
            },
            sheetState = updateSheetState,
        ) {
            UpdateCheckBottomSheetContent(
                isCheckingUpdate = isCheckingUpdate,
                updateInfo = updateInfo,
                updateCheckFinishedWithoutResult = updateCheckFinishedWithoutResult,
                downloadProgress = downloadProgress,
                onCheckAgain = beginUpdateCheck,
                onDownloadClick = downloadUpdate,
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
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
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
                    AppearanceSection(prefs = themePrefs, state = themeState)
                }
            }

            item(key = "notifications") {
                SettingsExpandableSection(
                    sectionKey = "notifications",
                    materialSymbolName = "notifications",
                    title = stringResource(R.string.settings_notifications_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .tapSoundClickable {
                                            if (!notificationsGranted) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                                    context.startActivity(notificationsAppSettingsIntent(context))
                                                }
                                            } else {
                                                context.startActivity(notificationsAppSettingsIntent(context))
                                            }
                                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "notifications",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.settings_notifications),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(R.string.settings_notifications_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                RememberSwitch(
                                    checked = notificationsGranted,
                                    onCheckedChange = { wantEnabled ->
                                        when {
                                            wantEnabled &&
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) != PackageManager.PERMISSION_GRANTED ->
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            wantEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                                context.startActivity(notificationsAppSettingsIntent(context))
                                            !wantEnabled ->
                                                context.startActivity(notificationsAppSettingsIntent(context))
                                            else -> { }
                                        }
                                    },
                                    thumbContent =
                                        if (notificationsGranted) {
                                            {
                                                RememberMaterialRoundedSymbol(
                                                    name = "check",
                                                    size = SwitchDefaults.IconSize,
                                                    weight = FontWeight.Bold,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                        if (permissionLinked) {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "timer",
                                    title = stringResource(R.string.settings_reliable_reminders),
                                    subtitle = stringResource(R.string.settings_reliable_reminders_desc),
                                    checked = canScheduleExactAlarms && isIgnoringBatteryOptimizations,
                                    onCheckedChange = { wantEnabled ->
                                        if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                            val intent =
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        } else {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "timer",
                                    title = stringResource(R.string.settings_reliable_reminders),
                                    subtitle = stringResource(R.string.settings_reliable_reminders_exact_desc),
                                    checked = canScheduleExactAlarms,
                                    onCheckedChange = {
                                        val intent =
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        context.startActivity(intent)
                                    },
                                )
                            }
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "battery_full",
                                    title = stringResource(R.string.settings_run_in_background),
                                    subtitle = stringResource(R.string.settings_run_in_background_desc),
                                    checked = isIgnoringBatteryOptimizations,
                                    onCheckedChange = { wantEnabled ->
                                        if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                            val intent =
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleRow(
                                materialSymbolName = "notification_important",
                                title = stringResource(R.string.settings_keep_reminders_until_done),
                                subtitle = stringResource(R.string.settings_keep_reminders_until_done_desc),
                                checked = reminderState.keepReminderNotificationsUntilDone,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        reminderPrefs.setKeepReminderNotificationsUntilDone(enabled)
                                        noteRepository.refreshActiveReminderNotifications()
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleRow(
                                materialSymbolName = "format_list_bulleted",
                                title = stringResource(R.string.settings_reminder_summary_notification),
                                subtitle = stringResource(R.string.settings_reminder_summary_notification_desc),
                                checked = reminderState.reminderSummaryNotificationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        reminderPrefs.setReminderSummaryNotificationEnabled(enabled)
                                        noteRepository.refreshReminderSummaryNotification()
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            SettingsToggleRow(
                                materialSymbolName = "bolt",
                                title = stringResource(R.string.settings_quick_capture_title),
                                subtitle = stringResource(R.string.settings_quick_capture_subtitle),
                                checked = quickCaptureState.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { quickCapturePrefs.setEnabled(enabled) }
                                },
                            )
                        }
                    }
                }
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
                        GroupedListItem(position = GroupPosition.FIRST) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_swipe_gesture),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                SwipeGestureModeDropdown(
                                    current = interactionState.swipeGestureMode,
                                    onSelect = { mode ->
                                        scope.launch { interactionPrefs.setSwipeGestureMode(mode) }
                                    },
                                )
                            }
                        }
                        if (interactionState.swipeGestureMode == SwipeGestureMode.EXECUTE_ONE) {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_swipe_right),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    NoteSwipeActionDropdown(
                                        current = interactionState.swipeStartToEnd,
                                        excluded = interactionState.swipeEndToStart,
                                        onSelect = { action ->
                                            scope.launch { interactionPrefs.setSwipeStartToEnd(action) }
                                        },
                                    )
                                }
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_swipe_left),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    NoteSwipeActionDropdown(
                                        current = interactionState.swipeEndToStart,
                                        excluded = interactionState.swipeStartToEnd,
                                        onSelect = { action ->
                                            scope.launch { interactionPrefs.setSwipeEndToStart(action) }
                                        },
                                    )
                                }
                            }
                        } else {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SwipeRevealSlotsRow(
                                    title = stringResource(R.string.settings_swipe_right_actions),
                                    actions = interactionState.swipeStartToEndRevealActions,
                                    onActionsChange = { actions ->
                                        scope.launch { interactionPrefs.setSwipeStartToEndRevealActions(actions) }
                                    },
                                )
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                SwipeRevealSlotsRow(
                                    title = stringResource(R.string.settings_swipe_left_actions),
                                    actions = interactionState.swipeEndToStartRevealActions,
                                    onActionsChange = { actions ->
                                        scope.launch { interactionPrefs.setSwipeEndToStartRevealActions(actions) }
                                    },
                                )
                            }
                        }
                    }
                    if (interactionState.swipeGestureMode == SwipeGestureMode.EXECUTE_ONE) {
                        Spacer(Modifier.height(8.dp))
                        NoteSwipePreviewCard(
                            swipeStartToEnd = interactionState.swipeStartToEnd,
                            swipeEndToStart = interactionState.swipeEndToStart,
                        )
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
                SettingsExpandableSection(
                    sectionKey = "security",
                    materialSymbolName = "security",
                    title = stringResource(R.string.settings_section_security),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ToggleRow(
                                materialSymbolName = "lock",
                                title = stringResource(R.string.settings_app_lock_title),
                                subtitle =
                                    when {
                                        !deviceCredentialAvailable -> stringResource(R.string.settings_app_lock_no_device_lock)
                                        lockState.enabled -> stringResource(R.string.settings_app_lock_enabled)
                                        else -> stringResource(R.string.settings_app_lock_disabled)
                                    },
                                checked = lockState.enabled,
                                enabled = deviceCredentialAvailable || lockState.enabled,
                                onChange = { want ->
                                    if (want) {
                                        scope.launch {
                                            if (deviceCredentialAvailable) {
                                                lockPrefs.enableDeviceCredential()
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.settings_app_lock_no_device_lock),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    } else {
                                        scope.launch { lockPrefs.disable() }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            ToggleRow(
                                materialSymbolName = "fingerprint",
                                title = stringResource(R.string.settings_biometric_title),
                                subtitle =
                                    when {
                                        !biometricAvailable -> stringResource(R.string.settings_biometric_no_hardware)
                                        !lockState.enabled -> stringResource(R.string.settings_biometric_need_lock)
                                        lockState.biometric -> stringResource(R.string.settings_biometric_enabled)
                                        else -> stringResource(R.string.settings_biometric_disabled)
                                    },
                                checked = lockState.biometric,
                                enabled = biometricAvailable && lockState.enabled,
                                onChange = { scope.launch { lockPrefs.setBiometric(it) } },
                            )
                        }
                    }
                }
            }

            item(key = "backup") {
                val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
                val chooseLocalFolderLabel = stringResource(R.string.settings_choose_local_backup_folder)
                val chooseCloudFolderLabel = stringResource(R.string.settings_choose_cloud_backup_file)
                val resolvedLocalFolderLabel by produceState(
                    initialValue =
                        backupState.exportFolderUri
                            .takeIf { it.isNotBlank() }
                            ?.let { internalStorageDisplayName } ?: chooseLocalFolderLabel,
                    backupState.exportFolderUri,
                    internalStorageDisplayName,
                    chooseLocalFolderLabel,
                ) {
                    val uriString = backupState.exportFolderUri
                    value =
                        if (uriString.isBlank()) {
                            chooseLocalFolderLabel
                        } else {
                            withContext(Dispatchers.IO) {
                                exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                            }
                        }
                }
                val resolvedCloudFolderLabel by produceState(
                    initialValue =
                        backupState.cloudExportFolderUri
                            .takeIf { it.isNotBlank() }
                            ?.let { internalStorageDisplayName } ?: chooseCloudFolderLabel,
                    backupState.cloudExportFolderUri,
                    internalStorageDisplayName,
                    chooseCloudFolderLabel,
                ) {
                    val uriString = backupState.cloudExportFolderUri
                    value =
                        if (uriString.isBlank()) {
                            chooseCloudFolderLabel
                        } else {
                            withContext(Dispatchers.IO) {
                                exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                            }
                        }
                }
                val localFolderLabel = resolvedLocalFolderLabel
                val cloudFolderLabel = resolvedCloudFolderLabel
                val exportFolderReady =
                    backupState.exportFolderUri.isNotBlank() ||
                        backupState.cloudExportFolderUri.isNotBlank()
                val includeMediaSwitchEnabled = exportFolderReady || backupState.includeMediaInBackup
                val autoExportSwitchEnabled = exportFolderReady || backupState.autoExportOnChange
                val scheduledExportSwitchEnabled = exportFolderReady || backupState.scheduledExportEnabled

                SettingsExpandableSection(
                    sectionKey = "backup",
                    materialSymbolName = "save",
                    title = stringResource(R.string.settings_backup_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            BackupFolderPickerItem(
                                title = localFolderLabel,
                                subtitle = stringResource(R.string.settings_local_backup_folder_hint),
                                accessibilityLabel = stringResource(R.string.settings_choose_local_backup_folder),
                                onClick = {
                                    pendingBackupFolderTarget = BackupFolderTarget.Local
                                    folderLauncher.launch(null)
                                },
                                onLongClick = {
                                    if (backupState.exportFolderUri.isNotBlank()) {
                                        scope.launch {
                                            backupPrefs.setExportFolderUri("")
                                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.settings_local_backup_folder_cleared),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderPickerItem(
                                title = cloudFolderLabel,
                                subtitle = stringResource(R.string.settings_cloud_backup_folder_hint),
                                accessibilityLabel = stringResource(R.string.settings_choose_cloud_backup_file),
                                onClick = {
                                    cloudBackupDocumentLauncher.launch("remember_cloud_backup.zip")
                                },
                                onLongClick = {
                                    if (backupState.cloudExportFolderUri.isNotBlank()) {
                                        scope.launch {
                                            backupPrefs.setCloudExportFolderUri("")
                                            RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.settings_cloud_backup_file_cleared),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_include_media_in_backup),
                                subtitle = stringResource(R.string.settings_include_media_in_backup_hint),
                                infoTooltipText = stringResource(R.string.settings_include_media_in_backup_tooltip),
                                infoContentDescription = stringResource(R.string.settings_include_media_info_cd),
                                checked = backupState.includeMediaInBackup,
                                switchEnabled = includeMediaSwitchEnabled,
                                onDisabledInteraction = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        backupPrefs.setIncludeMediaInBackup(enabled)
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_auto_export_on_change),
                                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                                checked = backupState.autoExportOnChange,
                                switchEnabled = autoExportSwitchEnabled,
                                onDisabledInteraction = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        backupPrefs.setAutoExportOnChange(enabled)
                                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_scheduled_export),
                                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                                checked = backupState.scheduledExportEnabled,
                                switchEnabled = scheduledExportSwitchEnabled,
                                onDisabledInteraction = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        backupPrefs.setScheduledExportEnabled(enabled)
                                        RememberBackupWork.updateSchedule(context, backupPrefs.snapshot())
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            val backupHelpCd = stringResource(R.string.settings_backup_help_icon_cd)
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RememberOutlinedButton(
                                        onClick = {
                                            importMergeLauncher.launch(
                                                arrayOf("application/zip", "application/json"),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_import_rules))
                                    }
                                    RememberOutlinedButton(
                                        onClick = {
                                            if (exportFolderReady) {
                                                scope.launch {
                                                    val backupDestinations =
                                                        listOf(
                                                            backupState.exportFolderUri,
                                                            backupState.cloudExportFolderUri,
                                                        ).filter { it.isNotBlank() }
                                                    val exportOutcome = backupIo.exportToTreeFolders(backupDestinations)
                                                    val message =
                                                        exportOutcome.fold(
                                                            onSuccess = { fileNames ->
                                                                context.resources.getQuantityString(
                                                                    R.plurals.toast_exported_to_destinations,
                                                                    fileNames.size,
                                                                    fileNames.size,
                                                                )
                                                            },
                                                            onFailure = {
                                                                context.getString(R.string.toast_export_failed)
                                                            },
                                                        )
                                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.settings_export_select_folder_first),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_export_now))
                                    }
                                }
                                val restoreShape = ButtonDefaults.outlinedShape
                                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                val restoreLabelColor = MaterialTheme.colorScheme.error
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(restoreShape)
                                            .border(BorderStroke(1.dp, restoreOutline), restoreShape),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight()
                                                .tapSoundClickable {
                                                    importReplaceLauncher.launch(
                                                        arrayOf("application/zip", "application/json"),
                                                    )
                                                },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_restore_backup),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = restoreLabelColor,
                                        )
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SettingsInfoDropdown(
                                            title = stringResource(R.string.settings_backup_help_title),
                                            tipText = stringResource(R.string.settings_backup_help_body),
                                            contentDescription = backupHelpCd,
                                            iconTint = restoreLabelColor.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                                                    context.getString(R.string.settings_notify_updates_need_auto_check),
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
                                                    context.getString(R.string.settings_notify_updates_enable_notifications),
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

            if (BuildConfig.BUILD_TYPE == "devRelease") {
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
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SettingsStaticSectionHeader(
                        materialSymbolName = "info",
                        title = stringResource(R.string.settings_section_about),
                    )
                    Spacer(Modifier.height(8.dp))
                    AboutSettingsBlock(onOpenIntro = onOpenIntro)
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
                                    context.getString(R.string.toast_imported_notes, count),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutSettingsBlock(
    onOpenIntro: () -> Unit,
) {
    val context = LocalContext.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val profileUrl = stringResource(R.string.about_author_github_profile_url)
    val diagnosticsChooserTitle = stringResource(R.string.settings_share_diagnostics_chooser)
    val copyAboutLink =
        remember(context) {
            { url: String ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(context.getString(R.string.clipboard_link_label), url),
                )
                Toast.makeText(context, context.getString(R.string.toast_about_link_copied), Toast.LENGTH_SHORT).show()
            }
        }
    val iconShape = MaterialTheme.shapes.extraLarge
    val authorShape = MaterialTheme.shapes.large
    val aboutPillShape = MaterialTheme.shapes.extraExtraLarge

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.ONLY) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.app_version_format,
                            stringResource(R.string.app_name),
                            BuildConfig.VERSION_NAME,
                        ),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(
                        modifier =
                            Modifier
                                .size(84.dp)
                                .clip(iconShape)
                                .tapSoundClickable(onClick = onOpenIntro),
                    )
                    Spacer(Modifier.width(20.dp))
                    AboutAuthorPhoto(
                        modifier =
                            Modifier
                                .size(84.dp)
                                .clip(authorShape)
                                .tapSoundCombinedClickable(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                                        }
                                    },
                                    onLongClick = { copyAboutLink(profileUrl) },
                                ),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.settings_byline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (BuildConfig.FLAVOR == "github") {
                        Surface(
                            shape = aboutPillShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier =
                                Modifier
                                    .clip(aboutPillShape)
                                    .tapSoundCombinedClickable(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(playStoreListingUrl)),
                                                )
                                            }
                                        },
                                        onLongClick = { copyAboutLink(playStoreListingUrl) },
                                    ),
                        ) {
                            Row(
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            val repoUrl = "https://github.com/$githubRepoForSourceLink"
                            Surface(
                                shape = aboutPillShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .tapSoundCombinedClickable(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                                }
                                            },
                                            onLongClick = { copyAboutLink(repoUrl) },
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = aboutPillShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier
                                    .clip(aboutPillShape)
                                    .tapSoundCombinedClickable(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(playStoreListingUrl)),
                                                )
                                            }
                                        },
                                        onLongClick = { copyAboutLink(playStoreListingUrl) },
                                    ),
                        ) {
                            Row(
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            val repoUrl = "https://github.com/$githubRepoForSourceLink"
                            Surface(
                                shape = aboutPillShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .tapSoundCombinedClickable(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                                }
                                            },
                                            onLongClick = { copyAboutLink(repoUrl) },
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                RememberOutlinedButton(
                    onClick = {
                        DiagnosticLog.record(context, "Diagnostic log shared from Settings")
                        val diagnosticsFile = DiagnosticLog.createShareFile(context)
                        val diagnosticsUri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                diagnosticsFile,
                            )
                        val shareIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, diagnosticsUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(shareIntent, diagnosticsChooserTitle))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "bug_report",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.primary,
                            weight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_share_diagnostics))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_share_diagnostics_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
