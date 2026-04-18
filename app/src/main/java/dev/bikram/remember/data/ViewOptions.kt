package dev.bikram.remember.data

enum class SortKey { LAST_MODIFIED, CREATED, REMINDER }

enum class SortDir { ASC, DESC }

enum class GroupBy { NONE, TAG, TYPE }

data class ViewOptions(
    val sortKey: SortKey = SortKey.LAST_MODIFIED,
    val sortDir: SortDir = SortDir.DESC,
    val groupBy: GroupBy = GroupBy.NONE,
)
