package dev.bikram.remember.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.data.NoteSwipeAction

private val DoneSwipeBackground = Color(0xFF2E7D32)
private val DoneSwipeContent = Color.White
private val StarSwipeBackground = Color(0xFF6A561F)

@Composable
fun NoteSwipeAction.semanticSwipeBackground(): Color =
    when (this) {
        NoteSwipeAction.EDIT -> MaterialTheme.colorScheme.primaryContainer
        NoteSwipeAction.TRASH -> MaterialTheme.colorScheme.errorContainer
        NoteSwipeAction.DUPLICATE -> MaterialTheme.colorScheme.secondaryContainer
        NoteSwipeAction.TOGGLE_STAR -> StarSwipeBackground
        NoteSwipeAction.ARCHIVE -> MaterialTheme.colorScheme.secondaryContainer
        NoteSwipeAction.MARK_DONE -> DoneSwipeBackground
    }

@Composable
fun NoteSwipeAction.semanticSwipeIconTint(): Color =
    when (this) {
        NoteSwipeAction.EDIT -> MaterialTheme.colorScheme.onPrimaryContainer
        NoteSwipeAction.TRASH -> MaterialTheme.colorScheme.onErrorContainer
        NoteSwipeAction.DUPLICATE -> MaterialTheme.colorScheme.onSecondaryContainer
        NoteSwipeAction.TOGGLE_STAR -> MaterialTheme.colorScheme.onSecondaryContainer
        NoteSwipeAction.ARCHIVE -> MaterialTheme.colorScheme.onSecondaryContainer
        NoteSwipeAction.MARK_DONE -> DoneSwipeContent
    }
