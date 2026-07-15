package dev.bikram.remember.data

internal fun checklistSearchText(items: Iterable<String>): String =
    items
        .asSequence()
        .map { item -> item.trim() }
        .filter { item -> item.isNotEmpty() }
        .joinToString(" ")

internal fun attachmentSearchText(attachments: Iterable<NoteAttachmentEntity>): String =
    attachments
        .asSequence()
        .map { attachment -> attachment.displayName.trim() }
        .filter { name -> name.isNotEmpty() }
        .joinToString(" ")

internal fun actionsSearchText(actions: Iterable<NoteAction>): String =
    actions
        .asSequence()
        .flatMap { action -> sequenceOf(action.title, action.details) }
        .map { text -> text.trim() }
        .filter { text -> text.isNotEmpty() }
        .joinToString(" ")

internal fun actionsSearchTextFromJson(actionsJson: String): String {
    if (actionsJson.isBlank()) return ""
    val converters = Converters()
    return actionsSearchText(converters.toActions(actionsJson))
}
