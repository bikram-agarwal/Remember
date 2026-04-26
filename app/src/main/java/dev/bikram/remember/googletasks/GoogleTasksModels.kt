package dev.bikram.remember.googletasks

import kotlinx.serialization.Serializable

/**
 * Wire-format DTOs for the Google Tasks REST API v1.
 *
 * Reference: https://developers.google.com/workspace/tasks/reference/rest
 *
 * Only the fields we actually consume are declared. The serializer is configured to ignore
 * unknown keys, so future additions on the server side do not break parsing.
 */

@Serializable
data class GoogleTaskListsResponse(
    val items: List<GoogleTaskList> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class GoogleTaskList(
    val id: String,
    val title: String,
    val updated: String? = null,
)

@Serializable
data class GoogleTasksResponse(
    val items: List<GoogleTask> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class GoogleTask(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    /**
     * Status. Either "needsAction" or "completed". We treat any other value as needsAction.
     */
    val status: String? = null,
    /**
     * RFC 3339 date. Per Google docs the time portion of the timestamp is always discarded
     * server-side, so we only ever read a date here. We map it to 09:00 local on import.
     */
    val due: String? = null,
    /**
     * RFC 3339 timestamp recording when the task was completed. Optional.
     */
    val completed: String? = null,
    /**
     * Parent task id when this is a subtask. One level of nesting is supported by Google Tasks.
     */
    val parent: String? = null,
    /**
     * Lexicographic position string used by Google Tasks to order siblings. We use it for
     * stable sorting only; we do not interpret the value.
     */
    val position: String? = null,
    val deleted: Boolean? = null,
    val hidden: Boolean? = null,
)

/**
 * Status constants. Google currently returns lower-camelCase strings but the codebase keeps a
 * single source of truth here so a typo does not silently classify everything as not-done.
 */
internal object GoogleTaskStatus {
    const val NEEDS_ACTION = "needsAction"
    const val COMPLETED = "completed"
}
