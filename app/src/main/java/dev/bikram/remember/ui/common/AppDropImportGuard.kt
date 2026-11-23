package dev.bikram.remember.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Switches off Remember's window-wide drag-and-drop import (and its overlay) while a note or
 * checklist is in edit mode. Text dragged in from another app then lands in the note's text field
 * without the import overlay covering it, and a stray file drop can't import, and open the new
 * note, in the middle of an edit. A file dropped onto the text itself is ignored by the field.
 *
 * Counted rather than a flag: during a navigation transition two editors can be composed at once.
 */
@Stable
internal class AppDropImportGuard {
    private var editingEditorCount by mutableIntStateOf(0)

    val suppressed: Boolean get() = editingEditorCount > 0

    fun acquire() {
        editingEditorCount++
    }

    fun release() {
        editingEditorCount = (editingEditorCount - 1).coerceAtLeast(0)
    }
}

/** Null outside the main window (previews, tests), where there is no drop target to guard. */
internal val LocalAppDropImportGuard = staticCompositionLocalOf<AppDropImportGuard?> { null }

/**
 * Keeps drag-and-drop import off while the calling editor is in edit mode on an editable note
 * (not archived or trashed) and this call is composed.
 */
@Composable
internal fun SuppressAppDropImportWhileEditing(
    isEditMode: Boolean,
    readOnly: Boolean,
) {
    val guard = LocalAppDropImportGuard.current ?: return
    val editing = isEditMode && !readOnly
    DisposableEffect(guard, editing) {
        if (editing) guard.acquire()
        onDispose {
            if (editing) guard.release()
        }
    }
}
