package dev.bikram.remember.ui.help

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens Help at a subsection, given one of the HELP_FOCUS_* ids. Provided once around the nav
 * host because the editor that needs it is nested under several hosts (its own route, and the
 * Notes and History panes), where threading a callback would touch every layer in between.
 * Null where no navigation is available; callers hide their help button then.
 */
val LocalOpenHelp = staticCompositionLocalOf<((focusSection: String) -> Unit)?> { null }
