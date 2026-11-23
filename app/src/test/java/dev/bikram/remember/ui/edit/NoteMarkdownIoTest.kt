package dev.bikram.remember.ui.edit

import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NoteMarkdownIoTest {
    @Test
    fun `readMarkdownFromInputStream returns Success and normalizes CRLF`() {
        val raw = "# Heading\r\n\r\nLine two\rLine three"
        val result = readMarkdownFromInputStream(ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        assertEquals(
            NoteMarkdownImportResult.Success("# Heading\n\nLine two\nLine three"),
            result,
        )
    }

    @Test
    fun `readMarkdownFromInputStream returns Empty for blank file`() {
        val result = readMarkdownFromInputStream(ByteArrayInputStream("  \r\n\t ".toByteArray(Charsets.UTF_8)))
        assertEquals(NoteMarkdownImportResult.Empty, result)
    }

    @Test
    fun `readMarkdownFromInputStream returns TooLarge when exceeding byte limit`() {
        val oversized = ByteArray(1025) { 'a'.code.toByte() }
        val result = readMarkdownFromInputStream(ByteArrayInputStream(oversized), maximumBytes = 1024L)
        assertEquals(NoteMarkdownImportResult.TooLarge, result)
    }

    @Test
    fun `readMarkdownFromInputStream accepts exact byte limit`() {
        val exact = ByteArray(1024) { 'a'.code.toByte() }
        val result = readMarkdownFromInputStream(ByteArrayInputStream(exact), maximumBytes = 1024L)
        assertTrue(result is NoteMarkdownImportResult.Success)
        assertEquals(1024, (result as NoteMarkdownImportResult.Success).markdown.length)
    }

    @Test
    fun `buildMarkdownExportFileName sanitizes title and appends md`() {
        assertEquals("Project_Plan.md", buildMarkdownExportFileName("Project:Plan", "Untitled note"))
        assertEquals("Meeting_Notes.md", buildMarkdownExportFileName("Meeting/Notes.MD", "Untitled note"))
        assertEquals("Untitled note.md", buildMarkdownExportFileName("   ", "Untitled note"))
    }

    @Test
    fun `markdownTitleFromFileName strips markdown extension and sanitizes`() {
        assertEquals("Demo_ Markdown", markdownTitleFromFileName("Demo_ Markdown.md"))
        assertEquals("Notes", markdownTitleFromFileName("primary:Download/Notes.MARKDOWN"))
        assertEquals("PlainName", markdownTitleFromFileName("PlainName"))
        assertNull(markdownTitleFromFileName("   .md"))
    }

    @Test
    fun `checklistToMarkdown and markdownToChecklistItems round-trip hierarchy`() {
        val markdown =
            """
            - [ ] Groceries
              Milk and eggs
              - [x] Apples
            - [x] Call dentist
            """.trimIndent()
        val items = markdownToChecklistItems(markdown)
        assertEquals(3, items.size)
        assertEquals("Groceries", items[0].text)
        assertEquals("Milk and eggs", items[0].details)
        assertEquals(false, items[0].checked)
        assertEquals(0, items[0].depth)
        assertEquals("Apples", items[1].text)
        assertEquals(true, items[1].checked)
        assertEquals(1, items[1].depth)
        assertEquals(items[0].localId, items[1].parentLocalId)
        assertEquals("Call dentist", items[2].text)
        assertEquals(true, items[2].checked)

        assertEquals(markdown, checklistToMarkdown(items))
    }

    @Test
    fun `writeNotesMarkdownZip writes deduplicated md entries for notes and checklists`() {
        val note1 =
            NoteWithItems(
                note =
                    NoteEntity(
                        id = 1L,
                        kind = NoteKind.NOTE,
                        title = "Project",
                        body = "# Hello",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                items = emptyList(),
            )
        val note2 =
            NoteWithItems(
                note =
                    NoteEntity(
                        id = 2L,
                        kind = NoteKind.LIST,
                        title = "Project",
                        body = "",
                        colorIndex = 0,
                        starred = false,
                        trashed = false,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                items =
                    listOf(
                        ChecklistItemEntity(
                            id = 10L,
                            noteId = 2L,
                            text = "Task 1",
                            details = "",
                            checked = true,
                            sortOrder = 1.0,
                        ),
                    ),
            )

        val out = ByteArrayOutputStream()
        writeNotesMarkdownZip(
            outputStream = out,
            notes = listOf(note1, note2),
            fallbackUntitledNote = "Untitled note",
            fallbackUntitledList = "Untitled list",
        )

        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertEquals(listOf("Project.md", "Project (2).md"), entries.keys.toList())
        assertEquals("# Hello", entries["Project.md"])
        assertEquals("- [x] Task 1", entries["Project (2).md"])
    }

    @Test
    fun `readMarkdownEntriesFromZip extracts valid md files and skips hidden or system entries`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun addEntry(
                name: String,
                content: String,
            ) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("folder/Meeting Notes.md", "# Agenda\r\n- Item 1")
            addEntry("Todo.MARKDOWN", "- [ ] Buy milk")
            addEntry("__MACOSX/folder/._Meeting Notes.md", "appledouble")
            addEntry(".DS_Store", "ds_store")
            addEntry(".hidden/Secret.md", "hidden")
            addEntry("._AppleDouble.md", "appledouble")
            addEntry("Empty.md", "   \n  ")
            addEntry("readme.txt", "not markdown")
        }

        val extracted = readMarkdownEntriesFromZip(ByteArrayInputStream(out.toByteArray()))
        assertEquals(
            listOf(
                MarkdownZipEntry(suggestedTitle = "Meeting Notes", markdown = "# Agenda\n- Item 1"),
                MarkdownZipEntry(suggestedTitle = "Todo", markdown = "- [ ] Buy milk"),
            ),
            extracted,
        )
    }

    @Test
    fun `isStrictChecklistMarkdown accepts checklists with details and rejects plain lists or prose`() {
        assertTrue(
            isStrictChecklistMarkdown(
                """
                - [ ] Drinkables
                  - [ ] Milk
                    Don't forget 2%
                    Not from QFC
                  - [x] Cola
                """.trimIndent(),
            ),
        )
        assertFalse(isStrictChecklistMarkdown("- Plain bullet\n- Another bullet"))
        assertFalse(isStrictChecklistMarkdown("# Heading\n- [ ] Task"))
        assertFalse(isStrictChecklistMarkdown("Intro paragraph\n- [ ] Task"))
    }

    @Test
    fun `canConvertMarkdownNoteToChecklist accepts checklists and plain lists but rejects rich blocks`() {
        assertTrue(canConvertMarkdownNoteToChecklist("- [ ] Task 1\n- [x] Task 2"))
        assertTrue(canConvertMarkdownNoteToChecklist("- Milk\n  2 percent\n  - Oat\n1. Bread"))
        assertFalse(canConvertMarkdownNoteToChecklist(""))
        assertFalse(canConvertMarkdownNoteToChecklist("Just a plain paragraph"))
        assertFalse(canConvertMarkdownNoteToChecklist("# Shopping\n- Milk"))
        assertFalse(canConvertMarkdownNoteToChecklist("```kotlin\nval x = 1\n```"))
        assertFalse(canConvertMarkdownNoteToChecklist("- Parent\n  - Child\n    - Grandchild"))
    }
}
