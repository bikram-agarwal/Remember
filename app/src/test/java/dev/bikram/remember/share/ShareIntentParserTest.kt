package dev.bikram.remember.share

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentParserTest {
    @Test
    fun actionSend_plainTextAndSubject_routesToQuickCapture() {
        val result =
            ShareIntentParser.classifySharePayload<String>(
                action = Intent.ACTION_SEND,
                declaredIntentType = "text/plain",
                rawSubject = "  Kotlin Coroutines\nGuide  ",
                rawHtml = null,
                clipHtml = null,
                extraTexts = listOf("https://kotlinlang.org/docs/coroutines-overview.html"),
                clipTexts = emptyList(),
                explicitStreams = emptyList(),
                clipStreams = emptyList(),
                viewTarget = null,
            )

        assertTrue(result is ClassifiedSharePayload.QuickCapture)
        val capture = result as ClassifiedSharePayload.QuickCapture
        assertEquals("Kotlin Coroutines Guide", capture.initialTitle)
        assertEquals("https://kotlinlang.org/docs/coroutines-overview.html", capture.plainText)
        assertTrue(capture.imageStreams.isEmpty())
    }

    @Test
    fun resolveRichOrPlainBody_prefersHtmlMarkdownAndFallsBackToPlainText() {
        val rich =
            ShareIntentParser.resolveRichOrPlainBody(
                rawHtml = "<p>Read <b>bold</b> and <a href=\"https://example.com\">link</a></p><ul><li>One</li><li>Two</li></ul>",
                plainText = "Read bold and link\nOne\nTwo",
            )
        assertEquals("Read **bold** and [link](https://example.com)\n\n- One\n- Two", rich)

        val fallbackWhenHtmlEmpty =
            ShareIntentParser.resolveRichOrPlainBody(
                rawHtml = "<script>alert(1)</script><style>body{}</style>",
                plainText = "Plain fallback\r\nsecond line",
            )
        assertEquals("Plain fallback\nsecond line", fallbackWhenHtmlEmpty)
    }

    @Test
    fun actionSend_textPlainWithFileStream_routesToFileImportInsteadOfEmptySnippet() {
        val txtStream =
            RawShareStream(
                handle = "content://downloads/meeting_notes.txt",
                scheme = "content",
                displayName = "meeting_notes.txt",
                resolvedMimeType = "text/plain",
            )
        val result =
            ShareIntentParser.classifySharePayload(
                action = Intent.ACTION_SEND,
                declaredIntentType = "text/plain",
                rawSubject = null,
                rawHtml = null,
                clipHtml = null,
                extraTexts = emptyList(),
                clipTexts = emptyList(),
                explicitStreams = listOf(txtStream),
                clipStreams = emptyList(),
                viewTarget = null,
            )

        assertEquals(
            ClassifiedSharePayload.FileImport(listOf("content://downloads/meeting_notes.txt")),
            result,
        )
    }

    @Test
    fun actionSendMultiple_imagesWithCaption_routesToQuickCaptureWithImages() {
        val images =
            listOf(
                RawShareStream("content://media/1", "content", "photo1.jpg", "image/jpeg"),
                RawShareStream("content://media/2", "content", "photo2.png", "image/png"),
            )
        val result =
            ShareIntentParser.classifySharePayload(
                action = Intent.ACTION_SEND_MULTIPLE,
                declaredIntentType = "image/*",
                rawSubject = "Trip photos",
                rawHtml = null,
                clipHtml = null,
                extraTexts = listOf("Weekend hike"),
                clipTexts = emptyList(),
                explicitStreams = images,
                clipStreams = emptyList(),
                viewTarget = null,
            )

        assertTrue(result is ClassifiedSharePayload.QuickCapture)
        val capture = result as ClassifiedSharePayload.QuickCapture
        assertEquals("Trip photos", capture.initialTitle)
        assertEquals("Weekend hike", capture.plainText)
        assertEquals(listOf("content://media/1", "content://media/2"), capture.imageStreams)
    }

    @Test
    fun actionSend_octetStreamMarkdownOrZipOrJson_routesToFileImport() {
        for (fileName in listOf("notes.md", "backup.zip", "Tasks.json")) {
            val stream =
                RawShareStream(
                    handle = "content://provider/$fileName",
                    scheme = "content",
                    displayName = fileName,
                    resolvedMimeType = "application/octet-stream",
                )
            val result =
                ShareIntentParser.classifySharePayload(
                    action = Intent.ACTION_SEND,
                    declaredIntentType = "application/octet-stream",
                    rawSubject = null,
                    rawHtml = null,
                    clipHtml = null,
                    extraTexts = emptyList(),
                    clipTexts = emptyList(),
                    explicitStreams = listOf(stream),
                    clipStreams = emptyList(),
                    viewTarget = null,
                )

            assertEquals(
                ClassifiedSharePayload.FileImport(listOf("content://provider/$fileName")),
                result,
            )
        }
    }

    @Test
    fun actionView_acceptsContentAndFileSchemesAndRejectsUnsupportedSchemes() {
        val fileTarget =
            RawShareStream(
                handle = "file:///sdcard/Download/todo.md",
                scheme = "file",
                displayName = "todo.md",
                resolvedMimeType = "text/markdown",
            )
        assertEquals(
            ClassifiedSharePayload.FileImport(listOf("file:///sdcard/Download/todo.md")),
            ShareIntentParser.classifySharePayload(
                action = Intent.ACTION_VIEW,
                declaredIntentType = "text/markdown",
                rawSubject = null,
                rawHtml = null,
                clipHtml = null,
                extraTexts = emptyList(),
                clipTexts = emptyList(),
                explicitStreams = emptyList(),
                clipStreams = emptyList(),
                viewTarget = fileTarget,
            ),
        )

        val httpTarget =
            RawShareStream(
                handle = "https://example.com/todo.md",
                scheme = "https",
                displayName = "todo.md",
                resolvedMimeType = "text/markdown",
            )
        assertEquals(
            ClassifiedSharePayload.NothingToShare,
            ShareIntentParser.classifySharePayload(
                action = Intent.ACTION_VIEW,
                declaredIntentType = "text/markdown",
                rawSubject = null,
                rawHtml = null,
                clipHtml = null,
                extraTexts = emptyList(),
                clipTexts = emptyList(),
                explicitStreams = emptyList(),
                clipStreams = emptyList(),
                viewTarget = httpTarget,
            ),
        )
    }

    @Test
    fun emptyOrMalformedAction_returnsNothingToShare() {
        assertEquals(
            ClassifiedSharePayload.NothingToShare,
            ShareIntentParser.classifySharePayload<String>(
                action = Intent.ACTION_SEND,
                declaredIntentType = "text/plain",
                rawSubject = "   ",
                rawHtml = "",
                clipHtml = null,
                extraTexts = listOf("  "),
                clipTexts = emptyList(),
                explicitStreams = emptyList(),
                clipStreams = emptyList(),
                viewTarget = null,
            ),
        )
    }
}
