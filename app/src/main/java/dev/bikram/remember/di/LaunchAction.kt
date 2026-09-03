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
    ) : LaunchAction

    data object OpenSettingsUpdates : LaunchAction
}
