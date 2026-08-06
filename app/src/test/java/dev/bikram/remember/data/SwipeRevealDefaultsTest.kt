package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal-mode slot editor can only *swap* two occupied slots - it has no palette to drag an
 * unused action in from. So the default layout is the only thing that decides which actions are
 * reachable on swipe, and these are the invariants that keeps honest.
 */
class SwipeRevealDefaultsTest {
    private val defaultSlots =
        (DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS + DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS)
            .filterNotNull()

    @Test
    fun defaults_fill_every_slot_with_no_repeats() {
        assertEquals(SLOT_COUNT, defaultSlots.size)
        assertEquals(defaultSlots.size, defaultSlots.distinct().size)
    }

    @Test
    fun default_layout_is_pin_star_duplicate_then_done_archive_trash() {
        assertEquals(
            listOf(NoteSwipeAction.TOGGLE_PIN, NoteSwipeAction.TOGGLE_STAR, NoteSwipeAction.DUPLICATE),
            DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS.filterNotNull(),
        )
        assertEquals(
            listOf(NoteSwipeAction.MARK_DONE, NoteSwipeAction.ARCHIVE, NoteSwipeAction.TRASH),
            DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS.filterNotNull(),
        )
    }

    @Test
    fun edit_is_the_only_action_left_out_of_the_reveal_defaults() {
        // If a future action is added without revisiting the defaults, it silently becomes
        // unreachable on swipe. This test is the tripwire for that.
        val omitted = NoteSwipeAction.entries.filterNot { it in defaultSlots }

        assertEquals(listOf(NoteSwipeAction.EDIT), omitted)
    }

    @Test
    fun default_slots_match_the_canonical_action_order() {
        // SettingsSwipeSection lists actions (direct mode) and back-fills empty reveal slots from
        // one canonical order; its first six entries have to BE the default layout, or the two
        // swipe modes would present actions in different orders.
        val canonicalOrder =
            listOf(
                NoteSwipeAction.TOGGLE_PIN,
                NoteSwipeAction.TOGGLE_STAR,
                NoteSwipeAction.DUPLICATE,
                NoteSwipeAction.MARK_DONE,
                NoteSwipeAction.ARCHIVE,
                NoteSwipeAction.TRASH,
                NoteSwipeAction.EDIT,
            )

        assertEquals(canonicalOrder.take(SLOT_COUNT), defaultSlots)
        assertEquals(NoteSwipeAction.entries.size, canonicalOrder.size)
        assertEquals(NoteSwipeAction.EDIT, canonicalOrder.last())
    }

    @Test
    fun pin_is_reachable_on_swipe_out_of_the_box() {
        assertTrue(NoteSwipeAction.TOGGLE_PIN in defaultSlots)
    }

    @Test
    fun direct_mode_right_swipe_default_matches_the_first_reveal_slot() {
        assertEquals(
            DEFAULT_SWIPE_START_TO_END_REVEAL_ACTIONS.first(),
            DEFAULT_SWIPE_START_TO_END_ACTION,
        )
        assertEquals(NoteSwipeAction.TOGGLE_PIN, DEFAULT_SWIPE_START_TO_END_ACTION)
    }

    @Test
    fun direct_mode_left_swipe_default_stays_trash_and_does_not_follow_the_reveal_slots() {
        // Intentional asymmetry, documented in InteractionPrefs: direct mode fires with no
        // confirmation step, and Trash is its long-standing left default. This asserts the
        // divergence on purpose so nobody "restores symmetry" with the reveal defaults.
        assertEquals(NoteSwipeAction.TRASH, DEFAULT_SWIPE_END_TO_START_ACTION)
        assertEquals(NoteSwipeAction.MARK_DONE, DEFAULT_SWIPE_END_TO_START_REVEAL_ACTIONS.first())
    }

    @Test
    fun default_interaction_state_uses_the_documented_defaults() {
        val state = InteractionState()

        assertEquals(NoteSwipeAction.TOGGLE_PIN, state.swipeStartToEnd)
        assertEquals(NoteSwipeAction.TRASH, state.swipeEndToStart)
        assertEquals(defaultSlots.take(3), state.swipeStartToEndRevealActions.filterNotNull())
    }

    private companion object {
        /** Three slots per direction, matching the reveal editor. */
        const val SLOT_COUNT = 6
    }
}
