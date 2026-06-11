package dev.bikram.remember.data

import androidx.compose.runtime.Immutable

enum class SortKey { LAST_MODIFIED, CREATED, REMINDER }

enum class SortDir { ASC, DESC }

enum class GroupBy { DATE, NONE, TAG, TYPE }

@Immutable
data class ViewOptions(
    // Default to Reminder ascending so the task-first sectioning (Today / Upcoming /
    // No date) fires out of the box. Existing users who have already set a different
    // sort keep theirs - this only takes effect for fresh prefs.
    val sortKey: SortKey = SortKey.REMINDER,
    val sortDir: SortDir = SortDir.ASC,
    val groupBy: GroupBy = GroupBy.DATE,
    val settingsCollapsedSectionKeys: List<String> = emptyList(),
)
