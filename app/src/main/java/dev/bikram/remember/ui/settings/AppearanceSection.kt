package dev.bikram.remember.ui.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.ColorSource
import dev.bikram.remember.data.PaletteStyleOpt
import dev.bikram.remember.data.ThemeMode
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.data.normalizeHex
import dev.bikram.remember.ui.common.HueColorSlider
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.colorHexFromHue
import dev.bikram.remember.ui.common.hueFromHexColor
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.colorSourceSpecFor
import dev.bikram.remember.ui.theme.contrastingTextColor
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
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
    val blackThemeEffectsDisabled = state.themeMode == ThemeMode.BLACK
    val blackThemeEffectsDisabledMessage = stringResource(R.string.appearance_black_theme_effect_disabled)

    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceStudioControls(
                    state = state,
                    onThemeModeChange = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                    onSelectPreset = { source -> scope.launch { prefs.setColorSource(source) } },
                    onSelectCustomHex = { hex -> scope.launch { prefs.setActiveCustomSeed(hex) } },
                    onCustomHexLongPress = { hex -> pendingDelete = hex },
                    customColorPickerOpen = customColorPickerOpen,
                    onAddCustomHexClick = { customColorPickerOpen = !customColorPickerOpen },
                    onPreviewCustomHex = { hex -> scope.launch { prefs.previewCustomSeed(hex) } },
                    onSaveCustomHex = { hex -> scope.launch { prefs.addCustomSeed(hex) } },
                    onPaletteStyleChange = { style -> scope.launch { prefs.setPaletteStyle(style) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_gradient_title),
                    subtitle = stringResource(R.string.appearance_gradient_subtitle),
                    checked = state.useGradient && !blackThemeEffectsDisabled,
                    enabled = !blackThemeEffectsDisabled,
                    onDisabledClick = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(blackThemeEffectsDisabledMessage)
                        }
                    },
                    onCheckedChange = { scope.launch { prefs.setUseGradient(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_shading_title),
                    subtitle = stringResource(R.string.appearance_shading_subtitle),
                    checked = state.useEnhancedShading || blackThemeEffectsDisabled,
                    enabled = !blackThemeEffectsDisabled,
                    onDisabledClick = {
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(blackThemeEffectsDisabledMessage)
                        }
                    },
                    onCheckedChange = { scope.launch { prefs.setUseEnhancedShading(it) } },
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

    pendingDelete?.let { hex ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.appearance_remove_custom_color_title)) },
            text = { Text(stringResource(R.string.appearance_remove_custom_color_message)) },
            confirmButton = {
                RememberTextButton(onClick = {
                    scope.launch { prefs.removeCustomSeed(hex) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = {
                RememberTextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun AppearanceStudioControls(
    state: ThemeState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSelectPreset: (ColorSource) -> Unit,
    onSelectCustomHex: (String) -> Unit,
    onCustomHexLongPress: (String) -> Unit,
    customColorPickerOpen: Boolean,
    onAddCustomHexClick: () -> Unit,
    onPreviewCustomHex: (String) -> Unit,
    onSaveCustomHex: (String) -> Unit,
    onPaletteStyleChange: (PaletteStyleOpt) -> Unit,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ThemeModeSegmentedRow(
            selected = state.themeMode,
            onSelect = onThemeModeChange,
        )
        ThemeAccentRow(
            colorSource = state.colorSource,
            activeCustomSeedHex = state.activeCustomSeed,
            savedCustomSeedHexes = state.customSeeds,
            customColorPickerOpen = customColorPickerOpen,
            onSelectPreset = onSelectPreset,
            onSelectCustomHex = onSelectCustomHex,
            onCustomHexLongPress = onCustomHexLongPress,
            onAddCustomHexClick = onAddCustomHexClick,
        )
        AnimatedVisibility(
            visible = customColorPickerOpen,
            enter = fadeIn(animationSpec = fadeInSpec) + expandVertically(animationSpec = spatialSpec),
            exit = fadeOut(animationSpec = fadeOutSpec) + shrinkVertically(animationSpec = spatialSpec),
        ) {
            CustomColorSlider(
                initialSeedHex = customSliderInitialSeedHex(state, MaterialTheme.colorScheme.primary),
                onPreviewColor = onPreviewCustomHex,
                onSaveColor = onSaveCustomHex,
            )
        }
        AppearanceStudioSection(
            title = stringResource(R.string.appearance_palette_style),
        ) {
            ThemePaletteStyleRow(
                selected = state.paletteStyle,
                enabled = colorSourcePaletteChipsEnabled(state.colorSource),
                onSelect = onPaletteStyleChange,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        )
        ThemePreviewPanel(colorSource = state.colorSource)
    }
}

@Composable
private fun ThemeModeSegmentedRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
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
                        checked = selected == mode,
                        onCheckedChange = { checked ->
                            if (checked) onSelect(mode)
                        },
                        modifier = itemModifier,
                        shapes = shapes[index],
                        colors = colors,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
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

@Composable
private fun AppearanceStudioSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
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
                .tapSoundClickable {
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
    onPreviewColor: (String) -> Unit,
    onSaveColor: (String) -> Unit,
) {
    val normalizedInitialSeedHex = normalizeHex(initialSeedHex) ?: colorHexFromHue(DEFAULT_CUSTOM_HUE)
    var selectedSeedHex by rememberSaveable(normalizedInitialSeedHex) { mutableStateOf(normalizedInitialSeedHex) }
    var hexEditing by rememberSaveable { mutableStateOf(false) }
    var hexDraft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(normalizedInitialSeedHex.toHexFieldValue())
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
        val committedHex = draftHex ?: selectedSeedHex
        selectedSeedHex = committedHex
        hexDraft = committedHex.toHexFieldValue()
        if (hexEditing && draftHex != null) onPreviewColor(committedHex)
        hexEditing = false
        return committedHex
    }
    LaunchedEffect(normalizedInitialSeedHex) {
        selectedSeedHex = normalizedInitialSeedHex
        hexDraft = normalizedInitialSeedHex.toHexFieldValue()
    }
    LaunchedEffect(hexEditing) {
        if (hexEditing) hexFocusRequester.requestFocus()
    }
    LaunchedEffect(hexDraft, hexEditing) {
        if (!hexEditing || hexDraft.text.length != 6) return@LaunchedEffect
        delay(HEX_INPUT_DEBOUNCE_MILLIS)
        val normalized = "#${hexDraft.text.uppercase(Locale.US)}"
        if (hueFromHexColor(normalized) != null) {
            selectedSeedHex = normalized
            onPreviewColor(normalized)
        }
    }
    val selectedHex = selectedSeedHex
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
                    .pointerInput(hexEditing, hexDraft, selectedSeedHex) {
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
                Text(
                    text = stringResource(R.string.appearance_select_color),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                EditableHexValue(
                    hex = selectedHex,
                    editing = hexEditing,
                    draft = hexDraft,
                    focusRequester = hexFocusRequester,
                    onStartEditing = {
                        hexDraft = selectedSeedHex.toHexFieldValue()
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
                selectedHex = selectedSeedHex,
                onSelect = {
                    selectedSeedHex = it
                    hexDraft = it.toHexFieldValue()
                },
                modifier = Modifier.fillMaxWidth(),
                fallbackHue = DEFAULT_CUSTOM_HUE,
                sliderPanelColor = sliderPanelColor,
                onValueChangeFinished = onPreviewColor,
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
    val haptic = LocalHapticFeedback.current
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
                    ).tapSoundClickable(onClick = onStartEditing)
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
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
    val activeCustomSeed = normalizeHex(state.activeCustomSeed)
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
private fun ThemePreviewPanel(colorSource: ColorSource) {
    val scheme = MaterialTheme.colorScheme
    val title =
        stringResource(
            R.string.appearance_preview_title_named,
            colorSourceDisplayName(colorSource),
        )

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
        PreviewSubsection(title = stringResource(R.string.appearance_preview_surface_ladder)) {
            SurfaceLadderStrip(scheme = scheme)
        }
        PreviewSubsection(title = stringResource(R.string.appearance_preview_accent_containers)) {
            AccentContainersStrip(scheme = scheme)
        }
    }
}

@Composable
private fun PreviewSubsection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

@Composable
private fun SurfaceLadderStrip(scheme: ColorScheme) {
    val swatches =
        listOf(
            scheme.surfaceContainerLowest to stringResource(R.string.appearance_preview_label_lowest),
            scheme.surface to stringResource(R.string.appearance_preview_label_surface),
            scheme.surfaceContainerLow to stringResource(R.string.appearance_preview_label_low),
            scheme.surfaceContainer to stringResource(R.string.appearance_preview_label_base),
            scheme.surfaceContainerHigh to stringResource(R.string.appearance_preview_label_high),
            scheme.surfaceContainerHighest to stringResource(R.string.appearance_preview_label_highest),
        )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
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
                        style = MaterialTheme.typography.labelSmall,
                        color = contrastingTextColor(color),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentContainersStrip(scheme: ColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            label = stringResource(R.string.appearance_preview_label_primary),
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.secondaryContainer,
            onContainer = scheme.onSecondaryContainer,
            label = stringResource(R.string.appearance_preview_label_secondary),
        )
        AccentChip(
            modifier = Modifier.weight(1f),
            container = scheme.tertiaryContainer,
            onContainer = scheme.onTertiaryContainer,
            label = stringResource(R.string.appearance_preview_label_tertiary),
        )
    }
}

@Composable
private fun AccentChip(
    modifier: Modifier,
    container: Color,
    onContainer: Color,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = container,
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
