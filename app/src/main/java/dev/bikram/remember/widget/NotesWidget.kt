package dev.bikram.remember.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ViewOptions
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.di.NotesWidgetEntryPoint
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.NoteIcon
import dev.bikram.remember.ui.edit.resolveNoteIcon
import dev.bikram.remember.ui.home.sortNotes
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Agenda widget for reminders due now or soon.
 *
 * Existing installed Remember widgets keep this receiver/provider and therefore upgrade
 * from the old mixed Starred + Reminder Summary surface into the richer daily view.
 */
@Composable
private fun Int.scaledSp(): TextUnit {
    val context = LocalContext.current
    val fontScale =
        context.resources.configuration.fontScale
            .coerceAtLeast(0.01f)
    val cappedScale = fontScale.coerceAtMost(1.3f)
    return (this * (cappedScale / fontScale)).sp
}

internal fun quickCaptureNeedsCondensedCopy(
    widgetWidthDp: Float,
    density: Float,
    fontScale: Float,
    title: String,
    trailingText: String,
    measureTextWidthPx: (String, Float) -> Float = ::measureWidgetTextWidthPx,
): Boolean {
    val contentWidthPx = (widgetWidthDp - QUICK_CAPTURE_HORIZONTAL_PADDING_DP * 2) * density
    val cappedScale = fontScale.coerceAtLeast(0.01f).coerceAtMost(1.3f)
    val titleWidthPx = measureTextWidthPx(title, QUICK_CAPTURE_HEADER_TITLE_SP * cappedScale * density)
    val trailingWidthPx = measureTextWidthPx(trailingText, QUICK_CAPTURE_HEADER_TRAILING_SP * cappedScale * density)
    return titleWidthPx + trailingWidthPx + QUICK_CAPTURE_HEADER_FIT_BUFFER_DP * density > contentWidthPx
}

private fun measureWidgetTextWidthPx(
    text: String,
    textSizePx: Float,
): Float {
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSizePx
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    return paint.measureText(text)
}

class NotesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(width = 180.dp, height = 110.dp),
                DpSize(width = 250.dp, height = 110.dp),
            ),
        )

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val noteRepository = widgetEntryPoint(context).noteRepository()
        val initialNotes = noteRepository.observeActive().first()
        provideContent {
            GlanceTheme {
                val activeNotesFlow = remember(noteRepository) { noteRepository.observeActive() }
                val activeNotes by activeNotesFlow.collectAsState(initial = initialNotes)
                val now = System.currentTimeMillis()
                val agenda = agendaWidgetItems(activeNotes, now)
                AgendaWidgetContent(overdue = agenda.overdue, upcoming = agenda.upcoming, now = now)
            }
        }
    }
}

class QuickCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val noteRepository = widgetEntryPoint(context).noteRepository()
        val initialNotes = noteRepository.observeActive().first()
        provideContent {
            GlanceTheme {
                val activeNotesFlow = remember(noteRepository) { noteRepository.observeActive() }
                val activeNotes by activeNotesFlow.collectAsState(initial = initialNotes)
                val now = System.currentTimeMillis()
                val counts = quickCaptureWidgetCounts(activeNotes, now)
                QuickCaptureContent(overdueCount = counts.overdueCount, dueTodayCount = counts.dueTodayCount)
            }
        }
    }
}

class StarredWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(width = 180.dp, height = 110.dp),
                DpSize(width = 250.dp, height = 110.dp),
            ),
        )

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = widgetEntryPoint(context)
        val noteRepository = entryPoint.noteRepository()
        val viewOptionsPrefs = entryPoint.viewOptionsPrefs()
        val initialNotes = noteRepository.observeActive().first()
        val initialViewOptions = viewOptionsPrefs.state.first()
        provideContent {
            GlanceTheme {
                val activeNotesFlow = remember(noteRepository) { noteRepository.observeActive() }
                val activeNotes by activeNotesFlow.collectAsState(initial = initialNotes)
                val viewOptionsFlow = remember(viewOptionsPrefs) { viewOptionsPrefs.state }
                val viewOptions by viewOptionsFlow.collectAsState(initial = initialViewOptions)
                val starred = starredWidgetItems(activeNotes, viewOptions)
                StarredWidgetContent(starred = starred)
            }
        }
    }
}

@Keep
class WidgetMarkDoneAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val noteId = parameters[WidgetNoteIdKey] ?: return
        widgetEntryPoint(context).noteRepository().markCompleted(noteId)
    }
}

@Keep
class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val widgetKind = parameters[WidgetKindKey] ?: WIDGET_KIND_AGENDA
        when (widgetKind) {
            WIDGET_KIND_QUICK_CAPTURE -> QuickCaptureWidget().update(context, glanceId)
            WIDGET_KIND_STARRED -> StarredWidget().update(context, glanceId)
            else -> NotesWidget().update(context, glanceId)
        }
    }
}

@Composable
private fun AgendaWidgetContent(
    overdue: List<NoteWithItems>,
    upcoming: List<NoteWithItems>,
    now: Long,
) {
    val context = LocalContext.current
    val compact = LocalSize.current.width < 220.dp
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(if (compact) 10.dp else 12.dp),
    ) {
        WidgetHeader(
            compact = compact,
            showActions = true,
            widgetKind = WIDGET_KIND_AGENDA,
        )
        Spacer(GlanceModifier.height(if (compact) 6.dp else 8.dp))
        if (overdue.isEmpty() && upcoming.isEmpty()) {
            AgendaEmptyState()
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                if (overdue.isNotEmpty() || upcoming.isNotEmpty()) {
                    item { SectionHeader(context.getString(R.string.widget_section_overdue), strong = true) }
                    if (overdue.isEmpty()) {
                        item { InlineEmptyState(context.getString(R.string.widget_empty_nothing_overdue)) }
                    } else {
                        items(count = overdue.size) { index ->
                            Column {
                                ReminderCard(
                                    note = overdue[index],
                                    context = context,
                                    now = now,
                                    overdue = true,
                                    compact = compact,
                                )
                                if (index != overdue.lastIndex) {
                                    Spacer(GlanceModifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
                if (upcoming.isNotEmpty() || overdue.isNotEmpty()) {
                    item { Spacer(GlanceModifier.height(8.dp)) }
                }
                if (upcoming.isNotEmpty() || overdue.isNotEmpty()) {
                    item { SectionHeader(context.getString(R.string.widget_section_upcoming), strong = false) }
                    if (upcoming.isEmpty()) {
                        item { InlineEmptyState(context.getString(R.string.widget_empty_nothing_upcoming)) }
                    } else {
                        items(count = upcoming.size) { index ->
                            Column {
                                ReminderCard(
                                    note = upcoming[index],
                                    context = context,
                                    now = now,
                                    overdue = false,
                                    compact = compact,
                                )
                                if (index != upcoming.lastIndex) {
                                    Spacer(GlanceModifier.height(6.dp))
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
private fun StarredWidgetContent(starred: List<NoteWithItems>) {
    val context = LocalContext.current
    val compact = LocalSize.current.width < 220.dp
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(if (compact) 10.dp else 12.dp),
    ) {
        WidgetHeader(
            title = context.getString(R.string.widget_starred_title),
            compact = compact,
            showActions = true,
            widgetKind = WIDGET_KIND_STARRED,
        )
        Spacer(GlanceModifier.height(if (compact) 6.dp else 8.dp))
        if (starred.isEmpty()) {
            StarredEmptyState()
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(count = starred.size) { index ->
                    Column {
                        StarredCard(
                            note = starred[index],
                            context = context,
                            compact = compact,
                        )
                        if (index != starred.lastIndex) {
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCaptureContent(
    overdueCount: Int,
    dueTodayCount: Int,
) {
    val context = LocalContext.current
    val fullStatusText = quickCaptureStatus(context, overdueCount, dueTodayCount)
    val useCondensedCopy =
        quickCaptureNeedsCondensedCopy(
            widgetWidthDp = LocalSize.current.width.value,
            density = context.resources.displayMetrics.density,
            fontScale = context.resources.configuration.fontScale,
            title = context.getString(R.string.widget_header_title),
            trailingText = fullStatusText,
        )
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp),
    ) {
        WidgetHeader(
            compact = true,
            showActions = false,
            trailingText =
                if (useCondensedCopy && overdueCount == 0 && dueTodayCount == 0) {
                    context.getString(R.string.widget_empty_no_due)
                } else {
                    fullStatusText
                },
            widgetKind = WIDGET_KIND_QUICK_CAPTURE,
        )
        Spacer(GlanceModifier.height(10.dp))
        QuickCaptureButton(
            label =
                context.getString(
                    if (useCondensedCopy) {
                        R.string.widget_new_note
                    } else {
                        R.string.widget_create_new_note
                    },
                ),
            imageProvider = ImageProvider(R.drawable.ic_widget_note_add),
            intent = newNoteIntent(context),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.height(8.dp))
        QuickCaptureButton(
            label =
                context.getString(
                    if (useCondensedCopy) {
                        R.string.widget_new_list
                    } else {
                        R.string.widget_create_new_list
                    },
                ),
            imageProvider = ImageProvider(R.drawable.ic_widget_list_add),
            intent = newListIntent(context),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun WidgetHeader(
    title: String? = null,
    compact: Boolean,
    showActions: Boolean,
    trailingText: String? = null,
    widgetKind: String,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title ?: context.getString(R.string.widget_header_title),
            style =
                TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = if (compact) 13.scaledSp() else 14.scaledSp(),
                    fontWeight = FontWeight.Medium,
                ),
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openNotesIntent(context))),
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
        if (showActions) {
            RefreshHeaderAction(
                compact = compact,
                widgetKind = widgetKind,
            )
            Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
            WidgetIconButton(
                provider = ImageProvider(R.drawable.ic_widget_note_add),
                contentDescription = context.getString(R.string.widget_create_new_note),
                action = actionStartActivity(newNoteIntent(context)),
                compact = compact,
            )
            Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
            WidgetIconButton(
                provider = ImageProvider(R.drawable.ic_widget_list_add),
                contentDescription = context.getString(R.string.widget_create_new_list),
                action = actionStartActivity(newListIntent(context)),
                compact = compact,
            )
        }
    }
}

@Composable
private fun RefreshHeaderAction(
    compact: Boolean,
    widgetKind: String,
) {
    val context = LocalContext.current
    WidgetIconButton(
        provider = ImageProvider(R.drawable.ic_widget_refresh),
        contentDescription = context.getString(R.string.widget_refresh_cd),
        action =
            actionRunCallback<WidgetRefreshAction>(
                actionParametersOf(WidgetKindKey to widgetKind),
            ),
        compact = compact,
    )
}

@Composable
private fun WidgetIconButton(
    provider: ImageProvider,
    contentDescription: String,
    action: Action,
    compact: Boolean,
) {
    CircleIconButton(
        imageProvider = provider,
        contentDescription = contentDescription,
        onClick = action,
        modifier =
            GlanceModifier
                .size(if (compact) 26.dp else 28.dp),
        backgroundColor = GlanceTheme.colors.primaryContainer,
        contentColor = GlanceTheme.colors.onPrimaryContainer,
    )
}

@Composable
private fun QuickCaptureButton(
    label: String,
    imageProvider: ImageProvider,
    intent: Intent,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(18.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = imageProvider,
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = label,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 14.scaledSp(),
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

@Composable
private fun SectionHeader(
    label: String,
    strong: Boolean,
) {
    Text(
        text = label,
        style =
            TextStyle(
                color = if (strong) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                fontSize = 11.scaledSp(),
                fontWeight = FontWeight.Medium,
            ),
        modifier = GlanceModifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun InlineEmptyState(label: String) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.scaledSp(),
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

@Composable
private fun AgendaEmptyState() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_empty_watermark),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primaryContainer,
                        fontSize = 34.scaledSp(),
                    ),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_empty_nothing_due),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

@Composable
private fun StarredEmptyState() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_empty_starred_watermark),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primaryContainer,
                        fontSize = 34.scaledSp(),
                    ),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_empty_starred),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}

@Composable
private fun ReminderCard(
    note: NoteWithItems,
    context: Context,
    now: Long,
    overdue: Boolean,
    compact: Boolean,
) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(if (overdue) GlanceTheme.colors.errorContainer else GlanceTheme.colors.surface)
                .cornerRadius(18.dp)
                .clickable(actionStartActivity(openNoteIntent(context, note.note.id)))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 8.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(if (compact) 28.dp else 32.dp)
                    .cornerRadius(16.dp)
                    .background(if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            NoteWidgetIcon(
                note = note,
                tint = if (overdue) GlanceTheme.colors.onError else GlanceTheme.colors.primary,
            )
        }
        Spacer(GlanceModifier.width(if (compact) 8.dp else 10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = noteWidgetTitle(context, note),
                maxLines = 1,
                style =
                    TextStyle(
                        color = if (overdue) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSurface,
                        fontSize = if (compact) 13.scaledSp() else 14.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
            note.note.reminderAt?.let { reminderAt ->
                Text(
                    text = summaryTimingLabel(context, reminderAt, now),
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                            fontSize = 11.scaledSp(),
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
            val subline = noteWidgetSubline(note)
            if (subline.isNotBlank() && !compact) {
                Text(
                    text = subline,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = if (overdue) GlanceTheme.colors.onErrorContainer else GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.scaledSp(),
                        ),
                )
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        WidgetIconButton(
            provider = ImageProvider(R.drawable.ic_widget_done),
            contentDescription = context.getString(R.string.widget_mark_done_cd),
            action =
                actionRunCallback<WidgetMarkDoneAction>(
                    actionParametersOf(WidgetNoteIdKey to note.note.id),
                ),
            compact = compact,
        )
    }
}

@Composable
private fun StarredCard(
    note: NoteWithItems,
    context: Context,
    compact: Boolean,
) {
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(18.dp)
                .clickable(actionStartActivity(openNoteIntent(context, note.note.id)))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 8.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(if (compact) 28.dp else 32.dp)
                    .cornerRadius(16.dp)
                    .background(GlanceTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            NoteWidgetIcon(
                note = note,
                tint = GlanceTheme.colors.primary,
            )
        }
        Spacer(GlanceModifier.width(if (compact) 8.dp else 10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = noteWidgetTitle(context, note),
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (compact) 13.scaledSp() else 14.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
            val subline = noteWidgetSubline(note)
            if (subline.isNotBlank()) {
                Text(
                    text = subline,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = if (compact) 11.scaledSp() else 12.scaledSp(),
                        ),
                )
            }
        }
    }
}

private fun widgetEntryPoint(context: Context): NotesWidgetEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        NotesWidgetEntryPoint::class.java,
    )

private data class AgendaWidgetItems(
    val overdue: List<NoteWithItems>,
    val upcoming: List<NoteWithItems>,
)

private data class QuickCaptureWidgetCounts(
    val overdueCount: Int,
    val dueTodayCount: Int,
)

private fun agendaWidgetItems(
    notes: List<NoteWithItems>,
    now: Long,
): AgendaWidgetItems {
    val reminderItems =
        notes
            .asSequence()
            .filter { it.note.completedAt == null }
            .filter { it.note.visibility != Visibility.SECRET }
            .filter { it.note.reminderAt != null }
            .filter { noteWithItems ->
                val reminderAt = noteWithItems.note.reminderAt ?: return@filter false
                reminderAt <= now + UPCOMING_WINDOW_MILLIS
            }.sortedBy { it.note.reminderAt }
            .toList()
    return AgendaWidgetItems(
        overdue = reminderItems.filter { (it.note.reminderAt ?: Long.MAX_VALUE) < now },
        upcoming =
            reminderItems.filter { noteWithItems ->
                val reminderAt = noteWithItems.note.reminderAt ?: return@filter false
                reminderAt >= now
            },
    )
}

private fun quickCaptureWidgetCounts(
    notes: List<NoteWithItems>,
    now: Long,
): QuickCaptureWidgetCounts {
    val reminderItems =
        notes
            .asSequence()
            .filter { it.note.completedAt == null }
            .filter { it.note.visibility != Visibility.SECRET }
            .filter { it.note.reminderAt != null }
            .toList()
    return QuickCaptureWidgetCounts(
        overdueCount = reminderItems.count { (it.note.reminderAt ?: Long.MAX_VALUE) < now },
        dueTodayCount =
            reminderItems.count { noteWithItems ->
                val reminderAt = noteWithItems.note.reminderAt ?: return@count false
                reminderAt >= now && reminderAt < startOfTomorrow(now)
            },
    )
}

private fun starredWidgetItems(
    notes: List<NoteWithItems>,
    viewOptions: ViewOptions,
): List<NoteWithItems> =
    sortNotes(
        notes =
            notes
                .filter { it.note.visibility != Visibility.SECRET }
                .filter { it.note.starred },
        opts = viewOptions,
    )

private fun openNotesIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = "remember://widget/notes".toUri()
        addWidgetLaunchFlags()
    }

private fun newNoteIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_SHORTCUT_NEW_NOTE
        data = "remember://widget/new-note".toUri()
        addWidgetLaunchFlags()
    }

private fun newListIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_SHORTCUT_NEW_LIST
        data = "remember://widget/new-list".toUri()
        addWidgetLaunchFlags()
    }

private fun openNoteIntent(
    context: Context,
    noteId: Long,
): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = "remember://widget/open/$noteId".toUri()
        putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
        putExtra(MainActivity.EXTRA_OPEN_NOTE_EXIT_ON_BACK, true)
        addWidgetLaunchFlags(clearTask = true)
    }

private fun Intent.addWidgetLaunchFlags(clearTask: Boolean = false) {
    var flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    if (clearTask) {
        flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    addFlags(flags)
}

@Composable
private fun NoteWidgetIcon(
    note: NoteWithItems,
    tint: ColorProvider,
) {
    val context = LocalContext.current
    when (val icon = resolveNoteIcon(note.note.iconKey, note.note.kind)) {
        is NoteIcon.Emoji ->
            Text(
                text = icon.text,
                style =
                    TextStyle(
                        color = tint,
                        fontSize = 13.scaledSp(),
                        fontWeight = FontWeight.Medium,
                    ),
            )
        is NoteIcon.Drawable ->
            Image(
                provider = ImageProvider(icon.resId),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(tint),
            )
        is NoteIcon.Symbol ->
            MaterialSymbolWidgetImage(
                context = context,
                name = icon.name,
                filled = icon.filled,
                tint = tint,
            )
        NoteIcon.ListPlaceholder ->
            MaterialSymbolWidgetImage(
                context = context,
                name = DEFAULT_LIST_HEADER_SYMBOL,
                filled = true,
                tint = tint,
            )
        NoteIcon.NotePlaceholder ->
            MaterialSymbolWidgetImage(
                context = context,
                name = DEFAULT_NOTE_HEADER_SYMBOL,
                filled = true,
                tint = tint,
            )
    }
}

@Composable
private fun MaterialSymbolWidgetImage(
    context: Context,
    name: String,
    filled: Boolean,
    tint: ColorProvider,
) {
    val appContext = context.applicationContext
    val bitmap =
        remember(appContext, name, filled) {
            materialSymbolBitmap(
                context = appContext,
                name = name,
                filled = filled,
            )
        }
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    } else {
        Text(
            text = "\u2022",
            style =
                TextStyle(
                    color = tint,
                    fontSize = 13.scaledSp(),
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

private fun materialSymbolBitmap(
    context: Context,
    name: String,
    filled: Boolean,
): Bitmap? {
    val typeface =
        ResourcesCompat.getFont(
            context,
            if (filled) R.font.material_symbols_rounded else R.font.material_symbols_rounded_outlined,
        ) ?: return null
    return materialSymbolBitmap(
        name = name,
        typeface = typeface,
    )
}

private fun materialSymbolBitmap(
    name: String,
    typeface: Typeface,
): Bitmap {
    val source = createBitmap(WIDGET_SYMBOL_SOURCE_BITMAP_SIZE_PX, WIDGET_SYMBOL_SOURCE_BITMAP_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(source)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = WIDGET_SYMBOL_TEXT_SIZE_PX
            this.typeface = typeface
            fontFeatureSettings = "\"rlig\" 1, \"liga\" 1"
        }
    val baseline =
        (WIDGET_SYMBOL_SOURCE_BITMAP_SIZE_PX / 2f) -
            ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
    canvas.drawText(name, WIDGET_SYMBOL_SOURCE_BITMAP_SIZE_PX / 2f, baseline, paint)
    val bounds = source.nonTransparentBounds() ?: return source
    val output = createBitmap(WIDGET_SYMBOL_BITMAP_SIZE_PX, WIDGET_SYMBOL_BITMAP_SIZE_PX, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        source,
        bounds,
        RectF(
            WIDGET_SYMBOL_OUTPUT_PADDING_PX,
            WIDGET_SYMBOL_OUTPUT_PADDING_PX,
            WIDGET_SYMBOL_BITMAP_SIZE_PX - WIDGET_SYMBOL_OUTPUT_PADDING_PX,
            WIDGET_SYMBOL_BITMAP_SIZE_PX - WIDGET_SYMBOL_OUTPUT_PADDING_PX,
        ),
        null,
    )
    source.recycle()
    return output
}

private fun Bitmap.nonTransparentBounds(): Rect? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((this[x, y] ushr 24) != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    return if (right >= left && bottom >= top) {
        Rect(left, top, right + 1, bottom + 1)
    } else {
        null
    }
}

private fun noteWidgetTitle(
    context: Context,
    note: NoteWithItems,
): String =
    note.note.title.ifBlank {
        if (note.note.kind == NoteKind.NOTE) {
            context.getString(R.string.edit_note_title_new)
        } else {
            context.getString(R.string.edit_list_title_new)
        }
    }

private fun noteWidgetSubline(note: NoteWithItems): String =
    if (note.note.visibility == Visibility.DEFAULT) {
        note.note.body.toWidgetPlainText().ifBlank {
            note.items
                .firstOrNull()
                ?.text
                ?.toWidgetPlainText()
                .orEmpty()
        }
    } else {
        ""
    }

private fun String.toWidgetPlainText(): String = widgetPlainText(this)

internal fun widgetPlainText(markdown: String): String =
    markdown
        .lineSequence()
        .filterNot { line ->
            widgetMarkdownCodeFenceRegex.matches(line) ||
                // A horizontal rule carries no text, so it must not become the widget's one-line
                // preview of the note.
                widgetMarkdownHorizontalRuleRegex.matches(line)
        }.map { line ->
            line
                .replace(widgetMarkdownHeadingRegex, "")
                .replace(widgetMarkdownChecklistRegex, "")
                .replace(widgetMarkdownBulletRegex, "")
                .replace(widgetMarkdownQuoteRegex, "")
                .replace(widgetMarkdownLinkRegex, "$1")
                .replace(widgetMarkdownInlineCodeRegex, "$1")
                .replace(widgetMarkdownBoldItalicRegex, "$1")
                .replace(widgetMarkdownBoldRegex, "$1")
                .replace(widgetMarkdownItalicRegex, "$1")
                .replace(widgetMarkdownStrikeRegex, "$1")
                .replace(widgetMarkdownUnderlineOpenRegex, "")
                .replace(widgetMarkdownUnderlineCloseRegex, "")
                .trim()
        }.firstOrNull { line -> line.isNotBlank() }
        .orEmpty()

private fun quickCaptureStatus(
    context: Context,
    overdueCount: Int,
    dueTodayCount: Int,
): String =
    when {
        overdueCount > 0 ->
            context.resources.getQuantityString(
                R.plurals.widget_status_overdue,
                overdueCount,
                overdueCount,
            )
        dueTodayCount > 0 ->
            context.resources.getQuantityString(
                R.plurals.widget_status_due_today,
                dueTodayCount,
                dueTodayCount,
            )
        else -> context.getString(R.string.widget_empty_nothing_due)
    }

private fun summaryTimingLabel(
    context: Context,
    reminderAt: Long,
    now: Long,
): String {
    val todayStart = startOfDay(now)
    val tomorrowStart = startOfTomorrow(now)
    return when {
        reminderAt < todayStart -> {
            val overdueDays = daysBetween(startOfDay(reminderAt), todayStart).coerceAtLeast(1)
            context.resources.getQuantityString(
                R.plurals.reminder_summary_overdue_days,
                overdueDays,
                overdueDays,
            )
        }
        reminderAt < tomorrowStart -> context.getString(R.string.reminder_summary_due_today)
        reminderAt - now < HOUR_MILLIS * 24 -> {
            val hoursUntil =
                ((reminderAt - now + HOUR_MILLIS - 1) / HOUR_MILLIS)
                    .coerceAtLeast(1)
                    .toInt()
            context.resources.getQuantityString(
                R.plurals.reminder_summary_in_hours,
                hoursUntil,
                hoursUntil,
            )
        }
        else -> {
            val daysUntil = daysBetween(todayStart, startOfDay(reminderAt)).coerceAtLeast(1)
            context.resources.getQuantityString(
                R.plurals.reminder_summary_in_days,
                daysUntil,
                daysUntil,
            )
        }
    }
}

private fun startOfDay(millis: Long): Long {
    val calendar =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return calendar.timeInMillis
}

private fun startOfTomorrow(now: Long): Long {
    val calendar =
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            add(Calendar.DAY_OF_MONTH, 1)
        }
    return calendar.timeInMillis
}

private fun daysBetween(
    startMillis: Long,
    endMillis: Long,
): Int = ((endMillis - startMillis) / DAY_MILLIS).toInt()

private val WidgetNoteIdKey = ActionParameters.Key<Long>("note_id")
private val WidgetKindKey = ActionParameters.Key<String>("widget_kind")
private val widgetMarkdownHeadingRegex = Regex("""^\s*#{1,6}\s+""")
private val widgetMarkdownChecklistRegex = Regex("""^\s*[-*+]\s+\[[ xX]\]\s+""")
private val widgetMarkdownBulletRegex = Regex("""^\s*[-*+]\s+""")
private val widgetMarkdownQuoteRegex = Regex("""^\s*>\s?""")
private val widgetMarkdownCodeFenceRegex = Regex("""^\s*```.*$""")
private val widgetMarkdownHorizontalRuleRegex = Regex("""^ {0,3}-{3,}[ \t]*$""")
private val widgetMarkdownLinkRegex = Regex("""\[([^]]+)]\([^)]+\)""")
private val widgetMarkdownInlineCodeRegex = Regex("""`([^`]+)`""")
private val widgetMarkdownBoldItalicRegex = Regex("""\*\*\*(.+?)\*\*\*""")
private val widgetMarkdownBoldRegex = Regex("""\*\*(.+?)\*\*""")
private val widgetMarkdownItalicRegex = Regex("""(?<!\*)\*(?!\s)(.+?)(?<!\s)\*(?!\*)""")
private val widgetMarkdownStrikeRegex = Regex("""~~(.+?)~~""")
private val widgetMarkdownUnderlineOpenRegex = Regex("""<u>""", RegexOption.IGNORE_CASE)
private val widgetMarkdownUnderlineCloseRegex = Regex("""</u>""", RegexOption.IGNORE_CASE)

private const val HOUR_MILLIS = 60L * 60L * 1000L
private const val DAY_MILLIS = 24L * HOUR_MILLIS
private const val UPCOMING_WINDOW_MILLIS = 7L * DAY_MILLIS
private const val WIDGET_KIND_AGENDA = "agenda"
private const val WIDGET_KIND_QUICK_CAPTURE = "quick_capture"
private const val WIDGET_KIND_STARRED = "starred"
private const val WIDGET_SYMBOL_BITMAP_SIZE_PX = 64
private const val WIDGET_SYMBOL_SOURCE_BITMAP_SIZE_PX = 192
private const val WIDGET_SYMBOL_TEXT_SIZE_PX = 144f
private const val WIDGET_SYMBOL_OUTPUT_PADDING_PX = 4f
private const val QUICK_CAPTURE_HORIZONTAL_PADDING_DP = 12f
private const val QUICK_CAPTURE_HEADER_TITLE_SP = 13
private const val QUICK_CAPTURE_HEADER_TRAILING_SP = 11
private const val QUICK_CAPTURE_HEADER_FIT_BUFFER_DP = 4f
