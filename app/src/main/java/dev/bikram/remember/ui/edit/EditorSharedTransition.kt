package dev.bikram.remember.ui.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.bikram.remember.ui.nav.noteMorphContainer

/**
 * Editor half of the card <-> editor container morph, shared by the note and list editors.
 *
 * Both halves of the morph go through [noteMorphContainer] so they can't drift apart. The clip
 * matches the list card's `shapes.medium`: without it the overlay renders rounded corners
 * throughout the animation and then pops to square the instant it hands off to this Box.
 */
@Composable
internal fun EditorMorphContainer(
    noteId: Long?,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .noteMorphContainer(noteId)
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium),
        content = content,
    )
}
