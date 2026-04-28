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

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tapSoundCombinedClickable(
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
        val playTap = rememberPlayTapSound()
        val hapticEnabled = LocalHapticEnabled.current
        val view = LocalView.current
        val actualIndication = indication ?: androidx.compose.foundation.LocalIndication.current
        val actualInteractionSource = interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() }
        combinedClickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            role = role,
            indication = actualIndication,
            interactionSource = actualInteractionSource,
            onClick = {
                playTap()
                onClick()
            },
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

fun Modifier.tapSoundClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier =
    composed {
        val playTap = rememberPlayTapSound()
        val actualIndication = indication ?: androidx.compose.foundation.LocalIndication.current
        val actualInteractionSource = interactionSource ?: androidx.compose.runtime.remember { MutableInteractionSource() }
        clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            indication = actualIndication,
            interactionSource = actualInteractionSource,
            onClick = {
                playTap()
                onClick()
            },
        )
    }
