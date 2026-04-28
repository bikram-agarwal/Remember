package dev.bikram.remember.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSection(
    prefs: ThemePrefs,
    state: ThemeState,
) {
    val scope = rememberCoroutineScope()
    var customHexOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            themePickerOrder.forEachIndexed { index, mode ->
                RememberToggleButton(
                    checked = state.themeMode == mode,
                    onCheckedChange = { checked ->
                        if (checked) scope.launch { prefs.setThemeMode(mode) }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            themePickerOrder.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    Text(
                        text = themeModeLabel(mode),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_theme_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ThemeAccentRow(
            colorSource = state.colorSource,
            activeCustomSeedHex = state.activeCustomSeed,
            savedCustomSeedHexes = state.customSeeds,
            onSelectPreset = { source -> scope.launch { prefs.setColorSource(source) } },
            onSelectCustomHex = { hex -> scope.launch { prefs.setActiveCustomSeed(hex) } },
            onCustomHexLongPress = { hex -> pendingDelete = hex },
            onAddCustomHexClick = { customHexOpen = true },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.appearance_palette_style),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ThemePaletteStyleRow(
            selected = state.paletteStyle,
            enabled = colorSourcePaletteChipsEnabled(state.colorSource),
            onSelect = { style -> scope.launch { prefs.setPaletteStyle(style) } },
        )

        if (state.colorSource == ColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.appearance_material_you_requires_s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_gradient_title),
                    subtitle = stringResource(R.string.appearance_gradient_subtitle),
                    checked = state.useGradient,
                    onCheckedChange = { scope.launch { prefs.setUseGradient(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_shading_title),
                    subtitle = stringResource(R.string.appearance_shading_subtitle),
                    checked = state.fixedCardColors,
                    onCheckedChange = { scope.launch { prefs.setFixedCardColors(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_blur_title),
                    subtitle = stringResource(R.string.appearance_blur_subtitle),
                    checked = state.blurBars,
                    leadingMaterialSymbolName = "blur_on",
                    onCheckedChange = { scope.launch { prefs.setBlurBars(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.LAST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_hero_title),
                    subtitle = stringResource(R.string.appearance_hero_subtitle),
                    checked = state.heroOnCards,
                    onCheckedChange = { scope.launch { prefs.setHeroOnCards(it) } },
                )
            }
        }
    }

    if (customHexOpen) {
        CustomHexSheet(
            onConfirm = { hex ->
                scope.launch { prefs.addCustomSeed(hex) }
                customHexOpen = false
            },
            onDismiss = { customHexOpen = false },
        )
    }

    pendingDelete?.let { hex ->
        AppBottomSheet(
            title = stringResource(R.string.appearance_remove_custom_color_title),
            subtitle = hex,
            onDismiss = { pendingDelete = null },
            actions = {
                RememberTextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
                RememberTextButton(onClick = {
                    scope.launch { prefs.removeCustomSeed(hex) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_remove)) }
            },
        ) { }
    }
}

@Composable
private fun AppearanceSettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    leadingMaterialSymbolName: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingMaterialSymbolName != null) {
            RememberMaterialRoundedSymbol(
                name = leadingMaterialSymbolName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CustomHexSheet(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftHex by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeHex(draftHex.trim())
    val previewColor =
        normalized?.let {
            runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
        }
    val previewShape = RoundedCornerShape(12.dp)
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties =
            androidx.compose.ui.window
                .DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(max = 400.dp)
                    .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.appearance_custom_accent_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.appearance_custom_accent_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.appearance_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val swatchModifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(previewShape)
                            .then(
                                if (previewColor != null) {
                                    Modifier
                                        .background(previewColor)
                                        .border(1.dp, outlineColor, previewShape)
                                } else {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, outlineColor, previewShape)
                                },
                            )
                    Box(modifier = swatchModifier, contentAlignment = Alignment.Center) {
                        if (previewColor == null && draftHex.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.appearance_invalid_hex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = draftHex,
                    onValueChange = { draftHex = it.filter { ch -> ch != '\n' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tags_hex_label)) },
                    placeholder = { Text(stringResource(R.string.appearance_hex_placeholder)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.size(8.dp))
                    RememberTextButton(
                        onClick = { onConfirm(normalized ?: draftHex.trim()) },
                        enabled = previewColor != null,
                    ) { Text(stringResource(R.string.common_add)) }
                }
            }
        }
    }
}

private val themePickerOrder =
    listOf(
        ThemeMode.SYSTEM,
        ThemeMode.LIGHT,
        ThemeMode.DARK,
        ThemeMode.BLACK,
    )

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode) {
            ThemeMode.SYSTEM -> R.string.appearance_theme_system
            ThemeMode.LIGHT -> R.string.appearance_theme_light
            ThemeMode.DARK -> R.string.appearance_theme_dark
            ThemeMode.BLACK -> R.string.appearance_theme_black
        },
    )
