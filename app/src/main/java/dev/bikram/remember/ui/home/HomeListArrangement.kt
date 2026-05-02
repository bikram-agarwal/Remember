package dev.bikram.remember.ui.home

import dev.bikram.remember.R
import dev.bikram.remember.data.GroupBy
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.SortDir
import dev.bikram.remember.data.SortKey
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.ui.components.toNoteCardUiModel
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lay out the home list with the task-first section model:
 *
 *   [Overdue]            - always pinned at top when non-empty.
 *   [middle]             - date sub-sections or the selected grouping mode.
 *   [Done] (collapsible) - always pinned at bottom when non-empty.
 */
internal fun arrangeItems(
    notes: List<NoteWithItems>,
    opts: ViewOptions,
): List<HomeListItem> {
    val now = System.currentTimeMillis()
    val sortedNotes = sortNotes(notes, opts)

    val doneNotes = sortedNotes.filter { it.note.completedAt != null }
    val activeNotes = sortedNotes.filter { it.note.completedAt == null }
    val overdueNotes =
        activeNotes.filter { noteWithItems ->
            val reminderAt = noteWithItems.note.reminderAt
            reminderAt != null && reminderAt < now
        }
    val remainingActiveNotes =
        activeNotes.filterNot { noteWithItems ->
            val reminderAt = noteWithItems.note.reminderAt
            reminderAt != null && reminderAt < now
        }

    return buildList {
        if (overdueNotes.isNotEmpty()) {
            add(
                HomeListItem.Header(
                    label = "",
                    count = overdueNotes.size,
                    stableKey = "OVERDUE",
                    labelRes = R.string.home_section_overdue,
                ),
            )
            overdueNotes.forEach { noteWithItems ->
                add(
                    HomeListItem.NoteRow(
                        note = noteWithItems,
                        card = noteWithItems.toNoteCardUiModel(),
                        groupKey = "OVERDUE",
                    ),
                )
            }
        }
        addAll(arrangeMiddle(remainingActiveNotes, opts))
        if (doneNotes.isNotEmpty()) {
            add(
                HomeListItem.Header(
                    label = "",
                    count = doneNotes.size,
                    stableKey = "DONE",
                    labelRes = R.string.home_section_done,
                ),
            )
            doneNotes.forEach { noteWithItems ->
                add(
                    HomeListItem.NoteRow(
                        note = noteWithItems,
                        card = noteWithItems.toNoteCardUiModel(),
                        groupKey = "DONE",
                    ),
                )
            }
        }
    }
}

/**
 * The "middle" between Overdue and Done. With Group by Date we explode this into
 * Today / Upcoming / No date sections. Otherwise, we use the selected group mode.
 */
private fun arrangeMiddle(
    activeNotes: List<NoteWithItems>,
    opts: ViewOptions,
): List<HomeListItem> {
    if (opts.groupBy != GroupBy.DATE) {
        val groupedItems = arrangeByGroupBy(activeNotes, opts)
        return if (opts.groupBy == GroupBy.NONE && groupedItems.isNotEmpty()) {
            buildList {
                add(
                    HomeListItem.Header(
                        label = "",
                        count = activeNotes.size,
                        stableKey = "ACTIVE",
                        labelRes = R.string.home_section_active,
                    ),
                )
                addAll(groupedItems)
            }
        } else {
            groupedItems
        }
    }

    val zone = ZoneId.systemDefault()
    val tomorrowMidnightMillis =
        ZonedDateTime
            .now(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    val todayNotes = mutableListOf<NoteWithItems>()
    val upcomingNotes = mutableListOf<NoteWithItems>()
    val noDateNotes = mutableListOf<NoteWithItems>()
    activeNotes.forEach { noteWithItems ->
        val reminderAt = noteWithItems.note.reminderAt
        when {
            reminderAt == null -> noDateNotes += noteWithItems
            reminderAt < tomorrowMidnightMillis -> todayNotes += noteWithItems
            else -> upcomingNotes += noteWithItems
        }
    }

    data class SectionDef(
        val label: String,
        val key: String,
        val items: List<NoteWithItems>,
    )

    val ascendingSections =
        listOf(
            SectionDef("", "TODAY", todayNotes),
            SectionDef("", "UPCOMING", upcomingNotes),
            SectionDef("", "NO_DATE", noDateNotes),
        )
    val sectionLabelRes =
        mapOf(
            "TODAY" to R.string.home_section_today,
            "UPCOMING" to R.string.home_section_upcoming,
            "NO_DATE" to R.string.home_section_no_date,
        )
    val orderedSections =
        if (opts.sortDir == SortDir.ASC) {
            ascendingSections
        } else {
            ascendingSections.reversed()
        }

    return buildList {
        orderedSections.forEach { section ->
            if (section.items.isNotEmpty()) {
                add(
                    HomeListItem.Header(
                        label = section.label,
                        count = section.items.size,
                        stableKey = section.key,
                        labelRes = sectionLabelRes[section.key],
                    ),
                )
                section.items.forEach { noteWithItems ->
                    add(
                        HomeListItem.NoteRow(
                            note = noteWithItems,
                            card = noteWithItems.toNoteCardUiModel(),
                            groupKey = section.key,
                        ),
                    )
                }
            }
        }
    }
}

/** Existing GroupBy logic, scoped to the active middle section. */
private fun arrangeByGroupBy(
    activeNotes: List<NoteWithItems>,
    opts: ViewOptions,
): List<HomeListItem> =
    when (opts.groupBy) {
        GroupBy.DATE ->
            activeNotes.map { noteWithItems ->
                HomeListItem.NoteRow(
                    note = noteWithItems,
                    card = noteWithItems.toNoteCardUiModel(),
                )
            }
        GroupBy.NONE ->
            activeNotes.map { noteWithItems ->
                HomeListItem.NoteRow(
                    note = noteWithItems,
                    card = noteWithItems.toNoteCardUiModel(),
                    groupKey = "ACTIVE",
                )
            }
        GroupBy.TYPE -> arrangeByType(activeNotes)
        GroupBy.TAG -> arrangeByTag(activeNotes)
    }

private fun arrangeByType(activeNotes: List<NoteWithItems>): List<HomeListItem> {
    val notesOnly = activeNotes.filter { it.note.kind == NoteKind.NOTE }
    val listsOnly = activeNotes.filter { it.note.kind == NoteKind.LIST }
    return buildList {
        if (notesOnly.isNotEmpty()) {
            add(
                HomeListItem.Header(
                    label = "",
                    count = notesOnly.size,
                    stableKey = "TYPE_NOTE",
                    labelRes = R.string.home_section_notes,
                ),
            )
            notesOnly.forEach { noteWithItems ->
                add(
                    HomeListItem.NoteRow(
                        note = noteWithItems,
                        card = noteWithItems.toNoteCardUiModel(),
                        groupKey = "TYPE_NOTE",
                    ),
                )
            }
        }
        if (listsOnly.isNotEmpty()) {
            add(
                HomeListItem.Header(
                    label = "",
                    count = listsOnly.size,
                    stableKey = "TYPE_LIST",
                    labelRes = R.string.home_section_lists,
                ),
            )
            listsOnly.forEach { noteWithItems ->
                add(
                    HomeListItem.NoteRow(
                        note = noteWithItems,
                        card = noteWithItems.toNoteCardUiModel(),
                        groupKey = "TYPE_LIST",
                    ),
                )
            }
        }
    }
}

private fun arrangeByTag(activeNotes: List<NoteWithItems>): List<HomeListItem> {
    val taggedNotes =
        activeNotes.filter { noteWithItems ->
            RememberReservedTags.userVisibleTags(noteWithItems.note.tags).isNotEmpty()
        }
    val untaggedNotes =
        activeNotes.filter { noteWithItems ->
            RememberReservedTags.userVisibleTags(noteWithItems.note.tags).isEmpty()
        }
    val tags =
        taggedNotes
            .flatMap { noteWithItems -> RememberReservedTags.userVisibleTags(noteWithItems.note.tags) }
            .distinct()
            .sorted()

    return buildList {
        tags.forEach { tag ->
            val notesInTag =
                taggedNotes.filter { noteWithItems ->
                    RememberReservedTags
                        .userVisibleTags(noteWithItems.note.tags)
                        .any { tagName -> tagName.equals(tag, ignoreCase = true) }
                }
            if (notesInTag.isNotEmpty()) {
                val sectionKey = "TAG_$tag"
                add(HomeListItem.Header(label = tag, count = notesInTag.size, stableKey = sectionKey))
                notesInTag.forEach { noteWithItems ->
                    add(
                        HomeListItem.NoteRow(
                            note = noteWithItems,
                            card = noteWithItems.toNoteCardUiModel(),
                            groupKey = sectionKey,
                        ),
                    )
                }
            }
        }
        if (untaggedNotes.isNotEmpty()) {
            add(
                HomeListItem.Header(
                    label = "",
                    count = untaggedNotes.size,
                    stableKey = "TAG_UNTAGGED",
                    labelRes = R.string.home_section_untagged,
                ),
            )
            untaggedNotes.forEach { noteWithItems ->
                add(
                    HomeListItem.NoteRow(
                        note = noteWithItems,
                        card = noteWithItems.toNoteCardUiModel(),
                        groupKey = "TAG_UNTAGGED",
                    ),
                )
            }
        }
    }
}

private fun sortNotes(
    notes: List<NoteWithItems>,
    opts: ViewOptions,
): List<NoteWithItems> = notes.sortedWith(buildComparator(opts))

private fun buildComparator(opts: ViewOptions): Comparator<NoteWithItems> {
    val ascendingBase: Comparator<NoteWithItems> =
        when (opts.sortKey) {
            SortKey.LAST_MODIFIED -> compareBy { noteWithItems -> noteWithItems.note.updatedAt }
            SortKey.CREATED -> compareBy { noteWithItems -> noteWithItems.note.createdAt }
            SortKey.REMINDER -> compareBy { noteWithItems -> noteWithItems.note.reminderAt ?: Long.MAX_VALUE }
        }
    val createdTieBreaker: Comparator<NoteWithItems> =
        compareBy<NoteWithItems> { noteWithItems -> noteWithItems.note.createdAt }
            .thenBy { noteWithItems -> noteWithItems.note.id }
    val directedCreatedTieBreaker =
        if (opts.sortDir == SortDir.DESC) {
            createdTieBreaker.reversed()
        } else {
            createdTieBreaker
        }
    val directed =
        if (opts.sortDir == SortDir.DESC) {
            ascendingBase.reversed()
        } else {
            ascendingBase
        }
    return if (opts.sortKey == SortKey.REMINDER) {
        Comparator { firstNote, secondNote ->

            val firstHasNoReminder = firstNote.note.reminderAt == null
            val secondHasNoReminder = secondNote.note.reminderAt == null
            when {
                firstHasNoReminder && secondHasNoReminder -> 0
                firstHasNoReminder -> 1
                secondHasNoReminder -> -1
                else -> directed.compare(firstNote, secondNote)
            }
        }
    } else {
        directed
    }
}
