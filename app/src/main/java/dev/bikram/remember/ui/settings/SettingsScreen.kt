package dev.bikram.remember.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.backup.RememberBackupWork
import dev.bikram.remember.data.BackupPreferencesState
import dev.bikram.remember.data.InteractionState
import dev.bikram.remember.data.LockPrefs
import dev.bikram.remember.data.NoteSwipeAction
import dev.bikram.remember.ui.lock.PinSetupDialog
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import dev.bikram.remember.ui.components.AboutAuthorPhoto
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsRoute() {
    val context = LocalContext.current
    val app = context.applicationContext as RememberApp
    val container = app.container
    val scope = rememberCoroutineScope()

    val lockState by container.lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = LockPrefs.State(),
    )
    val themeState = LocalThemeState.current
    val interactionState by container.interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )

    val biometricAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var pinSetupOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val backupState by container.backupPrefs.state.collectAsStateWithLifecycle(
        initialValue = BackupPreferencesState(),
    )

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showBackupHelp by rememberSaveable { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                container.backupPrefs.setExportFolderUri(uri.toString())
                RememberBackupWork.updateSchedule(context, container.backupPrefs.snapshot())
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val exportedCount = container.backupIo.exportTo(uri)
            val message = when {
                exportedCount < 0 -> context.getString(R.string.toast_export_failed)
                else -> context.getString(R.string.toast_exported_notes, exportedCount)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val importMergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val count = container.backupIo.importFrom(uri, preserveIdsForNotes = false)
            Toast.makeText(context, context.getString(R.string.toast_imported_notes, count), Toast.LENGTH_SHORT).show()
        }
    }
    val importReplaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        pendingRestoreUri = uri
    }

    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val blurStyle = rememberProgressiveBlurStyle()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                colors = transparentLargeTopAppBarColors(),
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val blurMod = blurStyle?.applyToScrollableList() ?: Modifier
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = pillInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "appearance") {
                Column {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_section_appearance),
                    )
                    Spacer(Modifier.height(8.dp))
                    AppearanceSection(prefs = container.themePrefs, state = themeState)
                }
            }

            item(key = "touch") {
                Column {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Vibration,
                        title = stringResource(R.string.settings_touch_sound_section),
                    )
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            SettingsToggleRow(
                                icon = Icons.Filled.Vibration,
                                title = stringResource(R.string.settings_haptic_feedback),
                                subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                checked = interactionState.hapticFeedbackEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { container.interactionPrefs.setHapticFeedbackEnabled(enabled) }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.settings_notifications),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.settings_notifications_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = notificationsGranted,
                                        onCheckedChange = { wantEnabled ->
                                            when {
                                                wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                    ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.POST_NOTIFICATIONS,
                                                    ) != PackageManager.PERMISSION_GRANTED ->
                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                wantEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                                    context.startActivity(notificationsAppSettingsIntent(context))
                                                !wantEnabled ->
                                                    context.startActivity(notificationsAppSettingsIntent(context))
                                                else -> { }
                                            }
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (!notificationsGranted) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                                            context.startActivity(notificationsAppSettingsIntent(context))
                                        }
                                    } else {
                                        context.startActivity(notificationsAppSettingsIntent(context))
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "Bulletproof Reminders",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (isIgnoringBatteryOptimizations) "Battery optimizations are disabled. Reminders will fire precisely."
                                        else "Allow app to ignore battery optimizations to ensure reminders fire exactly on time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = isIgnoringBatteryOptimizations,
                                        onCheckedChange = { wantEnabled ->
                                            if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } else if (!wantEnabled && isIgnoringBatteryOptimizations) {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            }
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (!isIgnoringBatteryOptimizations) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            item(key = "swipe") {
                Column {
                    SettingsSectionHeader(
                        icon = Icons.Filled.SwipeLeft,
                        title = stringResource(R.string.settings_swipe_section),
                    )
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.settings_swipe_right),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                trailingContent = {
                                    NoteSwipeActionDropdown(
                                        current = interactionState.swipeStartToEnd,
                                        excluded = interactionState.swipeEndToStart,
                                        onSelect = { action ->
                                            scope.launch { container.interactionPrefs.setSwipeStartToEnd(action) }
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.settings_swipe_left),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                trailingContent = {
                                    NoteSwipeActionDropdown(
                                        current = interactionState.swipeEndToStart,
                                        excluded = interactionState.swipeStartToEnd,
                                        onSelect = { action ->
                                            scope.launch { container.interactionPrefs.setSwipeEndToStart(action) }
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    NoteSwipePreviewCard(
                        swipeStartToEnd = interactionState.swipeStartToEnd,
                        swipeEndToStart = interactionState.swipeEndToStart,
                    )
                }
            }

            item(key = "backup") {
                val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
                val folderLabel = backupState.exportFolderUri.takeIf { it.isNotBlank() }?.let { treeUriString ->
                    exportFolderDisplayLabel(context, treeUriString, internalStorageDisplayName)
                } ?: stringResource(R.string.settings_choose_export_folder)
                val exportFolderReady = backupState.exportFolderUri.isNotBlank()
                val autoExportSwitchEnabled = exportFolderReady || backupState.autoExportOnChange
                val scheduledExportSwitchEnabled = exportFolderReady || backupState.scheduledExportEnabled

                Column {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Save,
                        title = stringResource(R.string.settings_backup_section),
                    )
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ListItem(
                                headlineContent = {
                                    Text(folderLabel, style = MaterialTheme.typography.bodyLarge)
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.settings_export_folder_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                                        Icon(
                                            Icons.Filled.FolderOpen,
                                            contentDescription = stringResource(R.string.settings_choose_export_folder),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_include_media_in_backup),
                                subtitle = stringResource(R.string.settings_include_media_in_backup_hint),
                                checked = backupState.includeMediaInBackup,
                                switchEnabled = true,
                                onDisabledInteraction = null,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.backupPrefs.setIncludeMediaInBackup(enabled)
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_auto_export_on_change),
                                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                                checked = backupState.autoExportOnChange,
                                switchEnabled = autoExportSwitchEnabled,
                                onDisabledInteraction = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.backupPrefs.setAutoExportOnChange(enabled)
                                        RememberBackupWork.updateSchedule(context, container.backupPrefs.snapshot())
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderSettingsToggleItem(
                                title = stringResource(R.string.settings_scheduled_export),
                                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                                checked = backupState.scheduledExportEnabled,
                                switchEnabled = scheduledExportSwitchEnabled,
                                onDisabledInteraction = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.backupPrefs.setScheduledExportEnabled(enabled)
                                        RememberBackupWork.updateSchedule(context, container.backupPrefs.snapshot())
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            importMergeLauncher.launch(
                                                arrayOf("application/zip", "application/json"),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_import_rules))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            exportLauncher.launch(container.backupIo.suggestedBackupFileName())
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_export_now))
                                    }
                                }
                                val restoreShape = ButtonDefaults.outlinedShape
                                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                val restoreLabelColor = MaterialTheme.colorScheme.error
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .clip(restoreShape)
                                        .border(BorderStroke(1.dp, restoreOutline), restoreShape),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .clickable {
                                                importReplaceLauncher.launch(
                                                    arrayOf("application/zip", "application/json"),
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_restore_backup),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = restoreLabelColor,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                            .width(40.dp)
                                            .clickable { showBackupHelp = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Info,
                                            contentDescription = stringResource(R.string.settings_backup_help_icon_cd),
                                            modifier = Modifier.size(20.dp),
                                            tint = restoreLabelColor.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "security") {
                Column {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.settings_section_security),
                    )
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ToggleRow(
                                icon = Icons.Filled.Lock,
                                title = stringResource(R.string.settings_app_lock_title),
                                subtitle = if (lockState.enabled) {
                                    stringResource(R.string.settings_app_lock_enabled)
                                } else {
                                    stringResource(R.string.settings_app_lock_disabled)
                                },
                                checked = lockState.enabled,
                                onChange = { want ->
                                    if (want) pinSetupOpen = true
                                    else scope.launch { container.lockPrefs.disable() }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            ToggleRow(
                                icon = Icons.Filled.Fingerprint,
                                title = stringResource(R.string.settings_biometric_title),
                                subtitle = when {
                                    !biometricAvailable -> stringResource(R.string.settings_biometric_no_hardware)
                                    !lockState.enabled -> stringResource(R.string.settings_biometric_need_lock)
                                    lockState.biometric -> stringResource(R.string.settings_biometric_enabled)
                                    else -> stringResource(R.string.settings_biometric_disabled)
                                },
                                checked = lockState.biometric,
                                enabled = biometricAvailable && lockState.enabled,
                                onChange = { scope.launch { container.lockPrefs.setBiometric(it) } },
                            )
                        }
                    }
                }
            }

            item(key = "about") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_section_about),
                    )
                    Spacer(Modifier.height(8.dp))
                    AboutSettingsBlock()
                }
            }
        }
    }

    if (pinSetupOpen) {
        PinSetupDialog(
            onConfirm = { pin ->
                scope.launch {
                    container.lockPrefs.enable(pin)
                    pinSetupOpen = false
                }
            },
            onDismiss = { pinSetupOpen = false },
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        scope.launch {
                            val count = container.backupIo.restoreFullReplace(uri)
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_imported_notes, count),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_restore_go_ahead))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showBackupHelp) {
        AlertDialog(
            onDismissRequest = { showBackupHelp = false },
            title = { Text(stringResource(R.string.settings_backup_help_title)) },
            text = { Text(stringResource(R.string.settings_backup_help_body)) },
            confirmButton = {
                TextButton(onClick = { showBackupHelp = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

private fun notificationsAppSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

private fun exportFolderDisplayLabel(context: Context, uriString: String, internalStorageFallback: String): String {
    if (uriString.isBlank()) return ""
    val uri = Uri.parse(uriString)
    DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }?.let { return it }
    return runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val decoded = Uri.decode(treeId)
        val lastSlash = decoded.lastIndexOf('/')
        if (lastSlash >= 0) decoded.substring(lastSlash + 1) else decoded.substringAfterLast(':')
    }.getOrDefault(internalStorageFallback)
}

@Composable
private fun BackupFolderSettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    onDisabledInteraction: (() -> Unit)?,
    onCheckedChange: (Boolean) -> Unit,
) {
    val switchInteractive = switchEnabled || onDisabledInteraction != null
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { enabled ->
                    when {
                        switchEnabled -> onCheckedChange(enabled)
                        onDisabledInteraction != null && enabled -> onDisabledInteraction.invoke()
                        else -> { }
                    }
                },
                enabled = switchInteractive,
            )
        },
        modifier = Modifier.clickable {
            if (!switchEnabled) {
                onDisabledInteraction?.invoke()
            } else {
                onCheckedChange(!checked)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.55f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { modifier -> if (enabled) modifier.clickable { onChange(!checked) } else modifier }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
        )
    }
}

@Composable
private fun NoteSwipeActionDropdown(
    current: NoteSwipeAction,
    excluded: NoteSwipeAction,
    onSelect: (NoteSwipeAction) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text(noteSwipeActionLabel(current))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NoteSwipeAction.entries.filter { it != excluded }.forEach { action ->
                DropdownMenuItem(
                    text = { Text(noteSwipeActionLabel(action)) },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun noteSwipeActionLabel(action: NoteSwipeAction): String = stringResource(
    when (action) {
        NoteSwipeAction.OPEN -> R.string.swipe_action_open
        NoteSwipeAction.TRASH -> R.string.swipe_action_trash
        NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
        NoteSwipeAction.TOGGLE_PIN -> R.string.swipe_action_toggle_pin
    },
)

@Composable
private fun NoteSwipePreviewCard(
    swipeStartToEnd: NoteSwipeAction,
    swipeEndToStart: NoteSwipeAction,
) {
    val leftBg by animateColorAsState(
        targetValue = swipeStartToEnd.semanticSwipeBackground(),
        animationSpec = tween(300),
        label = "leftBg",
    )
    val rightBg by animateColorAsState(
        targetValue = swipeEndToStart.semanticSwipeBackground(),
        animationSpec = tween(300),
        label = "rightBg",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(leftBg)
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = swipeStartToEnd.previewIcon(),
                    contentDescription = null,
                    tint = swipeStartToEnd.semanticSwipeIconTint(),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    noteSwipeActionLabel(swipeStartToEnd),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeStartToEnd.semanticSwipeIconTint(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(rightBg)
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    noteSwipeActionLabel(swipeEndToStart),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeEndToStart.semanticSwipeIconTint(),
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = swipeEndToStart.previewIcon(),
                    contentDescription = null,
                    tint = swipeEndToStart.semanticSwipeIconTint(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.settings_swipe_preview_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun NoteSwipeAction.previewIcon(): ImageVector = when (this) {
    NoteSwipeAction.OPEN -> Icons.Filled.Edit
    NoteSwipeAction.TRASH -> Icons.Filled.Delete
    NoteSwipeAction.DUPLICATE -> Icons.Filled.ContentCopy
    NoteSwipeAction.TOGGLE_PIN -> Icons.Filled.PushPin
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutSettingsBlock() {
    val context = LocalContext.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val profileUrl = stringResource(R.string.about_author_github_profile_url)
    val copyAboutLink = remember(context) {
        { url: String ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
            Toast.makeText(context, context.getString(R.string.toast_about_link_copied), Toast.LENGTH_SHORT).show()
        }
    }
    val iconShape = RoundedCornerShape(percent = 25)
    val authorShape = RoundedCornerShape(16.dp)
    val aboutPillShape = RoundedCornerShape(50)

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.ONLY) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(iconShape),
                    )
                    Spacer(Modifier.width(20.dp))
                    AboutAuthorPhoto(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(authorShape)
                            .combinedClickable(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                                    }
                                },
                                onLongClick = { copyAboutLink(profileUrl) },
                            ),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.settings_byline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (BuildConfig.FLAVOR == "github") {
                        Surface(
                            shape = aboutPillShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .clip(aboutPillShape)
                                .combinedClickable(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(playStoreListingUrl)),
                                            )
                                        }
                                    },
                                    onLongClick = { copyAboutLink(playStoreListingUrl) },
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shop,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            val repoUrl = "https://github.com/$githubRepoForSourceLink"
                            Surface(
                                shape = aboutPillShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .clip(aboutPillShape)
                                    .combinedClickable(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                            }
                                        },
                                        onLongClick = { copyAboutLink(repoUrl) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = aboutPillShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(aboutPillShape)
                                .combinedClickable(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(playStoreListingUrl)),
                                            )
                                        }
                                    },
                                    onLongClick = { copyAboutLink(playStoreListingUrl) },
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shop,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
                            val repoUrl = "https://github.com/$githubRepoForSourceLink"
                            Surface(
                                shape = aboutPillShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .clip(aboutPillShape)
                                    .combinedClickable(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
                                            }
                                        },
                                        onLongClick = { copyAboutLink(repoUrl) },
                                    ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}