package dev.bikram.remember.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for the app's responsive breakpoints. Screens used to each re-derive
 * "is this a short landscape window?" inline - some from [LocalConfiguration] and some from
 * `BoxWithConstraints.maxHeight` - which let the same device be classified differently on
 * different screens. Everything now routes through the helpers below so the threshold and the
 * measurement stay consistent.
 */

/** Below this many dp of usable height in landscape, screens switch to space-saving layouts. */
const val SMALL_LANDSCAPE_HEIGHT_DP = 480

@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * True in landscape on a short window (e.g. most phones rotated, or a small split-screen pane),
 * where screens drop to compact spacing / two-column / smaller controls.
 */
@Composable
@ReadOnlyComposable
fun isSmallLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenHeightDp < SMALL_LANDSCAPE_HEIGHT_DP
}

/** Vertical gaps between an empty-state illustration, its title, and its subtitle. */
@Immutable
data class EmptyStateSpacing(
    val titleSpacer: Dp,
    val subtitleSpacer: Dp,
)

/**
 * Spacing ladder shared by every empty state (home + history) so they stay in lockstep instead of
 * each hardcoding the same dp values. Gaps tighten on short landscape windows.
 *
 * @param prominent the first-run "pristine vault" hero state uses larger gaps; the secondary
 *   "no results" / "empty shelf" states use the tighter set.
 */
@Composable
@ReadOnlyComposable
fun emptyStateSpacing(prominent: Boolean): EmptyStateSpacing {
    val small = isSmallLandscape()
    return if (prominent) {
        EmptyStateSpacing(
            titleSpacer = if (small) 12.dp else 24.dp,
            subtitleSpacer = if (small) 4.dp else 8.dp,
        )
    } else {
        EmptyStateSpacing(
            titleSpacer = if (small) 10.dp else 18.dp,
            subtitleSpacer = if (small) 4.dp else 6.dp,
        )
    }
}
