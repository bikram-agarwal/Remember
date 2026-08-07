package dev.bikram.remember.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import dev.bikram.remember.R
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.data.migrated
import dev.bikram.remember.data.normalizeCustomSeed
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.HueColorSlider
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.colorHexFromHue
import dev.bikram.remember.ui.common.hueFromHexColor
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.feedback.performRejectHaptic
import dev.bikram.remember.ui.theme.CustomFontStorage
import dev.bikram.remember.ui.theme.colorSourceSpecFor
import dev.bikram.remember.ui.theme.contrastingTextColor
import dev.bikram.remember.ui.theme.generateTripletForSeed
import dev.bikram.remember.ui.theme.parseCustomTriplet
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSection(
    prefs: ThemePrefs,
    state: ThemeState,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var customColorPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val systemDark = isSystemInDarkTheme()
    val blackThemeActive = state.blackThemeActive(systemDark)
    val blackThemeSurfaceSettingDisabledMessage =
        stringResource(R.string.appearance_black_theme_effect_disabled)

    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceThemeControls(
                    state = state,
                    onThemeModeChange = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                    onUseBlackThemeChange = { enabled -> scope.launch { prefs.setUseBlackTheme(enabled) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceAccentStudioControls(
                    state = state,
                    onSelectPreset = { source -> scope.launch { prefs.setColorSource(source) } },
                    onSelectCustomHex = { hex -> scope.launch { prefs.setActiveCustomSeed(hex) } },
                    onCustomHexLongPress = { hex -> pendingDelete = hex },
                    customColorPickerOpen = customColorPickerOpen,
                    onCustomColorPickerOpenChange = { customColorPickerOpen = it },
                    onPreviewCustomHex = { hex -> scope.launch { prefs.previewCustomSeed(hex) } },
                    onSaveCustomHex = { hex -> scope.launch { prefs.addCustomSeed(hex) } },
                    onPaletteStyleChange = { style -> scope.launch { prefs.setPaletteStyle(style) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                val enabled = !blackThemeActive
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .appClickable(enabled = !enabled) {
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(blackThemeSurfaceSettingDisabledMessage)
                                }
                            }.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.appearance_shading_intensity_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                        )
                        Text(
                            text = stringResource(R.string.appearance_shading_intensity_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (enabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                        )
                    }
                    ShadingIntensitySlider(
                        intensity = state.effectiveShadingIntensity(blackThemeActive),
                        enabled = enabled,
                        onValueChange = { intensity ->
                            scope.launch {
                                prefs.setShadingIntensity(intensity)
                            }
                        },
                    )
                }
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_gradient_title),
                    subtitle = stringResource(R.string.appearance_gradient_subtitle),
                    checked = state.effectiveUseGradient(blackThemeActive),
                    enabled = !blackThemeActive,
                    onDisabledClick = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(blackThemeSurfaceSettingDisabledMessage)
                        }
                    },
                    onCheckedChange = { scope.launch { prefs.setUseGradient(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_adaptive_note_themes_title),
                    subtitle = stringResource(R.string.appearance_adaptive_note_themes_subtitle),
                    checked = state.adaptiveNoteThemes,
                    onCheckedChange = { scope.launch { prefs.setAdaptiveNoteThemes(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_cover_title),
                    subtitle = stringResource(R.string.appearance_cover_subtitle),
                    checked = state.heroOnCards,
                    onCheckedChange = { scope.launch { prefs.setHeroOnCards(it) } },
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
                CustomFontSettingsRow(
                    prefs = prefs,
                    state = state,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    pendingDelete?.let { hex ->
        RememberConfirmDialog(
            title = stringResource(R.string.appearance_remove_custom_color_title),
            text = stringResource(R.string.appearance_remove_custom_color_message),
            confirmLabel = stringResource(R.string.common_remove),
            onConfirm = {
                scope.launch { prefs.removeCustomSeed(hex) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun CustomFontSettingsRow(
    prefs: ThemePrefs,
    state: ThemeState,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val customFontSuccessMessage = stringResource(R.string.appearance_custom_font_success)
    val customFontInvalidMessage = stringResource(R.string.appearance_custom_font_error_invalid)
    val customFontResetSuccessMessage = stringResource(R.string.appearance_custom_font_reset_success)
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val fontPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        CustomFontStorage.importFromUri(context, uri)
                    }
                when (result) {
                    is CustomFontStorage.ImportResult.Success -> {
                        prefs.setCustomFont(result.path, result.displayName)
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(customFontSuccessMessage)
                    }

                    CustomFontStorage.ImportResult.InvalidFont -> {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(customFontInvalidMessage)
                    }
                }
            }
        }

    if (showImportDialog) {
        RememberConfirmDialog(
            title = stringResource(R.string.appearance_custom_font_choose),
            text = stringResource(R.string.appearance_custom_font_choose_explanation),
            confirmLabel = stringResource(R.string.appearance_custom_font_choose),
            onConfirm = {
                showImportDialog = false
                fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf"))
            },
            onDismiss = { showImportDialog = false },
        )
    }

    val hasCustomFont = state.customFontPath.isNotBlank()
    val subtitleText =
        if (hasCustomFont) {
            state.customFontName.ifBlank { state.customFontPath.substringAfterLast('/') }
        } else {
            stringResource(R.string.appearance_custom_font_default)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable { showImportDialog = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "font_download",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.appearance_custom_font_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasCustomFont) {
            Spacer(Modifier.width(8.dp))
            RememberFilledTonalIconButton(
                onClick = {
                    scope.launch {
                        prefs.clearCustomFont()
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(customFontResetSuccessMessage)
                    }
                },
            ) {
                RememberMaterialRoundedSymbol(
                    name = "close",
                )
            }
        }
    }
}

@Composable
private fun AppearanceThemeControls(
    state: ThemeState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onUseBlackThemeChange: (Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        val showBlackThemeToggle = state.themeMode != ThemeMode.LIGHT
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 10.dp else 16.dp,
                        top = 14.dp,
                        end = if (compact) 10.dp else 16.dp,
                        bottom = if (showBlackThemeToggle) 4.dp else 14.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(if (showBlackThemeToggle) 4.dp else 0.dp),
        ) {
            ThemeModeSegmentedRow(
                selected = state.themeMode,
                onSelect = onThemeModeChange,
            )
            if (showBlackThemeToggle) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .appClickable { onUseBlackThemeChange(!state.useBlackTheme) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.appearance_use_black_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    SettingsToggleSwitch(
                        checked = state.useBlackTheme,
                        enabled = true,
                        onCheckedChange = onUseBlackThemeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceAccentStudioControls(
    state: ThemeState,
    onSelectPreset: (ColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    customColorPickerOpen: Boolean,
    onCustomColorPickerOpenChange: (Boolean) -> Unit,
    onPreviewCustomHex: (String) -> Unit,
    onSaveCustomHex: (String) -> Unit,
    onPaletteStyleChange: (PaletteStyleOpt) -> Unit,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())

    var editingTarget by remember { mutableStateOf(ColorTarget.PRIMARY) }
    LaunchedEffect(customColorPickerOpen) {
        if (!customColorPickerOpen) {
            editingTarget = ColorTarget.PRIMARY
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.appearance_palette),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f),
                    )
                }
                ThemePaletteStyleRow(
                    selected = state.paletteStyle,
                    enabled = colorSourcePaletteChipsEnabled(state.colorSource),
                    onSelect = onPaletteStyleChange,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ThemeAccentRow(
                    colorSource = state.colorSource,
                    activeCustomSeedHex = state.activeCustomSeed,
                    savedCustomSeedHexes = state.customSeeds,
                    customColorPickerOpen = customColorPickerOpen,
                    onSelectPreset = { source ->
                        if (customColorPickerOpen) {
                            editingTarget = ColorTarget.PRIMARY
                        }
                        onSelectPreset(source)
                    },
                    onSelectCustomHex = { hex ->
                        if (customColorPickerOpen) {
                            editingTarget = ColorTarget.PRIMARY
                        }
                        onSelectCustomHex(hex)
                    },
                    onCustomHexLongPress = onCustomHexLongPress,
                    onAddCustomHexClick = { onCustomColorPickerOpenChange(!customColorPickerOpen) },
                )
                AnimatedVisibility(
                    visible = customColorPickerOpen,
                    enter = expandVertically(animationSpec = spatialSpec, expandFrom = Alignment.Top),
                    exit = shrinkVertically(animationSpec = spatialSpec, shrinkTowards = Alignment.Top),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
                    ) {
                        Spacer(Modifier.height(0.dp))
                        CustomColorSlider(
                            initialSeedHex = customSliderInitialSeedHex(state, MaterialTheme.colorScheme.primary),
                            editingTarget = editingTarget,
                            onPreviewColor = onPreviewCustomHex,
                            onSaveColor = onSaveCustomHex,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                )
                ThemePreviewPanel(
                    colorSource = state.colorSource,
                    isInteractive = state.colorSource == ColorSource.CUSTOM,
                    selectedTarget = editingTarget,
                    onTargetSelect = { target ->
                        editingTarget = target
                        onCustomColorPickerOpenChange(true)
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSegmentedRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        val ultraCompact = maxWidth < 300.dp
        val colors =
            ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        val labels = themePickerOrder.map { mode -> themeModeLabel(mode) }
        val shapes =
            themePickerOrder.mapIndexed { index, _ ->
                when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    themePickerOrder.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            }
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            overflowIndicator = { menuState ->
                ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
            },
        ) {
            themePickerOrder.forEachIndexed { index, mode ->
                val label = labels[index]
                val itemModifier = Modifier.weight(1f).semantics { role = Role.RadioButton }
                customItem(
                    buttonGroupContent = {
                        RememberToggleButton(
                            checked = selected.migrated() == mode,
                            onCheckedChange = { checked ->
                                if (checked) onSelect(mode)
                            },
                            modifier = itemModifier,
                            shapes = shapes[index],
                            colors = colors,
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (compact) 4.dp else 8.dp,
                                    vertical = 8.dp,
                                ),
                        ) {
                            Text(
                                text = label,
                                style =
                                    if (ultraCompact) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        MaterialTheme.typography.labelMedium
                                    },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    menuContent = { menuState ->
                        RememberDropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(mode)
                                menuState.dismiss()
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    leadingMaterialSymbolName: String? = null,
    onDisabledClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
    val subtitleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appClickable {
                    if (enabled) {
                        onCheckedChange(!checked)
                    } else {
                        onDisabledClick?.invoke()
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingMaterialSymbolName != null) {
            RememberMaterialRoundedSymbol(
                name = leadingMaterialSymbolName,
                tint = subtitleColor,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        SettingsToggleSwitch(
            checked = checked,
            enabled = enabled,
            onDisabledInteraction = onDisabledClick,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun CustomColorSlider(
    initialSeedHex: String,
    editingTarget: ColorTarget,
    onPreviewColor: (String) -> Unit,
    onSaveColor: (String) -> Unit,
) {
    var currentSeedHex by remember(initialSeedHex) { mutableStateOf(initialSeedHex) }
    val targetHex =
        remember(currentSeedHex, editingTarget) {
            extractTargetHex(currentSeedHex, editingTarget)
        }

    val normalizedTargetHex = normalizeHex(targetHex) ?: colorHexFromHue(DEFAULT_CUSTOM_HUE)
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(normalizedTargetHex.toHexFieldValue())
    }
    val hexFocusRequester = remember { FocusRequester() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hexEditorBoundsInRoot by remember { mutableStateOf<Rect?>(null) }

    fun commitHexEditing(): String {
        val draftHex =
            if (hexDraft.text.length == 6) {
                normalizeHex("#${hexDraft.text}")
            } else {
                null
            }
        val committedTargetHex = draftHex ?: normalizedTargetHex
        hexDraft = committedTargetHex.toHexFieldValue()

        val nextSeedHex = updateTargetHex(currentSeedHex, committedTargetHex, editingTarget)
        currentSeedHex = nextSeedHex

        if (hexEditing && draftHex != null) {
            onPreviewColor(nextSeedHex)
        }
        hexEditing = false
        return nextSeedHex
    }
    LaunchedEffect(normalizedTargetHex) {
        hexDraft = normalizedTargetHex.toHexFieldValue()
    }
    LaunchedEffect(hexEditing) {
        if (hexEditing) hexFocusRequester.requestFocus()
    }
    LaunchedEffect(hexDraft, hexEditing) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(HEX_INPUT_DEBOUNCE_MILLIS)
        val normalized = "#${hexDraft.text.uppercase(Locale.US)}"
        if (hueFromHexColor(normalized) != null) {
            val nextSeedHex = updateTargetHex(currentSeedHex, normalized, editingTarget)
            currentSeedHex = nextSeedHex
            onPreviewColor(nextSeedHex)
        }
    }
    val panelShape = MaterialTheme.shapes.extraLargeIncreased
    val sliderPanelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val saveColorLabel = stringResource(R.string.common_save)

    Surface(
        color = sliderPanelColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = panelShape,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { panelCoordinates = it }
                    .pointerInput(hexEditing, hexDraft, currentSeedHex, editingTarget) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val wasEditingAtDown = hexEditing
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                            if (!wasEditingAtDown || !hexEditing) return@awaitEachGesture
                            val tapInRoot = panelCoordinates?.localToRoot(up.position) ?: return@awaitEachGesture
                            val editorBounds = hexEditorBoundsInRoot
                            if (editorBounds == null || !editorBounds.contains(tapInRoot)) {
                                commitHexEditing()
                            }
                        }
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val rawString =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> stringResource(R.string.appearance_select_color)
                        ColorTarget.SECONDARY -> stringResource(R.string.appearance_select_color_secondary)
                        ColorTarget.TERTIARY -> stringResource(R.string.appearance_select_color_tertiary)
                    }
                val targetWord =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> stringResource(R.string.appearance_preview_label_primary).lowercase(Locale.US)
                        ColorTarget.SECONDARY -> stringResource(R.string.appearance_preview_label_secondary).lowercase(Locale.US)
                        ColorTarget.TERTIARY -> stringResource(R.string.appearance_preview_label_tertiary).lowercase(Locale.US)
                    }
                val parsedTriplet =
                    remember(currentSeedHex) {
                        parseCustomTriplet(currentSeedHex)
                    }
                val primaryColor = parsedTriplet?.primary ?: MaterialTheme.colorScheme.primary
                val secondaryColor = parsedTriplet?.secondary ?: MaterialTheme.colorScheme.secondary
                val tertiaryColor = parsedTriplet?.tertiary ?: MaterialTheme.colorScheme.tertiary
                val targetColor =
                    when (editingTarget) {
                        ColorTarget.PRIMARY -> primaryColor
                        ColorTarget.SECONDARY -> secondaryColor
                        ColorTarget.TERTIARY -> tertiaryColor
                    }
                val annotatedTitle =
                    remember(rawString, targetWord, targetColor) {
                        val index = rawString.indexOf(targetWord, ignoreCase = true)
                        buildAnnotatedString {
                            if (index != -1) {
                                append(rawString.substring(0, index))
                                withStyle(SpanStyle(color = targetColor)) {
                                    append(rawString.substring(index, index + targetWord.length))
                                }
                                append(rawString.substring(index + targetWord.length))
                            } else {
                                append(rawString)
                            }
                        }
                    }
                Text(
                    text = annotatedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                EditableHexValue(
                    hex = normalizedTargetHex,
                    editing = hexEditing,
                    draft = hexDraft,
                    focusRequester = hexFocusRequester,
                    onStartEditing = {
                        hexDraft = normalizedTargetHex.toHexFieldValue()
                        hexEditing = true
                    },
                    onDraftChange = { hexDraft = it },
                    onStopEditing = { commitHexEditing() },
                    onBoundsChange = { hexEditorBoundsInRoot = it },
                )
                RememberFilledTonalIconButton(
                    onClick = { onSaveColor(commitHexEditing()) },
                    modifier = Modifier.size(40.dp),
                    tooltipLabel = saveColorLabel,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        weight = FontWeight.Medium,
                    )
                }
            }
            HueColorSlider(
                selectedHex = normalizedTargetHex,
                onSelect = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    hexDraft = newHex.toHexFieldValue()
                },
                modifier = Modifier.fillMaxWidth(),
                fallbackHue = DEFAULT_CUSTOM_HUE,
                sliderPanelColor = sliderPanelColor,
                onValueChangeFinished = { newHex ->
                    val nextSeedHex = updateTargetHex(currentSeedHex, newHex, editingTarget)
                    currentSeedHex = nextSeedHex
                    onPreviewColor(nextSeedHex)
                },
            )
        }
    }
}

@Composable
private fun EditableHexValue(
    hex: String,
    editing: Boolean,
    draft: TextFieldValue,
    focusRequester: FocusRequester,
    onStartEditing: () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
    onStopEditing: () -> Unit,
    onBoundsChange: (Rect?) -> Unit,
) {
    val shape = CircleShape
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val textStyle =
        MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editing) onBoundsChange(null)
    }
    if (!editing) {
        Box(
            modifier =
                Modifier
                    .width(HexValueWidth)
                    .height(HexValueHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ).appClickable(onClick = onStartEditing)
                    .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hex,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        return
    }

    Box(
        modifier =
            Modifier
                .width(HexValueWidth)
                .height(HexValueHeight)
                .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = draft.toPrefixedHexFieldValue(),
            onValueChange = { value ->
                val acceptedValue = value.acceptPrefixedHexInput()
                if (acceptedValue != null) {
                    onDraftChange(acceptedValue)
                } else {
                    if (hapticEnabled) view.performRejectHaptic()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
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
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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

private const val DEFAULT_CUSTOM_HUE = 270f
private const val HEX_INPUT_DEBOUNCE_MILLIS = 450L
private val HexValueWidth = 84.dp
private val HexValueHeight = 40.dp

private fun String.dropHexPrefix(): String = removePrefix("#").take(6).uppercase(Locale.US)

private fun String.toHexFieldValue(): TextFieldValue {
    val text = dropHexPrefix()
    return TextFieldValue(text = text, selection = TextRange(text.length))
}

private fun TextFieldValue.toPrefixedHexFieldValue(): TextFieldValue {
    val prefixedSelection =
        TextRange(
            start = (selection.start + 1).coerceIn(1, text.length + 1),
            end = (selection.end + 1).coerceIn(1, text.length + 1),
        )
    return copy(text = "#$text", selection = prefixedSelection)
}

private fun TextFieldValue.acceptPrefixedHexInput(): TextFieldValue? {
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

private fun TextFieldValue.acceptHexInput(): TextFieldValue? {
    if (text.length > 6) return null
    if (text.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val uppercaseText = text.uppercase(Locale.US)
    return copy(
        text = uppercaseText,
        selection =
            TextRange(
                start = selection.start.coerceIn(0, uppercaseText.length),
                end = selection.end.coerceIn(0, uppercaseText.length),
            ),
    )
}

private fun customSliderInitialSeedHex(
    state: ThemeState,
    currentPrimary: Color,
): String {
    val activeCustomSeed = normalizeCustomSeed(state.activeCustomSeed)
    if (state.colorSource == ColorSource.CUSTOM && activeCustomSeed != null) {
        return activeCustomSeed
    }
    if (state.colorSource == ColorSource.MATERIAL_YOU) {
        return hexFromColor(currentPrimary)
    }
    return hexFromColor(colorSourceSpecFor(state.colorSource).representativeColor)
}

private fun hexFromColor(color: Color): String {
    val colorInt =
        AndroidColor.argb(
            255,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
}

private val themePickerOrder =
    listOf(
        ThemeMode.SYSTEM,
        ThemeMode.LIGHT,
        ThemeMode.DARK,
    )

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode.name) {
            ThemeMode.SYSTEM.name -> R.string.appearance_theme_system
            ThemeMode.LIGHT.name -> R.string.appearance_theme_light
            else -> R.string.appearance_theme_dark
        },
    )

/**
 * Live preview card showing how the active palette + style produce surface and accent
 * tones. Two strips:
 *
 *  1. Surface ladder: 6 swatches mapped to surfaceContainerLowest, surface,
 *     surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest.
 *     Helps users see whether their palette gives clean tonal separation between
 *     "recessed" and "raised" surfaces - a flat ladder is a sign the seed + style combo
 *     collapses too monochromatic.
 *
 *  2. Accent containers: primaryContainer, secondaryContainer, tertiaryContainer with
 *     "Aa" rendered in the corresponding onContainer color so users can spot-check both
 *     hue separation and text contrast in one glance.
 */
@Composable
private fun ThemePreviewPanel(
    colorSource: ColorSource,
    isInteractive: Boolean,
    selectedTarget: ColorTarget,
    onTargetSelect: (ColorTarget) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val title =
        if (colorSource == ColorSource.CUSTOM) {
            stringResource(R.string.appearance_preview_customize)
        } else {
            stringResource(
                R.string.appearance_preview_title_named,
                colorSourceDisplayName(colorSource),
            )
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier.weight(1f),
            )
        }
        AccentContainersStrip(
            scheme = scheme,
            isInteractive = isInteractive,
            selectedTarget = selectedTarget,
            onTargetSelect = onTargetSelect,
        )
        SurfaceLadderStrip(scheme = scheme)
    }
}

@Composable
private fun SurfaceLadderStrip(scheme: ColorScheme) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        val swatches =
            listOf(
                scheme.surfaceContainerLowest to
                    if (compact) {
                        "Lo-"
                    } else {
                        stringResource(R.string.appearance_preview_label_lowest)
                    },
                scheme.surface to stringResource(R.string.appearance_preview_label_surface),
                scheme.surfaceContainerLow to
                    if (compact) {
                        "Low"
                    } else {
                        stringResource(R.string.appearance_preview_label_low)
                    },
                scheme.surfaceContainer to
                    if (compact) {
                        "Base"
                    } else {
                        stringResource(R.string.appearance_preview_label_base)
                    },
                scheme.surfaceContainerHigh to
                    if (compact) {
                        "High"
                    } else {
                        stringResource(R.string.appearance_preview_label_high)
                    },
                scheme.surfaceContainerHighest to
                    if (compact) {
                        "Hi+"
                    } else {
                        stringResource(R.string.appearance_preview_label_highest)
                    },
            )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (compact) 32.dp else 36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
            ) {
                swatches.forEach { (color, label) ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style =
                                if (compact) {
                                    MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                } else {
                                    MaterialTheme.typography.labelSmall
                                },
                            color = contrastingTextColor(color),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentContainersStrip(
    scheme: ColorScheme,
    isInteractive: Boolean = false,
    selectedTarget: ColorTarget = ColorTarget.PRIMARY,
    onTargetSelect: (ColorTarget) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            label = stringResource(R.string.appearance_preview_label_primary),
            isSelected = isInteractive && selectedTarget == ColorTarget.PRIMARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.PRIMARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            label = stringResource(R.string.appearance_preview_label_secondary),
            isSelected = isInteractive && selectedTarget == ColorTarget.SECONDARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.SECONDARY) },
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
            label = stringResource(R.string.appearance_preview_label_tertiary),
            isSelected = isInteractive && selectedTarget == ColorTarget.TERTIARY,
            isInteractive = isInteractive,
            onClick = { onTargetSelect(ColorTarget.TERTIARY) },
        )
    }
}

@Composable
private fun AccentChip(
    modifier: Modifier,
    container: Color,
    onContainer: Color,
    label: String,
    isSelected: Boolean = false,
    isInteractive: Boolean = false,
    onClick: () -> Unit = {},
) {
    val outlineColor = MaterialTheme.colorScheme.primary
    val chipModifier =
        if (isInteractive) {
            modifier.appClickable(onClick = onClick)
        } else {
            modifier
        }
    Surface(
        modifier =
            chipModifier
                .then(
                    if (isSelected) {
                        Modifier.border(1.dp, outlineColor, MaterialTheme.shapes.medium)
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        color = container,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.appearance_preview_sample_text),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

enum class ColorTarget { PRIMARY, SECONDARY, TERTIARY }

fun extractTargetHex(
    seedHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|")
    return when (target) {
        ColorTarget.PRIMARY -> parts.getOrNull(0) ?: seedHex
        ColorTarget.SECONDARY -> parts.getOrNull(1) ?: seedHex
        ColorTarget.TERTIARY -> parts.getOrNull(2) ?: seedHex
    }
}

fun updateTargetHex(
    seedHex: String,
    newHex: String,
    target: ColorTarget,
): String {
    val parts = seedHex.split("|").toMutableList()
    while (parts.size < 3) {
        val primaryHex = parts.getOrNull(0) ?: "#16A34A"
        val primaryColor = Color(primaryHex.toColorInt())
        val generatedColors = generateTripletForSeed(primaryColor)
        val defaultColors =
            listOf(
                primaryHex,
                hexFromColor(generatedColors.secondary),
                hexFromColor(generatedColors.tertiary),
            )
        parts.add(defaultColors[parts.size])
    }
    parts[target.ordinal] = newHex

    if (target == ColorTarget.PRIMARY) {
        val newPrimaryColor = Color(newHex.toColorInt())
        val generated = generateTripletForSeed(newPrimaryColor)
        parts[1] = hexFromColor(generated.secondary)
        parts[2] = hexFromColor(generated.tertiary)
    }

    return parts.joinToString("|")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShadingIntensitySlider(
    intensity: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember { mutableFloatStateOf(intensity.roundToShadingStep()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val interacting = isDragged || isPressed

    LaunchedEffect(intensity, interacting) {
        if (!interacting) {
            sliderValue = intensity.roundToShadingStep()
        }
    }

    Slider(
        value = sliderValue,
        onValueChange = { rawValue ->
            if (enabled) {
                val steppedValue = rawValue.roundToShadingStep()
                if (steppedValue != sliderValue) {
                    sliderValue = steppedValue
                    onValueChange(steppedValue)
                }
            }
        },
        valueRange = 0f..2f,
        steps = 19,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            Label(
                label = {
                    PlainTooltip(
                        modifier =
                            Modifier
                                .sizeIn(
                                    minWidth = ShadingSliderLabelMinWidth,
                                    minHeight = ShadingSliderLabelMinHeight,
                                ).wrapContentWidth(),
                    ) {
                        Text(getShadingLabel(sliderValue))
                    }
                },
                interactionSource = interactionSource,
                isPersistent = interacting,
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    thumbSize = ShadingSliderThumbSize,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun getShadingLabel(value: Float): String {
    val percentage = (value * 100f).roundToInt()
    return "$percentage%"
}

private fun Float.roundToShadingStep(): Float =
    (this * 10f)
        .roundToInt()
        .coerceIn(0, 20) / 10f

private val ShadingSliderLabelMinWidth = 45.dp
private val ShadingSliderLabelMinHeight = 25.dp
private val ShadingSliderThumbSize = DpSize(width = 4.dp, height = 32.dp)
