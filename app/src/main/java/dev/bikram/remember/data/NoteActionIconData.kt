package dev.bikram.remember.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream

private const val NOTE_ACTION_ICON_SIZE_PX = 96

fun Drawable.toNoteActionIconData(): String? =
    runCatching {
        val bitmap = Bitmap.createBitmap(NOTE_ACTION_ICON_SIZE_PX, NOTE_ACTION_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(bounds)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        setBounds(oldBounds)
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull()

fun String?.toNoteActionIconBitmap(): Bitmap? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

fun String?.toNoteActionIconDrawable(resources: Resources): Drawable? =
    toNoteActionIconBitmap()?.let { BitmapDrawable(resources, it) }
