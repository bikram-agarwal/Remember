package com.example.checklist.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.checklist.R

/**
 * Material 3 Expressive indentation per nesting level. M3E tends toward slightly
 * larger spacing tokens than plain M3, and 24dp reads as a clear "this row is a
 * child" without dominating the layout.
 */
private val ChildIndent = 24.dp

/**
 * Horizontal-drag distance (in pixels) that triggers a nest/unnest commit.
 * Sized for a comfortable thumb swipe; tune on device if needed.
 */
private const val NEST_THRESHOLD_PX = 120f
private const val DRAG_MAX_TRANSLATION_PX = 220f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var newItemText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checklist_title)) },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            NewItemBar(
                value = newItemText,
                onValueChange = { newItemText = it },
                onAdd = {
                    viewModel.addItem(newItemText)
                    newItemText = ""
                },
            )

            // Single LazyColumn holds both sections so animateItem() can
            // animate rows moving across the section boundary.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item(key = "section-active") {
                    SectionHeader(
                        title = stringResource(R.string.section_active),
                        modifier = Modifier.animateItem(),
                    )
                }

                items(
                    items = state.active,
                    key = { it.key },
                ) { row ->
                    ChecklistRowUi(
                        row = row,
                        precedingTopLevelId = state.precedingTopLevelIds[row.key],
                        onToggle = { checked ->
                            (row as? ChecklistRow.Item)?.item?.let {
                                viewModel.toggleChecked(it.id, checked)
                            }
                        },
                        onNest = { itemId, parentId -> viewModel.nestUnder(itemId, parentId) },
                        onUnnest = { itemId -> viewModel.unnest(itemId) },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (state.completed.isNotEmpty()) {
                    item(key = "section-completed") {
                        SectionHeader(
                            title = stringResource(R.string.section_completed),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                items(
                    items = state.completed,
                    key = { it.key },
                ) { row ->
                    ChecklistRowUi(
                        row = row,
                        precedingTopLevelId = null,
                        onToggle = { checked ->
                            (row as? ChecklistRow.Item)?.item?.let {
                                viewModel.toggleChecked(it.id, checked)
                            }
                        },
                        // Horizontal drag is disabled in the completed section.
                        onNest = { _, _ -> },
                        onUnnest = { _ -> },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewItemBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.new_item_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onAdd) {
            Text(stringResource(R.string.add))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        // M3 Expressive emphasized typography token; gives the section header the
        // proper weight without introducing a custom TextStyle.
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ChecklistRowUi(
    row: ChecklistRow,
    precedingTopLevelId: Int?,
    onToggle: (Boolean) -> Unit,
    onNest: (itemId: Int, parentId: Int) -> Unit,
    onUnnest: (itemId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (row) {
        is ChecklistRow.Item -> ChecklistItemRow(
            row = row,
            precedingTopLevelId = precedingTopLevelId,
            onToggle = onToggle,
            onNest = onNest,
            onUnnest = onUnnest,
            modifier = modifier,
        )
        is ChecklistRow.GhostParent -> GhostParentRow(
            row = row,
            modifier = modifier,
        )
    }
}

@Composable
private fun ChecklistItemRow(
    row: ChecklistRow.Item,
    precedingTopLevelId: Int?,
    onToggle: (Boolean) -> Unit,
    onNest: (itemId: Int, parentId: Int) -> Unit,
    onUnnest: (itemId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = row.item
    // Remembered per item.id so dragging one row doesn't bleed offset onto another
    // after recomposition / list shuffling.
    var dragOffset by remember(item.id) { mutableStateOf(0f) }
    val startPadding = ChildIndent * item.depth

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .graphicsLayer { translationX = dragOffset }
            .pointerInput(item.id, item.depth, precedingTopLevelId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // Rightward past threshold on a top-level item: nest under
                        // the nearest preceding top-level item (if any).
                        val droppedRight = dragOffset > NEST_THRESHOLD_PX
                        val droppedLeft = dragOffset < -NEST_THRESHOLD_PX
                        if (droppedRight && item.depth == 0 && precedingTopLevelId != null) {
                            onNest(item.id, precedingTopLevelId)
                        } else if (droppedLeft && item.depth == 1) {
                            onUnnest(item.id)
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { _, delta ->
                    val next = dragOffset + delta
                    dragOffset = next.coerceIn(
                        -DRAG_MAX_TRANSLATION_PX,
                        DRAG_MAX_TRANSLATION_PX,
                    )
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onToggle,
            )
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GhostParentRow(
    row: ChecklistRow.GhostParent,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.parent.text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.ghost_parent_suffix),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
