package dev.bikram.remember.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.data.NoteSwipeAction

@Composable
fun NoteSwipeAction.semanticSwipeBackground(): Color = when (this) {
    NoteSwipeAction.EDIT -> MaterialTheme.colorScheme.primaryContainer
    NoteSwipeAction.TRASH -> MaterialTheme.colorScheme.errorContainer
    NoteSwipeAction.DUPLICATE -> MaterialTheme.colorScheme.secondaryContainer
    NoteSwipeAction.TOGGLE_PIN -> MaterialTheme.colorScheme.tertiaryContainer
    // Mark-done uses primaryContainer like edit - the visual signal is "task progress",
    // a positive primary action.
    NoteSwipeAction.MARK_DONE -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
fun NoteSwipeAction.semanticSwipeIconTint(): Color = when (this) {
    NoteSwipeAction.EDIT -> MaterialTheme.colorScheme.onPrimaryContainer
    NoteSwipeAction.TRASH -> MaterialTheme.colorScheme.onErrorContainer
    NoteSwipeAction.DUPLICATE -> MaterialTheme.colorScheme.onSecondaryContainer
    NoteSwipeAction.TOGGLE_PIN -> MaterialTheme.colorScheme.onTertiaryContainer
    NoteSwipeAction.MARK_DONE -> MaterialTheme.colorScheme.onPrimaryContainer
}
