package dev.bikram.remember.ui.modifiers

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun rememberContentOverflowScrollEnabled(
    listState: LazyListState,
    additionalScrollEnabled: Boolean = false,
): Boolean {
    val scrollEnabled by remember(listState, additionalScrollEnabled) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            when {
                additionalScrollEnabled -> true
                layoutInfo.totalItemsCount == 0 || visibleItems.isEmpty() -> false
                visibleItems.size < layoutInfo.totalItemsCount -> true
                else -> {
                    val firstItemTop = visibleItems.minOf { item -> item.offset }
                    val lastItemBottom = visibleItems.maxOf { item -> item.offset + item.size }
                    val contentHeight = lastItemBottom - firstItemTop
                    val viewportHeight =
                        layoutInfo.viewportSize.height -
                            layoutInfo.beforeContentPadding -
                            layoutInfo.afterContentPadding
                    contentHeight > viewportHeight.coerceAtLeast(0)
                }
            }
        }
    }
    return scrollEnabled
}
