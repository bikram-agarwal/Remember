package dev.bikram.remember.ui.edit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.bikram.remember.ui.common.MarkdownBlockKind
import dev.bikram.remember.ui.common.MarkdownInlineKind
import dev.bikram.remember.ui.common.MarkdownInlineProjection
import dev.bikram.remember.ui.common.parseMarkdownDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToMarkdownTest {
    // Shaped like a Chrome copy of a GitHub issue body: heading anchors with SVG icons, task
    // lists, a highlighted code block with its copy button, and a user mention.
    private val gitHubIssueHtml =
        """
        <meta charset='utf-8'><div class="markdown-heading" dir="auto"><h2 tabindex="-1" class="heading-element" dir="auto">Steps to reproduce</h2><a id="user-content-steps" class="anchor" aria-label="Permalink: Steps to reproduce" href="#steps-to-reproduce"><svg class="octicon octicon-link" viewBox="0 0 16 16" aria-hidden="true"><path d="M7.775"></path></svg></a></div>
        <ol dir="auto">
        <li>Open <strong>Settings</strong> → <em>Backup</em></li>
        <li>Tap <code class="notranslate">Export</code>
        <ul dir="auto">
        <li>pick <a href="https://example.com/docs">the docs</a></li>
        </ul>
        </li>
        </ol>
        <ul class="contains-task-list">
        <li class="task-list-item"><input type="checkbox" id="" disabled="" class="task-list-item-checkbox" checked=""> Done thing</li>
        <li class="task-list-item"><input type="checkbox" id="" disabled="" class="task-list-item-checkbox"> Open thing</li>
        </ul>
        <blockquote>
        <p>Quoted <del>old</del> text</p>
        </blockquote>
        <div class="highlight highlight-source-kotlin notranslate position-relative overflow-auto" dir="auto"><pre><span class="pl-k">val</span> x <span class="pl-k">=</span> <span class="pl-c1">1</span>
        <span class="pl-k">if</span> (x &lt; 2) println(x)</pre><div class="zeroclipboard-container"><clipboard-copy aria-label="Copy" value="val x = 1"><svg aria-hidden="true"></svg></clipboard-copy></div></div>
        <hr>
        <p>Thanks <a class="user-mention notranslate" href="https://github.com/octocat">@octocat</a>!</p>
        """.trimIndent()

    private val gitHubIssueMarkdown =
        listOf(
            "## Steps to reproduce",
            "",
            "1. Open **Settings** → *Backup*",
            "2. Tap `Export`",
            "  - pick [the docs](https://example.com/docs)",
            "",
            "- [x] Done thing",
            "- [ ] Open thing",
            "",
            "> Quoted ~~old~~ text",
            "",
            "```",
            "val x = 1",
            "if (x < 2) println(x)",
            "```",
            "",
            "---",
            "",
            "Thanks [@octocat](https://github.com/octocat)!",
        ).joinToString("\n")

    @Test
    fun gitHubIssueKeepsHeadingsListsTasksQuotesCodeAndLinks() {
        assertEquals(gitHubIssueMarkdown, htmlToMarkdown(gitHubIssueHtml))
    }

    @Test
    fun convertedGitHubIssueParsesAsTheSameBlocksInRemember() {
        val lines = parseMarkdownDocument(htmlToMarkdown(gitHubIssueHtml)).lines
        val kinds = lines.map { it.kind }
        assertEquals(MarkdownBlockKind.Heading, kinds[0])
        assertEquals(2, lines[0].headingLevel)
        assertEquals(listOf(MarkdownBlockKind.Numbered, MarkdownBlockKind.Numbered, MarkdownBlockKind.Bullet), kinds.subList(2, 5))
        assertEquals(listOf(MarkdownBlockKind.Checklist, MarkdownBlockKind.Checklist), kinds.subList(6, 8))
        assertEquals(listOf(true, false), lines.subList(6, 8).map { it.checked })
        assertEquals(MarkdownBlockKind.Quote, kinds[9])
        assertEquals(listOf(MarkdownBlockKind.CodeFence, MarkdownBlockKind.Code, MarkdownBlockKind.Code, MarkdownBlockKind.CodeFence), kinds.subList(11, 15))
        assertEquals(MarkdownBlockKind.Rule, kinds[16])
    }

    @Test
    fun pastingConvertedMarkdownThroughTheEditorLeavesItIntact() {
        val markdown = htmlToMarkdown(gitHubIssueHtml)
        val state = MarkdownEditorState()
        state.update(TextFieldValue(markdown, TextRange(markdown.length)))
        assertEquals(markdown, state.markdown)
    }

    @Test
    fun inlineStylesNestLikeTheToolbarAndRenderWithoutVisibleMarkers() {
        val markdown = htmlToMarkdown("<p><b>bold <i>both</i></b> after <u>under</u> <s>gone</s> <code>x</code></p>")
        assertEquals("**bold *both*** after <u>under</u> ~~gone~~ `x`", markdown)
        val projection = MarkdownInlineProjection(markdown)
        assertEquals("bold both after under gone x", projection.text)
        val kinds = projection.spans.map { it.kind }.toSet()
        assertTrue(kinds.toString(), kinds.containsAll(listOf(MarkdownInlineKind.Bold, MarkdownInlineKind.Italic, MarkdownInlineKind.Underline, MarkdownInlineKind.Strikethrough, MarkdownInlineKind.Code)))
    }

    @Test
    fun whitespaceAtStyleEdgesMovesOutsideTheMarkers() {
        assertEquals("**bold** next *lean*", htmlToMarkdown("<p><b>bold </b>next <i> lean</i></p>"))
    }

    @Test
    fun googleDocsStyleBasedFormattingAndItsNormalWeightWrapper() {
        // Docs wraps the whole copy in <b style="font-weight:normal"> and marks bold with spans.
        val html =
            "<meta charset=\"utf-8\"><b style=\"font-weight:normal;\" id=\"docs-internal-guid-1234\">" +
                "<p dir=\"ltr\"><span style=\"font-size:11pt;font-weight:700;\">Bold</span>" +
                "<span style=\"font-size:11pt;font-weight:400;\"> plain </span>" +
                "<span style=\"font-size:11pt;font-style:italic;font-weight:400;\">slanted</span></p>" +
                "<p dir=\"ltr\"><span style=\"text-decoration:underline;\">under</span> and " +
                "<span style=\"text-decoration:line-through;\">gone</span></p></b><br>"
        assertEquals("**Bold** plain *slanted*\n\n<u>under</u> and ~~gone~~", htmlToMarkdown(html))
    }

    @Test
    fun headingsCapAtLevelThreeAndDropRedundantBold() {
        assertEquals("# One\n\n### Four", htmlToMarkdown("<h1>One</h1><h4><strong>Four</strong></h4>"))
    }

    @Test
    fun onlyAbsoluteLinksBecomeLinksAndUnsafeLabelsFallBack() {
        val html =
            "<p><a href=\"/relative\">rel</a> <a href=\"javascript:alert(1)\">js</a> " +
                "<a href=\"https://e.com/a b(c)\">[x] y</a> <a href=\"mailto:me@e.com\">mail</a></p>"
        assertEquals("rel js [x] y (https://e.com/a%20b%28c%29) [mail](mailto:me@e.com)", htmlToMarkdown(html))
    }

    @Test
    fun formattedLinkLabelsKeepTheirStylesInsideTheBrackets() {
        val markdown = htmlToMarkdown("<p><b>See <a href=\"https://e.com\">the <i>docs</i></a></b></p>")
        assertEquals("**See [the *docs*](https://e.com)**", markdown)
        assertEquals("See the docs", MarkdownInlineProjection(markdown).text)
    }

    @Test
    fun imagesBecomeLinksExceptEmoji() {
        val html = "<p><img src=\"https://e.com/i.png\" alt=\"Screenshot\"> <img class=\"emoji\" alt=\":tada:\" src=\"https://e.com/t.png\"></p>"
        assertEquals("[Screenshot](https://e.com/i.png) :tada:", htmlToMarkdown(html))
    }

    @Test
    fun entitiesDecodeAndUnknownOnesStayLiteral() {
        assertEquals(
            "Fish & chips — 😀 &unknown; <tag>",
            htmlToMarkdown("<p>Fish &amp; chips&nbsp;&nbsp;&#8212; &#x1F600; &unknown; &lt;tag&gt;</p>"),
        )
    }

    @Test
    fun hiddenScriptsAndStylesAreDropped() {
        val html =
            "<p>Visible<span style=\"display:none\">hidden</span><span aria-hidden=\"true\">icon</span>" +
                "<script>alert('<b>no</b>')</script><style>p { color: red }</style></p>"
        assertEquals("Visible", htmlToMarkdown(html))
    }

    @Test
    fun lineBreaksTablesAndClipboardFragmentMarkers() {
        assertEquals("line one\nline two", htmlToMarkdown("<p>line one<br>line two</p>"))
        assertEquals(
            "Name | Qty\nMilk | 2",
            htmlToMarkdown("<table><thead><tr><th>Name</th><th>Qty</th></tr></thead><tbody><tr><td>Milk</td><td>2</td></tr></tbody></table>"),
        )
        assertEquals(
            "Only **this**",
            htmlToMarkdown("<html><body><p>Not this</p><!--StartFragment--><p>Only <b>this</b></p><!--EndFragment--></body></html>"),
        )
    }

    @Test
    fun orderedListsHonourStartAndLooseItemsStayInTheirItem() {
        val html = "<ol start=\"3\"><li><p>First</p><p>more</p></li><li>Second</li></ol>"
        assertEquals("3. First\n  more\n4. Second", htmlToMarkdown(html))
    }

    @Test
    fun emptyOrWhitespaceOnlyHtmlGivesNothing() {
        assertEquals("", htmlToMarkdown(""))
        assertEquals("", htmlToMarkdown("<p> </p><div><br></div><span>&nbsp;</span>"))
    }

    @Test(timeout = 5_000)
    fun deeplyNestedHtmlDoesNotOverflowTheStack() {
        val html = "<div>".repeat(20_000) + "deep" + "</div>".repeat(20_000)
        assertEquals("deep", htmlToMarkdown(html))
    }
}
