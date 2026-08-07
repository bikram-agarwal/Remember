package dev.bikram.remember.ui.feedback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/**
 * Long-press haptic is fired centrally here - in parity with FilePipe's
 * `ui/feedback/ClickModifiers.kt` - so anything long-pressable buzzes without each caller
 * remembering to ask for it. Do NOT add a `performLongPressHaptic()` call inside an `onLongClick`
 * passed to this modifier; it would double-buzz.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.appCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier =
    composed {
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        combinedClickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            role = role,
            indication = indication,
            interactionSource = interactionSource,
            onClick = onClick,
            onLongClick =
                if (onLongClick != null) {
                    {
                        if (hapticEnabled) view.performLongPressHaptic()
                        onLongClick()
                    }
                } else {
                    null
                },
        )
    }

fun Modifier.appClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier =
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        indication = indication,
        interactionSource = interactionSource,
        onClick = onClick,
    )
