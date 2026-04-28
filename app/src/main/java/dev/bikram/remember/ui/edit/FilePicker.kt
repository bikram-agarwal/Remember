package dev.bikram.remember.ui.edit

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.bikram.remember.R
import java.io.File
import java.io.IOException

@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): ActivityResultLauncher<PickVisualMediaRequest> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) onPicked(uri)
    }

/**
 * Opens the photo picker, copies the selection into app-private `note_heroes/` when possible,
 * then invokes [onImageReady] with the stored URI string and the private file when copy succeeded.
 */
@Composable
fun rememberHeroImagePickThenCopy(
    onImageReady: (uriString: String, copiedPrivateFile: File?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { picked: Uri? ->
            if (picked == null) return@rememberLauncherForActivityResult
            persistReadPermission(context, picked)
            finalizeHeroImageToPrivateStorage(context, picked, onImageReady)
        }
    return remember(pickLauncher) {
        {
            pickLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}

/**
 * Copies [sourceUri] into [filesDir]/note_heroes/ when possible.
 * On success: [onResult] receives the FileProvider URI string and the destination [File].
 * On failure: [onResult] receives the original URI string and null file.
 */
fun finalizeHeroImageToPrivateStorage(
    context: Context,
    sourceUri: Uri,
    onResult: (uriString: String, copiedPrivateFile: File?) -> Unit,
) {
    persistReadPermission(context, sourceUri)
    val heroesDir = File(context.filesDir, "note_heroes").apply { mkdirs() }
    val destFile = File(heroesDir, "cover_${System.currentTimeMillis()}.jpg")
    val copyResult =
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("openInputStream failed")
        }
    if (copyResult.isSuccess) {
        onResult(
            FileProvider
                .getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    destFile,
                ).toString(),
            destFile,
        )
    } else {
        runCatching { destFile.delete() }
        onResult(sourceUri.toString(), null)
    }
}

@Composable
fun rememberDocumentPicker(onPicked: (Uri) -> Unit): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onPicked(uri)
    }

fun persistReadPermission(
    context: Context,
    uri: Uri,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String {
    val resolver = context.contentResolver
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx) ?: uri.lastPathSegment.orEmpty()
        }
    }
    return uri.lastPathSegment.orEmpty().ifBlank { "attachment" }
}

fun resolveMimeType(
    context: Context,
    uri: Uri,
): String? {
    val resolver: ContentResolver = context.contentResolver
    return resolver.getType(uri)
}

fun openUriWithChooser(
    context: Context,
    uri: Uri,
    mimeType: String?,
) {
    val resolvedType = mimeType?.takeUnless { it.isBlank() } ?: "*/*"
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser = Intent.createChooser(intent, null)
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(chooser)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_open_attachment_failed), Toast.LENGTH_SHORT).show()
    }
}

suspend fun copyUriIntoDownloads(
    context: Context,
    sourceUri: Uri,
    displayName: String,
    mimeType: String?,
) {
    val resolver = context.contentResolver
    val safeName = displayName.ifBlank { "attachment" }.replace('/', '_')
    val values =
        ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType?.takeUnless { it.isBlank() } ?: "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val itemUri =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { resolver.insert(collection, values) }.getOrNull()
        }

    if (itemUri == null) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.toast_save_download_failed), Toast.LENGTH_SHORT).show()
        }
        return
    }

    val writeResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(itemUri, "w")?.use { output ->
                        input.copyTo(output)
                    } ?: throw IOException("openOutputStream failed")
                } ?: throw IOException("openInputStream failed")
            }
        }

    if (writeResult.isFailure) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { resolver.delete(itemUri, null, null) }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.toast_save_download_failed), Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
        }
    }

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        Toast.makeText(context, context.getString(R.string.toast_saved_to_downloads), Toast.LENGTH_SHORT).show()
    }
}
