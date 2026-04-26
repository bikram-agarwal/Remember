package dev.bikram.remember.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.RememberApp
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.ui.edit.iconEmojiPayload
import dev.bikram.remember.data.labelRes
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return
        
        val pendingResult = goAsync()
        val app = context.applicationContext as RememberApp
        val repo = app.container.noteRepository

        app.container.applicationScope.launch {
            try {
                val noteWithItems = repo.get(noteId) ?: return@launch
                val note = noteWithItems.note
                if (note.trashed) return@launch

                val keepUntilDone = app.container.reminderPrefs
                    .snapshot()
                    .keepReminderNotificationsUntilDone
                showNotification(
                    context = context,
                    note = note,
                    items = noteWithItems.items,
                    keepUntilDone = keepUntilDone,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun showNotification(
            context: Context,
            note: NoteEntity,
            items: List<ChecklistItemEntity> = emptyList(),
            keepUntilDone: Boolean = false,
        ) {
            if (note.trashed) return

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = when (note.importance) {
                Importance.LOW -> ReminderScheduler.CHANNEL_ID_LOW
                Importance.HIGH -> ReminderScheduler.CHANNEL_ID_HIGH
                Importance.DEFAULT -> ReminderScheduler.CHANNEL_ID_DEFAULT
            }
            
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_remember)
                .setContentTitle(
                    notificationTitle(context, note),
                )
                .setContentText(summary(context, note, items))
                .setPriority(priorityFor(note.importance))
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openNotePendingIntent(context, note.id))
                .setDeleteIntent(dismissPendingIntent(context, note.id).takeIf { keepUntilDone })
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)

            if (note.kind == NoteKind.LIST) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(summary(context, note, items, expanded = true)),
                )
            }

            if (note.importance == Importance.HIGH) {
                // Heads-up + on-lockscreen popup. setDefaults provides the sound /
                // vibration / lights pattern when the per-channel sound has been
                // overridden by the user; the channel itself still drives behavior on
                // API 26+, but DEFAULT_ALL is a safe belt-and-braces for older devices
                // and lets the OS treat this as a high-attention notification.
                builder
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 250, 200, 250))
            }

            // Pre-compute the share text so SHARE_CONTENT can be served by a direct
            // PendingIntent.getActivity (with the text baked into Intent.EXTRA_TEXT)
            // instead of going through ActionReceiver, which is subject to BAL.
            val shareText = computeShareText(note, items)

            val customAction = note.actions.firstOrNull()
            if (customAction != null) {
                builder.addAction(actionButton(context, note.id, 0, customAction, shareText))
            }

            val snoozeAction = NoteAction(ActionType.SNOOZE, context.getString(R.string.action_type_snooze), "")
            val markDoneAction = NoteAction(ActionType.MARK_AS_DONE, context.getString(R.string.action_type_mark_as_done), "")

            builder.addAction(actionButton(context, note.id, 1, snoozeAction, shareText))
            builder.addAction(actionButton(context, note.id, 2, markDoneAction, shareText))

            nm.notify(ReminderScheduler.pendingRequestCodeForNote(note.id), builder.build())
        }

        private fun notificationTitle(context: Context, note: NoteEntity): String {
            val title = note.title.ifBlank { context.getString(R.string.options_reminder) }
            val emoji = iconEmojiPayload(note.iconKey) ?: return title
            return "$emoji $title"
        }

        private fun summary(
            context: Context,
            note: NoteEntity,
            items: List<ChecklistItemEntity>,
            expanded: Boolean = false,
        ): String {
            if (note.kind == NoteKind.LIST) {
                val uncheckedItems = items
                    .asSequence()
                    .filterNot { it.checked }
                    .sortedBy { it.sortOrder }
                    .map { item ->
                        val text = item.text.trim()
                        if (item.depth > 0 && text.isNotBlank()) "  $text" else text
                    }
                    .filter { it.isNotBlank() }
                    .toList()

                if (uncheckedItems.isEmpty()) {
                    return context.getString(R.string.reminder_notification_all_items_checked)
                }

                return if (expanded) {
                    uncheckedItems.joinToString("\n")
                } else {
                    uncheckedItems.first().take(120)
                }
            }

            return if (note.body.isNotBlank()) {
                note.body.take(120)
            } else {
                context.getString(R.string.reminder_notification_fallback)
            }
        }

        /**
         * Build the text payload for a SHARE_CONTENT action so it can be baked into the
         * notification action's [PendingIntent.getActivity] up front. Mirrors the
         * (formerly receiver-side) [ActionReceiver] logic so checklist items still get
         * the [x] / [ ] prefix.
         */
        private fun computeShareText(note: NoteEntity, items: List<ChecklistItemEntity>): String =
            if (note.kind == NoteKind.NOTE) {
                note.body
            } else {
                items.sortedBy { it.sortOrder }.joinToString("\n") { item ->
                    if (item.checked) "[x] ${item.text}" else "[ ] ${item.text}"
                }
            }

        private fun priorityFor(importance: Importance): Int = when (importance) {
            Importance.LOW -> NotificationCompat.PRIORITY_LOW
            Importance.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
        }

        private fun openNotePendingIntent(context: Context, noteId: Long): PendingIntent {
            val open = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_note_id", noteId)
            }
            return PendingIntent.getActivity(
                context,
                ReminderScheduler.pendingRequestCodeForNote(noteId),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun dismissPendingIntent(context: Context, noteId: Long): PendingIntent {
            val dismiss = Intent(context, ReminderDismissReceiver::class.java).apply {
                action = ReminderDismissReceiver.ACTION_DISMISSED
                putExtra(ReminderDismissReceiver.EXTRA_NOTE_ID, noteId)
            }
            return PendingIntent.getBroadcast(
                context,
                ReminderScheduler.pendingRequestCodeForDismiss(noteId),
                dismiss,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun actionButton(
            context: Context,
            noteId: Long,
            index: Int,
            action: NoteAction,
            shareText: String,
        ): NotificationCompat.Action {
            // Activity-launching actions go through PendingIntent.getActivity directly,
            // not through ActionReceiver. The OS grants notification-driven activity
            // launches reliably; the ActionReceiver -> context.startActivity() path
            // depends on a Background Activity Launch grant that has expired by the
            // time our coroutine resumes from a Room IO suspend, which is exactly why
            // these actions fired intermittently when the app was backgrounded.
            val requestCode = ReminderScheduler.pendingRequestCodeForNoteAction(noteId, index)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val launchIntent = buildActivityLaunchIntent(context, noteId, action, shareText)
            val pi = if (launchIntent != null) {
                PendingIntent.getActivity(context, requestCode, launchIntent, flags)
            } else {
                // Non-activity actions (MARK_AS_DONE, COPY_TO_CLIPBOARD) and any
                // activity action whose data is unusable at notification-creation time
                // (e.g. OPEN_APP for an uninstalled package) fall back to the receiver,
                // which can surface a Toast for the failure case.
                val i = Intent(context, ActionReceiver::class.java).apply {
                    this.action = ActionReceiver.ACTION_FIRE
                    putExtra(ActionReceiver.EXTRA_NOTE_ID, noteId)
                    putExtra(ActionReceiver.EXTRA_ACTION_INDEX, index)
                }
                PendingIntent.getBroadcast(context, requestCode, i, flags)
            }
            val actionLabel = action.title.trim().ifBlank {
                if (action.type == ActionType.MARK_AS_DONE) {
                    context.getString(R.string.action_type_mark_as_done)
                } else {
                    context.getString(action.type.labelRes())
                }
            }
            return NotificationCompat.Action.Builder(
                R.drawable.ic_stat_remember,
                actionLabel,
                pi,
            ).build()
        }

        /**
         * Returns a launchable [Intent] for activity-launching action types, or null when
         * the action does not start an activity (or its data could not be resolved).
         * Building the intent here - at notification creation time - lets us hand the
         * notification a [PendingIntent.getActivity] directly, which sidesteps Android's
         * Background Activity Launch (BAL) restrictions that were silently dropping
         * activity launches from [ActionReceiver] on a backgrounded app.
         */
        private fun buildActivityLaunchIntent(
            context: Context,
            noteId: Long,
            action: NoteAction,
            shareText: String,
        ): Intent? {
            val intent: Intent = when (action.type) {
                ActionType.SNOOZE -> Intent(context, SnoozeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(SnoozeActivity.EXTRA_NOTE_ID, noteId)
                }
                // Always ACTION_DIAL even when CALL_PHONE is granted. ACTION_CALL from a
                // PendingIntent is fragile (some OEMs deny it without an active activity)
                // and dropping the user into the dialer with the number pre-filled is the
                // more predictable behavior anyway.
                ActionType.CALL_NUMBER -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.details}"))
                ActionType.SEND_MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${action.details}"))
                ActionType.SEND_EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${action.details}"))
                ActionType.GET_DIRECTIONS -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(action.details)}"))
                ActionType.OPEN_LINK -> Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(action.details)))
                ActionType.OPEN_APP -> {
                    // Resolve at notification time. If the app is uninstalled return null
                    // so the receiver can Toast a useful error instead of silently no-oping.
                    context.packageManager.getLaunchIntentForPackage(action.details) ?: return null
                }
                ActionType.OPEN_SHORTCUT -> {
                    runCatching { Intent.parseUri(action.details, Intent.URI_INTENT_SCHEME) }
                        .getOrNull() ?: return null
                }
                ActionType.SHARE_CONTENT -> {
                    if (shareText.isBlank()) return null
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        },
                        action.title.trim().ifBlank { context.getString(R.string.share_chooser_generic) },
                    )
                }
                // Non-activity types - handled by ActionReceiver.
                ActionType.MARK_AS_DONE,
                ActionType.COPY_TO_CLIPBOARD,
                -> return null
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        private fun normalizeUrl(s: String): String =
            if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
    }
}
