package dev.bikram.remember.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import dev.bikram.remember.ui.edit.iconEmojiPayload
import dev.bikram.remember.ui.edit.iconFor
import dev.bikram.remember.ui.common.ApplyRichEditorListIndent
import dev.bikram.remember.ui.theme.LocalHeroOnCards
import dev.bikram.remember.ui.theme.elevatedCardColors

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun NoteCard(
    note: NoteWithItems,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColors = elevatedCardColors()
    val hasTagStrip = note.note.tags.isNotEmpty()
    val heroEnabled = LocalHeroOnCards.current
    val showHero = heroEnabled && note.note.pictureUri != null
    val surface = MaterialTheme.colorScheme.surface
    
    val richTextState = rememberRichTextState()
    ApplyRichEditorListIndent(richTextState)
    androidx.compose.runtime.LaunchedEffect(note.note.body) {
        richTextState.setMarkdown(note.note.body)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onClick),
        shape = CardShape,
        color = if (showHero) Color.Transparent else cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = if (showHero) 0.dp else 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showHero) {
                HeroBackground(uri = note.note.pictureUri!!, scrimTop = surface.copy(alpha = 0.50f), scrimBottom = surface.copy(alpha = 0.85f))
            }
            Row(modifier = Modifier.height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                if (hasTagStrip && !showHero) {
                    TagAccentCardStrip(tags = note.note.tags)
                }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val customIcon = iconFor(note.note.iconKey)
                        val cardEmoji = iconEmojiPayload(note.note.iconKey)
                        if (customIcon != null) {
                            Icon(
                                customIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).alpha(0.75f),
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (cardEmoji != null) {
                            Text(
                                text = cardEmoji,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                modifier = Modifier.alpha(0.85f),
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (note.note.kind == NoteKind.LIST) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).alpha(0.65f),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = note.note.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (note.note.pinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp).alpha(0.75f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    when (note.note.kind) {
                        NoteKind.NOTE -> {
                            if (note.note.body.isNotBlank()) {
                                RichText(
                                    state = richTextState,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(0.82f),
                                )
                            } else {
                                Text(
                                    text = "Empty note",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            }
                        }
                        NoteKind.LIST -> ChecklistPreview(note.items)
                    }
                    MetadataRow(note = note)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HeroBackground(uri: String, scrimTop: Color, scrimBottom: Color) {
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = 0.32f },
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(colors = listOf(scrimTop, scrimBottom))),
    )
}

@Composable
private fun MetadataRow(note: NoteWithItems) {
    val tags = note.note.tags.take(3)
    val extraTags = (note.note.tags.size - tags.size).coerceAtLeast(0)
    val hasReminder = note.note.reminderAt != null
    val hasPicture = note.note.pictureUri != null
    val hasAttachment = note.attachments.isNotEmpty()
    val anyMetadata = tags.isNotEmpty() || hasReminder || hasPicture || hasAttachment
    if (!anyMetadata) return

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag -> TagMini(tag) }
            if (extraTags > 0) {
                Text(
                    text = "+$extraTags",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.alpha(0.7f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasReminder) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Has reminder",
                    modifier = Modifier.size(14.dp).alpha(0.6f),
                )
            }
            if (hasPicture) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "Has picture",
                    modifier = Modifier.size(14.dp).alpha(0.6f),
                )
            }
            if (hasAttachment) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Has attachment",
                    modifier = Modifier.size(14.dp).alpha(0.6f),
                )
            }
        }
    }
}

@Composable
private fun TagMini(label: String) {
    TagChipFilled(tag = label, compact = true)
}

@Composable
private fun ChecklistPreview(items: List<ChecklistItemEntity>, limit: Int = 4) {
    if (items.isEmpty()) {
        Text(
            text = "Empty list",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0.5f),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.take(limit).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).alpha(if (item.checked) 0.6f else 0.85f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.alpha(if (item.checked) 0.55f else 0.92f),
                )
            }
        }
        val extra = items.size - limit
        if (extra > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "+ $extra more",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}

