package dev.bikram.remember.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesWidgetTest {
    @Test
    fun widget_plain_text_strips_inline_markdown() {
        assertEquals("30 May IST", widgetPlainText("**30 May IST**"))
        assertEquals("May 2026 games were:...", widgetPlainText("**May 2026** games were:..."))
        assertEquals("Read docs", widgetPlainText("[Read docs](https://example.com)"))
        assertEquals("code", widgetPlainText("`code`"))
    }

    @Test
    fun widget_plain_text_uses_first_non_blank_rendered_line() {
        assertEquals("Heading", widgetPlainText("\n## Heading\nBody"))
        assertEquals("Buy milk", widgetPlainText("- [ ] Buy milk"))
        assertEquals("Quoted", widgetPlainText("> Quoted"))
    }
}
