package dev.bikram.remember.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint

private val NoteCardShape = RoundedCornerShape(20.dp)

@Composable
fun SwipeableRememberNoteCard(
    note: NoteWithItems,
    interaction: InteractionState,
    onOpenNote: (NoteWithItems) -> Unit,
    onSwipeAction: (NoteWithItems, NoteSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeStart = interaction.swipeStartToEnd
    val swipeEnd = interaction.swipeEndToStart
    DeliberateSwipeRevealCard(
        modifier = modifier.fillMaxWidth(),
        commitThresholdFraction = 0.35f,
        cardShape = NoteCardShape,
        hapticEnabled = interaction.hapticFeedbackEnabled,
        onSwipeStartToEnd = { onSwipeAction(note, swipeStart) },
        onSwipeEndToStart = { onSwipeAction(note, swipeEnd) },
        backgroundContent = { fromStart ->
            val action = if (fromStart) swipeStart else swipeEnd
            val backgroundColor by animateColorAsState(
                targetValue = action.semanticSwipeBackground(),
                animationSpec = tween(300),
                label = "swipeBg",
            )
            val tint = action.semanticSwipeIconTint()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fromStart) {
                        RememberMaterialRoundedSymbol(
                            name = action.materialSymbolName,
                            size = 20.dp,
                            tint = tint,
                            weight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = action.labelString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                    } else {
                        Text(
                            text = action.labelString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                        )
                        Spacer(Modifier.width(6.dp))
                        RememberMaterialRoundedSymbol(
                            name = action.materialSymbolName,
                            size = 20.dp,
                            tint = tint,
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
    ) {
        NoteCard(
            note = note,
            onClick = { onOpenNote(note) },
        )
    }
}

@Composable
private fun NoteSwipeAction.labelString(): String = stringResource(
    when (this) {
        NoteSwipeAction.OPEN -> R.string.swipe_action_open
        NoteSwipeAction.TRASH -> R.string.swipe_action_trash
        NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
        NoteSwipeAction.TOGGLE_PIN -> R.string.swipe_action_toggle_pin
    },
)
