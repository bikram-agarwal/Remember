package dev.bikram.remember.backup

import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.BackupSettingsRestoreOutcome
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.QuickCapturePrefs
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.UpdatePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import org.json.JSONObject

object SettingsBackup {
    const val KEY_THEME = "theme_prefs"
    const val KEY_VIEW_OPTIONS = "view_options_prefs"
    const val KEY_LOCK = "lock_prefs"
    const val KEY_INTERACTION = "interaction_prefs"
    const val KEY_BACKUP = "backup_prefs"
    const val KEY_QUICK_CAPTURE = "quick_capture_prefs"
    const val KEY_REMINDER = "reminder_prefs"
    const val KEY_UPDATE = "update_prefs"

    suspend fun exportJson(
        themePrefs: ThemePrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        updatePrefs: UpdatePrefs,
    ): JSONObject =
        JSONObject().apply {
            put(KEY_THEME, themePrefs.exportForBackup())
            put(KEY_VIEW_OPTIONS, viewOptionsPrefs.exportForBackup())
            put(KEY_LOCK, lockPrefs.exportForBackup())
            put(KEY_INTERACTION, interactionPrefs.exportForBackup())
            put(KEY_BACKUP, backupPrefs.exportForBackup())
            put(KEY_QUICK_CAPTURE, quickCapturePrefs.exportForBackup())
            put(KEY_REMINDER, reminderPrefs.exportForBackup())
            put(KEY_UPDATE, updatePrefs.exportForBackup())
        }

    suspend fun importJson(
        root: JSONObject?,
        themePrefs: ThemePrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
        quickCapturePrefs: QuickCapturePrefs,
        reminderPrefs: ReminderPrefs,
        updatePrefs: UpdatePrefs,
    ): BackupSettingsRestoreOutcome {
        if (root == null) return BackupSettingsRestoreOutcome()
        themePrefs.importFromBackup(root.optJSONObject(KEY_THEME))
        viewOptionsPrefs.importFromBackup(root.optJSONObject(KEY_VIEW_OPTIONS))
        lockPrefs.importFromBackup(root.optJSONObject(KEY_LOCK))
        interactionPrefs.importFromBackup(root.optJSONObject(KEY_INTERACTION))
        val backupRestoreOutcome = backupPrefs.importFromBackup(root.optJSONObject(KEY_BACKUP))
        quickCapturePrefs.importFromBackup(root.optJSONObject(KEY_QUICK_CAPTURE))
        reminderPrefs.importFromBackup(root.optJSONObject(KEY_REMINDER))
        updatePrefs.importFromBackup(root.optJSONObject(KEY_UPDATE))
        return backupRestoreOutcome
    }
}
