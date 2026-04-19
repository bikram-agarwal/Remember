package dev.bikram.remember.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.theme.seedColorFor
import dev.bikram.remember.ui.components.RememberFilterChip
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable

private val accentPresetOrder: List<ColorSource> = listOf(
    ColorSource.DEFAULT,
    ColorSource.MATERIAL_YOU,
    ColorSource.SAPPHIRE,
    ColorSource.EMERALD,
    ColorSource.AMBER,
    ColorSource.VIOLET,
    ColorSource.CORAL,
    ColorSource.TEAL,
    ColorSource.LIME,
    ColorSource.ROSE,
    ColorSource.SLATE,
)

private val paletteStyleOrder: List<PaletteStyleOpt> = PaletteStyleOpt.entries.toList()

private fun colorSourceIsSeedBased(source: ColorSource): Boolean = when (source) {
    ColorSource.CUSTOM,
    ColorSource.SAPPHIRE,
    ColorSource.EMERALD,
    ColorSource.AMBER,
    ColorSource.VIOLET,
    ColorSource.CORAL,
    ColorSource.TEAL,
    ColorSource.LIME,
    ColorSource.ROSE,
    ColorSource.SLATE,
    -> true
    else -> false
}

fun colorSourcePaletteChipsEnabled(source: ColorSource): Boolean =
    source == ColorSource.DEFAULT || colorSourceIsSeedBased(source)

private fun customHexSwatchSelected(
    colorSource: ColorSource,
    activeCustomSeedHex: String,
    storedHex: String,
): Boolean {
    if (colorSource != ColorSource.CUSTOM) return false
    val activeNorm = normalizeHex(activeCustomSeedHex)
    val storedNorm = normalizeHex(storedHex)
    return when {
        activeNorm != null && storedNorm != null -> activeNorm == storedNorm
        else -> activeCustomSeedHex.trim() == storedHex.trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThemeAccentRow(
    colorSource: ColorSource,
    activeCustomSeedHex: String,
    savedCustomSeedHexes: List<String>,
    onSelectPreset: (ColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    onAddCustomHexClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(accentPresetOrder, key = { "preset_${it.name}" }) { source ->
            val isSelected = colorSource == source
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = borderColor,
                        shape = CircleShape,
                    )
                    .tapSoundClickable(
                        onClick = { onSelectPreset(source) },
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                ThemeAccentCircleContent(source = source)
            }
        }
        items(savedCustomSeedHexes, key = { "hex_$it" }) { storedHex ->
            val isSelected = customHexSwatchSelected(colorSource, activeCustomSeedHex, storedHex)
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            val fillColor = runCatching {
                Color(android.graphics.Color.parseColor(normalizeHex(storedHex) ?: storedHex))
            }.getOrDefault(MaterialTheme.colorScheme.surfaceVariant)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = borderColor,
                        shape = CircleShape,
                    )
                    .tapSoundCombinedClickable(
                        onClick = { onSelectCustomHex(storedHex) },
                        onLongClick = { onCustomHexLongPress(storedHex) },
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(fillColor),
                )
            }
        }
        item(key = "add_custom_seed") {
            val addCustomColorCd = stringResource(R.string.appearance_add_custom_color_cd)
            val addBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = addBorder, shape = CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .tapSoundClickable(
                        onClick = onAddCustomHexClick,
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "add",
                    size = 26.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    modifier = Modifier.semantics { contentDescription = addCustomColorCd },
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentCircleContent(source: ColorSource) {
    when (source) {
        ColorSource.DEFAULT -> Row(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF485CC7)),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF775A30)),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF8C4A63)),
            )
        }
        ColorSource.MATERIAL_YOU -> Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF6750A4),
                            Color(0xFF625B71),
                            Color(0xFF7D5260),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "palette",
                size = 22.dp,
                tint = Color.White.copy(alpha = 0.92f),
                weight = FontWeight.Medium,
            )
        }
        else -> {
            val seed = seedColorFor(source, "")
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(seed),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePaletteStyleRow(
    selected: PaletteStyleOpt,
    enabled: Boolean,
    onSelect: (PaletteStyleOpt) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(paletteStyleOrder, key = { it.name }) { style ->
            RememberFilterChip(
                selected = selected == style,
                onClick = { if (enabled) onSelect(style) },
                enabled = enabled,
                label = {
                    Text(
                        text = paletteStyleLabel(style),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
            )
        }
    }
}

@Composable
private fun paletteStyleLabel(style: PaletteStyleOpt): String = stringResource(
    when (style) {
        PaletteStyleOpt.TONAL_SPOT -> R.string.palette_style_tonal_spot
        PaletteStyleOpt.NEUTRAL -> R.string.palette_style_neutral
        PaletteStyleOpt.VIBRANT -> R.string.palette_style_vibrant
        PaletteStyleOpt.EXPRESSIVE -> R.string.palette_style_expressive
        PaletteStyleOpt.RAINBOW -> R.string.palette_style_rainbow
        PaletteStyleOpt.FRUIT_SALAD -> R.string.palette_style_fruit_salad
        PaletteStyleOpt.MONOCHROME -> R.string.palette_style_monochrome
        PaletteStyleOpt.FIDELITY -> R.string.palette_style_fidelity
        PaletteStyleOpt.CONTENT -> R.string.palette_style_content
    },
)
