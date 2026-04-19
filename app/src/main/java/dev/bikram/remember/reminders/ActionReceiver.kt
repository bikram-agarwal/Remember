package dev.bikram.remember.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.NoteRepository
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val index = intent.getIntExtra(EXTRA_ACTION_INDEX, -1)
        if (noteId <= 0L || index < 0) return
        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        val repository = app.container.noteRepository
        app.container.applicationScope.launch {
            try {
                val note = repository.get(noteId)?.note ?: return@launch
                val action = note.actions.getOrNull(index) ?: return@launch

                val fired = try {
                    fire(context, repository, noteId, action)
                } catch (t: Throwable) {
                    val message = t.message.orEmpty().ifBlank { context.getString(R.string.common_empty) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.action_receiver_run_error, message),
                        Toast.LENGTH_SHORT,
                    ).show()
                    false
                }

                if (fired) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(ReminderScheduler.pendingRequestCodeForNote(noteId))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(
        context: Context,
        repository: NoteRepository,
        noteId: Long,
        action: dev.bikram.remember.data.NoteAction,
    ): Boolean {
        if (action.type == ActionType.COPY_TO_CLIPBOARD) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipLabel = action.title.ifBlank { context.getString(R.string.clipboard_default_label) }
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, action.details))
            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
            return true
        }
        if (action.type == ActionType.MARK_AS_DONE) {
            return repository.clearReminderFromNotificationAction(noteId)
        }

        val intent: Intent = when (action.type) {
            ActionType.CALL_NUMBER -> callIntent(context, action.details)
            ActionType.SEND_MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${action.details}"))
            ActionType.SEND_EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${action.details}"))
            ActionType.GET_DIRECTIONS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.details)}"))
            ActionType.OPEN_LINK -> Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(action.details)))
            ActionType.OPEN_APP -> context.packageManager.getLaunchIntentForPackage(action.details) ?: return false
            ActionType.OPEN_SHORTCUT -> runCatching { Intent.parseUri(action.details, Intent.URI_INTENT_SCHEME) }
                .getOrNull() ?: return false
            ActionType.SHARE_CONTENT -> Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, action.details)
                },
                action.title.ifBlank { context.getString(R.string.share_chooser_generic) },
            )
            ActionType.COPY_TO_CLIPBOARD, ActionType.MARK_AS_DONE -> return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun callIntent(context: Context, number: String): Intent {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return Intent(action, Uri.parse("tel:$number"))
    }

    private fun normalizeUrl(s: String): String =
        if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"

    companion object {
        const val ACTION_FIRE = "dev.bikram.remember.reminders.ACTION_FIRE"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_ACTION_INDEX = "action_index"
    }
}
