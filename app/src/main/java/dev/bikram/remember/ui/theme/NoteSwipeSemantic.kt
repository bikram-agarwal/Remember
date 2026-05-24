package dev.bikram.remember.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.bikram.remember.data.NoteSwipeAction

fun NoteSwipeAction.swipeActionAccent(): Color =
    when (this) {
        NoteSwipeAction.EDIT -> Color(0xFF3F7AF6)
        NoteSwipeAction.DUPLICATE -> Color(0xFF7E57C2)
        NoteSwipeAction.TOGGLE_STAR -> Color(0xFFFFC107)
        NoteSwipeAction.MARK_DONE -> Color(0xFF2E7D32)
        NoteSwipeAction.ARCHIVE -> Color(0xFF5F6F82)
        NoteSwipeAction.TRASH -> Color(0xFFE53935)
    }

@Composable
fun NoteSwipeAction.semanticSwipeBackground(): Color =
    when (this) {
        NoteSwipeAction.EDIT -> Color(0xFF0A3050)
        NoteSwipeAction.DUPLICATE -> Color(0xFF33215F)
        NoteSwipeAction.TOGGLE_STAR -> Color(0xFF4D3E00)
        NoteSwipeAction.MARK_DONE -> Color(0xFF0F3D1A)
        NoteSwipeAction.ARCHIVE -> Color(0xFF3F3F3F)
        NoteSwipeAction.TRASH -> Color(0xFF5C1414)
    }

@Composable
fun NoteSwipeAction.semanticSwipeIconTint(): Color =
    when (this) {
        NoteSwipeAction.EDIT -> Color(0xFF9ECAFF)
        NoteSwipeAction.DUPLICATE -> Color(0xFFD0BCFF)
        NoteSwipeAction.TOGGLE_STAR -> Color(0xFFFFE082)
        NoteSwipeAction.MARK_DONE -> Color(0xFFA3D9B0)
        NoteSwipeAction.ARCHIVE -> Color(0xFFCAC4D0)
        NoteSwipeAction.TRASH -> Color(0xFFF2B8B5)
    }
