package dev.bikram.remember.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.AppMediaStorage
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteAttachmentEntity
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.feedback.tapSoundClickable
import java.text.DateFormat
import java.util.Date
import dev.bikram.remember.data.Visibility as NoteVisibility

private val OPTION_CELL_HEIGHT = 56.dp
private const val TAG_PILLS_MAX_LINES = 2

@Composable
fun OptionsPanel(
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
    importance: Importance,
    visibility: NoteVisibility,
    pictureUri: String?,
    iconKey: String?,
    isChecklist: Boolean,
    actions: List<NoteAction>,
    tags: List<String>,
    attachments: List<NoteAttachmentEntity>,
    onOpenReminder: () -> Unit,
    onSetImportance: (Importance) -> Unit,
    onSetVisibility: (NoteVisibility) -> Unit,
    onOpenPicture: () -> Unit,
    onOpenIcon: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenAttachments: () -> Unit,
    modifier: Modifier = Modifier,
    // When true (archived / trashed), the Importance row's internal bottom-sheet trigger is
    // suppressed. Every OTHER row already no-ops through its caller-provided `onOpen*` lambda
    // when read-only, but Importance owns its own sheet so the gate has to live here.
    readOnly: Boolean = false,
    // Mirrors the starred-card visual treatment from the Home/History grid: a subtle yellow
    // wash on the panel surface plus a tilted watermark star at the top-end. Keeps the
    // starred cue consistent when the user opens a starred note or list into the editor.
    starred: Boolean = false,
) {
    var importanceOpen by rememberSaveable { mutableStateOf(false) }
    var visibilityOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val storedAttachmentCount =
        attachments.count { attachment ->
            AppMediaStorage.isAppStoredMediaUri(context, attachment.uri)
        }
    val linkedAttachmentCount = attachments.size - storedAttachmentCount
    val attachmentSummary =
        if (attachments.isEmpty()) {
            attachmentsSummary(attachments.size)
        } else {
            attachmentStorageSummary(
                fileSummary = attachmentsSummary(attachments.size),
                storedCount = storedAttachmentCount,
                linkedCount = linkedAttachmentCount,
            )
        }

    val baseContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    // Same recipe as NoteCard: blend ~7% of the starred-yellow swatch into the panel's
    // surface tint when the open note/list is starred. Non-starred is a no-op.
    val tintedContainerColor =
        if (starred) {
            lerp(baseContainerColor, Color(0xFFFFD54F), 0.07f)
        } else {
            baseContainerColor
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = tintedContainerColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Watermark star for starred cards, mirrored from NoteCard. Tilted ~-15deg
            // and very low alpha so it reads as a soft starred cue without competing with
            // the option rows. Sits behind the Column so taps still land on rows.
            if (starred) {
                RememberMaterialRoundedSymbol(
                    name = "star",
                    filled = true,
                    size = 96.dp,
                    tint = Color(0xFFFFD54F),
                    weight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                rotationZ = -15f
                                alpha = 0.13f
                            },
                )
            }
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                OptionRow(
                    symbolName = "label",
                    title = stringResource(R.string.options_tags),
                    summary = if (tags.isEmpty()) stringResource(R.string.common_none) else "",
                    onClick = onOpenTags,
                    summaryContent =
                        if (tags.isEmpty()) {
                            null
                        } else {
                            { TagPillsRow(tags = tags) }
                        },
                )
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OptionCell(
                        symbolName = "alarm",
                        title = stringResource(R.string.options_reminder),
                        summary = reminderSummary(reminderAt, recurrence),
                        onClick = onOpenReminder,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OptionCell(
                        symbolName = actions.firstOrNull()?.type?.materialSymbolName() ?: "bolt",
                        title = stringResource(R.string.options_actions),
                        summary = actionsSummary(actions),
                        onClick = onOpenActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        OptionCell(
                            symbolName = "add_a_photo",
                            title = stringResource(R.string.options_picture),
                            summary =
                                if (pictureUri == null) {
                                    stringResource(R.string.options_picture_none)
                                } else {
                                    stringResource(R.string.options_picture_attached)
                                },
                            onClick = onOpenPicture,
                            modifier = Modifier.weight(1f),
                        )
                        OptionCell(
                            symbolName = "attach_file",
                            title = stringResource(R.string.options_attachments),
                            summary = attachmentSummary,
                            onClick = onOpenAttachments,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            OptionPill(
                                symbolName = "emoji_symbols",
                                label = stringResource(R.string.options_icon),
                                value =
                                    iconLabelFor(iconKey)
                                        ?: stringResource(
                                            if (isChecklist) {
                                                R.string.options_icon_default_checklist
                                            } else {
                                                R.string.options_icon_default_note
                                            },
                                        ),
                                onClick = onOpenIcon,
                            )
                        }
                        item {
                            OptionPill(
                                symbolName = "priority_high",
                                label = stringResource(R.string.options_importance),
                                value = importance.label(),
                                onClick = if (readOnly) null else ({ importanceOpen = true }),
                            )
                        }
                        item {
                            OptionPill(
                                symbolName = "visibility",
                                label = stringResource(R.string.options_visibility),
                                value = visibility.label(),
                                onClick = if (readOnly) null else ({ visibilityOpen = true }),
                            )
                        }
                    }
                }
            }
        }
    }

    if (visibilityOpen) {
        ChoiceSheet(
            title = stringResource(R.string.options_visibility),
            options =
                NoteVisibility.entries.map {
                    ChoiceOption(it, it.label(), it.description())
                },
            selected = visibility,
            onPick = {
                onSetVisibility(it)
                visibilityOpen = false
            },
            onDismiss = { visibilityOpen = false },
        )
    }
    if (importanceOpen) {
        ChoiceSheet(
            title = stringResource(R.string.options_importance),
            options =
                Importance.entries.map {
                    ChoiceOption(it, it.label(), it.description())
                },
            selected = importance,
            onPick = {
                onSetImportance(it)
                importanceOpen = false
            },
            onDismiss = { importanceOpen = false },
        )
    }
}

@Composable
private fun OptionRow(
    symbolName: String,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    summaryContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.tapSoundClickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summaryContent != null) {
                Spacer(Modifier.size(6.dp))
                summaryContent()
            } else {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Grid cell used by the 2-column zone of the options panel. The fixed height keeps
 * paired cells symmetrical even when a value is long; overflowing metadata is
 * ellipsized because tapping the cell opens the full editor/sheet.
 *
 * Passing a null [onClick] drops the
 * clickable modifier so archived / trashed shelves can show inert cells. Surface
 * clips its content to the rounded shape, so the click ripple stays inside the card.
 */
@Composable
private fun OptionCell(
    symbolName: String,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    fixedHeight: Boolean = true,
    summaryContent: (@Composable () -> Unit)? = null,
) {
    val tileModifier =
        if (fixedHeight) {
            modifier.height(OPTION_CELL_HEIGHT)
        } else {
            modifier.heightIn(min = OPTION_CELL_HEIGHT)
        }
    Surface(
        modifier = tileModifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .let { if (fixedHeight) it.fillMaxHeight() else it }
                    .let { if (onClick != null) it.tapSoundClickable(onClick = onClick) else it }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RememberMaterialRoundedSymbol(
                name = symbolName,
                size = 19.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summaryContent != null) {
                    Spacer(Modifier.size(4.dp))
                    summaryContent()
                } else {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionPill(
    symbolName: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier =
            modifier
                .background(containerColor, shape)
                .border(0.dp, Color.Transparent, shape)
                .let {
                    if (onClick != null) {
                        it.clip(shape).tapSoundClickable(onClick = onClick)
                    } else {
                        it
                    }
                }.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = symbolName,
            size = 14.dp,
            tint = contentColor.copy(alpha = 0.72f),
            weight = FontWeight.Medium,
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.72f),
            fontWeight = FontWeight.Medium,
            softWrap = false,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            softWrap = false,
        )
    }
}

@Composable
fun MediaStorageChip(
    storedInApp: Boolean,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            if (storedInApp) {
                R.string.media_storage_stored_in_app
            } else {
                R.string.media_storage_linked_original
            },
        )
    val statusContentDescription = stringResource(R.string.media_storage_status_cd)
    val linkedOriginalHint = stringResource(R.string.media_storage_linked_original_hint)
    val chipContentDescription =
        if (storedInApp) {
            "$statusContentDescription: $label"
        } else {
            "$statusContentDescription: $label. $linkedOriginalHint"
        }
    Surface(
        modifier =
            modifier.semantics {
                this.contentDescription = chipContentDescription
            },
        shape = MaterialTheme.shapes.extraExtraLarge,
        color =
            if (storedInApp) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (storedInApp) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TagPillsRow(tags: List<String>) {
    val horizontalSpacing = 6.dp
    val verticalSpacing = 6.dp

    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val horizontalSpacingPx = horizontalSpacing.roundToPx()
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val tagPlaceables =
            subcompose("tags") {
                tags.forEach { tag ->
                    TagChipFilled(tag = tag, compact = true)
                }
            }.map { measurable ->
                measurable.measure(looseConstraints)
            }
        val unboundedLineWidth =
            tagPlaceables.sumOf { placeable -> placeable.width } +
                horizontalSpacingPx * (tagPlaceables.size - 1).coerceAtLeast(0)
        val maxLineWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                unboundedLineWidth
            }

        fun buildPlacements(placeables: List<Placeable>): List<TagChipPlacement> {
            var currentLine = 1
            var cursorLeft = 0
            var cursorTop = 0
            var currentLineHeight = 0
            val placements = mutableListOf<TagChipPlacement>()

            placeables.forEach { placeable ->
                val spacingBeforeItem = if (cursorLeft == 0) 0 else horizontalSpacingPx
                val itemRight = cursorLeft + spacingBeforeItem + placeable.width
                val shouldWrap = cursorLeft > 0 && itemRight > maxLineWidth

                if (shouldWrap) {
                    currentLine += 1
                    if (currentLine > TAG_PILLS_MAX_LINES) {
                        return placements
                    }
                    cursorLeft = 0
                    cursorTop += currentLineHeight + verticalSpacingPx
                    currentLineHeight = 0
                }

                val itemLeft = if (cursorLeft == 0) 0 else cursorLeft + horizontalSpacingPx
                placements +=
                    TagChipPlacement(
                        placeable = placeable,
                        left = itemLeft,
                        top = cursorTop,
                    )
                cursorLeft = itemLeft + placeable.width
                currentLineHeight = maxOf(currentLineHeight, placeable.height)
            }

            return placements
        }

        var selectedPlacements: List<TagChipPlacement> = emptyList()
        for (visibleTagCount in tagPlaceables.size downTo 0) {
            val hiddenCount = tags.size - visibleTagCount
            val overflowPlaceable =
                if (hiddenCount > 0) {
                    subcompose("overflow:$hiddenCount") {
                        MoreTagsChip(count = hiddenCount)
                    }.first().measure(looseConstraints)
                } else {
                    null
                }
            val candidatePlaceables =
                if (overflowPlaceable == null) {
                    tagPlaceables
                } else {
                    tagPlaceables.take(visibleTagCount) + overflowPlaceable
                }
            val candidatePlacements = buildPlacements(candidatePlaceables)

            if (candidatePlacements.size == candidatePlaceables.size) {
                selectedPlacements = candidatePlacements
                break
            }
        }

        val layoutWidth = maxLineWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight =
            (
                selectedPlacements.maxOfOrNull { placement ->
                    placement.top + placement.placeable.height
                } ?: 0
            ).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(layoutWidth, layoutHeight) {
            selectedPlacements.forEach { placement ->
                placement.placeable.placeRelative(placement.left, placement.top)
            }
        }
    }
}

private data class TagChipPlacement(
    val placeable: Placeable,
    val left: Int,
    val top: Int,
)

@Composable
private fun MoreTagsChip(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
    ) {
        Text(
            text = stringResource(R.string.options_tags_more_count, count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private data class ChoiceOption<T>(
    val value: T,
    val label: String,
    val description: String,
)

@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = title,
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    ) {
        options.forEach { option ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tapSoundClickable { onPick(option.value) }
                        .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option.value == selected,
                    onClick = { onPick(option.value) },
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        option.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Importance.label(): String = stringResource(labelRes())

@Composable
private fun Importance.description(): String =
    stringResource(
        when (this) {
            Importance.LOW -> R.string.importance_low_description
            Importance.DEFAULT -> R.string.importance_default_description
            Importance.HIGH -> R.string.importance_high_description
        },
    )

@Composable
private fun NoteVisibility.label(): String = stringResource(labelRes())

@Composable
private fun NoteVisibility.description(): String =
    stringResource(
        when (this) {
            NoteVisibility.DEFAULT -> R.string.visibility_default_description
            NoteVisibility.PRIVATE -> R.string.visibility_private_description
            NoteVisibility.SECRET -> R.string.visibility_secret_description
        },
    )

@Composable
private fun reminderSummary(
    reminderAt: Long?,
    recurrence: RecurrenceRule?,
): String {
    if (reminderAt == null) return stringResource(R.string.common_none)
    val datePart = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(reminderAt))
    val rule = recurrence?.sanitized() ?: return datePart
    val recurrenceLabel = compactRecurrenceLabel(rule)
    return if (recurrenceLabel.isEmpty()) datePart else "$datePart  |  $recurrenceLabel"
}

/**
 * Short, human-readable recurrence label for the reminder pill summary line. Mirrors
 * the long-form summary the picker emits but keeps it to one or two words ("Daily",
 * "Every 3 weeks", "Monthly") so the pill stays compact.
 */
@Composable
private fun compactRecurrenceLabel(rule: RecurrenceRule): String {
    val interval = rule.interval.coerceAtLeast(1)
    return if (interval == 1) {
        when (rule.unit) {
            RecurrenceUnit.DAY -> stringResource(R.string.reminder_recurrence_daily)
            RecurrenceUnit.WEEK -> stringResource(R.string.reminder_recurrence_weekly)
            RecurrenceUnit.MONTH -> stringResource(R.string.reminder_recurrence_monthly)
            RecurrenceUnit.YEAR -> stringResource(R.string.reminder_recurrence_yearly)
        }
    } else {
        pluralStringResource(
            when (rule.unit) {
                RecurrenceUnit.DAY -> R.plurals.reminder_recurrence_every_days
                RecurrenceUnit.WEEK -> R.plurals.reminder_recurrence_every_weeks
                RecurrenceUnit.MONTH -> R.plurals.reminder_recurrence_every_months
                RecurrenceUnit.YEAR -> R.plurals.reminder_recurrence_every_years
            },
            interval,
            interval,
        )
    }
}

@Composable
private fun actionsSummary(actions: List<NoteAction>): String =
    when (actions.size) {
        0 -> stringResource(R.string.common_none)
        1 ->
            actions[0].title.ifBlank {
                pluralStringResource(R.plurals.options_actions_count, 1, 1)
            }
        else -> pluralStringResource(R.plurals.options_actions_count, actions.size, actions.size)
    }

@Composable
private fun attachmentStorageSummary(
    fileSummary: String,
    storedCount: Int,
    linkedCount: Int,
): String {
    val storedSummary =
        pluralStringResource(
            R.plurals.media_storage_stored_count,
            storedCount,
            storedCount,
        )
    val linkedSummary =
        pluralStringResource(
            R.plurals.media_storage_linked_count,
            linkedCount,
            linkedCount,
        )
    return stringResource(
        R.string.options_attachments_storage_summary,
        fileSummary,
        storedSummary,
        linkedSummary,
    )
}

@Composable
private fun attachmentsSummary(count: Int): String =
    when (count) {
        0 -> stringResource(R.string.common_none)
        else -> pluralStringResource(R.plurals.options_attachments_file_count, count, count)
    }
