package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.data.normalizeTagName
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.HueColorSlider
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.colorHexFromHue
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.RememberUnsavedChangesDialog
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.components.parseHexColor
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.tags.LocalTagColors
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import java.util.Locale

private val FieldHeight = 40.dp
internal const val TAG_NAME_MAX_LENGTH = 20

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagEditorSheet(
    initial: List<String>,
    availableTags: List<String>,
    onConfirm: (List<String>, Map<String, String>) -> Unit,
    onEditExistingTag: (oldName: String, newName: String, colorHex: String?, resetColor: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tags by rememberSaveable { mutableStateOf(initial) }
    var knownTagOptions by rememberSaveable { mutableStateOf((availableTags + initial).distinctByNormalizedName()) }
    var pendingAddTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingRemoveTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    val firstHex = defaultTagColorHex()
    var lastValidHex by rememberSaveable { mutableStateOf(firstHex) }
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(firstHex.toTagHexFieldValue())
    }
    var sheetBodyCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hexEditorBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var localColors by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editingTag by rememberSaveable { mutableStateOf<String?>(null) }
    val tagColorMap = LocalTagColors.current
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val trimmedDraft = draftName.trim()

    fun currentDraftHexOrLastValid(): String =
        if (hexEditing && hexDraft.text.length == 6) {
            normalizeHex("#${hexDraft.text}") ?: lastValidHex
        } else {
            lastValidHex
        }

    val committedDraftHex: String = currentDraftHexOrLastValid()
    val chosenColor: Color = parseHexColor(committedDraftHex) ?: TagPalette.presets[0]

    fun hexForTag(tag: String): String {
        val key = normalizeTagName(tag)
        val localHex =
            localColors.entries
                .firstOrNull { colorEntry ->
                    normalizeTagName(colorEntry.key) == key
                }?.value
        return localHex ?: tagColorMap[key] ?: paletteHex(TagPalette.defaultFor(key))
    }

    fun removeTagByName(
        sourceTags: List<String>,
        tag: String,
    ): List<String> = sourceTags.filterNot { sourceTag -> sourceTag.equals(tag, ignoreCase = true) }

    fun addTagByName(
        sourceTags: List<String>,
        tag: String,
    ): List<String> {
        if (sourceTags.any { sourceTag -> sourceTag.equals(tag, ignoreCase = true) }) {
            return sourceTags
        }
        return sourceTags + tag
    }

    fun togglePendingAdd(tag: String) {
        pendingAddTags =
            if (pendingAddTags.any { pendingTag -> pendingTag.equals(tag, ignoreCase = true) }) {
                removeTagByName(pendingAddTags, tag)
            } else {
                addTagByName(pendingAddTags, tag)
            }
    }

    fun togglePendingRemoval(tag: String) {
        pendingRemoveTags =
            if (pendingRemoveTags.any { pendingTag -> pendingTag.equals(tag, ignoreCase = true) }) {
                removeTagByName(pendingRemoveTags, tag)
            } else {
                addTagByName(pendingRemoveTags, tag)
            }
    }

    fun clearEditSelection() {
        editingTag = null
        draftName = ""
        lastValidHex = firstHex
        hexDraft = firstHex.toTagHexFieldValue()
        hexEditing = false
        hexEditorBoundsInRoot = null
    }

    fun selectForEditing(displayTag: String) {
        val trim = displayTag.trim()
        if (trim.isBlank()) return
        editingTag = trim
        draftName = trim
        val existingHex = hexForTag(trim)
        lastValidHex = existingHex
        hexDraft = existingHex.toTagHexFieldValue()
        hexEditing = false
        hexEditorBoundsInRoot = null
    }

    fun commitHexEditing(): String {
        val committedHex = currentDraftHexOrLastValid()
        lastValidHex = committedHex
        hexDraft = committedHex.toTagHexFieldValue()
        hexEditing = false
        hexEditorBoundsInRoot = null
        return committedHex
    }

    fun commitOnSave() {
        val committedHex = commitHexEditing()
        val trimmed = trimmedDraft
        val tagBeingEdited = editingTag
        val editDirty =
            editMode &&
                tagBeingEdited != null &&
                trimmed.isNotBlank() &&
                (
                    !tagBeingEdited.equals(trimmed, ignoreCase = true) ||
                        !hexForTag(tagBeingEdited).equals(committedHex, ignoreCase = true)
                )
        val draftTagMatch = knownTagOptions.firstOrNull { tag -> tag.equals(trimmed, ignoreCase = true) }
        val colorsToSave = mutableMapOf<String, String>()

        if (editDirty) {
            val editedTag = tagBeingEdited
            onEditExistingTag(editedTag, trimmed, committedHex, false)
            localColors =
                localColors
                    .filterKeys { tagName -> !tagName.equals(editedTag, ignoreCase = true) }
                    .plus(trimmed to committedHex)
            tags =
                tags
                    .map { tag ->
                        if (tag.equals(editedTag, ignoreCase = true)) trimmed else tag
                    }.distinctBy { tag -> tag.lowercase() }
            knownTagOptions =
                knownTagOptions
                    .map { tag ->
                        if (tag.equals(editedTag, ignoreCase = true)) trimmed else tag
                    }.distinctByNormalizedName()
            pendingAddTags =
                pendingAddTags
                    .map { tag ->
                        if (tag.equals(editedTag, ignoreCase = true)) trimmed else tag
                    }.distinctByNormalizedName()
            pendingRemoveTags =
                pendingRemoveTags
                    .map { tag ->
                        if (tag.equals(editedTag, ignoreCase = true)) trimmed else tag
                    }.distinctByNormalizedName()
        }

        var finalTags =
            tags.filterNot { tag ->
                pendingRemoveTags.any { pendingTag -> pendingTag.equals(tag, ignoreCase = true) }
            }
        pendingAddTags.forEach { tag ->
            finalTags = addTagByName(finalTags, tag)
        }

        if (trimmed.isNotBlank() && tagBeingEdited == null) {
            val tagToAssign = draftTagMatch ?: trimmed
            finalTags = addTagByName(finalTags, tagToAssign)
            if (draftTagMatch == null) {
                colorsToSave[tagToAssign] = committedHex
                localColors = localColors + (tagToAssign to committedHex)
                knownTagOptions = addTagByName(knownTagOptions, tagToAssign)
            }
        }
        finalTags = finalTags.distinctByNormalizedName()
        tags = finalTags
        knownTagOptions = (knownTagOptions + finalTags).distinctByNormalizedName()
        pendingAddTags = emptyList()
        pendingRemoveTags = emptyList()
        clearEditSelection()
        onConfirm(finalTags, colorsToSave)
    }

    val allTags: List<String> =
        run {
            val seen = LinkedHashMap<String, String>()
            (knownTagOptions + availableTags + tags + localColors.keys).forEach { raw ->
                val trim = raw.trim()
                if (trim.isBlank()) return@forEach
                val key = trim.lowercase()
                if (!seen.containsKey(key)) seen[key] = trim
            }
            seen.values.sortedBy { it.lowercase() }
        }
    val assignedTags = tags.distinctByNormalizedName()
    val availableDisplayTags =
        allTags.filterNot { tag ->
            assignedTags.any { assignedTag -> assignedTag.equals(tag, ignoreCase = true) }
        }
    val pendingTagChanges = pendingAddTags.isNotEmpty() || pendingRemoveTags.isNotEmpty()
    val editDirty =
        editMode &&
            editingTag != null &&
            trimmedDraft.isNotBlank() &&
            (
                !editingTag.equals(trimmedDraft, ignoreCase = true) ||
                    !hexForTag(editingTag.orEmpty()).equals(committedDraftHex, ignoreCase = true)
            )
    val draftDirty = editingTag == null && trimmedDraft.isNotBlank()
    val sheetDirty = pendingTagChanges || editDirty || draftDirty
    val currentSheetDirty = rememberUpdatedState(sheetDirty)
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange =
                remember {
                    { sheetValue ->
                        if (sheetValue == SheetValue.Hidden && currentSheetDirty.value) {
                            showUnsavedChangesDialog = true
                            false
                        } else {
                            true
                        }
                    }
                },
        )

    val draftConflictsWithExistingTag =
        trimmedDraft.isNotBlank() &&
            allTags.any { tag ->
                tag.equals(trimmedDraft, ignoreCase = true) &&
                    !tag.equals(editingTag.orEmpty(), ignoreCase = true)
            }
    val draftIsReserved =
        trimmedDraft.isNotBlank() && RememberReservedTags.isSuggestionReserved(trimmedDraft)
    val primaryIsSave = sheetDirty
    val canSave = !draftConflictsWithExistingTag && !draftIsReserved

    LaunchedEffect(hexEditing, hexDraft.text) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(TAG_HEX_INPUT_DEBOUNCE_MILLIS)
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
        title = stringResource(R.string.options_tags),
        subtitle =
            stringResource(
                if (editMode) {
                    R.string.tag_editor_edit_mode_hint
                } else {
                    R.string.tag_editor_assign_mode_hint
                },
            ),
        onDismiss = {
            if (sheetDirty) {
                showUnsavedChangesDialog = true
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        titleAccessory = {
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(animationSpec = fadeInSpec) + expandHorizontally(animationSpec = spatialSpec),
                exit = fadeOut(animationSpec = fadeOutSpec) + shrinkHorizontally(animationSpec = spatialSpec),
            ) {
                EditModeChip()
            }
        },
        titleActions = {
            if (!editMode) {
                val editTagsCd = stringResource(R.string.tag_editor_edit_mode_cd)
                RememberIconButton(
                    onClick = {
                        editMode = true
                        clearEditSelection()
                    },
                    modifier = Modifier.semantics { contentDescription = editTagsCd },
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "edit",
                        size = 24.dp,
                        weight = FontWeight.Medium,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
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
            TagEditorSection(
                heading = stringResource(R.string.tag_editor_assigned_heading),
                emptyText = stringResource(R.string.tag_editor_no_assigned_tags),
                tags = assignedTags,
            ) { tag ->
                val pendingRemoval = pendingRemoveTags.any { pendingTag -> pendingTag.equals(tag, ignoreCase = true) }
                val selectedForEditing = editingTag?.equals(tag, ignoreCase = true) == true
                if (pendingRemoval) {
                    TagSheetIntentChip(
                        tag = tag,
                        color = parseHexColor(hexForTag(tag)) ?: TagPalette.defaultFor(tag),
                        intent = TagSheetChipIntent.REMOVE,
                        onClick = {
                            if (editMode) {
                                selectForEditing(tag)
                            } else {
                                togglePendingRemoval(tag)
                            }
                        },
                    )
                } else {
                    TagChipFilled(
                        tag = tag,
                        highlighted = editMode && selectedForEditing,
                        onClick = {
                            if (editMode) {
                                selectForEditing(tag)
                            } else {
                                togglePendingRemoval(tag)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TagEditorSection(
                heading = stringResource(R.string.tag_editor_available_heading),
                emptyText = stringResource(R.string.tag_editor_no_available_tags),
                tags = availableDisplayTags,
            ) { tag ->
                val pendingAdd = pendingAddTags.any { pendingTag -> pendingTag.equals(tag, ignoreCase = true) }
                val selectedForEditing = editingTag?.equals(tag, ignoreCase = true) == true
                if (pendingAdd) {
                    TagSheetIntentChip(
                        tag = tag,
                        color = parseHexColor(hexForTag(tag)) ?: TagPalette.defaultFor(tag),
                        intent = TagSheetChipIntent.ADD,
                        onClick = {
                            if (editMode) {
                                selectForEditing(tag)
                            } else {
                                togglePendingAdd(tag)
                            }
                        },
                    )
                } else {
                    TagChipFilled(
                        tag = tag,
                        faded = !editMode,
                        highlighted = editMode && selectedForEditing,
                        onClick = {
                            if (editMode) {
                                selectForEditing(tag)
                            } else {
                                togglePendingAdd(tag)
                            }
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(animationSpec = fadeInSpec) + expandVertically(animationSpec = spatialSpec),
                exit = fadeOut(animationSpec = fadeOutSpec) + shrinkVertically(animationSpec = spatialSpec),
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CompactOutlinedField(
                            value = draftName,
                            onValueChange = { draftName = sanitizeTagNameInput(it) },
                            placeholder = stringResource(R.string.tag_editor_create_placeholder),
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
                            hex = committedDraftHex,
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

                    if (draftConflictsWithExistingTag) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tag_editor_duplicate_existing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (draftIsReserved) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tag_editor_reserved_name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    TagColorSlider(
                        selectedHex = lastValidHex,
                        onSelect = { hex ->
                            lastValidHex = hex
                            hexDraft = hex.toTagHexFieldValue()
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primaryIsSave) {
                    RememberTextButton(onClick = { showUnsavedChangesDialog = true }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                RememberButton(
                    onClick = {
                        if (primaryIsSave) {
                            commitOnSave()
                        } else if (editMode) {
                            editMode = false
                            clearEditSelection()
                        } else {
                            onDismiss()
                        }
                    },
                    enabled = canSave,
                ) {
                    Text(
                        stringResource(
                            if (primaryIsSave) {
                                R.string.common_save
                            } else {
                                R.string.common_done
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showUnsavedChangesDialog) {
        RememberUnsavedChangesDialog(
            onConfirm = {
                showUnsavedChangesDialog = false
                onDismiss()
            },
            onDismiss = { showUnsavedChangesDialog = false },
        )
    }
}

private fun List<String>.distinctByNormalizedName(): List<String> {
    val seenNames = LinkedHashSet<String>()
    return buildList {
        this@distinctByNormalizedName.forEach { tag ->
            val trimmedTag = tag.trim()
            if (trimmedTag.isBlank()) return@forEach
            if (seenNames.add(normalizeTagName(trimmedTag))) {
                add(trimmedTag)
            }
        }
    }
}

@Composable
private fun TagEditorSection(
    heading: String,
    emptyText: String,
    tags: List<String>,
    chip: @Composable (String) -> Unit,
) {
    Text(
        text = heading,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    if (tags.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        return
    }
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag -> chip(tag) }
    }
}

private enum class TagSheetChipIntent {
    ADD,
    REMOVE,
}

@Composable
private fun TagSheetIntentChip(
    tag: String,
    color: Color,
    intent: TagSheetChipIntent,
    onClick: () -> Unit,
) {
    val containerColor: Color
    val textColor: Color
    val iconColor: Color
    val dotColor: Color
    val textDecoration: TextDecoration
    val leadingIcon: String
    val textWeight: FontWeight
    when (intent) {
        TagSheetChipIntent.ADD -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            textColor = MaterialTheme.colorScheme.onSurface
            iconColor = color
            dotColor = color
            textDecoration = TextDecoration.None
            leadingIcon = "add"
            textWeight = FontWeight.SemiBold
        }
        TagSheetChipIntent.REMOVE -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            iconColor = color.copy(alpha = 0.5f)
            dotColor = color.copy(alpha = 0.3f)
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
                .tapSoundClickable(onClick = onClick)
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

@Composable
private fun EditModeChip() {
    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.tag_editor_edit_mode_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CompactOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    counterText: String? = null,
    counterHighlighted: Boolean = false,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val pillShape = MaterialTheme.shapes.medium
    Row(
        modifier =
            modifier
                .height(FieldHeight)
                .clip(pillShape)
                .border(width = 1.dp, color = borderColor, shape = pillShape)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {}),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
        }
        if (counterText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = counterText,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (counterHighlighted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TagColorSlider(
    selectedHex: String?,
    onSelect: (String) -> Unit,
) {
    HueColorSlider(
        selectedHex = selectedHex,
        onSelect = onSelect,
        fallbackHue = DEFAULT_TAG_COLOR_HUE,
    )
}

internal fun paletteHex(color: Color): String {
    val argb =
        android.graphics.Color.argb(
            255,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    return "#%06X".format(argb and 0xFFFFFF)
}

internal fun sanitizeTagNameInput(value: String): String =
    value
        .filter { ch -> ch != '\n' }
        .take(TAG_NAME_MAX_LENGTH)

@Composable
internal fun EditableTagHexChip(
    hex: String,
    color: Color,
    editing: Boolean,
    draft: TextFieldValue,
    onStartEditing: () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
    onStopEditing: () -> Unit,
    onBoundsChange: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val textStyle =
        MaterialTheme.typography.labelLarge.copy(
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    val shape = CircleShape
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    var hadFocus by remember(editing) { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
        } else {
            onBoundsChange(null)
        }
    }

    if (!editing) {
        Row(
            modifier =
                modifier
                    .height(FieldHeight)
                    .width(TagHexChipWidth)
                    .clip(shape)
                    .background(containerColor)
                    .tapSoundClickable(onClick = onStartEditing)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = hex,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    Row(
        modifier =
            modifier
                .height(FieldHeight)
                .width(TagHexChipWidth)
                .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
                .clip(shape)
                .background(containerColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    shape = shape,
                ).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = draft.toPrefixedTagHexFieldValue(),
            onValueChange = { value ->
                val acceptedValue = value.acceptPrefixedTagHexInput()
                if (acceptedValue != null) {
                    onDraftChange(acceptedValue)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            onStopEditing()
                        }
                    },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(contentColor),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onStopEditing() }),
        )
    }
}

private const val DEFAULT_TAG_COLOR_HUE = 180f
private const val TAG_HEX_INPUT_DEBOUNCE_MILLIS = 450L
private val TagHexChipWidth = 96.dp

internal fun defaultTagColorHex(): String = colorHexFromHue(DEFAULT_TAG_COLOR_HUE)

internal fun String.toTagHexFieldValue(): TextFieldValue {
    val text = removePrefix("#").take(6).uppercase(Locale.US)
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

private fun TextFieldValue.toPrefixedTagHexFieldValue(): TextFieldValue {
    val prefixedSelection =
        TextRange(
            start = (selection.start + 1).coerceIn(1, text.length + 1),
            end = (selection.end + 1).coerceIn(1, text.length + 1),
        )
    return copy(text = "#$text", selection = prefixedSelection)
}

private fun TextFieldValue.acceptPrefixedTagHexInput(): TextFieldValue? {
    val hasPrefix = text.startsWith("#")
    val rawHexText = text.removePrefix("#")
    if (rawHexText.length > 6) return null
    val hexText = rawHexText.uppercase(Locale.US)
    if (hexText.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val prefixOffset = if (hasPrefix) 1 else 0
    return TextFieldValue(
        text = hexText,
        selection =
            TextRange(
                start = (selection.start - prefixOffset).coerceIn(0, hexText.length),
                end = (selection.end - prefixOffset).coerceIn(0, hexText.length),
            ),
    )
}
