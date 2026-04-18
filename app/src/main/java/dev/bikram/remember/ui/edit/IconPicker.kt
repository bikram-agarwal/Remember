package dev.bikram.remember.ui.edit

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.AppBottomSheet
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPicker(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalContext.current.resources
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var emojiDialogOpen by rememberSaveable { mutableStateOf(false) }
    var emojiDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            searchFocusRequester.requestFocus()
        }
    }

    val trimmedQuery = searchQuery.trim()
    val filteredOrdered = remember(trimmedQuery, resources.configuration) {
        if (trimmedQuery.isEmpty()) emptyList()
        else iconChoicesRankedForSearch(resources, trimmedQuery)
    }

    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        showTitleBar = false,
        scrollable = false,
        actions = {
            TextButton(onClick = { emojiDialogOpen = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = stringResource(R.string.icon_picker_choose_emoji_cd),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.icon_picker_choose_emoji))
                }
            }
            if (current != null) {
                TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.common_remove)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!searchExpanded) {
                    Text(
                        text = stringResource(R.string.icon_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { searchExpanded = true }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.icon_picker_search_cd),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.icon_picker_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    searchExpanded = false
                                    searchQuery = ""
                                },
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_clear))
                            }
                        },
                    )
                }
            }

            Box(modifier = Modifier.heightIn(min = 240.dp, max = 520.dp)) {
                if (trimmedQuery.isEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        iconCatalog.forEach { category ->
                            item(span = { GridItemSpan(5) }) {
                                Text(
                                    text = stringResource(category.nameRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(category.icons, key = { it.key }) { choice ->
                                IconTile(
                                    choice = choice,
                                    selected = choice.key == current,
                                    onClick = { onPick(choice.key) },
                                )
                            }
                        }
                    }
                } else if (filteredOrdered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.icon_picker_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(span = { GridItemSpan(5) }) {
                            Text(
                                text = stringResource(R.string.icon_picker_results_heading),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(filteredOrdered, key = { it.key }) { choice ->
                            IconTile(
                                choice = choice,
                                selected = choice.key == current,
                                onClick = { onPick(choice.key) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (emojiDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                emojiDialogOpen = false
                emojiDraft = ""
            },
            title = { Text(stringResource(R.string.icon_picker_emoji_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = emojiDraft,
                    onValueChange = { entered -> emojiDraft = entered.take(64) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text(stringResource(R.string.icon_picker_emoji_dialog_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = emojiDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onPick("$ICON_EMOJI_PREFIX$trimmed")
                            emojiDialogOpen = false
                            emojiDraft = ""
                        }
                    },
                    enabled = emojiDraft.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.icon_picker_emoji_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        emojiDialogOpen = false
                        emojiDraft = ""
                    },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun iconChoicesRankedForSearch(resources: Resources, rawQuery: String): List<IconChoice> {
    val query = rawQuery.lowercase(Locale.getDefault())
    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val scored = ArrayList<Pair<IconChoice, Float>>(iconCatalog.sumOf { it.icons.size })
    iconCatalog.forEach { category ->
        val categoryLabel = resources.getString(category.nameRes).lowercase(Locale.getDefault())
        category.icons.forEach { choice ->
            val iconLabel = resources.getString(choice.labelRes).lowercase(Locale.getDefault())
            val keyWords = camelKeyToSearchWords(choice.key)
            val combined = "$iconLabel $keyWords $categoryLabel"
            val lowestTokenScore = tokens.minOfOrNull { token ->
                tokenMatchStrength(token, combined, iconLabel, keyWords, categoryLabel)
            } ?: 0f
            if (lowestTokenScore > 0f) {
                scored += choice to (lowestTokenScore + iconLabel.length * 0.001f)
            }
        }
    }
    return scored
        .sortedWith(
            compareByDescending<Pair<IconChoice, Float>> { it.second }
                .thenBy { resources.getString(it.first.labelRes).lowercase(Locale.getDefault()) },
        )
        .map { it.first }
}

private fun tokenMatchStrength(
    token: String,
    combinedLower: String,
    labelLower: String,
    keyWordsLower: String,
    categoryLower: String,
): Float {
    if (token.isEmpty()) return 1f
    if (combinedLower.contains(token)) return 1f + token.length * 0.04f
    val bestField = maxOf(
        subsequenceStrength(token, labelLower),
        subsequenceStrength(token, keyWordsLower),
        subsequenceStrength(token, categoryLower),
    )
    if (bestField > 0f) return bestField * 0.92f
    return 0f
}

private fun subsequenceStrength(token: String, field: String): Float {
    if (token.isEmpty() || field.isEmpty()) return 0f
    if (!isSubsequence(token, field)) return 0f
    return 0.55f + token.length.toFloat() / (field.length + 3).coerceAtLeast(1)
}

private fun isSubsequence(tokenLower: String, fieldLower: String): Boolean {
    var tokenIndex = 0
    fieldLower.forEach { ch ->
        if (tokenIndex < tokenLower.length && ch == tokenLower[tokenIndex]) {
            tokenIndex++
        }
    }
    return tokenIndex == tokenLower.length
}

private fun camelKeyToSearchWords(key: String): String {
    return key.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2")
        .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")
        .lowercase(Locale.getDefault())
}

@Composable
private fun IconTile(
    choice: IconChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(choice.labelRes)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                choice.vector,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
