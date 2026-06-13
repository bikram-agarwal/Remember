package dev.bikram.remember.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.bikram.remember.backup.BackupExportCoordinator
import dev.bikram.remember.backup.NoteBackupDirtyTracker
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.DevModePrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.RememberDatabase
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.googletasks.GoogleTasksApi
import dev.bikram.remember.googletasks.GoogleTasksImportPrefs
import dev.bikram.remember.googletasks.GoogleTasksImporter
import dev.bikram.remember.googletasks.GoogleTasksRepository
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.widget.NotesWidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RememberModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): RememberDatabase = RememberDatabase.build(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun provideDevModePrefs(
        @ApplicationContext context: Context,
    ): DevModePrefs = DevModePrefs(context)

    @Provides
    @Singleton
    fun provideReminderPrefs(
        @ApplicationContext context: Context,
    ): ReminderPrefs = ReminderPrefs(context)

    @Provides
    @Singleton
    fun provideReminderScheduler(
        @ApplicationContext context: Context,
        reminderPrefs: ReminderPrefs,
    ): ReminderScheduler = ReminderScheduler(context, reminderPrefs)

    @Provides
    @Singleton
    fun provideThemePrefs(
        @ApplicationContext context: Context,
    ): ThemePrefs = ThemePrefs(context)

    @Provides
    @Singleton
    fun provideViewOptionsPrefs(
        @ApplicationContext context: Context,
    ): ViewOptionsPrefs = ViewOptionsPrefs(context)

    @Provides
    @Singleton
    fun provideTagRepository(
        database: RememberDatabase,
    ): TagRepository =
        TagRepository(
            tagDao = database.tagDao(),
            noteDao = database.noteDao(),
            database = database,
        )

    @Provides
    @Singleton
    fun provideNoteRepository(
        database: RememberDatabase,
        reminderScheduler: ReminderScheduler,
        tagRepository: TagRepository,
        notesWidgetUpdater: NotesWidgetUpdater,
        appMediaStorage: AppMediaStorage,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
    ): NoteRepository =
        NoteRepository(
            noteDao = database.noteDao(),
            itemDao = database.checklistItemDao(),
            attachmentDao = database.attachmentDao(),
            scheduler = reminderScheduler,
            tagRepository = tagRepository,
            notesWidgetUpdater = notesWidgetUpdater,
            database = database,
            appMediaStorage = appMediaStorage,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher,
            applicationScope = applicationScope,
        )

    @Provides
    @Singleton
    fun provideLockPrefs(
        @ApplicationContext context: Context,
    ): LockPrefs = LockPrefs(context)

    @Provides
    @Singleton
    fun provideOnboardingPrefs(
        @ApplicationContext context: Context,
    ): OnboardingPrefs = OnboardingPrefs(context)

    @Provides
    @Singleton
    fun provideInteractionPrefs(
        @ApplicationContext context: Context,
    ): InteractionPrefs = InteractionPrefs(context)

    @Provides
    @Singleton
    fun provideBackupPrefs(
        @ApplicationContext context: Context,
    ): BackupPrefs = BackupPrefs(context)

    @Provides
    @Singleton
    fun provideQuickCapturePrefs(
        @ApplicationContext context: Context,
    ): QuickCapturePrefs = QuickCapturePrefs(context)

    @Provides
    @Singleton
    fun provideUpdatePrefs(
        @ApplicationContext context: Context,
    ): UpdatePrefs = UpdatePrefs(context)

    @Provides
    @Singleton
    fun provideBackupIo(
        @ApplicationContext context: Context,
        noteRepository: NoteRepository,
        themePrefs: ThemePrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        updatePrefs: UpdatePrefs,
    ): BackupIo =
        BackupIo(
            context = context,
            repository = noteRepository,
            themePrefs = themePrefs,
            viewOptionsPrefs = viewOptionsPrefs,
            lockPrefs = lockPrefs,
            interactionPrefs = interactionPrefs,
            backupPrefs = backupPrefs,
            quickCapturePrefs = quickCapturePrefs,
            reminderPrefs = reminderPrefs,
            updatePrefs = updatePrefs,
        )

    @Provides
    @Singleton
    fun provideGoogleTasksImportPrefs(
        @ApplicationContext context: Context,
    ): GoogleTasksImportPrefs = GoogleTasksImportPrefs(context)

    @Provides
    @Singleton
    fun provideGoogleTasksApi(): GoogleTasksApi = GoogleTasksApi()

    @Provides
    @Singleton
    fun provideGoogleTasksRepository(
        @ApplicationContext context: Context,
        api: GoogleTasksApi,
    ): GoogleTasksRepository =
        GoogleTasksRepository(
            context = context,
            api = api,
        )

    @Provides
    @Singleton
    fun provideGoogleTasksImporter(noteRepository: NoteRepository): GoogleTasksImporter = GoogleTasksImporter(repository = noteRepository)

    @Provides
    @Singleton
    fun provideNoteBackupDirtyTracker(): NoteBackupDirtyTracker = NoteBackupDirtyTracker()

    @Provides
    @Singleton
    fun provideBackupExportCoordinator(
        backupPrefs: BackupPrefs,
        backupIo: BackupIo,
        noteRepository: NoteRepository,
        @ApplicationScope applicationScope: CoroutineScope,
        noteBackupDirtyTracker: NoteBackupDirtyTracker,
    ): BackupExportCoordinator =
        BackupExportCoordinator(
            backupPrefs = backupPrefs,
            backupIo = backupIo,
            noteRepository = noteRepository,
            applicationScope = applicationScope,
            noteBackupDirtyTracker = noteBackupDirtyTracker,
        )

    @Provides
    @Singleton
    fun providePendingLaunchFlow(): MutableStateFlow<LaunchAction?> = MutableStateFlow(null)

    @Provides
    @Singleton
    fun provideAppUnlockedFlow(): MutableStateFlow<Boolean> = MutableStateFlow(false)

    @Provides
    @Singleton
    fun provideAppStartupWarmup(
        @ApplicationScope applicationScope: CoroutineScope,
        noteRepository: NoteRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AppStartupWarmup =
        AppStartupWarmup(
            applicationScope = applicationScope,
            noteRepository = noteRepository,
            ioDispatcher = ioDispatcher,
        )
}

class AppStartupWarmup(
    private val applicationScope: CoroutineScope,
    private val noteRepository: NoteRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun start() {
        // Warm the first Home query while the system splash / initial composition is still
        // happening, so the first fullscreen Home frame is less likely to render before Room
        // has produced the active notes list.
        applicationScope.launch(ioDispatcher) {
            noteRepository.observeActive().first()
        }
    }
}
