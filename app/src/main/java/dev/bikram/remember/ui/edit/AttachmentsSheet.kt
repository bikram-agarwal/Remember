package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.ui.common.AppBottomSheet

@Composable
fun AttachmentsSheet(
    attachments: List<NoteAttachmentEntity>,
    onDismiss: () -> Unit,
    onAdd: (uri: Uri, displayName: String, mimeType: String?) -> Unit,
    onRemove: (id: Long) -> Unit,
) {
    val context = LocalContext.current
    val pickDoc = rememberDocumentPicker { uri ->
        persistReadPermission(context, uri)
        onAdd(
            uri,
            resolveDisplayName(context, uri),
            resolveMimeType(context, uri),
        )
    }
    AppBottomSheet(
        title = "Attachments",
        subtitle = "Attach any file — PDFs, docs, audio, video",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    ) {
        if (attachments.isEmpty()) {
            Text(
                "No files attached yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { a ->
                    AttachmentRow(a, onRemove = { onRemove(a.id) })
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        OutlinedButton(
            onClick = { pickDoc.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Add attachment")
        }
    }
}

@Composable
private fun AttachmentRow(a: NoteAttachmentEntity, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.displayName.ifBlank { "Attachment" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (!a.mimeType.isNullOrBlank()) {
                    Text(
                        a.mimeType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
