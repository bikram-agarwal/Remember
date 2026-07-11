@file:Suppress("ConfigurationScreenWidthHeight")

package dev.bikram.remember.ui.edit

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bikram.remember.R
import dev.bikram.remember.data.MonthlyMode
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.RecurrenceEndKind
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import dev.bikram.remember.domain.formatTimeOfDay
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.rememberBottomSheetStateWithUnsavedChanges
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberConfirmDialog
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalButton
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberUnsavedChangesDialog
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

internal data class ReminderDraft(
    val selectedDate: Long,
    val reminderDateExplicit: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val reminderTimeExplicit: Boolean,
    val repeatOn: Boolean,
    val repeatExpanded: Boolean,
    val unit: RecurrenceUnit,
    val intervalText: String,
    val daysOfWeek: Set<Int>,
    val monthlyKind: MonthlyKind,
    val dayOfMonth: Int,
    val nthOrdinal: Int,
    val nthWeekday: Int,
    val endKind: RecurrenceEndKind,
    val endDate: Long?,
    val endCountText: String,
    val unitMenuOpen: Boolean = false,
    val dayOfMonthMenuOpen: Boolean = false,
    val nthOrdinalMenuOpen: Boolean = false,
    val nthWeekdayMenuOpen: Boolean = false,
    val originalReminderAt: Long? = null,
    val snoozedUntil: Long? = null,
    val originalRecurrence: RecurrenceRule? = null,
)

internal val ReminderDraftSaver =
    listSaver<ReminderDraft, Any?>(
        save = { draft ->
            listOf(
                draft.selectedDate,
                draft.reminderDateExplicit,
                draft.reminderHour,
                draft.reminderMinute,
                draft.reminderTimeExplicit,
                draft.repeatOn,
                draft.repeatExpanded,
                draft.unit.name,
                draft.intervalText,
                draft.daysOfWeek.toList(),
                draft.monthlyKind.name,
                draft.dayOfMonth,
                draft.nthOrdinal,
                draft.nthWeekday,
                draft.endKind.name,
                draft.endDate,
                draft.endCountText,
                draft.unitMenuOpen,
                draft.dayOfMonthMenuOpen,
                draft.nthOrdinalMenuOpen,
                draft.nthWeekdayMenuOpen,
                draft.originalReminderAt,
                draft.snoozedUntil,
                RecurrenceRule.toJson(draft.originalRecurrence),
            )
        },
        restore = { list ->
            ReminderDraft(
                selectedDate = list[0] as Long,
                reminderDateExplicit = list[1] as Boolean,
                reminderHour = list[2] as Int,
                reminderMinute = list[3] as Int,
                reminderTimeExplicit = list[4] as Boolean,
                repeatOn = list[5] as Boolean,
                repeatExpanded = list[6] as Boolean,
                unit = RecurrenceUnit.valueOf(list[7] as String),
                intervalText = list[8] as String,
                daysOfWeek = (list[9] as List<*>).map { (it as Number).toInt() }.toSet(),
                monthlyKind = MonthlyKind.valueOf(list[10] as String),
                dayOfMonth = list[11] as Int,
                nthOrdinal = list[12] as Int,
                nthWeekday = list[13] as Int,
                endKind = RecurrenceEndKind.valueOf(list[14] as String),
                endDate = list[15] as Long?,
                endCountText = list[16] as String,
                unitMenuOpen = list[17] as Boolean,
                dayOfMonthMenuOpen = list[18] as Boolean,
                nthOrdinalMenuOpen = list[19] as Boolean,
                nthWeekdayMenuOpen = list[20] as Boolean,
                originalReminderAt = list.getOrNull(21) as? Long,
                snoozedUntil = list.getOrNull(22) as? Long,
                originalRecurrence = RecurrenceRule.fromJson(list.getOrNull(23) as? String),
            )
        },
    )

internal val ReminderDraftListSaver =
    listSaver<List<ReminderDraft>, Any?>(
        save = { list ->
            list.map { draft ->
                with(ReminderDraftSaver) { save(draft) }
            }
        },
        restore = { list ->
            list.map { element ->
                ReminderDraftSaver.restore(element as List<*>)!!
            }
        },
    )

private fun createBlankDraft(now: Long): ReminderDraft {
    val cal = Calendar.getInstance().apply { timeInMillis = now + 60 * 60 * 1000L }
    return ReminderDraft(
        selectedDate = pickerDayMillisForLocalWallClock(cal.timeInMillis),
        reminderDateExplicit = false,
        reminderHour = 9,
        reminderMinute = 0,
        reminderTimeExplicit = false,
        repeatOn = false,
        repeatExpanded = false,
        unit = RecurrenceUnit.DAY,
        intervalText = "1",
        daysOfWeek = setOf(cal.get(Calendar.DAY_OF_WEEK)),
        monthlyKind = MonthlyKind.BY_DAY,
        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
        nthOrdinal = ((cal.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1,
        nthWeekday = cal.get(Calendar.DAY_OF_WEEK),
        endKind = RecurrenceEndKind.NEVER,
        endDate = null,
        endCountText = "10",
        originalReminderAt = null,
        snoozedUntil = null,
        originalRecurrence = null,
    )
}

internal fun NoteReminder.toDraft(): ReminderDraft {
    val baseTime = originalReminderAt ?: reminderAt
    val cal = Calendar.getInstance().apply { timeInMillis = baseTime }
    val rule = recurrence
    return ReminderDraft(
        selectedDate = pickerDayMillisForLocalWallClock(baseTime),
        reminderDateExplicit = true,
        reminderHour = cal.get(Calendar.HOUR_OF_DAY),
        reminderMinute = cal.get(Calendar.MINUTE),
        reminderTimeExplicit = true,
        repeatOn = rule != null,
        repeatExpanded = false,
        unit = rule?.unit ?: RecurrenceUnit.DAY,
        intervalText = (rule?.interval ?: 1).toString(),
        daysOfWeek = rule?.daysOfWeek ?: setOf(cal.get(Calendar.DAY_OF_WEEK)),
        monthlyKind =
            when (rule?.monthlyMode) {
                is MonthlyMode.ByNthWeekday -> MonthlyKind.BY_WEEKDAY
                else -> MonthlyKind.BY_DAY
            },
        dayOfMonth = (rule?.monthlyMode as? MonthlyMode.ByDayOfMonth)?.day ?: cal.get(Calendar.DAY_OF_MONTH),
        nthOrdinal = (rule?.monthlyMode as? MonthlyMode.ByNthWeekday)?.ordinal ?: (((cal.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1),
        nthWeekday = (rule?.monthlyMode as? MonthlyMode.ByNthWeekday)?.weekday ?: cal.get(Calendar.DAY_OF_WEEK),
        endKind = rule?.endKind ?: RecurrenceEndKind.NEVER,
        endDate = rule?.endDate,
        endCountText = (rule?.endCount ?: DEFAULT_END_COUNT).toString(),
        originalReminderAt = originalReminderAt,
        snoozedUntil = if (originalReminderAt != null) reminderAt else null,
        originalRecurrence = if (originalReminderAt != null) recurrence else null,
    )
}

internal fun ReminderDraft.toReminder(): NoteReminder {
    val hour24 = if (reminderTimeExplicit) reminderHour else 9
    val minuteVal = if (reminderTimeExplicit) reminderMinute else 0
    val selectedDay = Instant.ofEpochMilli(selectedDate).atZone(ZoneOffset.UTC).toLocalDate()
    val fireAt =
        selectedDay
            .atTime(LocalTime.of(hour24, minuteVal))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    val rule =
        if (!repeatOn) {
            null
        } else {
            val interval = intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1
            val mode: MonthlyMode? =
                if (unit == RecurrenceUnit.MONTH) {
                    if (monthlyKind == MonthlyKind.BY_DAY) {
                        MonthlyMode.ByDayOfMonth(dayOfMonth)
                    } else {
                        MonthlyMode.ByNthWeekday(nthOrdinal, nthWeekday)
                    }
                } else {
                    null
                }
            val daysSet = if (unit == RecurrenceUnit.WEEK) daysOfWeek else emptySet()
            val count = endCountText.toIntOrNull()?.coerceIn(1, 9999) ?: DEFAULT_END_COUNT
            RecurrenceRule(
                unit = unit,
                interval = interval,
                daysOfWeek = daysSet,
                monthlyMode = mode,
                endKind = endKind,
                endDate = if (endKind == RecurrenceEndKind.ON_DATE) endDate else null,
                endCount = if (endKind == RecurrenceEndKind.AFTER_COUNT) count else null,
            ).sanitized()
        }

    val scheduleChanged =
        originalReminderAt == null ||
            fireAt != originalReminderAt ||
            rule != originalRecurrence

    return if (scheduleChanged) {
        NoteReminder(
            reminderAt = fireAt,
            recurrence = rule,
            originalReminderAt = null,
        )
    } else {
        NoteReminder(
            reminderAt = snoozedUntil ?: fireAt,
            recurrence = rule,
            originalReminderAt = originalReminderAt,
        )
    }
}

private fun getCompactRecurrenceLabel(
    context: Context,
    rule: RecurrenceRule,
): String {
    val interval = rule.interval.coerceAtLeast(1)
    return if (interval == 1) {
        when (rule.unit) {
            RecurrenceUnit.HOUR -> context.getString(R.string.reminder_recurrence_hourly)
            RecurrenceUnit.DAY -> context.getString(R.string.reminder_recurrence_daily)
            RecurrenceUnit.WEEK -> context.getString(R.string.reminder_recurrence_weekly)
            RecurrenceUnit.MONTH -> context.getString(R.string.reminder_recurrence_monthly)
            RecurrenceUnit.YEAR -> context.getString(R.string.reminder_recurrence_yearly)
        }
    } else {
        val resId =
            when (rule.unit) {
                RecurrenceUnit.HOUR -> R.plurals.reminder_recurrence_every_hours
                RecurrenceUnit.DAY -> R.plurals.reminder_recurrence_every_days
                RecurrenceUnit.WEEK -> R.plurals.reminder_recurrence_every_weeks
                RecurrenceUnit.MONTH -> R.plurals.reminder_recurrence_every_months
                RecurrenceUnit.YEAR -> R.plurals.reminder_recurrence_every_years
            }
        context.resources.getQuantityString(resId, interval, interval)
    }
}

private fun formatCollapsedHeader(
    context: Context,
    draft: ReminderDraft,
): String {
    if (!draft.reminderDateExplicit) {
        return context.getString(R.string.options_reminder)
    }
    val timePart =
        if (draft.reminderTimeExplicit) {
            formatTimeOfDay(context, draft.reminderHour, draft.reminderMinute)
        } else {
            formatTimeOfDay(context, 9, 0)
        }
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val formattedDate =
        Instant
            .ofEpochMilli(draft.selectedDate)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(formatter)
    val base = "$formattedDate • $timePart"
    return if (draft.repeatOn) {
        val interval = draft.intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1
        val dummyRule =
            RecurrenceRule(
                unit = draft.unit,
                interval = interval,
                daysOfWeek = if (draft.unit == RecurrenceUnit.WEEK) draft.daysOfWeek else emptySet(),
                monthlyMode = null,
                endKind = draft.endKind,
                endDate = draft.endDate,
                endCount = draft.endCountText.toIntOrNull(),
            )
        val repeatText = getCompactRecurrenceLabel(context, dummyRule)
        "$base • $repeatText"
    } else {
        base
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderPickerSheet(
    initialReminders: List<NoteReminder>,
    onConfirm: (List<NoteReminder>) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    var drafts by rememberSaveable(stateSaver = ReminderDraftListSaver) {
        mutableStateOf(
            if (initialReminders.isEmpty()) {
                listOf(createBlankDraft(now))
            } else {
                initialReminders.map { it.toDraft() }
            },
        )
    }
    var expandedIndex by rememberSaveable { mutableIntStateOf(0) }
    var draftKeys by rememberSaveable { mutableStateOf(List(drafts.size) { index -> index.toLong() }) }
    var nextDraftKey by rememberSaveable { mutableLongStateOf(drafts.size.toLong()) }
    var enteringExpandedDraftKey by remember { mutableLongStateOf(Long.MIN_VALUE) }

    val hasChanges =
        if (initialReminders.isEmpty()) {
            drafts.any { it.reminderDateExplicit }
        } else {
            drafts.map { it.toReminder() } != initialReminders
        }
    val currentHasChanges = rememberUpdatedState(hasChanges)
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var showClearAllConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val sheetState =
        rememberBottomSheetStateWithUnsavedChanges(
            isDirty = hasChanges,
            onShowDialog = { showUnsavedDialog = true },
        )

    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var canScheduleExact by remember { mutableStateOf(am.canScheduleExactAlarms()) }
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canScheduleExact = am.canScheduleExactAlarms()
                    notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val controlsEnabled = notificationsGranted
    var dateDialogOpen by rememberSaveable { mutableStateOf(false) }
    var timePickerOpen by rememberSaveable { mutableStateOf(false) }
    var endDateDialogOpen by rememberSaveable { mutableStateOf(false) }

    AppBottomSheet(
        title = stringResource(R.string.reminder_set_title),
        onDismiss = {
            if (hasChanges) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        showTitleBar = notificationsGranted,
        scrollable = true,
        actions = null,
    ) {
        if (!notificationsGranted) {
            NotificationPermissionRequiredContent(
                iconName = "alarm",
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
        }

        if (notificationsGranted) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val expansionSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
                val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
                val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
                drafts.forEachIndexed { index, draft ->
                    val draftKey = draftKeys.getOrElse(index) { index.toLong() }
                    val isExpanded = expandedIndex == index

                    key(draftKey) {
                        val expandedContentState =
                            remember {
                                MutableTransitionState(isExpanded && enteringExpandedDraftKey != draftKey)
                            }
                        expandedContentState.targetState = isExpanded

                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .border(
                                        width = 1.dp,
                                        color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.medium,
                                    ),
                            shape = MaterialTheme.shapes.medium,
                            color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Column {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable {
                                                expandedIndex = if (isExpanded) -1 else index
                                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = if (isExpanded) "keyboard_arrow_down" else "keyboard_arrow_right",
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        weight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text =
                                            if (isExpanded) {
                                                stringResource(R.string.reminder_header_index, index + 1)
                                            } else {
                                                formatCollapsedHeader(context, draft)
                                            },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (drafts.size > 1) {
                                        RememberIconButton(
                                            onClick = {
                                                val removedKey = draftKeys.getOrNull(index)
                                                val nextDrafts = drafts.toMutableList()
                                                val nextDraftKeys = draftKeys.toMutableList()
                                                nextDrafts.removeAt(index)
                                                if (index in nextDraftKeys.indices) {
                                                    nextDraftKeys.removeAt(index)
                                                }
                                                drafts = nextDrafts
                                                draftKeys = nextDraftKeys
                                                if (removedKey == enteringExpandedDraftKey) {
                                                    enteringExpandedDraftKey = Long.MIN_VALUE
                                                }
                                                expandedIndex =
                                                    when {
                                                        nextDrafts.isEmpty() -> -1
                                                        index == expandedIndex -> index.coerceAtMost(nextDrafts.lastIndex)
                                                        index < expandedIndex -> expandedIndex - 1
                                                        expandedIndex >= nextDrafts.size -> nextDrafts.lastIndex
                                                        else -> expandedIndex
                                                    }
                                            },
                                        ) {
                                            RememberMaterialRoundedSymbol(
                                                name = "delete",
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.error,
                                                weight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visibleState = expandedContentState,
                                    enter = expandVertically(animationSpec = expansionSpec) + fadeIn(animationSpec = fadeInSpec),
                                    exit = shrinkVertically(animationSpec = expansionSpec) + fadeOut(animationSpec = fadeOutSpec),
                                ) {
                                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                        PillRow(
                                            materialSymbolName = "calendar_month",
                                            label =
                                                if (draft.reminderDateExplicit) {
                                                    formatDate(draft.selectedDate)
                                                } else {
                                                    stringResource(R.string.reminder_pick_date)
                                                },
                                            hasValue = draft.reminderDateExplicit,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                dateDialogOpen = true
                                            },
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        PillRow(
                                            materialSymbolName = "schedule",
                                            label =
                                                if (draft.reminderTimeExplicit) {
                                                    formatReminderTimePill(draft.reminderHour, draft.reminderMinute)
                                                } else {
                                                    stringResource(R.string.reminder_pick_time)
                                                },
                                            hasValue = draft.reminderTimeExplicit,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                timePickerOpen = true
                                            },
                                            onClear = null,
                                        )

                                        if (draft.snoozedUntil != null) {
                                            Spacer(Modifier.height(8.dp))
                                            SnoozeIndicator(snoozedUntil = draft.snoozedUntil)
                                        }

                                        Spacer(Modifier.height(8.dp))

                                        PillRow(
                                            materialSymbolName = "repeat",
                                            label =
                                                if (draft.repeatOn) {
                                                    repeatSummary(
                                                        unit = draft.unit,
                                                        interval = draft.intervalText.toIntOrNull() ?: 1,
                                                        daysOfWeek = draft.daysOfWeek,
                                                        monthlyKind = draft.monthlyKind,
                                                        dayOfMonth = draft.dayOfMonth,
                                                        nthOrdinal = draft.nthOrdinal,
                                                        nthWeekday = draft.nthWeekday,
                                                        endKind = draft.endKind,
                                                        endDate = draft.endDate,
                                                        endCount = draft.endCountText.toIntOrNull(),
                                                    )
                                                } else {
                                                    stringResource(R.string.reminder_repeat)
                                                },
                                            hasValue = draft.repeatOn,
                                            enabled = controlsEnabled,
                                            onClick = {
                                                drafts =
                                                    drafts.mapIndexed { idx, d ->
                                                        if (idx == index) {
                                                            if (!d.repeatOn) {
                                                                d.copy(repeatOn = true, repeatExpanded = true)
                                                            } else {
                                                                d.copy(repeatExpanded = !d.repeatExpanded)
                                                            }
                                                        } else {
                                                            d
                                                        }
                                                    }
                                            },
                                            onClear =
                                                if (draft.repeatOn && controlsEnabled) {
                                                    {
                                                        drafts =
                                                            drafts.mapIndexed { idx, d ->
                                                                if (idx == index) d.copy(repeatOn = false, repeatExpanded = false) else d
                                                            }
                                                    }
                                                } else {
                                                    null
                                                },
                                        )

                                        if (draft.repeatOn && draft.repeatExpanded && controlsEnabled) {
                                            Spacer(Modifier.height(10.dp))
                                            RepeatConfig(
                                                unit = draft.unit,
                                                onUnit = { nextUnit ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(unit = nextUnit) else d
                                                        }
                                                },
                                                unitMenuOpen = draft.unitMenuOpen,
                                                onUnitMenuOpen = { open ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(unitMenuOpen = open) else d
                                                        }
                                                },
                                                intervalText = draft.intervalText,
                                                onIntervalText = { nextText ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(intervalText = nextText) else d
                                                        }
                                                },
                                                daysOfWeek = draft.daysOfWeek,
                                                onDaysOfWeek = { nextDays ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(daysOfWeek = nextDays) else d
                                                        }
                                                },
                                                monthlyKind = draft.monthlyKind,
                                                onMonthlyKind = { nextKind ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(monthlyKind = nextKind) else d
                                                        }
                                                },
                                                dayOfMonth = draft.dayOfMonth,
                                                onDayOfMonth = { nextDay ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(dayOfMonth = nextDay) else d
                                                        }
                                                },
                                                dayOfMonthMenuOpen = draft.dayOfMonthMenuOpen,
                                                onDayOfMonthMenuOpen = { open ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(dayOfMonthMenuOpen = open) else d
                                                        }
                                                },
                                                nthOrdinal = draft.nthOrdinal,
                                                onNthOrdinal = { nextOrd ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(nthOrdinal = nextOrd) else d
                                                        }
                                                },
                                                nthOrdinalMenuOpen = draft.nthOrdinalMenuOpen,
                                                onNthOrdinalMenuOpen = { open ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(nthOrdinalMenuOpen = open) else d
                                                        }
                                                },
                                                nthWeekday = draft.nthWeekday,
                                                onNthWeekday = { nextWd ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(nthWeekday = nextWd) else d
                                                        }
                                                },
                                                nthWeekdayMenuOpen = draft.nthWeekdayMenuOpen,
                                                onNthWeekdayMenuOpen = { open ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(nthWeekdayMenuOpen = open) else d
                                                        }
                                                },
                                                endKind = draft.endKind,
                                                onEndKind = { nextKind ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(endKind = nextKind) else d
                                                        }
                                                },
                                                endDate = draft.endDate,
                                                onOpenEndDatePicker = { endDateDialogOpen = true },
                                                endCountText = draft.endCountText,
                                                onEndCountText = { nextCount ->
                                                    drafts =
                                                        drafts.mapIndexed { idx, d ->
                                                            if (idx == index) d.copy(endCountText = nextCount) else d
                                                        }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (drafts.size < 3 && controlsEnabled) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .drawWithCache {
                                    val stroke =
                                        Stroke(
                                            width = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                                        )
                                    onDrawBehind {
                                        drawRoundRect(
                                            color = primaryColor.copy(alpha = 0.5f),
                                            style = stroke,
                                            cornerRadius = CornerRadius(12.dp.toPx()),
                                        )
                                    }
                                }.clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    val newDraftKey = nextDraftKey
                                    val nextDrafts = drafts + createBlankDraft(now)
                                    nextDraftKey += 1L
                                    drafts = nextDrafts
                                    draftKeys = draftKeys + newDraftKey
                                    enteringExpandedDraftKey = newDraftKey
                                    expandedIndex = nextDrafts.lastIndex
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RememberMaterialRoundedSymbol(
                                name = "add",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.reminder_add_button),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (controlsEnabled && !canScheduleExact) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .tapSoundClickable {
                            @SuppressLint("InlinedApi")
                            val intent =
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            context.startActivity(intent)
                        }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reminder_exact_alarm_permission_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RememberFilledTonalButton(
                enabled = drafts.any { it.reminderDateExplicit },
                onClick = {
                    showClearAllConfirmDialog = true
                },
            ) { Text(stringResource(R.string.reminder_clear_all)) }

            RememberFilledTonalButton(
                onClick = {
                    if (hasChanges) {
                        showUnsavedDialog = true
                    } else {
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.common_cancel)) }

            val isDoneEnabled =
                run {
                    if (drafts.isEmpty()) return@run initialReminders.isNotEmpty()
                    if (!controlsEnabled) return@run initialReminders.isNotEmpty()
                    drafts.forEach { draft ->
                        if (!draft.reminderDateExplicit) return@run false
                        if (draft.repeatOn) {
                            if (draft.unit == RecurrenceUnit.WEEK && draft.daysOfWeek.isEmpty()) return@run false
                            if (draft.endKind == RecurrenceEndKind.ON_DATE && draft.endDate == null) return@run false
                            if (draft.endKind == RecurrenceEndKind.AFTER_COUNT && (draft.endCountText.toIntOrNull() == null || draft.endCountText.toInt() < 1)) return@run false
                            if (draft.intervalText.toIntOrNull() == null || draft.intervalText.toInt() < 1) return@run false
                        }
                    }
                    val currentReminders = drafts.map { it.toReminder() }
                    if (currentReminders == initialReminders) return@run false
                    true
                }

            RememberButton(
                enabled = isDoneEnabled,
                onClick = {
                    if (drafts.isEmpty()) {
                        onConfirm(emptyList())
                    } else {
                        val remindersList = drafts.map { it.toReminder() }
                        onConfirm(remindersList)
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (dateDialogOpen && expandedIndex in drafts.indices) {
        val currentDraft = drafts[expandedIndex]
        CalendarPickerDialog(
            initial = currentDraft.selectedDate,
            onConfirm = { dateMillis ->
                drafts =
                    drafts.mapIndexed { idx, d ->
                        if (idx == expandedIndex) d.copy(selectedDate = dateMillis, reminderDateExplicit = true) else d
                    }
                dateDialogOpen = false
            },
            onDismiss = { dateDialogOpen = false },
        )
    }

    if (endDateDialogOpen && expandedIndex in drafts.indices) {
        val currentDraft = drafts[expandedIndex]
        CalendarPickerDialog(
            initial = currentDraft.endDate ?: pickerDayMillisForLocalWallClock(now + 30L * 24 * 60 * 60 * 1000L),
            onConfirm = { dateMillis ->
                drafts =
                    drafts.mapIndexed { idx, d ->
                        if (idx == expandedIndex) d.copy(endDate = dateMillis, endKind = RecurrenceEndKind.ON_DATE) else d
                    }
                endDateDialogOpen = false
            },
            onDismiss = { endDateDialogOpen = false },
        )
    }

    if (timePickerOpen && expandedIndex in drafts.indices) {
        val currentDraft = drafts[expandedIndex]
        ReminderTimePickerDialog(
            initialHour = currentDraft.reminderHour,
            initialMinute = currentDraft.reminderMinute,
            onDismiss = { timePickerOpen = false },
            onConfirm = { pickedHour, pickedMinute ->
                drafts =
                    drafts.mapIndexed { idx, d ->
                        if (idx == expandedIndex) d.copy(reminderHour = pickedHour, reminderMinute = pickedMinute, reminderTimeExplicit = true) else d
                    }
                timePickerOpen = false
            },
        )
    }
    if (showUnsavedDialog) {
        RememberUnsavedChangesDialog(
            onConfirm = {
                showUnsavedDialog = false
                onDismiss()
            },
            onDismiss = { showUnsavedDialog = false },
        )
    }
    if (showClearAllConfirmDialog) {
        RememberConfirmDialog(
            title = stringResource(R.string.reminder_clear_all_confirm_title),
            text = stringResource(R.string.reminder_clear_all_confirm_body),
            confirmLabel = stringResource(R.string.reminder_clear_all_confirm_action),
            onConfirm = {
                showClearAllConfirmDialog = false
                drafts = emptyList()
                onConfirm(emptyList())
            },
            onDismiss = { showClearAllConfirmDialog = false },
            destructive = true,
        )
    }
}

@Composable
internal fun NotificationPermissionRequiredSheet(
    onDismiss: () -> Unit,
    @StringRes titleRes: Int = R.string.reminder_notifications_required_title,
    @StringRes bodyRes: Int = R.string.reminder_notifications_required_body,
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (notificationsGranted) onDismiss()
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    if (notificationsGranted) onDismiss()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppBottomSheet(
        title = "",
        onDismiss = onDismiss,
        showTitleBar = false,
        scrollable = false,
        actions = null,
    ) {
        NotificationPermissionRequiredContent(
            titleRes = titleRes,
            bodyRes = bodyRes,
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
    }
}

@Composable
private fun NotificationPermissionRequiredContent(
    iconName: String = "notifications",
    @StringRes titleRes: Int = R.string.reminder_notifications_required_title,
    @StringRes bodyRes: Int = R.string.reminder_notifications_required_body,
    onEnableNotifications: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RememberMaterialRoundedSymbol(
            name = iconName,
            size = 40.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

internal enum class MonthlyKind { BY_DAY, BY_WEEKDAY }

// Saveable replacement for Material's [DisplayMode], which is a @JvmInline value class and
// therefore has no default Saver - trying to `rememberSaveable { mutableStateOf(DisplayMode.X) }`
// crashes with IllegalArgumentException. We store this enum instead and translate at the
// DatePicker boundary.
// the picker and as a sanity floor when the user submits an empty / unparseable field.
// Pre-fill / fallback occurrence count for AFTER_COUNT mode. Used both when first opening
private const val DEFAULT_END_COUNT = 10
private const val CALENDAR_PICKER_MIN_WIDTH_DENSITY_SCALE = 0.82f
private const val CALENDAR_PICKER_MIN_HEIGHT_DENSITY_SCALE = 0.74f
private val CALENDAR_PICKER_WIDTH = 360.dp
private val CALENDAR_PICKER_HEIGHT = 520.dp
private val CALENDAR_DIALOG_MARGIN = 8.dp
private val CALENDAR_ACTION_AREA_HEIGHT = 68.dp
private val CALENDAR_LANDSCAPE_ACTION_WIDTH = 144.dp
private val CALENDAR_LANDSCAPE_ACTION_GAP = 8.dp
private val CALENDAR_PICKER_WIDTH_RESERVE = 16.dp
private const val TIME_PICKER_MIN_DENSITY_SCALE = 0.74f
private val TIME_PICKER_HEIGHT = 420.dp
private val TIME_PICKER_ACTION_AREA_HEIGHT = 92.dp
private val TIME_PICKER_LANDSCAPE_ACTION_WIDTH = 144.dp
private val TIME_PICKER_LANDSCAPE_ACTION_GAP = 8.dp
private val TIME_PICKER_DIALOG_MARGIN = 8.dp

@Composable
private fun formatReminderTimePill(
    hour24: Int,
    minute: Int,
): String = formatTimeOfDay(LocalContext.current, hour24, minute)

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
    // Slightly darker card (surfaceContainer) so the selected-day marker and grid read clearly,
    // matching the time picker's darker container.
    val datePickerContainerColor = MaterialTheme.colorScheme.surfaceContainer
    LaunchedEffect(state.displayedMonthMillis) {
        val carriedSelection =
            selectedDateMillisInDisplayedMonth(
                selectedDateMillis = state.selectedDateMillis,
                displayedMonthMillis = state.displayedMonthMillis,
            )
        if (carriedSelection != null && carriedSelection != state.selectedDateMillis) {
            state.selectedDateMillis = carriedSelection
        }
    }

    // Capture the app-capped density BEFORE opening the Dialog. A Dialog opens its own window
    // that resets LocalDensity to the raw OS density/fontScale, so reading it inside would
    // bypass the app-wide font cap and size the picker off the uncapped OS font.
    val baseDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Re-provide the app-capped density inside the Dialog window (see note above), so the
        // header/mode-toggle that sit outside the picker's own density scope stay capped too.
        val cappedDensity = baseDensity
        CompositionLocalProvider(LocalDensity provides cappedDensity) {
            // By wrapping the content in a full-screen box, we force the Android Dialog Window to
            // be full-screen. This means that when the DatePicker switches modes and changes its
            // content height, we use pure Compose `animateContentSize` on the inner Surface.
            // If we didn't do this, the Android WindowManager would try to resize the dialog window
            // mid-animation, causing severe (2-3 seconds) stuttering on many devices.
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                val landscape = isLandscape()
                val dialogWidthLimit = if (landscape) 640.dp else 432.dp
                val dialogMaxWidth = minOf(maxWidth - CALENDAR_DIALOG_MARGIN * 2, dialogWidthLimit)
                val dialogMaxHeight = (maxHeight - CALENDAR_DIALOG_MARGIN * 2).coerceAtLeast(0.dp)
                val pickerMaxWidth =
                    if (landscape) {
                        (
                            dialogMaxWidth -
                                CALENDAR_LANDSCAPE_ACTION_WIDTH -
                                CALENDAR_LANDSCAPE_ACTION_GAP
                        ).coerceAtLeast(0.dp)
                    } else {
                        dialogMaxWidth
                    }
                val widthDensityScale =
                    if (pickerMaxWidth < CALENDAR_PICKER_WIDTH + CALENDAR_PICKER_WIDTH_RESERVE) {
                        ((pickerMaxWidth - CALENDAR_PICKER_WIDTH_RESERVE) / CALENDAR_PICKER_WIDTH)
                            .coerceIn(CALENDAR_PICKER_MIN_WIDTH_DENSITY_SCALE, 1f)
                    } else {
                        1f
                    }
                val requiredCalendarHeight =
                    if (landscape) {
                        CALENDAR_PICKER_HEIGHT
                    } else {
                        CALENDAR_PICKER_HEIGHT + CALENDAR_ACTION_AREA_HEIGHT
                    }
                val availableCalendarHeight =
                    if (landscape) {
                        dialogMaxHeight
                    } else {
                        dialogMaxHeight - CALENDAR_ACTION_AREA_HEIGHT
                    }
                val heightDensityScale =
                    if (dialogMaxHeight < requiredCalendarHeight) {
                        (availableCalendarHeight / CALENDAR_PICKER_HEIGHT)
                            .coerceIn(CALENDAR_PICKER_MIN_HEIGHT_DENSITY_SCALE, 1f)
                    } else {
                        1f
                    }
                val pickerDensityScale = minOf(widthDensityScale, heightDensityScale)
                val compactCalendar = pickerDensityScale < 1f
                val pickerDensity =
                    remember(cappedDensity, pickerDensityScale) {
                        Density(
                            density = cappedDensity.density * pickerDensityScale,
                            fontScale =
                                if (compactCalendar) {
                                    cappedDensity.fontScale.coerceAtMost(0.90f)
                                } else {
                                    cappedDensity.fontScale
                                },
                        )
                    }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = datePickerContainerColor,
                    tonalElevation = 0.dp,
                    modifier =
                        Modifier
                            .padding(CALENDAR_DIALOG_MARGIN)
                            .widthIn(max = dialogWidthLimit)
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .animateContentSize(
                                animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ).clickable(
                                // Catch clicks on the surface so they don't leak to the dismiss background
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                ) {
                    MaterialTheme(
                        typography =
                            MaterialTheme.typography.copy(
                                displayLarge = MaterialTheme.typography.headlineMedium,
                                headlineLarge = MaterialTheme.typography.headlineMedium,
                            ),
                    ) {
                        if (landscape) {
                            // Only the calendar grid is compacted to fit; Today/Cancel/OK stay at
                            // the app font scale (the ambient cappedDensity) so their labels don't
                            // render tiny next to the grid.
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                        DatePicker(
                                            state = state,
                                            title = null,
                                            showModeToggle = true,
                                            modifier = Modifier.width(CALENDAR_PICKER_WIDTH),
                                            colors =
                                                DatePickerDefaults.colors(
                                                    containerColor = datePickerContainerColor,
                                                    dividerColor = Color.Transparent,
                                                ),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(CALENDAR_LANDSCAPE_ACTION_GAP))
                                Column(
                                    modifier = Modifier.width(CALENDAR_LANDSCAPE_ACTION_WIDTH),
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                ) {
                                    RememberTextButton(
                                        onClick = {
                                            val today = pickerDayMillisForLocalWallClock(System.currentTimeMillis())
                                            state.selectedDateMillis = today
                                            state.displayedMonthMillis = today
                                            state.displayMode = DisplayMode.Picker
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.reminder_date_picker_today))
                                    }
                                    RememberTextButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                    RememberTextButton(
                                        onClick = { state.selectedDateMillis?.let(onConfirm) },
                                        enabled = state.selectedDateMillis != null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.common_ok))
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                        DatePicker(
                                            state = state,
                                            title = null,
                                            showModeToggle = true,
                                            modifier = Modifier.width(CALENDAR_PICKER_WIDTH),
                                            colors =
                                                DatePickerDefaults.colors(
                                                    containerColor = datePickerContainerColor,
                                                    dividerColor = Color.Transparent,
                                                ),
                                        )
                                    }
                                }
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RememberTextButton(
                                        onClick = {
                                            val today = pickerDayMillisForLocalWallClock(System.currentTimeMillis())
                                            state.selectedDateMillis = today
                                            state.displayedMonthMillis = today
                                            state.displayMode = DisplayMode.Picker
                                        },
                                    ) {
                                        Text(stringResource(R.string.reminder_date_picker_today))
                                    }
                                    Spacer(Modifier.weight(1f))
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var showDial by remember { mutableStateOf(true) }
    // Capture the app-capped density BEFORE opening the Dialog. A Dialog opens its own window
    // that resets LocalDensity to the raw OS density/fontScale, so reading it inside would
    // bypass the app-wide font cap and size the picker off the uncapped OS font.
    val baseDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = isLandscape()
            val dialogMaxHeight = (maxHeight - TIME_PICKER_DIALOG_MARGIN * 2).coerceAtLeast(0.dp)
            val requiredTimeHeight =
                if (landscape) {
                    TIME_PICKER_HEIGHT
                } else {
                    TIME_PICKER_HEIGHT + TIME_PICKER_ACTION_AREA_HEIGHT
                }
            val availableTimeHeight =
                if (landscape) {
                    dialogMaxHeight
                } else {
                    dialogMaxHeight - TIME_PICKER_ACTION_AREA_HEIGHT
                }
            val pickerDensityScale =
                if (dialogMaxHeight < requiredTimeHeight) {
                    (availableTimeHeight / TIME_PICKER_HEIGHT)
                        .coerceIn(TIME_PICKER_MIN_DENSITY_SCALE, 1f)
                } else {
                    1f
                }
            val pickerDensity =
                remember(baseDensity, pickerDensityScale) {
                    Density(
                        density = baseDensity.density * pickerDensityScale,
                        // When compact (short landscape), cap font so the fixed-size picker fits;
                        // otherwise use the full app font scale so picker text stays close to the
                        // rest of the app instead of rendering conspicuously tiny. Same rule as
                        // CalendarPickerDialog and FilePipe's ScheduleTimePickerDialog.
                        fontScale =
                            if (pickerDensityScale < 1f) {
                                baseDensity.fontScale.coerceAtMost(0.90f)
                            } else {
                                baseDensity.fontScale
                            },
                    )
                }
            key(initialHour, initialMinute) {
                val pickerContext = LocalContext.current
                val timePickerState =
                    rememberTimePickerState(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        is24Hour = DateFormat.is24HourFormat(pickerContext),
                    )
                val pickerColors =
                    TimePickerDefaults.colors(
                        // Dial stays surfaceContainerHigh so it contrasts against the darker
                        // surfaceContainer card below.
                        clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier =
                        Modifier
                            .padding(TIME_PICKER_DIALOG_MARGIN)
                            .widthIn(max = if (landscape) 640.dp else 432.dp)
                            .fillMaxWidth()
                            .heightIn(
                                min = if (landscape) dialogMaxHeight else 0.dp,
                                max = dialogMaxHeight,
                            ).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                ) {
                    // Ambient app-capped density for the action buttons; only the dial itself is
                    // wrapped in the compact pickerDensity so it fits. This keeps the buttons at a
                    // readable app-scale size instead of shrinking them with the dial.
                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                        if (landscape) {
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                        if (showDial) {
                                            TimePicker(state = timePickerState, colors = pickerColors)
                                        } else {
                                            TimeInput(state = timePickerState, colors = pickerColors)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(TIME_PICKER_LANDSCAPE_ACTION_GAP))
                                Column(
                                    modifier = Modifier.width(TIME_PICKER_LANDSCAPE_ACTION_WIDTH),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                ) {
                                    val timeInputModeLabel = stringResource(R.string.reminder_time_input_mode)
                                    val timeDialModeLabel = stringResource(R.string.reminder_time_dial_mode)
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                Text(
                                                    text =
                                                        if (showDial) {
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
                                                modifier =
                                                    Modifier.semantics {
                                                        contentDescription = if (showDial) timeInputModeLabel else timeDialModeLabel
                                                    },
                                            )
                                        }
                                    }
                                    RememberTextButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
                                    }
                                    RememberTextButton(
                                        onClick = {
                                            onConfirm(timePickerState.hour, timePickerState.minute)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.common_save))
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f, fill = false)
                                            .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                        if (showDial) {
                                            TimePicker(state = timePickerState, colors = pickerColors)
                                        } else {
                                            TimeInput(state = timePickerState, colors = pickerColors)
                                        }
                                    }
                                }
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val timeInputModeLabel = stringResource(R.string.reminder_time_input_mode)
                                    val timeDialModeLabel = stringResource(R.string.reminder_time_dial_mode)
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                Text(
                                                    text =
                                                        if (showDial) {
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
                                                modifier =
                                                    Modifier.semantics {
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
    enabled: Boolean = true,
    compact: Boolean = false,
    singleLineLabel: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val iconColor =
        when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.52f)
            hasValue -> scheme.onPrimaryContainer
            else -> scheme.onSurfaceVariant
        }
    val labelColor =
        when {
            !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.52f)
            hasValue -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = PillHeight)
                .clip(MaterialTheme.shapes.large)
                .background(
                    // Use full-opacity primaryContainer for the activated state so the pill
                    // stays clearly distinct from the sheet background even in seed-based
                    // themes where the half-alpha version washes out against light surfaces.
                    if (!enabled) {
                        scheme.surfaceContainerHigh
                    } else if (hasValue) {
                        scheme.primaryContainer
                    } else {
                        scheme.surfaceVariant
                    },
                ).let { modifier ->
                    if (enabled) {
                        modifier.tapSoundClickable(onClick = onClick)
                    } else {
                        modifier
                    }
                }.padding(
                    start = if (compact) 14.dp else 20.dp,
                    end = if (compact) 4.dp else 8.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 20.dp,
            tint = iconColor,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = Modifier.weight(1f),
            maxLines = if (singleLineLabel) 1 else Int.MAX_VALUE,
            softWrap = !singleLineLabel,
            overflow = if (singleLineLabel) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
        // Reserve the trailing slot so rows with and without Clear match height/width.
        if (onClear != null || !compact) {
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
    val configuration = LocalConfiguration.current
    val isSmallScreenPortrait =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            configuration.screenWidthDp < 480

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // The leading Box matches the RadioButton+spacer width used by the "Ends" rows
            // below, so the "Every" digit field, the date pill, and the occurrences digit
            // field all start at the same vertical line.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = RepeatRowHeight),
            ) {
                Box(
                    modifier = Modifier.widthIn(min = RepeatLeadingPad),
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
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = RepeatRowHeight),
                    value = unitLabel(unit),
                    expanded = unitMenuOpen,
                    onExpandedChange = onUnitMenuOpen,
                ) {
                    RecurrenceUnit.entries.forEach { u ->
                        RememberDropdownMenuItem(
                            text = { Text(unitLabel(u)) },
                            onClick = {
                                onUnit(u)
                                onUnitMenuOpen(false)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            when (unit) {
                RecurrenceUnit.WEEK ->
                    WeekdayRow(selected = daysOfWeek, onToggle = { day ->
                        val next =
                            if (day in daysOfWeek) {
                                if (daysOfWeek.size == 1) daysOfWeek else daysOfWeek - day
                            } else {
                                daysOfWeek + day
                            }
                        onDaysOfWeek(next)
                    })
                RecurrenceUnit.MONTH -> {
                    RadioOption(
                        selected = monthlyKind == MonthlyKind.BY_DAY,
                        onSelect = { onMonthlyKind(MonthlyKind.BY_DAY) },
                    ) {
                        SheetDropdown(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = RepeatRowHeight),
                            value = stringResource(R.string.reminder_on_day, dayOfMonth),
                            expanded = dayOfMonthMenuOpen,
                            onExpandedChange = {
                                if (it) {
                                    onMonthlyKind(MonthlyKind.BY_DAY)
                                }
                                onDayOfMonthMenuOpen(it)
                            },
                        ) {
                            for (dayOfMonthOption in 1..31) {
                                RememberDropdownMenuItem(
                                    text = { Text(stringResource(R.string.reminder_on_day, dayOfMonthOption)) },
                                    onClick = {
                                        onDayOfMonth(dayOfMonthOption)
                                        onDayOfMonthMenuOpen(false)
                                    },
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
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = RepeatRowHeight),
                                value = ordinalLabel(nthOrdinal),
                                expanded = nthOrdinalMenuOpen,
                                onExpandedChange = {
                                    if (it) {
                                        onMonthlyKind(MonthlyKind.BY_WEEKDAY)
                                    }
                                    onNthOrdinalMenuOpen(it)
                                },
                            ) {
                                listOf(1, 2, 3, 4, 5).forEach { ordinal ->
                                    RememberDropdownMenuItem(
                                        text = { Text(ordinalLabel(ordinal)) },
                                        onClick = {
                                            onNthOrdinal(ordinal)
                                            onNthOrdinalMenuOpen(false)
                                        },
                                    )
                                }
                            }
                            SheetDropdown(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = RepeatRowHeight),
                                value =
                                    if (isSmallScreenPortrait) {
                                        weekdayShort(nthWeekday)
                                    } else {
                                        weekdayFullName(nthWeekday)
                                    },
                                expanded = nthWeekdayMenuOpen,
                                onExpandedChange = {
                                    if (it) {
                                        onMonthlyKind(MonthlyKind.BY_WEEKDAY)
                                    }
                                    onNthWeekdayMenuOpen(it)
                                },
                            ) {
                                listOf(
                                    Calendar.SUNDAY,
                                    Calendar.MONDAY,
                                    Calendar.TUESDAY,
                                    Calendar.WEDNESDAY,
                                    Calendar.THURSDAY,
                                    Calendar.FRIDAY,
                                    Calendar.SATURDAY,
                                ).forEach { wd ->
                                    RememberDropdownMenuItem(
                                        text = { Text(weekdayFullName(wd)) },
                                        onClick = {
                                            onNthWeekday(wd)
                                            onNthWeekdayMenuOpen(false)
                                        },
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
                stringResource(R.string.reminder_ends_label),
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
                    label = endDate?.let { formatDate(it) } ?: stringResource(R.string.reminder_pick_end_date),
                    hasValue = endDate != null && endKind == RecurrenceEndKind.ON_DATE,
                    onClick = {
                        onOpenEndDatePicker()
                    },
                    compact = true,
                    singleLineLabel = true,
                )
            }
            RadioOption(
                selected = endKind == RecurrenceEndKind.AFTER_COUNT,
                onSelect = { onEndKind(RecurrenceEndKind.AFTER_COUNT) },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = RepeatRowHeight),
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
    val textColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
    val outlineShape = MaterialTheme.shapes.medium
    Box(
        modifier =
            modifier
                .width(fieldWidth)
                .heightIn(min = RepeatRowHeight)
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
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = textColor,
                    textAlign = TextAlign.Center,
                ),
            modifier =
                Modifier
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RepeatRowHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.medium,
                    ).tapSoundClickable { onExpandedChange(true) }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
        modifier =
            Modifier
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
private fun WeekdayRow(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val weekdays =
        listOf(
            Calendar.SUNDAY to (stringResource(R.string.reminder_weekday_sunday_narrow) to weekdayFullName(Calendar.SUNDAY)),
            Calendar.MONDAY to (stringResource(R.string.reminder_weekday_monday_narrow) to weekdayFullName(Calendar.MONDAY)),
            Calendar.TUESDAY to (stringResource(R.string.reminder_weekday_tuesday_narrow) to weekdayFullName(Calendar.TUESDAY)),
            Calendar.WEDNESDAY to (stringResource(R.string.reminder_weekday_wednesday_narrow) to weekdayFullName(Calendar.WEDNESDAY)),
            Calendar.THURSDAY to (stringResource(R.string.reminder_weekday_thursday_narrow) to weekdayFullName(Calendar.THURSDAY)),
            Calendar.FRIDAY to (stringResource(R.string.reminder_weekday_friday_narrow) to weekdayFullName(Calendar.FRIDAY)),
            Calendar.SATURDAY to (stringResource(R.string.reminder_weekday_saturday_narrow) to weekdayFullName(Calendar.SATURDAY)),
        )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekdays.forEach { (day, labels) ->
            val (label, contentDescription) = labels
            val isSel = day in selected
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSel) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ).border(
                            width = 1.dp,
                            color =
                                if (isSel) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = CircleShape,
                        ).semantics {
                            this.contentDescription = contentDescription
                        }.tapSoundClickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (isSel) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun unitLabel(unit: RecurrenceUnit): String =
    when (unit) {
        RecurrenceUnit.HOUR -> stringResource(R.string.reminder_unit_hour)
        RecurrenceUnit.DAY -> stringResource(R.string.reminder_unit_day)
        RecurrenceUnit.WEEK -> stringResource(R.string.reminder_unit_week)
        RecurrenceUnit.MONTH -> stringResource(R.string.reminder_unit_month)
        RecurrenceUnit.YEAR -> stringResource(R.string.reminder_unit_year)
    }

@Composable
private fun ordinalLabel(ordinal: Int): String =
    when (ordinal) {
        1 -> stringResource(R.string.reminder_ordinal_first)
        2 -> stringResource(R.string.reminder_ordinal_second)
        3 -> stringResource(R.string.reminder_ordinal_third)
        4 -> stringResource(R.string.reminder_ordinal_fourth)
        5 -> stringResource(R.string.reminder_ordinal_last)
        else -> stringResource(R.string.reminder_ordinal_first)
    }

@Composable
private fun ordinalWord(ordinal: Int): String =
    when (ordinal) {
        1 -> stringResource(R.string.reminder_ordinal_first_lower)
        2 -> stringResource(R.string.reminder_ordinal_second_lower)
        3 -> stringResource(R.string.reminder_ordinal_third_lower)
        4 -> stringResource(R.string.reminder_ordinal_fourth_lower)
        5 -> stringResource(R.string.reminder_ordinal_last_lower)
        else -> stringResource(R.string.reminder_ordinal_first_lower)
    }

@Composable
private fun weekdayFullName(weekday: Int): String =
    when (weekday) {
        Calendar.SUNDAY -> stringResource(R.string.reminder_weekday_sunday)
        Calendar.MONDAY -> stringResource(R.string.reminder_weekday_monday)
        Calendar.TUESDAY -> stringResource(R.string.reminder_weekday_tuesday)
        Calendar.WEDNESDAY -> stringResource(R.string.reminder_weekday_wednesday)
        Calendar.THURSDAY -> stringResource(R.string.reminder_weekday_thursday)
        Calendar.FRIDAY -> stringResource(R.string.reminder_weekday_friday)
        Calendar.SATURDAY -> stringResource(R.string.reminder_weekday_saturday)
        else -> ""
    }

@Composable
private fun weekdayShort(weekday: Int): String =
    when (weekday) {
        Calendar.SUNDAY -> stringResource(R.string.reminder_weekday_sunday_short)
        Calendar.MONDAY -> stringResource(R.string.reminder_weekday_monday_short)
        Calendar.TUESDAY -> stringResource(R.string.reminder_weekday_tuesday_short)
        Calendar.WEDNESDAY -> stringResource(R.string.reminder_weekday_wednesday_short)
        Calendar.THURSDAY -> stringResource(R.string.reminder_weekday_thursday_short)
        Calendar.FRIDAY -> stringResource(R.string.reminder_weekday_friday_short)
        Calendar.SATURDAY -> stringResource(R.string.reminder_weekday_saturday_short)
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

private fun selectedDateMillisInDisplayedMonth(
    selectedDateMillis: Long?,
    displayedMonthMillis: Long,
): Long? {
    val selectedMillis = selectedDateMillis ?: return null
    val selectedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val displayedMonth = Instant.ofEpochMilli(displayedMonthMillis).atZone(ZoneOffset.UTC).toLocalDate()
    if (selectedDate.year == displayedMonth.year && selectedDate.month == displayedMonth.month) {
        return selectedMillis
    }
    val carriedDay = minOf(selectedDate.dayOfMonth, displayedMonth.lengthOfMonth())
    return displayedMonth
        .withDayOfMonth(carriedDay)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
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

@Composable
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
    val every =
        if (interval == 1) {
            when (unit) {
                RecurrenceUnit.HOUR -> stringResource(R.string.reminder_recurrence_hourly)
                RecurrenceUnit.DAY -> stringResource(R.string.reminder_recurrence_daily)
                RecurrenceUnit.WEEK -> stringResource(R.string.reminder_recurrence_weekly)
                RecurrenceUnit.MONTH -> stringResource(R.string.reminder_recurrence_monthly)
                RecurrenceUnit.YEAR -> stringResource(R.string.reminder_recurrence_yearly)
            }
        } else {
            pluralStringResource(
                when (unit) {
                    RecurrenceUnit.HOUR -> R.plurals.reminder_recurrence_every_hours
                    RecurrenceUnit.DAY -> R.plurals.reminder_recurrence_every_days
                    RecurrenceUnit.WEEK -> R.plurals.reminder_recurrence_every_weeks
                    RecurrenceUnit.MONTH -> R.plurals.reminder_recurrence_every_months
                    RecurrenceUnit.YEAR -> R.plurals.reminder_recurrence_every_years
                },
                interval,
                interval,
            )
        }
    val detail =
        when (unit) {
            RecurrenceUnit.WEEK ->
                if (daysOfWeek.size in 1..6) {
                    val weekdayNames = StringBuilder()
                    daysOfWeek.sorted().forEach { weekday ->
                        if (weekdayNames.isNotEmpty()) {
                            weekdayNames.append(", ")
                        }
                        weekdayNames.append(weekdayShort(weekday))
                    }
                    stringResource(
                        R.string.reminder_recurrence_on_weekdays,
                        weekdayNames.toString(),
                    )
                } else {
                    ""
                }
            RecurrenceUnit.MONTH ->
                when (monthlyKind) {
                    MonthlyKind.BY_DAY -> stringResource(R.string.reminder_recurrence_on_day, dayOfMonth)
                    MonthlyKind.BY_WEEKDAY ->
                        stringResource(
                            R.string.reminder_recurrence_on_ordinal_weekday,
                            ordinalWord(nthOrdinal),
                            weekdayShort(nthWeekday),
                        )
                }
            else -> ""
        }
    val ending =
        when (endKind) {
            RecurrenceEndKind.NEVER -> ""
            RecurrenceEndKind.ON_DATE ->
                endDate?.let { date -> stringResource(R.string.reminder_recurrence_until, formatDate(date)) }.orEmpty()
            RecurrenceEndKind.AFTER_COUNT ->
                endCount
                    ?.let { count ->
                        pluralStringResource(R.plurals.reminder_recurrence_for_times, count, count)
                    }.orEmpty()
        }
    return listOf(every, detail, ending).filter { part -> part.isNotEmpty() }.joinToString(" ")
}

private sealed interface SnoozeType {
    data class Today(
        val time: String,
    ) : SnoozeType

    data class Tomorrow(
        val time: String,
    ) : SnoozeType

    data class Future(
        val date: String,
        val time: String,
    ) : SnoozeType
}

@Composable
private fun SnoozeIndicator(snoozedUntil: Long) {
    val context = LocalContext.current
    val snoozeType =
        remember(snoozedUntil, context) {
            val zone = java.time.ZoneId.systemDefault()
            val target = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(snoozedUntil), zone)
            val now = java.time.ZonedDateTime.now(zone)
            val timeFormatter = timeFormatterFor(context)
            val timeStr = target.format(timeFormatter)
            val targetDate = target.toLocalDate()
            val today = now.toLocalDate()
            when (targetDate) {
                today -> SnoozeType.Today(timeStr)
                today.plusDays(1) -> SnoozeType.Tomorrow(timeStr)
                else -> {
                    val dayPattern =
                        java.time.format.DateTimeFormatter
                            .ofPattern("EEE, MMM d", Locale.getDefault())
                    SnoozeType.Future(target.format(dayPattern), timeStr)
                }
            }
        }
    val text =
        when (snoozeType) {
            is SnoozeType.Today -> stringResource(R.string.snooze_indicator_today, snoozeType.time)
            is SnoozeType.Tomorrow -> stringResource(R.string.snooze_indicator_tomorrow, snoozeType.time)
            is SnoozeType.Future -> stringResource(R.string.snooze_indicator_future, snoozeType.date, snoozeType.time)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = "snooze",
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun timeFormatterFor(context: Context): java.time.format.DateTimeFormatter {
    val pattern =
        if (android.text.format.DateFormat
                .is24HourFormat(context)
        ) {
            "HH:mm"
        } else {
            "h:mm a"
        }
    return java.time.format.DateTimeFormatter
        .ofPattern(pattern, Locale.getDefault())
}
