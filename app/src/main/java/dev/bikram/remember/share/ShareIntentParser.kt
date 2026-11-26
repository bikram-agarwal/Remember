package dev.bikram.remember.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.bikram.remember.ui.common.normalizeMarkdownLineEndings
import dev.bikram.remember.ui.edit.htmlToMarkdown
import dev.bikram.remember.ui.edit.resolveMarkdownDisplayName

/** Larger HTML payloads fall back to plain text rather than stalling quick-capture startup. */
internal const val MAX_SHARE_HTML_CHARS = 2_000_000

internal sealed interface ShareRoute {
    data class FileImport(
        val uris: List<Uri>,
    ) : ShareRoute

    data class QuickCapture(
        val initialTitle: String,
        val rawHtml: String?,
        val plainText: String,
        val imageUris: List<Uri>,
    ) : ShareRoute

    data object NothingToShare : ShareRoute
}

internal data class RawShareStream<Handle>(
    val handle: Handle,
    val scheme: String?,
    val displayName: String,
    val resolvedMimeType: String?,
)

internal sealed interface ClassifiedSharePayload<out Handle> {
    data class FileImport<Handle>(
        val streams: List<Handle>,
    ) : ClassifiedSharePayload<Handle>

    data class QuickCapture<Handle>(
        val initialTitle: String,
        val rawHtml: String?,
        val plainText: String,
        val imageStreams: List<Handle>,
    ) : ClassifiedSharePayload<Handle>

    data object NothingToShare : ClassifiedSharePayload<Nothing>
}

internal object ShareIntentParser {
    private val KNOWN_DOCUMENT_EXTENSIONS =
        listOf(".md", ".markdown", ".txt", ".zip", ".json")

    private val KNOWN_IMAGE_EXTENSIONS =
        listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif", ".avif")

    fun parse(
        context: Context,
        intent: Intent?,
    ): ShareRoute {
        intent ?: return ShareRoute.NothingToShare
        return runCatching {
            val action = intent.action
            val isMultiple = action == Intent.ACTION_SEND_MULTIPLE
            val viewUri = intent.data
            val clipStreams = extractClipDataStreams(context, intent)
            val classified =
                classifySharePayload(
                    action = action,
                    declaredIntentType = intent.type,
                    rawSubject = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString() }.getOrNull(),
                    rawHtml = runCatching { intent.getStringExtra(Intent.EXTRA_HTML_TEXT) }.getOrNull(),
                    clipHtml = extractSingleClipHtml(intent),
                    extraTexts = extractExtraTexts(intent, isMultiple = isMultiple),
                    clipTexts = extractClipTexts(intent),
                    explicitStreams = extractExplicitStreams(context, intent, isMultiple = isMultiple),
                    clipStreams = clipStreams,
                    viewTarget = viewUri?.let { uri -> toRawShareStream(context, uri) },
                )
            when (classified) {
                is ClassifiedSharePayload.FileImport -> ShareRoute.FileImport(classified.streams)
                is ClassifiedSharePayload.QuickCapture ->
                    ShareRoute.QuickCapture(
                        initialTitle = classified.initialTitle,
                        rawHtml = classified.rawHtml,
                        plainText = classified.plainText,
                        imageUris = classified.imageStreams,
                    )
                ClassifiedSharePayload.NothingToShare -> ShareRoute.NothingToShare
            }
        }.getOrDefault(ShareRoute.NothingToShare)
    }

    internal fun <Handle> classifySharePayload(
        action: String?,
        declaredIntentType: String?,
        rawSubject: String?,
        rawHtml: String?,
        clipHtml: String?,
        extraTexts: List<String>,
        clipTexts: List<String>,
        explicitStreams: List<RawShareStream<Handle>>,
        clipStreams: List<RawShareStream<Handle>>,
        viewTarget: RawShareStream<Handle>?,
    ): ClassifiedSharePayload<Handle> =
        when (action) {
            Intent.ACTION_VIEW -> {
                val target =
                    viewTarget?.takeIf { stream -> isSupportedUriScheme(stream.scheme) }
                        ?: clipStreams.firstOrNull { stream -> isSupportedUriScheme(stream.scheme) }
                if (target != null) {
                    ClassifiedSharePayload.FileImport(listOf(target.handle))
                } else {
                    ClassifiedSharePayload.NothingToShare
                }
            }
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            -> {
                val subject = normalizeSubject(rawSubject)
                val effectiveHtml = rawHtml?.takeIf(String::isNotBlank) ?: clipHtml?.takeIf(String::isNotBlank)
                val nonBlankExtraTexts = extraTexts.mapNotNull { text -> text.takeIf(String::isNotBlank) }
                val effectivePlainText =
                    if (nonBlankExtraTexts.isNotEmpty()) {
                        nonBlankExtraTexts.joinToString("\n\n")
                    } else {
                        clipTexts.mapNotNull { text -> text.takeIf(String::isNotBlank) }.joinToString("\n\n")
                    }

                val supportedExplicitStreams = explicitStreams.filter { stream -> isSupportedUriScheme(stream.scheme) }
                val supportedClipStreams = clipStreams.filter { stream -> isSupportedUriScheme(stream.scheme) }
                val candidateStreams =
                    if (supportedExplicitStreams.isNotEmpty()) {
                        supportedExplicitStreams
                    } else if (effectiveHtml == null && effectivePlainText.isBlank()) {
                        supportedClipStreams
                    } else {
                        emptyList()
                    }

                if (candidateStreams.isNotEmpty()) {
                    val allImages = candidateStreams.all { stream -> isImageStream(stream, declaredIntentType) }
                    if (allImages) {
                        ClassifiedSharePayload.QuickCapture(
                            initialTitle = subject,
                            rawHtml = effectiveHtml,
                            plainText = effectivePlainText,
                            imageStreams = candidateStreams.map { stream -> stream.handle },
                        )
                    } else {
                        ClassifiedSharePayload.FileImport(candidateStreams.map { stream -> stream.handle })
                    }
                } else if (subject.isBlank() && effectiveHtml == null && effectivePlainText.isBlank()) {
                    ClassifiedSharePayload.NothingToShare
                } else {
                    ClassifiedSharePayload.QuickCapture(
                        initialTitle = subject,
                        rawHtml = effectiveHtml,
                        plainText = effectivePlainText,
                        imageStreams = emptyList(),
                    )
                }
            }
            else -> ClassifiedSharePayload.NothingToShare
        }

    fun resolveRichOrPlainBody(
        rawHtml: String?,
        plainText: String,
    ): String {
        val convertedMarkdown =
            if (!rawHtml.isNullOrBlank() && rawHtml.length <= MAX_SHARE_HTML_CHARS) {
                runCatching { htmlToMarkdown(rawHtml) }
                    .getOrNull()
                    ?.takeIf { markdown -> markdown.isNotBlank() }
            } else {
                null
            }
        return (convertedMarkdown ?: plainText).normalizeMarkdownLineEndings().trim()
    }

    private fun normalizeSubject(rawSubject: String?): String =
        rawSubject
            .orEmpty()
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()

    internal fun isSupportedUriScheme(scheme: String?): Boolean {
        val normalized = scheme?.lowercase()
        return normalized == "content" || normalized == "file"
    }

    internal fun <Handle> isImageStream(
        stream: RawShareStream<Handle>,
        declaredIntentType: String?,
    ): Boolean {
        val fileName = stream.displayName.substringAfterLast('/').lowercase()
        if (KNOWN_DOCUMENT_EXTENSIONS.any { extension -> fileName.endsWith(extension) }) {
            return false
        }
        val resolvedMime = stream.resolvedMimeType
        if (resolvedMime != null) {
            if (resolvedMime.startsWith("image/", ignoreCase = true)) return true
            if (!resolvedMime.equals("application/octet-stream", ignoreCase = true)) return false
        }
        if (KNOWN_IMAGE_EXTENSIONS.any { extension -> fileName.endsWith(extension) }) {
            return true
        }
        return declaredIntentType?.startsWith("image/", ignoreCase = true) == true
    }

    private fun toRawShareStream(
        context: Context,
        uri: Uri,
    ): RawShareStream<Uri> {
        val displayName = resolveMarkdownDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        val resolvedMimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return RawShareStream(
            handle = uri,
            scheme = uri.scheme,
            displayName = displayName,
            resolvedMimeType = resolvedMimeType,
        )
    }

    private fun extractExplicitStreams(
        context: Context,
        intent: Intent,
        isMultiple: Boolean,
    ): List<RawShareStream<Uri>> =
        if (isMultiple) {
            runCatching {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            }.getOrNull()
                ?.filterNotNull()
                ?.map { uri -> toRawShareStream(context, uri) }
                .orEmpty()
        } else {
            val singleUri =
                runCatching {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                }.getOrNull()
            listOfNotNull(singleUri?.let { uri -> toRawShareStream(context, uri) })
        }

    private fun extractClipDataStreams(
        context: Context,
        intent: Intent,
    ): List<RawShareStream<Uri>> {
        val clip = runCatching { intent.clipData }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until clip.itemCount) {
                val uri = clip.getItemAt(index)?.uri
                if (uri != null) {
                    add(toRawShareStream(context, uri))
                }
            }
        }
    }

    private fun extractSingleClipHtml(intent: Intent): String? {
        val clip = runCatching { intent.clipData }.getOrNull() ?: return null
        if (clip.itemCount != 1) return null
        return clip.getItemAt(0)?.htmlText
    }

    private fun extractExtraTexts(
        intent: Intent,
        isMultiple: Boolean,
    ): List<String> {
        if (isMultiple) {
            val multipleTexts =
                runCatching { intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT) }
                    .getOrNull()
                    ?.mapNotNull { item -> item?.toString() }
                    .orEmpty()
            if (multipleTexts.isNotEmpty()) return multipleTexts
        }
        val singleText = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull()
        return listOfNotNull(singleText)
    }

    private fun extractClipTexts(intent: Intent): List<String> {
        val clip = runCatching { intent.clipData }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                if (item?.uri == null) {
                    item?.text?.toString()?.let(::add)
                }
            }
        }
    }
}
