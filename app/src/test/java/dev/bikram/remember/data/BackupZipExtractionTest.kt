package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipExtractionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun bounded_extraction_materializes_safe_entries() {
        val extractRoot = temporaryFolder.newFolder("safe")
        val archive = archive("notes.json" to "notes", "media/image.txt" to "image")

        val extracted = extractZipEntriesWithinLimits(ByteArrayInputStream(archive), extractRoot)

        assertTrue(extracted)
        assertEquals("notes", extractRoot.resolve("notes.json").readText())
        assertEquals("image", extractRoot.resolve("media/image.txt").readText())
    }

    @Test
    fun bounded_extraction_rejects_excessive_entry_count() {
        val extractRoot = temporaryFolder.newFolder("entries")
        val archive = archive("first.txt" to "1", "second.txt" to "2")

        val extracted =
            extractZipEntriesWithinLimits(
                inputStream = ByteArrayInputStream(archive),
                extractRoot = extractRoot,
                limits = ZipExtractionLimits(maximumEntryCount = 1),
            )

        assertFalse(extracted)
    }

    @Test
    fun bounded_extraction_rejects_large_entries_and_total_size() {
        val largeEntryRoot = temporaryFolder.newFolder("large-entry")
        val totalSizeRoot = temporaryFolder.newFolder("total-size")
        val largeEntryArchive = archive("large.txt" to "12345")
        val totalSizeArchive = archive("first.txt" to "123", "second.txt" to "456")

        val largeEntryExtracted =
            extractZipEntriesWithinLimits(
                inputStream = ByteArrayInputStream(largeEntryArchive),
                extractRoot = largeEntryRoot,
                limits = ZipExtractionLimits(maximumEntryBytes = 4),
            )
        val totalSizeExtracted =
            extractZipEntriesWithinLimits(
                inputStream = ByteArrayInputStream(totalSizeArchive),
                extractRoot = totalSizeRoot,
                limits = ZipExtractionLimits(maximumEntryBytes = 10, maximumTotalBytes = 5),
            )

        assertFalse(largeEntryExtracted)
        assertFalse(totalSizeExtracted)
    }

    @Test
    fun bounded_extraction_rejects_paths_outside_restore_directory() {
        val extractRoot = temporaryFolder.newFolder("unsafe")
        val archive = archive("../escape.txt" to "escape")

        val extracted = extractZipEntriesWithinLimits(ByteArrayInputStream(archive), extractRoot)

        assertFalse(extracted)
        assertFalse(temporaryFolder.root.resolve("escape.txt").exists())
    }

    @Test
    fun bounded_json_reader_rejects_oversized_input() {
        val accepted =
            readUtf8TextWithinLimit(
                inputStream = ByteArrayInputStream("1234".toByteArray()),
                maximumBytes = 4,
            )
        val rejected =
            readUtf8TextWithinLimit(
                inputStream = ByteArrayInputStream("12345".toByteArray()),
                maximumBytes = 4,
            )

        assertEquals("1234", accepted)
        assertNull(rejected)
    }

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipOutput ->
            entries.forEach { (entryName, content) ->
                zipOutput.putNextEntry(ZipEntry(entryName))
                zipOutput.write(content.toByteArray())
                zipOutput.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
