package dev.bikram.remember.ui.edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bikram.remember.R
import dev.bikram.remember.data.MonthlyMode
import dev.bikram.remember.data.RecurrenceEndKind
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import java.util.Calendar
import java.util.Locale
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalButton
import dev.bikram.remember.ui.feedback.tapSoundClickable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderPickerSheet(
    initialMillis: Long?,
    initialRule: RecurrenceRule?,
    onConfirm: (Long?, RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val initial = initialMillis ?: (now + 60 * 60 * 1000L)
    val initialCal = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }

    // Date: always store Material's "UTC start-of-Gregorian-day" millis (same as DatePicker
    // output). [initial] is a wall-clock reminder instant on reopen; normalizing avoids the pill
    // jumping to the next calendar day when that instant falls on the next day in UTC.
    var selectedDate by rememberSaveable {
        mutableStateOf(pickerDayMillisForLocalWallClock(initial))
    }
    var dateDialogOpen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var canScheduleExact by remember { 
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) 
    }
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var reminderTimeExplicit by rememberSaveable { mutableStateOf(initialMillis != null) }
    var reminderHour by rememberSaveable { mutableStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var reminderMinute by rememberSaveable { mutableStateOf(initialCal.get(Calendar.MINUTE)) }
    var timePickerOpen by rememberSaveable { mutableStateOf(false) }

    // Repeat
    var repeatOn by rememberSaveable { mutableStateOf(initialRule != null) }
    var repeatExpanded by rememberSaveable { mutableStateOf(false) }
    var unit by rememberSaveable { mutableStateOf(initialRule?.unit ?: RecurrenceUnit.DAY) }
    var unitMenuOpen by rememberSaveable { mutableStateOf(false) }
    var intervalText by rememberSaveable {
        mutableStateOf((initialRule?.interval ?: 1).toString())
    }
    val defaultDayOfWeek = initialCal.get(Calendar.DAY_OF_WEEK)
    var daysOfWeek by rememberSaveable {
        mutableStateOf(initialRule?.daysOfWeek ?: setOf(defaultDayOfWeek))
    }
    var monthlyKind by rememberSaveable {
        mutableStateOf(
            when (initialRule?.monthlyMode) {
                is MonthlyMode.ByNthWeekday -> MonthlyKind.BY_WEEKDAY
                else -> MonthlyKind.BY_DAY
            },
        )
    }
    val defaultDayOfMonth = initialCal.get(Calendar.DAY_OF_MONTH)
    var dayOfMonth by rememberSaveable {
        mutableStateOf(
            (initialRule?.monthlyMode as? MonthlyMode.ByDayOfMonth)?.day ?: defaultDayOfMonth,
        )
    }
    var dayOfMonthMenuOpen by rememberSaveable { mutableStateOf(false) }
    val defaultWeekOrdinal = ((defaultDayOfMonth - 1) / 7) + 1
    var nthOrdinal by rememberSaveable {
        mutableStateOf(
            (initialRule?.monthlyMode as? MonthlyMode.ByNthWeekday)?.ordinal ?: defaultWeekOrdinal,
        )
    }
    var nthOrdinalMenuOpen by rememberSaveable { mutableStateOf(false) }
    var nthWeekday by rememberSaveable {
        mutableStateOf(
            (initialRule?.monthlyMode as? MonthlyMode.ByNthWeekday)?.weekday ?: defaultDayOfWeek,
        )
    }
    var nthWeekdayMenuOpen by rememberSaveable { mutableStateOf(false) }

    // Ends
    var endKind by rememberSaveable { mutableStateOf(initialRule?.endKind ?: RecurrenceEndKind.NEVER) }
    var endDate by rememberSaveable { mutableStateOf(initialRule?.endDate) }
    var endDateDialogOpen by rememberSaveable { mutableStateOf(false) }
    var endCountText by rememberSaveable {
        mutableStateOf((initialRule?.endCount ?: DEFAULT_END_COUNT).toString())
    }

    AppBottomSheet(
        title = stringResource(R.string.reminder_set_title),
        onDismiss = onDismiss,
        scrollable = true,
        actions = null,
    ) {
        if (!notificationsGranted) {
            NotificationPermissionRequiredCard(
                onEnableNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(notificationSettingsIntent(context))
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            return@AppBottomSheet
        }

        // Date pill
        PillRow(
            materialSymbolName = "calendar_month",
            label = formatDate(selectedDate),
            hasValue = true,
            onClick = { dateDialogOpen = true },
        )

        Spacer(Modifier.height(8.dp))

        PillRow(
            materialSymbolName = "schedule",
            label = if (reminderTimeExplicit) {
                formatReminderTimePill(reminderHour, reminderMinute)
            } else {
                stringResource(R.string.reminder_pick_time)
            },
            hasValue = reminderTimeExplicit,
            onClick = { timePickerOpen = true },
            onClear = if (reminderTimeExplicit) {
                { reminderTimeExplicit = false }
            } else {
                null
            },
        )

        Spacer(Modifier.height(8.dp))

        // Repeat pill
        PillRow(
            materialSymbolName = "repeat",
            label = if (repeatOn) repeatSummary(
                unit = unit,
                interval = intervalText.toIntOrNull() ?: 1,
                daysOfWeek = daysOfWeek,
                monthlyKind = monthlyKind,
                dayOfMonth = dayOfMonth,
                nthOrdinal = nthOrdinal,
                nthWeekday = nthWeekday,
                endKind = endKind,
                endDate = endDate,
                endCount = endCountText.toIntOrNull(),
            ) else "Repeat",
            hasValue = repeatOn,
            onClick = {
                if (!repeatOn) {
                    repeatOn = true
                    repeatExpanded = true
                } else {
                    repeatExpanded = !repeatExpanded
                }
            },
            onClear = if (repeatOn) { { repeatOn = false; repeatExpanded = false } } else null,
        )

        if (repeatOn && repeatExpanded) {
            Spacer(Modifier.height(10.dp))
            RepeatConfig(
                unit = unit,
                onUnit = { unit = it },
                unitMenuOpen = unitMenuOpen,
                onUnitMenuOpen = { unitMenuOpen = it },
                intervalText = intervalText,
                onIntervalText = { intervalText = it },
                daysOfWeek = daysOfWeek,
                onDaysOfWeek = { daysOfWeek = it },
                monthlyKind = monthlyKind,
                onMonthlyKind = { monthlyKind = it },
                dayOfMonth = dayOfMonth,
                onDayOfMonth = { dayOfMonth = it },
                dayOfMonthMenuOpen = dayOfMonthMenuOpen,
                onDayOfMonthMenuOpen = { dayOfMonthMenuOpen = it },
                nthOrdinal = nthOrdinal,
                onNthOrdinal = { nthOrdinal = it },
                nthOrdinalMenuOpen = nthOrdinalMenuOpen,
                onNthOrdinalMenuOpen = { nthOrdinalMenuOpen = it },
                nthWeekday = nthWeekday,
                onNthWeekday = { nthWeekday = it },
                nthWeekdayMenuOpen = nthWeekdayMenuOpen,
                onNthWeekdayMenuOpen = { nthWeekdayMenuOpen = it },
                endKind = endKind,
                onEndKind = { endKind = it },
                endDate = endDate,
                onOpenEndDatePicker = { endDateDialogOpen = true },
                endCountText = endCountText,
                onEndCountText = { endCountText = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!canScheduleExact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .tapSoundClickable {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.reminder_exact_alarm_permission_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Bottom action row — Material 3 filled/tonal buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (initialMillis != null) {
                RememberFilledTonalButton(onClick = { onConfirm(null, null) }) { Text(stringResource(R.string.common_clear)) }
            }
            RememberFilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            
            val isDoneEnabled = run {
                if (repeatOn) {
                    if (unit == RecurrenceUnit.WEEK && daysOfWeek.isEmpty()) return@run false
                    if (endKind == RecurrenceEndKind.ON_DATE && endDate == null) return@run false
                    if (endKind == RecurrenceEndKind.AFTER_COUNT && (endCountText.toIntOrNull() == null || endCountText.toInt() < 1)) return@run false
                    if (intervalText.toIntOrNull() == null || intervalText.toInt() < 1) return@run false
                }
                true
            }
            
            RememberButton(
                enabled = isDoneEnabled,
                onClick = {
                val hour24 = if (reminderTimeExplicit) reminderHour else 18
                val minuteVal = if (reminderTimeExplicit) reminderMinute else 0
                val selectedDay =
                    Instant.ofEpochMilli(selectedDate).atZone(ZoneOffset.UTC).toLocalDate()
                val fireAt = selectedDay
                    .atTime(LocalTime.of(hour24, minuteVal))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val rule = if (!repeatOn) null else {
                    val interval = intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1
                    val mode: MonthlyMode? = if (unit == RecurrenceUnit.MONTH) {
                        if (monthlyKind == MonthlyKind.BY_DAY) MonthlyMode.ByDayOfMonth(dayOfMonth)
                        else MonthlyMode.ByNthWeekday(nthOrdinal, nthWeekday)
                    } else null
                    val daysSet = if (unit == RecurrenceUnit.WEEK) daysOfWeek else emptySet()
                    // Fall back to the same default the picker pre-populates (10) when the field is
                    // empty or otherwise unparseable. [RecurrenceRule.sanitized] also maps invalid
                    // AFTER_COUNT + null endCount to NEVER so reminders never stop after one fire.
                    val count = endCountText.toIntOrNull()?.coerceIn(1, 9999) ?: DEFAULT_END_COUNT
                    RecurrenceRule(
                        unit = unit,
                        interval = interval,
                        daysOfWeek = daysSet,
                        monthlyMode = mode,
                        endKind = endKind,
                        endDate = if (endKind == RecurrenceEndKind.ON_DATE) endDate else null,
                        endCount = if (endKind == RecurrenceEndKind.AFTER_COUNT) count else null,
                    )
                }
                onConfirm(fireAt, rule)
            }) { Text(stringResource(R.string.common_done)) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (dateDialogOpen) {
        CalendarPickerDialog(
            initial = selectedDate,
            onConfirm = { selectedDate = it; dateDialogOpen = false },
            onDismiss = { dateDialogOpen = false },
        )
    }
    if (endDateDialogOpen) {
        // Fallback has to be pre-normalized to UTC-midnight because CalendarPickerDialog
        // feeds [initial] straight to rememberDatePickerState, and Material's DatePicker
        // decodes that millis in UTC to pick the displayed day. A raw "now + 30 days" epoch
        // is a wall-clock instant, so in negative-offset zones near midnight local it would
        // render the grid one day ahead of what the user expects. endDate itself is already
        // UTC-midnight (DatePicker round-trip) so it doesn't need the helper.
        CalendarPickerDialog(
            initial = endDate ?: pickerDayMillisForLocalWallClock(now + 30L * 24 * 60 * 60 * 1000L),
            onConfirm = {
                endDate = it
                endKind = RecurrenceEndKind.ON_DATE
                endDateDialogOpen = false
            },
            onDismiss = { endDateDialogOpen = false },
        )
    }

    if (timePickerOpen) {
        ReminderTimePickerDialog(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            onDismiss = { timePickerOpen = false },
            onConfirm = { pickedHour, pickedMinute ->
                reminderHour = pickedHour
                reminderMinute = pickedMinute
                reminderTimeExplicit = true
                timePickerOpen = false
            },
        )
    }
}

@Composable
private fun NotificationPermissionRequiredCard(
    onEnableNotifications: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RememberMaterialRoundedSymbol(
                name = "notifications",
                size = 40.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                weight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.reminder_notifications_required_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.reminder_notifications_required_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(18.dp))
            RememberButton(
                onClick = onEnableNotifications,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reminder_notifications_required_action))
            }
        }
    }
}

private fun notificationSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}

private enum class MonthlyKind { BY_DAY, BY_WEEKDAY }

// Saveable replacement for Material's [DisplayMode], which is a @JvmInline value class and
// therefore has no default Saver - trying to `rememberSaveable { mutableStateOf(DisplayMode.X) }`
// crashes with IllegalArgumentException. We store this enum instead and translate at the
// DatePicker boundary.
// the picker and as a sanity floor when the user submits an empty / unparseable field.
// Pre-fill / fallback occurrence count for AFTER_COUNT mode. Used both when first opening
private const val DEFAULT_END_COUNT = 10

@Composable
private fun formatReminderTimePill(hour24: Int, minute: Int): String {
    return if (DateFormat.is24HourFormat(LocalContext.current)) {
        "%02d:%02d".format(hour24, minute)
    } else {
        formatTime12h(hour24, minute)
    }
}

private fun formatTime12h(hour24: Int, minute: Int): String {
    val ampm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%d:%02d %s".format(hour12, minute, ampm)
}

/**
 * Full-screen-width [Dialog] plus capped-width [Surface] (same pattern as the time picker).
 * Avoids [DatePickerDialog] so the headline gets top padding, the grid gets enough width
 * for all seven columns, and action buttons sit in a tight row instead of a tall footer slot.
 *
 * The header (date text + mode-toggle pencil/calendar icon) is a custom row aligned
 * [Alignment.CenterVertically] instead of Material's built-in headline slot + showModeToggle.
 * This fixes the misalignment caused by headline top-padding vs a center-aligned icon toggle.
 *
 * Switching between calendar-grid and text-input modes is driven externally via an
 * [AnimatedContent] keyed on our own [DisplayMode] state. Each branch holds its own
 * [rememberDatePickerState] locked to one mode, so the transition is a true crossfade / slide
 * animated with the M3 Expressive [androidx.compose.material3.MotionScheme]. Selection is
 * preserved across mode switches through a shared [selectedMillis] state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarPickerDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    val datePickerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
    ) {
        // By wrapping the content in a full-screen box, we force the Android Dialog Window to
        // be full-screen. This means that when the DatePicker switches modes and changes its
        // content height, we use pure Compose `animateContentSize` on the inner Surface.
        // If we didn't do this, the Android WindowManager would try to resize the dialog window
        // mid-animation, causing severe (2-3 seconds) stuttering on many devices.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = datePickerContainerColor,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(min = 328.dp, max = 400.dp)
                    .wrapContentHeight()
                    .animateContentSize(
                        animationSpec = spring<androidx.compose.ui.unit.IntSize>(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .padding(horizontal = 16.dp)
                    .clickable(
                        // Catch clicks on the surface so they don't leak to the dismiss background
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
            ) {
                MaterialTheme(
                    typography = MaterialTheme.typography.copy(
                        displayLarge = MaterialTheme.typography.headlineMedium,
                        headlineLarge = MaterialTheme.typography.headlineMedium,
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        DatePicker(
                            state = state,
                            title = null,
                            showModeToggle = true,
                            modifier = Modifier.padding(top = 16.dp),
                            colors = DatePickerDefaults.colors(
                                containerColor = datePickerContainerColor,
                                dividerColor = Color.Transparent,
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RememberTextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            RememberTextButton(
                                onClick = { state.selectedDateMillis?.let(onConfirm) },
                                enabled = state.selectedDateMillis != null,
                            ) {
                                Text(stringResource(R.string.common_ok))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Same structure as FilePipe `ScheduleTimePickerDialog`; uses the app [MaterialTheme] (no nested palette).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var showDial by remember { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        key(initialHour, initialMinute) {
            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = false,
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.reminder_time_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    // Use surfaceContainerHigh for the dial so it sits visibly above the
                    // surfaceContainerLowest dialog surface, even when Material You's
                    // tonal-surface roles compress close together.
                    val pickerColors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    if (showDial) {
                        TimePicker(state = timePickerState, colors = pickerColors)
                    } else {
                        TimeInput(state = timePickerState, colors = pickerColors)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val timeInputModeLabel = stringResource(R.string.reminder_time_input_mode)
                        val timeDialModeLabel = stringResource(R.string.reminder_time_dial_mode)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        text = if (showDial) {
                                            timeInputModeLabel
                                        } else {
                                            timeDialModeLabel
                                        },
                                    )
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            RememberIconButton(
                                onClick = { showDial = !showDial },
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = if (showDial) "keyboard" else "schedule",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics {
                                        contentDescription = if (showDial) timeInputModeLabel else timeDialModeLabel
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        RememberTextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        RememberTextButton(
                            onClick = {
                                onConfirm(timePickerState.hour, timePickerState.minute)
                            },
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillRow(
    materialSymbolName: String,
    label: String,
    hasValue: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PillHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(
                // Use full-opacity primaryContainer for the activated state so the pill
                // stays clearly distinct from the sheet background even in seed-based
                // themes where the half-alpha version washes out against light surfaces.
                if (hasValue) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .tapSoundClickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 20.dp,
            tint = if (hasValue) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (hasValue) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Reserve the trailing slot so rows with and without Clear match height/width.
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (onClear != null) {
                val cdClear = stringResource(R.string.common_clear)
                RememberIconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    RememberMaterialRoundedSymbol(
                        name = "close",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        weight = FontWeight.Medium,
                        modifier = Modifier.semantics { contentDescription = cdClear },
                    )
                }
            }
        }
    }
}

private val PillHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatConfig(
    unit: RecurrenceUnit,
    onUnit: (RecurrenceUnit) -> Unit,
    unitMenuOpen: Boolean,
    onUnitMenuOpen: (Boolean) -> Unit,
    intervalText: String,
    onIntervalText: (String) -> Unit,
    daysOfWeek: Set<Int>,
    onDaysOfWeek: (Set<Int>) -> Unit,
    monthlyKind: MonthlyKind,
    onMonthlyKind: (MonthlyKind) -> Unit,
    dayOfMonth: Int,
    onDayOfMonth: (Int) -> Unit,
    dayOfMonthMenuOpen: Boolean,
    onDayOfMonthMenuOpen: (Boolean) -> Unit,
    nthOrdinal: Int,
    onNthOrdinal: (Int) -> Unit,
    nthOrdinalMenuOpen: Boolean,
    onNthOrdinalMenuOpen: (Boolean) -> Unit,
    nthWeekday: Int,
    onNthWeekday: (Int) -> Unit,
    nthWeekdayMenuOpen: Boolean,
    onNthWeekdayMenuOpen: (Boolean) -> Unit,
    endKind: RecurrenceEndKind,
    onEndKind: (RecurrenceEndKind) -> Unit,
    endDate: Long?,
    onOpenEndDatePicker: () -> Unit,
    endCountText: String,
    onEndCountText: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // The leading Box matches the RadioButton+spacer width used by the "Ends" rows
            // below, so the "Every" digit field, the date pill, and the occurrences digit
            // field all start at the same vertical line.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RepeatRowHeight),
            ) {
                Box(
                    modifier = Modifier.width(RepeatLeadingPad),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.reminder_every_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                CompactDigitField(
                    value = intervalText,
                    onFilteredChange = { filtered -> onIntervalText(filtered) },
                    maxDigits = 3,
                    fieldWidth = RepeatDigitFieldWidth,
                )
                Spacer(Modifier.width(10.dp))
                SheetDropdown(
                    boxModifier = Modifier
                        .weight(1f)
                        .height(RepeatRowHeight),
                    value = unitLabel(unit),
                    expanded = unitMenuOpen,
                    onExpandedChange = onUnitMenuOpen,
                ) {
                    RecurrenceUnit.entries.forEach { u ->
                        RememberDropdownMenuItem(
                            text = { Text(unitLabel(u)) },
                            onClick = { onUnit(u); onUnitMenuOpen(false) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when (unit) {
                RecurrenceUnit.WEEK -> WeekdayRow(selected = daysOfWeek, onToggle = { day ->
                    val next = if (day in daysOfWeek) {
                        if (daysOfWeek.size == 1) daysOfWeek else daysOfWeek - day
                    } else daysOfWeek + day
                    onDaysOfWeek(next)
                })
                RecurrenceUnit.MONTH -> {
                    RadioOption(
                        selected = monthlyKind == MonthlyKind.BY_DAY,
                        onSelect = { onMonthlyKind(MonthlyKind.BY_DAY) },
                    ) {
                        SheetDropdown(
                            boxModifier = Modifier
                                .fillMaxWidth()
                                .height(RepeatRowHeight),
                            value = "On day $dayOfMonth",
                            expanded = dayOfMonthMenuOpen,
                            onExpandedChange = {
                                if (monthlyKind == MonthlyKind.BY_DAY) onDayOfMonthMenuOpen(it)
                            },
                        ) {
                                (1..31).forEach { d ->
                                    RememberDropdownMenuItem(
                                        text = { Text(stringResource(R.string.reminder_on_day, d)) },
                                        onClick = { onDayOfMonth(d); onDayOfMonthMenuOpen(false) },
                                    )
                                }
                            }
                    }
                    Spacer(Modifier.height(6.dp))
                    RadioOption(
                        selected = monthlyKind == MonthlyKind.BY_WEEKDAY,
                        onSelect = { onMonthlyKind(MonthlyKind.BY_WEEKDAY) },
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SheetDropdown(
                                boxModifier = Modifier
                                    .weight(1f)
                                    .height(RepeatRowHeight),
                                value = ordinalLabel(nthOrdinal),
                                expanded = nthOrdinalMenuOpen,
                                onExpandedChange = {
                                    if (monthlyKind == MonthlyKind.BY_WEEKDAY) onNthOrdinalMenuOpen(it)
                                },
                            ) {
                                    listOf(1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth", 5 to "Last").forEach { (n, lbl) ->
                                        RememberDropdownMenuItem(
                                            text = { Text(lbl) },
                                            onClick = { onNthOrdinal(n); onNthOrdinalMenuOpen(false) },
                                        )
                                    }
                            }
                            SheetDropdown(
                                boxModifier = Modifier
                                    .weight(1f)
                                    .height(RepeatRowHeight),
                                value = weekdayFullName(nthWeekday),
                                expanded = nthWeekdayMenuOpen,
                                onExpandedChange = {
                                    if (monthlyKind == MonthlyKind.BY_WEEKDAY) onNthWeekdayMenuOpen(it)
                                },
                            ) {
                                    listOf(
                                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                                        Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                                        Calendar.SATURDAY,
                                    ).forEach { wd ->
                                        RememberDropdownMenuItem(
                                            text = { Text(weekdayFullName(wd)) },
                                            onClick = { onNthWeekday(wd); onNthWeekdayMenuOpen(false) },
                                        )
                                    }
                            }
                        }
                    }
                }
                else -> {}
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Ends",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            RadioOption(
                selected = endKind == RecurrenceEndKind.NEVER,
                onSelect = { onEndKind(RecurrenceEndKind.NEVER) },
            ) { Text(stringResource(R.string.reminder_never), style = MaterialTheme.typography.bodyLarge) }
            RadioOption(
                selected = endKind == RecurrenceEndKind.ON_DATE,
                onSelect = { onEndKind(RecurrenceEndKind.ON_DATE) },
            ) {
                PillRow(
                    materialSymbolName = "calendar_month",
                    label = endDate?.let { formatDate(it) } ?: "Pick end date",
                    hasValue = endDate != null && endKind == RecurrenceEndKind.ON_DATE,
                    onClick = {
                        onOpenEndDatePicker()
                    },
                )
            }
            RadioOption(
                selected = endKind == RecurrenceEndKind.AFTER_COUNT,
                onSelect = { onEndKind(RecurrenceEndKind.AFTER_COUNT) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RepeatRowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactDigitField(
                        value = endCountText,
                        onFilteredChange = { filtered -> onEndCountText(filtered) },
                        maxDigits = 4,
                        fieldWidth = RepeatDigitFieldWidth,
                        enabled = endKind == RecurrenceEndKind.AFTER_COUNT,
                    )
                    Text(
                        text = stringResource(R.string.reminder_occurrences),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val RepeatRowHeight = 44.dp

// Width of the leading column in repeat rows. Matches the default RadioButton touch
// target (48.dp) plus the 8.dp spacer used inside RadioOption so the "Every" textbox
// starts at the same x as the date pill / occurrences textbox below.
private val RepeatLeadingPad = 56.dp

// Shared width for the "Every" interval and the "occurrences" count digit fields so
// they line up as matched inputs.
private val RepeatDigitFieldWidth = 76.dp

@Composable
private fun CompactDigitField(
    value: String,
    onFilteredChange: (String) -> Unit,
    maxDigits: Int,
    modifier: Modifier = Modifier,
    fieldWidth: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val outlineShape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .width(fieldWidth)
            .height(RepeatRowHeight)
            .clip(outlineShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.6f else 0.35f),
                shape = outlineShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { entered ->
                onFilteredChange(entered.filter { ch -> ch.isDigit() }.take(maxDigits))
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SheetDropdown(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    boxModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RepeatRowHeight)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                )
                .tapSoundClickable { onExpandedChange(true) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            RememberMaterialRoundedSymbol(
                name = "arrow_drop_down",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                weight = FontWeight.Medium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            content()
        }
    }
}

@Composable
private fun RadioOption(
    selected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tapSoundClickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun WeekdayRow(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val weekdays = listOf(
        Calendar.SUNDAY to "S",
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekdays.forEach { (day, label) ->
            val isSel = day in selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .tapSoundClickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun unitLabel(u: RecurrenceUnit): String = when (u) {
    RecurrenceUnit.DAY -> "Day"
    RecurrenceUnit.WEEK -> "Week"
    RecurrenceUnit.MONTH -> "Month"
    RecurrenceUnit.YEAR -> "Year"
}

private fun ordinalLabel(n: Int): String = when (n) {
    1 -> "First"; 2 -> "Second"; 3 -> "Third"; 4 -> "Fourth"; 5 -> "Last"
    else -> "First"
}

private fun ordinalWord(n: Int): String = when (n) {
    1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "last"
    else -> "first"
}

private fun weekdayFullName(weekday: Int): String = when (weekday) {
    Calendar.SUNDAY -> "Sunday"
    Calendar.MONDAY -> "Monday"
    Calendar.TUESDAY -> "Tuesday"
    Calendar.WEDNESDAY -> "Wednesday"
    Calendar.THURSDAY -> "Thursday"
    Calendar.FRIDAY -> "Friday"
    Calendar.SATURDAY -> "Saturday"
    else -> ""
}

private fun weekdayShort(weekday: Int): String = when (weekday) {
    Calendar.SUNDAY -> "Sun"
    Calendar.MONDAY -> "Mon"
    Calendar.TUESDAY -> "Tue"
    Calendar.WEDNESDAY -> "Wed"
    Calendar.THURSDAY -> "Thu"
    Calendar.FRIDAY -> "Fri"
    Calendar.SATURDAY -> "Sat"
    else -> ""
}

/**
 * Material [DatePicker] reports and expects millis at **start of the selected day in UTC**
 * (not the user's reminder wall-clock instant). Map a real instant to that encoding using the
 * **local** calendar date so reopening a saved reminder keeps the same day as on the grid.
 */
private fun pickerDayMillisForLocalWallClock(wallClockEpochMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val localDate = Instant.ofEpochMilli(wallClockEpochMillis).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

// Material DatePicker encodes the chosen calendar day at UTC midnight. Format using that UTC
// calendar date so the pill matches the grid; combine with wall time in the system zone for
// [fireAt] above.
private object LocalDatePillFormatterCache {
    private var cachedLocale: Locale? = null
    private var cachedFormatter: DateTimeFormatter? = null

    @Synchronized
    fun get(locale: Locale): DateTimeFormatter {
        val current = cachedFormatter
        if (current != null && cachedLocale == locale) return current
        val fresh = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale)
        cachedLocale = locale
        cachedFormatter = fresh
        return fresh
    }
}

private fun formatDate(millis: Long): String {
    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDatePillFormatterCache.get(Locale.getDefault()).format(localDate)
}

private fun repeatSummary(
    unit: RecurrenceUnit,
    interval: Int,
    daysOfWeek: Set<Int>,
    monthlyKind: MonthlyKind,
    dayOfMonth: Int,
    nthOrdinal: Int,
    nthWeekday: Int,
    endKind: RecurrenceEndKind,
    endDate: Long?,
    endCount: Int?,
): String {
    val every = if (interval == 1) when (unit) {
        RecurrenceUnit.DAY -> "Daily"
        RecurrenceUnit.WEEK -> "Weekly"
        RecurrenceUnit.MONTH -> "Monthly"
        RecurrenceUnit.YEAR -> "Yearly"
    } else "Every $interval ${unitLabel(unit).lowercase()}s"
    val detail = when (unit) {
        RecurrenceUnit.WEEK -> if (daysOfWeek.size in 1..6) {
            " on " + daysOfWeek.sorted().joinToString(", ") { weekdayShort(it) }
        } else ""
        RecurrenceUnit.MONTH -> when (monthlyKind) {
            MonthlyKind.BY_DAY -> " on day $dayOfMonth"
            MonthlyKind.BY_WEEKDAY -> " on the ${ordinalWord(nthOrdinal)} ${weekdayShort(nthWeekday)}"
        }
        else -> ""
    }
    val ending = when (endKind) {
        RecurrenceEndKind.NEVER -> ""
        RecurrenceEndKind.ON_DATE -> endDate?.let { " until ${formatDate(it)}" }.orEmpty()
        RecurrenceEndKind.AFTER_COUNT -> endCount?.let { " for $it times" }.orEmpty()
    }
    return every + detail + ending
}
