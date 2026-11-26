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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.LockPrefs
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
import dev.bikram.remember.di.PendingNoteSelection
import dev.bikram.remember.share.FileImportCoordinator
import dev.bikram.remember.share.FileImportOutcome
import dev.bikram.remember.share.ShareMediaStaging
import dev.bikram.remember.share.StagedBackupActionDialog
import dev.bikram.remember.share.StagedShareImage
import dev.bikram.remember.share.rememberPendingStagedBackupPath
import dev.bikram.remember.ui.InAppRatingAutoPromptHost
import dev.bikram.remember.ui.common.AppDropImportGuard
import dev.bikram.remember.ui.common.LocalAppDropImportGuard
import dev.bikram.remember.ui.common.LocalSystemPaneScaffoldDirective
import dev.bikram.remember.ui.common.LocalSystemWindowAdaptiveInfo
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.UpdateChromeState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var backupIo: BackupIo

    @Inject lateinit var defaultNotePrefs: DefaultNotePrefs

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

    @Inject lateinit var pendingNoteSelection: PendingNoteSelection

    @Inject lateinit var appMediaStorage: AppMediaStorage

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
                        defaultNotePrefs = defaultNotePrefs,
                        onboardingPrefs = onboardingPrefs,
                        interactionPrefs = interactionPrefs,
                        lockPrefs = lockPrefs,
                        updatePrefs = updatePrefs,
                        rememberUpdateState = rememberUpdateState,
                        appReviewLauncher = appReviewLauncher,
                        playInAppUpdateProgressController = playInAppUpdateProgressController,
                        appScope = applicationScope,
                        launchFlow = pendingLaunch,
                        pendingNoteSelection = pendingNoteSelection,
                        appMediaStorage = appMediaStorage,
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
    defaultNotePrefs: DefaultNotePrefs,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    lockPrefs: LockPrefs,
    updatePrefs: UpdatePrefs,
    rememberUpdateState: RememberUpdateState,
    appReviewLauncher: AppReviewLauncher,
    playInAppUpdateProgressController: PlayInAppUpdateProgressController,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    pendingNoteSelection: PendingNoteSelection,
    appMediaStorage: AppMediaStorage,
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
    var pendingDroppedBackupPath by rememberPendingStagedBackupPath(context)
    // Dropped files Remember can't import as notes, already copied into cache, awaiting the user's OK.
    var pendingDropAttachments by remember { mutableStateOf<PendingDropAttachments?>(null) }
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
        val dropImportGuard = remember { AppDropImportGuard() }
        androidx.compose.runtime.CompositionLocalProvider(
            dev.bikram.remember.ui.theme.LocalSnackbarHostState provides snackbarHostState,
            LocalAppDropImportGuard provides dropImportGuard,
        ) {
            var isDragSessionActive by remember { mutableStateOf(false) }
            var isDragHovered by remember { mutableStateOf(false) }
            val dragAndDropTarget =
                rememberAppRootDragAndDropTarget(
                    context = context,
                    activity = activity,
                    noteRepository = noteRepository,
                    backupIo = backupIo,
                    defaultNotePrefs = defaultNotePrefs,
                    appScope = appScope,
                    launchFlow = launchFlow,
                    pendingNoteSelection = pendingNoteSelection,
                    onDragSessionChanged = { isDragSessionActive = it },
                    onDragHoverChanged = { isDragHovered = it },
                    onAttachmentsStaged = { staged ->
                        pendingDropAttachments = replacePendingDropAttachments(context, pendingDropAttachments, staged)
                    },
                    onBackupZipStaged = { staged ->
                        pendingDroppedBackupPath?.let { previousPath -> File(previousPath).delete() }
                        pendingDroppedBackupPath = staged.absolutePath
                    },
                )
            val updateKey = updateInfo?.notificationDedupeKey()
            val updateBarState = resolveUpdateBarState(playBannerState, updateInfo, updateKey, dismissedUpdateBarKey)
            Box(modifier = Modifier.fillMaxSize().appRootDropTarget(dropImportGuard, dragAndDropTarget)) {
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
                    onInstallUpdate = { completeUpdateInstall(context, rememberUpdateState, playInAppUpdateProgressController) },
                )
                AppRootDragAndDropOverlay(
                    visible = isDragSessionActive || isDragHovered,
                    isDragHovered = isDragHovered,
                )
                DroppedFileDialogs(
                    pendingBackupPath = pendingDroppedBackupPath,
                    onBackupCleared = { pendingDroppedBackupPath = null },
                    pendingAttachments = pendingDropAttachments,
                    onAttachmentsCleared = { pendingDropAttachments = null },
                    noteRepository = noteRepository,
                    backupIo = backupIo,
                    appMediaStorage = appMediaStorage,
                    defaultNotePrefs = defaultNotePrefs,
                    appScope = appScope,
                    launchFlow = launchFlow,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

/** Dropped files already copied to cache, waiting for the user to confirm "create a note with attachments". */
private data class PendingDropAttachments(
    val sessionId: String,
    val files: List<StagedShareImage>,
    /** Ask before creating the note; false creates it straight away (see [FileImportOutcome.NeedsAttachments]). */
    val confirmFirst: Boolean,
)

private fun completeUpdateInstall(
    context: android.content.Context,
    rememberUpdateState: RememberUpdateState,
    playInAppUpdateProgressController: PlayInAppUpdateProgressController,
) {
    if (!rememberUpdateState.devReleaseCompletePlayUpdateIfReady()) {
        (context as? Activity)?.let(playInAppUpdateProgressController::completeFlexibleUpdateIfReady)
    }
}

private fun replacePendingDropAttachments(
    context: android.content.Context,
    previous: PendingDropAttachments?,
    staged: PendingDropAttachments,
): PendingDropAttachments {
    previous?.let { ShareMediaStaging.deleteSession(context.cacheDir, it.sessionId) }
    return staged
}

/** The two confirm dialogs for files dropped on the window: backup ZIP (import/restore) and attach-to-new-note. */
@Composable
private fun DroppedFileDialogs(
    pendingBackupPath: String?,
    onBackupCleared: () -> Unit,
    pendingAttachments: PendingDropAttachments?,
    onAttachmentsCleared: () -> Unit,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    appMediaStorage: AppMediaStorage,
    defaultNotePrefs: DefaultNotePrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    pendingAttachments?.let { pending ->
        DroppedAttachmentsDialog(
            pending = pending,
            noteRepository = noteRepository,
            appMediaStorage = appMediaStorage,
            defaultNotePrefs = defaultNotePrefs,
            appScope = appScope,
            launchFlow = launchFlow,
            onClear = onAttachmentsCleared,
        )
    }
    pendingBackupPath?.let { backupPath ->
        StagedBackupActionDialog(
            backupFile = remember(backupPath) { File(backupPath) },
            backupIo = backupIo,
            appScope = appScope,
            snackbarHostState = snackbarHostState,
            onClear = onBackupCleared,
        )
    }
}

@Composable
private fun DroppedAttachmentsDialog(
    pending: PendingDropAttachments,
    noteRepository: NoteRepository,
    appMediaStorage: AppMediaStorage,
    defaultNotePrefs: DefaultNotePrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val untitledTitle = stringResource(R.string.edit_note_title_new)
    // Runs on the app scope so it outlives this composable leaving composition when the state clears.
    val createNote: () -> Unit = {
        appScope.launch {
            val noteId =
                runCatching {
                    FileImportCoordinator.createNoteWithAttachments(
                        noteRepository = noteRepository,
                        appMediaStorage = appMediaStorage,
                        defaultNotePrefs = defaultNotePrefs,
                        staged = pending.files,
                        untitledTitle = untitledTitle,
                    )
                }.getOrNull()
            ShareMediaStaging.deleteSession(context.cacheDir, pending.sessionId)
            if (noteId != null) launchFlow.value = LaunchAction.OpenNote(noteId, forceEdit = true)
        }
    }
    if (!pending.confirmFirst) {
        LaunchedEffect(pending) {
            createNote()
            onClear()
        }
        return
    }
    RememberConfirmDialog(
        title = stringResource(R.string.drop_attachments_title),
        text = pluralStringResource(R.plurals.drop_attachments_message, pending.files.size, pending.files.size),
        confirmLabel = stringResource(R.string.drop_attachments_create),
        onConfirm = {
            createNote()
            onClear()
        },
        onDismiss = {
            ShareMediaStaging.deleteSession(context.cacheDir, pending.sessionId)
            onClear()
        },
    )
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
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
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
                        text = stringResource(R.string.drag_drop_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.drag_drop_subtitle),
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
private fun rememberAppRootDragAndDropTarget(
    context: android.content.Context,
    activity: FragmentActivity?,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    defaultNotePrefs: DefaultNotePrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    pendingNoteSelection: PendingNoteSelection,
    onAttachmentsStaged: (PendingDropAttachments) -> Unit,
    onDragSessionChanged: (Boolean) -> Unit,
    onDragHoverChanged: (Boolean) -> Unit,
    onBackupZipStaged: (File) -> Unit,
): DragAndDropTarget =
    remember(context, activity, noteRepository, backupIo, defaultNotePrefs, appScope, launchFlow) {
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
                        when (
                            val outcome =
                                FileImportCoordinator.importUris(
                                    context = context,
                                    uris = droppedUris,
                                    noteRepository = noteRepository,
                                    backupIo = backupIo,
                                    defaultNotePrefs = defaultNotePrefs,
                                )
                        ) {
                            is FileImportOutcome.ImportedSingleNote -> launchFlow.value = LaunchAction.OpenNote(outcome.noteId)
                            is FileImportOutcome.StagedBackupZip -> onBackupZipStaged(outcome.file)
                            is FileImportOutcome.ValidTaskImportJson -> launchFlow.value = LaunchAction.OpenTaskImport(outcome.jsonText)
                            is FileImportOutcome.ImportedBatch -> {
                                pendingNoteSelection.offer(outcome.noteIds)
                                launchFlow.value = LaunchAction.ShowNotesHome
                            }
                            is FileImportOutcome.NeedsAttachments -> {
                                val sessionId = UUID.randomUUID().toString()
                                val staged = ShareMediaStaging.stageImages(context, sessionId, outcome.uris)
                                if (staged.stagedImages.isEmpty()) {
                                    ShareMediaStaging.deleteSession(context.cacheDir, sessionId)
                                    Toast
                                        .makeText(context, R.string.share_capture_error_image_read_failed, Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    onAttachmentsStaged(PendingDropAttachments(sessionId, staged.stagedImages, outcome.confirmFirst))
                                }
                            }
                            FileImportOutcome.HandledWithErrorToast -> Unit
                        }
                    } finally {
                        dragPermissions?.release()
                    }
                }
                return true
            }
        }
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
 * Drags that never carry a file and so must not light up the drop overlay: a link or selected rich
 * text dragged from a browser, and Android system payloads such as launcher shortcuts. Android
 * exposes only the drag's declared types until the drop, so everything else is accepted and the
 * drop handler decides per file: importable notes and backups are imported, anything else is
 * offered as attachments. A dragged plain-text selection declares text/plain like a .txt file does;
 * it is accepted here and ignored on drop because it carries no file URI.
 */
private val AppRootDropIgnoredMimeTypes =
    listOf(
        "text/html",
        "text/uri-list",
        "application/vnd.android.*",
    )

/** The window-wide import drop target; off while a note or checklist is being edited ([AppDropImportGuard]). */
private fun Modifier.appRootDropTarget(
    guard: AppDropImportGuard,
    target: DragAndDropTarget,
): Modifier =
    dragAndDropTarget(
        shouldStartDragAndDrop = { event -> !guard.suppressed && canAcceptAppRootDragAndDrop(event) },
        target = target,
    )

private fun canAcceptAppRootDragAndDrop(event: DragAndDropEvent): Boolean {
    val description = event.toAndroidDragEvent().clipDescription ?: return false
    return (0 until description.mimeTypeCount).any { index ->
        val mimeType = description.getMimeType(index)
        AppRootDropIgnoredMimeTypes.none { ignored -> ClipDescription.compareMimeTypes(mimeType, ignored) }
    }
}
