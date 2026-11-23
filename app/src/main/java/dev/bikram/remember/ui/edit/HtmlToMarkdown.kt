package dev.bikram.remember.ui.edit

/**
 * Converts clipboard HTML (a copy from a browser, mail, or docs app) into the Markdown subset
 * Remember renders, so pasting keeps the source's formatting:
 * - `#`-`###` headings (h4-h6 become `###`), paragraphs separated by a blank line;
 * - `-` / `1.` lists, `- [ ]` / `- [x]` checklists (GitHub task lists), `>` quotes, `---` rules;
 * - bare fences for `<pre>` (Remember's fence takes no language tag);
 * - bold, italic, strikethrough, underline, and inline code, from tags or inline styles
 *   (Google Docs marks bold with a style, not a tag);
 * - `[text](url)` for absolute links; an image becomes a link to it labelled with its alt text.
 *
 * Remember has no tables, so each row keeps its cell text as `a | b`. Scripts, styles, SVG icons,
 * form controls, and hidden elements are dropped. Returns an empty string when the HTML holds no
 * visible text.
 */
internal fun htmlToMarkdown(html: String): String = HtmlMarkdownRenderer().render(parseHtml(clipboardFragment(html)))

/** Windows-style clipboard HTML marks the copied part; anything around it is page boilerplate. */
private fun clipboardFragment(html: String): String {
    val start = html.indexOf(FRAGMENT_START_MARKER)
    val end = html.indexOf(FRAGMENT_END_MARKER)
    return if (start >= 0 && end > start) html.substring(start + FRAGMENT_START_MARKER.length, end) else html
}

private const val FRAGMENT_START_MARKER = "<!--StartFragment-->"
private const val FRAGMENT_END_MARKER = "<!--EndFragment-->"

/** Deeper elements are flattened into their ancestor, so rendering never recurses unboundedly. */
private const val MAX_ELEMENT_DEPTH = 200

private val VoidTags =
    setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

/** Elements whose content is not markup; it is skipped whole rather than parsed. */
private val RawTextTags = setOf("script", "style", "textarea", "title", "noscript", "template", "xmp")

/** Elements parsed for structure but never rendered (chrome, icons, controls). */
private val SkippedTags =
    setOf(
        "head",
        "svg",
        "math",
        "button",
        "select",
        "option",
        "canvas",
        "video",
        "audio",
        "iframe",
        "object",
        "embed",
        "meta",
        "link",
        "input",
        "label",
        "dialog",
        "tool-tip",
        "clipboard-copy",
    )

private val BlockTags =
    setOf(
        "address",
        "article",
        "aside",
        "blockquote",
        "body",
        "caption",
        "center",
        "dd",
        "details",
        "div",
        "dl",
        "dt",
        "fieldset",
        "figcaption",
        "figure",
        "footer",
        "form",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "header",
        "hgroup",
        "hr",
        "html",
        "li",
        "main",
        "nav",
        "ol",
        "p",
        "pre",
        "section",
        "summary",
        "table",
        "tbody",
        "td",
        "tfoot",
        "th",
        "thead",
        "tr",
        "ul",
    )

private val HeadingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val ListTags = setOf("ul", "ol")
private val CodeTags = setOf("code", "kbd", "samp", "tt")
private val BoldTags = setOf("b", "strong")
private val ItalicTags = setOf("i", "em", "cite", "dfn", "var")
private val UnderlineTags = setOf("u", "ins")
private val StrikethroughTags = setOf("s", "strike", "del")

private const val REMEMBER_MAX_HEADING_LEVEL = 3
private const val NESTED_LIST_INDENT = "  "

// region Parsing

private sealed interface HtmlNode

private class HtmlText(
    val text: String,
) : HtmlNode

private class HtmlElement(
    val tag: String,
    val attributes: Map<String, String>,
) : HtmlNode {
    val children = mutableListOf<HtmlNode>()

    val isSkipped: Boolean by lazy {
        tag in SkippedTags ||
            "hidden" in attributes ||
            attributes["aria-hidden"].equals("true", ignoreCase = true) ||
            styleHidesElement(attributes["style"])
    }

    /** True when any rendered descendant is block-level, so this element lays out as a block. */
    val containsBlock: Boolean by lazy {
        children.any { child -> child is HtmlElement && !child.isSkipped && (child.tag in BlockTags || child.containsBlock) }
    }
}

private val HiddenStyleRegex = Regex("""(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*hidden)""", RegexOption.IGNORE_CASE)

private fun styleHidesElement(style: String?): Boolean = style != null && HiddenStyleRegex.containsMatchIn(style)

private fun parseHtml(html: String): HtmlElement {
    val root = HtmlElement("#root", emptyMap())
    val open = ArrayDeque<HtmlElement>().apply { addLast(root) }
    val text = StringBuilder()

    fun flushText() {
        if (text.isEmpty()) return
        open.last().children += HtmlText(decodeHtmlEntities(text.toString()))
        text.setLength(0)
    }

    var index = 0
    while (index < html.length) {
        val char = html[index]
        if (char != '<') {
            text.append(char)
            index++
            continue
        }
        if (html.startsWith("<!--", index)) {
            val end = html.indexOf("-->", index + 4)
            index = if (end < 0) html.length else end + 3
            continue
        }
        if (html.startsWith("<!", index) || html.startsWith("<?", index)) {
            val end = html.indexOf('>', index)
            index = if (end < 0) html.length else end + 1
            continue
        }
        val closing = html.getOrNull(index + 1) == '/'
        val nameStart = if (closing) index + 2 else index + 1
        var nameEnd = nameStart
        while (nameEnd < html.length && (html[nameEnd].isLetterOrDigit() || html[nameEnd] == '-' || html[nameEnd] == ':')) {
            nameEnd++
        }
        if (nameEnd == nameStart || !html[nameStart].isLetter()) {
            // A literal '<' in text, such as "a < b".
            text.append(char)
            index++
            continue
        }
        val tagEnd = findTagEnd(html, nameEnd)
        if (tagEnd < 0) break
        val tag = html.substring(nameStart, nameEnd).lowercase()
        flushText()
        index = tagEnd + 1
        if (closing) {
            closeElement(open, tag)
            continue
        }
        if (tag in RawTextTags) {
            val close = html.indexOf("</$tag", index, ignoreCase = true)
            index = if (close < 0) html.length else html.indexOf('>', close).let { if (it < 0) html.length else it + 1 }
            continue
        }
        val attributeSource = html.substring(nameEnd, tagEnd)
        val element = HtmlElement(tag, parseAttributes(attributeSource))
        closeImplicitly(open, tag)
        if (open.size > MAX_ELEMENT_DEPTH) continue
        open.last().children += element
        if (tag !in VoidTags && !attributeSource.trimEnd().endsWith("/")) open.addLast(element)
    }
    flushText()
    return root
}

/** Index of the `>` ending a tag that starts before [from], skipping quoted attribute values. */
private fun findTagEnd(
    html: String,
    from: Int,
): Int {
    var quote: Char? = null
    for (index in from until html.length) {
        val char = html[index]
        when {
            quote != null -> if (char == quote) quote = null
            char == '"' || char == '\'' -> quote = char
            char == '>' -> return index
        }
    }
    return -1
}

private fun closeElement(
    open: ArrayDeque<HtmlElement>,
    tag: String,
) {
    val index = open.indexOfLast { it.tag == tag }
    if (index <= 0) return
    while (open.size > index) open.removeLast()
}

/** The few implicit end tags that matter for clipboard HTML: a new item or block ends a `<li>`/`<p>`. */
private fun closeImplicitly(
    open: ArrayDeque<HtmlElement>,
    tag: String,
) {
    if (tag == "li") {
        val openItem = open.indexOfLast { it.tag == "li" }
        val openList = open.indexOfLast { it.tag in ListTags }
        if (openItem > 0 && openItem > openList) while (open.size > openItem) open.removeLast()
    }
    if (tag in BlockTags && open.last().tag == "p") open.removeLast()
}

private val AttributeRegex = Regex("""([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")

private fun parseAttributes(source: String): Map<String, String> {
    if (source.isBlank()) return emptyMap()
    return AttributeRegex.findAll(source).associate { match ->
        val value = match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value ?: ""
        match.groupValues[1].lowercase() to decodeHtmlEntities(value)
    }
}

private val EntityRegex = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});""")

private val NamedEntities =
    mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–",
        "lsquo" to "‘",
        "rsquo" to "’",
        "sbquo" to "‚",
        "ldquo" to "“",
        "rdquo" to "”",
        "bdquo" to "„",
        "laquo" to "«",
        "raquo" to "»",
        "bull" to "•",
        "middot" to "·",
        "deg" to "°",
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "euro" to "€",
        "pound" to "£",
        "yen" to "¥",
        "cent" to "¢",
        "sect" to "§",
        "para" to "¶",
        "larr" to "←",
        "rarr" to "→",
        "uarr" to "↑",
        "darr" to "↓",
        "harr" to "↔",
        "check" to "✓",
        "hearts" to "♥",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "zwj" to "‍",
        "zwnj" to "‌",
    )

internal fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    return EntityRegex.replace(text) { match ->
        val name = match.groupValues[1]
        val decoded =
            when {
                name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePointText)
                name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePointText)
                else -> NamedEntities[name]
            }
        decoded ?: match.value
    }
}

private fun codePointText(codePoint: Int): String? =
    if (codePoint in 1..Character.MAX_CODE_POINT && codePoint !in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code) {
        String(Character.toChars(codePoint))
    } else {
        null
    }

// endregion

// region Rendering

private enum class InlineStyle(
    val open: String,
    val close: String,
) {
    // Declaration order is the opening order, so nesting reads ~~<u>***text***</u>~~ like the toolbar's.
    STRIKETHROUGH("~~", "~~"),
    UNDERLINE("<u>", "</u>"),
    BOLD("**", "**"),
    ITALIC("*", "*"),
}

/** A stretch of inline text with one set of styles; a run whose text is "\n" is a line break. */
private data class InlineRun(
    val text: String,
    val styles: Set<InlineStyle>,
    val link: String?,
    val code: Boolean,
) {
    val isLineBreak: Boolean get() = text == "\n"
}

private val LineBreakRun = InlineRun("\n", emptySet(), null, code = false)

private val CollapsibleWhitespaceRegex = Regex("""[ \t\n\r\u000C ]+""")
private val ExtraBlankLinesRegex = Regex("""\n{3,}""")
private val AbsoluteUrlRegex = Regex("""^(?:https?|mailto|tel|ftp):""", RegexOption.IGNORE_CASE)

private class HtmlMarkdownRenderer {
    fun render(root: HtmlElement): String =
        blockChunks(root.children, emptySet())
            .joinToString("\n\n") { chunk -> chunk.joinToString("\n") }
            .lines()
            .joinToString("\n") { line -> line.trimEnd() }
            .replace(ExtraBlankLinesRegex, "\n\n")
            .trim('\n')

    /** Markdown blocks (each a list of lines) for a sequence of sibling nodes. */
    private fun blockChunks(
        nodes: List<HtmlNode>,
        styles: Set<InlineStyle>,
    ): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        val pendingInline = mutableListOf<HtmlNode>()

        fun flushInline() {
            if (pendingInline.isEmpty()) return
            inlineLines(pendingInline, styles).takeIf { it.isNotEmpty() }?.let(chunks::add)
            pendingInline.clear()
        }
        for (node in nodes) {
            if (node is HtmlElement && node.isSkipped) continue
            if (node is HtmlElement && (node.tag in BlockTags || node.containsBlock)) {
                flushInline()
                chunks += blockElementChunks(node, styles)
            } else {
                pendingInline += node
            }
        }
        flushInline()
        return chunks
    }

    private fun blockElementChunks(
        element: HtmlElement,
        styles: Set<InlineStyle>,
    ): List<List<String>> =
        when (element.tag) {
            in HeadingTags -> listOfNotNull(headingLines(element, styles))
            in ListTags -> listOfNotNull(listLines(element, styles).takeIf { it.isNotEmpty() })
            "li" -> listOfNotNull(listItemLines(element.children, taskCheckboxState(element), "- ", styles).takeIf { it.isNotEmpty() })
            "blockquote" -> listOfNotNull(quoteLines(element, styles))
            "pre" -> listOfNotNull(codeFenceLines(element))
            "hr" -> listOf(listOf("---"))
            "table" -> listOfNotNull(tableLines(element, styles).takeIf { it.isNotEmpty() })
            else -> blockChunks(element.children, stylesFor(element, styles))
        }

    private fun headingLines(
        element: HtmlElement,
        styles: Set<InlineStyle>,
    ): List<String>? {
        // Headings already render bold, so bold inside one would only add markers.
        val text = inlineLines(element.children, styles, suppressed = setOf(InlineStyle.BOLD)).joinToString(" ").trim()
        if (text.isEmpty()) return null
        val level =
            element.tag
                .last()
                .digitToInt()
                .coerceAtMost(REMEMBER_MAX_HEADING_LEVEL)
        return listOf("#".repeat(level) + " " + text)
    }

    private fun listLines(
        list: HtmlElement,
        styles: Set<InlineStyle>,
    ): List<String> {
        val ordered = list.tag == "ol"
        var number = list.attributes["start"]?.trim()?.toIntOrNull() ?: 1
        val lines = mutableListOf<String>()
        for (child in list.children) {
            when {
                child is HtmlText && child.text.isBlank() -> Unit
                child is HtmlElement && child.isSkipped -> Unit
                // A list nested directly in a list (instead of inside an item) still indents.
                child is HtmlElement && child.tag in ListTags -> lines += listLines(child, styles).map { NESTED_LIST_INDENT + it }
                else -> {
                    val content = if (child is HtmlElement && child.tag == "li") child.children else listOf(child)
                    val checkbox = (child as? HtmlElement)?.takeIf { it.tag == "li" }?.let(::taskCheckboxState)
                    val marker = if (ordered) "$number. " else "- "
                    val item = listItemLines(content, checkbox, marker, styles)
                    if (item.isNotEmpty()) {
                        lines += item
                        number++
                    }
                }
            }
        }
        return lines
    }

    private fun listItemLines(
        content: List<HtmlNode>,
        checked: Boolean?,
        marker: String,
        styles: Set<InlineStyle>,
    ): List<String> {
        val lines = blockChunks(content, styles).flatten().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (lines.isEmpty()) return emptyList()
        // Remember's checklist syntax is always "- [ ]", whatever list the task sits in.
        val itemMarker =
            when (checked) {
                true -> "- [x] "
                false -> "- [ ] "
                null -> marker
            }
        return listOf(itemMarker + lines.first()) + lines.drop(1).map { line -> if (line.isBlank()) "" else NESTED_LIST_INDENT + line }
    }

    /** The checked state of a task-list item's checkbox (GitHub, Notion), or null for a plain item. */
    private fun taskCheckboxState(item: HtmlElement): Boolean? {
        fun find(nodes: List<HtmlNode>): Boolean? {
            for (node in nodes) {
                if (node !is HtmlElement || node.tag in ListTags) continue
                if (node.tag == "input") {
                    if (node.attributes["type"].equals("checkbox", ignoreCase = true)) return "checked" in node.attributes
                } else {
                    find(node.children)?.let { return it }
                }
            }
            return null
        }
        return find(item.children)
    }

    private fun quoteLines(
        element: HtmlElement,
        styles: Set<InlineStyle>,
    ): List<String>? {
        val chunks = blockChunks(element.children, styles)
        if (chunks.isEmpty()) return null
        return chunks
            .flatMapIndexed { index, chunk -> if (index == 0) chunk else listOf("") + chunk }
            .map { line -> if (line.isBlank()) ">" else "> $line" }
    }

    private fun codeFenceLines(pre: HtmlElement): List<String>? {
        val code =
            StringBuilder()
                .also { appendRawText(pre, it) }
                .toString()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(' ', ' ')
                .removePrefix("\n")
                .trimEnd('\n', ' ', '\t')
        if (code.isBlank()) return null
        return listOf("```") + code.lines() + "```"
    }

    private fun appendRawText(
        node: HtmlNode,
        out: StringBuilder,
    ) {
        when (node) {
            is HtmlText -> out.append(node.text)
            is HtmlElement ->
                when {
                    node.isSkipped -> Unit
                    node.tag == "br" -> out.append('\n')
                    else -> node.children.forEach { appendRawText(it, out) }
                }
        }
    }

    private fun tableLines(
        table: HtmlElement,
        styles: Set<InlineStyle>,
    ): List<String> {
        val rows = mutableListOf<HtmlElement>()

        fun collectRows(element: HtmlElement) {
            for (child in element.children) {
                if (child !is HtmlElement || child.isSkipped) continue
                if (child.tag == "tr") {
                    rows += child
                } else if (child.tag != "table") {
                    collectRows(child)
                }
            }
        }
        collectRows(table)
        return rows.mapNotNull { row ->
            val cells =
                row.children
                    .filterIsInstance<HtmlElement>()
                    .filter { cell -> !cell.isSkipped && (cell.tag == "td" || cell.tag == "th") }
                    .map { cell -> inlineLines(cell.children, styles).joinToString(" ").trim() }
            cells.takeIf { it.any(String::isNotEmpty) }?.joinToString(" | ")
        }
    }

    /** One paragraph: inline nodes rendered to Markdown lines (split at `<br>`), trimmed. */
    private fun inlineLines(
        nodes: List<HtmlNode>,
        styles: Set<InlineStyle>,
        suppressed: Set<InlineStyle> = emptySet(),
    ): List<String> {
        val runs = mutableListOf<InlineRun>()
        nodes.forEach { node -> collectRuns(node, styles, link = null, code = false, suppressed = suppressed, out = runs) }
        val lines = splitLines(normalizeWhitespace(runs)).map { line -> emitInline(line).trim() }
        val trimmed = lines.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return trimmed.filterIndexed { index, line -> line.isNotEmpty() || trimmed[index - 1].isNotEmpty() }
    }

    private fun collectRuns(
        node: HtmlNode,
        styles: Set<InlineStyle>,
        link: String?,
        code: Boolean,
        suppressed: Set<InlineStyle>,
        out: MutableList<InlineRun>,
    ) {
        when (node) {
            is HtmlText -> {
                val text = node.text.replace(CollapsibleWhitespaceRegex, " ")
                if (text.isNotEmpty()) out += InlineRun(text, styles - suppressed, link, code)
            }
            is HtmlElement ->
                when {
                    node.isSkipped -> Unit
                    node.tag == "br" -> out += LineBreakRun
                    node.tag == "img" -> imageRun(node, styles - suppressed, link)?.let(out::add)
                    else -> {
                        // A block inside inline context (a heading, a table cell) still breaks the line.
                        val isBlock = node.tag in BlockTags
                        if (isBlock) out += LineBreakRun
                        val childStyles = stylesFor(node, styles)
                        val childLink = if (node.tag == "a" && link == null) absoluteUrl(node.attributes["href"]) else link
                        val childCode = code || node.tag in CodeTags
                        node.children.forEach { child -> collectRuns(child, childStyles, childLink, childCode, suppressed, out) }
                        if (isBlock) out += LineBreakRun
                    }
                }
        }
    }

    private fun imageRun(
        image: HtmlElement,
        styles: Set<InlineStyle>,
        link: String?,
    ): InlineRun? {
        val alt =
            image.attributes["alt"]
                ?.replace(CollapsibleWhitespaceRegex, " ")
                ?.trim()
                .orEmpty()
        val source = absoluteUrl(image.attributes["src"])
        // Emoji images (GitHub's custom emoji) read as their alt text, not as a link.
        val isEmoji = image.attributes["class"]?.contains("emoji", ignoreCase = true) == true
        return when {
            isEmoji || link != null || source == null -> alt.takeIf { it.isNotEmpty() }?.let { InlineRun(it, styles, link, code = false) }
            else -> InlineRun(alt.ifEmpty { "Image" }, styles, source, code = false)
        }
    }

    /** Collapses whitespace across run boundaries, the way a browser lays out inline text. */
    private fun normalizeWhitespace(runs: List<InlineRun>): List<InlineRun> {
        val normalized = mutableListOf<InlineRun>()
        var afterSpace = true
        for (run in runs) {
            if (run.isLineBreak) {
                normalized += run
                afterSpace = true
                continue
            }
            val text = if (afterSpace) run.text.trimStart(' ') else run.text
            if (text.isEmpty()) continue
            normalized += run.copy(text = text)
            afterSpace = text.endsWith(' ')
        }
        return normalized
    }

    private fun splitLines(runs: List<InlineRun>): List<List<InlineRun>> {
        val lines = mutableListOf(mutableListOf<InlineRun>())
        for (run in runs) {
            if (run.isLineBreak) {
                lines += mutableListOf<InlineRun>()
            } else {
                val line = lines.last()
                val previous = line.lastOrNull()
                if (previous != null && previous.styles == run.styles && previous.link == run.link && previous.code == run.code) {
                    line[line.lastIndex] = previous.copy(text = previous.text + run.text)
                } else {
                    line += run
                }
            }
        }
        return lines
    }

    /**
     * Writes one line of runs as Markdown. Styles open and close like a stack, so nested formatting
     * reads `**bold *both***` and adjacent runs with the same style share one pair of markers.
     * Whitespace at a run's edge is moved outside its markers, where Markdown expects it.
     */
    private fun emitInline(runs: List<InlineRun>): String {
        val out = StringBuilder()
        val open = mutableListOf<InlineStyle>()
        var pendingSpace = false

        fun closeStylesNotIn(keep: Set<InlineStyle>) {
            while (open.any { it !in keep }) out.append(open.removeAt(open.lastIndex).close)
        }

        fun write(
            markdown: String,
            styles: Set<InlineStyle>,
            leadingSpace: Boolean,
            trailingSpace: Boolean,
        ) {
            closeStylesNotIn(styles)
            if (pendingSpace || leadingSpace) out.append(' ')
            InlineStyle.entries.filter { it in styles && it !in open }.forEach { style ->
                out.append(style.open)
                open += style
            }
            out.append(markdown)
            pendingSpace = trailingSpace
        }

        var index = 0
        while (index < runs.size) {
            val run = runs[index]
            val link = run.link
            if (link == null) {
                val core = run.text.trim(' ')
                if (core.isEmpty()) {
                    pendingSpace = true
                } else {
                    write(if (run.code) codeSpan(core) else core, run.styles, run.text.startsWith(' '), run.text.endsWith(' '))
                }
                index++
                continue
            }
            var end = index
            while (end + 1 < runs.size && runs[end + 1].link == link) end++
            val linkRuns = runs.subList(index, end + 1)
            // Styles shared by the whole label sit outside the brackets; the rest stay inside.
            val shared = linkRuns.map { it.styles }.reduce { acc, styles -> acc intersect styles }
            val label = emitInline(linkRuns.map { it.copy(styles = it.styles - shared, link = null) }).trim()
            if (label.isNotEmpty()) {
                val markdown = if (']' in label) "$label ($link)" else "[$label]($link)"
                write(markdown, shared, linkRuns.first().text.startsWith(' '), linkRuns.last().text.endsWith(' '))
            } else if (linkRuns.any { it.text.isNotEmpty() }) {
                pendingSpace = true
            }
            index = end + 1
        }
        closeStylesNotIn(emptySet())
        return out.toString()
    }

    private fun codeSpan(text: String): String = if ('`' in text) text else "`$text`"

    private fun stylesFor(
        element: HtmlElement,
        inherited: Set<InlineStyle>,
    ): Set<InlineStyle> {
        val styles = inherited.toMutableSet()
        when (element.tag) {
            in BoldTags -> styles += InlineStyle.BOLD
            in ItalicTags -> styles += InlineStyle.ITALIC
            in UnderlineTags -> styles += InlineStyle.UNDERLINE
            in StrikethroughTags -> styles += InlineStyle.STRIKETHROUGH
        }
        element.attributes["style"]?.let { applyInlineStyle(it, styles) }
        return styles
    }

    /** Applies CSS that changes formatting; an inline style overrides its own element's tag (Google Docs). */
    private fun applyInlineStyle(
        css: String,
        styles: MutableSet<InlineStyle>,
    ) {
        for (declaration in css.split(';')) {
            val property = declaration.substringBefore(':', "").trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().lowercase()
            when (property) {
                "font-weight" ->
                    when {
                        value == "bold" || value == "bolder" || (value.toIntOrNull() ?: 0) >= BOLD_FONT_WEIGHT -> styles += InlineStyle.BOLD
                        value == "normal" || value == "lighter" || (value.toIntOrNull() ?: BOLD_FONT_WEIGHT) < BOLD_FONT_WEIGHT -> styles -= InlineStyle.BOLD
                    }
                "font-style" ->
                    when (value) {
                        "italic", "oblique" -> styles += InlineStyle.ITALIC
                        "normal" -> styles -= InlineStyle.ITALIC
                    }
                "text-decoration", "text-decoration-line" -> {
                    if ("underline" in value) styles += InlineStyle.UNDERLINE
                    if ("line-through" in value) styles += InlineStyle.STRIKETHROUGH
                }
            }
        }
    }

    /** An absolute link Remember can open, escaped for `[text](url)`; null for relative or script links. */
    private fun absoluteUrl(raw: String?): String? {
        val url = raw?.trim()?.takeIf { AbsoluteUrlRegex.containsMatchIn(it) } ?: return null
        return url
            .filterNot { it == '\n' || it == '\r' || it == '\t' }
            .replace(" ", "%20")
            .replace("(", "%28")
            .replace(")", "%29")
    }
}

private const val BOLD_FONT_WEIGHT = 600

// endregion
