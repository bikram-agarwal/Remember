package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.rememberUpdatedState
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
import dev.bikram.remember.data.normalizeTagName
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.rememberBottomSheetStateWithUnsavedChanges
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberUnsavedChangesDialog
import dev.bikram.remember.ui.components.parseHexColor
import dev.bikram.remember.ui.components.tagColor
import dev.bikram.remember.ui.edit.CompactOutlinedField
import dev.bikram.remember.ui.edit.EditableTagHexChip
import dev.bikram.remember.ui.edit.TAG_NAME_MAX_LENGTH
import dev.bikram.remember.ui.edit.TagColorSlider
import dev.bikram.remember.ui.edit.defaultTagColorHex
import dev.bikram.remember.ui.edit.sanitizeTagNameInput
import dev.bikram.remember.ui.edit.toTagHexFieldValue
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay

/**
 * Per-tag intent the user can stage while the sheet is open. [NEUTRAL] means "leave this tag
 * alone on every selected note", [ADD] forces the tag onto every selection, [REMOVE] strips it
 * from every selection that had it.
 */
internal enum class BulkTagIntent { NEUTRAL, ADD, REMOVE }

internal data class BulkTagIntentActions(
    val addTags: Set<String>,
    val removeTags: Set<String>,
    val newTagColors: Map<String, String>,
)

internal fun bulkTagIntentKey(tag: String): String = normalizeTagName(tag)

internal fun resolveBulkTagIntentActions(
    coverage: List<BulkTagCoverage>,
    extraAddedTags: List<String>,
    tagIntents: Map<String, BulkTagIntent>,
    newTagColorsByKey: Map<String, String>,
): BulkTagIntentActions {
    val tagLabelsByKey = LinkedHashMap<String, String>()
    coverage.forEach { item ->
        val trimmedTag = item.tag.trim()
        if (trimmedTag.isNotBlank()) {
            tagLabelsByKey.putIfAbsent(bulkTagIntentKey(trimmedTag), trimmedTag)
        }
    }
    extraAddedTags.forEach { tag ->
        val trimmedTag = tag.trim()
        if (trimmedTag.isNotBlank()) {
            tagLabelsByKey.putIfAbsent(bulkTagIntentKey(trimmedTag), trimmedTag)
        }
    }

    val addKeys =
        tagIntents
            .filterValues { intent -> intent == BulkTagIntent.ADD }
            .keys
            .toSet()
    val removeKeys =
        tagIntents
            .filterValues { intent -> intent == BulkTagIntent.REMOVE }
            .keys
            .toSet()
    val addTags = addKeys.mapNotNull { key -> tagLabelsByKey[key] }.toSet()
    val removeTags = removeKeys.mapNotNull { key -> tagLabelsByKey[key] }.toSet()
    val newTagColors =
        addKeys
            .mapNotNull { key ->
                val tag = tagLabelsByKey[key]
                val hex = newTagColorsByKey[key]
                if (tag != null && hex != null) tag to hex else null
            }.toMap()

    return BulkTagIntentActions(
        addTags = addTags,
        removeTags = removeTags,
        newTagColors = newTagColors,
    )
}

private fun BulkTagCoverageState.neutralTapIntent(): BulkTagIntent =
    when (this) {
        BulkTagCoverageState.ALL -> BulkTagIntent.REMOVE
        BulkTagCoverageState.SOME -> BulkTagIntent.ADD
        BulkTagCoverageState.NONE -> BulkTagIntent.ADD
    }

private fun nextBulkTagIntent(
    coverageState: BulkTagCoverageState,
    intent: BulkTagIntent,
): BulkTagIntent =
    when (intent) {
        BulkTagIntent.NEUTRAL -> coverageState.neutralTapIntent()
        BulkTagIntent.ADD ->
            when (coverageState) {
                BulkTagCoverageState.SOME -> BulkTagIntent.REMOVE
                BulkTagCoverageState.ALL,
                BulkTagCoverageState.NONE,
                -> BulkTagIntent.NEUTRAL
            }
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
internal fun BulkTagSheet(
    tagCoverage: List<BulkTagCoverage>,
    selectedNoteCount: Int,
    onApply: (addTags: Set<String>, removeTags: Set<String>, newTagColors: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val extraAdded = remember { mutableStateListOf<String>() }
    val mergedCoverage by remember(tagCoverage, selectedNoteCount) {
        derivedStateOf {
            val existingCoverage = LinkedHashMap<String, BulkTagCoverage>()
            (
                tagCoverage +
                    extraAdded.map { tag ->
                        BulkTagCoverage(
                            tag = tag,
                            matchCount = 0,
                            totalCount = selectedNoteCount,
                            state = BulkTagCoverageState.NONE,
                        )
                    }
            ).forEach { coverage ->
                val trimmedTag = coverage.tag.trim()
                if (trimmedTag.isNotBlank()) {
                    existingCoverage.putIfAbsent(bulkTagIntentKey(trimmedTag), coverage.copy(tag = trimmedTag))
                }
            }
            existingCoverage.values.sortedWith(bulkTagCoverageSheetOrder)
        }
    }
    val tagIntents = remember { mutableStateMapOf<String, BulkTagIntent>() }
    LaunchedEffect(mergedCoverage) {
        mergedCoverage.forEach { coverage ->
            val tagKey = bulkTagIntentKey(coverage.tag)
            if (!tagIntents.containsKey(tagKey)) {
                tagIntents[tagKey] = BulkTagIntent.NEUTRAL
            }
        }
    }
    val newTagColorsState = remember { mutableStateMapOf<String, String>() }
    val pendingActions by remember {
        derivedStateOf {
            resolveBulkTagIntentActions(
                coverage = mergedCoverage,
                extraAddedTags = extraAdded,
                tagIntents = tagIntents,
                newTagColorsByKey = newTagColorsState,
            )
        }
    }

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
    val normalizedTags = mergedCoverage.map { coverage -> bulkTagIntentKey(coverage.tag) }.toSet()
    val draftIsDuplicate =
        trimmedDraft.isNotBlank() &&
            normalizedTags.contains(bulkTagIntentKey(trimmedDraft))
    val chosenColor: Color = parseHexColor(lastValidHex) ?: TagPalette.presets[0]
    val canStageNewTag = trimmedDraft.isNotBlank() && !draftIsDuplicate && !RememberReservedTags.isSuggestionReserved(trimmedDraft)

    val hasPending = pendingActions.addTags.isNotEmpty() || pendingActions.removeTags.isNotEmpty()
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    val isDirty = hasPending || trimmedDraft.isNotBlank()
    val sheetState =
        rememberBottomSheetStateWithUnsavedChanges(
            isDirty = isDirty,
            onShowDialog = { showUnsavedDialog = true },
        )

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
        val tagKey = bulkTagIntentKey(trimmedDraft)
        extraAdded.add(trimmedDraft)
        tagIntents[tagKey] = BulkTagIntent.ADD
        newTagColorsState[tagKey] = committedHex
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
        subtitleContent = { BulkTagSheetSubtitle() },
        sheetState = sheetState,
        onDismiss = {
            if (isDirty) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        actionsImePadding = true,
        actions = {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
            RememberButton(
                onClick = {
                    onApply(pendingActions.addTags, pendingActions.removeTags, pendingActions.newTagColors)
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
            if (mergedCoverage.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_bulk_tag_no_existing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                BulkTagCoverageFlow(
                    coverage = mergedCoverage,
                    tagIntents = tagIntents,
                    newTagColors = newTagColorsState,
                )
            }

            Spacer(Modifier.height(4.dp))

            // --- Secondary section: pull-tab disclosure + collapsible create form ---
            CreateNewTagPullTab(
                expanded = createExpanded,
                onToggle = { createExpanded = !createExpanded },
            )
            val createSpatialSpec =
                reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>())
            val createFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
            val createFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
            AnimatedVisibility(
                visible = createExpanded,
                enter =
                    fadeIn(animationSpec = createFadeInSpec) +
                        expandVertically(animationSpec = createSpatialSpec),
                exit =
                    fadeOut(animationSpec = createFadeOutSpec) +
                        shrinkVertically(animationSpec = createSpatialSpec),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .animateContentSize(animationSpec = createSpatialSpec),
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
                            counterText =
                                stringResource(
                                    R.string.tag_editor_name_length_counter,
                                    draftName.length,
                                    TAG_NAME_MAX_LENGTH,
                                ),
                            counterHighlighted = draftName.length >= TAG_NAME_MAX_LENGTH,
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

    if (showUnsavedDialog) {
        RememberUnsavedChangesDialog(
            onConfirm = {
                showUnsavedDialog = false
                onDismiss()
            },
            onDismiss = { showUnsavedDialog = false },
        )
    }
}

private const val TAG_BULK_HEX_INPUT_DEBOUNCE_MILLIS = 450L

private val bulkTagCoverageSheetOrder =
    compareBy<BulkTagCoverage> { coverage ->
        when (coverage.state) {
            BulkTagCoverageState.ALL,
            BulkTagCoverageState.SOME,
            -> 0
            BulkTagCoverageState.NONE -> 1
        }
    }.thenBy { coverage -> coverage.tag.lowercase() }

@Composable
private fun BulkTagSheetSubtitle() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.home_bulk_tag_sheet_subtitle_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RememberMaterialRoundedSymbol(
            name = "add",
            size = 16.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.home_bulk_tag_sheet_subtitle_add),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RememberMaterialRoundedSymbol(
            name = "close",
            size = 16.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.home_bulk_tag_sheet_subtitle_remove),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulkTagCoverageFlow(
    coverage: List<BulkTagCoverage>,
    tagIntents: MutableMap<String, BulkTagIntent>,
    newTagColors: Map<String, String>,
) {
    if (coverage.isEmpty()) return

    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        coverage.forEach { tagCoverage ->
            val tagKey = bulkTagIntentKey(tagCoverage.tag)
            val intent = tagIntents[tagKey] ?: BulkTagIntent.NEUTRAL
            val pendingHex = newTagColors[tagKey]
            val chipColor = pendingHex?.let { parseHexColor(it) } ?: tagColor(tagCoverage.tag)
            TriStateTagChip(
                coverage = tagCoverage,
                color = chipColor,
                intent = intent,
                onCycle = {
                    tagIntents[tagKey] = nextBulkTagIntent(tagCoverage.state, intent)
                },
            )
        }
    }
}

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
                    .appClickable(onClick = onToggle)
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
 *  - [BulkTagIntent.NEUTRAL]: shows the tag's current coverage in the selected notes.
 *  - [BulkTagIntent.ADD]: fully filled in the tag's color, leading '+' glyph, bolder label.
 *    Reads as "claimed / committed to every selected note."
 *  - [BulkTagIntent.REMOVE]: transparent interior, 2 dp border in the tag's color, leading 'x'
 *    glyph, strikethrough label. Reads as "stripped / negative space - being removed."
 */
@Composable
private fun TriStateTagChip(
    coverage: BulkTagCoverage,
    color: Color,
    intent: BulkTagIntent,
    onCycle: () -> Unit,
) {
    val baseColor = color
    val tag = coverage.tag

    // All per-intent visual parameters resolved here so the layout block stays clean.
    val containerColor: Color
    val textColor: Color
    val iconColor: Color
    val dotColor: Color
    val textDecoration: TextDecoration
    val leadingIcon: String
    val textWeight: FontWeight
    when (intent) {
        BulkTagIntent.NEUTRAL -> {
            textDecoration = TextDecoration.None
            when (coverage.state) {
                BulkTagCoverageState.ALL -> {
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                    textColor = MaterialTheme.colorScheme.onSurface
                    iconColor = baseColor
                    dotColor = baseColor
                    leadingIcon = "radio_button_checked"
                    textWeight = FontWeight.SemiBold
                }
                BulkTagCoverageState.SOME -> {
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    textColor = MaterialTheme.colorScheme.onSurface
                    iconColor = baseColor
                    dotColor = baseColor.copy(alpha = 0.72f)
                    leadingIcon = "radio_button_partial"
                    textWeight = FontWeight.Medium
                }
                BulkTagCoverageState.NONE -> {
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    iconColor = baseColor.copy(alpha = 0.55f)
                    dotColor = baseColor.copy(alpha = 0.35f)
                    leadingIcon = "radio_button_unchecked"
                    textWeight = FontWeight.Medium
                }
            }
        }
        BulkTagIntent.ADD -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.onSurface
            iconColor = baseColor
            dotColor = baseColor
            textDecoration = TextDecoration.None
            leadingIcon = "add"
            textWeight = FontWeight.SemiBold
        }
        BulkTagIntent.REMOVE -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            iconColor = baseColor.copy(alpha = 0.5f)
            dotColor = baseColor.copy(alpha = 0.3f)
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
                .appClickable(onClick = onCycle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = leadingIcon,
            size = 14.dp,
            tint = iconColor,
            weight = FontWeight.Medium,
        )
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
        )
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
