package dev.bikram.remember.di

sealed interface LaunchAction {
    data class NewNote(
        val prefill: String = "",
    ) : LaunchAction

    data object NewList : LaunchAction

    data class OpenNote(
        val id: Long,
        /**
         * True when the request came from outside the app (reminder notification, widget) rather than
         * from in-app navigation, so the note has to be opened on a back stack of its own making.
         */
        val externalLaunch: Boolean = false,
        /** Open straight into edit mode instead of view mode. */
        val forceEdit: Boolean = false,
    ) : LaunchAction

    /** Bring the Notes home screen to the front (after an import that has no single note to open). */
    data object ShowNotesHome : LaunchAction

    data object OpenSettingsUpdates : LaunchAction

    data class OpenTaskImport(
        val jsonText: String,
    ) : LaunchAction
}
