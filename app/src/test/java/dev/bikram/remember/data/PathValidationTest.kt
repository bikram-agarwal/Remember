package dev.bikram.remember.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathValidationTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun acceptsFileInsideBaseDirectory() {
        val baseDirectory = temporaryFolder.newFolder("extract")
        val childFile =
            File(baseDirectory, "media/picture.jpg").apply {
                parentFile?.mkdirs()
                writeText("image")
            }

        assertEquals(
            childFile.canonicalFile,
            canonicalFileInsideBaseDirectoryOrNull(
                candidate = childFile,
                baseDirectory = baseDirectory,
            ),
        )
    }

    @Test
    fun rejectsSiblingDirectoryThatSharesBasePrefix() {
        val baseDirectory = temporaryFolder.newFolder("extract")
        val siblingDirectory = File(baseDirectory.parentFile, "extract_evil").apply { mkdirs() }
        val siblingFile = File(siblingDirectory, "payload.txt").apply { writeText("bad") }

        assertNull(
            canonicalFileInsideBaseDirectoryOrNull(
                candidate = siblingFile,
                baseDirectory = baseDirectory,
            ),
        )
    }

    @Test
    fun rejectsTraversalIntoSiblingDirectory() {
        val baseDirectory = temporaryFolder.newFolder("extract")
        val siblingDirectory = File(baseDirectory.parentFile, "extract_evil").apply { mkdirs() }
        File(siblingDirectory, "payload.txt").writeText("bad")
        val traversalFile = File(baseDirectory, "../extract_evil/payload.txt")

        assertNull(
            canonicalFileInsideBaseDirectoryOrNull(
                candidate = traversalFile,
                baseDirectory = baseDirectory,
            ),
        )
    }
}
