package dev.bikram.remember.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.feedback.LocalHapticEnabled
import dev.bikram.remember.ui.feedback.performLongPressHaptic
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val FLOATING_ACTION_TOOLTIP_DISMISS_MILLIS = 5_000L

@Composable
fun rememberResponsiveActionButtonSize(
    defaultSize: Dp = 40.dp,
    compactSize: Dp = 34.dp,
    ultraCompactSize: Dp = 30.dp,
): Dp {
    val screenWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val targetSize =
        when {
            screenWidth < 360.dp -> ultraCompactSize
            screenWidth < 430.dp -> compactSize
            else -> defaultSize
        }
    return targetSize
}

@Composable
fun rememberResponsiveActionIconSize(
    defaultSize: Dp = 20.dp,
    compactSize: Dp = 17.dp,
    ultraCompactSize: Dp = 15.dp,
): Dp {
    val screenWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val targetSize =
        when {
            screenWidth < 360.dp -> ultraCompactSize
            screenWidth < 430.dp -> compactSize
            else -> defaultSize
        }
    return targetSize
}

@Composable
fun RememberActionLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RememberLongPressLabelTooltip(
    label: String?,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (label == null) {
        content()
        return
    }

    val tooltipState = rememberTooltipState(isPersistent = true)
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current

    // The label tooltip is itself a long-press affordance, so it buzzes like any other long-press.
    // Matches FilePipe's tooltip-bearing icon buttons.
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible && hapticEnabled) {
            view.performLongPressHaptic()
        }
    }

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Above,
            ),
        tooltip = {
            PlainTooltip {
                Box(
                    modifier = Modifier.heightIn(min = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label)
                }
            }
        },
        state = tooltipState,
        modifier =
            Modifier.showLabelTooltipOnLongPress(
                enabled = enabled,
                tooltipState = tooltipState,
            ),
        enableUserInput = false,
    ) {
        content()
    }
}

private fun Modifier.showLabelTooltipOnLongPress(
    enabled: Boolean,
    tooltipState: TooltipState,
): Modifier {
    if (!enabled) return this

    return pointerInput(tooltipState) {
        coroutineScope {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val releasedBeforeLongPress =
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation(PointerEventPass.Initial)
                    }
                if (releasedBeforeLongPress == null) {
                    launch { tooltipState.show() }

                    var labelFingerReleased = false
                    while (!labelFingerReleased) {
                        val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val activePointerChange =
                            pointerEvent.changes.firstOrNull { pointerChange ->
                                pointerChange.id == firstDown.id
                            }
                        activePointerChange?.consume()
                        labelFingerReleased = activePointerChange?.pressed != true
                    }

                    launch {
                        delay(FLOATING_ACTION_TOOLTIP_DISMISS_MILLIS)
                        tooltipState.dismiss()
                    }
                }
            }
        }
    }
}

@Composable
fun RememberToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ToggleButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shapes = shapes,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    RememberLongPressLabelTooltip(
        label = tooltipLabel,
        enabled = enabled,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(
                onClick = {
                    onClick()
                },
                modifier = modifier,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}

@Composable
fun RememberFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    RememberLongPressLabelTooltip(
        label = tooltipLabel,
        enabled = enabled,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FilledIconButton(
                onClick = {
                    onClick()
                },
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}

@Composable
fun RememberFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    RememberLongPressLabelTooltip(
        label = tooltipLabel,
        enabled = enabled,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FilledTonalIconButton(
                onClick = {
                    onClick()
                },
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}

@Composable
fun RememberButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBorder =
        border ?: BorderStroke(
            width = 1.dp,
            color =
                if (enabled) {
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                } else {
                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.12f)
                },
        )
    OutlinedButton(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = resolvedBorder,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    tooltipLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val isSmallLandscape = isSmallLandscape()
    val sizeModifier =
        if (isSmallLandscape) {
            Modifier.size(rememberResponsiveActionButtonSize())
        } else {
            Modifier
        }
    RememberLongPressLabelTooltip(
        label = tooltipLabel,
        enabled = true,
    ) {
        FloatingActionButton(
            onClick = {
                if (enabled) {
                    onClick()
                }
            },
            modifier = modifier.then(sizeModifier),
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun RememberExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = androidx.compose.material3.contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    ExtendedFloatingActionButton(
        text = text,
        icon = icon,
        onClick = {
            onClick()
        },
        modifier = modifier,
        expanded = expanded,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = androidx.compose.ui.graphics.RectangleShape,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
    contentColor: Color = androidx.compose.material3.contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberElevatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.elevatedShape,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ElevatedCard(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.outlinedShape,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    elevation: CardElevation = CardDefaults.outlinedCardElevation(),
    border: BorderStroke = CardDefaults.outlinedCardBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    OutlinedCard(
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun RememberFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = androidx.compose.material3.FilterChipDefaults.shape,
    colors: androidx.compose.material3.SelectableChipColors =
        androidx.compose.material3.FilterChipDefaults
            .filterChipColors(),
    elevation: androidx.compose.material3.SelectableChipElevation? =
        androidx.compose.material3.FilterChipDefaults
            .filterChipElevation(),
    border: androidx.compose.foundation.BorderStroke? =
        androidx.compose.material3.FilterChipDefaults
            .filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    FilterChip(
        selected = selected,
        onClick = {
            onClick()
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = androidx.compose.material3.InputChipDefaults.shape,
    colors: androidx.compose.material3.SelectableChipColors =
        androidx.compose.material3.InputChipDefaults
            .inputChipColors(),
    elevation: androidx.compose.material3.SelectableChipElevation? =
        androidx.compose.material3.InputChipDefaults
            .inputChipElevation(),
    border: androidx.compose.foundation.BorderStroke? =
        androidx.compose.material3.InputChipDefaults
            .inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    androidx.compose.material3.InputChip(
        selected = selected,
        onClick = {
            onClick()
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        avatar = avatar,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    // The checked thumb is painted with onPrimary, so the check has to be drawn in primary to read
    // against it. Material's own default pairs an onPrimary thumb with an onPrimaryContainer icon,
    // which only contrasts under the 2021 color spec; the app generates its scheme with the 2025
    // spec, where onPrimaryContainer is itself dark in dark mode - leaving a near-black check on a
    // near-black thumb. Keep in parity with FilePipe's FilePipeSwitch.
    colors: androidx.compose.material3.SwitchColors =
        androidx.compose.material3.SwitchDefaults
            .colors(checkedIconColor = MaterialTheme.colorScheme.primary),
    interactionSource: MutableInteractionSource? = null,
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange =
            if (onCheckedChange != null) {
                {
                    onCheckedChange(it)
                }
            } else {
                null
            },
        modifier = modifier,
        thumbContent = thumbContent,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.CheckboxColors =
        androidx.compose.material3.CheckboxDefaults
            .colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    androidx.compose.material3.Checkbox(
        checked = checked,
        onCheckedChange =
            if (onCheckedChange != null) {
                {
                    onCheckedChange(it)
                }
            } else {
                null
            },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors =
        androidx.compose.material3.MenuDefaults
            .itemColors(),
    contentPadding: PaddingValues = androidx.compose.material3.MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    DropdownMenuItem(
        text = text,
        onClick = {
            onClick()
        },
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    )
}

@Composable
fun RememberTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = androidx.compose.material3.LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
) {
    Tab(
        selected = selected,
        onClick = {
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        text = text,
        icon = icon,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        interactionSource = interactionSource,
    )
}
