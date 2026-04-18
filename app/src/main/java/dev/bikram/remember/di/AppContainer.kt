package dev.bikram.remember.di

import android.content.Context
import dev.bikram.remember.backup.BackupExportCoordinator
import dev.bikram.remember.backup.NoteBackupDirtyTracker
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.RememberDatabase
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    private val db: RememberDatabase = RememberDatabase.build(appContext)
    /** Outlives any single screen's ViewModel — safe for fire-and-forget save-on-exit. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val reminderScheduler: ReminderScheduler = ReminderScheduler(appContext)
    val noteRepository: NoteRepository = NoteRepository(
        noteDao = db.noteDao(),
        itemDao = db.checklistItemDao(),
        attachmentDao = db.attachmentDao(),
        scheduler = reminderScheduler,
    )
    val lockPrefs: LockPrefs = LockPrefs(appContext)
    val themePrefs: ThemePrefs = ThemePrefs(appContext)
    val interactionPrefs: InteractionPrefs = InteractionPrefs(appContext)
    val backupPrefs: BackupPrefs = BackupPrefs(appContext)
    val backupIo: BackupIo = BackupIo(
        context = appContext,
        repository = noteRepository,
        themePrefs = themePrefs,
        lockPrefs = lockPrefs,
        interactionPrefs = interactionPrefs,
        backupPrefs = backupPrefs,
    )
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
}

sealed interface LaunchAction {
    data class NewNote(val prefill: String = "") : LaunchAction
    data object NewList : LaunchAction
    data class OpenNote(val id: Long) : LaunchAction
}
