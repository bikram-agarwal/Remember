package dev.bikram.remember.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.theme.elevatedCardColors

enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

private val innerRadius = 4.dp

@Composable
fun groupedItemShape(position: GroupPosition): CornerBasedShape {
    val outerShape = MaterialTheme.shapes.large
    val innerShape = MaterialTheme.shapes.extraSmall
    return when (position) {
        GroupPosition.FIRST ->
            outerShape.copy(
                bottomStart = CornerSize(innerRadius),
                bottomEnd = CornerSize(innerRadius),
            )
        GroupPosition.MIDDLE -> innerShape
        GroupPosition.LAST ->
            outerShape.copy(
                topStart = CornerSize(innerRadius),
                topEnd = CornerSize(innerRadius),
            )
        GroupPosition.ONLY -> outerShape
    }
}

@Composable
fun GroupedListItem(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable () -> Unit,
) {
    val cardColors = elevatedCardColors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupedItemShape(position),
        color = color ?: cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
fun GroupedListColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}
