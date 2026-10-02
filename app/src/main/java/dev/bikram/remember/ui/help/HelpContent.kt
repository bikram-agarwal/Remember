package dev.bikram.remember.ui.help

import androidx.annotation.StringRes
import dev.bikram.remember.R

data class HelpSection(
    val title: String,
    val subsections: List<HelpSubsection>,
)

data class HelpSubsection(
    val title: String,
    val body: String,
)

sealed class HelpAction {
    @get:StringRes
    abstract val labelRes: Int

    data class OpenAppSection(
        @get:StringRes override val labelRes: Int,
        val sectionKey: String,
    ) : HelpAction()
}

fun parseHelpContent(markdown: String): List<HelpSection> {
    val sections = mutableListOf<HelpSection>()
    var sectionTitle: String? = null
    val subsections = mutableListOf<HelpSubsection>()
    var subsectionTitle: String? = null
    val bodyLines = mutableListOf<String>()

    fun flushSubsection() {
        val t = subsectionTitle ?: return
        val body = bodyLines.joinToString("\n").trim()
        if (body.isNotEmpty()) subsections.add(HelpSubsection(t, body))
        subsectionTitle = null
        bodyLines.clear()
    }

    fun flushSection() {
        flushSubsection()
        val t = sectionTitle ?: return
        if (subsections.isNotEmpty()) sections.add(HelpSection(t, subsections.toList()))
        sectionTitle = null
        subsections.clear()
    }

    for (line in markdown.lines()) {
        when {
            line.startsWith("# ") -> Unit
            line.startsWith("## ") -> {
                flushSection()
                sectionTitle = line.removePrefix("## ").trim()
            }
            line.startsWith("### ") -> {
                flushSubsection()
                subsectionTitle = line.removePrefix("### ").trim()
            }
            line == "---" -> Unit
            else -> if (subsectionTitle != null) bodyLines.add(line)
        }
    }
    flushSection()
    return sections
}

/** Focus ids other screens pass to open Help at one subsection; see `Routes.help`. */
const val HELP_FOCUS_VISIBILITY = "visibility"
const val HELP_FOCUS_IMPORTANCE = "importance"

/**
 * The subsection each focus id opens, keyed by title like [helpSubsectionActions]. Renaming one
 * of these headings in docs/HELP.md means updating it here; HelpLinksTest fails until you do.
 */
val helpFocusSubsectionTitles: Map<String, String> =
    mapOf(
        HELP_FOCUS_VISIBILITY to "Visibility: Normal, Private, and Secret",
        HELP_FOCUS_IMPORTANCE to "Importance: Low, Normal, High, and Critical",
    )

/** Where a subsection sits in [HelpScreen]'s list, and the key that expands it. */
internal data class HelpFocusTarget(
    val listIndex: Int,
    val expandedKey: String,
)

/**
 * Locates [subsectionTitle] in the list [HelpScreen] builds from [sections]: the search field,
 * then each section's label followed by its subsections. Keep in step with that list.
 */
internal fun helpFocusTarget(
    sections: List<HelpSection>,
    subsectionTitle: String,
): HelpFocusTarget? {
    var index = 1 // The search field.
    for (section in sections) {
        index += 1 // The section label.
        section.subsections.forEachIndexed { position, subsection ->
            if (subsection.title == subsectionTitle) {
                return HelpFocusTarget(index + position, "${section.title}/${subsection.title}")
            }
        }
        index += section.subsections.size
    }
    return null
}

/**
 * Buttons under a subsection, keyed by its exact title in docs/HELP.md; an unmatched key shows no
 * button. HelpLinksTest fails if a key or a HELP_KEYWORDS.txt target is not a real heading.
 */
val helpSubsectionActions: Map<String, List<HelpAction>> =
    mapOf(
        "Notification permission and reliability" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_notification_settings, "notifications"),
            ),
        "Troubleshooting reminders" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_notification_settings, "notifications"),
            ),
        "Restore notifications" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_reminder_settings, "notifications.keep_until_done"),
            ),
        "What a backup includes" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_backup_settings, "backup"),
            ),
        "App lock" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_security_settings, "security"),
            ),
        "How reminders work" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_snooze_settings, "notifications.snooze_type"),
            ),
        "Snooze" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_snooze_settings, "notifications.snooze_type"),
            ),
        "Defaults" to
            listOf(
                HelpAction.OpenAppSection(R.string.help_action_defaults_settings, "defaults"),
            ),
    )
