package dev.bikram.remember.data

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

internal fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

internal fun encodeNoteForBackup(n: NoteWithItems): JSONObject {
    val note = n.note
    return JSONObject().apply {
        put("id", note.id)
        put("kind", note.kind.name)
        put("title", note.title)
        put("body", note.body)
        put("colorIndex", note.colorIndex)
        put("starred", note.starred)
        put("trashed", note.trashed)
        put("archived", note.archived)
        note.trashedAt?.let { put("trashedAt", it) }
        note.completedAt?.let { put("completedAt", it) }
        note.pinnedAt?.let { put("pinnedAt", it) }
        put("createdAt", note.createdAt)
        put("updatedAt", note.updatedAt)
        note.reminderAt?.let { put("reminderAt", it) }
        put("importance", note.importance.name)
        put("visibility", note.visibility.name)
        note.pictureUri?.let { put("pictureUri", it) }
        note.pictureHeroFraming?.let { put("pictureHeroFraming", it) }
        put("locked", note.locked)
        note.iconKey?.let { put("iconKey", it) }
        put(
            "actions",
            JSONArray().apply {
                note.actions.forEach { a ->
                    put(
                        JSONObject().apply {
                            put("type", a.type.name)
                            put("title", a.title)
                            put("details", a.details)
                            a.extra?.let { put("extra", it) }
                            a.iconData?.let { put("iconData", it) }
                        },
                    )
                }
            },
        )
        put("tags", JSONArray().apply { note.tags.forEach { put(it) } })
        RecurrenceRule.toJson(note.recurrence)?.let { put("recurrence", it) }
        put(
            "reminders",
            JSONArray().apply {
                note.reminders.limitedToReminderSlots().forEach { reminder ->
                    put(encodeReminderForBackup(reminder))
                }
            },
        )
        put(
            "items",
            JSONArray().apply {
                n.items.forEach { it2 ->
                    put(
                        JSONObject().apply {
                            put("id", it2.id)
                            put("text", it2.text)
                            put("details", it2.details)
                            put("checked", it2.checked)
                            put("sortOrder", it2.sortOrder)
                            it2.parentId?.let { parent -> put("parentId", parent) }
                            put("depth", it2.depth)
                        },
                    )
                }
            },
        )
        put(
            "attachments",
            JSONArray().apply {
                n.attachments.forEach { a ->
                    put(
                        JSONObject().apply {
                            put("uri", a.uri)
                            put("displayName", a.displayName)
                            a.mimeType?.let { put("mimeType", it) }
                        },
                    )
                }
            },
        )
    }
}

@Suppress("ktlint:standard:function-expression-body")
internal fun encodeReminderForBackup(reminder: NoteReminder): JSONObject {
    return JSONObject().apply {
        put("reminderAt", reminder.reminderAt)
        RecurrenceRule.toJson(reminder.recurrence)?.let { recurrenceJson ->
            put("recurrence", recurrenceJson)
        }
        reminder.originalReminderAt?.let { originalReminderAt ->
            put("originalReminderAt", originalReminderAt)
        }
    }
}

internal fun decodeNoteEntity(o: JSONObject): NoteEntity {
    val kind = NoteKind.valueOf(o.optString("kind", "NOTE"))
    return NoteEntity(
        id = o.optLong("id", 0L).takeIf { it > 0 } ?: 0L,
        kind = kind,
        title = o.optString("title", ""),
        body = o.optString("body", ""),
        colorIndex = o.optInt("colorIndex", 0),
        starred = o.optBoolean("starred", false),
        trashed = o.optBoolean("trashed", false),
        archived = o.optBoolean("archived", false),
        trashedAt = o.optLongOrNull("trashedAt"),
        completedAt = o.optLongOrNull("completedAt"),
        pinnedAt = o.optLongOrNull("pinnedAt"),
        createdAt = o.optLong("createdAt"),
        updatedAt = o.optLong("updatedAt"),
        reminderAt = o.optLongOrNull("reminderAt"),
        importance =
            runCatching { Importance.valueOf(o.optString("importance", Importance.DEFAULT.name)) }
                .getOrDefault(Importance.DEFAULT),
        visibility =
            runCatching { Visibility.valueOf(o.optString("visibility", Visibility.DEFAULT.name)) }
                .getOrDefault(Visibility.DEFAULT),
        pictureUri = o.optStringOrNull("pictureUri"),
        pictureHeroFraming = o.optStringOrNull("pictureHeroFraming"),
        locked = o.optBoolean("locked", false),
        iconKey = o.optStringOrNull("iconKey"),
        actions = o.optJSONArray("actions")?.let { decodeActions(it) } ?: emptyList(),
        tags = o.optJSONArray("tags")?.let { decodeStringArray(it) } ?: emptyList(),
        recurrence = o.optStringOrNull("recurrence")?.let { RecurrenceRule.fromJson(it) },
        reminders = o.optJSONArray("reminders")?.let { decodeReminders(it) } ?: emptyList(),
    )
}

internal fun decodeReminderFromBackup(reminderJson: JSONObject): NoteReminder? {
    val reminderAt = reminderJson.optLong("reminderAt", 0L)
    if (reminderAt <= 0L) return null
    val recurrence =
        if (reminderJson.has("recurrence") && !reminderJson.isNull("recurrence")) {
            RecurrenceRule.fromJson(reminderJson.getString("recurrence"))
        } else {
            null
        }
    val originalReminderAt =
        if (reminderJson.has("originalReminderAt") && !reminderJson.isNull("originalReminderAt")) {
            reminderJson.getLong("originalReminderAt")
        } else {
            null
        }
    return NoteReminder(
        reminderAt = reminderAt,
        recurrence = recurrence,
        originalReminderAt = originalReminderAt,
    )
}

internal fun decodeReminders(a: JSONArray): List<NoteReminder> {
    val out = ArrayList<NoteReminder>(a.length())
    for (i in 0 until a.length()) {
        val reminderJson = a.optJSONObject(i) ?: continue
        decodeReminderFromBackup(reminderJson)?.let { reminder ->
            out.add(reminder)
        }
    }
    return out.limitedToReminderSlots()
}

internal fun decodeChecklistItems(o: JSONObject): List<ChecklistItemEntity> {
    val arr = o.optJSONArray("items") ?: return emptyList()
    return List(arr.length()) { idx ->
        val jo = arr.getJSONObject(idx)
        // Legacy archives only have `position`; new archives use `sortOrder` (Double) plus
        // optional `id`, `parentId`, `depth`. Fall back gracefully so old backups keep working.
        val legacyPosition = jo.optInt("position", idx)
        val sortOrder =
            if (jo.has("sortOrder")) {
                jo.optDouble("sortOrder", legacyPosition.toDouble())
            } else {
                legacyPosition.toDouble()
            }
        val parentId: Long? =
            if (jo.has("parentId") && !jo.isNull("parentId")) {
                jo.optLong("parentId", 0L).takeIf { it != 0L }
            } else {
                null
            }
        ChecklistItemEntity(
            id = jo.optLong("id", 0L),
            noteId = 0L,
            text = jo.optString("text", ""),
            details = jo.optString("details", ""),
            checked = jo.optBoolean("checked", false),
            sortOrder = sortOrder,
            parentId = parentId,
            depth = jo.optInt("depth", if (parentId != null) 1 else 0).coerceIn(0, 1),
        )
    }.sortedBy { it.sortOrder }
}

internal fun decodeAttachmentMetas(o: JSONObject): List<NoteAttachmentEntity> {
    val arr = o.optJSONArray("attachments") ?: return emptyList()
    return List(arr.length()) { idx ->
        val a = arr.getJSONObject(idx)
        NoteAttachmentEntity(
            id = 0L,
            noteId = 0L,
            uri = a.optString("uri", ""),
            displayName = a.optString("displayName", ""),
            mimeType = a.optStringOrNull("mimeType"),
        )
    }
}

internal fun decodeActions(a: JSONArray): List<NoteAction> {
    val out = ArrayList<NoteAction>(a.length())
    for (i in 0 until a.length()) {
        val o = a.optJSONObject(i) ?: continue
        val type = runCatching { ActionType.valueOf(o.optString("type", "")) }.getOrNull() ?: continue
        out.add(
            NoteAction(
                type = type,
                title = o.optString("title", ""),
                details = o.optString("details", ""),
                extra = o.optStringOrNull("extra"),
                iconData = o.optStringOrNull("iconData"),
            ),
        )
    }
    return out
}

internal fun decodeStringArray(a: JSONArray): List<String> = List(a.length()) { a.optString(it, "") }.filter { it.isNotBlank() }
