package dev.bikram.remember

import org.junit.Test

class RichTextApiTest {
    @Test
    fun testApi() {
        println("=== CLASSES START ===")
        val guesses =
            listOf(
                "com.mohamedrejeb.richeditor.model.RichSpanStyle",
                "com.mohamedrejeb.richeditor.model.RichSpanStyle\$H1",
                "com.mohamedrejeb.richeditor.model.RichSpanStyle\$Heading1",
                "com.mohamedrejeb.richeditor.model.RichParagraphStyle\$H1",
                "com.mohamedrejeb.richeditor.paragraph.RichParagraphStyle",
                "com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi",
                "com.mohamedrejeb.richeditor.model.RichTextStateKt",
            )
        for (g in guesses) {
            try {
                val c = Class.forName(g)
                println("FOUND: $g")
                for (m in c.methods) {
                    println("  method: ${m.name}")
                }
            } catch (e: Exception) {
                println("NOT FOUND: $g")
            }
        }
        println("=== CLASSES END ===")
    }
}
