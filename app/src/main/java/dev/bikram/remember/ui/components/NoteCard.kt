package dev.bikram.remember.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import dev.bikram.remember.R
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.HeroFramedImage
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.iconDrawableRes
import dev.bikram.remember.ui.edit.iconEmojiPayload
import dev.bikram.remember.ui.edit.iconSymbolName
import dev.bikram.remember.ui.common.ApplyRichEditorListIndent
import dev.bikram.remember.ui.theme.LocalHeroOnCards
import dev.bikram.remember.ui.theme.elevatedCardColors
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun NoteCard(
    note: NoteWithItems,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val cardColors = elevatedCardColors()
    val visibleTags = RememberReservedTags.userVisibleTags(note.note.tags)
    val hasTagStrip = visibleTags.isNotEmpty()
    val heroEnabled = LocalHeroOnCards.current
    val showHero = heroEnabled && note.note.pictureUri != null
    val surface = MaterialTheme.colorScheme.surface

    val richTextState = rememberRichTextState()
    ApplyRichEditorListIndent(richTextState)
    androidx.compose.runtime.LaunchedEffect(note.note.body) {
        richTextState.setMarkdown(note.note.body)
    }
    val pinnedIconDescription = stringResource(R.string.notecard_pinned_cd)

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedModifier = if (sharedScope != null && navScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-card-${note.note.id}"),
                animatedVisibilityScope = navScope
            )
        }
    } else Modifier

    val selectionBorderColor = MaterialTheme.colorScheme.primary
    val selectionBorder = if (selected) {
        Modifier.border(BorderStroke(2.dp, selectionBorderColor), CardShape)
    } else {
        Modifier
    }
    val clickableModifier = if (onLongClick != null) {
        Modifier.tapSoundCombinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.tapSoundClickable(onClick = onClick)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(sharedModifier)
            .clip(CardShape)
            .then(selectionBorder)
            .then(clickableModifier),
        shape = CardShape,
        color = if (showHero) Color.Transparent else cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = if (showHero) 0.dp else 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showHero) {
                HeroBackground(
                    uri = note.note.pictureUri!!,
                    framing = remember(note.note.pictureHeroFraming) {
                        HeroFraming.fromJsonString(note.note.pictureHeroFraming)
                    },
                    cacheRevision = note.note.updatedAt,
                    scrimTop = surface.copy(alpha = 0.38f),
                    scrimBottom = surface.copy(alpha = 0.72f),
                )
            }
            Row(modifier = Modifier.height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                if (hasTagStrip && !showHero) {
                    TagAccentCardStrip(tags = visibleTags)
                }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val headerSymbol = iconSymbolName(note.note.iconKey)
                        val headerBrandDrawable = iconDrawableRes(note.note.iconKey)
                        val cardEmoji = iconEmojiPayload(note.note.iconKey)
                        if (headerSymbol != null) {
                            RememberMaterialRoundedSymbol(
                                name = headerSymbol,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Medium,
                                modifier = Modifier.alpha(0.75f),
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (headerBrandDrawable != null) {
                            Icon(
                                painterResource(headerBrandDrawable),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
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
                            RememberMaterialRoundedSymbol(
                                name = DEFAULT_LIST_HEADER_SYMBOL,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Medium,
                                modifier = Modifier.alpha(0.65f),
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (note.note.kind == NoteKind.NOTE) {
                            RememberMaterialRoundedSymbol(
                                name = DEFAULT_NOTE_HEADER_SYMBOL,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Medium,
                                modifier = Modifier.alpha(0.65f),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = note.note.title.ifBlank {
                                if (note.note.kind == NoteKind.NOTE) stringResource(R.string.edit_note_title_new)
                                else stringResource(R.string.edit_list_title_new)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (note.note.pinned) {
                            RememberMaterialRoundedSymbol(
                                name = "favorite",
                                size = 16.dp,
                                tint = MaterialTheme.colorScheme.onSurface,
                                weight = FontWeight.Medium,
                                opticalCenterYOffset = 1.dp,
                                modifier = Modifier
                                    .semantics { contentDescription = pinnedIconDescription }
                                    .alpha(0.75f),
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
                                    text = stringResource(R.string.common_empty_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            }
                        }
                        NoteKind.LIST -> ChecklistPreview(note.items)
                    }
                    MetadataRow(note = note, visibleTags = visibleTags)
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        weight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HeroBackground(
    uri: String,
    framing: HeroFraming?,
    cacheRevision: Long,
    scrimTop: Color,
    scrimBottom: Color,
) {
    HeroFramedImage(
        imageUri = uri,
        framing = framing,
        cacheRevision = cacheRevision,
        imageAlpha = 0.52f,
        modifier = Modifier.matchParentSize(),
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(colors = listOf(scrimTop, scrimBottom))),
    )
}

@Composable
private fun MetadataRow(note: NoteWithItems, visibleTags: List<String>) {
    val tags = visibleTags.take(3)
    val extraTags = (visibleTags.size - tags.size).coerceAtLeast(0)
    val reminderAt = note.note.reminderAt
    val isRecurring = note.note.recurrence != null
    val hasPicture = note.note.pictureUri != null
    val hasAttachment = note.attachments.isNotEmpty()
    val anyMetadata = tags.isNotEmpty() || reminderAt != null || hasPicture || hasAttachment
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
            if (hasPicture) {
                val cdPicture = stringResource(R.string.notecard_picture_cd)
                RememberMaterialRoundedSymbol(
                    name = "image",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier = Modifier
                        .semantics { contentDescription = cdPicture }
                        .alpha(0.6f),
                )
            }
            if (hasAttachment) {
                val cdAttachment = stringResource(R.string.notecard_attachment_cd)
                RememberMaterialRoundedSymbol(
                    name = "attach_file",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier = Modifier
                        .semantics { contentDescription = cdAttachment }
                        .alpha(0.6f),
                )
            }
            // Reminder slot - last in the row (rightmost). Replaces the old generic
            // notification icon with a short "MMM d" date string so the user reads the
            // due date directly off the card. When the note is recurring, a tiny
            // repeat icon precedes the date as a glanceable indicator.
            if (reminderAt != null) {
                val cdReminder = stringResource(R.string.notecard_reminder_cd)
                if (isRecurring) {
                    RememberMaterialRoundedSymbol(
                        name = "repeat",
                        size = 14.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Medium,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
                Text(
                    text = formatShortReminderDate(reminderAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .semantics { contentDescription = cdReminder }
                        .alpha(0.85f),
                )
            }
        }
    }
}

/**
 * Short, glanceable due-date label used on note cards. "MMM d" by default ("Apr 27"),
 * which is readable at the small card-metadata size without consuming a year column
 * for dates the user is most likely to care about (the next 12 months). Anything
 * past that horizon falls back to "MMM yyyy" so the card never silently shows a
 * date in the wrong year.
 */
private fun formatShortReminderDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val twelveMonthsMs = 365L * 24 * 60 * 60 * 1000
    val pattern = if (kotlin.math.abs(epochMillis - now) > twelveMonthsMs) "MMM yyyy" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
}

@Composable
private fun TagMini(label: String) {
    TagChipFilled(tag = label, compact = true)
}

@Composable
private fun ChecklistPreview(items: List<ChecklistItemEntity>, limit: Int = 2) {
    if (items.isEmpty()) {
        Text(
            text = "Empty list",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0.5f),
        )
        return
    }
    // Room's @Relation returns items in primary-key order (insertion order), not user-visible
    // order. Match the list editor: unchecked (active) rows first, then checked (completed),
    // each block sorted by sortOrder so the home preview matches what the user sees after
    // checking items off (they move to the completed section rather than staying in insertion
    // order in the flat relation list).
    val ordered =
        items.filter { !it.checked }.sortedBy { it.sortOrder } +
            items.filter { it.checked }.sortedBy { it.sortOrder }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ordered.take(limit).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indent children one gutter's width to mirror the editor hierarchy.
                if (item.depth > 0) Spacer(Modifier.width(16.dp))
                RememberMaterialRoundedSymbol(
                    name = if (item.checked) "check_circle" else "radio_button_unchecked",
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier = Modifier.alpha(if (item.checked) 0.6f else 0.85f),
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
        val extra = ordered.size - limit
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
