package dev.bikram.remember.ui.edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import java.util.Locale
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.feedback.tapSoundClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPicker(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalContext.current.resources
    val normalizedCurrent = remember(current) { normalizeIconKey(current) }
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
            RememberTextButton(onClick = { emojiDialogOpen = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RememberMaterialRoundedSymbol(
                        name = "emoji_emotions",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.icon_picker_choose_emoji))
                }
            }
            if (current != null) {
                RememberTextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.common_remove)) }
            }
            RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
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
                    RememberIconButton(onClick = { searchExpanded = true }) {
                        RememberMaterialRoundedSymbol(
                            name = "search",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                            weight = FontWeight.Medium,
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
                            RememberMaterialRoundedSymbol(
                                name = "search",
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                weight = FontWeight.Medium,
                            )
                        },
                        trailingIcon = {
                            RememberIconButton(
                                onClick = {
                                    searchExpanded = false
                                    searchQuery = ""
                                },
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    weight = FontWeight.Medium,
                                )
                            }
                        },
                    )
                }
            }

            Box(modifier = Modifier.heightIn(min = 240.dp, max = 520.dp)) {
                when {
                    trimmedQuery.isEmpty() -> IconPickerGrid {
                        iconCatalog.forEach { category ->
                            iconHeader(category.nameRes, topPadding = 8.dp)
                            // Keys must be unique in the whole grid: the same [IconChoice.key] can repeat
                            // across categories (e.g. airplane_ticket in Maps and Social).
                            itemsIndexed(
                                category.icons,
                                key = { index, _ -> "${category.nameRes}_$index" },
                            ) { _, choice ->
                                IconTile(
                                    choice = choice,
                                    selected = choice.key == normalizedCurrent,
                                    onClick = { onPick(choice.key) },
                                )
                            }
                        }
                    }
                    filteredOrdered.isEmpty() -> Text(
                        text = stringResource(R.string.icon_picker_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    else -> IconPickerGrid {
                        iconHeader(R.string.icon_picker_results_heading, topPadding = 4.dp)
                        itemsIndexed(
                            filteredOrdered,
                            key = { index, choice -> "icon_picker_search_${index}_${choice.key}" },
                        ) { _, choice ->
                            IconTile(
                                choice = choice,
                                selected = choice.key == normalizedCurrent,
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
                RememberTextButton(
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
                RememberTextButton(
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

/**
 * Extra stems matched against icon ligature / label text when the user types a **concept**
 * (e.g. "work") that does not literally appear in names like `laptop` or `business_center`.
 * Keys must be lowercase. Values are lowercase substrings that appear in real symbol names
 * or humanized labels in [BundledMaterialSymbolIcons].
 */
private val searchConceptSynonyms: Map<String, List<String>> = mapOf(
    "work" to listOf("business", "job", "office", "laptop", "desktop", "computer", "corporate", "domain", "engineering", "assignment", "workspace", "meeting", "schedule", "chart"),
    "job" to listOf("business", "work", "laptop", "corporate", "engineering", "assignment"),
    "office" to listOf("business", "work", "laptop", "desktop", "corporate", "domain", "building", "apartment", "meeting"),
    "home" to listOf("house", "door", "family", "pets", "garden", "bed", "chair", "sofa", "kitchen", "home"),
    "house" to listOf("home", "door", "apartment", "hotel", "cottage", "garage"),
    "love" to listOf("favorite", "heart", "valentine", "romance", "partner"),
    "money" to listOf("attach", "currency", "euro", "payment", "card", "wallet", "savings", "paid", "lira"),
    "try" to listOf("lira", "currency"),
    "travel" to listOf("flight", "train", "hotel", "map", "luggage", "beach", "car", "vacation", "airplane", "passport"),
    "trip" to listOf("flight", "train", "hotel", "map", "luggage", "car", "airplane"),
    "food" to listOf("restaurant", "cafe", "pizza", "cake", "coffee", "bar", "local", "dining", "fastfood", "bakery"),
    "sport" to listOf("fitness", "pool", "gym", "sports", "football", "basketball", "tennis", "golf", "exercise"),
    "music" to listOf("mic", "headphones", "album", "library", "audio", "volume", "radio"),
    "photo" to listOf("camera", "image", "gallery", "picture", "photo", "lens"),
    "time" to listOf("calendar", "clock", "alarm", "schedule", "hour", "today", "event"),
    "security" to listOf("lock", "key", "shield", "visibility", "password", "fingerprint", "vpn"),
    "delete" to listOf("trash", "delete", "remove", "sweep", "close", "clear"),
    "mail" to listOf("email", "mail", "send", "drafts", "inbox", "reply"),
    "email" to listOf("mail", "send", "drafts", "inbox", "reply"),
    "phone" to listOf("call", "phone", "mobile", "contact", "sim", "voicemail"),
    "people" to listOf("person", "group", "face", "family", "contacts", "public"),
    "car" to listOf("directions", "traffic", "taxi", "parking", "electric", "suv", "sedan"),
    "health" to listOf("medical", "medication", "local", "hospital", "fitness", "monitor", "spa"),
    "game" to listOf("sports", "esports", "casino", "toys", "puzzle", "stadia"),
    "school" to listOf("school", "book", "menu_book", "science", "calculate", "backpack"),
    "shop" to listOf("shopping", "cart", "store", "basket", "payment", "sell"),
    "wifi" to listOf("wifi", "network", "router", "signal", "bluetooth", "cell"),
    "battery" to listOf("battery", "charging", "power", "bolt"),
    "location" to listOf("location", "map", "place", "navigation", "gps", "pin", "near"),
    "weather" to listOf("wb", "sunny", "rain", "snow", "cloud", "storm", "thermostat", "air"),
    "idea" to listOf("lightbulb", "tips", "emoji", "psychology", "science"),
    "write" to listOf("edit", "note", "pen", "draw", "post", "sticky"),
    "todo" to listOf("check", "list", "task", "done", "rule", "assignment"),
)

private fun iconChoicesRankedForSearch(resources: Resources, rawQuery: String): List<IconChoice> {
    val query = rawQuery.lowercase(Locale.getDefault())
    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val scored = ArrayList<Pair<IconChoice, Float>>(iconCatalog.sumOf { it.icons.size })
    iconCatalog.forEach { category ->
        val categoryLabel = resources.getString(category.nameRes).lowercase(Locale.getDefault())
        category.icons.forEach { choice ->
            val iconLabel = humanizeIconKey(choice.key).lowercase(Locale.getDefault())
            val keyWords = iconKeyToSearchWords(choice.key)
            val combined = "$iconLabel $keyWords $categoryLabel"
            val lowestTokenScore = tokens.minOfOrNull { token ->
                bestConceptualTokenScore(token, combined, iconLabel, keyWords, categoryLabel)
            } ?: 0f
            if (lowestTokenScore > 0f) {
                scored += choice to (lowestTokenScore + iconLabel.length * 0.001f)
            }
        }
    }
    // Same persisted [IconChoice.key] can appear in multiple catalog categories; keep the
    // highest-scoring row and unique keys so LazyVerticalGrid does not throw on duplicates.
    return scored
        .sortedWith(
            compareByDescending<Pair<IconChoice, Float>> { it.second }
                .thenBy { entry -> humanizeIconKey(entry.first.key).lowercase(Locale.getDefault()) },
        )
        .distinctBy { scoredEntry -> scoredEntry.first.key }
        .map { scoredEntry -> scoredEntry.first }
}

private fun bestConceptualTokenScore(
    token: String,
    combinedLower: String,
    labelLower: String,
    keyWordsLower: String,
    categoryLower: String,
): Float {
    val tokenLower = token.lowercase(Locale.getDefault())
    val synonyms = searchConceptSynonyms[tokenLower].orEmpty()
    val stems = buildList {
        add(tokenLower)
        addAll(synonyms)
    }
    return stems.maxOfOrNull { stem ->
        tokenMatchStrength(stem, combinedLower, labelLower, keyWordsLower, categoryLower)
    } ?: 0f
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

private fun iconKeyToSearchWords(key: String): String {
    val raw = when {
        key.startsWith(ICON_SYMBOL_PREFIX) -> key.removePrefix(ICON_SYMBOL_PREFIX)
        key.startsWith(ICON_DRAWABLE_PREFIX) -> key.removePrefix(ICON_DRAWABLE_PREFIX)
        else -> key
    }
    return raw.replace('_', ' ').lowercase(Locale.getDefault())
}

@Composable
private fun IconPickerGrid(
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun LazyGridScope.iconHeader(@StringRes labelRes: Int, topPadding: Dp) {
    item(span = { GridItemSpan(5) }) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = 2.dp),
        )
    }
}

@Composable
private fun IconTile(
    choice: IconChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = humanizeIconKey(choice.key)
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .tapSoundClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val symbolName = choice.symbolName
            if (symbolName != null) {
                RememberMaterialRoundedSymbol(
                    name = symbolName,
                    size = 22.dp,
                    tint = fg,
                    weight = FontWeight.Medium,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    painterResource(choice.drawableRes!!),
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
