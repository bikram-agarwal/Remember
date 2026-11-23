package dev.bikram.remember.ui.edit

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.nativeClipboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Larger rich-text clips paste as plain text rather than stall on conversion. */
private const val MAX_PASTED_HTML_CHARS = 2_000_000

/**
 * The note body's clipboard. Pasting rich text (HTML from a browser, mail, or docs app) inserts it
 * as Markdown ([htmlToMarkdown]) instead of the plain text Compose would take; copying and every
 * other clip go to the platform clipboard unchanged.
 *
 * Paste is converted here, not with `Modifier.contentReceiver`, because a content receiver makes
 * the field advertise every MIME type to the keyboard, so Gboard would offer stickers and GIFs a
 * note can't hold. Drag and drop is unaffected (Chrome only ever drags plain text), and so is the
 * keyboard's own clipboard suggestion, which the keyboard types in as plain text.
 *
 * On the Android emulator, host clipboard sharing re-posts every copy as plain text, so rich paste
 * can only be tried there with clipboard sharing turned off.
 *
 * It implements [AndroidClipboard] because BasicTextField decides whether to offer Paste through
 * `Clipboard.nativeClipboardManager`, which only accepts an AndroidClipboard. Compose marks that
 * interface `@VisibleForTesting`, hence the lint suppression; delegating to the platform
 * clipboard is exactly the use it exists for.
 */
@SuppressLint("VisibleForTests")
internal class MarkdownPasteClipboard(
    private val platformClipboard: Clipboard,
    private val shouldConvertRichText: () -> Boolean,
) : AndroidClipboard {
    override val clipboardManager: ClipboardManager
        get() = platformClipboard.nativeClipboardManager

    override suspend fun getClipEntry(): ClipEntry? {
        val entry = platformClipboard.getClipEntry() ?: return null
        if (!shouldConvertRichText()) return entry
        val markdown = richTextClipAsMarkdown(entry.clipData) ?: return entry
        return ClipEntry(ClipData.newPlainText(entry.clipData.description.label, markdown))
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        platformClipboard.setClipEntry(clipEntry)
    }
}

/** Markdown for a single-item rich-text clip, or null when the clip should paste as it is. */
private suspend fun richTextClipAsMarkdown(clipData: ClipData): String? {
    if (clipData.itemCount != 1) return null
    val html = clipData.getItemAt(0).htmlText ?: return null
    if (html.length > MAX_PASTED_HTML_CHARS) return null
    return withContext(Dispatchers.Default) {
        runCatching { htmlToMarkdown(html) }.getOrNull()
    }?.takeIf { it.isNotBlank() }
}
