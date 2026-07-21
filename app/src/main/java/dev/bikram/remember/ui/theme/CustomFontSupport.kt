package dev.bikram.remember.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object CustomFontStorage {
    private const val FONTS_DIR_NAME = "fonts"
    private const val STORED_FONT_BASENAME = "custom_font"

    sealed interface ImportResult {
        data class Success(
            val path: String,
            val displayName: String,
        ) : ImportResult

        data object InvalidFont : ImportResult
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
    ): ImportResult {
        val bytes =
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }
            }.getOrNull() ?: return ImportResult.InvalidFont

        if (bytes.isEmpty()) return ImportResult.InvalidFont

        val fontsDirectory = File(context.filesDir, FONTS_DIR_NAME).apply { mkdirs() }
        listOf("ttf", "otf").forEach { extension ->
            File(fontsDirectory, "$STORED_FONT_BASENAME.$extension").delete()
        }

        val extension = resolveFontExtension(context, uri)
        val targetFile = File(fontsDirectory, "$STORED_FONT_BASENAME.$extension")
        return runCatching {
            targetFile.writeBytes(bytes)
            Typeface.createFromFile(targetFile)
            val parsedName = readFontDisplayName(bytes)
            val displayName =
                parsedName?.takeIf { it.isNotBlank() }
                    ?: queryDisplayName(context, uri)
                    ?: targetFile.name
            ImportResult.Success(
                path = targetFile.absolutePath,
                displayName = displayName,
            )
        }.getOrElse {
            targetFile.delete()
            ImportResult.InvalidFont
        }
    }

    fun deleteStoredFontFiles(context: Context) {
        val fontsDirectory = File(context.filesDir, FONTS_DIR_NAME)
        if (!fontsDirectory.isDirectory) return
        fontsDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(STORED_FONT_BASENAME)) {
                file.delete()
            }
        }
    }

    fun loadFontFamily(path: String?): FontFamily? {
        if (path.isNullOrBlank()) return null
        val fontFile = File(path)
        if (!fontFile.isFile) return null
        return runCatching {
            Typeface.createFromFile(fontFile)
            // Register the same file at every standard weight so Compose matches
            // TextStyle.fontWeight to the nearest slot. For variable fonts, each slot
            // applies FontVariation.Settings(weight) and drives the wght axis instead of
            // faux-bolding a single Normal instance.
            FontFamily(
                *customFontWeightStops.map { weight ->
                    Font(fontFile, weight)
                }.toTypedArray(),
            )
        }.getOrNull()
    }

    private val customFontWeightStops =
        arrayOf(
            FontWeight.W100,
            FontWeight.W200,
            FontWeight.W300,
            FontWeight.W400,
            FontWeight.W500,
            FontWeight.W600,
            FontWeight.W700,
            FontWeight.W800,
            FontWeight.W900,
        )

    private fun resolveFontExtension(
        context: Context,
        uri: Uri,
    ): String {
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        when {
            mimeType.contains("otf") -> return "otf"
            mimeType.contains("ttf") -> return "ttf"
        }
        val displayName = queryDisplayName(context, uri)?.lowercase().orEmpty()
        return when {
            displayName.endsWith(".otf") -> "otf"
            displayName.endsWith(".ttf") -> "ttf"
            else -> "ttf"
        }
    }

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex < 0 || !cursor.moveToFirst()) return@use null
            cursor.getString(nameIndex)
        }
    }
}

/** Reads TTF/OTF name table IDs 1 (font family) and 4 (full name), matching ObtainX. */
internal fun readFontDisplayName(bytes: ByteArray): String? {
    return runCatching {
        if (bytes.size < 12) return null
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val tableCount = header.getShort(4).toInt() and 0xFFFF
        if (bytes.size < 12 + tableCount * 16) return null

        var nameTableOffset: Int? = null
        for (tableIndex in 0 until tableCount) {
            val recordOffset = 12 + tableIndex * 16
            if (recordOffset + 16 > bytes.size) break
            val tag =
                String(
                    charArrayOf(
                        bytes[recordOffset].toInt().toChar(),
                        bytes[recordOffset + 1].toInt().toChar(),
                        bytes[recordOffset + 2].toInt().toChar(),
                        bytes[recordOffset + 3].toInt().toChar(),
                    ),
                )
            if (tag == "name") {
                nameTableOffset =
                    ByteBuffer.wrap(bytes, recordOffset + 8, 4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .int
                break
            }
        }

        val nameOffset = nameTableOffset ?: return null
        if (nameOffset < 0 || nameOffset + 6 > bytes.size) return null

        val nameHeader = ByteBuffer.wrap(bytes, nameOffset, 6).order(ByteOrder.BIG_ENDIAN)
        val nameRecordCount = nameHeader.getShort(2).toInt() and 0xFFFF
        val stringStorageOffset = nameHeader.getShort(4).toInt() and 0xFFFF
        val stringStorageStart = nameOffset + stringStorageOffset
        if (stringStorageStart >= bytes.size) return null

        var fontFamilyName: String? = null
        var fullFontName: String? = null

        for (recordIndex in 0 until nameRecordCount) {
            val recordPos = nameOffset + 6 + recordIndex * 12
            if (recordPos + 12 > bytes.size) break
            val recordBuffer = ByteBuffer.wrap(bytes, recordPos, 12).order(ByteOrder.BIG_ENDIAN)
            val platformId = recordBuffer.getShort(0).toInt() and 0xFFFF
            val nameId = recordBuffer.getShort(6).toInt() and 0xFFFF
            val length = recordBuffer.getShort(8).toInt() and 0xFFFF
            val offset = recordBuffer.getShort(10).toInt() and 0xFFFF
            if (nameId != 1 && nameId != 4) continue

            val start = stringStorageStart + offset
            if (start + length > bytes.size) continue
            val nameBytes = bytes.copyOfRange(start, start + length)
            val nameString =
                if (platformId == 3 || platformId == 0) {
                    decodeUtf16Be(nameBytes)
                } else {
                    nameBytes.toString(Charsets.UTF_8)
                }.trim()
            if (nameString.isEmpty()) continue
            if (nameId == 4) {
                fullFontName = nameString
            } else {
                fontFamilyName = nameString
            }
        }

        fullFontName ?: fontFamilyName
    }.getOrNull()
}

private fun decodeUtf16Be(nameBytes: ByteArray): String {
    val builder = StringBuilder()
    var byteIndex = 0
    while (byteIndex + 1 < nameBytes.size) {
        val charCode =
            ((nameBytes[byteIndex].toInt() and 0xFF) shl 8) or
                (nameBytes[byteIndex + 1].toInt() and 0xFF)
        if (charCode != 0) {
            builder.append(charCode.toChar())
        }
        byteIndex += 2
    }
    return builder.toString()
}
