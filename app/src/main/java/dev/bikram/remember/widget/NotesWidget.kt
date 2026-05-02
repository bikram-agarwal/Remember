package dev.bikram.remember.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
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
import dagger.hilt.android.EntryPointAccessors
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.di.NotesWidgetEntryPoint
import dev.bikram.remember.ui.edit.iconEmojiPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Two-section Glance widget:
 *   - Favorites (active favorite notes, up to [MAX_FAVORITE_ROWS])
 *   - Reminder summary (same overdue/upcoming window as the persistent summary notification)
 *
 * Tapping a row opens that note; the header actions create a new note or list.
 */
class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                NotesWidgetEntryPoint::class.java,
            )
        val noteRepository = entryPoint.noteRepository()
        val now = System.currentTimeMillis()
        val activeAndReminderSummary =
            coroutineScope {
                val activeNotesDeferred = async { noteRepository.observeActive().first() }
                val reminderSummaryDeferred =
                    async {
                        noteRepository
                            .reminderSummaryItems(now)
                            .take(MAX_REMINDER_SUMMARY_ROWS)
                    }
                activeNotesDeferred.await() to reminderSummaryDeferred.await()
            }
        val favorites =
            activeAndReminderSummary
                .first
                .asSequence()
                .filter { it.note.favorite }
                .take(MAX_FAVORITE_ROWS)
                .toList()
        provideContent {
            GlanceTheme {
                WidgetContent(
                    favorites = favorites,
                    reminderSummary = activeAndReminderSummary.second,
                    now = now,
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        favorites: List<NoteWithItems>,
        reminderSummary: List<NoteWithItems>,
        now: Long,
    ) {
        val context = LocalContext.current
        val newNoteIntent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHORTCUT_NEW_NOTE
                data = Uri.parse("remember://widget/new-note")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val newListIntent =
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_SHORTCUT_NEW_LIST
                data = Uri.parse("remember://widget/new-list")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(20.dp)
                    .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_header_title),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_note_add),
                    contentDescription = context.getString(R.string.widget_create_new_note),
                    modifier =
                        GlanceModifier
                            .size(28.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(14.dp)
                            .padding(4.dp)
                            .clickable(actionStartActivity(newNoteIntent)),
                )
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_list_add),
                    contentDescription = context.getString(R.string.widget_create_new_list),
                    modifier =
                        GlanceModifier
                            .size(28.dp)
                            .background(GlanceTheme.colors.primaryContainer)
                            .cornerRadius(14.dp)
                            .padding(4.dp)
                            .clickable(actionStartActivity(newListIntent)),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            // Single LazyColumn so both sections scroll together when the widget is tall.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                item { SectionHeader(context.getString(R.string.widget_section_pinned)) }
                if (favorites.isEmpty()) {
                    item { SectionEmpty(context.getString(R.string.widget_section_empty_pinned)) }
                } else {
                    items(count = favorites.size) { i ->
                        NotificationPreviewRow(note = favorites[i], context = context, showReminder = false)
                    }
                }
                item { Spacer(GlanceModifier.height(8.dp)) }
                item { SectionHeader(context.getString(R.string.widget_section_reminders)) }
                if (reminderSummary.isEmpty()) {
                    item {
                        SectionEmpty(context.getString(R.string.widget_section_empty_reminders))
                    }
                } else {
                    items(count = reminderSummary.size) { index ->
                        NotificationPreviewRow(
                            note = reminderSummary[index],
                            context = context,
                            showReminder = true,
                            now = now,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(label: String) {
        Text(
            text = label,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            modifier = GlanceModifier.padding(vertical = 4.dp),
        )
    }

    @Composable
    private fun SectionEmpty(label: String) {
        Text(
            text = label,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            modifier = GlanceModifier.padding(vertical = 4.dp),
        )
    }

    @Composable
    private fun NotificationPreviewRow(
        note: NoteWithItems,
        context: Context,
        showReminder: Boolean,
        now: Long = System.currentTimeMillis(),
    ) {
        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("remember://widget/open/${note.note.id}")
                putExtra("open_note_id", note.note.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .clickable(actionStartActivity(openIntent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    GlanceModifier
                        .size(32.dp)
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = noteWidgetGlyph(note),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = noteWidgetTitle(context, note),
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
                if (showReminder && note.note.reminderAt != null) {
                    Text(
                        text = summaryTimingLabel(context, note.note.reminderAt, now),
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                }
                val subline =
                    note.note.body.ifBlank {
                        note.items
                            .firstOrNull()
                            ?.text
                            .orEmpty()
                    }
                if (subline.isNotBlank()) {
                    Text(
                        text = subline,
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            ),
                    )
                }
            }
        }
    }

    private fun noteWidgetGlyph(note: NoteWithItems): String {
        val emoji = iconEmojiPayload(note.note.iconKey)
        if (emoji != null) return emoji
        return if (note.note.kind == NoteKind.LIST) "L" else "N"
    }

    private fun noteWidgetTitle(
        context: Context,
        note: NoteWithItems,
    ): String {
        val title =
            note.note.title.ifBlank {
                if (note.note.kind == NoteKind.NOTE) {
                    context.getString(R.string.edit_note_title_new)
                } else {
                    context.getString(R.string.edit_list_title_new)
                }
            }
        return title
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

    companion object {
        private const val MAX_FAVORITE_ROWS = 3
        private const val MAX_REMINDER_SUMMARY_ROWS = 7
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
