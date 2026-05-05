package dev.bikram.remember.ui.edit

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.bikram.remember.R
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.performLongPressHaptic
import dev.bikram.remember.ui.feedback.rememberPlayTapSound
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import kotlinx.coroutines.launch

@Composable
fun AttachmentsSheet(
    attachments: List<NoteAttachmentEntity>,
    onDismiss: () -> Unit,
    onAdd: (uri: Uri, displayName: String, mimeType: String?) -> Unit,
    onRemove: (id: Long) -> Unit,
) {
    val context = LocalContext.current
    val pickDoc =
        rememberDocumentPicker { uri ->
            persistReadPermission(context, uri)
            onAdd(
                uri,
                resolveDisplayName(context, uri),
                resolveMimeType(context, uri),
            )
        }
    AppBottomSheet(
        title = stringResource(R.string.options_attachments),
        subtitle = stringResource(R.string.attachments_subtitle),
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
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
                attachments.forEach { attachment ->
                    AttachmentRow(attachment, onRemove = { onRemove(attachment.id) })
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        RememberOutlinedButton(
            onClick = { pickDoc.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            RememberMaterialRoundedSymbol(name = "attach_file", weight = FontWeight.Medium)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.attachments_add))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttachmentRow(
    attachment: NoteAttachmentEntity,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val sourceUri = remember(attachment.uri) { attachment.uri.toUri() }
    val rowShape = MaterialTheme.shapes.medium
    val rowInteractionSource = remember { MutableInteractionSource() }
    val pressed by rowInteractionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    var savingPulse by remember { mutableStateOf(false) }
    val savingMix by animateFloatAsState(
        targetValue = if (savingPulse) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "attachmentSavePulse",
    )
    val idleBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val pressedBackground =
        lerp(
            idleBackground,
            MaterialTheme.colorScheme.primary,
            0.22f,
        )
    val savingBackground =
        lerp(
            idleBackground,
            MaterialTheme.colorScheme.tertiaryContainer,
            0.55f,
        )
    val blendedBase = if (pressed) pressedBackground else idleBackground
    val rowBackground by animateColorAsState(
        targetValue = lerp(blendedBase, savingBackground, savingMix),
        animationSpec = motion.defaultEffectsSpec(),
        label = "attachmentRowPress",
    )
    val scaleFactor = 1f - (1f - 0.95f) * savingMix
    val playTap = rememberPlayTapSound()
    val hapticEnabled = LocalHapticEnabled.current
    val hostView = LocalView.current
    Surface(
        shape = rowShape,
        color = rowBackground,
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scaleFactor)
                .clip(rowShape)
                .tapSoundCombinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = {
                        playTap()
                        openUriWithChooser(context, sourceUri, attachment.mimeType)
                    },
                    onLongClick = {
                        if (hapticEnabled) hostView.performLongPressHaptic()
                        scope.launch {
                            savingPulse = true
                            val start = System.currentTimeMillis()
                            copyUriIntoDownloads(
                                context,
                                sourceUri,
                                attachment.displayName,
                                attachment.mimeType,
                            )
                            val elapsed = System.currentTimeMillis() - start
                            if (elapsed < 400) {
                                kotlinx.coroutines.delay(400 - elapsed)
                            }
                            savingPulse = false
                        }
                    },
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = "attach_file",
                size = 22.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    attachment.displayName.ifBlank { "Attachment" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (!attachment.mimeType.isNullOrBlank()) {
                    Text(
                        attachment.mimeType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.size(6.dp))
                MediaStorageChip(
                    storedInApp = AppMediaStorage.isAppStoredMediaUri(context, attachment.uri),
                )
            }
            val cdRemove = stringResource(R.string.common_remove)
            RememberIconButton(onClick = onRemove) {
                RememberMaterialRoundedSymbol(
                    name = "delete_outline",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = cdRemove },
                )
            }
        }
    }
}
