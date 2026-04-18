package dev.bikram.remember.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.TagPalette
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.components.TagChipFilled
import dev.bikram.remember.ui.components.parseHexColor
import kotlinx.coroutines.flow.collect

private val SwatchCorner = RoundedCornerShape(10.dp)
private val PillCorner = RoundedCornerShape(12.dp)
private val HexSwatchCorner = RoundedCornerShape(6.dp)
private val FieldHeight = 40.dp
private val SwatchGap = 8.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    initial: List<String>,
    repository: NoteRepository,
    onConfirm: (List<String>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var remoteTags by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(repository) {
        repository.observeActiveTagSuggestions().collect { remoteTags = it }
    }

    var tags by rememberSaveable { mutableStateOf(initial) }
    var draftName by rememberSaveable { mutableStateOf("") }
    val firstHex = paletteHex(TagPalette.presets[0])
    var hexInput by rememberSaveable { mutableStateOf(firstHex) }
    var lastValidHex by rememberSaveable { mutableStateOf(firstHex) }
    val newColors by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }

    val trimmedDraft = draftName.trim()
    val chosenColor: Color = parseHexColor(lastValidHex) ?: TagPalette.presets[0]
    val chosenHex: String = lastValidHex

    fun toggleByDisplay(displayTag: String) {
        val trim = displayTag.trim()
        val existing = tags.firstOrNull { it.equals(trim, ignoreCase = true) }
        tags = if (existing != null) tags - existing else tags + trim
    }

    fun commitOnSave() {
        val trimmed = trimmedDraft
        val finalTags: List<String>
        val finalColors: Map<String, String>
        if (trimmed.isNotBlank() && tags.none { it.equals(trimmed, ignoreCase = true) }) {
            finalTags = tags + trimmed
            finalColors = newColors + (trimmed.lowercase() to chosenHex)
        } else {
            finalTags = tags
            finalColors = newColors
        }
        onConfirm(finalTags, finalColors)
    }

    val suggestions: List<String> = run {
        val seen = LinkedHashMap<String, String>()
        (remoteTags + tags + newColors.keys).forEach { raw ->
            val trim = raw.trim()
            if (trim.isBlank()) return@forEach
            val key = trim.lowercase()
            if (!seen.containsKey(key)) seen[key] = trim
        }
        seen.values.sortedBy { it.lowercase() }
    }

    val draftIsDuplicate = trimmedDraft.isNotBlank() &&
        tags.any { it.equals(trimmedDraft, ignoreCase = true) }

    AppBottomSheet(
        title = stringResource(R.string.tags_title),
        onDismiss = onDismiss,
        actions = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.tag_editor_suggested_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { tag ->
                    val selected = tags.any { it.equals(tag, ignoreCase = true) }
                    TagChipFilled(
                        tag = tag,
                        faded = !selected,
                        onClick = { toggleByDisplay(tag) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactOutlinedField(
                value = draftName,
                onValueChange = { draftName = it.filter { ch -> ch != '\n' } },
                placeholder = stringResource(R.string.tag_editor_field_placeholder),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .height(FieldHeight)
                    .clip(PillCorner)
                    .background(chosenColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trimmedDraft.ifBlank {
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
                text = stringResource(R.string.tag_editor_duplicate),
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
                        modifier = Modifier
                            .size(20.dp)
                            .clip(HexSwatchCorner)
                            .background(chosenColor)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                                shape = HexSwatchCorner,
                            ),
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tag_editor_cancel))
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { commitOnSave() }) {
                Text(stringResource(R.string.tag_editor_save))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CompactOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .height(FieldHeight)
            .clip(PillCorner)
            .border(width = 1.dp, color = borderColor, shape = PillCorner)
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(
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
private fun ColorGrid(
    selectedHex: String?,
    onSelect: (String) -> Unit,
) {
    val grid = TagPalette.grid
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
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .aspectRatio(1f)
                            .clip(SwatchCorner)
                            .background(color)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                },
                                shape = SwatchCorner,
                            )
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = TagPalette.textOn(color),
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun paletteHex(color: Color): String {
    val argb = android.graphics.Color.argb(
        255,
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    return "#%06X".format(argb and 0xFFFFFF)
}
