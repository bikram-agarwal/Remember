package dev.bikram.remember

import android.app.Activity
import android.content.ClipDescription
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NotesUiPrefs
import dev.bikram.remember.data.NotesUiState
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.OnboardingState
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.data.UpdatePreferencesState
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.data.effectiveDarkTheme
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.googletasks.ManualTaskImportParser
import dev.bikram.remember.ui.InAppRatingAutoPromptHost
import dev.bikram.remember.ui.common.LocalSystemPaneScaffoldDirective
import dev.bikram.remember.ui.common.LocalSystemWindowAdaptiveInfo
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.rememberNotificationsAllowed
import dev.bikram.remember.ui.components.RememberBackupZipActionDialog
import dev.bikram.remember.ui.components.UpdateChromeState
import dev.bikram.remember.ui.edit.MAX_NOTE_MARKDOWN_KB
import dev.bikram.remember.ui.edit.MarkdownZipTooLargeException
import dev.bikram.remember.ui.edit.NoteMarkdownImportResult
import dev.bikram.remember.ui.edit.createImportedMarkdownNote
import dev.bikram.remember.ui.edit.importMarkdownFromUri
import dev.bikram.remember.ui.edit.isJsonFileUri
import dev.bikram.remember.ui.edit.isMarkdownOrPlainTextUri
import dev.bikram.remember.ui.edit.isZipFileUri
import dev.bikram.remember.ui.edit.readMarkdownEntriesFromZip
import dev.bikram.remember.ui.lock.AppLockSession
import dev.bikram.remember.ui.lock.LockScreen
import dev.bikram.remember.ui.nav.RememberNavGraph
import dev.bikram.remember.ui.settings.RememberUpdateViewModel
import dev.bikram.remember.ui.tags.LocalTagColors
import dev.bikram.remember.ui.theme.RememberTheme
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.update.AppReviewLauncher
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.notificationDedupeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var backupIo: BackupIo

    @Inject lateinit var tagRepository: TagRepository

    @Inject lateinit var themePrefs: ThemePrefs

    @Inject lateinit var notesUiPrefs: NotesUiPrefs

    @Inject lateinit var interactionPrefs: InteractionPrefs

    @Inject lateinit var onboardingPrefs: OnboardingPrefs

    @Inject lateinit var lockPrefs: LockPrefs

    @Inject lateinit var updatePrefs: UpdatePrefs

    @Inject lateinit var rememberUpdateState: RememberUpdateState

    @Inject lateinit var appReviewLauncher: AppReviewLauncher

    @Inject lateinit var playInAppUpdateProgressController: PlayInAppUpdateProgressController

    @Inject lateinit var pendingLaunch: MutableStateFlow<LaunchAction?>

    @Inject lateinit var appLockSession: AppLockSession

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    companion object {
        const val ACTION_SHORTCUT_NEW_NOTE = "dev.bikram.remember.action.SHORTCUT_NEW_NOTE"
        const val ACTION_SHORTCUT_NEW_LIST = "dev.bikram.remember.action.SHORTCUT_NEW_LIST"
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"

        /**
         * Marks an open-note request as coming from outside the app. The key string predates the name
         * and is left alone on purpose: notifications and widget taps created by an older build are
         * still in flight with it, and renaming the key would make them look like in-app navigation.
         */
        const val EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH = "open_note_exit_on_back"
        const val EXTRA_OPEN_SETTINGS_UPDATES = "extra_open_settings_updates"
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        handleIntent(intent)
        applicationScope.launch { themePrefs.migrateLegacyColorSourceIfNeeded() }
        applicationScope.launch { notesUiPrefs.migrateFromThemePrefsIfNeeded() }
        applicationScope.launch { themePrefs.migrateLegacyBlackThemeIfNeeded() }
        applicationScope.launch { openSettingsUpdatesIfAppWasUpdated() }
        setContent {
            val systemWindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
            val systemPaneScaffoldDirective = calculatePaneScaffoldDirective(systemWindowAdaptiveInfo)
            val themeState by themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val notesUiState by notesUiPrefs.state.collectAsStateWithLifecycle(
                initialValue = NotesUiState(),
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = themeState.themeMode.effectiveDarkTheme(systemDark)
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
            }
            val tagColorFlow = remember(tagRepository) { tagRepository.observeTagColorMap() }
            val tagColors by tagColorFlow.collectAsStateWithLifecycle(
                initialValue = emptyMap(),
            )
            val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
                initialValue = InteractionState(),
            )
            CompositionLocalProvider(
                LocalTagColors provides tagColors,
                LocalSystemWindowAdaptiveInfo provides systemWindowAdaptiveInfo,
                LocalSystemPaneScaffoldDirective provides systemPaneScaffoldDirective,
            ) {
                RememberTheme(
                    themeState = themeState,
                    notesUiState = notesUiState,
                    hapticFeedbackEnabled = interactionState.hapticFeedbackEnabled,
                ) {
                    AppRoot(
                        noteRepository = noteRepository,
                        backupIo = backupIo,
                        onboardingPrefs = onboardingPrefs,
                        interactionPrefs = interactionPrefs,
                        lockPrefs = lockPrefs,
                        updatePrefs = updatePrefs,
                        rememberUpdateState = rememberUpdateState,
                        appReviewLauncher = appReviewLauncher,
                        playInAppUpdateProgressController = playInAppUpdateProgressController,
                        appScope = applicationScope,
                        launchFlow = pendingLaunch,
                        appLockSession = appLockSession,
                    )
                }
            }
        }
    }

    override fun onStart() {
        appLockSession.onAppForegrounded()
        super.onStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS_UPDATES, false)) {
            pendingLaunch.value = LaunchAction.OpenSettingsUpdates
            intent.removeExtra(EXTRA_OPEN_SETTINGS_UPDATES)
            return
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                pendingLaunch.value = LaunchAction.NewNote(prefill = text)
            }
            ACTION_SHORTCUT_NEW_NOTE -> {
                pendingLaunch.value = LaunchAction.NewNote()
            }
            ACTION_SHORTCUT_NEW_LIST -> {
                pendingLaunch.value = LaunchAction.NewList
            }
            // QS tile long-press. The manifest filter makes the system open Remember
            // instead of App Info. Bring the current screen forward and do not start a note.
            TileService.ACTION_QS_TILE_PREFERENCES -> Unit
            Intent.ACTION_VIEW -> {
                val shortcut = intent.getStringExtra("action")
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val externalLaunch = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH, false)
                when {
                    shortcut == "new_note" -> pendingLaunch.value = LaunchAction.NewNote()
                    shortcut == "new_list" -> pendingLaunch.value = LaunchAction.NewList
                    openId > 0L -> pendingLaunch.value = LaunchAction.OpenNote(openId, externalLaunch)
                }
            }
            else -> {
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val externalLaunch = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH, false)
                if (openId > 0L) pendingLaunch.value = LaunchAction.OpenNote(openId, externalLaunch)
            }
        }
    }

    /** First launch after an update (fresh installs are not announced): auto-opens the update sheet with the changelog. */
    private suspend fun openSettingsUpdatesIfAppWasUpdated() {
        val lastSeenVersion = updatePrefs.getLastSeenAppVersion()
        val currentVersion = BuildConfig.VERSION_NAME
        val wasUpdated = !lastSeenVersion.isNullOrBlank() && lastSeenVersion != currentVersion
        if (wasUpdated && BuildConfig.CHECK_UPDATES) {
            pendingLaunch.value = LaunchAction.OpenSettingsUpdates
        }
        updatePrefs.setLastSeenAppVersion(currentVersion)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRoot(
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    lockPrefs: LockPrefs,
    updatePrefs: UpdatePrefs,
    rememberUpdateState: RememberUpdateState,
    appReviewLauncher: AppReviewLauncher,
    playInAppUpdateProgressController: PlayInAppUpdateProgressController,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    appLockSession: AppLockSession,
) {
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(
        initialValue = OnboardingState(),
    )
    val updatePreferencesState by updatePrefs.state.collectAsStateWithLifecycle(
        initialValue = UpdatePreferencesState(),
    )
    val unlocked by appLockSession.unlocked.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val updateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val realPlayBannerState by playInAppUpdateProgressController.bannerUiState.collectAsStateWithLifecycle()
    val devReleaseMockPlayBannerState by rememberUpdateState.devReleasePlayBannerMockUiState.collectAsStateWithLifecycle()
    val playBannerState =
        if (devReleaseMockPlayBannerState != PlayInAppUpdateBannerUiState.Hidden) {
            devReleaseMockPlayBannerState
        } else {
            realPlayBannerState
        }
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val updateVm: RememberUpdateViewModel = hiltViewModel()
    var openSettingsRequest by rememberSaveable { mutableIntStateOf(0) }
    var dismissedUpdateBarKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Every update-flow (re-)trigger resurrects a dismissed update bar. The handled
    // epoch is saveable so rotation does not count as a new trigger.
    val updateSignalEpoch by rememberUpdateState.updateSignalEpoch.collectAsStateWithLifecycle()
    var lastHandledUpdateSignalEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(updateSignalEpoch) {
        if (updateSignalEpoch != lastHandledUpdateSignalEpoch) {
            lastHandledUpdateSignalEpoch = updateSignalEpoch
            dismissedUpdateBarKey = null
        }
    }
    // Held above the lock-screen branch so the app locking doesn't drop a pending backup dialog.
    var pendingDroppedBackupPath by rememberPendingDroppedBackupPath(context)
    val currentLockState = lockState

    if (currentLockState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val deviceUnlockAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    val deviceUnlockRequired = currentLockState.enabled && !currentLockState.hasPin
    LaunchedEffect(deviceUnlockRequired, deviceUnlockAvailable) {
        if (deviceUnlockRequired && !deviceUnlockAvailable) {
            lockPrefs.disable()
        }
    }
    val showLockScreen =
        currentLockState.enabled &&
            !unlocked &&
            (currentLockState.hasPin || deviceUnlockAvailable)

    if (showLockScreen) {
        LockScreen(
            biometricEnabled = currentLockState.biometric,
            hasPin = currentLockState.hasPin,
            pinLength = currentLockState.pinLength,
            onUnlocked = appLockSession::unlock,
            verify = { pin -> lockPrefs.verify(pin) },
        )
    } else {
        activity?.let { fragmentActivity ->
            InAppRatingAutoPromptHost(
                onboardingState = onboardingState,
                updateState = updatePreferencesState,
                activity = fragmentActivity,
                updatePrefs = updatePrefs,
                appReviewLauncher = appReviewLauncher,
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(
            dev.bikram.remember.ui.theme.LocalSnackbarHostState provides snackbarHostState,
        ) {
            var isDragSessionActive by remember { mutableStateOf(false) }
            var isDragHovered by remember { mutableStateOf(false) }
            val dragAndDropTarget =
                rememberAppRootDragAndDropTarget(
                    context = context,
                    activity = activity,
                    noteRepository = noteRepository,
                    backupIo = backupIo,
                    appScope = appScope,
                    launchFlow = launchFlow,
                    onDragSessionChanged = { isDragSessionActive = it },
                    onDragHoverChanged = { isDragHovered = it },
                    onBackupZipStaged = { staged ->
                        pendingDroppedBackupPath?.let { previousPath -> File(previousPath).delete() }
                        pendingDroppedBackupPath = staged.absolutePath
                    },
                )
            val updateKey = updateInfo?.notificationDedupeKey()
            val updateBarState = resolveUpdateBarState(playBannerState, updateInfo, updateKey, dismissedUpdateBarKey)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = ::canAcceptAppRootDragAndDrop,
                            target = dragAndDropTarget,
                        ),
            ) {
                RememberNavGraph(
                    repository = noteRepository,
                    onboardingPrefs = onboardingPrefs,
                    interactionPrefs = interactionPrefs,
                    appScope = appScope,
                    launchFlow = launchFlow,
                    openSettingsRequest = openSettingsRequest,
                    updateVm = updateVm,
                    onUpdateCheckStarted = { dismissedUpdateBarKey = null },
                    updateBarState = updateBarState,
                    updateSignalEpoch = updateSignalEpoch,
                    onUpdateClick = {
                        if (updateBarState == UpdateChromeState.Available) {
                            dismissedUpdateBarKey = updateKey
                        }
                        // Check only surfaces the update sheet; starting the actual
                        // download is the sheet's job.
                        openSettingsRequest += 1
                        updateVm.requestOpenSheet()
                    },
                    onDismissUpdateAvailable = { dismissedUpdateBarKey = updateKey },
                    onInstallUpdate = {
                        if (!rememberUpdateState.devReleaseCompletePlayUpdateIfReady()) {
                            val activity = context as? Activity
                            if (activity != null) {
                                playInAppUpdateProgressController.completeFlexibleUpdateIfReady(activity)
                            }
                        }
                    },
                )
                AppRootDragAndDropOverlay(
                    visible = isDragSessionActive || isDragHovered,
                    isDragHovered = isDragHovered,
                )
                pendingDroppedBackupPath?.let { backupPath ->
                    AppRootDroppedBackupDialog(
                        backupFile = remember(backupPath) { File(backupPath) },
                        backupIo = backupIo,
                        appScope = appScope,
                        onClear = { pendingDroppedBackupPath = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRootDragAndDropOverlay(
    visible: Boolean,
    isDragHovered: Boolean,
) {
    val dropOverlaySpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(dropOverlaySpec),
        exit = fadeOut(dropOverlaySpec),
        modifier = Modifier.fillMaxSize(),
    ) {
        val containerColor =
            if (isDragHovered) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        val contentColor =
            if (isDragHovered) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        val borderColor =
            if (isDragHovered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        val iconTint =
            if (isDragHovered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        val subtitleColor =
            if (isDragHovered) {
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        val dropZoneShape = RoundedCornerShape(24.dp)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .drawBehind {
                            val strokeWidthPx = 2.dp.toPx()
                            val cornerRadiusPx = 24.dp.toPx()
                            drawRoundRect(
                                color = borderColor,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                style =
                                    Stroke(
                                        width = strokeWidthPx,
                                        pathEffect =
                                            PathEffect.dashPathEffect(
                                                floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                                                0f,
                                            ),
                                    ),
                            )
                        },
                shape = dropZoneShape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "upload_file",
                        size = 48.dp,
                        tint = iconTint,
                        weight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.drag_drop_import_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.drag_drop_import_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRootDroppedBackupDialog(
    backupFile: File,
    backupIo: BackupIo,
    appScope: CoroutineScope,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmingRestore by remember(backupFile) { mutableStateOf(false) }
    var mediaSummary by remember(backupFile) { mutableStateOf<BackupIo.RestoreMediaSummary?>(null) }
    RememberBackupZipActionDialog(
        confirmingRestore = confirmingRestore,
        mediaSummary = mediaSummary,
        onImportMerge = {
            onClear()
            appScope.launch {
                val count =
                    try {
                        backupIo.importFrom(Uri.fromFile(backupFile), preserveIdsForNotes = false)
                    } finally {
                        backupFile.delete()
                    }
                val message =
                    if (count > 0) {
                        resources.getQuantityString(R.plurals.toast_imported_notes, count, count)
                    } else {
                        resources.getString(R.string.backup_zip_import_failed)
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        },
        onRequestRestore = {
            appScope.launch {
                mediaSummary = backupIo.inspectRestoreMedia(Uri.fromFile(backupFile))
                confirmingRestore = true
            }
        },
        onConfirmRestore = {
            onClear()
            appScope.launch {
                val result =
                    try {
                        backupIo.restoreFullReplace(Uri.fromFile(backupFile))
                    } finally {
                        backupFile.delete()
                    }
                val message =
                    resources.getQuantityString(
                        R.plurals.toast_imported_notes,
                        result.noteCount,
                        result.noteCount,
                    )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = {
            backupFile.delete()
            onClear()
        },
    )
}

@Composable
private fun rememberAppRootDragAndDropTarget(
    context: android.content.Context,
    activity: FragmentActivity?,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    onDragSessionChanged: (Boolean) -> Unit,
    onDragHoverChanged: (Boolean) -> Unit,
    onBackupZipStaged: (File) -> Unit,
): DragAndDropTarget =
    remember(context, activity, noteRepository, backupIo, appScope, launchFlow) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) = onDragSessionChanged(true)

            override fun onEntered(event: DragAndDropEvent) {
                onDragSessionChanged(true)
                onDragHoverChanged(true)
            }

            override fun onExited(event: DragAndDropEvent) = onDragHoverChanged(false)

            override fun onEnded(event: DragAndDropEvent) {
                onDragSessionChanged(false)
                onDragHoverChanged(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragSessionChanged(false)
                onDragHoverChanged(false)
                val androidEvent = event.toAndroidDragEvent()
                val clipData = androidEvent.clipData ?: return false
                val droppedUris =
                    buildList {
                        for (index in 0 until clipData.itemCount) {
                            clipData.getItemAt(index).uri?.let(::add)
                        }
                    }
                if (droppedUris.isEmpty()) return false
                val dragPermissions = activity?.requestDragAndDropPermissions(androidEvent)
                appScope.launch {
                    try {
                        handleDroppedUris(
                            context = context,
                            droppedUris = droppedUris,
                            noteRepository = noteRepository,
                            backupIo = backupIo,
                            launchFlow = launchFlow,
                            onBackupZipStaged = onBackupZipStaged,
                        )
                    } finally {
                        dragPermissions?.release()
                    }
                }
                return true
            }
        }
    }

private suspend fun handleDroppedUris(
    context: android.content.Context,
    droppedUris: List<Uri>,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    launchFlow: MutableStateFlow<LaunchAction?>,
    onBackupZipStaged: (File) -> Unit,
) {
    val firstUri = droppedUris.first()
    if (droppedUris.size == 1 && isJsonFileUri(context, firstUri)) {
        handleSingleDroppedJson(context, firstUri, launchFlow)
        return
    }
    if (droppedUris.size == 1 && isZipFileUri(context, firstUri) && backupIo.isBackupZip(firstUri)) {
        val stagedFile = stageDroppedBackupFile(context, firstUri)
        if (stagedFile != null) {
            onBackupZipStaged(stagedFile)
        } else {
            Toast.makeText(context, context.getString(R.string.backup_zip_import_failed), Toast.LENGTH_SHORT).show()
        }
        return
    }
    if (droppedUris.size == 1 && !isZipFileUri(context, firstUri)) {
        handleSingleDroppedMarkdown(context, firstUri, noteRepository, launchFlow)
        return
    }
    handleMultiDroppedUris(context, droppedUris, noteRepository, backupIo)
}

private suspend fun handleSingleDroppedJson(
    context: android.content.Context,
    uri: Uri,
    launchFlow: MutableStateFlow<LaunchAction?>,
) {
    val validJson =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { it.reader(Charsets.UTF_8).readText() }
                        ?: return@runCatching null
                ManualTaskImportParser().parse(text)
                text
            }.getOrNull()
        }
    if (validJson != null) {
        launchFlow.value = LaunchAction.OpenTaskImport(validJson)
    } else {
        Toast.makeText(context, context.getString(R.string.drag_drop_error_invalid_json), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun stageDroppedBackupFile(
    context: android.content.Context,
    uri: Uri,
): File? =
    withContext(Dispatchers.IO) {
        var tempFile: File? = null
        runCatching {
            val stagedFile = DroppedBackupStaging.createFile(context.cacheDir)
            tempFile = stagedFile
            context.contentResolver.openInputStream(uri)?.use { input ->
                stagedFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not open dropped backup")
            stagedFile
        }.onFailure {
            tempFile?.delete()
        }.getOrNull()
    }

/**
 * Path of a dropped Remember backup waiting for the Import/Restore choice. Saveable, so a window
 * resize or rotation keeps the pending dialog instead of stranding its staged copy in the cache;
 * a restored path whose copy has since been deleted is cleared.
 */
@Composable
private fun rememberPendingDroppedBackupPath(context: android.content.Context): MutableState<String?> {
    val pendingPath = rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val keptPath = pendingPath.value
        val keptFileExists =
            withContext(Dispatchers.IO) {
                DroppedBackupStaging.deleteOrphansOnce(context.cacheDir, keepPath = keptPath)
                keptPath != null && File(keptPath).isFile
            }
        if (keptPath != null && !keptFileExists && pendingPath.value == keptPath) {
            pendingPath.value = null
        }
    }
    return pendingPath
}

/**
 * Cache copies of dropped Remember backup ZIPs awaiting the Import/Restore choice. Copies left
 * behind by an earlier process (killed while the dialog was open) are deleted once per process;
 * a copy still referenced by restored UI state is kept.
 */
private object DroppedBackupStaging {
    private const val FILE_PREFIX = "remember_drop_backup_"
    private const val FILE_SUFFIX = ".zip"
    private val orphansDeleted = AtomicBoolean(false)

    fun createFile(cacheDir: File): File = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDir)

    fun deleteOrphansOnce(
        cacheDir: File,
        keepPath: String?,
    ) {
        if (!orphansDeleted.compareAndSet(false, true)) return
        cacheDir
            .listFiles { file ->
                file.name.startsWith(FILE_PREFIX) &&
                    file.name.endsWith(FILE_SUFFIX) &&
                    file.absolutePath != keepPath
            }?.forEach(File::delete)
    }
}

private suspend fun handleSingleDroppedMarkdown(
    context: android.content.Context,
    uri: Uri,
    noteRepository: NoteRepository,
    launchFlow: MutableStateFlow<LaunchAction?>,
) {
    if (!isMarkdownOrPlainTextUri(context, uri)) {
        Toast.makeText(context, context.getString(R.string.drag_drop_error_unsupported_type), Toast.LENGTH_SHORT).show()
        return
    }
    when (val result = importMarkdownFromUri(context, uri)) {
        is NoteMarkdownImportResult.Success -> {
            val noteTitle = result.suggestedTitle ?: context.getString(R.string.edit_note_title_new)
            val newId = createImportedMarkdownNote(noteRepository, noteTitle, result.markdown)
            Toast.makeText(context, context.getString(R.string.toast_import_md_success), Toast.LENGTH_SHORT).show()
            launchFlow.value = LaunchAction.OpenNote(newId)
        }
        NoteMarkdownImportResult.TooLarge ->
            Toast
                .makeText(
                    context,
                    context.getString(R.string.toast_import_md_too_large, MAX_NOTE_MARKDOWN_KB),
                    Toast.LENGTH_SHORT,
                ).show()
        NoteMarkdownImportResult.Empty ->
            Toast.makeText(context, context.getString(R.string.toast_import_md_empty), Toast.LENGTH_SHORT).show()
        NoteMarkdownImportResult.Failed ->
            Toast.makeText(context, context.getString(R.string.toast_import_md_failed), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun handleMultiDroppedUris(
    context: android.content.Context,
    droppedUris: List<Uri>,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
) {
    var importedCount = 0
    var sawZip = false
    var sawZipValidContent = false
    var sawUnsupported = false
    var sawFailure = false
    var sawZipTooLarge = false
    val fallbackTitle = context.getString(R.string.edit_note_title_new)
    withContext(Dispatchers.IO) {
        droppedUris.forEach { uri ->
            when {
                isZipFileUri(context, uri) -> {
                    sawZip = true
                    if (backupIo.isBackupZip(uri)) {
                        val count = backupIo.importFrom(uri, preserveIdsForNotes = false)
                        if (count > 0) {
                            sawZipValidContent = true
                            importedCount += count
                        } else {
                            sawFailure = true
                        }
                    } else {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                readMarkdownEntriesFromZip(inputStream)
                            } ?: throw IllegalArgumentException("Could not open archive")
                        }.onSuccess { entries ->
                            if (entries.isNotEmpty()) sawZipValidContent = true
                            entries.forEach { entry ->
                                createImportedMarkdownNote(
                                    noteRepository = noteRepository,
                                    title = entry.suggestedTitle ?: fallbackTitle,
                                    markdown = entry.markdown,
                                )
                                importedCount++
                            }
                        }.onFailure { error ->
                            if (error is MarkdownZipTooLargeException) sawZipTooLarge = true else sawFailure = true
                        }
                    }
                }
                isMarkdownOrPlainTextUri(context, uri) -> {
                    when (val result = importMarkdownFromUri(context, uri)) {
                        is NoteMarkdownImportResult.Success -> {
                            createImportedMarkdownNote(
                                noteRepository = noteRepository,
                                title = result.suggestedTitle ?: fallbackTitle,
                                markdown = result.markdown,
                            )
                            importedCount++
                        }
                        NoteMarkdownImportResult.Failed -> sawFailure = true
                        NoteMarkdownImportResult.Empty,
                        NoteMarkdownImportResult.TooLarge,
                        -> Unit
                    }
                }
                else -> sawUnsupported = true
            }
        }
    }
    val feedbackMessage =
        if (importedCount > 0) {
            context.resources.getQuantityString(R.plurals.toast_imported_notes, importedCount, importedCount)
        } else {
            when {
                sawZipTooLarge -> context.getString(R.string.markdown_import_zip_too_large)
                sawZip && !sawZipValidContent && !sawFailure -> context.getString(R.string.drag_drop_error_zip_empty)
                sawUnsupported && !sawFailure -> context.getString(R.string.drag_drop_error_unsupported_type)
                sawFailure -> context.getString(R.string.markdown_import_failed)
                else -> context.getString(R.string.markdown_import_none_valid)
            }
        }
    Toast.makeText(context, feedbackMessage, Toast.LENGTH_SHORT).show()
}

private fun resolveUpdateBarState(
    playBannerState: PlayInAppUpdateBannerUiState,
    updateInfo: dev.bikram.remember.update.RememberUpdateInfo?,
    updateKey: String?,
    dismissedUpdateBarKey: String?,
): UpdateChromeState {
    val updateAvailable = BuildConfig.CHECK_UPDATES && updateInfo != null
    return when (playBannerState) {
        is PlayInAppUpdateBannerUiState.Downloading ->
            UpdateChromeState.Downloading(
                bytesDownloaded = playBannerState.bytesDownloaded,
                totalBytesToDownload = playBannerState.totalBytesToDownload,
                indeterminateProgress = playBannerState.indeterminateProgress,
            )
        PlayInAppUpdateBannerUiState.ReadyToInstall -> UpdateChromeState.ReadyToInstall
        PlayInAppUpdateBannerUiState.Hidden ->
            if (updateAvailable && updateKey != dismissedUpdateBarKey) {
                UpdateChromeState.Available
            } else {
                UpdateChromeState.Hidden
            }
    }
}

/**
 * Types that light up the drop overlay. Android exposes only the drag's declared types until the
 * drop, and the drop handler re-checks each file's name and type.
 * - text/plain covers .txt files, but a dragged text selection declares the same type, so the
 *   overlay also shows for text dragged in from another app; the two can't be told apart here.
 * - application/octet-stream is kept because some file managers and cloud apps label .md (and
 *   occasionally .zip) files that way; anything else with that label gets the unsupported-type
 *   toast on drop.
 * Everything else (images, video, PDFs, launcher shortcuts, ...) is ignored.
 */
private val AppRootDropMimeTypes =
    listOf(
        "text/markdown",
        "text/x-markdown",
        ClipDescription.MIMETYPE_TEXT_PLAIN,
        "application/json",
        "text/json",
        "application/zip",
        "application/x-zip-compressed",
        "application/octet-stream",
    )

private fun canAcceptAppRootDragAndDrop(event: DragAndDropEvent): Boolean {
    val description = event.toAndroidDragEvent().clipDescription ?: return false
    return AppRootDropMimeTypes.any(description::hasMimeType)
}
