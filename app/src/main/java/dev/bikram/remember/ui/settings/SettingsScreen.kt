package dev.bikram.remember.ui.settings
import androidx.compose.material3.TextButton

import android.app.AlarmManager
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
import dev.bikram.remember.data.QuickCaptureState
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.SwipeGestureMode
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.PillBottomScrimExtra
import dev.bikram.remember.ui.modifiers.applyToScrollableList
import dev.bikram.remember.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import dev.bikram.remember.ui.theme.LocalThemeState
import dev.bikram.remember.ui.theme.semanticSwipeBackground
import dev.bikram.remember.ui.theme.semanticSwipeIconTint
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.transparentLargeTopAppBarColors
import dev.bikram.remember.ui.components.AboutAuthorPhoto
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.feedback.rememberPlayTapSound

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsRoute(
    onOpenIntro: () -> Unit = {},
) {
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
    val quickCaptureState by container.quickCapturePrefs.state.collectAsStateWithLifecycle(
        initialValue = QuickCaptureState(),
    )
    val reminderState by container.reminderPrefs.state.collectAsStateWithLifecycle(
        initialValue = ReminderPreferencesState(),
    )

    val biometricAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    val deviceCredentialAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

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
    var collapsedSettingsSectionKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val settingsListState = rememberLazyListState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val permissionLinked = remember { isPermissionLinked() }
    var canScheduleExactAlarms by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
        )
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, alarmManager, powerManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                canScheduleExactAlarms =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val settingsScrollEnabled = rememberContentOverflowScrollEnabled(
        listState = settingsListState,
        additionalScrollEnabled = topBarState.collapsedFraction > 0f,
    )
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
        val blurMod = remember(blurStyle) { blurStyle?.applyToScrollableList() ?: Modifier }
        val topInset = padding.calculateTopPadding() + 8.dp
        val bottomPadding = pillInset + 24.dp
        val listContentPadding = remember(topInset, bottomPadding) {
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topInset,
                bottom = bottomPadding,
            )
        }
        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .fillMaxSize()
                .then(blurMod),
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            userScrollEnabled = settingsScrollEnabled,
        ) {
            item(key = "appearance") {
                SettingsExpandableSection(
                    sectionKey = "appearance",
                    materialSymbolName = "palette",
                    title = stringResource(R.string.settings_section_appearance),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    AppearanceSection(prefs = container.themePrefs, state = themeState)
                }
            }

            item(key = "notifications") {
                SettingsExpandableSection(
                    sectionKey = "notifications",
                    materialSymbolName = "notifications",
                    title = stringResource(R.string.settings_notifications_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
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
                                    RememberMaterialRoundedSymbol(
                                        name = "notifications",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        weight = FontWeight.Medium,
                                    )
                                },
                                trailingContent = {
                                    RememberSwitch(
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
                                modifier = Modifier.tapSoundClickable {
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
                        if (permissionLinked) {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "timer",
                                    title = stringResource(R.string.settings_reliable_reminders),
                                    subtitle = stringResource(R.string.settings_reliable_reminders_desc),
                                    checked = canScheduleExactAlarms && isIgnoringBatteryOptimizations,
                                    onCheckedChange = { wantEnabled ->
                                        if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                            val intent =
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        } else {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "timer",
                                    title = stringResource(R.string.settings_reliable_reminders),
                                    subtitle = stringResource(R.string.settings_reliable_reminders_exact_desc),
                                    checked = canScheduleExactAlarms,
                                    onCheckedChange = {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    },
                                )
                            }
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleRow(
                                    materialSymbolName = "battery_full",
                                    title = stringResource(R.string.settings_run_in_background),
                                    subtitle = stringResource(R.string.settings_run_in_background_desc),
                                    checked = isIgnoringBatteryOptimizations,
                                    onCheckedChange = { wantEnabled ->
                                        if (wantEnabled && !isIgnoringBatteryOptimizations) {
                                            val intent =
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                            context.startActivity(intent)
                                        } else {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(intent)
                                        }
                                    },
                                )
                            }
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleRow(
                                materialSymbolName = "notification_important",
                                title = stringResource(R.string.settings_keep_reminders_until_done),
                                subtitle = stringResource(R.string.settings_keep_reminders_until_done_desc),
                                checked = reminderState.keepReminderNotificationsUntilDone,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.reminderPrefs.setKeepReminderNotificationsUntilDone(enabled)
                                        container.noteRepository.refreshActiveReminderNotifications()
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleRow(
                                materialSymbolName = "format_list_bulleted",
                                title = stringResource(R.string.settings_reminder_summary_notification),
                                subtitle = stringResource(R.string.settings_reminder_summary_notification_desc),
                                checked = reminderState.reminderSummaryNotificationEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        container.reminderPrefs.setReminderSummaryNotificationEnabled(enabled)
                                        container.noteRepository.refreshReminderSummaryNotification()
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            SettingsToggleRow(
                                materialSymbolName = "bolt",
                                title = stringResource(R.string.settings_quick_capture_title),
                                subtitle = stringResource(R.string.settings_quick_capture_subtitle),
                                checked = quickCaptureState.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { container.quickCapturePrefs.setEnabled(enabled) }
                                },
                            )
                        }
                    }
                }
            }

            item(key = "swipe") {
                SettingsExpandableSection(
                    sectionKey = "swipe",
                    materialSymbolName = "swipe_left",
                    title = stringResource(R.string.settings_swipe_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.settings_swipe_gesture),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                trailingContent = {
                                    SwipeGestureModeDropdown(
                                        current = interactionState.swipeGestureMode,
                                        onSelect = { mode ->
                                            scope.launch { container.interactionPrefs.setSwipeGestureMode(mode) }
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                        if (interactionState.swipeGestureMode == SwipeGestureMode.EXECUTE_ONE) {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
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
                        } else {
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SwipeRevealSlotsRow(
                                    title = stringResource(R.string.settings_swipe_right_actions),
                                    actions = interactionState.swipeStartToEndRevealActions,
                                    onActionsChange = { actions ->
                                        scope.launch { container.interactionPrefs.setSwipeStartToEndRevealActions(actions) }
                                    },
                                )
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                SwipeRevealSlotsRow(
                                    title = stringResource(R.string.settings_swipe_left_actions),
                                    actions = interactionState.swipeEndToStartRevealActions,
                                    onActionsChange = { actions ->
                                        scope.launch { container.interactionPrefs.setSwipeEndToStartRevealActions(actions) }
                                    },
                                )
                            }
                        }
                    }
                    if (interactionState.swipeGestureMode == SwipeGestureMode.EXECUTE_ONE) {
                        Spacer(Modifier.height(8.dp))
                        NoteSwipePreviewCard(
                            swipeStartToEnd = interactionState.swipeStartToEnd,
                            swipeEndToStart = interactionState.swipeEndToStart,
                        )
                    }
                }
            }

            item(key = "haptics") {
                SettingsExpandableSection(
                    sectionKey = "haptics",
                    materialSymbolName = "vibration",
                    title = stringResource(R.string.settings_haptics_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.ONLY) {
                            SettingsToggleRow(
                                materialSymbolName = "vibration",
                                title = stringResource(R.string.settings_haptic_feedback),
                                subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                checked = interactionState.hapticFeedbackEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { container.interactionPrefs.setHapticFeedbackEnabled(enabled) }
                                },
                            )
                        }
                    }
                }
            }

            item(key = "security") {
                SettingsExpandableSection(
                    sectionKey = "security",
                    materialSymbolName = "security",
                    title = stringResource(R.string.settings_section_security),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            ToggleRow(
                                materialSymbolName = "lock",
                                title = stringResource(R.string.settings_app_lock_title),
                                subtitle = when {
                                    !deviceCredentialAvailable -> stringResource(R.string.settings_app_lock_no_device_lock)
                                    lockState.enabled -> stringResource(R.string.settings_app_lock_enabled)
                                    else -> stringResource(R.string.settings_app_lock_disabled)
                                },
                                checked = lockState.enabled,
                                enabled = deviceCredentialAvailable || lockState.enabled,
                                onChange = { want ->
                                    if (want) {
                                        scope.launch {
                                            if (deviceCredentialAvailable) {
                                                container.lockPrefs.enableDeviceCredential()
                                            } else {
                                                snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.settings_app_lock_no_device_lock),
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }
                                    } else {
                                        scope.launch { container.lockPrefs.disable() }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            ToggleRow(
                                materialSymbolName = "fingerprint",
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

            item(key = "backup") {
                val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
                val chooseFolderLabel = stringResource(R.string.settings_choose_export_folder)
                val resolvedFolderLabel by produceState(
                    initialValue = backupState.exportFolderUri.takeIf { it.isNotBlank() }
                        ?.let { internalStorageDisplayName } ?: chooseFolderLabel,
                    backupState.exportFolderUri,
                    internalStorageDisplayName,
                    chooseFolderLabel,
                ) {
                    val uriString = backupState.exportFolderUri
                    value = if (uriString.isBlank()) {
                        chooseFolderLabel
                    } else {
                        withContext(Dispatchers.IO) {
                            exportFolderDisplayLabel(context, uriString, internalStorageDisplayName)
                        }
                    }
                }
                val folderLabel = resolvedFolderLabel
                val exportFolderReady = backupState.exportFolderUri.isNotBlank()
                val autoExportSwitchEnabled = exportFolderReady || backupState.autoExportOnChange
                val scheduledExportSwitchEnabled = exportFolderReady || backupState.scheduledExportEnabled

                SettingsExpandableSection(
                    sectionKey = "backup",
                    materialSymbolName = "save",
                    title = stringResource(R.string.settings_backup_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            val chooseExportFolderCd = stringResource(R.string.settings_choose_export_folder)
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
                                    RememberOutlinedButton(onClick = { folderLauncher.launch(null) }) {
                                        RememberMaterialRoundedSymbol(
                                            name = "folder_open",
                                            size = 18.dp,
                                            weight = FontWeight.Medium,
                                            modifier = Modifier.semantics {
                                                contentDescription = chooseExportFolderCd
                                            },
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
                            val backupHelpCd = stringResource(R.string.settings_backup_help_icon_cd)
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
                                    RememberOutlinedButton(
                                        onClick = {
                                            importMergeLauncher.launch(
                                                arrayOf("application/zip", "application/json"),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_import_rules))
                                    }
                                    RememberOutlinedButton(
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
                                            .tapSoundClickable {
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
                                            .tapSoundClickable { showBackupHelp = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RememberMaterialRoundedSymbol(
                                            name = "info",
                                            size = 20.dp,
                                            tint = restoreLabelColor.copy(alpha = 0.75f),
                                            weight = FontWeight.Medium,
                                            modifier = Modifier.semantics { contentDescription = backupHelpCd },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "about") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SettingsStaticSectionHeader(
                        materialSymbolName = "info",
                        title = stringResource(R.string.settings_section_about),
                    )
                    Spacer(Modifier.height(8.dp))
                    AboutSettingsBlock(onOpenIntro = onOpenIntro)
                }
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                RememberTextButton(
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
                RememberTextButton(onClick = { pendingRestoreUri = null }) {
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
                RememberTextButton(onClick = { showBackupHelp = false }) {
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
            RememberSwitch(
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
        modifier = Modifier.tapSoundClickable {
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsExpandableSection(
    sectionKey: String,
    materialSymbolName: String,
    title: String,
    collapsedSectionKeys: Set<String>,
    onCollapsedSectionKeysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val collapsed = sectionKey in collapsedSectionKeys
    val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Column(modifier = modifier) {
        SettingsSectionHeader(
            materialSymbolName = materialSymbolName,
            title = title,
            collapsed = collapsed,
            onToggle = {
                onCollapsedSectionKeysChange(
                    if (collapsed) {
                        collapsedSectionKeys - sectionKey
                    } else {
                        collapsedSectionKeys + sectionKey
                    },
                )
            },
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(
                animationSpec = spatialSpec,
                expandFrom = Alignment.Top,
            ) + fadeIn(fadeInSpec),
            exit = shrinkVertically(
                animationSpec = spatialSpec,
                shrinkTowards = Alignment.Top,
            ) + fadeOut(fadeOutSpec),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun SettingsStaticSectionHeader(
    materialSymbolName: String,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsSectionHeader(
    materialSymbolName: String,
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "settings_section_chevron_rotation",
    )
    val cdExpand = stringResource(R.string.section_expand_cd, title)
    val cdCollapse = stringResource(R.string.section_collapse_cd, title)
    val headerInteractionSource = remember { MutableInteractionSource() }
    val playTap = rememberPlayTapSound()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = if (collapsed) cdExpand else cdCollapse }
            .clickable(
                indication = null,
                interactionSource = headerInteractionSource,
            ) {
                playTap()
                onToggle()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        RememberMaterialRoundedSymbol(
            name = "chevron_right",
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun SettingsToggleRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tapSoundClickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        RememberSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun isPermissionLinked(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        Build.MANUFACTURER.lowercase() in setOf("google", "samsung", "nothing", "motorola")
}

@Composable
private fun ToggleRow(
    materialSymbolName: String,
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
            .let { modifier -> if (enabled) modifier.tapSoundClickable { onChange(!checked) } else modifier }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            weight = FontWeight.Medium,
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
        RememberSwitch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
        )
    }
}

@Composable
private fun SwipeGestureModeDropdown(
    current: SwipeGestureMode,
    onSelect: (SwipeGestureMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(onClick = { expanded = true }) {
        Text(swipeGestureModeLabel(current))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeGestureMode.entries.forEach { mode ->
                RememberDropdownMenuItem(
                    text = { Text(swipeGestureModeLabel(mode)) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeRevealSlotsRow(
    title: String,
    actions: List<NoteSwipeAction?>,
    onActionsChange: (List<NoteSwipeAction?>) -> Unit,
) {
    val normalizedActions = List(3) { slotIndex -> actions.getOrNull(slotIndex) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            RememberTextButton(
                onClick = { onActionsChange(listOf(null, null, null)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.action_reset))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            normalizedActions.forEachIndexed { slotIndex, action ->
                SwipeRevealSlotDropdown(
                    current = action,
                    unavailableActions = normalizedActions
                        .filterIndexed { otherIndex, _ -> otherIndex != slotIndex }
                        .filterNotNull()
                        .toSet(),
                    onSelect = { selected ->
                        val nextActions = normalizedActions.toMutableList()
                        nextActions[slotIndex] = selected
                        onActionsChange(nextActions)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SwipeRevealSlotDropdown(
    current: NoteSwipeAction?,
    unavailableActions: Set<NoteSwipeAction>,
    onSelect: (NoteSwipeAction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(
        onClick = { expanded = true },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        SwipeActionLabelContent(action = current)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RememberDropdownMenuItem(
                text = { Text(stringResource(R.string.settings_swipe_none)) },
                leadingIcon = { SwipeActionIcon(action = null) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            SwipeActionDisplayOrder
                .filter { action -> action == current || action !in unavailableActions }
                .forEach { action ->
                    RememberDropdownMenuItem(
                        text = { Text(noteSwipeActionLabel(action)) },
                        leadingIcon = { SwipeActionIcon(action = action) },
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
private fun NoteSwipeActionDropdown(
    current: NoteSwipeAction,
    excluded: NoteSwipeAction,
    onSelect: (NoteSwipeAction) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    RememberOutlinedButton(onClick = { expanded = true }) {
        SwipeActionLabelContent(action = current)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeActionDisplayOrder.filter { it != excluded }.forEach { action ->
                RememberDropdownMenuItem(
                    text = { Text(noteSwipeActionLabel(action)) },
                    leadingIcon = { SwipeActionIcon(action = action) },
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
private fun SwipeActionLabelContent(action: NoteSwipeAction?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SwipeActionIcon(action = action)
        Text(
            text = action?.let { noteSwipeActionLabel(it) } ?: stringResource(R.string.settings_swipe_none),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwipeActionIcon(action: NoteSwipeAction?) {
    val tint = action?.semanticSwipeIconTint() ?: MaterialTheme.colorScheme.onSurfaceVariant
    val container = action?.semanticSwipeBackground() ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = action?.materialSymbolName ?: "remove",
            size = 16.dp,
            tint = tint,
            filled = action == NoteSwipeAction.TOGGLE_FAVORITE,
            weight = FontWeight.Medium,
        )
    }
}

private val SwipeActionDisplayOrder: List<NoteSwipeAction> = listOf(
    NoteSwipeAction.EDIT,
    NoteSwipeAction.DUPLICATE,
    NoteSwipeAction.TOGGLE_FAVORITE,
    NoteSwipeAction.MARK_DONE,
    NoteSwipeAction.ARCHIVE,
    NoteSwipeAction.TRASH,
)

@Composable
private fun noteSwipeActionLabel(action: NoteSwipeAction): String = stringResource(
    when (action) {
        NoteSwipeAction.EDIT -> R.string.swipe_action_open
        NoteSwipeAction.TRASH -> R.string.edit_bottom_bar_trash
        NoteSwipeAction.DUPLICATE -> R.string.swipe_action_duplicate
        NoteSwipeAction.TOGGLE_FAVORITE -> R.string.swipe_action_toggle_favorite
        NoteSwipeAction.ARCHIVE -> R.string.edit_bottom_bar_archive
        NoteSwipeAction.MARK_DONE -> R.string.swipe_action_mark_done
    },
)

@Composable
private fun swipeGestureModeLabel(mode: SwipeGestureMode): String = stringResource(
    when (mode) {
        SwipeGestureMode.EXECUTE_ONE -> R.string.settings_swipe_mode_execute_one
        SwipeGestureMode.REVEAL_ACTIONS -> R.string.settings_swipe_mode_reveal_actions
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
                RememberMaterialRoundedSymbol(
                    name = swipeStartToEnd.materialSymbolName,
                    size = 20.dp,
                    tint = swipeStartToEnd.semanticSwipeIconTint(),
                    weight = FontWeight.Medium,
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
                RememberMaterialRoundedSymbol(
                    name = swipeEndToStart.materialSymbolName,
                    size = 20.dp,
                    tint = swipeEndToStart.semanticSwipeIconTint(),
                    weight = FontWeight.Medium,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutSettingsBlock(
    onOpenIntro: () -> Unit,
) {
    val context = LocalContext.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val profileUrl = stringResource(R.string.about_author_github_profile_url)
    val copyAboutLink = remember(context) {
        { url: String ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.clipboard_link_label), url),
            )
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
                    text = stringResource(
                        R.string.app_version_format,
                        stringResource(R.string.app_name),
                        BuildConfig.VERSION_NAME,
                    ),
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
                            .clip(iconShape)
                            .tapSoundClickable(onClick = onOpenIntro),
                    )
                    Spacer(Modifier.width(20.dp))
                    AboutAuthorPhoto(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(authorShape)
                            .tapSoundCombinedClickable(
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
                                .tapSoundCombinedClickable(
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
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    weight = FontWeight.Medium,
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
                                    .tapSoundCombinedClickable(
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
                                .tapSoundCombinedClickable(
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
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Medium,
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
                                    .tapSoundCombinedClickable(
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
