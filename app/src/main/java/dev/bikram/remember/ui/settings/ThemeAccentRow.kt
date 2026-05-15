package dev.bikram.remember.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.bikram.remember.R
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberFilterChip
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.theme.ColorSourceSpec
import dev.bikram.remember.ui.theme.ColorSourceSwatchType
import dev.bikram.remember.ui.theme.colorSourceSpecFor
import dev.bikram.remember.ui.theme.colorSourceSpecsInPickerOrder

// Material You leads the row when available so the wallpaper-driven option is what users
// see first. DEFAULT (Forest - the green/teal/lime triplet baked into the app) sits next as
// the factory default, followed by named curated triplets whose primary seeds are spread
// across distinct identities: flame orange, olive, gold, ocean blue, violet, slate dusk,
// and fuchsia berry. Custom hexes and the +Add affordance follow inside ThemeAccentRow
// itself.
private val accentPresetSpecs: List<ColorSourceSpec> = colorSourceSpecsInPickerOrder

private val paletteStyleOrder: List<PaletteStyleOpt> = PaletteStyleOpt.entries.toList()

// Palette-style availability lives with the color source spec so the picker and resolver
// agree on which sources are style-driven.
fun colorSourcePaletteChipsEnabled(source: ColorSource): Boolean = colorSourceSpecFor(source).supportsPaletteStyle

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
    customColorPickerOpen: Boolean,
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
        items(accentPresetSpecs, key = { "preset_${it.source.name}" }) { spec ->
            val isSelected = colorSource == spec.source
            val borderColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape,
                        ).tapSoundClickable(
                            onClick = { onSelectPreset(spec.source) },
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                ThemeAccentCircleContent(spec = spec)
            }
        }
        items(savedCustomSeedHexes, key = { "hex_$it" }) { storedHex ->
            val isSelected = customHexSwatchSelected(colorSource, activeCustomSeedHex, storedHex)
            val borderColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                }
            val fillColor =
                runCatching {
                    Color((normalizeHex(storedHex) ?: storedHex).toColorInt())
                }.getOrDefault(MaterialTheme.colorScheme.surfaceVariant)
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape,
                        ).tapSoundCombinedClickable(
                            onClick = { onSelectCustomHex(storedHex) },
                            onLongClick = { onCustomHexLongPress(storedHex) },
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.RadioButton },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
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
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(width = 1.dp, color = addBorder, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .tapSoundClickable(
                            onClick = onAddCustomHexClick,
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                        ).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = if (customColorPickerOpen) "chevron_right" else "add",
                    size = if (customColorPickerOpen) 22.dp else 26.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .graphicsLayer { rotationZ = if (customColorPickerOpen) 90f else 0f }
                            .semantics { contentDescription = addCustomColorCd },
                )
            }
        }
    }
}

@Composable
private fun ThemeAccentCircleContent(spec: ColorSourceSpec) {
    when (spec.swatchType) {
        ColorSourceSwatchType.MATERIAL_YOU ->
            Box(
                modifier =
                    Modifier
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
        ColorSourceSwatchType.TRIPLET -> {
            val triplet = requireNotNull(spec.triplet)
            CuratedTripletSwatch(
                primary = triplet.primary,
                secondary = triplet.secondary,
                tertiary = triplet.tertiary,
            )
        }
        ColorSourceSwatchType.SOLID ->
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(spec.representativeColor),
            )
    }
}

/**
 * Renders a 44dp circle with the primary filling the top half and the secondary/tertiary
 * splitting the bottom half, mirroring the stock Android Material You wallpaper-color
 * picker. Primary dominates so the preset's identity hue is unambiguous, while the bottom
 * split still surfaces accent variety at a glance.
 */
@Composable
private fun CuratedTripletSwatch(
    primary: Color,
    secondary: Color,
    tertiary: Color,
) {
    Column(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(primary),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(secondary))
            Box(Modifier.weight(1f).fillMaxHeight().background(tertiary))
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
                colors =
                    FilterChipDefaults.filterChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
            )
        }
    }
}

@Composable
private fun paletteStyleLabel(style: PaletteStyleOpt): String =
    stringResource(
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

/**
 * User-facing name for a color source - shown in the preview card title and accessibility
 * labels. Deprecated sources never reach the UI (the data layer migrates them on read), so
 * the else branch is just a defensive fallback for any future stragglers.
 */
@Composable
fun colorSourceDisplayName(source: ColorSource): String =
    stringResource(
        when (source) {
            ColorSource.MATERIAL_YOU -> R.string.color_source_material_you
            ColorSource.DEFAULT -> R.string.color_source_forest
            ColorSource.CURATED_EMBER -> R.string.color_source_ember
            ColorSource.CURATED_GROVE -> R.string.color_source_grove
            ColorSource.CURATED_HONEY -> R.string.color_source_honey
            ColorSource.CURATED_OCEAN -> R.string.color_source_ocean
            ColorSource.CURATED_IRIS -> R.string.color_source_iris
            ColorSource.CURATED_DUSK -> R.string.color_source_dusk
            ColorSource.CURATED_BERRY -> R.string.color_source_berry
            ColorSource.CUSTOM -> R.string.color_source_custom
            else -> R.string.color_source_custom
        },
    )
