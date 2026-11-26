package dev.bikram.remember.share

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.bikram.remember.R
import dev.bikram.remember.data.DefaultNotePreferencesState
import dev.bikram.remember.data.Importance
import dev.bikram.remember.data.NoteReminder
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ReminderPreferencesState
import dev.bikram.remember.data.SnoozeType
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.domain.formatTimeOfDay
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.TitleNoNewlinesTransformation
import dev.bikram.remember.ui.common.enterMovesFocusNext
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.rememberHoistedStringTextFieldState
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.NoteCard
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalButton
import dev.bikram.remember.ui.components.RememberFilterChip
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.edit.ReminderPickerSheet
import dev.bikram.remember.ui.edit.TagEditorSheet
import dev.bikram.remember.ui.feedback.appClickable
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun ShareCaptureBottomSheet(
    title: String,
    body: String,
    stagedImages: List<StagedShareImage>,
    singleImageMode: SingleImagePlacement,
    reminders: List<NoteReminder>,
    tags: List<String>,
    availableTags: List<String>,
    importance: Importance,
    defaultNoteState: DefaultNotePreferencesState,
    reminderState: ReminderPreferencesState,
    isInitializing: Boolean,
    isSaving: Boolean,
    savedNoteId: Long?,
    savedNote: NoteWithItems?,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSingleImageModeChange: (SingleImagePlacement) -> Unit,
    onRemoveStagedImage: (String) -> Unit,
    onRemindersChange: (List<NoteReminder>) -> Unit,
    onClearReminders: () -> Unit,
    onTagsWithColorsChange: (List<String>, Map<String, String>) -> Unit,
    onEditExistingTag: (String, String, String?, Boolean) -> Unit,
    onImportanceChange: (Importance) -> Unit,
    onSave: () -> Unit,
    onOpenInEditor: () -> Unit,
    onClose: () -> Unit,
    onOpenSavedNote: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val saved = savedNoteId != null
    val context = LocalContext.current
    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagPickerOpen by rememberSaveable { mutableStateOf(false) }
    var importanceMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val landscape = isLandscape()
    val canSave = !isInitializing && !isSaving && (title.isNotBlank() || body.isNotBlank() || stagedImages.isNotEmpty())
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Expanded,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )

    AppBottomSheet(
        title =
            stringResource(
                if (saved) R.string.share_capture_saved_title else R.string.share_capture_sheet_title,
            ),
        onDismiss = if (saved) onClose else onCancel,
        sheetState = sheetState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        actionsImePadding = true,
        titleLeading = {
            if (saved) {
                RememberMaterialRoundedSymbol(
                    name = "check_circle",
                    size = if (landscape) 24.dp else 28.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    weight = FontWeight.Medium,
                )
            } else {
                AppIconImage(
                    modifier = Modifier.size(if (landscape) 24.dp else 28.dp),
                )
            }
        },
        actions = {
            if (savedNoteId != null) {
                RememberTextButton(onClick = onClose) {
                    Text(stringResource(R.string.share_capture_close))
                }
                RememberButton(onClick = { onOpenSavedNote(savedNoteId) }) {
                    Text(stringResource(R.string.share_capture_open_note))
                }
            } else {
                RememberTextButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                RememberFilledTonalButton(
                    onClick = onOpenInEditor,
                    enabled = canSave,
                ) {
                    Text(stringResource(R.string.share_capture_open_in_editor))
                }
                RememberButton(
                    onClick = onSave,
                    enabled = canSave,
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
    ) {
        // Saved: show the note exactly as the Notes list renders it (banner, body, tags, reminder...)
        // instead of the editable form.
        if (savedNote != null) {
            NoteCard(
                note = savedNote,
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            return@AppBottomSheet
        }
        ShareTextFields(
            title = title,
            body = body,
            onTitleChange = onTitleChange,
            onBodyChange = onBodyChange,
        )

        ShareImagesStatus(
            stagedImages = stagedImages,
            singleImageMode = singleImageMode,
            isInitializing = isInitializing,
            onSingleImageModeChange = onSingleImageModeChange,
            onRemoveStagedImage = onRemoveStagedImage,
        )

        Spacer(Modifier.height(12.dp))

        QuickMetadataRow(
            context = context,
            reminders = reminders,
            tags = tags,
            importance = importance,
            importanceMenuExpanded = importanceMenuExpanded,
            onOpenReminderPicker = { reminderPickerOpen = true },
            onClearReminders = onClearReminders,
            onOpenTagPicker = { tagPickerOpen = true },
            onImportanceMenuExpandedChange = { expanded -> importanceMenuExpanded = expanded },
            onImportanceChange = onImportanceChange,
        )
    }

    if (reminderPickerOpen) {
        ReminderPickerSheet(
            initialReminders = reminders,
            defaultReminderMinutesOfDay = defaultNoteState.defaultReminderMinutesOfDay,
            defaultRecurrence = defaultNoteState.defaultRecurrence,
            useDurationPresets = reminderState.snoozeType == SnoozeType.ABSOLUTE,
            onConfirm = { updated ->
                onRemindersChange(updated)
                reminderPickerOpen = false
            },
            onDismiss = { reminderPickerOpen = false },
        )
    }

    if (tagPickerOpen) {
        TagEditorSheet(
            initial = tags,
            availableTags = availableTags,
            onConfirm = { updatedTags, newColors ->
                onTagsWithColorsChange(updatedTags, newColors)
                tagPickerOpen = false
            },
            onEditExistingTag = onEditExistingTag,
            onDismiss = { tagPickerOpen = false },
        )
    }
}

@Composable
private fun ShareTextFields(
    title: String,
    body: String,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
) {
    val titleFieldState = rememberHoistedStringTextFieldState(title, onTitleChange)
    val bodyFieldState = rememberHoistedStringTextFieldState(body, onBodyChange)

    OutlinedTextField(
        state = titleFieldState,
        label = { Text(stringResource(R.string.common_title)) },
        inputTransformation = TitleNoNewlinesTransformation,
        modifier = Modifier.fillMaxWidth().enterMovesFocusNext(),
        textStyle = MaterialTheme.typography.titleMedium,
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 2),
        shape = MaterialTheme.shapes.medium,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
    )

    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        state = bodyFieldState,
        label = { Text(stringResource(R.string.share_capture_content_label)) },
        placeholder = { Text(stringResource(R.string.edit_note_body_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium,
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
        shape = MaterialTheme.shapes.medium,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareImagesStatus(
    stagedImages: List<StagedShareImage>,
    singleImageMode: SingleImagePlacement,
    isInitializing: Boolean,
    onSingleImageModeChange: (SingleImagePlacement) -> Unit,
    onRemoveStagedImage: (String) -> Unit,
) {
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
    val fadeSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())

    AnimatedVisibility(
        visible = isInitializing && stagedImages.isEmpty(),
        enter = fadeIn(fadeSpec) + expandVertically(spatialSpec),
        exit = fadeOut(fadeSpec) + shrinkVertically(spatialSpec),
    ) {
        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.share_capture_staging_images),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    AnimatedVisibility(
        visible = stagedImages.isNotEmpty(),
        enter = fadeIn(fadeSpec) + expandVertically(spatialSpec),
        exit = fadeOut(fadeSpec) + shrinkVertically(spatialSpec),
    ) {
        SharedImagesSection(
            stagedImages = stagedImages,
            singleImageMode = singleImageMode,
            onSingleImageModeChange = onSingleImageModeChange,
            onRemoveStagedImage = onRemoveStagedImage,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedImagesSection(
    stagedImages: List<StagedShareImage>,
    singleImageMode: SingleImagePlacement,
    onSingleImageModeChange: (SingleImagePlacement) -> Unit,
    onRemoveStagedImage: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                pluralStringResource(
                    if (stagedImages.all { image -> image.isImage }) {
                        R.plurals.share_capture_images_count
                    } else {
                        R.plurals.share_capture_files_count
                    },
                    stagedImages.size,
                    stagedImages.size,
                ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = stagedImages,
                key = { image -> image.id },
            ) { image ->
                SharedImageThumbnail(
                    image = image,
                    onRemove = { onRemoveStagedImage(image.id) },
                )
            }
        }

        if (stagedImages.size == 1 && stagedImages.single().isImage) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val attachSelected = singleImageMode == SingleImagePlacement.ATTACH_TO_NOTE
                RememberFilterChip(
                    selected = attachSelected,
                    onClick = { onSingleImageModeChange(SingleImagePlacement.ATTACH_TO_NOTE) },
                    leadingIcon = {
                        RememberMaterialRoundedSymbol(
                            name = if (attachSelected) "check" else "attach_file",
                            size = 18.dp,
                            weight = FontWeight.Medium,
                        )
                    },
                    label = { Text(stringResource(R.string.share_capture_image_attach)) },
                )

                val bannerSelected = singleImageMode == SingleImagePlacement.USE_AS_BANNER
                RememberFilterChip(
                    selected = bannerSelected,
                    onClick = { onSingleImageModeChange(SingleImagePlacement.USE_AS_BANNER) },
                    leadingIcon = {
                        RememberMaterialRoundedSymbol(
                            name = if (bannerSelected) "check" else "image",
                            size = 18.dp,
                            weight = FontWeight.Medium,
                        )
                    },
                    label = { Text(stringResource(R.string.share_capture_image_banner)) },
                )
            }
        }
    }
}

@Composable
private fun SharedImageThumbnail(
    image: StagedShareImage,
    onRemove: () -> Unit,
) {
    val removeDescription = stringResource(R.string.share_capture_remove_image_cd)
    Box(
        modifier =
            Modifier
                .size(76.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (image.isImage) {
            AsyncImage(
                model = image.file,
                contentDescription = image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "description",
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Medium,
                )
                Text(
                    text = image.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                    .semantics { contentDescription = removeDescription }
                    .appClickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "close",
                size = 14.dp,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                weight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickMetadataRow(
    context: Context,
    reminders: List<NoteReminder>,
    tags: List<String>,
    importance: Importance,
    importanceMenuExpanded: Boolean,
    onOpenReminderPicker: () -> Unit,
    onClearReminders: () -> Unit,
    onOpenTagPicker: () -> Unit,
    onImportanceMenuExpandedChange: (Boolean) -> Unit,
    onImportanceChange: (Importance) -> Unit,
) {
    val hasReminders = reminders.isNotEmpty()
    val clearReminderCd = stringResource(R.string.share_capture_clear_reminder_cd)
    val reminderLabel =
        if (hasReminders) {
            remember(reminders, context) { formatReminderChipSummary(context, reminders) }
        } else {
            stringResource(R.string.options_reminder)
        }

    val tagLabel =
        when (tags.size) {
            0 -> stringResource(R.string.options_tags)
            1 -> stringResource(R.string.filter_dropdown_tags_one, tags.first())
            else -> stringResource(R.string.filter_dropdown_tags_count, tags.size)
        }

    val nonDefaultImportance = importance != Importance.DEFAULT
    val importanceLabel = stringResource(importance.labelRes())

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RememberFilterChip(
            selected = hasReminders,
            onClick = onOpenReminderPicker,
            leadingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "alarm",
                    size = 18.dp,
                    weight = FontWeight.Medium,
                )
            },
            trailingIcon =
                if (hasReminders) {
                    {
                        Box(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .semantics { contentDescription = clearReminderCd }
                                    .appClickable(onClick = onClearReminders),
                            contentAlignment = Alignment.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "close",
                                size = 15.dp,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    null
                },
            label = { Text(reminderLabel) },
        )

        RememberFilterChip(
            selected = tags.isNotEmpty(),
            onClick = onOpenTagPicker,
            leadingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "label",
                    size = 18.dp,
                    weight = FontWeight.Medium,
                )
            },
            label = { Text(tagLabel) },
        )

        Box {
            RememberFilterChip(
                selected = nonDefaultImportance,
                onClick = { onImportanceMenuExpandedChange(true) },
                leadingIcon = {
                    RememberMaterialRoundedSymbol(
                        name = "flag",
                        size = 18.dp,
                        weight = FontWeight.Medium,
                    )
                },
                trailingIcon = {
                    RememberMaterialRoundedSymbol(
                        name = "arrow_drop_down",
                        size = 18.dp,
                        weight = FontWeight.Medium,
                    )
                },
                label = { Text(importanceLabel) },
            )

            DropdownMenu(
                expanded = importanceMenuExpanded,
                onDismissRequest = { onImportanceMenuExpandedChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Importance.entries.forEach { level ->
                    val isSelected = level == importance
                    RememberDropdownMenuItem(
                        text = { Text(stringResource(level.labelRes())) },
                        trailingIcon =
                            if (isSelected) {
                                {
                                    RememberMaterialRoundedSymbol(
                                        name = "check",
                                        size = 18.dp,
                                        tint = MaterialTheme.colorScheme.primary,
                                        weight = FontWeight.Medium,
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            onImportanceChange(level)
                            onImportanceMenuExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

private fun formatReminderChipSummary(
    context: Context,
    reminders: List<NoteReminder>,
): String {
    val soonest = reminders.minByOrNull { reminder -> reminder.reminderAt } ?: return ""
    val zone = ZoneId.systemDefault()
    val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(soonest.reminderAt), zone)
    val today = ZonedDateTime.now(zone).toLocalDate()
    val timeText = formatTimeOfDay(context, soonest.reminderAt)
    val baseSummary =
        when (target.toLocalDate()) {
            today -> timeText
            else -> {
                val dayText = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(target)
                "$dayText, $timeText"
            }
        }
    return if (reminders.size > 1) "$baseSummary (+${reminders.size - 1})" else baseSummary
}
