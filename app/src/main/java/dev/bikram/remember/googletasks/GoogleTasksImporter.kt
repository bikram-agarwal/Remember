package dev.bikram.remember.googletasks

import dev.bikram.remember.data.MAX_REMINDERS_PER_NOTE
import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.PersistableChecklistItem
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.limitedToReminderSlots
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Converts imported tasks into Remember notes/lists.
 *
 * Mapping rules (one note per task is the default; group-by-list and list-as-checklist are
 * driven by [ImportMode]):
 *  - title  -> NoteEntity.title (falls back to the first line of `notes` when title is blank)
 *  - notes  -> NoteEntity.body, or checklist item details in list-as-checklist mode
 *  - status == "completed" -> NoteEntity.completedAt (uses the API-provided completion timestamp
 *    when present, otherwise [now])
 *  - due (date-only per Google's docs) -> reminderAt at 09:00 in [zone]; in the list-level modes
 *    the due times and recurrences of the list's open tasks fill the note's reminder slots
 *  - tags: one-note-per-task keeps the source list title plus any tags from the backup;
 *    list-as-checklist and one-note-per-list use only the backup tags (the list name is
 *    already the note title)
 *
 * The Google Tasks API does NOT expose the per-task reminder time set inside the Tasks app
 * (the bell icon). The wire field is documented as date-only and the reminder data lives in a
 * separate, non-public sync layer. We surface this caveat in the UI; the mapper here always
 * lands at 09:00 when only [GoogleTask.due] is present.
 *
 * Idempotency: callers pass [alreadyImported] (googleTaskId -> rememberNoteId). Tasks already
 * in the map are skipped unless [overwrite] is true, in which case the existing note is updated
 * in place via [NoteRepository.updateNote] / [NoteRepository.updateList].
 */
class GoogleTasksImporter(
    private val repository: NoteRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * Import the selected [tasks] into Remember.
     *
     * The mapper is safe to call from any dispatcher; the underlying [NoteRepository] suspends
     * on Room which is already configured to use the IO dispatcher.
     */
    suspend fun import(
        tasks: List<TaskToImport>,
        mode: ImportMode,
        alreadyImported: Map<String, Long>,
        overwrite: Boolean,
        onImported: (googleTaskId: String, rememberNoteId: Long) -> Unit,
        onProgress: (completedCount: Int) -> Unit = {},
    ): ImportOutcome {
        val createdPairs = mutableMapOf<String, Long>()
        var skippedAlreadyImported = 0
        var written = 0
        var completedCount = 0

        when (mode) {
            ImportMode.ONE_NOTE_PER_TASK -> {
                tasks.forEach { task ->
                    val previousNoteId = alreadyImported[task.task.id]
                    val existingNoteId =
                        previousNoteId?.takeIf { noteId ->
                            // A note the user sent to the trash counts as gone. Otherwise a
                            // re-import would either skip the task or quietly rewrite a row the
                            // user can no longer see, and nothing would come back.
                            repository.get(noteId)?.note?.trashed == false
                        }
                    if (existingNoteId != null && !overwrite) {
                        skippedAlreadyImported++
                        completedCount++
                        onProgress(completedCount)
                        return@forEach
                    }
                    val noteId = upsertOneTask(task, existingNoteId.takeIf { overwrite })
                    if (noteId != null) {
                        createdPairs[task.task.id] = noteId
                        onImported(task.task.id, noteId)
                        written++
                    }
                    completedCount++
                    onProgress(completedCount)
                }
            }
            ImportMode.LIST_AS_CHECKLIST -> {
                tasks.groupBy { it.taskListId }.forEach { (_, group) ->
                    val anchor = group.first()
                    val taskListTitle = anchor.taskListTitle
                    val newId =
                        repository.createListWithItems(
                            title = taskListTitle,
                            colorIndex = 0,
                            items = checklistItemsFor(group),
                            options =
                                NoteOptions(
                                    tags = importedTagsFor(group),
                                    reminders = importedRemindersFor(group),
                                ),
                        )
                    group.forEach {
                        createdPairs[it.task.id] = newId
                        onImported(it.task.id, newId)
                    }
                    written += group.size
                    completedCount += group.size
                    onProgress(completedCount)
                }
            }
            ImportMode.GROUP_BY_LIST -> {
                tasks.groupBy { it.taskListId }.forEach { (_, group) ->
                    val anchor = group.first()
                    val title = anchor.taskListTitle
                    val body =
                        buildString {
                            group
                                .sortedWith(compareBy({ it.task.parent ?: "" }, { it.task.position.orEmpty() }))
                                .forEach { wrapper ->
                                    val indent = if (wrapper.task.parent.isNullOrBlank()) "" else "  "
                                    val mark = if (isCompleted(wrapper.task)) "[x]" else "[ ]"
                                    val displayTitle =
                                        wrapper.task.title
                                            .orEmpty()
                                            .ifBlank { firstLineOfNotes(wrapper.task) }
                                    if (displayTitle.isNotBlank()) {
                                        append(indent)
                                            .append("- ")
                                            .append(mark)
                                            .append(' ')
                                            .append(displayTitle)
                                            .append('\n')
                                    }
                                    if (!wrapper.task.notes.isNullOrBlank() &&
                                        wrapper.task.title
                                            .orEmpty()
                                            .isNotBlank()
                                    ) {
                                        append(indent)
                                            .append("    ")
                                            .append(wrapper.task.notes.replace("\n", "\n$indent    "))
                                            .append('\n')
                                    }
                                }
                        }.trimEnd()
                    val newId =
                        repository.createNote(
                            title = title,
                            body = body,
                            colorIndex = 0,
                            options =
                                NoteOptions(
                                    tags = importedTagsFor(group),
                                    reminders = importedRemindersFor(group),
                                ),
                        )
                    group.forEach {
                        createdPairs[it.task.id] = newId
                        onImported(it.task.id, newId)
                    }
                    written += group.size
                    completedCount += group.size
                    onProgress(completedCount)
                }
            }
        }

        return ImportOutcome(
            writtenCount = written,
            skippedAlreadyImported = skippedAlreadyImported,
            googleTaskIdToRememberNoteId = createdPairs,
        )
    }

    /**
     * Either creates a new note for [task] or rewrites the existing [existingNoteId] in place.
     * Returns the resulting note id, or null when the row was skipped (eg. blank title and notes).
     */
    private suspend fun upsertOneTask(
        task: TaskToImport,
        existingNoteId: Long?,
    ): Long? {
        val title =
            task.task.title
                .orEmpty()
                .trim()
                .ifBlank { firstLineOfNotes(task.task) }
        val body = task.task.notes.orEmpty()
        if (title.isBlank() && body.isBlank()) return null

        val reminderAt = task.reminderAt ?: computeReminderAt(task.task)
        val isDone = isCompleted(task.task)

        val tag = task.taskListTitle.trim()
        val tags =
            buildList {
                if (tag.isNotEmpty()) add(tag)
                addAll(task.tags.filter { importedTag -> importedTag.isNotBlank() })
            }.distinct()
        val options =
            NoteOptions(
                reminderAt = reminderAt,
                tags = tags,
                recurrence = task.recurrence,
            )

        return if (existingNoteId != null) {
            repository.updateNote(
                id = existingNoteId,
                title = title,
                body = body,
                colorIndex = 0,
                options = options,
            )
            // markCompleted / markIncomplete write completedAt + handle scheduler cancellation.
            if (isDone) {
                repository.markCompleted(existingNoteId)
            } else {
                repository.markIncomplete(existingNoteId)
            }
            existingNoteId
        } else {
            val newId =
                repository.createNote(
                    title = title,
                    body = body,
                    colorIndex = 0,
                    options = options,
                )
            if (isDone) repository.markCompleted(newId)
            newId
        }
    }

    /**
     * Convert the task's [GoogleTask.due] field (date-only per the API contract) into a Remember
     * reminder timestamp at 09:00 local. Returns null when there is no due date.
     */
    private fun computeReminderAt(task: GoogleTask): Long? {
        val raw = task.due?.takeIf { it.isNotBlank() } ?: return null
        // Tasks API always returns "YYYY-MM-DDT00:00:00.000Z" but we accept date-only just in case.
        val date = parseDate(raw) ?: return null
        return LocalDateTime
            .of(date, NINE_AM)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun parseDate(input: String): LocalDate? {
        // Try full RFC 3339 first; fall back to the raw 10-char date.
        runCatching { return OffsetDateTime.parse(input, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate() }
        runCatching { return LocalDate.parse(input.substring(0, minOf(10, input.length))) }
        return null
    }

    private fun firstLineOfNotes(task: GoogleTask): String =
        task.notes
            .orEmpty()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

    /**
     * Tags carried by the tasks themselves. The source list name is already the note title in
     * list-as-checklist and one-note-per-list modes, so it is not repeated as a tag.
     */
    private fun importedTagsFor(group: List<TaskToImport>): List<String> =
        group
            .flatMap { wrapper -> wrapper.tags }
            .map { tag -> tag.trim() }
            .filter { tag -> tag.isNotEmpty() }
            .distinct()

    /**
     * Reminders for a note that stands in for a whole source list. Each task keeps its own due
     * time and recurrence, so they are merged into the note's reminder slots soonest-first.
     * Remember allows [MAX_REMINDERS_PER_NOTE] per note, so only the soonest few survive; tasks
     * that are already completed contribute nothing.
     */
    private fun importedRemindersFor(group: List<TaskToImport>): List<NoteReminder> =
        group
            .filterNot { wrapper -> isCompleted(wrapper.task) }
            .mapNotNull { wrapper ->
                val reminderAt = wrapper.reminderAt ?: computeReminderAt(wrapper.task) ?: return@mapNotNull null
                NoteReminder(reminderAt = reminderAt, recurrence = wrapper.recurrence)
            }.distinct()
            .sortedBy { reminder -> reminder.reminderAt }
            .limitedToReminderSlots()

    /**
     * Flattens one source list into checklist rows, keeping subtasks under the task they belong
     * to. Sources can nest arbitrarily deep while a Remember checklist only nests one level, so
     * every descendant lands at depth 1 under its top-level ancestor, in depth-first order.
     *
     * A task whose parent is missing from this list (deleted, or filtered out of the selection)
     * is emitted as a top-level row rather than dropped.
     */
    private fun checklistItemsFor(group: List<TaskToImport>): List<PersistableChecklistItem> {
        val tasksById = group.associateBy { wrapper -> wrapper.task.id }
        val resolvedParentIds =
            group.associate { wrapper ->
                val parentId = wrapper.task.parent
                wrapper.task.id to parentId?.takeIf { it != wrapper.task.id && it in tasksById }
            }
        val childrenByParentId =
            group
                .filter { wrapper -> resolvedParentIds[wrapper.task.id] != null }
                .groupBy { wrapper -> resolvedParentIds.getValue(wrapper.task.id)!! }
                .mapValues { (_, children) -> children.sortedBy { child -> child.task.position.orEmpty() } }

        val items = mutableListOf<PersistableChecklistItem>()
        val emittedTaskIds = mutableSetOf<String>()
        var nextLocalKey = -1L
        var sortOrder = 1.0
        group
            .filter { wrapper -> resolvedParentIds[wrapper.task.id] == null }
            .sortedBy { wrapper -> wrapper.task.position.orEmpty() }
            .forEach { root ->
                val rootTitle = checklistItemTitle(root.task)
                val rootLocalKey = nextLocalKey--
                emittedTaskIds.add(root.task.id)
                if (rootTitle.isNotBlank()) {
                    items.add(
                        PersistableChecklistItem(
                            localKey = rootLocalKey,
                            text = rootTitle,
                            details = checklistItemDetails(root.task),
                            checked = isCompleted(root.task),
                            sortOrder = sortOrder++,
                        ),
                    )
                }
                val pendingDescendants = ArrayDeque(childrenByParentId[root.task.id].orEmpty().asReversed())
                while (pendingDescendants.isNotEmpty()) {
                    val descendant = pendingDescendants.removeLast()
                    // Guards against a cyclic parent chain in a hand-edited backup.
                    if (!emittedTaskIds.add(descendant.task.id)) continue
                    val descendantTitle = checklistItemTitle(descendant.task)
                    if (descendantTitle.isNotBlank()) {
                        items.add(
                            PersistableChecklistItem(
                                localKey = nextLocalKey--,
                                text = descendantTitle,
                                details = checklistItemDetails(descendant.task),
                                checked = isCompleted(descendant.task),
                                sortOrder = sortOrder++,
                                parentLocalKey = rootLocalKey.takeIf { rootTitle.isNotBlank() },
                                depth = if (rootTitle.isNotBlank()) 1 else 0,
                            ),
                        )
                    }
                    childrenByParentId[descendant.task.id].orEmpty().asReversed().forEach { grandchild ->
                        pendingDescendants.addLast(grandchild)
                    }
                }
            }
        return items
    }

    private fun checklistItemTitle(task: GoogleTask): String =
        task.title
            .orEmpty()
            .trim()
            .ifBlank { firstLineOfNotes(task) }

    private fun checklistItemDetails(task: GoogleTask): String {
        val notes = task.notes.orEmpty().trim()
        if (notes.isBlank()) return ""
        if (task.title.orEmpty().isNotBlank()) return notes
        return notes
            .lineSequence()
            .dropWhile { line -> line.isBlank() }
            .drop(1)
            .joinToString("\n")
            .trim()
    }

    private fun isCompleted(task: GoogleTask): Boolean = task.status.equals(GoogleTaskStatus.COMPLETED, ignoreCase = true)

    companion object {
        private val NINE_AM: LocalTime = LocalTime.of(9, 0)
    }
}

/** A single task plus source-specific metadata needed to create a Remember note. */
data class TaskToImport(
    val task: GoogleTask,
    val taskListId: String,
    val taskListTitle: String,
    val reminderAt: Long? = null,
    val recurrence: RecurrenceRule? = null,
    val tags: List<String> = emptyList(),
)

enum class ImportMode {
    /** Default. Each Google task becomes its own NOTE in Remember. */
    ONE_NOTE_PER_TASK,

    /** All tasks under a single Google list become one NOTE whose body is a markdown checklist. */
    GROUP_BY_LIST,

    /** All tasks under a single Google list become one LIST in Remember, with checklist rows. */
    LIST_AS_CHECKLIST,
}

data class ImportOutcome(
    val writtenCount: Int,
    val skippedAlreadyImported: Int,
    val googleTaskIdToRememberNoteId: Map<String, Long>,
)
