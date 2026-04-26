package dev.bikram.remember.reminders

import android.Manifest
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
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteRepository
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val index = intent.getIntExtra(EXTRA_ACTION_INDEX, -1)
        if (noteId <= 0L || index < 0) {
            return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        val repository = app.container.noteRepository
        app.container.applicationScope.launch {
            try {
                val noteWithItems = repository.get(noteId)
                if (noteWithItems == null) return@launch
                val note = noteWithItems.note
                val action = if (index == 1) {
                    NoteAction(ActionType.SNOOZE, context.getString(R.string.action_type_snooze), "")
                } else if (index == 2) {
                    NoteAction(ActionType.MARK_AS_DONE, context.getString(R.string.action_type_mark_as_done), "")
                } else {
                    note.actions.firstOrNull() ?: return@launch
                }

                try {
                    // ACTION_CLOSE_SYSTEM_DIALOGS was restricted to system apps in API 31+.
                    // The broadcast is best-effort: it still works on older devices and on
                    // permissive OEM builds, and we already swallow the SecurityException
                    // when the platform refuses it.
                    @Suppress("DEPRECATION")
                    val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    try {
                        context.sendBroadcast(closeIntent)
                    } catch (_: SecurityException) { }

                    fire(context, repository, noteWithItems, action)
                } catch (t: Throwable) {
                    val message = t.message.orEmpty().ifBlank { context.getString(R.string.common_empty) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.action_receiver_run_error, message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(
        context: Context,
        repository: NoteRepository,
        noteWithItems: dev.bikram.remember.data.NoteWithItems,
        action: dev.bikram.remember.data.NoteAction,
    ): Boolean {
        val noteId = noteWithItems.note.id
        if (action.type == ActionType.COPY_TO_CLIPBOARD) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipLabel = action.title.ifBlank { context.getString(R.string.toast_copied) }
            cm.setPrimaryClip(ClipData.newPlainText(clipLabel, getNoteContent(noteWithItems)))
            Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
            return true
        }
        if (action.type == ActionType.MARK_AS_DONE) {
            return repository.clearReminderFromNotificationAction(noteId)
        }
        if (action.type == ActionType.SNOOZE) {
            val snoozeIntent = Intent(context, SnoozeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(SnoozeActivity.EXTRA_NOTE_ID, noteId)
            }
            context.startActivity(snoozeIntent)
            return true
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
                    putExtra(Intent.EXTRA_TEXT, getNoteContent(noteWithItems))
                },
                action.title.ifBlank { context.getString(R.string.share_chooser_generic) },
            )
            ActionType.COPY_TO_CLIPBOARD, ActionType.MARK_AS_DONE, ActionType.SNOOZE, ActionType.SHARE_CONTENT -> return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun getNoteContent(noteWithItems: dev.bikram.remember.data.NoteWithItems): String {
        val note = noteWithItems.note
        return if (note.kind == dev.bikram.remember.data.NoteKind.NOTE) {
            note.body
        } else {
            noteWithItems.items.sortedBy { it.sortOrder }.joinToString("\n") { 
                if (it.checked) "[x] ${it.text}" else "[ ] ${it.text}"
            }
        }
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
