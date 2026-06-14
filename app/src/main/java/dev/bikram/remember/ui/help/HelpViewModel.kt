package dev.bikram.remember.ui.help

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HelpViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ViewModel() {
        val sections: List<HelpSection> =
            context.assets.open("HELP.md").bufferedReader().use { reader ->
                parseHelpContent(reader.readText())
            }

        // Map of lowercase phrase -> set of subsection titles to highlight
        private val keywordMap: Map<String, Set<String>> =
            context.assets.open("HELP_KEYWORDS.txt").bufferedReader().use { reader ->
                buildMap {
                    reader
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { line ->
                            val arrow = line.indexOf("->")
                            if (arrow < 0) return@forEach
                            val phrase = line.substring(0, arrow).trim().lowercase()
                            val titles =
                                line
                                    .substring(arrow + 2)
                                    .split("|")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                            if (phrase.isNotEmpty() && titles.isNotEmpty()) {
                                put(phrase, titles)
                            }
                        }
                }
            }

        private val _expandedKeys = MutableStateFlow(emptySet<String>())
        val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        val filteredSections: StateFlow<List<HelpSection>> =
            _searchQuery
                .debounce(300L)
                .map { query ->
                    if (query.isBlank()) {
                        sections
                    } else {
                        val q = query.trim().lowercase()
                        // Keyword map: collect all subsection titles matched by any phrase in the query
                        val keywordHits: Set<String> =
                            keywordMap.entries
                                .filter { (phrase, _) -> q.contains(phrase) }
                                .flatMapTo(mutableSetOf()) { (_, titles) -> titles }
                        val tokens =
                            q
                                .split(Regex("\\s+"))
                                .filter { it.length >= 3 && it !in searchStopwords }
                        sections.mapNotNull { section ->
                            val matching =
                                section.subsections.filter { sub ->
                                    sub.title in keywordHits || subsectionMatchesQuery(sub, q, tokens)
                                }
                            if (matching.isEmpty()) null else section.copy(subsections = matching)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sections)

        var scrollIndex: Int = 0
            private set
        var scrollOffset: Int = 0
            private set

        fun setExpanded(
            key: String,
            expanded: Boolean,
        ) {
            _expandedKeys.value =
                if (expanded) _expandedKeys.value + key else _expandedKeys.value - key
        }

        fun expandAll(keys: Collection<String>) {
            _expandedKeys.value = _expandedKeys.value + keys
        }

        fun collapseAll(keys: Collection<String>) {
            _expandedKeys.value = _expandedKeys.value - keys.toSet()
        }

        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        fun saveScrollState(
            index: Int,
            offset: Int,
        ) {
            scrollIndex = index
            scrollOffset = offset
        }

        private fun subsectionMatchesQuery(
            sub: HelpSubsection,
            q: String,
            tokens: List<String>,
        ): Boolean {
            val content = "${sub.title} ${sub.body}".lowercase()
            // Exact substring match first (handles "troubleshooting", "backup", etc.)
            if (content.contains(q)) return true
            if (tokens.isEmpty()) return false
            val words = content.split(Regex("[\\s\\n\\r,.:;!?()\\[\\]\"]+"))
            return when {
                // Single token: word-prefix OR — "reminder" matches "reminders"
                tokens.size == 1 -> words.any { word -> word.startsWith(tokens[0]) }
                // Multiple tokens: word-prefix AND — all must appear; avoids irrelevant matches
                else -> tokens.all { token -> words.any { word -> word.startsWith(token) } }
            }
        }

        companion object {
            private val searchStopwords =
                setOf(
                    "not",
                    "the",
                    "and",
                    "for",
                    "are",
                    "but",
                    "can",
                    "how",
                    "why",
                    "its",
                    "you",
                    "was",
                    "has",
                    "did",
                    "this",
                    "that",
                    "with",
                    "have",
                    "will",
                    "your",
                    "any",
                    "all",
                    "also",
                    "from",
                    "into",
                    "when",
                    "then",
                    "than",
                    "them",
                    "they",
                    "just",
                    "been",
                )
        }
    }
