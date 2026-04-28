package dev.bikram.remember.di

sealed interface LaunchAction {
    data class NewNote(
        val prefill: String = "",
    ) : LaunchAction

    data object NewList : LaunchAction

    data class OpenNote(
        val id: Long,
    ) : LaunchAction
}
