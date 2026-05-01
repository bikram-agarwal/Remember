package dev.bikram.remember.backup

import dev.bikram.remember.data.BackupPrefs
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import org.json.JSONObject

object SettingsBackup {
    const val KEY_THEME = "theme_prefs"
    const val KEY_VIEW_OPTIONS = "view_options_prefs"
    const val KEY_LOCK = "lock_prefs"
    const val KEY_INTERACTION = "interaction_prefs"
    const val KEY_BACKUP = "backup_prefs"

    suspend fun exportJson(
        themePrefs: ThemePrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
    ): JSONObject =
        JSONObject().apply {
            put(KEY_THEME, themePrefs.exportForBackup())
            put(KEY_VIEW_OPTIONS, viewOptionsPrefs.exportForBackup())
            put(KEY_LOCK, lockPrefs.exportForBackup())
            put(KEY_INTERACTION, interactionPrefs.exportForBackup())
            put(KEY_BACKUP, backupPrefs.exportForBackup())
        }

    suspend fun importJson(
        root: JSONObject?,
        themePrefs: ThemePrefs,
        viewOptionsPrefs: ViewOptionsPrefs,
        lockPrefs: LockPrefs,
        interactionPrefs: InteractionPrefs,
        backupPrefs: BackupPrefs,
    ) {
        if (root == null) return
        themePrefs.importFromBackup(root.optJSONObject(KEY_THEME))
        viewOptionsPrefs.importFromBackup(root.optJSONObject(KEY_VIEW_OPTIONS))
        lockPrefs.importFromBackup(root.optJSONObject(KEY_LOCK))
        interactionPrefs.importFromBackup(root.optJSONObject(KEY_INTERACTION))
        backupPrefs.importFromBackup(root.optJSONObject(KEY_BACKUP))
    }
}
