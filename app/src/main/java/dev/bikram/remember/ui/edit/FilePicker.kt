package dev.bikram.remember.ui.edit

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): ActivityResultLauncher<PickVisualMediaRequest> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) onPicked(uri)
    }

@Composable
fun rememberDocumentPicker(onPicked: (Uri) -> Unit): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onPicked(uri)
    }

fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

fun resolveDisplayName(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: uri.lastPathSegment.orEmpty()
        }
    }
    return uri.lastPathSegment.orEmpty().ifBlank { "attachment" }
}

fun resolveMimeType(context: Context, uri: Uri): String? {
    val resolver: ContentResolver = context.contentResolver
    return resolver.getType(uri)
}
