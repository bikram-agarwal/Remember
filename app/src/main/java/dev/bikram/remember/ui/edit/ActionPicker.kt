package dev.bikram.remember.ui.edit

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import dev.bikram.remember.R
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.NoteAction
import dev.bikram.remember.data.dataLabelRes
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.data.recycleNoteActionIconBitmap
import dev.bikram.remember.data.toNoteActionIconData
import dev.bikram.remember.data.toNoteActionIconDrawable
import dev.bikram.remember.ui.common.AppBottomSheet
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.rememberBottomSheetStateWithUnsavedChanges
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.components.RememberUnsavedChangesDialog
import dev.bikram.remember.ui.feedback.appClickable

private const val LEGACY_CONTACT_MANUAL_ENTRY_EXTRA = "__remember_manual_contact_entry__"

fun ActionType.materialSymbolName(): String =
    when (this) {
        ActionType.CALL_NUMBER -> "call"
        ActionType.SEND_MESSAGE -> "send"
        ActionType.SEND_EMAIL -> "mail"
        ActionType.GET_DIRECTIONS -> "directions"
        ActionType.OPEN_LINK -> "link"
        ActionType.OPEN_APP -> "apps"
        ActionType.OPEN_SHORTCUT -> "app_shortcut"
        ActionType.COPY_TO_CLIPBOARD -> "content_copy"
        ActionType.SHARE_CONTENT -> "share"
        ActionType.MARK_AS_DONE -> "check"
        ActionType.SNOOZE -> "snooze"
    }

private enum class ActionPickerScreen {
    ChooseType,
    EditAction,
    PickApp,
    PickShortcutProvider,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPicker(
    current: List<NoteAction>,
    onConfirm: (List<NoteAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val resources = LocalResources.current
    val initialAction = remember(current) { current.firstOrNull() }
    var screen by remember(initialAction) {
        mutableStateOf(
            if (initialAction == null) {
                ActionPickerScreen.ChooseType
            } else {
                ActionPickerScreen.EditAction
            },
        )
    }
    var selectedType by remember(initialAction) { mutableStateOf(initialAction?.type) }
    var title by remember(initialAction) { mutableStateOf(initialAction?.title.orEmpty()) }
    var details by remember(initialAction) { mutableStateOf(initialAction?.details.orEmpty()) }
    var extra by remember(initialAction) { mutableStateOf(initialAction?.extra) }
    var targetIcon by remember(initialAction) { mutableStateOf<Drawable?>(null) }
    val savedShortcutIcon =
        remember(initialAction?.iconData, resources) {
            initialAction?.iconData.toNoteActionIconDrawable(resources)
        }
    DisposableEffect(savedShortcutIcon) {
        val iconToRecycle = savedShortcutIcon
        onDispose {
            iconToRecycle?.recycleNoteActionIconBitmap()
        }
    }

    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    val hasChanges = title != (initialAction?.title.orEmpty()) || details != (initialAction?.details.orEmpty()) || selectedType != initialAction?.type
    val sheetState =
        rememberBottomSheetStateWithUnsavedChanges(
            isDirty = hasChanges,
            onShowDialog = { showUnsavedDialog = true },
        )

    val targetDisplayName =
        when (selectedType) {
            ActionType.OPEN_APP ->
                if (details.isBlank()) {
                    stringResource(R.string.actions_pick_app)
                } else {
                    appLabel(packageManager, details) ?: extra?.takeIf { it.isNotBlank() } ?: details
                }
            ActionType.OPEN_SHORTCUT ->
                if (details.isBlank()) {
                    stringResource(R.string.actions_pick_shortcut)
                } else {
                    extra?.takeIf { it.isNotBlank() } ?: title.ifBlank { details.take(80) }
                }
            else -> ""
        }
    val targetDisplayIcon =
        remember(selectedType, details, targetIcon, savedShortcutIcon) {
            when (selectedType) {
                ActionType.CALL_NUMBER,
                ActionType.SEND_MESSAGE,
                ActionType.SEND_EMAIL,
                -> targetIcon
                ActionType.GET_DIRECTIONS -> null
                ActionType.OPEN_LINK -> null
                ActionType.OPEN_APP -> targetIcon ?: appIcon(packageManager, details)
                ActionType.OPEN_SHORTCUT -> targetIcon ?: savedShortcutIcon ?: shortcutFallbackIcon(packageManager, details)
                ActionType.COPY_TO_CLIPBOARD -> null
                ActionType.SHARE_CONTENT -> null
                ActionType.MARK_AS_DONE -> null
                ActionType.SNOOZE -> null
                null -> null
            }
        }

    fun selectType(type: ActionType) {
        selectedType = type
        title = resources.getString(type.labelRes())
        details = ""
        extra = null
        targetIcon = null
        screen =
            when (type) {
                ActionType.OPEN_APP -> ActionPickerScreen.PickApp
                ActionType.OPEN_SHORTCUT -> ActionPickerScreen.PickShortcutProvider
                else -> ActionPickerScreen.EditAction
            }
    }

    fun resetAction() {
        selectedType = null
        title = ""
        details = ""
        extra = null
        targetIcon = null
        screen = ActionPickerScreen.EditAction
    }

    fun actionToSave(): NoteAction? {
        val type = selectedType ?: return null
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return null
        val cleanDetails = details.trim()
        if (type.requiresDetails() && cleanDetails.isBlank()) return null
        if (!type.isValidDetails(cleanDetails)) return null
        val cleanExtra =
            when (type) {
                ActionType.OPEN_APP -> appLabel(packageManager, cleanDetails) ?: extra?.trim()?.takeIf { it.isNotBlank() }
                ActionType.OPEN_SHORTCUT -> extra?.trim()?.takeIf { it.isNotBlank() }
                ActionType.CALL_NUMBER,
                ActionType.SEND_MESSAGE,
                ActionType.SEND_EMAIL,
                -> extra?.trim()?.takeIf { it.isNotBlank() && it != LEGACY_CONTACT_MANUAL_ENTRY_EXTRA }
                else -> extra?.trim()?.takeIf { it.isNotBlank() }
            }
        val cleanIconData =
            if (type == ActionType.OPEN_SHORTCUT) {
                targetIcon?.toNoteActionIconData()
                    ?: initialAction
                        ?.takeIf { it.type == ActionType.OPEN_SHORTCUT && it.details == cleanDetails }
                        ?.iconData
            } else {
                null
            }
        return NoteAction(type, cleanTitle, cleanDetails, extra = cleanExtra, iconData = cleanIconData)
    }

    val phoneLauncher =
        rememberPhonePickLauncher { pick ->
            val verb =
                if (selectedType == ActionType.SEND_MESSAGE) {
                    resources.getString(R.string.action_contact_verb_message)
                } else {
                    resources.getString(R.string.action_contact_verb_call)
                }
            if (pick.displayName.isNotBlank()) {
                title =
                    resources.getString(
                        R.string.actions_contact_title_format,
                        verb,
                        pick.displayName,
                    )
            }
            details = pick.data
            extra = pick.displayName.takeIf { it.isNotBlank() }
            targetIcon = pick.avatar
        }
    val emailLauncher =
        rememberEmailPickLauncher { pick ->
            if (pick.displayName.isNotBlank()) {
                title =
                    resources.getString(
                        R.string.actions_contact_title_format,
                        resources.getString(R.string.action_contact_verb_email),
                        pick.displayName,
                    )
            }
            details = pick.data
            extra = pick.displayName.takeIf { it.isNotBlank() }
            targetIcon = pick.avatar
        }
    val shortcutLauncher =
        rememberShortcutPickLauncher { pick ->
            val newTitle = pick.label.ifBlank { title.ifBlank { resources.getString(ActionType.OPEN_SHORTCUT.labelRes()) } }
            title = newTitle
            details = pick.intentUri
            extra = pick.label.ifBlank { newTitle }
            targetIcon = pick.icon
            screen = ActionPickerScreen.EditAction
        }
    val saveEnabled =
        when {
            screen == ActionPickerScreen.ChooseType -> false
            selectedType == null -> true
            else -> actionToSave() != null
        }
    val pickingInSheet =
        screen == ActionPickerScreen.PickApp ||
            screen == ActionPickerScreen.PickShortcutProvider

    AppBottomSheet(
        title = stringResource(R.string.options_actions),
        subtitle = if (screen == ActionPickerScreen.ChooseType) stringResource(R.string.actions_sheet_subtitle) else null,
        sheetState = sheetState,
        onDismiss = {
            if (hasChanges) {
                showUnsavedDialog = true
            } else {
                onDismiss()
            }
        },
        scrollable = !pickingInSheet,
        // The type-chooser is a plain tappable list; drop the body's vertical padding (each row
        // already carries its own) so more options are visible at once, especially in landscape.
        contentPadding =
            if (screen == ActionPickerScreen.ChooseType) {
                PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            } else {
                PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            },
        actionsImePadding = screen == ActionPickerScreen.EditAction,
        // No action bar on the plain type-list screen: a lone Cancel button wastes a whole row
        // (especially in landscape) and is redundant — the sheet dismisses via the drag handle,
        // scrim tap, or back gesture. Later screens keep Cancel/Reset/Save.
        actions =
            if (screen == ActionPickerScreen.ChooseType) {
                null
            } else {
                {
                    RememberTextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    if (screen == ActionPickerScreen.EditAction && selectedType != null) {
                        RememberTextButton(onClick = ::resetAction) { Text(stringResource(R.string.action_reset)) }
                    }
                    if (screen == ActionPickerScreen.EditAction) {
                        RememberButton(
                            enabled = saveEnabled,
                            onClick = { onConfirm(actionToSave()?.let { listOf(it) } ?: emptyList()) },
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            },
    ) {
        when (screen) {
            ActionPickerScreen.ChooseType ->
                ActionTypeChooser(onPick = ::selectType)
            ActionPickerScreen.EditAction ->
                ActionEditor(
                    type = selectedType,
                    title = title,
                    onTitleChange = { title = it },
                    details = details,
                    targetDisplayName = targetDisplayName,
                    targetDisplayIcon = targetDisplayIcon,
                    onDetailsChange = {
                        details = it
                        if (selectedType.isContactAction()) {
                            extra = null
                            targetIcon = null
                        }
                    },
                    onChangeType = {
                        screen = ActionPickerScreen.ChooseType
                    },
                    onPickContact = {
                        when (selectedType) {
                            ActionType.CALL_NUMBER,
                            ActionType.SEND_MESSAGE,
                            -> phoneLauncher.launch(phonePickIntent())
                            ActionType.SEND_EMAIL -> emailLauncher.launch(emailPickIntent())
                            else -> Unit
                        }
                    },
                    onPickApp = {
                        screen = ActionPickerScreen.PickApp
                    },
                    onPickShortcutProvider = {
                        screen = ActionPickerScreen.PickShortcutProvider
                    },
                )
            ActionPickerScreen.PickApp ->
                InSheetAppPicker(
                    title = stringResource(R.string.actions_pick_app),
                    queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    onBack = {
                        screen = ActionPickerScreen.EditAction
                    },
                    onPick = { app ->
                        val newTitle = resources.getString(R.string.actions_open_app_title, app.label)
                        title = newTitle
                        details = app.packageName
                        extra = app.label
                        targetIcon = app.iconBitmap?.toDrawable(resources)
                        screen = ActionPickerScreen.EditAction
                    },
                )
            ActionPickerScreen.PickShortcutProvider ->
                InSheetAppPicker(
                    title = stringResource(R.string.actions_pick_app_shortcut),
                    queryIntent = Intent(Intent.ACTION_CREATE_SHORTCUT),
                    onBack = {
                        screen = ActionPickerScreen.EditAction
                    },
                    onPick = { app ->
                        screen = ActionPickerScreen.EditAction
                        shortcutLauncher(app.componentName)
                    },
                )
        }
    }

    if (showUnsavedDialog) {
        RememberUnsavedChangesDialog(
            onConfirm = {
                showUnsavedDialog = false
                onDismiss()
            },
            onDismiss = { showUnsavedDialog = false },
        )
    }
}

@Composable
private fun ActionTypeChooser(
    onPick: (ActionType) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ActionType.entries
            .filter { it != ActionType.MARK_AS_DONE && it != ActionType.SNOOZE }
            .forEach { type ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .appClickable { onPick(type) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = type.materialSymbolName(),
                        size = 22.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Medium,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text(
                        stringResource(type.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
    }
}

@Composable
private fun ActionEditor(
    type: ActionType?,
    title: String,
    onTitleChange: (String) -> Unit,
    details: String,
    targetDisplayName: String,
    targetDisplayIcon: Drawable?,
    onDetailsChange: (String) -> Unit,
    onChangeType: () -> Unit,
    onPickContact: () -> Unit,
    onPickApp: () -> Unit,
    onPickShortcutProvider: () -> Unit,
) {
    val detailsError = actionDetailsError(type, details)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActionNavigationRow(
            iconName = type?.materialSymbolName() ?: "close",
            label = stringResource(R.string.actions_field_action_type),
            value = if (type == null) stringResource(R.string.common_none) else stringResource(type.labelRes()),
            prominent = true,
            onClick = onChangeType,
        )
        when (type) {
            ActionType.CALL_NUMBER,
            ActionType.SEND_MESSAGE,
            ActionType.SEND_EMAIL,
            -> {
                ActionEditableRow(
                    iconName = type.materialSymbolName(),
                    icon = targetDisplayIcon,
                    label = stringResource(type.dataLabelRes()),
                    value = details,
                    onValueChange = onDetailsChange,
                    placeholder = stringResource(R.string.actions_contact_input_placeholder),
                    keyboardOptions =
                        if (type == ActionType.SEND_EMAIL) {
                            KeyboardOptions(keyboardType = KeyboardType.Email)
                        } else {
                            KeyboardOptions(keyboardType = KeyboardType.Phone)
                        },
                    errorText = detailsError,
                    trailingIconName = "contacts",
                    leadingTone = ActionRowTone.SurfaceVariant,
                    trailingTone = ActionRowTone.Primary,
                    onTrailingClick = onPickContact,
                )
            }
            ActionType.OPEN_APP -> {
                ActionNavigationRow(
                    iconName = "apps",
                    icon = targetDisplayIcon,
                    label = stringResource(R.string.actions_target_app),
                    value = targetDisplayName,
                    valuePlaceholder = details.isBlank(),
                    leadingTone = ActionRowTone.SurfaceVariant,
                    onClick = onPickApp,
                )
            }
            ActionType.OPEN_SHORTCUT -> {
                ActionNavigationRow(
                    iconName = "app_shortcut",
                    icon = targetDisplayIcon,
                    label = stringResource(R.string.actions_target_shortcut),
                    value = targetDisplayName,
                    valuePlaceholder = details.isBlank(),
                    leadingTone = ActionRowTone.SurfaceVariant,
                    onClick = onPickShortcutProvider,
                )
            }
            ActionType.COPY_TO_CLIPBOARD,
            ActionType.SHARE_CONTENT,
            ActionType.MARK_AS_DONE,
            ActionType.SNOOZE,
            -> Unit
            null -> Unit
            else -> {
                ActionEditableRow(
                    iconName = type.materialSymbolName(),
                    label = stringResource(type.dataLabelRes()),
                    value = details,
                    onValueChange = onDetailsChange,
                    placeholder = stringResource(type.dataLabelRes()),
                    errorText = detailsError,
                    leadingTone = ActionRowTone.SurfaceVariant,
                    keyboardOptions =
                        if (type == ActionType.OPEN_LINK) {
                            KeyboardOptions(keyboardType = KeyboardType.Uri)
                        } else {
                            KeyboardOptions.Default
                        },
                )
            }
        }
        if (type != null) {
            ActionEditableRow(
                iconName = "edit",
                label = stringResource(R.string.actions_field_button_label),
                value = title,
                onValueChange = onTitleChange,
                placeholder = stringResource(type.labelRes()),
                leadingTone = ActionRowTone.Tertiary,
            )
        }
    }
}

private enum class ActionRowTone {
    Primary,
    PrimarySubtle,
    SurfaceVariant,
    Tertiary,
}

private data class ActionToneColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun actionToneColors(tone: ActionRowTone): ActionToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        ActionRowTone.Primary ->
            ActionToneColors(
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
            )
        ActionRowTone.PrimarySubtle ->
            ActionToneColors(
                container = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                content = scheme.onPrimaryContainer,
            )
        ActionRowTone.SurfaceVariant ->
            ActionToneColors(
                container = scheme.surfaceVariant,
                content = scheme.onSurfaceVariant,
            )
        ActionRowTone.Tertiary ->
            ActionToneColors(
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
            )
    }
}

@Composable
private fun ActionNavigationRow(
    iconName: String,
    icon: Drawable? = null,
    label: String,
    value: String,
    onClick: () -> Unit,
    valuePlaceholder: Boolean = false,
    errorText: String? = null,
    trailingLabel: String? = null,
    prominent: Boolean = false,
    leadingTone: ActionRowTone = ActionRowTone.Primary,
    onTrailingClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (prominent) scheme.primaryContainer else scheme.surfaceContainerHigh
    val contentColor = if (prominent) scheme.onPrimaryContainer else scheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .appClickable(onClick = onClick)
                        .padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionRowIcon(
                    iconName = iconName,
                    icon = icon,
                    tone = if (prominent) ActionRowTone.PrimarySubtle else leadingTone,
                )
                ActionRowText(
                    label = label,
                    value = value,
                    valuePlaceholder = valuePlaceholder,
                    isError = errorText != null,
                    modifier = Modifier.weight(1f),
                )
                if (trailingLabel != null && onTrailingClick != null) {
                    RememberTextButton(onClick = onTrailingClick) {
                        Text(trailingLabel)
                    }
                }
                ActionTrailingIcon(
                    iconName = "chevron_right",
                    tone = if (prominent) ActionRowTone.PrimarySubtle else ActionRowTone.SurfaceVariant,
                    onClick = onClick,
                )
            }
        }
        ActionRowErrorText(errorText)
    }
}

@Composable
private fun ActionEditableRow(
    iconName: String,
    icon: Drawable? = null,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    errorText: String? = null,
    trailingLabel: String? = null,
    leadingTone: ActionRowTone = ActionRowTone.Tertiary,
    trailingTone: ActionRowTone = ActionRowTone.SurfaceVariant,
    trailingIconName: String = "chevron_right",
    onTrailingClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Surface(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionRowIcon(iconName = iconName, icon = icon, tone = leadingTone)
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        textStyle =
                            MaterialTheme.typography.titleMedium.copy(
                                color =
                                    if (errorText != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (value.isBlank()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
                if (trailingLabel != null && onTrailingClick != null) {
                    RememberTextButton(onClick = onTrailingClick) {
                        Text(trailingLabel)
                    }
                } else if (onTrailingClick != null) {
                    ActionTrailingIcon(
                        iconName = trailingIconName,
                        tone = trailingTone,
                        onClick = onTrailingClick,
                    )
                }
            }
        }
        ActionRowErrorText(errorText)
    }
}

@Composable
private fun ActionTrailingIcon(
    iconName: String,
    tone: ActionRowTone,
    onClick: (() -> Unit)? = null,
) {
    val colors = actionToneColors(tone)
    RememberFilledTonalIconButton(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.size(32.dp),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.container,
                contentColor = colors.content,
                disabledContainerColor = colors.container,
                disabledContentColor = colors.content,
            ),
    ) {
        RememberMaterialRoundedSymbol(
            name = iconName,
            size = 20.dp,
            tint = colors.content,
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionRowIcon(
    iconName: String,
    icon: Drawable? = null,
    tone: ActionRowTone,
) {
    val painter =
        remember(icon) {
            icon?.let {
                runCatching {
                    BitmapPainter(it.toBitmap(96, 96).asImageBitmap())
                }.getOrNull()
            }
        }
    val colors = actionToneColors(tone)
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colors.container,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(40.dp),
        ) {}
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        } else {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = 20.dp,
                tint = colors.content,
                weight = FontWeight.Medium,
            )
        }
    }
    Spacer(Modifier.size(18.dp))
}

@Composable
private fun ActionRowText(
    label: String,
    value: String,
    valuePlaceholder: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color =
                when {
                    isError -> MaterialTheme.colorScheme.error
                    valuePlaceholder -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionRowErrorText(errorText: String?) {
    if (errorText != null) {
        Text(
            errorText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 52.dp, end = 8.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun InSheetAppPicker(
    title: String,
    queryIntent: Intent,
    onBack: () -> Unit,
    onPick: (AppChoice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            RememberTextButton(onClick = onBack) {
                Text(stringResource(R.string.common_back))
            }
        }
        AppPickerContent(
            queryIntent = queryIntent,
            onPick = onPick,
        )
    }
}

private fun ActionType.requiresDetails(): Boolean =
    when (this) {
        ActionType.CALL_NUMBER,
        ActionType.SEND_MESSAGE,
        ActionType.SEND_EMAIL,
        ActionType.GET_DIRECTIONS,
        ActionType.OPEN_LINK,
        ActionType.OPEN_APP,
        ActionType.OPEN_SHORTCUT,
        -> true
        ActionType.COPY_TO_CLIPBOARD,
        ActionType.SHARE_CONTENT,
        ActionType.MARK_AS_DONE,
        ActionType.SNOOZE,
        -> false
    }

private fun ActionType.isValidDetails(value: String): Boolean =
    when (this) {
        ActionType.CALL_NUMBER,
        ActionType.SEND_MESSAGE,
        -> value.isBlank() || value.isValidPhoneActionValue()
        ActionType.SEND_EMAIL -> value.isBlank() || Patterns.EMAIL_ADDRESS.matcher(value).matches()
        else -> true
    }

@Composable
private fun actionDetailsError(
    type: ActionType?,
    value: String,
): String? {
    val trimmed = value.trim()
    if (type == null || trimmed.isBlank() || type.isValidDetails(trimmed)) return null
    return when (type) {
        ActionType.CALL_NUMBER,
        ActionType.SEND_MESSAGE,
        -> if (trimmed.any { it.isDigit() }) stringResource(R.string.actions_error_phone_number) else null
        ActionType.SEND_EMAIL -> stringResource(R.string.actions_error_email_address)
        else -> null
    }
}

private fun String.isValidPhoneActionValue(): Boolean =
    trim().let { value ->
        value.any { it.isDigit() } &&
            value.matches(
                Regex(
                    pattern = """^\+?[0-9\s().-]+(?:\s*(?:x|ext\.?)\s*[0-9]+)?$""",
                    option = RegexOption.IGNORE_CASE,
                ),
            )
    }

private fun ActionType?.isContactAction(): Boolean =
    this == ActionType.CALL_NUMBER ||
        this == ActionType.SEND_MESSAGE ||
        this == ActionType.SEND_EMAIL

private fun appLabel(
    packageManager: PackageManager,
    packageName: String,
): String? =
    if (packageName.isBlank()) {
        null
    } else {
        runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

private fun appIcon(
    packageManager: PackageManager,
    packageName: String,
): Drawable? =
    if (packageName.isBlank()) {
        null
    } else {
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

private fun shortcutFallbackIcon(
    packageManager: PackageManager,
    intentUri: String,
): Drawable? {
    if (intentUri.isBlank()) return null
    val intent =
        runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
            .getOrNull() ?: return null
    val packageName = intent.component?.packageName ?: intent.`package`
    return packageName?.let { appIcon(packageManager, it) }
}
