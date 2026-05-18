package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.parseHexColor
import dev.bikram.remember.ui.components.tagColor
import dev.bikram.remember.ui.edit.CompactOutlinedField
import dev.bikram.remember.ui.edit.EditableTagHexChip
import dev.bikram.remember.ui.edit.TagColorSlider
import dev.bikram.remember.ui.edit.defaultTagColorHex
import dev.bikram.remember.ui.edit.sanitizeTagNameInput
import dev.bikram.remember.ui.edit.toTagHexFieldValue
import dev.bikram.remember.ui.feedback.tapSoundClickable
import kotlinx.coroutines.delay

/**
 * Per-tag intent the user can stage while the sheet is open. [NEUTRAL] means "leave this tag
 * alone on every selected note", [ADD] forces the tag onto every selection, [REMOVE] strips it
 * from every selection that had it.
 */
private enum class BulkTagIntent { NEUTRAL, ADD, REMOVE }

private fun BulkTagIntent.next(): BulkTagIntent =
    when (this) {
        BulkTagIntent.NEUTRAL -> BulkTagIntent.ADD
        BulkTagIntent.ADD -> BulkTagIntent.REMOVE
        BulkTagIntent.REMOVE -> BulkTagIntent.NEUTRAL
    }

/**
 * Bottom sheet for applying / removing tags on a batch of selected notes.
 *
 * Primary affordance is the grid of existing tag chips: each chip cycles NEUTRAL -> ADD -> REMOVE
 * on tap. A secondary "+ Create new tag" pill expands inline to reveal the full tag creation form
 * (name, color slider, hex entry). The Apply button stays anchored to the sheet's action row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkTagSheet(
    availableTags: List<String>,
    onApply: (addTags: Set<String>, removeTags: Set<String>, newTagColors: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val extraAdded = remember { mutableStateListOf<String>() }
    val mergedTags by remember {
        derivedStateOf {
            (availableTags + extraAdded).distinct().sortedBy { it.lowercase() }
        }
    }
    val tagIntents = remember { mutableStateMapOf<String, BulkTagIntent>() }
    LaunchedEffect(mergedTags) {
        mergedTags.forEach { tag ->
            if (!tagIntents.containsKey(tag)) {
                tagIntents[tag] = BulkTagIntent.NEUTRAL
            }
        }
    }
    val newTagColorsState = remember { mutableStateMapOf<String, String>() }

    var createExpanded by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    val presetHex = remember { defaultTagColorHex() }
    var lastValidHex by rememberSaveable { mutableStateOf(presetHex) }
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(presetHex.toTagHexFieldValue())
    }
    var sheetBodyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hexEditorBoundsInRoot by remember { mutableStateOf<Rect?>(null) }

    val trimmedDraft = draftName.trim()
    val normalizedLowerTags = mergedTags.map { it.lowercase() }.toSet()
    val draftIsDuplicate =
        trimmedDraft.isNotBlank() &&
            normalizedLowerTags.contains(trimmedDraft.lowercase())
    val chosenColor: Color = parseHexColor(lastValidHex) ?: TagPalette.presets[0]
    val canStageNewTag = trimmedDraft.isNotBlank() && !draftIsDuplicate && !RememberReservedTags.isSuggestionReserved(trimmedDraft)

    val hasPending = tagIntents.values.any { it != BulkTagIntent.NEUTRAL }

    fun commitHexEditing(): String {
        val draftHex =
            if (hexDraft.text.length == 6) {
                normalizeHex("#${hexDraft.text}")
            } else {
                null
            }
        val committedHex = draftHex ?: lastValidHex
        lastValidHex = committedHex
        hexDraft = committedHex.toTagHexFieldValue()
        hexEditing = false
        hexEditorBoundsInRoot = null
        return committedHex
    }

    fun stageNewTag() {
        if (!canStageNewTag) return
        val committedHex = commitHexEditing()
        extraAdded.add(trimmedDraft)
        tagIntents[trimmedDraft] = BulkTagIntent.ADD
        newTagColorsState[trimmedDraft] = committedHex
        draftName = ""
    }

    LaunchedEffect(hexEditing, hexDraft.text) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(TAG_BULK_HEX_INPUT_DEBOUNCE_MILLIS)
        normalizeHex("#${hexDraft.text}")?.let { normalized ->
            lastValidHex = normalized
        }
    }

    LaunchedEffect(hexEditing, lastValidHex) {
        if (!hexEditing) {
            hexDraft = lastValidHex.toTagHexFieldValue()
        }
    }

    AppBottomSheet(
        title = stringResource(R.string.home_bulk_tag_sheet_title),
        subtitle = stringResource(R.string.home_bulk_tag_sheet_subtitle),
        onDismiss = onDismiss,
        actions = {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            RememberButton(
                onClick = {
                    val adds =
                        tagIntents
                            .filterValues { it == BulkTagIntent.ADD }
                            .keys
                            .toSet()
                    val removes =
                        tagIntents
                            .filterValues { it == BulkTagIntent.REMOVE }
                            .keys
                            .toSet()
                    val colors =
                        newTagColorsState
                            .filterKeys { key -> adds.any { tag -> tag.equals(key, ignoreCase = true) } }
                            .toMap()
                    onApply(adds, removes, colors)
                    // Selection mode exits after apply (VM clears selectedIds) - the sheet must
                    // close with it, otherwise any follow-up Apply lands on an empty selection
                    // and silently no-ops.
                    onDismiss()
                },
                enabled = hasPending,
            ) {
                Text(stringResource(R.string.home_bulk_tag_apply))
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { sheetBodyCoordinates = it }
                    .pointerInput(hexEditing, hexDraft, lastValidHex) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val wasEditingAtDown = hexEditing
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                            if (!wasEditingAtDown || !hexEditing) return@awaitEachGesture
                            val tapInRoot =
                                sheetBodyCoordinates?.localToRoot(up.position)
                                    ?: return@awaitEachGesture
                            val editorBounds = hexEditorBoundsInRoot
                            if (editorBounds == null || !editorBounds.contains(tapInRoot)) {
                                commitHexEditing()
                            }
                        }
                    },
        ) {
            // --- Primary section: existing tag chips, tri-state cycling ---
            if (mergedTags.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_bulk_tag_no_existing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                FlowRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    mergedTags.forEach { tag ->
                        val intent = tagIntents[tag] ?: BulkTagIntent.NEUTRAL
                        // A tag staged during this sheet session has its color in newTagColorsState
                        // but NOT yet in LocalTagColors (that only updates post-apply). Prefer the
                        // pending color so the chip matches what the user just picked in the form.
                        val pendingHex =
                            newTagColorsState.entries
                                .firstOrNull { (tagName) -> tagName.equals(tag, ignoreCase = true) }
                                ?.value
                        val chipColor = pendingHex?.let { parseHexColor(it) } ?: tagColor(tag)
                        TriStateTagChip(
                            tag = tag,
                            color = chipColor,
                            intent = intent,
                            onCycle = { tagIntents[tag] = intent.next() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // --- Secondary section: pull-tab disclosure + collapsible create form ---
            CreateNewTagPullTab(
                expanded = createExpanded,
                onToggle = { createExpanded = !createExpanded },
            )
            AnimatedVisibility(
                visible = createExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .animateContentSize(),
                ) {
                    // Name field + live preview in one row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CompactOutlinedField(
                            value = draftName,
                            onValueChange = { draftName = sanitizeTagNameInput(it) },
                            placeholder = stringResource(R.string.tag_editor_field_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                        EditableTagHexChip(
                            hex = lastValidHex,
                            color = chosenColor,
                            editing = hexEditing,
                            draft = hexDraft,
                            onStartEditing = {
                                hexDraft = lastValidHex.toTagHexFieldValue()
                                hexEditing = true
                            },
                            onDraftChange = { hexDraft = it },
                            onStopEditing = { commitHexEditing() },
                            onBoundsChange = { hexEditorBoundsInRoot = it },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    TagColorSlider(
                        selectedHex = lastValidHex,
                        onSelect = { hex ->
                            lastValidHex = hex
                            hexDraft = hex.toTagHexFieldValue()
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        RememberButton(
                            onClick = { stageNewTag() },
                            enabled = canStageNewTag,
                        ) {
                            Text(stringResource(R.string.home_bulk_tag_stage))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val TAG_BULK_HEX_INPUT_DEBOUNCE_MILLIS = 450L

/**
 * "Pull-tab on divider" disclosure (Option D). A horizontal line crosses the full width with a
 * centered pill sitting astride it - a visual metaphor for "pull me down, there's more below".
 * The pill has a solid [MaterialTheme.colorScheme.surfaceContainerLow] background so it punches
 * through the divider line behind it; the chevron flips to "expand_less" when the section is open.
 */
@Composable
private fun CreateNewTagPullTab(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val cd = stringResource(R.string.home_bulk_tag_create_pill_cd)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    // Match the sheet's actual container color so the pill appears to visually cut the line.
    val sheetSurface = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
    ) {
        // Divider line - spans full width, centered vertically behind the pill.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.Center)
                    .background(dividerColor),
        )
        // The pull-tab pill itself, centered, with solid background to occlude the line.
        Row(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(sheetSurface)
                    .border(width = 1.dp, color = borderColor, shape = CircleShape)
                    .semantics { contentDescription = cd }
                    .tapSoundClickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_bulk_tag_create_pill),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RememberMaterialRoundedSymbol(
                name = if (expanded) "expand_less" else "expand_more",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Tri-state chip: visual weight differs by operation to make ADD (additive, constructive) and
 * REMOVE (subtractive, destructive) read distinctly at a glance.
 *
 *  - [BulkTagIntent.NEUTRAL]: ghosted - faded tag color, no icon. "Leave alone."
 *  - [BulkTagIntent.ADD]: fully filled in the tag's color, leading '+' glyph, bolder label.
 *    Reads as "claimed / committed to every selected note."
 *  - [BulkTagIntent.REMOVE]: transparent interior, 2 dp border in the tag's color, leading 'x'
 *    glyph, strikethrough label. Reads as "stripped / negative space - being removed."
 *
 * Tapping cycles NEUTRAL -> ADD -> REMOVE -> NEUTRAL.
 */
@Composable
private fun TriStateTagChip(
    tag: String,
    color: Color,
    intent: BulkTagIntent,
    onCycle: () -> Unit,
) {
    val baseColor = color
    val onBaseColor = TagPalette.textOn(baseColor)

    // All per-intent visual parameters resolved here so the layout block stays clean.
    val containerColor: Color
    val textColor: Color
    val iconColor: Color
    val borderStroke: BorderStroke?
    val textDecoration: TextDecoration
    val leadingIcon: String?
    val textWeight: FontWeight
    when (intent) {
        BulkTagIntent.NEUTRAL -> {
            containerColor = baseColor.copy(alpha = 0.35f)
            textColor = onBaseColor
            iconColor = onBaseColor
            borderStroke = null
            textDecoration = TextDecoration.None
            leadingIcon = null
            textWeight = FontWeight.Medium
        }
        BulkTagIntent.ADD -> {
            containerColor = baseColor
            textColor = onBaseColor
            iconColor = onBaseColor
            borderStroke = null
            textDecoration = TextDecoration.None
            leadingIcon = "add"
            textWeight = FontWeight.SemiBold
        }
        BulkTagIntent.REMOVE -> {
            containerColor = Color.Transparent
            textColor = baseColor
            iconColor = baseColor
            borderStroke = BorderStroke(2.dp, baseColor)
            textDecoration = TextDecoration.LineThrough
            leadingIcon = "close"
            textWeight = FontWeight.Medium
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(CircleShape)
                .background(containerColor)
                .let { base ->
                    if (borderStroke != null) {
                        base.border(
                            width = borderStroke.width,
                            brush = borderStroke.brush,
                            shape = CircleShape,
                        )
                    } else {
                        base
                    }
                }.tapSoundClickable(onClick = onCycle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (leadingIcon != null) {
            RememberMaterialRoundedSymbol(
                name = leadingIcon,
                size = 14.dp,
                tint = iconColor,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = tag,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = textWeight,
                    textDecoration = textDecoration,
                ),
            color = textColor,
            maxLines = 1,
        )
    }
}
