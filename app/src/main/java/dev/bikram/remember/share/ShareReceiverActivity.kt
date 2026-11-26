package dev.bikram.remember.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.DefaultNotePreferencesState
import dev.bikram.remember.data.DefaultNotePrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NotesUiPrefs
import dev.bikram.remember.data.NotesUiState
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.di.PendingNoteSelection
import dev.bikram.remember.ui.lock.AppLockSession
import dev.bikram.remember.ui.lock.LockScreen
import dev.bikram.remember.ui.tags.LocalTagColors
import dev.bikram.remember.ui.theme.RememberTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject
import android.graphics.Color as AndroidColor

@AndroidEntryPoint
class ShareReceiverActivity : FragmentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var backupIo: BackupIo

    @Inject lateinit var tagRepository: TagRepository

    @Inject lateinit var themePrefs: ThemePrefs

    @Inject lateinit var notesUiPrefs: NotesUiPrefs

    @Inject lateinit var interactionPrefs: InteractionPrefs

    @Inject lateinit var defaultNotePrefs: DefaultNotePrefs

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @Inject lateinit var lockPrefs: LockPrefs

    @Inject lateinit var appLockSession: AppLockSession

    @Inject lateinit var pendingLaunch: MutableStateFlow<LaunchAction?>

    @Inject lateinit var pendingNoteSelection: PendingNoteSelection

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())

        val initialRoute = ShareIntentParser.parse(this, intent)
        if (initialRoute is ShareRoute.NothingToShare) {
            Toast
                .makeText(
                    applicationContext,
                    getString(R.string.share_capture_error_nothing_to_share),
                    Toast.LENGTH_SHORT,
                ).show()
            finish()
            return
        }

        setContent {
            // A file import that turns out to be unimportable switches this to the attachment sheet.
            var route by remember { mutableStateOf(initialRoute) }
            val themeState by themePrefs.state.collectAsStateWithLifecycle(initialValue = ThemeState())
            val notesUiState by notesUiPrefs.state.collectAsStateWithLifecycle(initialValue = NotesUiState())
            val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(initialValue = InteractionState())
            val defaultNoteState by defaultNotePrefs.state.collectAsStateWithLifecycle(initialValue = DefaultNotePreferencesState())
            val reminderState by reminderPrefs.state.collectAsStateWithLifecycle(initialValue = ReminderPreferencesState())
            val tagColorFlow = remember(tagRepository) { tagRepository.observeTagColorMap() }
            val tagColors by tagColorFlow.collectAsStateWithLifecycle(initialValue = emptyMap())

            CompositionLocalProvider(LocalTagColors provides tagColors) {
                RememberTheme(
                    themeState = themeState,
                    notesUiState = notesUiState,
                    paintBackground = false,
                    hapticFeedbackEnabled = interactionState.hapticFeedbackEnabled,
                ) {
                    when (val currentRoute = route) {
                        is ShareRoute.FileImport ->
                            ShareFileImportHost(
                                route = currentRoute,
                                onNeedsAttachments = { uris ->
                                    route = ShareRoute.QuickCapture(initialTitle = "", rawHtml = null, plainText = "", imageUris = uris)
                                },
                                noteRepository = noteRepository,
                                backupIo = backupIo,
                                defaultNotePrefs = defaultNotePrefs,
                                lockPrefs = lockPrefs,
                                appLockSession = appLockSession,
                                applicationScope = applicationScope,
                                onOpenTaskImport = { validJson ->
                                    pendingLaunch.value = LaunchAction.OpenTaskImport(validJson)
                                    launchMainActivity()
                                    finish()
                                },
                                onOpenNote = { noteId ->
                                    pendingLaunch.value = LaunchAction.OpenNote(id = noteId, externalLaunch = true)
                                    launchMainActivity()
                                    finish()
                                },
                                onOpenHome = { importedNoteIds ->
                                    if (importedNoteIds.size == 1) {
                                        pendingLaunch.value = LaunchAction.OpenNote(id = importedNoteIds.single(), externalLaunch = true)
                                    } else {
                                        pendingNoteSelection.offer(importedNoteIds)
                                        pendingLaunch.value = LaunchAction.ShowNotesHome
                                    }
                                    launchMainActivity()
                                    finish()
                                },
                                onFinished = { finish() },
                            )
                        is ShareRoute.QuickCapture ->
                            ShareQuickCaptureHost(
                                route = currentRoute,
                                defaultNoteState = defaultNoteState,
                                reminderState = reminderState,
                                onOpenInEditor = { savedNoteId ->
                                    pendingLaunch.value = LaunchAction.OpenNote(id = savedNoteId, externalLaunch = true)
                                    launchMainActivity()
                                    finish()
                                },
                                onAbortWithImageError = {
                                    Toast
                                        .makeText(
                                            applicationContext,
                                            getString(R.string.share_capture_error_image_read_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    finish()
                                },
                                onCancel = { finish() },
                            )
                        ShareRoute.NothingToShare -> Unit
                    }
                }
            }
        }
    }

    private fun launchMainActivity() {
        val mainIntent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
        startActivity(mainIntent)
    }
}

@Composable
private fun ShareFileImportHost(
    route: ShareRoute.FileImport,
    noteRepository: NoteRepository,
    backupIo: BackupIo,
    defaultNotePrefs: DefaultNotePrefs,
    lockPrefs: LockPrefs,
    appLockSession: AppLockSession,
    applicationScope: CoroutineScope,
    onOpenTaskImport: (String) -> Unit,
    onNeedsAttachments: (List<Uri>) -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenHome: (List<Long>) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var pendingBackupPath by rememberPendingStagedBackupPath(context)

    // Keyed on Unit: flipping a state key from inside the effect would cancel the import mid-flight,
    // leaving this translucent activity alive and swallowing every touch meant for the app beneath.
    LaunchedEffect(Unit) {
        when (
            val outcome =
                FileImportCoordinator.importUris(
                    context = context,
                    uris = route.uris,
                    noteRepository = noteRepository,
                    backupIo = backupIo,
                    defaultNotePrefs = defaultNotePrefs,
                )
        ) {
            is FileImportOutcome.StagedBackupZip -> {
                pendingBackupPath?.let { previous -> File(previous).delete() }
                pendingBackupPath = outcome.file.absolutePath
            }
            is FileImportOutcome.ValidTaskImportJson -> onOpenTaskImport(outcome.jsonText)
            is FileImportOutcome.NeedsAttachments -> onNeedsAttachments(outcome.uris)
            is FileImportOutcome.ImportedSingleNote -> onOpenNote(outcome.noteId)
            is FileImportOutcome.ImportedBatch -> onOpenHome(outcome.noteIds)
            FileImportOutcome.HandledWithErrorToast -> onFinished()
        }
    }

    val activeBackupPath = pendingBackupPath ?: return
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val unlocked by appLockSession.unlocked.collectAsStateWithLifecycle()
    val currentLockState = lockState ?: return

    val deviceUnlockAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    val requiresUnlock =
        currentLockState.enabled &&
            !unlocked &&
            (currentLockState.hasPin || deviceUnlockAvailable)

    if (requiresUnlock) {
        Box(modifier = Modifier.fillMaxSize()) {
            LockScreen(
                biometricEnabled = currentLockState.biometric,
                hasPin = currentLockState.hasPin,
                pinLength = currentLockState.pinLength,
                onUnlocked = appLockSession::unlock,
                verify = { pin -> lockPrefs.verify(pin) },
            )
        }
    } else {
        StagedBackupActionDialog(
            backupFile = remember(activeBackupPath) { File(activeBackupPath) },
            backupIo = backupIo,
            appScope = applicationScope,
            snackbarHostState = null,
            onActionChosen = { onOpenHome(emptyList()) },
            onClear = {
                pendingBackupPath = null
                onFinished()
            },
        )
    }
}

@Composable
private fun ShareQuickCaptureHost(
    route: ShareRoute.QuickCapture,
    defaultNoteState: DefaultNotePreferencesState,
    reminderState: ReminderPreferencesState,
    onOpenInEditor: (Long) -> Unit,
    onAbortWithImageError: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ShareCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val stagedImages by viewModel.stagedImages.collectAsStateWithLifecycle()
    val singleImageMode by viewModel.singleImageMode.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val availableTags by viewModel.activeTagSuggestions.collectAsStateWithLifecycle()
    val importance by viewModel.importance.collectAsStateWithLifecycle()
    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val savedNoteId by viewModel.savedNoteId.collectAsStateWithLifecycle()
    val savedNote by viewModel.savedNote.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.initializeIfNeeded(route)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ShareCaptureUiEvent.ImageStageFailed -> {
                    if (event.abortCapture) {
                        onAbortWithImageError()
                    } else {
                        Toast
                            .makeText(
                                appContext,
                                appContext.getString(R.string.share_capture_error_image_read_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
                is ShareCaptureUiEvent.SavedForEditor -> onOpenInEditor(event.noteId)
            }
        }
    }

    ShareCaptureBottomSheet(
        title = title,
        body = body,
        stagedImages = stagedImages,
        singleImageMode = singleImageMode,
        reminders = reminders,
        tags = tags,
        availableTags = availableTags,
        importance = importance,
        defaultNoteState = defaultNoteState,
        reminderState = reminderState,
        isInitializing = isInitializing,
        isSaving = isSaving,
        savedNoteId = savedNoteId,
        savedNote = savedNote,
        onTitleChange = viewModel::setTitle,
        onBodyChange = viewModel::setBody,
        onSingleImageModeChange = viewModel::setSingleImageMode,
        onRemoveStagedImage = viewModel::removeStagedImage,
        onRemindersChange = viewModel::setReminders,
        onClearReminders = viewModel::clearReminders,
        onTagsWithColorsChange = viewModel::saveTagsWithColors,
        onEditExistingTag = viewModel::editExistingTag,
        onImportanceChange = viewModel::setImportance,
        onSave = { viewModel.saveQuickClose() },
        onClose = onCancel,
        onOpenSavedNote = { noteId -> onOpenInEditor(noteId) },
        onOpenInEditor = viewModel::saveAndOpenInEditor,
        onCancel = {
            viewModel.discardCapture()
            onCancel()
        },
    )
}
