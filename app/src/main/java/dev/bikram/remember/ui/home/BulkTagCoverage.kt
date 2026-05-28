package dev.bikram.remember.ui.home

import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.normalizeTagName

internal enum class BulkTagCoverageState {
    ALL,
    SOME,
    NONE,
}

internal data class BulkTagCoverage(
    val tag: String,
    val matchCount: Int,
    val totalCount: Int,
    val state: BulkTagCoverageState,
)

internal fun buildBulkTagCoverage(
    availableTags: List<String>,
    selectedNoteTags: List<List<String>>,
): List<BulkTagCoverage> {
    val tagLabelsByKey = LinkedHashMap<String, String>()
    availableTags.forEach { tag ->
        val trimmedTag = tag.trim()
        if (trimmedTag.isBlank() || RememberReservedTags.isSuggestionReserved(trimmedTag)) return@forEach
        tagLabelsByKey.putIfAbsent(normalizeTagName(trimmedTag), trimmedTag)
    }
    selectedNoteTags.forEach { tags ->
        tags.forEach { tag ->
            val trimmedTag = tag.trim()
            if (trimmedTag.isBlank() || RememberReservedTags.isSuggestionReserved(trimmedTag)) return@forEach
            tagLabelsByKey.putIfAbsent(normalizeTagName(trimmedTag), trimmedTag)
        }
    }

    val selectedTagKeys =
        selectedNoteTags.map { tags ->
            tags
                .asSequence()
                .map { tag -> tag.trim() }
                .filter { tag -> tag.isNotBlank() && !RememberReservedTags.isSuggestionReserved(tag) }
                .map { tag -> normalizeTagName(tag) }
                .toSet()
        }

    return tagLabelsByKey
        .map { (tagKey, tagLabel) ->
            val matchCount = selectedTagKeys.count { tags -> tagKey in tags }
            BulkTagCoverage(
                tag = tagLabel,
                matchCount = matchCount,
                totalCount = selectedTagKeys.size,
                state =
                    when {
                        matchCount == 0 -> BulkTagCoverageState.NONE
                        matchCount == selectedTagKeys.size -> BulkTagCoverageState.ALL
                        else -> BulkTagCoverageState.SOME
                    },
            )
        }.sortedBy { coverage -> coverage.tag.lowercase() }
}
