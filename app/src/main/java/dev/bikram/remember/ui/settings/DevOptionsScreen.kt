package dev.bikram.remember.ui.settings

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.data.DevModePrefs
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.PersistableChecklistItem
import dev.bikram.remember.data.RememberReservedTags
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.di.DevOptionsDependenciesEntryPoint
import dev.bikram.remember.reminders.ReminderReceiver
import dev.bikram.remember.reminders.ReminderScheduler
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.nav.DevOptionsSharedBoundsKey
import dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope
import dev.bikram.remember.ui.nav.LocalSharedTransitionScope
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DbStats(
    val active: Int,
    val archived: Int,
    val trashed: Int,
    val fileSizeBytes: Long,
    val heroStoredCount: Int,
    val heroStoredBytes: Long,
    val heroLinkedCount: Int,
    val attachStoredCount: Int,
    val attachStoredBytes: Long,
    val attachLinkedCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DevOptionsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val deps =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                DevOptionsDependenciesEntryPoint::class.java,
            )
        }

    val devModePrefs = deps.devModePrefs()
    val onboardingPrefs = deps.onboardingPrefs()
    val updatePrefs = deps.updatePrefs()
    val noteRepository = deps.noteRepository()
    val notesWidgetUpdater = deps.notesWidgetUpdater()
    val themePrefs = deps.themePrefs()
    val viewOptionsPrefs = deps.viewOptionsPrefs()
    val interactionPrefs = deps.interactionPrefs()
    val reminderPrefs = deps.reminderPrefs()
    val quickCapturePrefs = deps.quickCapturePrefs()
    val lockPrefs = deps.lockPrefs()
    val backupPrefs = deps.backupPrefs()
    val rememberUpdateState = deps.rememberUpdateState()

    val scope = rememberCoroutineScope()
    val isEnabled by devModePrefs.isEnabled.collectAsStateWithLifecycle(initialValue = true)
    val hasMockNotesFlow = remember(noteRepository) {
        noteRepository.observeActive()
            .map { notes -> notes.any { RememberReservedTags.MOCK in it.note.tags } }
    }
    val hasMockNotes by hasMockNotesFlow.collectAsStateWithLifecycle(initialValue = false)

    var dbStats by remember { mutableStateOf<DbStats?>(null) }
    var showResetPrefsConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val activeNotes = noteRepository.observeActive().first()
            val archivedNotes = noteRepository.observeArchived().first()
            val trashedNotes = noteRepository.observeTrashed().first()
            val allNotes = activeNotes + archivedNotes + trashedNotes
            val dbFile = context.getDatabasePath("remember.db")

            val fpAuthority = "${context.packageName}.fileprovider"
            fun isInternal(uri: String) = runCatching { android.net.Uri.parse(uri).authority == fpAuthority }.getOrDefault(false)
            fun uriBytes(uri: String) = runCatching {
                context.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)

            val heroUris = allNotes.mapNotNull { it.note.pictureUri?.takeIf { u -> u.isNotBlank() } }
            val heroStored = heroUris.filter { isInternal(it) }
            val heroLinked = heroUris.filter { !isInternal(it) }

            val allAttachUris = allNotes.flatMap { it.attachments }.map { it.uri }
            val attachStored = allAttachUris.filter { isInternal(it) }
            val attachLinked = allAttachUris.filter { !isInternal(it) }

            dbStats = DbStats(
                active = activeNotes.size,
                archived = archivedNotes.size,
                trashed = trashedNotes.size,
                fileSizeBytes = dbFile.length(),
                heroStoredCount = heroStored.size,
                heroStoredBytes = heroStored.sumOf { uriBytes(it) },
                heroLinkedCount = heroLinked.size,
                attachStoredCount = attachStored.size,
                attachStoredBytes = attachStored.sumOf { uriBytes(it) },
                attachLinkedCount = attachLinked.size,
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = 0.dp)
    val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val sharedModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = DevOptionsSharedBoundsKey),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }

    if (showResetPrefsConfirm) {
        AlertDialog(
            onDismissRequest = { showResetPrefsConfirm = false },
            title = { Text(stringResource(R.string.dev_options_reset_all_title)) },
            text = { Text(stringResource(R.string.dev_options_reset_all_message)) },
            confirmButton = {
                RememberTextButton(
                    onClick = {
                        scope.launch {
                            themePrefs.reset()
                            viewOptionsPrefs.reset()
                            interactionPrefs.reset()
                            reminderPrefs.reset()
                            quickCapturePrefs.reset()
                            lockPrefs.reset()
                            backupPrefs.reset()
                            updatePrefs.reset()
                            onboardingPrefs.resetIntroSeen()
                        }
                        showResetPrefsConfirm = false
                        Toast.makeText(context, context.getString(R.string.dev_options_toast_reset_done), Toast.LENGTH_SHORT).show()
                    },
                ) { Text(stringResource(R.string.dev_options_reset_all_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                RememberTextButton(onClick = { showResetPrefsConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier =
            sharedModifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            MediumTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = { Text(stringResource(R.string.dev_options_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    RememberIconButton(
                        onClick = onBack,
                        tooltipLabel = stringResource(R.string.dev_options_back_cd),
                    ) {
                        RememberMaterialRoundedSymbol(name = "arrow_back", size = 22.dp)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.then(blurMod),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Dev mode toggle
            item(key = "dev_mode_toggle") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.ONLY) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dev_options_mode_label), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.dev_options_mode_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { devModePrefs.setEnabled(enabled) }
                                    if (!enabled) onBack()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Warning banner
            item(key = "warning") {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "warning",
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.dev_options_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Data population
            item(key = "data_header") {
                SettingsStaticSectionHeader(materialSymbolName = "data_object", title = stringResource(R.string.dev_options_section_data))
                Spacer(Modifier.height(8.dp))
            }
            item(key = "data_content") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        DevActionRow(
                            label = if (hasMockNotes) stringResource(R.string.dev_options_mock_notes_already_exist) else stringResource(R.string.dev_options_create_mock_notes),
                            enabled = !hasMockNotes,
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val count = createMockNotes(context, noteRepository)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.dev_options_toast_mock_created, count), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_clear_mock_data),
                            enabled = hasMockNotes,
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    deleteMockNotes(noteRepository)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.dev_options_toast_mock_cleared), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Notification testing
            item(key = "notif_header") {
                SettingsStaticSectionHeader(materialSymbolName = "notifications", title = stringResource(R.string.dev_options_section_notifications))
                Spacer(Modifier.height(8.dp))
            }
            item(key = "notif_content") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        NotificationButtonGrid(context = context, noteRepository = noteRepository, scope = scope)
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_notif_overdue),
                            onClick = { fireNotifOverdue(context, noteRepository, reminderPrefs, scope) },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_notif_mock_update),
                            onClick = { rememberUpdateState.devReleaseMockArmUpdatePromoBanner(); onBack() },
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_notif_mock_play),
                            onClick = { rememberUpdateState.devReleaseMockStartPlayUpdateBannerSequence(); onBack() },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Settings
            item(key = "settings_header") {
                SettingsStaticSectionHeader(materialSymbolName = "settings", title = stringResource(R.string.dev_options_section_settings))
                Spacer(Modifier.height(8.dp))
            }
            item(key = "settings_content") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_open_notif_settings),
                            onClick = { context.startActivity(notifSettingsIntent(context)) },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_open_alarm_settings),
                            onClick = { context.startActivity(alarmsAndRemindersIntent(context)) },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_open_battery_settings),
                            onClick = { context.startActivity(batteryIntent()) },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_reset_skipped_update),
                            onClick = {
                                scope.launch { updatePrefs.clearGithubReleaseAck() }
                                Toast.makeText(context, context.getString(R.string.dev_options_toast_skipped_cleared), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_reset_first_launch),
                            onClick = {
                                scope.launch { onboardingPrefs.resetIntroSeen() }
                                Toast.makeText(context, context.getString(R.string.dev_options_toast_first_launch_reset), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Database inspection
            item(key = "db_header") {
                SettingsStaticSectionHeader(materialSymbolName = "database", title = stringResource(R.string.dev_options_section_db))
                Spacer(Modifier.height(8.dp))
            }
            item(key = "db_content") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.ONLY) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DbStatRow(stringResource(R.string.dev_options_db_stat_version), stringResource(R.string.dev_options_db_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                            DbStatRow(stringResource(R.string.dev_options_db_stat_build), stringResource(R.string.dev_options_db_build_format, BuildConfig.BUILD_TYPE, BuildConfig.FLAVOR))
                            DbStatRow(stringResource(R.string.dev_options_db_stat_room_schema), stringResource(R.string.dev_options_db_stat_room_version))
                            val stats = dbStats
                            if (stats != null) {
                                DbStatRow(stringResource(R.string.dev_options_db_stat_active_notes), stats.active.toString())
                                DbStatRow(stringResource(R.string.dev_options_db_stat_archived), stats.archived.toString())
                                DbStatRow(stringResource(R.string.dev_options_db_stat_trash), stats.trashed.toString())
                                val heroValue = if (stats.heroStoredCount > 0) stringResource(R.string.dev_options_db_stat_count_with_size, stats.heroStoredCount, formatBytes(stats.heroStoredBytes)) else stats.heroStoredCount.toString()
                                DbStatRow(stringResource(R.string.dev_options_db_stat_images_stored), heroValue)
                                if (stats.heroLinkedCount > 0) DbStatRow(stringResource(R.string.dev_options_db_stat_images_linked), stats.heroLinkedCount.toString())
                                val attachValue = if (stats.attachStoredCount > 0) stringResource(R.string.dev_options_db_stat_count_with_size, stats.attachStoredCount, formatBytes(stats.attachStoredBytes)) else stats.attachStoredCount.toString()
                                DbStatRow(stringResource(R.string.dev_options_db_stat_attachments_stored), attachValue)
                                if (stats.attachLinkedCount > 0) DbStatRow(stringResource(R.string.dev_options_db_stat_attachments_linked), stats.attachLinkedCount.toString())
                                DbStatRow(stringResource(R.string.dev_options_db_stat_file_size), formatBytes(stats.fileSizeBytes))
                            } else {
                                DbStatRow(stringResource(R.string.dev_options_db_stat_active_notes), stringResource(R.string.dev_options_db_stat_loading))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Diagnostic tools
            item(key = "diag_header") {
                SettingsStaticSectionHeader(materialSymbolName = "bug_report", title = stringResource(R.string.dev_options_section_diagnostics))
                Spacer(Modifier.height(8.dp))
            }
            item(key = "diag_content") {
                GroupedListColumn(modifier = Modifier.fillMaxWidth()) {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_force_refresh),
                            onClick = {
                                scope.launch { notesWidgetUpdater.refreshAll() }
                                Toast.makeText(context, context.getString(R.string.dev_options_toast_refresh), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_force_crash),
                            onClick = { Handler(Looper.getMainLooper()).post { throw RuntimeException("Developer-triggered crash") } },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_copy_diagnostics),
                            onClick = {
                                DiagnosticLog.record(context, "Diagnostic dump copied from Developer options")
                                val file = DiagnosticLog.createShareFile(context)
                                val text = runCatching { file.readText() }.getOrElse { "Failed to read diagnostic log" }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics", text))
                                Toast.makeText(context, context.getString(R.string.dev_options_toast_diagnostics_copied), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_export_logs),
                            onClick = {
                                DiagnosticLog.record(context, "Diagnostic log exported from Developer options")
                                val diagnosticsFile = DiagnosticLog.createShareFile(context)
                                val diagnosticsUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", diagnosticsFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    setDataAndType(diagnosticsUri, "text/plain")
                                    putExtra(Intent.EXTRA_STREAM, diagnosticsUri)
                                    putExtra(Intent.EXTRA_TITLE, diagnosticsFile.name)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    clipData = ClipData.newUri(context.contentResolver, diagnosticsFile.name, diagnosticsUri)
                                }
                                runCatching { context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.dev_options_export_logs_chooser))) }
                            },
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        DevActionRow(
                            label = stringResource(R.string.dev_options_clear_logs),
                            onClick = {
                                java.io.File(context.filesDir, "diagnostics/remember-diagnostics.log").delete()
                                Toast.makeText(context, context.getString(R.string.dev_options_toast_logs_cleared), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Reset all — danger zone
            item(key = "reset_all") {
                Button(
                    onClick = { showResetPrefsConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.dev_options_reset_all), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun NotificationButtonGrid(
    context: Context,
    noteRepository: NoteRepository,
    scope: CoroutineScope,
) {
    val labels =
        listOf(
            stringResource(R.string.dev_options_notif_low) to { fireNotifWithNote(context, noteRepository, scope, context.getString(R.string.dev_options_test_notif_low_title), Importance.LOW) },
            stringResource(R.string.dev_options_notif_default) to { fireNotifWithNote(context, noteRepository, scope, context.getString(R.string.dev_options_test_notif_default_title), Importance.DEFAULT) },
            stringResource(R.string.dev_options_notif_high) to { fireNotifWithNote(context, noteRepository, scope, context.getString(R.string.dev_options_test_notif_high_title), Importance.HIGH) },
            stringResource(R.string.dev_options_notif_big_picture) to { fireNotifBigPicture(context, noteRepository, scope) },
            stringResource(R.string.dev_options_notif_big_text) to { fireNotifBigText(context, noteRepository, scope) },
            stringResource(R.string.dev_options_notif_summary) to { fireTestSummary(context) },
        )
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { (label, action) ->
                    RememberOutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DevActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    androidx.compose.material3.ListItem(
        headlineContent = {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        modifier = if (enabled) Modifier.tapSoundClickable(onClick = onClick) else Modifier,
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun DbStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------------------------------------------------------------------
// Notification helpers — real notes so Snooze / Done buttons work
// ---------------------------------------------------------------------------

private fun fireNotifWithNote(
    context: Context,
    noteRepository: NoteRepository,
    scope: CoroutineScope,
    title: String,
    importance: Importance,
) {
    scope.launch(Dispatchers.IO) {
        val noteId = noteRepository.createNote(
            title = title,
            body = context.getString(R.string.dev_options_test_notif_body),
            colorIndex = 0,
            options = NoteOptions(importance = importance, tags = listOf(RememberReservedTags.MOCK)),
        )
        val noteWithItems = noteRepository.get(noteId) ?: return@launch
        ReminderReceiver.showNotification(context, noteWithItems.note, noteWithItems.items, keepUntilDone = false)
    }
}

private fun fireNotifBigText(
    context: Context,
    noteRepository: NoteRepository,
    scope: CoroutineScope,
) {
    scope.launch(Dispatchers.IO) {
        val noteId = noteRepository.createList(
            title = context.getString(R.string.dev_options_test_notif_big_text_title),
            colorIndex = 1,
            items = listOf(
                context.getString(R.string.dev_options_mock_checklist_item_milk),
                context.getString(R.string.dev_options_mock_checklist_item_eggs),
                context.getString(R.string.dev_options_mock_checklist_item_bread),
                context.getString(R.string.dev_options_mock_checklist_item_report),
                context.getString(R.string.dev_options_mock_checklist_item_pr),
                context.getString(R.string.dev_options_mock_checklist_item_dentist),
                context.getString(R.string.dev_options_mock_checklist_item_work),
            ),
            options = NoteOptions(tags = listOf(RememberReservedTags.MOCK)),
        )
        val noteWithItems = noteRepository.get(noteId) ?: return@launch
        ReminderReceiver.showNotification(context, noteWithItems.note, noteWithItems.items, keepUntilDone = false)
    }
}

private fun fireNotifBigPicture(
    context: Context,
    noteRepository: NoteRepository,
    scope: CoroutineScope,
) {
    scope.launch(Dispatchers.IO) {
        val heroUri = createMockHeroImageUri(context)
        val noteId = noteRepository.createNote(
            title = context.getString(R.string.dev_options_test_notif_big_picture_title),
            body = context.getString(R.string.dev_options_test_notif_big_picture_body),
            colorIndex = 2,
            options = NoteOptions(pictureUri = heroUri, tags = listOf(RememberReservedTags.MOCK)),
        )
        val noteWithItems = noteRepository.get(noteId) ?: return@launch
        ReminderReceiver.showNotification(context, noteWithItems.note, noteWithItems.items, keepUntilDone = false)
    }
}

private fun fireNotifOverdue(
    context: Context,
    noteRepository: NoteRepository,
    reminderPrefs: dev.bikram.remember.data.ReminderPrefs,
    scope: CoroutineScope,
) {
    scope.launch(Dispatchers.IO) {
        // ReminderDismissReceiver requires: (1) keepReminderNotificationsUntilDone pref = true,
        // (2) note.reminderAt set and in the past. Both must hold for re-posting to work.
        reminderPrefs.setKeepReminderNotificationsUntilDone(true)
        val noteId = noteRepository.createNote(
            title = context.getString(R.string.dev_options_test_notif_overdue_title),
            body = context.getString(R.string.dev_options_test_notif_overdue_body),
            colorIndex = 3,
            options = NoteOptions(
                importance = Importance.HIGH,
                reminderAt = System.currentTimeMillis() - 60_000L,
                tags = listOf(RememberReservedTags.MOCK),
            ),
        )
        val noteWithItems = noteRepository.get(noteId) ?: return@launch
        ReminderReceiver.showNotification(context, noteWithItems.note, noteWithItems.items, keepUntilDone = true)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.dev_options_toast_keep_until_done_enabled), Toast.LENGTH_LONG).show()
        }
    }
}

private fun fireTestSummary(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val n = androidx.core.app.NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID_SUMMARY)
        .setSmallIcon(R.drawable.ic_stat_remember)
        .setContentTitle(context.getString(R.string.dev_options_test_notif_summary_title))
        .setContentText(context.getString(R.string.dev_options_test_notif_summary_text))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
        .setStyle(
            androidx.core.app.NotificationCompat.InboxStyle()
                .addLine(context.getString(R.string.dev_options_test_notif_summary_line1))
                .addLine(context.getString(R.string.dev_options_test_notif_summary_line2))
                .addLine(context.getString(R.string.dev_options_test_notif_summary_line3))
                .setBigContentTitle(context.getString(R.string.dev_options_test_notif_summary_title)),
        )
        .setOngoing(true)
        .setAutoCancel(false)
        .build()
    nm.notify(ReminderScheduler.SUMMARY_NOTIFICATION_ID, n)
}

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

private suspend fun createMockNotes(context: Context, noteRepository: NoteRepository): Int {
    val now = System.currentTimeMillis()
    val mockTag = RememberReservedTags.MOCK

    val checklistTags = listOf(mockTag, context.getString(R.string.dev_options_mock_checklist_tag1))
    val listId = noteRepository.createList(
        title = context.getString(R.string.dev_options_mock_checklist_title),
        colorIndex = 2,
        items = emptyList(),
        options = NoteOptions(tags = checklistTags, reminderAt = now + 3 * 3_600_000L),
    )
    noteRepository.setStarred(listId, true)
    noteRepository.updateList(
        id = listId,
        title = context.getString(R.string.dev_options_mock_checklist_title),
        colorIndex = 2,
        items = listOf(
            PersistableChecklistItem(-1, context.getString(R.string.dev_options_mock_checklist_item_groceries), false, 1.0),
            PersistableChecklistItem(-2, context.getString(R.string.dev_options_mock_checklist_item_milk), true, 1.1, -1, 1),
            PersistableChecklistItem(-3, context.getString(R.string.dev_options_mock_checklist_item_eggs), false, 1.2, -1, 1),
            PersistableChecklistItem(-4, context.getString(R.string.dev_options_mock_checklist_item_bread), true, 1.3, -1, 1),
            PersistableChecklistItem(-5, context.getString(R.string.dev_options_mock_checklist_item_work), false, 2.0),
            PersistableChecklistItem(-6, context.getString(R.string.dev_options_mock_checklist_item_report), true, 2.1, -5, 1),
            PersistableChecklistItem(-7, context.getString(R.string.dev_options_mock_checklist_item_pr), false, 2.2, -5, 1),
            PersistableChecklistItem(-8, context.getString(R.string.dev_options_mock_checklist_item_dentist), false, 3.0),
        ),
        options = NoteOptions(tags = checklistTags, reminderAt = now + 3 * 3_600_000L),
    )

    val heroUri = createMockHeroImageUri(context)
    noteRepository.createNote(
        title = context.getString(R.string.dev_options_mock_note_title),
        body = context.getString(R.string.dev_options_mock_note_body),
        colorIndex = 4,
        options = NoteOptions(tags = listOf(mockTag), pictureUri = heroUri),
    )

    return 2
}

private suspend fun deleteMockNotes(noteRepository: NoteRepository) {
    val allNotes =
        noteRepository.observeActive().first() +
            noteRepository.observeArchived().first() +
            noteRepository.observeTrashed().first()
    val mockIds =
        allNotes
            .filter { RememberReservedTags.MOCK in it.note.tags }
            .map { it.note.id }
            .toSet()
    if (mockIds.isNotEmpty()) {
        noteRepository.deleteForever(mockIds)
    }
}

private fun createMockHeroImageUri(context: Context): String? =
    runCatching {
        val w = 800
        val h = 450
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val gradient = android.graphics.LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                android.graphics.Color.rgb(99, 102, 241),
                android.graphics.Color.rgb(168, 85, 247),
                android.graphics.Color.rgb(236, 72, 153),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        val paint = android.graphics.Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val file = java.io.File(context.cacheDir, "dev_mock/hero.jpg")
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }.getOrNull()

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }

private fun notifSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

private fun alarmsAndRemindersIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .apply { data = "package:${context.packageName}".toUri(); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

private fun batteryIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
