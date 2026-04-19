package dev.bikram.remember.widget
import androidx.compose.foundation.clickable

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.glance.currentState
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
import dev.bikram.remember.data.NoteWithItems
import kotlinx.coroutines.flow.first

class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RememberApp
        val active = app.container.noteRepository.observeActive().first()
        val pinned = active.filter { it.note.pinned }.take(5)
        val preview = if (pinned.isNotEmpty()) pinned else active.take(5)
        provideContent {
            GlanceTheme { WidgetContent(preview) }
        }
    }

    @Composable
    private fun WidgetContent(notes: List<NoteWithItems>) {
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
                    text = "Remember",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_note),
                    contentDescription = "New note",
                    modifier = GlanceModifier
                        .size(28.dp)
                        .clickable(actionStartActivity(newNoteIntent)),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (notes.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No notes yet",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(count = notes.size) { i ->
                        NoteRow(notes[i], context)
                    }
                }
            }
        }
    }

    @Composable
    private fun NoteRow(note: NoteWithItems, context: Context) {
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
                    note.note.title.ifBlank { "Untitled" },
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val preview = note.note.body.ifBlank {
                    note.items.firstOrNull()?.text.orEmpty()
                }
                if (preview.isNotBlank()) {
                    Text(
                        preview,
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
}
