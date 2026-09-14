package dev.bikram.remember.googletasks

import dev.bikram.remember.data.MonthlyMode
import dev.bikram.remember.data.RecurrenceEndKind
import dev.bikram.remember.data.RecurrenceRule
import dev.bikram.remember.data.RecurrenceUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

enum class ManualTaskImportSource {
    GOOGLE_TAKEOUT,
    TASKS_ORG,
}

class ManualTaskImportParser(
    private val json: Json = GoogleTasksApi.DefaultJson,
) {
    fun parse(text: String): GoogleTasksTakeoutImport {
        val root = json.parseToJsonElement(text)
        if (TasksOrgBackupParser.looksLikeBackup(root)) {
            return TasksOrgBackupParser(json).parse(root)
        }
        return GoogleTasksTakeoutParser(json).parse(root)
    }
}

class TasksOrgBackupParser(
    private val json: Json = GoogleTasksApi.DefaultJson,
) {
    fun parse(text: String): GoogleTasksTakeoutImport {
        require(text.isNotBlank()) { "The selected file is empty" }
        return parse(json.parseToJsonElement(text))
    }

    internal fun parse(root: JsonElement): GoogleTasksTakeoutImport {
        require(looksLikeBackup(root)) { "This is not a Tasks.org backup" }
        val data = (root as JsonObject)["data"] as JsonObject
        val calendarNames =
            (data["caldavCalendars"] as? JsonArray)
                .orEmpty()
                .mapNotNull { element -> element as? JsonObject }
                .mapNotNull { calendar ->
                    val calendarId = stringOrNull(calendar, "uuid") ?: return@mapNotNull null
                    calendarId to (stringOrNull(calendar, "name") ?: DEFAULT_LIST_TITLE)
                }.toMap()

        val taskWrappers =
            (data["tasks"] as? JsonArray)
                .orEmpty()
                .mapNotNull { element -> element as? JsonObject }
        val canonicalIdsByAlias = canonicalIdsByAlias(taskWrappers)
        val parsedTasks =
            taskWrappers.mapIndexedNotNull { taskIndex, wrapper ->
                parseTask(wrapper, taskIndex, calendarNames, canonicalIdsByAlias)
            }
        val taskLists =
            parsedTasks
                .map { task -> GoogleTaskList(id = task.taskListId, title = task.taskListTitle) }
                .distinctBy { taskList -> taskList.id }

        return GoogleTasksTakeoutImport(
            taskLists = taskLists,
            tasks = parsedTasks,
            stats =
                GoogleTasksTakeoutStats(
                    originalTaskCount = taskWrappers.size,
                    importedTaskCount = parsedTasks.size,
                    collapsedInstanceCount = 0,
                    recurringSeriesCount = 0,
                ),
            source = ManualTaskImportSource.TASKS_ORG,
        )
    }

    private fun parseTask(
        wrapper: JsonObject,
        taskIndex: Int,
        calendarNames: Map<String, String>,
        canonicalIdsByAlias: Map<String, String>,
    ): TaskToImport? {
        val taskObject = wrapper["task"] as? JsonObject ?: return null
        val title = stringOrNull(taskObject, "title")
        val notes = stringOrNull(taskObject, "notes")
        if (title.isNullOrBlank() && notes.isNullOrBlank()) return null

        val taskId = "$TASK_PREFIX${rawTaskId(wrapper, taskIndex)}"
        val calendarId =
            syncRows(wrapper).firstNotNullOfOrNull { syncRow -> stringOrNull(syncRow, "calendar", "listId") }
        val rawListId = calendarId ?: DEFAULT_LIST_ID
        val taskListId = "$LIST_PREFIX$rawListId"
        val taskListTitle = calendarNames[calendarId] ?: DEFAULT_LIST_TITLE
        val dueDate = positiveLongOrNull(taskObject, "dueDate")
        val completionDate = positiveLongOrNull(taskObject, "completionDate")
        // Tasks.org marks both the task's own id and its parent link as transient, so neither is
        // in the backup. Parentage lives on the sync rows instead: a child's `remoteParent` holds
        // the PARENT's sync-row uid, which is a different value from the parent's task `remoteId`.
        val parentId =
            syncRows(wrapper)
                .firstNotNullOfOrNull { syncRow -> stringOrNull(syncRow, "remoteParent") }
                ?.let { parentAlias -> canonicalIdsByAlias[parentAlias] }
                ?.takeIf { resolvedParentId -> resolvedParentId != taskId }
        val tags =
            (wrapper["tags"] as? JsonArray)
                .orEmpty()
                .mapNotNull { element -> element as? JsonObject }
                .mapNotNull { tag -> stringOrNull(tag, "name") }
                .distinct()

        return TaskToImport(
            task =
                GoogleTask(
                    id = taskId,
                    title = title,
                    notes = notes,
                    status =
                        if (completionDate != null) {
                            GoogleTaskStatus.COMPLETED
                        } else {
                            GoogleTaskStatus.NEEDS_ACTION
                        },
                    due = dueDate?.let { timestamp -> Instant.ofEpochMilli(timestamp).toString() },
                    completed = completionDate?.let { timestamp -> Instant.ofEpochMilli(timestamp).toString() },
                    parent = parentId,
                    position = taskIndex.toString().padStart(10, '0'),
                ),
            taskListId = taskListId,
            taskListTitle = taskListTitle,
            reminderAt = dueDate,
            recurrence =
                if (completionDate == null) {
                    parseRecurrence(stringOrNull(taskObject, "recurrence"), dueDate)
                } else {
                    null
                },
            tags = tags,
        )
    }

    /**
     * Maps every uid a task can be referenced by - its own `remoteId`/`id` plus the uid of each
     * CalDAV / Google sync row attached to it - onto the id we hand the importer, so a child's
     * `remoteParent` resolves to the right parent regardless of which uid it was written with.
     */
    private fun canonicalIdsByAlias(taskWrappers: List<JsonObject>): Map<String, String> {
        val aliases = mutableMapOf<String, String>()
        taskWrappers.forEachIndexed { taskIndex, wrapper ->
            val taskId = "$TASK_PREFIX${rawTaskId(wrapper, taskIndex)}"
            syncRows(wrapper).forEach { syncRow ->
                stringOrNull(syncRow, "remoteId")?.let { alias -> aliases[alias] = taskId }
            }
            val taskObject = wrapper["task"] as? JsonObject ?: return@forEachIndexed
            listOf("remoteId", "id").forEach { key ->
                stringOrNull(taskObject, key)?.let { alias -> aliases[alias] = taskId }
            }
        }
        return aliases
    }

    private fun rawTaskId(
        wrapper: JsonObject,
        taskIndex: Int,
    ): String {
        val taskObject = wrapper["task"] as? JsonObject
        return taskObject?.let { stringOrNull(it, "remoteId", "id") } ?: "task_$taskIndex"
    }

    /** CalDAV rows plus the legacy Google Tasks rows, which carry the list and parent linkage. */
    private fun syncRows(wrapper: JsonObject): List<JsonObject> =
        listOf("caldavTasks", "google")
            .flatMap { key -> (wrapper[key] as? JsonArray).orEmpty() }
            .mapNotNull { element -> element as? JsonObject }

    private fun parseRecurrence(
        rawRule: String?,
        dueDate: Long?,
    ): RecurrenceRule? {
        if (rawRule.isNullOrBlank() || dueDate == null) return null
        val entries =
            rawRule
                .split(';')
                .mapNotNull { component ->
                    val separator = component.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        component.substring(0, separator).uppercase(Locale.ROOT) to
                            component.substring(separator + 1)
                    }
                }.toMap()
        val unit =
            when (entries["FREQ"]?.uppercase(Locale.ROOT)) {
                "HOURLY" -> RecurrenceUnit.HOUR
                "DAILY" -> RecurrenceUnit.DAY
                "WEEKLY" -> RecurrenceUnit.WEEK
                "MONTHLY" -> RecurrenceUnit.MONTH
                "YEARLY" -> RecurrenceUnit.YEAR
                else -> return null
            }
        val interval = entries["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val daysOfWeek =
            if (unit == RecurrenceUnit.WEEK) {
                entries["BYDAY"]
                    ?.split(',')
                    ?.mapNotNull { day -> calendarDay(day.takeLast(2)) }
                    ?.toSet()
                    .orEmpty()
            } else {
                emptySet()
            }
        val monthlyMode =
            if (unit == RecurrenceUnit.MONTH) {
                monthlyMode(entries, dueDate)
            } else {
                null
            }
        val count = entries["COUNT"]?.toIntOrNull()?.takeIf { value -> value > 0 }
        val endDate = parseUntil(entries["UNTIL"])
        val endKind =
            when {
                count != null -> RecurrenceEndKind.AFTER_COUNT
                endDate != null -> RecurrenceEndKind.ON_DATE
                else -> RecurrenceEndKind.NEVER
            }
        return RecurrenceRule(
            unit = unit,
            interval = interval,
            daysOfWeek = daysOfWeek,
            monthlyMode = monthlyMode,
            endKind = endKind,
            endDate = endDate,
            endCount = count,
        )
    }

    private fun monthlyMode(
        entries: Map<String, String>,
        dueDate: Long,
    ): MonthlyMode? {
        val byMonthDay = entries["BYMONTHDAY"]?.split(',')?.firstNotNullOfOrNull { value -> value.toIntOrNull() }
        if (byMonthDay != null && byMonthDay in 1..31) {
            return MonthlyMode.ByDayOfMonth(byMonthDay)
        }
        val byDay = entries["BYDAY"]?.split(',')?.firstOrNull() ?: return null
        val weekday = calendarDay(byDay.takeLast(2)) ?: return null
        val explicitOrdinal = byDay.dropLast(2).toIntOrNull()
        val setPosition = entries["BYSETPOS"]?.toIntOrNull()
        val ordinal =
            when (val candidate = explicitOrdinal ?: setPosition) {
                null ->
                    Calendar
                        .getInstance()
                        .apply { timeInMillis = dueDate }
                        .get(Calendar.DAY_OF_MONTH)
                        .let { dayOfMonth -> ((dayOfMonth - 1) / 7) + 1 }
                -1 -> 5
                in 1..4 -> candidate
                else -> return null
            }
        return MonthlyMode.ByNthWeekday(ordinal = ordinal, weekday = weekday)
    }

    private fun parseUntil(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        runCatching {
            return Instant.parse(value).toEpochMilli()
        }
        runCatching {
            return DateTimeFormatter
                .ofPattern("yyyyMMdd'T'HHmmssX", Locale.ROOT)
                .parse(value, Instant::from)
                .toEpochMilli()
        }
        runCatching {
            return LocalDate
                .parse(value, DateTimeFormatter.BASIC_ISO_DATE)
                .atTime(23, 59, 59)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }
        return null
    }

    private fun calendarDay(value: String): Int? =
        when (value.uppercase(Locale.ROOT)) {
            "SU" -> Calendar.SUNDAY
            "MO" -> Calendar.MONDAY
            "TU" -> Calendar.TUESDAY
            "WE" -> Calendar.WEDNESDAY
            "TH" -> Calendar.THURSDAY
            "FR" -> Calendar.FRIDAY
            "SA" -> Calendar.SATURDAY
            else -> null
        }

    private fun stringOrNull(
        objectValue: JsonObject,
        vararg keys: String,
    ): String? {
        keys.forEach { key ->
            val value = (objectValue[key] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun positiveLongOrNull(
        objectValue: JsonObject,
        key: String,
    ): Long? =
        (objectValue[key] as? JsonPrimitive)
            ?.let { value -> value.longOrNull ?: value.intOrNull?.toLong() }
            ?.takeIf { value -> value > 0L }

    companion object {
        private const val TASK_PREFIX = "tasks-org:"
        private const val LIST_PREFIX = "tasks-org-list:"
        private const val DEFAULT_LIST_ID = "default"
        private const val DEFAULT_LIST_TITLE = "Tasks.org"

        internal fun looksLikeBackup(root: JsonElement): Boolean {
            val rootObject = root as? JsonObject ?: return false
            val data = rootObject["data"] as? JsonObject ?: return false
            val taskWrappers = data["tasks"] as? JsonArray ?: return false
            return rootObject.containsKey("version") &&
                taskWrappers.any { element ->
                    val wrapper = element as? JsonObject
                    wrapper?.get("task") is JsonObject
                }
        }
    }
}
