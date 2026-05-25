package dev.bikram.remember.ui.edit

import android.content.res.Resources
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bikram.remember.R
import dev.bikram.remember.data.ICON_PICKER_MAX_STARRED
import dev.bikram.remember.data.IconPickerPrefs
import dev.bikram.remember.data.IconPickerStarredState
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberToggleButton
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPicker(
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    isChecklist: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val iconPickerPrefs = remember(context.applicationContext) { IconPickerPrefs(context.applicationContext) }
    val starredState by iconPickerPrefs.starred.collectAsState(initial = IconPickerStarredState())
    val scope = rememberCoroutineScope()
    val defaultCatalogKey = remember(isChecklist) { defaultIconCatalogKey(isChecklist) }
    val selectionKey =
        remember(current, isChecklist, defaultCatalogKey) {
            if (current.isNullOrBlank()) {
                defaultCatalogKey
            } else {
                normalizeIconKey(current) ?: defaultCatalogKey
            }
        }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchFocusRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(defaultIconPickerTab(current)) }
    var symbolStyle by rememberSaveable(current) {
        mutableStateOf(initialIconPickerSymbolStyle(current ?: selectionKey))
    }
    var starredSelectionTab by rememberSaveable { mutableStateOf<IconPickerTab?>(null) }
    var pendingStarredIconKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingStarredEmojis by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val iconSheetContentHeight =
        if (imeBottom > 0.dp) {
            (configuration.screenHeightDp.dp - imeBottom - 116.dp).coerceIn(360.dp, 644.dp)
        } else {
            644.dp
        }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val emojis =
        remember(configuration) {
            loadBundledEmojis(resources)
        }
    val iconKeywords =
        remember(configuration) {
            loadIconKeywords(resources)
        }
    val iconAliases =
        remember(configuration) {
            loadIconAliases(resources)
        }
    // Build the skin-tone group index once; rebuilding it on every sub-tab
    // switch was the main culprit behind the laggy category change.
    val emojiSkinToneIndex =
        remember(emojis) {
            buildEmojiSkinToneIndex(emojis)
        }
    val selectedEmoji = current?.takeIf { it.startsWith(ICON_EMOJI_PREFIX) }?.removePrefix(ICON_EMOJI_PREFIX)
    var selectedEmojiCategoryKey by rememberSaveable {
        mutableStateOf(selectedEmojiCategoryKey(emojis, selectedEmoji))
    }
    val starredModeActive = starredSelectionTab != null
    val titleRes =
        if (starredModeActive) {
            R.string.icon_picker_starred_selection_title
        } else {
            R.string.icon_picker_title
        }
    val pendingStarredCount =
        when (starredSelectionTab) {
            IconPickerTab.ICONS -> pendingStarredIconKeys.size
            IconPickerTab.EMOJIS -> pendingStarredEmojis.size
            null -> 0
        }
    val maxStarredMessage = stringResource(R.string.icon_picker_max_starred)

    fun beginStarredSelection(tab: IconPickerTab) {
        starredSelectionTab = tab
        pendingStarredIconKeys =
            starredState.iconKeys
                .map { normalizeFavoriteIconKey(it) }
                .distinct()
        pendingStarredEmojis = starredState.emojis
        selectedTab = tab
    }

    fun cancelStarredSelection() {
        starredSelectionTab = null
        pendingStarredIconKeys = emptyList()
        pendingStarredEmojis = emptyList()
    }

    fun saveStarredSelection() {
        val activeTab = starredSelectionTab ?: return
        scope.launch {
            when (activeTab) {
                IconPickerTab.ICONS -> iconPickerPrefs.setStarredIconKeys(pendingStarredIconKeys)
                IconPickerTab.EMOJIS -> iconPickerPrefs.setStarredEmojis(pendingStarredEmojis)
            }
            cancelStarredSelection()
        }
    }

    fun togglePendingStarredIcon(iconKey: String) {
        val savedIconKey = normalizeIconKey(iconKey) ?: iconKey
        pendingStarredIconKeys =
            if (savedIconKey in pendingStarredIconKeys) {
                pendingStarredIconKeys - savedIconKey
            } else if (pendingStarredIconKeys.size >= ICON_PICKER_MAX_STARRED) {
                Toast.makeText(context, maxStarredMessage, Toast.LENGTH_SHORT).show()
                pendingStarredIconKeys
            } else {
                pendingStarredIconKeys + savedIconKey
            }
    }

    fun togglePendingStarredEmoji(emoji: String) {
        pendingStarredEmojis =
            if (emoji in pendingStarredEmojis) {
                pendingStarredEmojis - emoji
            } else if (pendingStarredEmojis.size >= ICON_PICKER_MAX_STARRED) {
                Toast.makeText(context, maxStarredMessage, Toast.LENGTH_SHORT).show()
                pendingStarredEmojis
            } else {
                pendingStarredEmojis + emoji
            }
    }

    fun toggleSavedStarredIcon(
        iconKey: String,
        label: String,
    ) {
        val savedIconKeys =
            starredState.iconKeys
                .map { normalizeFavoriteIconKey(it) }
                .distinct()
        val nextStarredIconKeys =
            if (iconKey in savedIconKeys) {
                savedIconKeys - iconKey
            } else if (savedIconKeys.size >= ICON_PICKER_MAX_STARRED) {
                Toast.makeText(context, maxStarredMessage, Toast.LENGTH_SHORT).show()
                return
            } else {
                savedIconKeys + iconKey
            }
        pendingStarredIconKeys = nextStarredIconKeys
        val messageRes =
            if (iconKey in savedIconKeys) {
                R.string.icon_picker_removed_from_favorites
            } else {
                R.string.icon_picker_added_to_favorites
            }
        Toast.makeText(context, context.getString(messageRes, label), Toast.LENGTH_SHORT).show()
        scope.launch {
            iconPickerPrefs.setStarredIconKeys(nextStarredIconKeys)
        }
    }

    fun toggleSavedStarredEmoji(
        emoji: String,
        label: String,
    ) {
        val nextStarredEmojis =
            if (emoji in starredState.emojis) {
                starredState.emojis - emoji
            } else if (starredState.emojis.size >= ICON_PICKER_MAX_STARRED) {
                Toast.makeText(context, maxStarredMessage, Toast.LENGTH_SHORT).show()
                return
            } else {
                starredState.emojis + emoji
            }
        pendingStarredEmojis = nextStarredEmojis
        val messageRes =
            if (emoji in starredState.emojis) {
                R.string.icon_picker_removed_from_favorites
            } else {
                R.string.icon_picker_added_to_favorites
            }
        Toast.makeText(context, context.getString(messageRes, label), Toast.LENGTH_SHORT).show()
        scope.launch {
            iconPickerPrefs.setStarredEmojis(nextStarredEmojis)
        }
    }

    // Debounced trimmed query. Without this, every keystroke re-runs the linear scan
    // over ~510 icons + ~3.7k emojis with per-item stem / Levenshtein scoring; on
    // burst typing that's wasted work + visible churn. We propagate clears (empty
    // text) immediately so tapping the X snaps back without lag.
    var trimmedQuery by rememberSaveable { mutableStateOf(searchQuery.trim()) }
    LaunchedEffect(searchQuery) {
        val nextTrimmed = searchQuery.trim()
        if (nextTrimmed.isEmpty()) {
            trimmedQuery = ""
        } else {
            delay(ICON_PICKER_SEARCH_DEBOUNCE_MILLIS)
            trimmedQuery = nextTrimmed
        }
    }
    val filteredOrdered =
        remember(trimmedQuery, configuration, iconKeywords, iconAliases) {
            if (trimmedQuery.isEmpty()) {
                emptyList()
            } else {
                iconChoicesRankedForSearch(resources, iconKeywords, iconAliases, trimmedQuery)
            }
        }
    val activeStarredEmojis =
        if (starredSelectionTab == IconPickerTab.EMOJIS) {
            pendingStarredEmojis
        } else {
            starredState.emojis
        }
    val filteredEmojis =
        remember(emojis, activeStarredEmojis, selectedEmojiCategoryKey, trimmedQuery) {
            emojisRankedForSearch(
                emojis = emojis,
                starredEmojis = activeStarredEmojis,
                selectedCategoryKey = selectedEmojiCategoryKey,
                rawQuery = trimmedQuery,
            )
        }

    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        showTitleBar = false,
        scrollable = false,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(iconSheetContentHeight),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = IconPickerActionToolbarHeight),
            ) {
                IconPickerSearchTitleRow(
                    searchExpanded = searchExpanded,
                    focusRequestKey = searchFocusRequestKey,
                    title = stringResource(titleRes),
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    symbolStyle = symbolStyle,
                    showSymbolStyleToggle = selectedTab == IconPickerTab.ICONS && !starredModeActive,
                    onToggleSymbolStyle = { symbolStyle = symbolStyle.toggled() },
                    onToggleSearch = {
                        if (searchExpanded && searchQuery.isNotEmpty()) {
                            searchQuery = ""
                        }
                        val nextSearchExpanded = !searchExpanded
                        searchExpanded = nextSearchExpanded
                        if (nextSearchExpanded) {
                            searchFocusRequestKey += 1
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 10.dp),
                )

                IconPickerTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (!starredModeActive) {
                            selectedTab = tab
                        }
                    },
                    enabled = !starredModeActive,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                )

                // Slide horizontally in the natural reading direction of the tab order +
                // a soft fade. Specs come from M3 Expressive's MotionScheme so the curve
                // matches every other transition in the app (NoteActionBottomBar etc.).
                val tabSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
                val tabEffectsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
                AnimatedContent(
                    targetState = selectedTab,
                    label = "iconPickerTabContent",
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(animationSpec = tabSpatialSpec) { fullWidth ->
                                direction * fullWidth / 6
                            } + fadeIn(animationSpec = tabEffectsSpec)
                        ).togetherWith(
                            slideOutHorizontally(animationSpec = tabSpatialSpec) { fullWidth ->
                                -direction * fullWidth / 6
                            } + fadeOut(animationSpec = tabEffectsSpec),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) { tab ->
                    when (tab) {
                        IconPickerTab.ICONS ->
                            IconPickerIconsContent(
                                trimmedQuery = trimmedQuery,
                                filteredOrdered = filteredOrdered,
                                defaultCatalogKey = defaultCatalogKey,
                                selectionKey = selectionKey,
                                starredIconKeys = starredState.iconKeys,
                                pendingStarredIconKeys = pendingStarredIconKeys,
                                starredSelectionActive = starredSelectionTab == IconPickerTab.ICONS,
                                symbolStyle = symbolStyle,
                                selectedGridIndex = selectedIconGridIndex(selectionKey, starredState.iconKeys),
                                onStartStarredSelection = { beginStarredSelection(IconPickerTab.ICONS) },
                                onToggleStarIcon = ::togglePendingStarredIcon,
                                onToggleSavedStarIcon = ::toggleSavedStarredIcon,
                                onPick = onPick,
                            )
                        IconPickerTab.EMOJIS ->
                            IconPickerEmojiContent(
                                trimmedQuery = trimmedQuery,
                                filteredEmojis = filteredEmojis,
                                emojiSkinToneIndex = emojiSkinToneIndex,
                                selectedCategoryKey = selectedEmojiCategoryKey,
                                selectedEmoji = selectedEmoji,
                                starredEmojis = starredState.emojis,
                                pendingStarredEmojis = pendingStarredEmojis,
                                starredSelectionActive = starredSelectionTab == IconPickerTab.EMOJIS,
                                onCategorySelected = { selectedEmojiCategoryKey = it },
                                onStartStarredSelection = { beginStarredSelection(IconPickerTab.EMOJIS) },
                                onToggleStarEmoji = ::togglePendingStarredEmoji,
                                onToggleSavedStarEmoji = ::toggleSavedStarredEmoji,
                                onEmojiSelected = { emoji -> onPick("$ICON_EMOJI_PREFIX$emoji") },
                            )
                    }
                }
            }
            IconPickerActionToolbar(
                starredModeActive = starredModeActive,
                pendingStarredCount = pendingStarredCount,
                current = current,
                onCancelStarredSelection = ::cancelStarredSelection,
                onSaveStarredSelection = ::saveStarredSelection,
                onRemove = { onPick(null) },
                onDismiss = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding(),
            )
        }
    }
}

private val IconPickerActionToolbarHeight = 64.dp

@Composable
private fun IconPickerActionToolbar(
    starredModeActive: Boolean,
    pendingStarredCount: Int,
    current: String?,
    onCancelStarredSelection: () -> Unit,
    onSaveStarredSelection: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IconPickerActionToolbarHeight)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (starredModeActive) {
            RememberTextButton(onClick = onCancelStarredSelection) {
                Text(stringResource(R.string.common_cancel))
            }
            StarredDoneButton(
                count = pendingStarredCount,
                onClick = onSaveStarredSelection,
            )
        } else if (current != null) {
            RememberTextButton(onClick = onRemove) {
                Text(stringResource(R.string.common_remove))
            }
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        } else {
            RememberTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_done))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconPickerSearchTitleRow(
    searchExpanded: Boolean,
    focusRequestKey: Int,
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    symbolStyle: IconPickerSymbolStyle,
    showSymbolStyleToggle: Boolean,
    onToggleSymbolStyle: () -> Unit,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val scaleIconSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = searchExpanded,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val enter =
                    fadeIn(fadeInSpec) +
                        expandHorizontally(spatialSpec, expandFrom = Alignment.End)
                val exit =
                    fadeOut(fadeOutSpec) +
                        shrinkHorizontally(spatialSpec, shrinkTowards = Alignment.End)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "iconPickerSearchExpand",
        ) { expanded ->
            if (expanded) {
                IconPickerInlineSearchField(
                    focusRequestKey = focusRequestKey,
                    query = query,
                    onQueryChange = onQueryChange,
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val cdCloseSearch = stringResource(R.string.cd_close_search)
        val cdSearch = stringResource(R.string.cd_search)
        RememberFilledTonalIconButton(onClick = onToggleSearch) {
            AnimatedContent(
                targetState = searchExpanded,
                transitionSpec = {
                    (scaleIn(scaleIconSpec) + fadeIn(fadeInSpec)) togetherWith
                        (scaleOut(scaleIconSpec) + fadeOut(fadeOutSpec))
                },
                label = "iconPickerSearchIconSwap",
            ) { expanded ->
                RememberMaterialRoundedSymbol(
                    name = if (expanded) "close" else "search",
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (expanded) cdCloseSearch else cdSearch
                        },
                )
            }
        }
        if (showSymbolStyleToggle) {
            Spacer(Modifier.width(8.dp))
            val symbolStyleLabel =
                stringResource(
                    if (symbolStyle == IconPickerSymbolStyle.FILLED) {
                        R.string.icon_picker_symbol_style_filled
                    } else {
                        R.string.icon_picker_symbol_style_outlined
                    },
                )
            RememberFilledTonalIconButton(
                onClick = onToggleSymbolStyle,
                tooltipLabel = symbolStyleLabel,
            ) {
                RememberMaterialRoundedSymbol(
                    name =
                        if (symbolStyle == IconPickerSymbolStyle.FILLED) {
                            "radio_button_checked"
                        } else {
                            "radio_button_unchecked"
                        },
                    weight = FontWeight.Medium,
                    filled = symbolStyle.filled,
                    modifier =
                        Modifier.semantics {
                            contentDescription = symbolStyleLabel
                        },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun IconPickerInlineSearchField(
    focusRequestKey: Int,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (query != searchFieldValue.text) {
            searchFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }
    LaunchedEffect(focusRequestKey) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val searchContentDescription = stringResource(R.string.cd_search)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.shapes.extraLargeIncreased,
                ).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = searchFieldValue,
            onValueChange = { newValue ->
                searchFieldValue = newValue
                if (newValue.text != query) {
                    onQueryChange(newValue.text)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = searchContentDescription
                    },
            decorationBox = { innerTextField ->
                if (searchFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.icon_picker_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconPickerTabRow(
    selectedTab: IconPickerTab,
    onTabSelected: (IconPickerTab) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabs = IconPickerTab.entries
    val labels = tabs.map { tab -> stringResource(tab.labelRes) }
    val shapes =
        tabs.mapIndexed { index, _ ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    val colors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    ButtonGroup(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val label = labels[index]
            customItem(
                buttonGroupContent = {
                    RememberToggleButton(
                        checked = selectedTab == tab,
                        onCheckedChange = { checked -> if (checked) onTabSelected(tab) },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        enabled = enabled,
                        shapes = shapes[index],
                        colors = colors,
                    ) {
                        Text(label)
                    }
                },
                menuContent = { menuState ->
                    RememberDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onTabSelected(tab)
                            menuState.dismiss()
                        },
                        enabled = enabled,
                    )
                },
            )
        }
    }
}

@Composable
private fun StarredDoneButton(
    count: Int,
    onClick: () -> Unit,
) {
    RememberTextButton(onClick = onClick) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.common_save),
                modifier = Modifier.padding(end = 16.dp),
            )
            Box(
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Badge {
                    Text(count.toString())
                }
            }
        }
    }
}

@Composable
private fun IconPickerIconsContent(
    trimmedQuery: String,
    filteredOrdered: List<IconChoice>,
    defaultCatalogKey: String,
    selectionKey: String,
    starredIconKeys: List<String>,
    pendingStarredIconKeys: List<String>,
    starredSelectionActive: Boolean,
    symbolStyle: IconPickerSymbolStyle,
    selectedGridIndex: Int,
    onStartStarredSelection: () -> Unit,
    onToggleStarIcon: (String) -> Unit,
    onToggleSavedStarIcon: (String, String) -> Unit,
    onPick: (String?) -> Unit,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedGridIndex)
    val displayedStarredKeys =
        if (starredSelectionActive) {
            pendingStarredIconKeys
        } else {
            starredIconKeys
        }
    val normalizedStarredIconKeys =
        remember(starredIconKeys) {
            starredIconKeys.map { normalizeFavoriteIconKey(it) }.distinct()
        }
    val starredChoices = starredIconChoices(displayedStarredKeys)
    when {
        trimmedQuery.isEmpty() ->
            IconPickerGrid(
                modifier = Modifier.padding(horizontal = 20.dp),
                state = gridState,
            ) {
                iconStarredSection(
                    starredChoices = starredChoices,
                    defaultCatalogKey = defaultCatalogKey,
                    selectedKey = if (starredSelectionActive) null else selectionKey,
                    pendingStarredIconKeys = pendingStarredIconKeys,
                    starredSelectionActive = starredSelectionActive,
                    onStartStarredSelection = onStartStarredSelection,
                    onToggleStarIcon = onToggleStarIcon,
                    onToggleSavedStarIcon = onToggleSavedStarIcon,
                    onPick = onPick,
                )
                iconCatalog.forEach { category ->
                    if (category.nameRes == R.string.icon_section_brand_google) {
                        iconBrandDivider()
                    }
                    iconHeader(category.nameRes, topPadding = 8.dp)
                    // Keys must be unique in the whole grid: the same [IconChoice.key] can repeat
                    // across categories (e.g. airplane_ticket in Maps and Social).
                    itemsIndexed(
                        category.icons,
                        key = { index, _ -> "${category.nameRes}_$index" },
                    ) { _, catalogChoice ->
                        val choice = catalogChoice.withSymbolStyle(symbolStyle)
                        val pendingStarred = choice.key in pendingStarredIconKeys
                        IconTile(
                            choice = choice,
                            selected =
                                if (starredSelectionActive) {
                                    pendingStarred
                                } else {
                                    iconChoiceMatchesSelection(choice, selectionKey) ||
                                        (catalogChoice.key == defaultCatalogKey && selectionKey == defaultCatalogKey)
                                },
                            favorite = choice.key in normalizedStarredIconKeys,
                            onClick = {
                                if (starredSelectionActive) {
                                    onToggleStarIcon(choice.key)
                                } else {
                                    if (catalogChoice.key == defaultCatalogKey) {
                                        onPick(null)
                                    } else {
                                        onPick(choice.key)
                                    }
                                }
                            },
                            onLongClick = {
                                onToggleSavedStarIcon(choice.key, humanizeIconKey(choice.key))
                            },
                        )
                    }
                }
            }
        filteredOrdered.isEmpty() ->
            IconPickerGrid(modifier = Modifier.padding(horizontal = 20.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.icon_picker_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        else ->
            IconPickerGrid(modifier = Modifier.padding(horizontal = 20.dp)) {
                iconHeader(R.string.icon_picker_results_heading, topPadding = 4.dp)
                itemsIndexed(
                    filteredOrdered,
                    key = { index, choice -> "icon_picker_search_${index}_${choice.key}" },
                ) { _, catalogChoice ->
                    val choice = catalogChoice.withSymbolStyle(symbolStyle)
                    val pendingStarred = choice.key in pendingStarredIconKeys
                    IconTile(
                        choice = choice,
                        selected =
                            if (starredSelectionActive) {
                                pendingStarred
                            } else {
                                iconChoiceMatchesSelection(choice, selectionKey)
                            },
                        favorite = choice.key in normalizedStarredIconKeys,
                        onClick = {
                            if (starredSelectionActive) {
                                onToggleStarIcon(choice.key)
                            } else {
                                if (catalogChoice.key == defaultCatalogKey) {
                                    onPick(null)
                                } else {
                                    onPick(choice.key)
                                }
                            }
                        },
                        onLongClick = {
                            onToggleSavedStarIcon(choice.key, humanizeIconKey(choice.key))
                        },
                    )
                }
            }
    }
}

@Composable
private fun IconPickerEmojiContent(
    trimmedQuery: String,
    filteredEmojis: List<BundledEmoji>,
    emojiSkinToneIndex: EmojiSkinToneIndex,
    selectedCategoryKey: String,
    selectedEmoji: String?,
    starredEmojis: List<String>,
    pendingStarredEmojis: List<String>,
    starredSelectionActive: Boolean,
    onCategorySelected: (String) -> Unit,
    onStartStarredSelection: () -> Unit,
    onToggleStarEmoji: (String) -> Unit,
    onToggleSavedStarEmoji: (String, String) -> Unit,
    onEmojiSelected: (String) -> Unit,
) {
    val displayEmojis =
        remember(filteredEmojis, emojiSkinToneIndex) {
            collapseEmojiSkinToneVariants(filteredEmojis, emojiSkinToneIndex)
        }
    val categoryFadeSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (trimmedQuery.isEmpty()) {
            LazyRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = EMOJI_STARRED_CATEGORY_KEY) {
                    FilterChip(
                        selected = selectedCategoryKey == EMOJI_STARRED_CATEGORY_KEY,
                        onClick = {
                            onCategorySelected(EMOJI_STARRED_CATEGORY_KEY)
                        },
                        label = { Text(stringResource(R.string.icon_picker_starred)) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
                items(emojiPickerCategories, key = { it.key }) { category ->
                    FilterChip(
                        selected = category.key == selectedCategoryKey,
                        onClick = {
                            onCategorySelected(category.key)
                        },
                        label = { Text(stringResource(category.labelRes)) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
        }
        // Sub-tab transition is intentionally a Crossfade (not the heavier slide+
        // fade used by the main Icons<->Emojis tabs). Users hop between emoji
        // categories more often, and AnimatedContent keeping two LazyVerticalGrid
        // subcompositions alive at once was adding noticeable lag on phones.
        Crossfade(
            targetState = selectedCategoryKey,
            label = "iconPickerEmojiCategory",
            animationSpec = categoryFadeSpec,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) { categoryKey ->
            val showingStarredCategory = categoryKey == EMOJI_STARRED_CATEGORY_KEY && trimmedQuery.isEmpty()
            // Per-category grid state so scroll position from one category doesn't
            // leak into the next.
            val gridState =
                rememberLazyGridState(
                    initialFirstVisibleItemIndex =
                        if (trimmedQuery.isEmpty()) {
                            displayEmojis.indexOfFirst { entry -> entry.variants.any { it.emoji == selectedEmoji } }.coerceAtLeast(0)
                        } else {
                            0
                        },
                )
            if (displayEmojis.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    state = gridState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.icon_picker_emoji_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                    if (showingStarredCategory && !starredSelectionActive) {
                        item(key = "starred_emoji_edit") {
                            EditStarredTile(
                                onClick = onStartStarredSelection,
                                cellSize = 56.dp,
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    state = gridState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        displayEmojis,
                        key = { index, entry -> "emoji_${index}_${entry.emoji.key}" },
                    ) { _, entry ->
                        val variantValues = entry.variants.map { it.emoji }
                        val pendingStarred = variantValues.any { it in pendingStarredEmojis }
                        val selectedVariant = variantValues.firstOrNull { it == selectedEmoji }
                        val pendingVariant = variantValues.firstOrNull { it in pendingStarredEmojis }
                        val displayEmoji = selectedVariant ?: pendingVariant ?: entry.displayEmoji
                        EmojiTile(
                            emoji = displayEmoji,
                            variants = entry.variants,
                            selected =
                                if (starredSelectionActive) {
                                    pendingStarred
                                } else {
                                    selectedVariant != null
                                },
                            favorite = variantValues.any { it in starredEmojis },
                            onClick = { selectedEmojiValue ->
                                if (starredSelectionActive) {
                                    onToggleStarEmoji(selectedEmojiValue)
                                } else {
                                    onEmojiSelected(selectedEmojiValue)
                                }
                            },
                            onLongClick = { emojiValue, label ->
                                onToggleSavedStarEmoji(emojiValue, label)
                            },
                        )
                    }
                    if (showingStarredCategory && !starredSelectionActive) {
                        item(key = "starred_emoji_edit") {
                            EditStarredTile(
                                onClick = onStartStarredSelection,
                                cellSize = 56.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class IconPickerTab(
    @param:StringRes val labelRes: Int,
) {
    ICONS(R.string.icon_picker_tab_icons),
    EMOJIS(R.string.icon_picker_tab_emojis),
}

private enum class IconPickerSymbolStyle(
    val filled: Boolean,
) {
    FILLED(true),
    OUTLINED(false),
}

private fun IconPickerSymbolStyle.toggled(): IconPickerSymbolStyle =
    when (this) {
        IconPickerSymbolStyle.FILLED -> IconPickerSymbolStyle.OUTLINED
        IconPickerSymbolStyle.OUTLINED -> IconPickerSymbolStyle.FILLED
    }

private fun initialIconPickerSymbolStyle(iconKey: String): IconPickerSymbolStyle =
    if (resolvedSymbolFilled(iconKey) == false) {
        IconPickerSymbolStyle.OUTLINED
    } else {
        IconPickerSymbolStyle.FILLED
    }

private fun IconChoice.withSymbolStyle(symbolStyle: IconPickerSymbolStyle): IconChoice =
    if (symbolName == null) {
        this
    } else {
        copy(
            key = iconKeyWithSymbolFilled(key, symbolStyle.filled),
            filled = symbolStyle.filled,
        )
    }

private fun iconChoiceMatchesSelection(
    choice: IconChoice,
    selectionKey: String,
): Boolean {
    if (choice.key == selectionKey) return true
    val choiceCatalogKey = iconSymbolCatalogKey(choice.key) ?: return false
    val selectionCatalogKey = iconSymbolCatalogKey(selectionKey) ?: return false
    return choiceCatalogKey == selectionCatalogKey &&
        choice.filled == (resolvedSymbolFilled(selectionKey) ?: true)
}

private fun normalizeFavoriteIconKey(iconKey: String): String {
    val normalized = normalizeIconKey(iconKey) ?: iconKey
    return if (isSymbolIconKey(normalized)) {
        iconKeyWithSymbolFilled(normalized, resolvedSymbolFilled(normalized) ?: true)
    } else {
        normalized
    }
}

private fun defaultIconPickerTab(current: String?): IconPickerTab =
    if (current?.startsWith(ICON_EMOJI_PREFIX) == true) {
        IconPickerTab.EMOJIS
    } else {
        IconPickerTab.ICONS
    }

private fun selectedIconGridIndex(
    selectionKey: String,
    starredIconKeys: List<String>,
): Int {
    val starredChoices = starredIconChoices(starredIconKeys)
    if (starredChoices.any { it.key == selectionKey }) {
        return 0
    }
    val selectedCatalogKey = iconSymbolCatalogKey(selectionKey) ?: selectionKey
    var gridIndex = 1 + starredChoices.size + 1
    iconCatalog.forEach { category ->
        if (category.nameRes == R.string.icon_section_brand_google) {
            gridIndex += 1
        }
        if (category.icons.any { it.key == selectedCatalogKey }) {
            return gridIndex
        }
        gridIndex += 1 + category.icons.size
    }
    return 0
}

private fun selectedEmojiCategoryKey(
    emojis: List<BundledEmoji>,
    selectedEmoji: String?,
): String = emojis.firstOrNull { it.emoji == selectedEmoji }?.category ?: EmojiPickerCategory.SMILEYS_AND_PEOPLE.key

private const val EMOJI_STARRED_CATEGORY_KEY = "starred"

private fun LazyGridScope.iconStarredSection(
    starredChoices: List<IconChoice>,
    defaultCatalogKey: String,
    selectedKey: String?,
    pendingStarredIconKeys: List<String>,
    starredSelectionActive: Boolean,
    onStartStarredSelection: () -> Unit,
    onToggleStarIcon: (String) -> Unit,
    onToggleSavedStarIcon: (String, String) -> Unit,
    onPick: (String?) -> Unit,
) {
    iconHeader(R.string.icon_picker_starred, topPadding = 4.dp)
    itemsIndexed(
        starredChoices,
        key = { index, choice -> "starred_icon_${index}_${choice.key}" },
    ) { _, choice ->
        IconTile(
            choice = choice,
            selected =
                if (starredSelectionActive) {
                    choice.key in pendingStarredIconKeys
                } else {
                    choice.key == selectedKey
                },
            favorite = true,
            onClick = {
                if (starredSelectionActive) {
                    onToggleStarIcon(choice.key)
                } else if (choice.key == defaultCatalogKey) {
                    onPick(null)
                } else {
                    onPick(choice.key)
                }
            },
            onLongClick = {
                onToggleSavedStarIcon(choice.key, humanizeIconKey(choice.key))
            },
        )
    }
    if (!starredSelectionActive) {
        item(key = "starred_icon_add") {
            EditStarredTile(
                onClick = onStartStarredSelection,
                filled = false,
            )
        }
    }
}

private enum class EmojiPickerCategory(
    val key: String,
    @param:StringRes val labelRes: Int,
) {
    SMILEYS_AND_PEOPLE("smileys_and_people", R.string.icon_picker_emoji_category_smileys),
    ANIMALS_AND_NATURE("animals_and_nature", R.string.icon_picker_emoji_category_animals),
    FOOD_AND_DRINK("food_and_drink", R.string.icon_picker_emoji_category_food),
    ACTIVITY("activity", R.string.icon_picker_emoji_category_activity),
    TRAVEL_AND_PLACES("travel_and_places", R.string.icon_picker_emoji_category_travel),
    OBJECTS("objects", R.string.icon_picker_emoji_category_objects),
    SYMBOLS("symbols", R.string.icon_picker_emoji_category_symbols),
    FLAGS("flags", R.string.icon_picker_emoji_category_flags),
}

private val emojiPickerCategories = EmojiPickerCategory.entries.toList()

private fun starredIconChoices(starredIconKeys: List<String>): List<IconChoice> {
    val choicesByKey = linkedMapOf<String, IconChoice>()
    iconCatalog.forEach { category ->
        category.icons.forEach { choice ->
            choicesByKey.putIfAbsent(choice.key, choice)
        }
    }
    return starredIconKeys.mapNotNull { iconKey ->
        val normalized = normalizeFavoriteIconKey(iconKey)
        val catalogKey = iconSymbolCatalogKey(normalized) ?: normalized
        choicesByKey[catalogKey]?.let { choice ->
            if (isSymbolIconKey(normalized)) {
                choice.copy(
                    key = normalized,
                    filled = resolvedSymbolFilled(normalized) ?: choice.filled,
                )
            } else {
                choice
            }
        }
    }
}

private fun starredEmojiEntries(
    starredEmojis: List<String>,
    emojis: List<BundledEmoji>,
): List<BundledEmoji> {
    val emojisByValue = emojis.associateBy { it.emoji }
    return starredEmojis.mapNotNull { emojisByValue[it] }
}

@Serializable
private data class BundledEmoji(
    val key: String,
    val emoji: String,
    val name: String,
    val slug: String,
    val category: String,
    /** CLDR keywords (e.g. fire -> flame, hot, lit). Empty until the build_emoji_data.py script runs. */
    val keywords: List<String> = emptyList(),
)

private data class EmojiDisplayEntry(
    val emoji: BundledEmoji,
    val variants: List<BundledEmoji>,
    val displayEmoji: String,
)

private data class EmojiSkinToneGroupKey(
    val category: String,
    val baseName: String,
)

private data class EmojiSkinToneGroup(
    val base: BundledEmoji,
    val variants: List<BundledEmoji>,
)

private val emojiSkinToneLabels =
    listOf(
        "light skin tone",
        "medium-light skin tone",
        "medium skin tone",
        "medium-dark skin tone",
        "dark skin tone",
    )

/**
 * Pre-baked lookups for skin-tone collapse. Built once via [buildEmojiSkinToneIndex]
 * when the emoji list loads; per-tab collapse then runs in O(filteredEmojis).
 *
 * - [groups] - canonical group descriptor (base + sorted variants) per group key.
 * - [keyByEmoji] - direct emoji-string -> group-key lookup, so we never re-run the
 *   relatively expensive [skinToneBaseName] pass during a tab switch.
 */
private data class EmojiSkinToneIndex(
    val groups: Map<EmojiSkinToneGroupKey, EmojiSkinToneGroup>,
    val keyByEmoji: Map<String, EmojiSkinToneGroupKey>,
)

private fun buildEmojiSkinToneIndex(allEmojis: List<BundledEmoji>): EmojiSkinToneIndex {
    val groups = buildEmojiSkinToneGroups(allEmojis)
    val keyByEmoji = HashMap<String, EmojiSkinToneGroupKey>(allEmojis.size)
    for (emoji in allEmojis) {
        emojiSkinToneGroupKey(emoji, groups)?.let { groupKey ->
            keyByEmoji[emoji.emoji] = groupKey
        }
    }
    return EmojiSkinToneIndex(groups = groups, keyByEmoji = keyByEmoji)
}

/**
 * Collapse skin-tone variants in [sourceEmojis] using a precomputed [index].
 * Single linear pass: every source emoji is either a standalone entry, the
 * first appearance of a group (becomes the display tile), or a subsequent
 * appearance of an already-seen group (skipped).
 *
 * The previous implementation rebuilt the group map on every call AND scanned
 * `sourceEmojis` again to find each group's display emoji, which made sub-tab
 * switching feel sluggish on phones.
 */
private fun collapseEmojiSkinToneVariants(
    sourceEmojis: List<BundledEmoji>,
    index: EmojiSkinToneIndex,
): List<EmojiDisplayEntry> {
    val seenGroups = HashSet<EmojiSkinToneGroupKey>()
    val ordered = ArrayList<EmojiDisplayEntry>(sourceEmojis.size)
    for (sourceEmoji in sourceEmojis) {
        val groupKey = index.keyByEmoji[sourceEmoji.emoji]
        if (groupKey == null) {
            ordered +=
                EmojiDisplayEntry(
                    emoji = sourceEmoji,
                    variants = listOf(sourceEmoji),
                    displayEmoji = sourceEmoji.emoji,
                )
        } else if (seenGroups.add(groupKey)) {
            val skinToneGroup = index.groups[groupKey] ?: continue
            // First time this group surfaces in the filtered set, so the current
            // sourceEmoji is by definition the right display tile.
            ordered +=
                EmojiDisplayEntry(
                    emoji = skinToneGroup.base,
                    variants = skinToneGroup.variants,
                    displayEmoji = sourceEmoji.emoji,
                )
        }
    }
    return ordered
}

private fun buildEmojiSkinToneGroups(allEmojis: List<BundledEmoji>): Map<EmojiSkinToneGroupKey, EmojiSkinToneGroup> {
    val emojisByGroupKey =
        allEmojis.associateBy { bundledEmoji ->
            EmojiSkinToneGroupKey(
                category = bundledEmoji.category,
                baseName = bundledEmoji.name,
            )
        }
    val skinToneVariantsByGroupKey = linkedMapOf<EmojiSkinToneGroupKey, MutableList<BundledEmoji>>()
    allEmojis.forEach { bundledEmoji ->
        val baseName = skinToneBaseName(bundledEmoji.name) ?: return@forEach
        val groupKey =
            EmojiSkinToneGroupKey(
                category = bundledEmoji.category,
                baseName = baseName,
            )
        skinToneVariantsByGroupKey
            .getOrPut(groupKey) { mutableListOf() }
            .add(bundledEmoji)
    }
    return skinToneVariantsByGroupKey.mapValues { (groupKey, skinToneVariants) ->
        val baseEmoji = emojisByGroupKey[groupKey] ?: skinToneVariants.first()
        val variants =
            listOf(baseEmoji)
                .plus(
                    skinToneVariants.sortedBy { skinToneVariant ->
                        skinToneSortIndex(skinToneVariant.name)
                    },
                ).distinctBy { bundledEmoji -> bundledEmoji.emoji }
        EmojiSkinToneGroup(
            base = baseEmoji,
            variants = variants,
        )
    }
}

private fun emojiSkinToneGroupKey(
    emoji: BundledEmoji,
    skinToneGroups: Map<EmojiSkinToneGroupKey, EmojiSkinToneGroup>,
): EmojiSkinToneGroupKey? {
    val baseName = skinToneBaseName(emoji.name) ?: emoji.name
    val groupKey =
        EmojiSkinToneGroupKey(
            category = emoji.category,
            baseName = baseName,
        )
    return groupKey.takeIf { it in skinToneGroups }
}

private fun skinToneBaseName(name: String): String? {
    // Skip multi-tone emojis (couples/families like "couple kissing: woman, man,
    // dark skin tone, light skin tone") — those are intentionally kept distinct.
    // We detect multi-tone by counting label occurrences as ": label" or
    // ", label" tokens, which avoids the substring overlap that broke the old
    // `name.contains(label)` check (where "medium-light skin tone" would
    // simultaneously match "light skin tone").
    val occurrences =
        emojiSkinToneLabels.sumOf { label ->
            countNonOverlapping(name, ": $label") + countNonOverlapping(name, ", $label")
        }
    if (occurrences != 1) return null
    val matchingLabel =
        emojiSkinToneLabels
            .filter { label -> name.endsWith(": $label") || name.endsWith(", $label") }
            .maxByOrNull { it.length }
            ?: return null
    return when {
        name.endsWith(": $matchingLabel") -> name.removeSuffix(": $matchingLabel").trim()
        name.endsWith(", $matchingLabel") -> name.removeSuffix(", $matchingLabel").trim()
        else -> null
    }
}

private fun countNonOverlapping(
    haystack: String,
    needle: String,
): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var index = haystack.indexOf(needle)
    while (index != -1) {
        count++
        index = haystack.indexOf(needle, index + needle.length)
    }
    return count
}

private fun skinToneSortIndex(name: String): Int {
    // Same gotcha as skinToneBaseName: name.endsWith("light skin tone") is true for
    // "medium-light skin tone", which would put it at index 0 (light) instead of 1
    // (medium-light). Pick the longest-matching label so sort order matches the
    // skin-tone ramp in [emojiSkinToneLabels].
    val matchingIndex =
        emojiSkinToneLabels
            .withIndex()
            .filter { (_, label) -> name.endsWith(label) }
            .maxByOrNull { (_, label) -> label.length }
            ?.index
    return matchingIndex?.coerceAtLeast(0) ?: 0
}

private val emojiJson = Json { ignoreUnknownKeys = true }

/**
 * Loads the bundled emoji list. Generated by `python font_subset/build_emoji_data.py`
 * — until that runs the resource is an empty array and the picker simply shows
 * the empty state. See font_subset/AGENT_ADD_ICON.md.
 */
private fun loadBundledEmojis(resources: Resources): List<BundledEmoji> =
    resources
        .openRawResource(R.raw.emojis)
        .bufferedReader()
        .use { reader ->
            emojiJson.decodeFromString<List<BundledEmoji>>(reader.readText())
        }

/**
 * Loads supplemental Material Symbols tags keyed by ligature (e.g.
 * `self_improvement` -> [calm, meditate, mindfulness, yoga, zen]). Generated by
 * `python font_subset/build_icon_keywords.py`; missing/empty file is treated as
 * "no extra keywords known" and falls back to label/category text.
 */
private fun loadIconKeywords(resources: Resources): Map<String, List<String>> =
    resources
        .openRawResource(R.raw.icon_keywords)
        .bufferedReader()
        .use { reader ->
            emojiJson.decodeFromString<Map<String, List<String>>>(reader.readText())
        }

/**
 * Loads hand-curated search aliases keyed by icon ligature (e.g.
 * `sports_esports` -> [controller, gamepad, gaming]). Edited in
 * `app/src/main/res/raw/icon_aliases.json`; aliases carry the highest field
 * weight so they surface the icon even on partial queries.
 */
private fun loadIconAliases(resources: Resources): Map<String, List<String>> =
    resources
        .openRawResource(R.raw.icon_aliases)
        .bufferedReader()
        .use { reader ->
            emojiJson.decodeFromString<Map<String, List<String>>>(reader.readText())
        }

/**
 * Wait this long after the last keystroke before re-running the in-memory icon /
 * emoji filter. Matches the home search debounce so typing feel is consistent.
 */
private const val ICON_PICKER_SEARCH_DEBOUNCE_MILLIS = 300L

/**
 * Field weights for ranking. Names beat slugs beat tags beat category headings.
 * See [scoreSearchable] in IconPickerSearch.kt for how these compose into a final score.
 */
private const val FIELD_WEIGHT_NAME: Float = 3.0f
private const val FIELD_WEIGHT_SLUG: Float = 2.0f
private const val FIELD_WEIGHT_ALIAS: Float = 4.0f
private const val FIELD_WEIGHT_KEYWORDS: Float = 1.5f
private const val FIELD_WEIGHT_CATEGORY: Float = 0.8f

private fun iconChoicesRankedForSearch(
    resources: Resources,
    iconKeywords: Map<String, List<String>>,
    iconAliases: Map<String, List<String>>,
    rawQuery: String,
): List<IconChoice> {
    val tokens = tokenizeQuery(rawQuery)
    if (tokens.isEmpty()) return emptyList()
    val scored = ArrayList<Pair<IconChoice, Float>>(iconCatalog.sumOf { it.icons.size })
    iconCatalog.forEach { category ->
        val categoryLabel = resources.getString(category.nameRes)
        category.icons.forEach { choice ->
            val score = scoreSearchable(tokens, buildIconSearchFields(choice, categoryLabel, iconKeywords, iconAliases))
            if (score > 0f) scored += choice to score
        }
    }
    // Same persisted [IconChoice.key] can appear in multiple catalog categories; keep the
    // highest-scoring row and unique keys so LazyVerticalGrid does not throw on duplicates.
    return scored
        .sortedWith(
            compareByDescending<Pair<IconChoice, Float>> { it.second }
                .thenBy { entry -> humanizeIconKey(entry.first.key).length }
                .thenBy { entry -> humanizeIconKey(entry.first.key).lowercase(Locale.getDefault()) },
        ).distinctBy { it.first.key }
        .map { it.first }
}

private fun buildIconSearchFields(
    choice: IconChoice,
    categoryLabel: String,
    iconKeywords: Map<String, List<String>>,
    iconAliases: Map<String, List<String>>,
): List<SearchableField> {
    val tags = choice.symbolName?.let { iconKeywords[it] }.orEmpty()
    val aliases = choice.symbolName?.let { iconAliases[it] }.orEmpty()
    return listOf(
        SearchableField(text = humanizeIconKey(choice.key), weight = FIELD_WEIGHT_NAME),
        SearchableField(text = iconKeyToSearchWords(choice.key), weight = FIELD_WEIGHT_SLUG),
        SearchableField(text = aliases.joinToString(" "), weight = FIELD_WEIGHT_ALIAS),
        SearchableField(text = tags.joinToString(" "), weight = FIELD_WEIGHT_KEYWORDS, prefixMatchEnabled = false),
        SearchableField(text = categoryLabel, weight = FIELD_WEIGHT_CATEGORY, prefixMatchEnabled = false),
    )
}

private fun emojisRankedForSearch(
    emojis: List<BundledEmoji>,
    starredEmojis: List<String>,
    selectedCategoryKey: String,
    rawQuery: String,
): List<BundledEmoji> {
    val tokens = tokenizeQuery(rawQuery)
    if (tokens.isEmpty()) {
        return if (selectedCategoryKey == EMOJI_STARRED_CATEGORY_KEY) {
            starredEmojiEntries(starredEmojis, emojis)
        } else {
            emojis.filter { emoji -> emoji.category == selectedCategoryKey }
        }
    }
    return emojis
        .map { emoji -> emoji to scoreSearchable(tokens, buildEmojiSearchFields(emoji)) }
        .filter { it.second > 0f }
        .sortedWith(
            compareByDescending<Pair<BundledEmoji, Float>> { it.second }
                .thenBy { (emoji, _) -> emoji.name.length }
                .thenBy { (emoji, _) -> emoji.name.lowercase(Locale.getDefault()) },
        ).map { (emoji, _) -> emoji }
}

private fun buildEmojiSearchFields(emoji: BundledEmoji): List<SearchableField> {
    val categoryLabel = emoji.category.replace('_', ' ')
    return listOf(
        SearchableField(text = emoji.name, weight = FIELD_WEIGHT_NAME),
        SearchableField(text = emoji.slug.replace('_', ' '), weight = FIELD_WEIGHT_SLUG),
        SearchableField(text = emoji.keywords.joinToString(" "), weight = FIELD_WEIGHT_KEYWORDS, prefixMatchEnabled = false),
        SearchableField(text = categoryLabel, weight = FIELD_WEIGHT_CATEGORY, prefixMatchEnabled = false),
    )
}

private fun favoriteLabelForEmoji(emoji: BundledEmoji): String =
    emoji.name
        .split(' ')
        .joinToString(" ") { word ->
            word.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
            }
        }

private fun iconKeyToSearchWords(key: String): String {
    val raw =
        when {
            key.startsWith(ICON_SYMBOL_PREFIX) -> key.removePrefix(ICON_SYMBOL_PREFIX)
            key.startsWith(ICON_SYMBOL_FILLED_PREFIX) -> key.removePrefix(ICON_SYMBOL_FILLED_PREFIX)
            key.startsWith(ICON_SYMBOL_OUTLINED_PREFIX) -> key.removePrefix(ICON_SYMBOL_OUTLINED_PREFIX)
            key.startsWith(ICON_DRAWABLE_PREFIX) -> key.removePrefix(ICON_DRAWABLE_PREFIX)
            else -> key
        }
    return raw.replace('_', ' ').lowercase(Locale.getDefault())
}

@Composable
private fun IconPickerGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        modifier = modifier,
        state = state,
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private fun LazyGridScope.iconHeader(
    @StringRes labelRes: Int,
    topPadding: Dp,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding, bottom = 2.dp),
        )
    }
}

private fun LazyGridScope.iconBrandDivider() {
    item(key = "icon_brand_divider", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = stringResource(R.string.icon_picker_brand_separator),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/**
 * "Edit starred" affordance shared by both grids. 44 dp circle that matches
 * [IconTile]'s Surface + CircleShape + clip structure (so the border antialiases
 * cleanly).
 *
 * - Emoji grid passes [cellSize] = 56 dp to centre the circle inside the
 *   adaptive emoji cell, and keeps the default [filled] = true so the disc
 *   reads as distinct from the surrounding emoji glyphs.
 * - Icon grid uses [cellSize] = 44 dp (default) with [filled] = false: the
 *   icon tiles around it are filled circles, so a transparent outline-only
 *   button is the visual cue that this one is an action, not another
 *   selectable icon.
 */
@Composable
private fun EditStarredTile(
    onClick: () -> Unit,
    cellSize: Dp = 44.dp,
    filled: Boolean = true,
) {
    val contentDescription = stringResource(R.string.icon_picker_add_starred_cd)
    val containerColor =
        if (filled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        }
    Box(
        modifier = Modifier.size(cellSize),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier =
                Modifier
                    .size(cellSize)
                    .clip(CircleShape)
                    .tapSoundClickable(onClick = onClick)
                    .semantics { this.contentDescription = contentDescription },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "edit",
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun IconTile(
    choice: IconChoice,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val label = humanizeIconKey(choice.key)
    val pulseScale = remember { Animatable(1f) }
    var pulseKey by remember { mutableIntStateOf(0) }
    var pulseAdding by remember { mutableStateOf(true) }
    var favoriteBorderVisible by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite) {
        if (!favorite) {
            favoriteBorderVisible = false
        } else if (pulseKey == 0 || !pulseAdding) {
            favoriteBorderVisible = true
        }
    }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        val target = if (pulseAdding) 1.14f else 0.86f
        pulseScale.animateTo(target, tween(durationMillis = 90))
        pulseScale.animateTo(1f, tween(durationMillis = 170))
        if (pulseAdding) {
            favoriteBorderVisible = true
        }
    }
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val fg = MaterialTheme.colorScheme.primary
    val border =
        if (favoriteBorderVisible) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        }
    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .tapSoundCombinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        pulseAdding = !favorite
                        if (!favorite) {
                            favoriteBorderVisible = false
                        }
                        pulseKey += 1
                        onLongClick()
                    },
                ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val symbolName = choice.symbolName
            if (symbolName != null) {
                RememberMaterialRoundedSymbol(
                    name = symbolName,
                    size = 21.dp,
                    tint = fg,
                    weight = FontWeight.Medium,
                    filled = choice.filled,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                val drawableRes = choice.drawableRes!!
                Icon(
                    painterResource(drawableRes),
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Right-angle triangle in the bottom-right corner used as a "this tile has
 * variants" affordance, mirroring how Gboard marks skin-tone-capable emojis.
 */
private val variantCornerShape =
    GenericShape { size, _ ->
        moveTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

@Composable
private fun EmojiTile(
    emoji: String,
    variants: List<BundledEmoji>,
    selected: Boolean,
    favorite: Boolean,
    onClick: (String) -> Unit,
    onLongClick: (String, String) -> Unit,
) {
    val uniqueVariants = variants.distinctBy { it.emoji }
    val hasVariants = uniqueVariants.size > 1
    val displayVariant = uniqueVariants.firstOrNull { it.emoji == emoji } ?: uniqueVariants.first()
    var expanded by rememberSaveable(emoji) { mutableStateOf(false) }
    val pulseScale = remember { Animatable(1f) }
    var pulseKey by remember { mutableIntStateOf(0) }
    var pulseAdding by remember { mutableStateOf(true) }
    var favoriteBorderVisible by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite) {
        if (!favorite) {
            favoriteBorderVisible = false
        } else if (pulseKey == 0 || !pulseAdding) {
            favoriteBorderVisible = true
        }
    }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0) return@LaunchedEffect
        val target = if (pulseAdding) 1.14f else 0.86f
        pulseScale.animateTo(target, tween(durationMillis = 90))
        pulseScale.animateTo(1f, tween(durationMillis = 170))
        if (pulseAdding) {
            favoriteBorderVisible = true
        }
    }
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .tapSoundCombinedClickable(
                    onClick = {
                        if (hasVariants) {
                            expanded = true
                        } else {
                            onClick(displayVariant.emoji)
                        }
                    },
                    onLongClick = {
                        pulseAdding = !favorite
                        if (!favorite) {
                            favoriteBorderVisible = false
                        }
                        pulseKey += 1
                        onLongClick(displayVariant.emoji, favoriteLabelForEmoji(displayVariant))
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border =
                    if (favoriteBorderVisible) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    } else {
                        null
                    },
            ) {}
        } else if (favoriteBorderVisible) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            ) {}
        }
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
        )
        if (hasVariants) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = variantCornerShape,
                        ),
            )
        }
        // Compact horizontal variant popup, like Gboard's skin-tone strip,
        // instead of the wide vertical DropdownMenu it used to be.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uniqueVariants.forEach { variant ->
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .tapSoundCombinedClickable(
                                    onClick = {
                                        expanded = false
                                        onClick(variant.emoji)
                                    },
                                    onLongClick = {
                                        expanded = false
                                        onLongClick(variant.emoji, favoriteLabelForEmoji(variant))
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = variant.emoji,
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp),
                        )
                    }
                }
            }
        }
    }
}
