package dev.bikram.remember.ui.help

data class HelpSection(val title: String, val subsections: List<HelpSubsection>)

data class HelpSubsection(val title: String, val body: String)

sealed class HelpAction {
    abstract val label: String
    data class OpenAppSection(override val label: String, val sectionKey: String) : HelpAction()
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
            line.startsWith("## ") -> { flushSection(); sectionTitle = line.removePrefix("## ").trim() }
            line.startsWith("### ") -> { flushSubsection(); subsectionTitle = line.removePrefix("### ").trim() }
            line == "---" -> Unit
            else -> if (subsectionTitle != null) bodyLines.add(line)
        }
    }
    flushSection()
    return sections
}

val helpSubsectionActions: Map<String, List<HelpAction>> = mapOf(
    "Notification permission and reliability" to listOf(
        HelpAction.OpenAppSection("Open notification settings", "notifications"),
    ),
    "Troubleshooting reminders" to listOf(
        HelpAction.OpenAppSection("Open notification settings", "notifications"),
    ),
    "Keep reminders until done" to listOf(
        HelpAction.OpenAppSection("Go to Reminders settings", "notifications.keep_until_done"),
    ),
    "What a backup includes" to listOf(
        HelpAction.OpenAppSection("Go to Backup settings", "backup"),
    ),
    "App lock" to listOf(
        HelpAction.OpenAppSection("Go to Security settings", "security"),
    ),
)
