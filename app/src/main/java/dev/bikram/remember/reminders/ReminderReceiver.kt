package dev.bikram.remember.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import dev.bikram.remember.MainActivity
import dev.bikram.remember.R
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.ChecklistItemEntity
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.NoteEntity
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ReminderPrefs
import dev.bikram.remember.data.Visibility
import dev.bikram.remember.data.getActiveReminders
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.data.toNoteActionIconBitmap
import dev.bikram.remember.di.ApplicationScope
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.notifications.canPostNotifications
import dev.bikram.remember.notifications.postNotificationIfAllowed
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.edit.iconEmojiPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var reminderPrefs: ReminderPrefs

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return
        val reminderIndex = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_INDEX, 0)

        val pendingResult = goAsync()

        applicationScope.launch {
            try {
                val noteWithItems = noteRepository.get(noteId) ?: return@launch
                val note = noteWithItems.note
                if (!isReminderDeliveryCurrent(note, reminderIndex, System.currentTimeMillis())) return@launch

                val keepUntilDone =
                    reminderPrefs
                        .snapshot()
                        .keepReminderNotificationsUntilDone
                showNotification(
                    context = context,
                    note = note,
                    items = noteWithItems.items,
                    reminderIndex = reminderIndex,
                    keepUntilDone = keepUntilDone,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_HERO_WIDTH_PX = 1280
        private const val NOTIFICATION_HERO_HEIGHT_PX = 720

        /**
         * Indent for a nested checklist row in notification text. Plain leading spaces are not
         * reliably preserved there, so the indent is built from non-breaking spaces and carries a
         * bullet so the one supported nesting level stays readable at notification text sizes.
         */
        private const val NOTIFICATION_CHILD_ITEM_INDENT = "\u00A0\u00A0\u00A0\u2022\u00A0"
        private val notificationMarkdownHeadingRegex = Regex("""^\s*#{1,6}\s+""")
        private val notificationMarkdownChecklistRegex = Regex("""^\s*[-*+]\s+\[[ xX]\]\s+""")
        private val notificationMarkdownBulletRegex = Regex("""^\s*[-*+]\s+""")
        private val notificationMarkdownQuoteRegex = Regex("""^\s*>\s?""")
        private val notificationMarkdownCodeFenceRegex = Regex("""^\s*```.*$""")
        private val notificationMarkdownLinkRegex = Regex("""\[([^]]+)]\([^)]+\)""")
        private val notificationMarkdownInlineCodeRegex = Regex("""`([^`]+)`""")
        private val notificationMarkdownBoldItalicRegex = Regex("""\*\*\*(.+?)\*\*\*""")
        private val notificationMarkdownBoldRegex = Regex("""\*\*(.+?)\*\*""")
        private val notificationMarkdownItalicRegex = Regex("""(?<!\*)\*(?!\s)(.+?)(?<!\s)\*(?!\*)""")
        private val notificationMarkdownStrikeRegex = Regex("""~~(.+?)~~""")
        private val notificationMarkdownUnderlineOpenRegex = Regex("""<u>""", RegexOption.IGNORE_CASE)
        private val notificationMarkdownUnderlineCloseRegex = Regex("""</u>""", RegexOption.IGNORE_CASE)

        fun showNotification(
            context: Context,
            note: NoteEntity,
            items: List<ChecklistItemEntity> = emptyList(),
            reminderIndex: Int = 0,
            keepUntilDone: Boolean = false,
            onlyAlertOnce: Boolean = false,
            silent: Boolean = false,
        ) {
            if (note.trashed) return

            if (!canPostNotifications(context)) {
                DiagnosticLog.record(context, "Reminder notification skipped for noteId=${note.id}: notifications are not allowed")
                return
            }

            val channelId =
                when (note.importance) {
                    Importance.LOW -> ReminderScheduler.CHANNEL_ID_LOW
                    Importance.HIGH -> ReminderScheduler.CHANNEL_ID_HIGH
                    Importance.DEFAULT -> ReminderScheduler.CHANNEL_ID_DEFAULT
                }

            val collapsedSummary = summary(context, note, items)
            val expandedSummary = summary(context, note, items, expanded = true)
            val builder =
                NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_remember)
                    .setContentTitle(notificationTitle(context, note))
                    .setPriority(priorityFor(note.importance))
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(notificationVisibility(note))
                    .setSilent(silent)
                    .setContentIntent(openNotePendingIntent(context, note.id))
                    .setDeleteIntent(dismissPendingIntent(context, note.id, reminderIndex).takeIf { keepUntilDone })
                    .setOngoing(false)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(onlyAlertOnce)

            if (collapsedSummary.isNotBlank()) {
                builder.setContentText(collapsedSummary)
            }

            val heroBitmap = if (note.visibility == Visibility.DEFAULT) decodeNotificationHeroBitmap(context, note) else null
            if (heroBitmap != null) {
                val bigPictureStyle =
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(heroBitmap)
                        .setBigContentTitle(notificationTitle(context, note))
                if (expandedSummary.isNotBlank()) {
                    bigPictureStyle.setSummaryText(expandedSummary)
                }
                bigPictureStyle.showBigPictureWhenCollapsed(true)
                builder
                    .setStyle(
                        bigPictureStyle,
                    )
            } else if (note.kind == NoteKind.LIST) {
                builder.setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(expandedSummary),
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
            val shareText = if (note.visibility != Visibility.SECRET) computeShareText(note, items) else ""

            val customAction = if (note.visibility != Visibility.SECRET) note.actions.firstOrNull() else null
            if (customAction != null) {
                builder.addAction(actionButton(context, note.id, 0, customAction, shareText))
            }

            val snoozeAction = NoteAction(ActionType.SNOOZE, context.getString(R.string.action_type_snooze), "")
            val markDoneAction = NoteAction(ActionType.MARK_AS_DONE, context.getString(R.string.action_type_mark_as_done), "")

            builder.addAction(actionButton(context, note.id, 1, snoozeAction, shareText))
            builder.addAction(actionButton(context, note.id, 2, markDoneAction, shareText))

            ReminderScheduler.cancelReminderSlotNotifications(context, note.id)
            postNotificationIfAllowed(
                context = context,
                notificationId = ReminderScheduler.pendingRequestCodeForNote(note.id),
                notification = builder.build(),
                source = "Reminder noteId=${note.id} reminderIndex=$reminderIndex",
            )
        }

        private fun decodeNotificationHeroBitmap(
            context: Context,
            note: NoteEntity,
        ): Bitmap? {
            val pictureUri = note.pictureUri?.takeIf { it.isNotBlank() } ?: return null
            val source =
                runCatching {
                    ImageDecoder.createSource(context.contentResolver, pictureUri.toUri())
                }.getOrNull() ?: return null
            val decodedBitmap =
                runCatching {
                    ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.setTargetSampleSize(
                            notificationHeroSampleSize(
                                sourceWidthPx = imageInfo.size.width,
                                sourceHeightPx = imageInfo.size.height,
                            ),
                        )
                    }
                }.getOrNull() ?: return null

            val framedBitmap =
                createCroppedBitmap(
                    sourceBitmap = decodedBitmap,
                    targetWidthPx = NOTIFICATION_HERO_WIDTH_PX,
                    targetHeightPx = NOTIFICATION_HERO_HEIGHT_PX,
                    framing = HeroFraming.fromJsonString(note.pictureHeroFraming),
                )
            if (framedBitmap !== decodedBitmap) {
                decodedBitmap.recycle()
            }
            return framedBitmap
        }

        private fun notificationHeroSampleSize(
            sourceWidthPx: Int,
            sourceHeightPx: Int,
        ): Int {
            if (sourceWidthPx <= 0 || sourceHeightPx <= 0) return 1

            var sampleSize = 1
            var nextSampleSize = sampleSize * 2
            while (
                sourceWidthPx / nextSampleSize >= NOTIFICATION_HERO_WIDTH_PX &&
                sourceHeightPx / nextSampleSize >= NOTIFICATION_HERO_HEIGHT_PX
            ) {
                sampleSize = nextSampleSize
                nextSampleSize = sampleSize * 2
            }
            return sampleSize
        }

        private fun createCroppedBitmap(
            sourceBitmap: Bitmap,
            targetWidthPx: Int,
            targetHeightPx: Int,
            framing: HeroFraming? = null,
        ): Bitmap {
            val sourceWidthPx = sourceBitmap.width.toFloat().coerceAtLeast(1f)
            val sourceHeightPx = sourceBitmap.height.toFloat().coerceAtLeast(1f)
            val clampedFraming = framing?.clamped()
            val coverScale = max(targetWidthPx / sourceWidthPx, targetHeightPx / sourceHeightPx)
            val displayScale = coverScale * (clampedFraming?.zoom ?: 1f).coerceIn(1f, 8f)
            val scaledWidthPx = sourceWidthPx * displayScale
            val scaledHeightPx = sourceHeightPx * displayScale
            val leftUnclamped =
                if (clampedFraming == null) {
                    (targetWidthPx - scaledWidthPx) / 2f
                } else {
                    targetWidthPx / 2f - clampedFraming.focalX * sourceWidthPx * displayScale
                }
            val topUnclamped =
                if (clampedFraming == null) {
                    (targetHeightPx - scaledHeightPx) / 2f
                } else {
                    targetHeightPx / 2f - clampedFraming.focalY * sourceHeightPx * displayScale
                }
            val destinationLeftPx = leftUnclamped.coerceIn(targetWidthPx - scaledWidthPx, 0f)
            val destinationTopPx = topUnclamped.coerceIn(targetHeightPx - scaledHeightPx, 0f)
            val destinationRect =
                Rect(
                    destinationLeftPx.roundToInt(),
                    destinationTopPx.roundToInt(),
                    (destinationLeftPx + scaledWidthPx).roundToInt(),
                    (destinationTopPx + scaledHeightPx).roundToInt(),
                )
            val outputBitmap = createBitmap(targetWidthPx, targetHeightPx)
            Canvas(outputBitmap).drawBitmap(sourceBitmap, null, destinationRect, null)
            return outputBitmap
        }

        private fun notificationTitle(
            context: Context,
            note: NoteEntity,
        ): String {
            if (note.visibility == Visibility.SECRET) return context.getString(R.string.reminder_notification_hidden_title)

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
            when (note.visibility) {
                Visibility.SECRET -> return context.getString(R.string.reminder_notification_hidden_body)
                Visibility.PRIVATE -> return context.getString(R.string.reminder_notification_hidden_body)
                Visibility.DEFAULT -> Unit
            }

            if (note.kind == NoteKind.LIST) {
                val activeLines = checklistNotificationLines(items)

                if (activeLines.isEmpty()) {
                    return context.getString(R.string.reminder_notification_all_items_checked)
                }

                // The first line is never a child (a child is only nested once its parent has
                // already been rendered above it), so the collapsed text needs no indent handling.
                return if (expanded) {
                    activeLines.joinToString("\n")
                } else {
                    activeLines.first().take(120)
                }
            }

            val renderedBody = notificationPlainText(note.body)
            if (renderedBody.isBlank()) return ""
            return if (expanded) {
                renderedBody
            } else {
                renderedBody
                    .lineSequence()
                    .firstOrNull { line -> line.isNotBlank() }
                    ?.take(120)
                    .orEmpty()
            }
        }

        /**
         * Unchecked checklist rows in the order the list screen shows them, with children indented
         * under the parent they belong to. A child whose parent is checked - or whose parent has no
         * text to render - has nothing to hang off in a flat text block, so it falls back to the top
         * level instead of appearing nested under an unrelated row.
         */
        private fun checklistNotificationLines(items: List<ChecklistItemEntity>): List<String> {
            val lines = mutableListOf<String>()
            var lastRenderedParentId: Long? = null
            items
                .filterNot { item -> item.checked }
                .sortedBy { item -> item.sortOrder }
                .forEach { item ->
                    val text =
                        notificationPlainText(item.text)
                            .lineSequence()
                            .firstOrNull { line -> line.isNotBlank() }
                            ?.trim()
                            .orEmpty()
                    if (text.isBlank()) return@forEach

                    val nestedUnderRenderedParent = item.parentId != null && item.parentId == lastRenderedParentId
                    lines += if (nestedUnderRenderedParent) "$NOTIFICATION_CHILD_ITEM_INDENT$text" else text
                    if (item.parentId == null) lastRenderedParentId = item.id
                }
            return lines
        }

        private fun notificationPlainText(markdown: String): String {
            var insideCodeBlock = false
            val renderedLines = mutableListOf<String>()
            markdown.lines().forEach { rawLine ->
                val trimmedLine = rawLine.trimEnd()
                if (notificationMarkdownCodeFenceRegex.matches(trimmedLine)) {
                    insideCodeBlock = !insideCodeBlock
                    return@forEach
                }

                val renderedLine =
                    if (insideCodeBlock) {
                        trimmedLine
                    } else {
                        notificationPlainTextLine(trimmedLine)
                    }
                if (renderedLine.isNotBlank()) {
                    renderedLines.add(renderedLine)
                }
            }
            return renderedLines.joinToString("\n").trim()
        }

        private fun notificationPlainTextLine(line: String): String {
            var renderedLine =
                line
                    .replace(notificationMarkdownHeadingRegex, "")
                    .replace(notificationMarkdownChecklistRegex, "")
                    .replace(notificationMarkdownBulletRegex, "")
                    .replace(notificationMarkdownQuoteRegex, "")
                    .replace(notificationMarkdownUnderlineOpenRegex, "")
                    .replace(notificationMarkdownUnderlineCloseRegex, "")

            renderedLine =
                notificationMarkdownLinkRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            renderedLine =
                notificationMarkdownInlineCodeRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            renderedLine =
                notificationMarkdownBoldItalicRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            renderedLine =
                notificationMarkdownBoldRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            renderedLine =
                notificationMarkdownItalicRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            renderedLine =
                notificationMarkdownStrikeRegex.replace(renderedLine) { matchResult ->
                    matchResult.groupValues[1]
                }
            return renderedLine.trim()
        }

        /**
         * Build the text payload for a SHARE_CONTENT action so it can be baked into the
         * notification action's [PendingIntent.getActivity] up front. Mirrors the
         * (formerly receiver-side) [ActionReceiver] logic so checklist items still get
         * the [x] / [ ] prefix.
         */
        private fun computeShareText(
            note: NoteEntity,
            items: List<ChecklistItemEntity>,
        ): String =
            if (note.kind == NoteKind.NOTE) {
                note.body
            } else {
                items.sortedBy { it.sortOrder }.joinToString("\n") { item ->
                    if (item.checked) "[x] ${item.text}" else "[ ] ${item.text}"
                }
            }

        private fun notificationVisibility(note: NoteEntity): Int =
            when (note.visibility) {
                Visibility.DEFAULT -> NotificationCompat.VISIBILITY_PUBLIC
                Visibility.PRIVATE -> NotificationCompat.VISIBILITY_PRIVATE
                Visibility.SECRET -> NotificationCompat.VISIBILITY_SECRET
            }

        private fun priorityFor(importance: Importance): Int =
            when (importance) {
                Importance.LOW -> NotificationCompat.PRIORITY_LOW
                Importance.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
                Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
            }

        private fun openNotePendingIntent(
            context: Context,
            noteId: Long,
        ): PendingIntent {
            val open =
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = "remember://notification/open/$noteId".toUri()
                    // Deliberately no FLAG_ACTIVITY_CLEAR_TASK. It finishes the running MainActivity
                    // and starts a second one, and the outgoing instance is destroyed only after the
                    // new one has resumed - long enough for its still-live collector to consume the
                    // app-scoped launch action, so the fresh instance found nothing pending and sat
                    // on the Notes tab. SINGLE_TOP/CLEAR_TOP hand the intent to the live activity via
                    // onNewIntent instead, and the editor is opened on a back stack of its own making
                    // (see openEditRouteFor), so nothing here needs the task cleared.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
                    putExtra(MainActivity.EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH, true)
                }
            return PendingIntent.getActivity(
                context,
                ReminderScheduler.pendingRequestCodeForNote(noteId),
                open,
                // CANCEL_CURRENT rather than UPDATE_CURRENT: PendingIntent matching ignores intent
                // flags, and UPDATE_CURRENT replaces only the extras of the record it matches, so a
                // record left over from an older build would keep serving that build's launch flags.
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun dismissPendingIntent(
            context: Context,
            noteId: Long,
            reminderIndex: Int,
        ): PendingIntent {
            val dismiss =
                Intent(context, ReminderDismissReceiver::class.java).apply {
                    action = ReminderDismissReceiver.ACTION_DISMISSED
                    putExtra(ReminderDismissReceiver.EXTRA_NOTE_ID, noteId)
                }
            return PendingIntent.getBroadcast(
                context,
                ReminderScheduler.pendingRequestCodeForDismiss(noteId, reminderIndex),
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
            val pi =
                if (launchIntent != null) {
                    PendingIntent.getActivity(context, requestCode, launchIntent, flags)
                } else {
                    // Non-activity actions (MARK_AS_DONE, COPY_TO_CLIPBOARD) and any
                    // activity action whose data is unusable at notification-creation time
                    // (e.g. OPEN_APP for an uninstalled package) fall back to the receiver,
                    // which can surface a Toast for the failure case.
                    val i =
                        Intent(context, ActionReceiver::class.java).apply {
                            this.action = ActionReceiver.ACTION_FIRE
                            putExtra(ActionReceiver.EXTRA_NOTE_ID, noteId)
                            putExtra(ActionReceiver.EXTRA_ACTION_INDEX, index)
                        }
                    PendingIntent.getBroadcast(context, requestCode, i, flags)
                }
            val actionLabel =
                action.title.trim().ifBlank {
                    if (action.type == ActionType.MARK_AS_DONE) {
                        context.getString(R.string.action_type_mark_as_done)
                    } else {
                        context.getString(action.type.labelRes())
                    }
                }
            return NotificationCompat.Action
                .Builder(
                    actionNotificationIcon(context, action),
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
            val intent: Intent =
                when (action.type) {
                    ActionType.SNOOZE ->
                        Intent(context, SnoozeActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra(SnoozeActivity.EXTRA_NOTE_ID, noteId)
                        }
                    ActionType.CALL_NUMBER -> Intent(Intent.ACTION_DIAL, "tel:${action.details}".toUri())
                    ActionType.SEND_MESSAGE -> Intent(Intent.ACTION_SENDTO, "smsto:${action.details}".toUri())
                    ActionType.SEND_EMAIL -> Intent(Intent.ACTION_SENDTO, "mailto:${action.details}".toUri())
                    ActionType.GET_DIRECTIONS -> Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(action.details)}".toUri())
                    ActionType.OPEN_LINK -> Intent(Intent.ACTION_VIEW, normalizeUrl(action.details).toUri())
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

        private fun normalizeUrl(s: String): String = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"

        private fun actionNotificationIcon(
            context: Context,
            action: NoteAction,
        ): IconCompat {
            val bitmap =
                when (action.type) {
                    ActionType.OPEN_APP -> appNotificationIconBitmap(context, action.details)
                    ActionType.OPEN_SHORTCUT ->
                        action.iconData.toNoteActionIconBitmap()
                            ?: shortcutNotificationIconBitmap(context, action.details)
                    else -> null
                }
            return if (bitmap != null) {
                bitmap.toNotificationIconCompatOrNull()
                    ?: IconCompat.createWithResource(context, R.drawable.ic_stat_remember)
            } else {
                IconCompat.createWithResource(context, R.drawable.ic_stat_remember)
            }
        }

        private fun Bitmap.toNotificationIconCompatOrNull(): IconCompat? =
            try {
                ByteArrayOutputStream().use { output ->
                    if (!compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        null
                    } else {
                        val bytes = output.toByteArray()
                        if (bytes.isEmpty()) {
                            null
                        } else {
                            IconCompat.createWithData(bytes, 0, bytes.size)
                        }
                    }
                }
            } catch (_: Throwable) {
                null
            } finally {
                recycle()
            }

        private fun appNotificationIconBitmap(
            context: Context,
            packageName: String,
        ): Bitmap? =
            if (packageName.isBlank()) {
                null
            } else {
                runCatching {
                    context.packageManager
                        .getApplicationIcon(packageName)
                        .toNotificationActionBitmap()
                }.getOrNull()
            }

        private fun shortcutNotificationIconBitmap(
            context: Context,
            intentUri: String,
        ): Bitmap? {
            if (intentUri.isBlank()) return null
            val intent =
                runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
                    .getOrNull() ?: return null
            val packageManager = context.packageManager
            val packageName =
                intent.component?.packageName
                    ?: intent.`package`
                    ?: intent.resolveActivity(packageManager)?.packageName
                    ?: return null
            return appNotificationIconBitmap(context, packageName)
        }

        private fun Drawable.toNotificationActionBitmap(): Bitmap {
            val bitmap = createBitmap(NOTIFICATION_ACTION_ICON_SIZE_PX, NOTIFICATION_ACTION_ICON_SIZE_PX)
            var success = false
            try {
                val canvas = Canvas(bitmap)
                val oldBounds = Rect(bounds)
                try {
                    setBounds(0, 0, canvas.width, canvas.height)
                    draw(canvas)
                } finally {
                    setBounds(oldBounds)
                }
                success = true
                return bitmap
            } finally {
                if (!success) {
                    bitmap.recycle()
                }
            }
        }

        private const val NOTIFICATION_ACTION_ICON_SIZE_PX = 96
    }
}

internal fun isReminderDeliveryCurrent(
    note: NoteEntity,
    reminderIndex: Int,
    now: Long,
): Boolean {
    if (note.trashed || note.archived || note.completedAt != null) return false
    val reminder = note.getActiveReminders().getOrNull(reminderIndex) ?: return false
    return reminder.reminderAt <= now
}
