package dev.bikram.remember.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.R
import dev.bikram.remember.data.TagRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ThemeState
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.RememberTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SelectedNotesWidgetConfigActivity : ComponentActivity() {
    @Inject
    lateinit var themePrefs: ThemePrefs

    @Inject
    lateinit var tagRepository: TagRepository

    @Inject
    lateinit var notesWidgetUpdater: NotesWidgetUpdater

    private var targetAppWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var configState by mutableStateOf(SelectedNotesWidgetConfig())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        extractWidgetId(intent)
        if (targetAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        configState = SelectedNotesWidgetConfigStore.loadConfig(this, targetAppWidgetId)
        loadGlanceState(targetAppWidgetId)

        setContent {
            val themeState by themePrefs.state.collectAsStateWithLifecycle(initialValue = ThemeState())
            val availableTags by tagRepository
                .observeActiveTagSuggestions()
                .collectAsStateWithLifecycle(initialValue = emptyList())

            RememberTheme(themeState = themeState) {
                SelectedNotesConfigContent(
                    currentConfig = configState,
                    availableTags = availableTags,
                    onSelectOption = { filterType, tag ->
                        configState = SelectedNotesWidgetConfig(filterType = filterType, tag = tag)
                        handleOptionSelected(filterType, tag)
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractWidgetId(intent)
        if (targetAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            configState = SelectedNotesWidgetConfigStore.loadConfig(this, targetAppWidgetId)
            loadGlanceState(targetAppWidgetId)
        }
    }

    private fun glanceIdFor(widgetId: Int): GlanceId? =
        runCatching {
            GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)
        }.recoverCatching {
            val appWidgetIdClass = Class.forName("androidx.glance.appwidget.AppWidgetId")
            val constructor = appWidgetIdClass.getDeclaredConstructor(Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            constructor.newInstance(widgetId) as GlanceId
        }.getOrNull()

    private fun loadGlanceState(widgetId: Int) {
        val glanceId = glanceIdFor(widgetId) ?: return
        lifecycleScope.launch {
            val prefs =
                runCatching {
                    getAppWidgetState(this@SelectedNotesWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                }.getOrNull()
            val glanceFilterType = prefs?.get(KEY_SELECTED_NOTES_FILTER_TYPE)
            if (glanceFilterType != null) {
                val filterType =
                    runCatching {
                        SelectedNotesFilterType.valueOf(glanceFilterType)
                    }.getOrDefault(SelectedNotesFilterType.ALL)
                val tag = prefs[KEY_SELECTED_NOTES_TAG].orEmpty()
                configState = SelectedNotesWidgetConfig(filterType = filterType, tag = tag)
            }
        }
    }

    private fun extractWidgetId(intent: Intent?) {
        val widgetId =
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            targetAppWidgetId = widgetId
        }
    }

    private fun handleOptionSelected(
        filterType: SelectedNotesFilterType,
        tag: String = "",
    ) {
        val config = SelectedNotesWidgetConfig(filterType = filterType, tag = tag)
        SelectedNotesWidgetConfigStore.saveConfig(this, targetAppWidgetId, config)

        val glanceId = glanceIdFor(targetAppWidgetId)
        lifecycleScope.launch {
            if (glanceId != null) {
                runCatching {
                    updateAppWidgetState(this@SelectedNotesWidgetConfigActivity, glanceId) { prefs ->
                        prefs[KEY_SELECTED_NOTES_FILTER_TYPE] = filterType.name
                        prefs[KEY_SELECTED_NOTES_TAG] = tag
                    }
                    SelectedWidget().update(this@SelectedNotesWidgetConfigActivity, glanceId)
                }
            }
            runCatching {
                SelectedWidget().updateAll(this@SelectedNotesWidgetConfigActivity)
            }
            notesWidgetUpdater.refreshAll()

            val resultValue =
                Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetAppWidgetId)
                }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectedNotesConfigContent(
    currentConfig: SelectedNotesWidgetConfig,
    availableTags: List<String>,
    onSelectOption: (SelectedNotesFilterType, String) -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.widget_config_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            contentDescription = stringResource(R.string.common_cancel),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { contentPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
            }
            item {
                ConfigSelectionRow(
                    label = stringResource(R.string.widget_config_category_all),
                    iconName = "notes",
                    selected = currentConfig.filterType == SelectedNotesFilterType.ALL,
                    onClick = { onSelectOption(SelectedNotesFilterType.ALL, "") },
                )
            }
            item {
                ConfigSelectionRow(
                    label = stringResource(R.string.widget_config_category_starred),
                    iconName = "star",
                    selected = currentConfig.filterType == SelectedNotesFilterType.STARRED,
                    onClick = { onSelectOption(SelectedNotesFilterType.STARRED, "") },
                )
            }
            item {
                ConfigSelectionRow(
                    label = stringResource(R.string.widget_config_category_pinned),
                    iconName = "push_pin",
                    selected = currentConfig.filterType == SelectedNotesFilterType.PINNED,
                    onClick = { onSelectOption(SelectedNotesFilterType.PINNED, "") },
                )
            }
            if (availableTags.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.widget_config_section_tags),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp),
                    )
                }
                items(availableTags) { tag ->
                    val isTagSelected =
                        currentConfig.filterType == SelectedNotesFilterType.TAG &&
                            currentConfig.tag.equals(tag, ignoreCase = true)
                    ConfigSelectionRow(
                        label = tag,
                        iconName = "label",
                        selected = isTagSelected,
                        onClick = { onSelectOption(SelectedNotesFilterType.TAG, tag) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConfigSelectionRow(
    label: String,
    iconName: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 20.dp,
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                modifier = Modifier.weight(1f),
            )
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}
