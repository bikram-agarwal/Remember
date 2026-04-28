package dev.bikram.remember.googletasks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.util.Locale

data class GoogleTasksTakeoutImport(
    val taskLists: List<GoogleTaskList>,
    val tasks: List<TaskToImport>,
    val stats: GoogleTasksTakeoutStats,
)

data class GoogleTasksTakeoutStats(
    val originalTaskCount: Int,
    val importedTaskCount: Int,
    val collapsedInstanceCount: Int,
    val recurringSeriesCount: Int,
)

class GoogleTasksTakeoutParser(
    private val json: Json = GoogleTasksApi.DefaultJson,
    private val now: () -> Instant = Instant::now,
) {
    fun parse(text: String): GoogleTasksTakeoutImport {
        val root = json.parseToJsonElement(text)
        val listObjects = findListObjects(root)
        if (listObjects.isNotEmpty()) {
            return parseListObjects(listObjects)
        }

        val taskObjects = findTaskArray(root)
        if (taskObjects.isNotEmpty()) {
            val defaultList =
                GoogleTaskList(
                    id = TAKEOUT_PREFIX + "default",
                    title = DEFAULT_LIST_TITLE,
                )
            val parsed = parseTasksForList(defaultList, taskObjects, emptyMap())
            return GoogleTasksTakeoutImport(
                taskLists = listOf(defaultList),
                tasks = parsed.tasks,
                stats = parsed.stats,
            )
        }

        throw IllegalArgumentException("No Google Tasks lists or tasks found")
    }

    private fun findListObjects(root: JsonElement): List<JsonObject> {
        val rootObject = root as? JsonObject
        val candidateArrays =
            buildList {
                if (root is JsonArray) add(root)
                if (rootObject != null) {
                    listOf("items", "taskLists", "task_lists", "lists").forEach { key ->
                        (rootObject[key] as? JsonArray)?.let { add(it) }
                    }
                }
            }
        return candidateArrays
            .flatMap { array -> array.mapNotNull { element -> element as? JsonObject } }
            .filter { objectValue -> findNestedTaskArray(objectValue).isNotEmpty() }
    }

    private fun findTaskArray(root: JsonElement): List<JsonObject> {
        val rootObject = root as? JsonObject ?: return emptyList()
        val directTasks = findNestedTaskArray(rootObject)
        if (directTasks.isNotEmpty()) return directTasks
        return (rootObject["items"] as? JsonArray)
            ?.mapNotNull { element -> element as? JsonObject }
            ?.filter { objectValue -> looksLikeTask(objectValue) }
            .orEmpty()
    }

    private fun parseListObjects(listObjects: List<JsonObject>): GoogleTasksTakeoutImport {
        val taskLists = mutableListOf<GoogleTaskList>()
        val tasks = mutableListOf<TaskToImport>()
        var originalTaskCount = 0
        var collapsedInstanceCount = 0
        var recurringSeriesCount = 0
        listObjects.forEachIndexed { listIndex, listObject ->
            val rawListId =
                stringOrNull(listObject, "id", "taskListId", "listId")
                    ?: "list_$listIndex"
            val listTitle =
                stringOrNull(listObject, "title", "name")
                    ?: DEFAULT_LIST_TITLE
            val taskList =
                GoogleTaskList(
                    id = TAKEOUT_PREFIX + rawListId,
                    title = listTitle,
                    updated = stringOrNull(listObject, "updated", "updatedAt"),
                )
            val taskObjects = findNestedTaskArray(listObject)
            val recurrences = recurrenceMap(listObject)
            val parsed = parseTasksForList(taskList, taskObjects, recurrences)
            taskLists.add(taskList)
            tasks.addAll(parsed.tasks)
            originalTaskCount += parsed.stats.originalTaskCount
            collapsedInstanceCount += parsed.stats.collapsedInstanceCount
            recurringSeriesCount += parsed.stats.recurringSeriesCount
        }
        return GoogleTasksTakeoutImport(
            taskLists = taskLists,
            tasks = tasks,
            stats =
                GoogleTasksTakeoutStats(
                    originalTaskCount = originalTaskCount,
                    importedTaskCount = tasks.size,
                    collapsedInstanceCount = collapsedInstanceCount,
                    recurringSeriesCount = recurringSeriesCount,
                ),
        )
    }

    private fun parseTasksForList(
        taskList: GoogleTaskList,
        taskObjects: List<JsonObject>,
        recurrences: Map<String, TakeoutRecurrence>,
    ): ParsedTakeoutTasks {
        val rawToPrefixedIds = mutableMapOf<String, String>()
        taskObjects.forEachIndexed { taskIndex, taskObject ->
            val rawTaskId =
                stringOrNull(taskObject, "id", "taskId")
                    ?: "task_$taskIndex"
            rawToPrefixedIds[rawTaskId] = "${TAKEOUT_PREFIX}${taskList.id.removePrefix(TAKEOUT_PREFIX)}:$rawTaskId"
        }
        val externalReferenceToRecurrenceId =
            recurrences.values
                .mapNotNull { recurrence ->
                    recurrence.externalReferenceTaskId?.let { externalReferenceTaskId ->
                        externalReferenceTaskId to recurrence.id
                    }
                }.toMap()

        val parsedTasks =
            taskObjects.mapIndexedNotNull { taskIndex, taskObject ->
                val rawTaskId =
                    stringOrNull(taskObject, "id", "taskId")
                        ?: "task_$taskIndex"
                val taskId = rawToPrefixedIds.getValue(rawTaskId)
                val rawParentId = stringOrNull(taskObject, "parent", "parentId")
                val completedAt = stringOrNull(taskObject, "completed", "completedAt", "completionTime")
                val explicitStatus = stringOrNull(taskObject, "status")
                val completed = booleanOrNull(taskObject, "completed")
                val status =
                    when {
                        explicitStatus != null -> explicitStatus
                        completed == true || completedAt != null -> GoogleTaskStatus.COMPLETED
                        else -> GoogleTaskStatus.NEEDS_ACTION
                    }
                val dueOrScheduled =
                    stringOrNull(taskObject, "due", "dueDate")
                        ?: scheduledTimeOrNull(taskObject)
                val recurrenceId =
                    stringOrNull(taskObject, "task_recurrence_id", "recurrenceId")
                        ?: externalReferenceToRecurrenceId[rawTaskId]
                val title =
                    stringOrNull(taskObject, "title", "name")
                        ?: recurrences[recurrenceId]?.title
                val notes = stringOrNull(taskObject, "notes", "description", "details")
                if (title.isNullOrBlank() && notes.isNullOrBlank()) {
                    return@mapIndexedNotNull null
                }
                ParsedTakeoutTask(
                    rawTaskId = rawTaskId,
                    rawParentId = rawParentId,
                    recurrenceId = recurrenceId,
                    fallbackGroupKey =
                        fallbackGroupKey(
                            taskListId = taskList.id,
                            title = title,
                            notes = notes,
                            parentId = rawParentId,
                        ),
                    scheduledInstant = parseInstantOrNull(dueOrScheduled),
                    completedInstant = parseInstantOrNull(completedAt),
                    updatedInstant = parseInstantOrNull(stringOrNull(taskObject, "updated", "updatedAt")),
                    originalIndex = taskIndex,
                    wrapper =
                        TaskToImport(
                            taskListId = taskList.id,
                            taskListTitle = taskList.title,
                            task =
                                GoogleTask(
                                    id = taskId,
                                    title = title,
                                    notes = notes,
                                    status = status,
                                    due = dueOrScheduled,
                                    completed = completedAt,
                                    parent =
                                        rawParentId?.let { parentId ->
                                            rawToPrefixedIds[parentId]
                                                ?: "${TAKEOUT_PREFIX}${taskList.id.removePrefix(TAKEOUT_PREFIX)}:$parentId"
                                        },
                                    position = stringOrNull(taskObject, "position", "sortOrder", "order"),
                                    deleted = booleanOrNull(taskObject, "deleted"),
                                    hidden = booleanOrNull(taskObject, "hidden"),
                                ),
                        ),
                )
            }
        return collapseRecurringTasks(
            taskList = taskList,
            parsedTasks = parsedTasks,
            originalTaskCount = taskObjects.size,
        )
    }

    private fun collapseRecurringTasks(
        taskList: GoogleTaskList,
        parsedTasks: List<ParsedTakeoutTask>,
        originalTaskCount: Int,
    ): ParsedTakeoutTasks {
        val selectedTasks = mutableListOf<ParsedTakeoutTask>()
        val rawIdToSelectedId = mutableMapOf<String, String>()
        var recurringSeriesCount = 0
        var collapsedInstanceCount = 0

        val groupedByExplicitRecurrence =
            parsedTasks
                .filter { task -> task.recurrenceId != null }
                .groupBy { task -> task.recurrenceId!! }
        val tasksHandledByExplicitRecurrence = mutableSetOf<ParsedTakeoutTask>()

        groupedByExplicitRecurrence.forEach { (recurrenceId, group) ->
            if (group.size > 1) {
                val selected =
                    representativeFor(group).withSeriesId(
                        "takeout-series:${taskList.id.removePrefix(TAKEOUT_PREFIX)}:$recurrenceId",
                    )
                selectedTasks.add(selected)
                group.forEach { task -> rawIdToSelectedId[task.rawTaskId] = selected.wrapper.task.id }
                recurringSeriesCount++
                collapsedInstanceCount += group.size - 1
            } else {
                selectedTasks.add(group.first())
            }
            tasksHandledByExplicitRecurrence.addAll(group)
        }

        parsedTasks
            .filterNot { task -> task in tasksHandledByExplicitRecurrence }
            .groupBy { task -> task.fallbackGroupKey }
            .forEach { (fallbackKey, group) ->
                val shouldCollapseFallback =
                    fallbackKey.isNotBlank() &&
                        group.size > 1 &&
                        group.all { task -> task.recurrenceId == null } &&
                        group.map { task -> task.scheduledInstant ?: task.completedInstant }.distinct().size > 1
                if (shouldCollapseFallback) {
                    val selected =
                        representativeFor(group).withSeriesId(
                            "takeout-series:${taskList.id.removePrefix(TAKEOUT_PREFIX)}:${Integer.toHexString(fallbackKey.hashCode())}",
                        )
                    selectedTasks.add(selected)
                    group.forEach { task -> rawIdToSelectedId[task.rawTaskId] = selected.wrapper.task.id }
                    recurringSeriesCount++
                    collapsedInstanceCount += group.size - 1
                } else {
                    selectedTasks.addAll(group)
                }
            }

        val dedupedSelectedTasks = mutableListOf<ParsedTakeoutTask>()
        selectedTasks
            .groupBy { task -> task.fallbackGroupKey }
            .forEach { (fallbackKey, group) ->
                val shouldMergeHistoricalSeries =
                    fallbackKey.isNotBlank() &&
                        group.size > 1 &&
                        group.count { task -> task.recurrenceId != null } > 1 &&
                        group.all { task -> isCompleted(task) }
                if (shouldMergeHistoricalSeries) {
                    val selected =
                        representativeFor(group).withSeriesId(
                            "takeout-series:${taskList.id.removePrefix(TAKEOUT_PREFIX)}:historical:${Integer.toHexString(fallbackKey.hashCode())}",
                        )
                    val oldSelectedIds = group.map { task -> task.wrapper.task.id }.toSet()
                    rawIdToSelectedId.entries.forEach { entry ->
                        if (entry.value in oldSelectedIds) {
                            entry.setValue(selected.wrapper.task.id)
                        }
                    }
                    group.forEach { task -> rawIdToSelectedId[task.rawTaskId] = selected.wrapper.task.id }
                    dedupedSelectedTasks.add(selected)
                    collapsedInstanceCount += group.size - 1
                } else {
                    dedupedSelectedTasks.addAll(group)
                }
            }

        selectedTasks.forEach { task ->
            rawIdToSelectedId.putIfAbsent(task.rawTaskId, task.wrapper.task.id)
        }

        val finalTasks =
            dedupedSelectedTasks
                .sortedBy { task -> task.originalIndex }
                .map { task ->
                    val remappedParent =
                        task.rawParentId?.let { rawParentId ->
                            rawIdToSelectedId[rawParentId] ?: task.wrapper.task.parent
                        }
                    if (remappedParent == task.wrapper.task.parent) {
                        task.wrapper
                    } else {
                        task.wrapper.copy(task = task.wrapper.task.copy(parent = remappedParent))
                    }
                }
        return ParsedTakeoutTasks(
            tasks = finalTasks,
            stats =
                GoogleTasksTakeoutStats(
                    originalTaskCount = originalTaskCount,
                    importedTaskCount = finalTasks.size,
                    collapsedInstanceCount = collapsedInstanceCount,
                    recurringSeriesCount = recurringSeriesCount,
                ),
        )
    }

    private fun representativeFor(group: List<ParsedTakeoutTask>): ParsedTakeoutTask {
        val currentInstant = now()
        val incomplete = group.filterNot { task -> isCompleted(task) }
        incomplete
            .filter { task -> task.scheduledInstant != null && task.scheduledInstant >= currentInstant }
            .minByOrNull { task -> task.scheduledInstant!! }
            ?.let { return it }
        incomplete
            .maxByOrNull { task -> task.scheduledInstant ?: Instant.MIN }
            ?.let { return it }
        return group.maxWithOrNull(
            compareBy<ParsedTakeoutTask> { task ->
                task.updatedInstant ?: task.completedInstant ?: task.scheduledInstant ?: Instant.MIN
            }.thenBy { task -> task.originalIndex },
        ) ?: group.minBy { task -> task.originalIndex }
    }

    private fun ParsedTakeoutTask.withSeriesId(seriesId: String): ParsedTakeoutTask = copy(wrapper = wrapper.copy(task = wrapper.task.copy(id = seriesId)))

    private fun isCompleted(task: ParsedTakeoutTask): Boolean =
        task.wrapper.task.status
            .equals(GoogleTaskStatus.COMPLETED, ignoreCase = true)

    private fun findNestedTaskArray(objectValue: JsonObject): List<JsonObject> {
        listOf("tasks", "Tasks", "items").forEach { key ->
            val array = objectValue[key] as? JsonArray ?: return@forEach
            val taskObjects =
                array
                    .mapNotNull { element -> element as? JsonObject }
                    .filter { taskObject -> looksLikeTask(taskObject) }
            if (taskObjects.isNotEmpty()) return taskObjects
        }
        return emptyList()
    }

    private fun looksLikeTask(objectValue: JsonObject): Boolean =
        listOf("title", "notes", "status", "due", "completed", "parent", "position")
            .any { key -> objectValue.containsKey(key) }

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

    private fun booleanOrNull(
        objectValue: JsonObject,
        key: String,
    ): Boolean? = (objectValue[key] as? JsonPrimitive)?.booleanOrNull

    private fun scheduledTimeOrNull(objectValue: JsonObject): String? =
        when (val scheduledTime = objectValue["scheduled_time"]) {
            is JsonArray -> {
                val entries = scheduledTime.mapNotNull { element -> element as? JsonObject }
                entries
                    .firstOrNull { entry -> booleanOrNull(entry, "current") == true }
                    ?.let { entry -> stringOrNull(entry, "start", "time", "date") }
                    ?: entries.firstOrNull()?.let { entry -> stringOrNull(entry, "start", "time", "date") }
                    ?: (scheduledTime.firstOrNull() as? JsonPrimitive)?.contentOrNull
            }
            is JsonObject -> stringOrNull(scheduledTime, "start", "time", "date")
            else -> (scheduledTime as? JsonPrimitive)?.contentOrNull
        }

    private fun recurrenceMap(listObject: JsonObject): Map<String, TakeoutRecurrence> {
        val recurrences = listObject["recurrences"] as? JsonArray ?: return emptyMap()
        return recurrences
            .mapNotNull { element -> element as? JsonObject }
            .mapNotNull { recurrenceObject ->
                val id = stringOrNull(recurrenceObject, "id") ?: return@mapNotNull null
                id to
                    TakeoutRecurrence(
                        id = id,
                        title = stringOrNull(recurrenceObject, "title"),
                        externalReferenceTaskId = stringOrNull(recurrenceObject, "external_reference_task_id"),
                    )
            }.toMap()
    }

    private fun parseInstantOrNull(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
    }

    private fun fallbackGroupKey(
        taskListId: String,
        title: String?,
        notes: String?,
        parentId: String?,
    ): String {
        val normalizedTitle = title.orEmpty().trim().lowercase(Locale.ROOT)
        if (normalizedTitle.isBlank()) return ""
        return listOf(
            taskListId,
            normalizedTitle,
            notes.orEmpty().trim().lowercase(Locale.ROOT),
            parentId.orEmpty(),
        ).joinToString("|")
    }

    private data class ParsedTakeoutTasks(
        val tasks: List<TaskToImport>,
        val stats: GoogleTasksTakeoutStats,
    )

    private data class TakeoutRecurrence(
        val id: String,
        val title: String?,
        val externalReferenceTaskId: String?,
    )

    private data class ParsedTakeoutTask(
        val rawTaskId: String,
        val rawParentId: String?,
        val recurrenceId: String?,
        val fallbackGroupKey: String,
        val scheduledInstant: Instant?,
        val completedInstant: Instant?,
        val updatedInstant: Instant?,
        val originalIndex: Int,
        val wrapper: TaskToImport,
    )

    companion object {
        private const val TAKEOUT_PREFIX = "takeout:"
        private const val DEFAULT_LIST_TITLE = "Google Tasks"
    }
}
