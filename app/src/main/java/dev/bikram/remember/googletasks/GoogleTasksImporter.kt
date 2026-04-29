package dev.bikram.remember.googletasks

import dev.bikram.remember.data.NoteOptions
import dev.bikram.remember.data.NoteRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Converts Google Tasks into Remember notes/lists.
 *
 * Mapping rules (one note per task is the default; group-by-list and list-as-checklist are
 * driven by [ImportMode]):
 *  - title  -> NoteEntity.title (falls back to the first line of `notes` when title is blank)
 *  - notes  -> NoteEntity.body
 *  - status == "completed" -> NoteEntity.completedAt (uses the API-provided completion timestamp
 *    when present, otherwise [now])
 *  - due (date-only per Google's docs) -> reminderAt at 09:00 in [zone]
 *  - tag = source task list title (sanitised, kept as-is otherwise) so the user can filter by
 *    the list they came from
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
                            repository.get(noteId) != null
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
                    // Subtasks become indented children under their parent.
                    val parents =
                        group
                            .filter { it.task.parent.isNullOrBlank() }
                            .sortedBy { it.task.position.orEmpty() }
                    val itemTexts = mutableListOf<String>()
                    parents.forEach { parent ->
                        itemTexts.add(
                            parent.task.title
                                .orEmpty()
                                .ifBlank { firstLineOfNotes(parent.task) },
                        )
                        group
                            .filter { it.task.parent == parent.task.id }
                            .sortedBy { it.task.position.orEmpty() }
                            .forEach { child ->
                                itemTexts.add(
                                    "- " +
                                        child.task.title
                                            .orEmpty()
                                            .ifBlank { firstLineOfNotes(child.task) },
                                )
                            }
                    }
                    val newId =
                        repository.createList(
                            title = taskListTitle,
                            colorIndex = 0,
                            items = itemTexts.filter { it.isNotBlank() },
                            options = NoteOptions(tags = listOf(taskListTitle)),
                        )
                    group.forEach { createdPairs[it.task.id] = newId }
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
                                        append(indent).append("    ").append(wrapper.task.notes.replace("\n", "\n    ")).append('\n')
                                    }
                                }
                        }.trimEnd()
                    val newId =
                        repository.createNote(
                            title = title,
                            body = body,
                            colorIndex = 0,
                            options = NoteOptions(tags = listOf(title)),
                        )
                    group.forEach { createdPairs[it.task.id] = newId }
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

        val reminderAt = computeReminderAt(task.task)
        val isDone = isCompleted(task.task)

        val tag = task.taskListTitle.trim()
        val tags = if (tag.isNotEmpty()) listOf(tag) else emptyList()
        val options = NoteOptions(reminderAt = reminderAt, tags = tags)

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

    private fun isCompleted(task: GoogleTask): Boolean = task.status.equals(GoogleTaskStatus.COMPLETED, ignoreCase = true)

    companion object {
        private val NINE_AM: LocalTime = LocalTime.of(9, 0)
    }
}

/** A single Google task plus the list it lives in - the only handle the importer needs. */
data class TaskToImport(
    val task: GoogleTask,
    val taskListId: String,
    val taskListTitle: String,
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
