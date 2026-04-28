package dev.bikram.remember.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Compact accent palette. Used as a small colored dot/strip on a card — not a full
 * background — so color tags stay subordinate to the unified expressive surface.
 */
data class NoteAccent(
    val name: String,
    val light: Color,
    val dark: Color,
)

object NoteColors {
    val palette: List<NoteAccent> =
        listOf(
            NoteAccent("None", Color(0x00000000), Color(0x00000000)),
            NoteAccent("Rose", Color(0xFFE5506A), Color(0xFFE87B8C)),
            NoteAccent("Peach", Color(0xFFE88C3C), Color(0xFFEBA75F)),
            NoteAccent("Sun", Color(0xFFDDB233), Color(0xFFE1C256)),
            NoteAccent("Lime", Color(0xFF85A93C), Color(0xFFA6C05A)),
            NoteAccent("Mint", Color(0xFF3EA88A), Color(0xFF61BAA0)),
            NoteAccent("Sky", Color(0xFF3C8ACC), Color(0xFF61A1D8)),
            NoteAccent("Indigo", Color(0xFF4E5AC8), Color(0xFF7A83D6)),
            NoteAccent("Orchid", Color(0xFF9353C8), Color(0xFFAB76D8)),
            NoteAccent("Stone", Color(0xFF7A6C5D), Color(0xFF9A8B7B)),
        )

    fun at(index: Int): NoteAccent = palette[index.coerceIn(0, palette.lastIndex)]

    fun isNone(index: Int): Boolean = index == 0
}
