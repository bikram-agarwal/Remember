package dev.bikram.remember.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.data.normalizeTagName
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.components.parseHexColor
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.tags.LocalTagColors

private val FieldHeight = 40.dp
private val SwatchGap = 8.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val firstHex = paletteHex(TagPalette.presets[0])
    var hexInput by rememberSaveable { mutableStateOf(firstHex) }
    var lastValidHex by rememberSaveable { mutableStateOf(firstHex) }
    var localColors by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editingTag by rememberSaveable { mutableStateOf<String?>(null) }
    val tagColorMap = LocalTagColors.current

    val trimmedDraft = draftName.trim()
    val chosenColor: Color = parseHexColor(lastValidHex) ?: TagPalette.presets[0]
    val chosenHex: String = lastValidHex
    val hexSwatchShape = MaterialTheme.shapes.extraSmall

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
        hexInput = firstHex
        lastValidHex = firstHex
    }

    fun selectForEditing(displayTag: String) {
        val trim = displayTag.trim()
        if (trim.isBlank()) return
        editingTag = trim
        draftName = trim
        val existingHex = hexForTag(trim)
        hexInput = existingHex
        lastValidHex = existingHex
    }

    fun commitOnSave() {
        val trimmed = trimmedDraft
        val tagBeingEdited = editingTag
        val editDirty =
            editMode &&
                tagBeingEdited != null &&
                trimmed.isNotBlank() &&
                (
                    !tagBeingEdited.equals(trimmed, ignoreCase = true) ||
                        !hexForTag(tagBeingEdited).equals(chosenHex, ignoreCase = true)
                )
        val draftTagMatch = knownTagOptions.firstOrNull { tag -> tag.equals(trimmed, ignoreCase = true) }
        val colorsToSave = mutableMapOf<String, String>()

        if (editDirty) {
            val editedTag = tagBeingEdited
            onEditExistingTag(editedTag, trimmed, chosenHex, false)
            localColors =
                localColors
                    .filterKeys { tagName -> !tagName.equals(editedTag, ignoreCase = true) }
                    .plus(trimmed to chosenHex)
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

        if (!editMode && trimmed.isNotBlank()) {
            val tagToAssign = draftTagMatch ?: trimmed
            finalTags = addTagByName(finalTags, tagToAssign)
            if (draftTagMatch == null) {
                colorsToSave[tagToAssign] = chosenHex
                localColors = localColors + (tagToAssign to chosenHex)
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
                    !hexForTag(editingTag.orEmpty()).equals(chosenHex, ignoreCase = true)
            )
    val draftDirty = !editMode && trimmedDraft.isNotBlank()
    val sheetDirty = pendingTagChanges || editDirty || draftDirty
    val currentSheetDirty = rememberUpdatedState(sheetDirty)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
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

    val draftIsDuplicate =
        editMode &&
            trimmedDraft.isNotBlank() &&
            allTags.any { tag ->
                tag.equals(trimmedDraft, ignoreCase = true) &&
                    !tag.equals(editingTag.orEmpty(), ignoreCase = true)
            }
    val primaryIsSave = sheetDirty
    val canSave = !draftIsDuplicate && (!editMode || !draftDirty || editingTag != null)

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
        titleActions = {
            val editTagsCd = stringResource(R.string.tag_editor_edit_mode_cd)
            RememberIconButton(
                onClick = {
                    editMode = !editMode
                    clearEditSelection()
                },
                modifier = Modifier.semantics { contentDescription = editTagsCd },
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (editMode) "close" else "edit",
                    size = 24.dp,
                    weight = FontWeight.Medium,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
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

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactOutlinedField(
                value = draftName,
                onValueChange = { draftName = it.filter { ch -> ch != '\n' } },
                placeholder = stringResource(R.string.tag_editor_create_placeholder),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .height(FieldHeight)
                        .clip(CircleShape)
                        .background(chosenColor)
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        trimmedDraft.ifBlank {
                            stringResource(R.string.tag_editor_preview_placeholder)
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = TagPalette.textOn(chosenColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (draftIsDuplicate) {
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    stringResource(
                        if (editMode) {
                            R.string.tag_editor_duplicate_existing
                        } else {
                            R.string.tag_editor_duplicate
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))

        ColorGrid(
            selectedHex = lastValidHex,
            onSelect = { hex ->
                hexInput = hex
                lastValidHex = hex
            },
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactOutlinedField(
                value = hexInput,
                onValueChange = { raw ->
                    val cleaned = raw.filter { ch -> ch != '\n' }
                    hexInput = cleaned
                    val normalized = normalizeHex(cleaned.trim())
                    if (normalized != null) lastValidHex = normalized
                },
                placeholder = stringResource(R.string.tags_hex_placeholder),
                modifier = Modifier.width(170.dp),
                leading = {
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(hexSwatchShape)
                                .background(chosenColor)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                    shape = hexSwatchShape,
                                ),
                    )
                },
            )
            Spacer(Modifier.weight(1f))
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

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text(stringResource(R.string.tag_editor_unsaved_title)) },
            text = { Text(stringResource(R.string.tag_editor_unsaved_body)) },
            confirmButton = {
                RememberButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.tag_editor_unsaved_discard))
                }
            },
            dismissButton = {
                RememberTextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text(stringResource(R.string.tag_editor_unsaved_keep_editing))
                }
            },
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
    val contentColor = TagPalette.textOn(color)
    val containerColor: Color
    val textColor: Color
    val iconColor: Color
    val borderStroke: BorderStroke?
    val textDecoration: TextDecoration
    val leadingIcon: String
    val textWeight: FontWeight
    when (intent) {
        TagSheetChipIntent.ADD -> {
            containerColor = color
            textColor = contentColor
            iconColor = contentColor
            borderStroke = null
            textDecoration = TextDecoration.None
            leadingIcon = "add"
            textWeight = FontWeight.SemiBold
        }
        TagSheetChipIntent.REMOVE -> {
            containerColor = Color.Transparent
            textColor = color
            iconColor = color
            borderStroke = BorderStroke(2.dp, color)
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
                .let { baseModifier ->
                    if (borderStroke != null) {
                        baseModifier.border(
                            width = borderStroke.width,
                            brush = borderStroke.brush,
                            shape = CircleShape,
                        )
                    } else {
                        baseModifier
                    }
                }.tapSoundClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = leadingIcon,
            size = 14.dp,
            tint = iconColor,
            weight = FontWeight.Medium,
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
internal fun CompactOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
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
    }
}

@Composable
internal fun ColorGrid(
    selectedHex: String?,
    onSelect: (String) -> Unit,
) {
    val grid = TagPalette.grid
    val swatchShape = MaterialTheme.shapes.small
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SwatchGap),
    ) {
        for (shadeIdx in 0 until 5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SwatchGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                grid.forEach { hueCol ->
                    val color = hueCol[shadeIdx]
                    val hex = paletteHex(color)
                    val selected = selectedHex?.equals(hex, ignoreCase = true) == true
                    Box(
                        modifier =
                            Modifier
                                .weight(1f, fill = true)
                                .aspectRatio(1f)
                                .clip(swatchShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        },
                                    shape = swatchShape,
                                ).tapSoundClickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            RememberMaterialRoundedSymbol(
                                name = "check",
                                size = 20.dp,
                                tint = TagPalette.textOn(color),
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
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
