package dev.bikram.remember.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.bikram.remember.data.BackupIo
import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.DevModePrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.update.AppReviewLauncher
import dev.bikram.remember.update.PlayInAppUpdateProgressController
import dev.bikram.remember.update.PlayInAppUpdateStarter
import dev.bikram.remember.update.PlayStoreUpdateChecker
import dev.bikram.remember.update.PlayUpdateSessionHandle
import dev.bikram.remember.update.RememberUpdateChecker
import dev.bikram.remember.update.RememberUpdateState
import dev.bikram.remember.update.UpdateAvailableNotifier
import dev.bikram.remember.update.UpdateCheckWorkScheduler
import dev.bikram.remember.widget.NotesWidgetUpdater

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsDependenciesEntryPoint {
    fun devModePrefs(): DevModePrefs

    fun lockPrefs(): LockPrefs

    fun interactionPrefs(): InteractionPrefs

    fun quickCapturePrefs(): QuickCapturePrefs

    fun reminderPrefs(): ReminderPrefs

    fun backupPrefs(): BackupPrefs

    fun backupIo(): BackupIo

    fun themePrefs(): ThemePrefs

    fun noteRepository(): NoteRepository

    fun updatePrefs(): UpdatePrefs

    fun rememberUpdateChecker(): RememberUpdateChecker

    fun playStoreUpdateChecker(): PlayStoreUpdateChecker

    fun playInAppUpdateStarter(): PlayInAppUpdateStarter

    fun playInAppUpdateProgressController(): PlayInAppUpdateProgressController

    fun playUpdateSessionHandle(): PlayUpdateSessionHandle

    fun rememberUpdateState(): RememberUpdateState

    fun updateAvailableNotifier(): UpdateAvailableNotifier

    fun updateCheckWorkScheduler(): UpdateCheckWorkScheduler

    fun appReviewLauncher(): AppReviewLauncher
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotesWidgetEntryPoint {
    fun noteRepository(): NoteRepository

    fun notesWidgetUpdater(): NotesWidgetUpdater

    fun viewOptionsPrefs(): ViewOptionsPrefs
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevOptionsDependenciesEntryPoint {
    fun devModePrefs(): DevModePrefs

    fun onboardingPrefs(): OnboardingPrefs

    fun updatePrefs(): UpdatePrefs

    fun noteRepository(): NoteRepository

    fun notesWidgetUpdater(): NotesWidgetUpdater

    fun reminderScheduler(): ReminderScheduler

    fun reminderPrefs(): ReminderPrefs

    fun themePrefs(): ThemePrefs

    fun viewOptionsPrefs(): ViewOptionsPrefs

    fun interactionPrefs(): InteractionPrefs

    fun quickCapturePrefs(): QuickCapturePrefs

    fun lockPrefs(): LockPrefs

    fun backupPrefs(): BackupPrefs

    fun rememberUpdateState(): RememberUpdateState
}
