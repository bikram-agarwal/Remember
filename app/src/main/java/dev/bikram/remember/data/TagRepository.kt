package dev.bikram.remember.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

data class TagEditResult(
    val oldName: String,
    val newName: String,
)

class TagRepository(
    private val tagDao: TagDao,
    private val noteDao: NoteDao,
    private val database: RememberDatabase? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeActiveTags(): Flow<List<TagEntity>> = tagDao.observeActiveTags()

    fun observeActiveTagSuggestions(): Flow<List<String>> =
        observeActiveTags().map { tags ->
            tags.map { tag -> tag.name }.sortedBy { tagName -> tagName.lowercase(Locale.ROOT) }
        }

    fun observeTagColorMap(): Flow<Map<String, String>> =
        tagDao.observeAllTags().map { tags ->
            tags
                .mapNotNull { tag ->
                    val colorHex = tag.colorHex ?: return@mapNotNull null
                    tag.normalizedName to colorHex
                }.toMap()
        }

    suspend fun replaceTagsForNote(
        noteId: Long,
        tagNames: List<String>,
    ) {
        val cleanedNames = cleanUserVisibleTagNames(tagNames)
        if (database != null) {
            database.withTransaction {
                replaceTagsForNoteInTransaction(noteId, cleanedNames)
            }
        } else {
            replaceTagsForNoteInTransaction(noteId, cleanedNames)
        }
    }

    suspend fun setTagColor(
        tagName: String,
        colorHex: String?,
    ) {
        val cleanedName = tagName.trim()
        if (cleanedName.isBlank() || cleanedName == RememberReservedTags.FAVORITE) return
        val normalizedColor = colorHex?.let { normalizeHex(it) }
        if (colorHex != null && normalizedColor == null) return
        if (database != null) {
            database.withTransaction {
                val tag = createOrGetTagInTransaction(cleanedName, normalizedColor)
                tagDao.updateTag(tag.copy(colorHex = normalizedColor, updatedAt = clock()))
            }
        } else {
            val tag = createOrGetTagInTransaction(cleanedName, normalizedColor)
            tagDao.updateTag(tag.copy(colorHex = normalizedColor, updatedAt = clock()))
        }
    }

    suspend fun editTag(
        oldName: String,
        newName: String,
        colorHex: String?,
        resetColor: Boolean = false,
    ): TagEditResult? {
        val oldNormalizedName = normalizeTagName(oldName)
        val cleanedNewName = newName.trim()
        val newNormalizedName = normalizeTagName(cleanedNewName)
        if (oldNormalizedName.isBlank() || newNormalizedName.isBlank()) return null
        if (oldName.trim() == RememberReservedTags.FAVORITE || cleanedNewName == RememberReservedTags.FAVORITE) {
            return null
        }
        val normalizedColor = if (resetColor) null else colorHex?.let { normalizeHex(it) }
        if (!resetColor && colorHex != null && normalizedColor == null) return null

        var result: TagEditResult? = null
        if (database != null) {
            database.withTransaction {
                result =
                    editTagInTransaction(
                        oldNormalizedName = oldNormalizedName,
                        newName = cleanedNewName,
                        newNormalizedName = newNormalizedName,
                        colorHex = normalizedColor,
                        resetColor = resetColor,
                    )
            }
        } else {
            result =
                editTagInTransaction(
                    oldNormalizedName = oldNormalizedName,
                    newName = cleanedNewName,
                    newNormalizedName = newNormalizedName,
                    colorHex = normalizedColor,
                    resetColor = resetColor,
                )
        }
        return result
    }

    private suspend fun editTagInTransaction(
        oldNormalizedName: String,
        newName: String,
        newNormalizedName: String,
        colorHex: String?,
        resetColor: Boolean,
    ): TagEditResult? {
        val existingTag = tagDao.getByNormalizedName(oldNormalizedName) ?: return null
        val collision = tagDao.getByNormalizedName(newNormalizedName)
        if (collision != null && collision.id != existingTag.id) return null

        val nextColor = if (resetColor) null else colorHex ?: existingTag.colorHex
        val updatedTag =
            existingTag.copy(
                name = newName,
                normalizedName = newNormalizedName,
                colorHex = nextColor,
                updatedAt = clock(),
            )
        tagDao.updateTag(updatedTag)
        if (oldNormalizedName != newNormalizedName || existingTag.name != newName) {
            tagDao.noteIdsForTag(existingTag.id).forEach { noteId ->
                syncNoteTagCache(noteId)
            }
        }
        return TagEditResult(oldName = existingTag.name, newName = newName)
    }

    private suspend fun replaceTagsForNoteInTransaction(
        noteId: Long,
        tagNames: List<String>,
    ) {
        tagDao.deleteAssignmentsForNote(noteId)
        val assignments =
            tagNames.mapIndexed { tagIndex, tagName ->
                val tag = createOrGetTagInTransaction(tagName, colorHex = null)
                NoteTagCrossRef(noteId = noteId, tagId = tag.id, sortOrder = tagIndex)
            }
        if (assignments.isNotEmpty()) tagDao.insertAssignments(assignments)
        syncNoteTagCache(noteId)
    }

    private suspend fun createOrGetTagInTransaction(
        tagName: String,
        colorHex: String?,
    ): TagEntity {
        val cleanedName = tagName.trim()
        val normalizedName = normalizeTagName(cleanedName)
        tagDao.getByNormalizedName(normalizedName)?.let { existingTag ->
            val shouldAdoptDisplayName = existingTag.name != cleanedName
            val shouldAdoptColor = colorHex != null && existingTag.colorHex == null
            if (shouldAdoptDisplayName || shouldAdoptColor) {
                val updatedTag =
                    existingTag.copy(
                        name = if (shouldAdoptDisplayName) cleanedName else existingTag.name,
                        colorHex = if (shouldAdoptColor) colorHex else existingTag.colorHex,
                        updatedAt = clock(),
                    )
                tagDao.updateTag(updatedTag)
                return updatedTag
            }
            return existingTag
        }
        val now = clock()
        val insertedId =
            tagDao.insertTag(
                TagEntity(
                    name = cleanedName,
                    normalizedName = normalizedName,
                    colorHex = colorHex,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        if (insertedId > 0L) {
            return tagDao.getByNormalizedName(normalizedName)
                ?: error("Inserted tag row was not readable")
        }
        return tagDao.getByNormalizedName(normalizedName)
            ?: error("Existing tag row was not readable")
    }

    private suspend fun syncNoteTagCache(noteId: Long) {
        val note = noteDao.get(noteId)?.note ?: return
        val visibleTags = tagDao.tagsForNote(noteId).map { tag -> tag.name }
        val reservedTags = note.tags.filter { tagName -> tagName == RememberReservedTags.FAVORITE }
        noteDao.updateTagCache(noteId, visibleTags + reservedTags)
    }
}

fun normalizeTagName(tagName: String): String = tagName.trim().lowercase(Locale.ROOT)

internal fun cleanUserVisibleTagNames(tagNames: List<String>): List<String> {
    val seenNames = LinkedHashSet<String>()
    return buildList {
        tagNames.forEach { rawName ->
            val cleanedName = rawName.trim()
            if (cleanedName.isBlank() || cleanedName == RememberReservedTags.FAVORITE) {
                return@forEach
            }
            val normalizedName = normalizeTagName(cleanedName)
            if (seenNames.add(normalizedName)) add(cleanedName)
        }
    }
}
