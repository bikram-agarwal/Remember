package dev.bikram.remember.di

import android.content.Context
import dev.bikram.remember.backup.BackupExportCoordinator
import dev.bikram.remember.backup.NoteBackupDirtyTracker
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.RememberDatabase
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.googletasks.GoogleTasksApi
import dev.bikram.remember.googletasks.GoogleTasksImporter
import dev.bikram.remember.googletasks.GoogleTasksImportPrefs
import dev.bikram.remember.googletasks.GoogleTasksRepository
import dev.bikram.remember.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    private val db: RememberDatabase = RememberDatabase.build(appContext)
    /** Outlives any single screen's ViewModel — safe for fire-and-forget save-on-exit. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val reminderPrefs: ReminderPrefs = ReminderPrefs(appContext)
    val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext, reminderPrefs)
    val themePrefs: ThemePrefs = ThemePrefs(appContext)
    val viewOptionsPrefs: ViewOptionsPrefs = ViewOptionsPrefs(appContext)
    val tagRepository: TagRepository = TagRepository(
        tagDao = db.tagDao(),
        noteDao = db.noteDao(),
        themePrefs = themePrefs,
        database = db,
    )
    val noteRepository: NoteRepository = NoteRepository(
        noteDao = db.noteDao(),
        itemDao = db.checklistItemDao(),
        attachmentDao = db.attachmentDao(),
        scheduler = reminderScheduler,
        tagRepository = tagRepository,
        database = db,
    )
    val lockPrefs: LockPrefs = LockPrefs(appContext)
    val onboardingPrefs: OnboardingPrefs = OnboardingPrefs(appContext)
    val interactionPrefs: InteractionPrefs = InteractionPrefs(appContext)
    val backupPrefs: BackupPrefs = BackupPrefs(appContext)
    val quickCapturePrefs: QuickCapturePrefs = QuickCapturePrefs(appContext)
    val backupIo: BackupIo = BackupIo(
        context = appContext,
        repository = noteRepository,
        themePrefs = themePrefs,
        viewOptionsPrefs = viewOptionsPrefs,
        lockPrefs = lockPrefs,
        interactionPrefs = interactionPrefs,
        backupPrefs = backupPrefs,
    )
    /** Google Tasks import wiring. The API + importer are stateless; the prefs hold per-user data. */
    val googleTasksImportPrefs: GoogleTasksImportPrefs = GoogleTasksImportPrefs(appContext)
    private val googleTasksApi: GoogleTasksApi = GoogleTasksApi()
    val googleTasksRepository: GoogleTasksRepository = GoogleTasksRepository(
        context = appContext,
        api = googleTasksApi,
    )
    val googleTasksImporter: GoogleTasksImporter = GoogleTasksImporter(repository = noteRepository)

    val noteBackupDirtyTracker: NoteBackupDirtyTracker = NoteBackupDirtyTracker()
    val backupExportCoordinator: BackupExportCoordinator = BackupExportCoordinator(
        backupPrefs = backupPrefs,
        backupIo = backupIo,
        noteRepository = noteRepository,
        applicationScope = applicationScope,
        noteBackupDirtyTracker = noteBackupDirtyTracker,
    )

    /** One-shot launch actions (share-in, shortcut, notification tap). Consumed by NavGraph. */
    val pendingLaunch = kotlinx.coroutines.flow.MutableStateFlow<LaunchAction?>(null)
    /** Process-local lock session. Cold starts still require unlock; warm returns do not. */
    val appUnlocked = kotlinx.coroutines.flow.MutableStateFlow(false)

    init {
        // Warm the first Home query while the system splash / initial composition is still
        // happening, so the first fullscreen Home frame is less likely to render before Room
        // has produced the active notes list.
        applicationScope.launch(Dispatchers.IO) {
            noteRepository.observeActive().first()
        }
        applicationScope.launch {
            tagRepository.synchronizeLegacyTagColors()
        }
    }
}

sealed interface LaunchAction {
    data class NewNote(val prefill: String = "") : LaunchAction
    data object NewList : LaunchAction
    data class OpenNote(val id: Long) : LaunchAction
}
