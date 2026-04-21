package dev.bikram.remember.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
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
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import kotlinx.coroutines.flow.first

/**
 * Two-section Glance widget:
 *   - Favorites (pinned active notes, up to [MAX_ROWS_PER_SECTION])
 *   - Upcoming reminders (active notes with a future [NoteEntity.reminderAt], soonest first)
 *
 * Tapping a row opens that note; tapping the plus icon starts a brand-new note.
 */
class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RememberApp
        val active = app.container.noteRepository.observeActive().first()
        val now = System.currentTimeMillis()
        val pinned = active
            .asSequence()
            .filter { it.note.pinned }
            .take(MAX_ROWS_PER_SECTION)
            .toList()
        val upcoming = active
            .asSequence()
            .filter { (it.note.reminderAt ?: Long.MIN_VALUE) > now }
            .sortedBy { it.note.reminderAt }
            .take(MAX_ROWS_PER_SECTION)
            .toList()
        provideContent {
            GlanceTheme { WidgetContent(pinned = pinned, upcoming = upcoming) }
        }
    }

    @Composable
    private fun WidgetContent(
        pinned: List<NoteWithItems>,
        upcoming: List<NoteWithItems>,
    ) {
        val context = LocalContext.current
        val newNoteIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("action", "new_note")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Column(
            modifier = GlanceModifier
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
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_note),
                    contentDescription = context.getString(R.string.widget_new_note_cd),
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionStartActivity(newNoteIntent)),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            // Single LazyColumn so both sections scroll together when the widget is tall.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                item { SectionHeader(context.getString(R.string.widget_section_pinned)) }
                if (pinned.isEmpty()) {
                    item { SectionEmpty(context.getString(R.string.widget_section_empty_pinned)) }
                } else {
                    items(count = pinned.size) { i ->
                        NoteRow(note = pinned[i], context = context, showReminder = false)
                    }
                }
                item { Spacer(GlanceModifier.height(8.dp)) }
                item { SectionHeader(context.getString(R.string.widget_section_reminders)) }
                if (upcoming.isEmpty()) {
                    item {
                        SectionEmpty(context.getString(R.string.widget_section_empty_reminders))
                    }
                } else {
                    items(count = upcoming.size) { i ->
                        NoteRow(note = upcoming[i], context = context, showReminder = true)
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(label: String) {
        Text(
            text = label,
            style = TextStyle(
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
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.padding(vertical = 4.dp),
        )
    }

    @Composable
    private fun NoteRow(
        note: NoteWithItems,
        context: Context,
        showReminder: Boolean,
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("open_note_id", note.note.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(actionStartActivity(openIntent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .cornerRadius(3.dp)
                    .background(GlanceTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) { }
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = note.note.title.ifBlank {
                        if (note.note.kind == NoteKind.NOTE) {
                            context.getString(R.string.edit_note_title_new)
                        } else {
                            context.getString(R.string.edit_list_title_new)
                        }
                    },
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val subline = when {
                    showReminder && note.note.reminderAt != null ->
                        formatRelativeReminder(context, note.note.reminderAt!!)
                    else -> note.note.body.ifBlank { note.items.firstOrNull()?.text.orEmpty() }
                }
                if (subline.isNotBlank()) {
                    Text(
                        text = subline,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }

    /** "in 3 hours", "tomorrow at 2:00 PM" etc. Uses [DateUtils] so it localises automatically. */
    private fun formatRelativeReminder(context: Context, at: Long): String {
        val now = System.currentTimeMillis()
        return DateUtils.getRelativeDateTimeString(
            context,
            at,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            if (at - now < DateUtils.DAY_IN_MILLIS) DateUtils.FORMAT_SHOW_TIME else 0,
        ).toString()
    }

    companion object {
        private const val MAX_ROWS_PER_SECTION = 3
    }
}
