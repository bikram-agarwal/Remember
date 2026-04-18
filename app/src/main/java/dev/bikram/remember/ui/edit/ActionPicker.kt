package dev.bikram.remember.ui.edit

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.bikram.remember.ui.common.AppBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.data.ActionType
import dev.bikram.remember.data.dataLabelRes
import dev.bikram.remember.data.labelRes
import dev.bikram.remember.data.NoteAction

fun ActionType.icon(): ImageVector = when (this) {
    ActionType.CALL_NUMBER -> Icons.Filled.Phone
    ActionType.SEND_MESSAGE -> Icons.AutoMirrored.Filled.Send
    ActionType.SEND_EMAIL -> Icons.Filled.Email
    ActionType.GET_DIRECTIONS -> Icons.Filled.Navigation
    ActionType.OPEN_LINK -> Icons.Filled.Link
    ActionType.OPEN_APP -> Icons.Filled.Apps
    ActionType.OPEN_SHORTCUT -> Icons.Filled.AppShortcut
    ActionType.COPY_TO_CLIPBOARD -> Icons.Filled.ContentCopy
    ActionType.SHARE_CONTENT -> Icons.Filled.Share
    ActionType.MARK_AS_DONE -> Icons.Filled.Check
}

@Composable
private fun ActionType.dataLabelText(): String = stringResource(dataLabelRes())

@Composable
fun ActionPicker(
    current: List<NoteAction>,
    onConfirm: (List<NoteAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var editorType by rememberSaveable { mutableStateOf<ActionType?>(null) }
    var typePickerOpen by rememberSaveable { mutableStateOf(false) }

    AppBottomSheet(
        title = stringResource(R.string.actions_sheet_title),
        subtitle = stringResource(R.string.actions_sheet_subtitle),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            TextButton(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.common_save)) }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (draft.isEmpty()) {
                Text(
                    stringResource(R.string.actions_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                draft.forEachIndexed { idx, action ->
                    ActionRow(
                        action = action,
                        onRemove = { draft = draft.toMutableList().apply { removeAt(idx) } },
                    )
                }
            }
            if (draft.size < 3) {
                AddActionRow(onClick = { typePickerOpen = true })
            }
        }
    }

    if (typePickerOpen) {
        TypePickerDialog(
            onPick = { type ->
                typePickerOpen = false
                editorType = type
            },
            onDismiss = { typePickerOpen = false },
        )
    }
    editorType?.let { t ->
        DetailEditorDialog(
            type = t,
            onConfirm = { action ->
                draft = (draft + action).take(3)
                editorType = null
            },
            onDismiss = { editorType = null },
        )
    }
}

@Composable
private fun ActionRow(
    action: NoteAction,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                action.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    action.title.ifBlank { stringResource(action.type.labelRes()) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                val summary = action.extra?.ifBlank { null } ?: action.details
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.common_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddActionRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.actions_add_sheet_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun TypePickerDialog(
    onPick: (ActionType) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = stringResource(R.string.actions_add_sheet_title),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ActionType.entries.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(t) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        t.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Text(
                        stringResource(t.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailEditorDialog(
    type: ActionType,
    onConfirm: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    when (type) {
        ActionType.CALL_NUMBER, ActionType.SEND_MESSAGE ->
            ContactBackedEditor(
                type = type,
                pickWith = { launcher -> launcher.launch(phonePickIntent()) },
                launcherFactory = { onPicked -> rememberPhonePickLauncher(onPicked) },
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        ActionType.SEND_EMAIL ->
            ContactBackedEditor(
                type = type,
                pickWith = { launcher -> launcher.launch(emailPickIntent()) },
                launcherFactory = { onPicked -> rememberEmailPickLauncher(onPicked) },
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        ActionType.OPEN_APP -> AppBackedEditor(type = type, onConfirm = onConfirm, onDismiss = onDismiss)
        ActionType.OPEN_SHORTCUT -> ShortcutBackedEditor(type = type, onConfirm = onConfirm, onDismiss = onDismiss)
        ActionType.MARK_AS_DONE -> SimpleEditor(type = type, showData = false, onConfirm = onConfirm, onDismiss = onDismiss)
        else -> SimpleEditor(type = type, showData = true, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun SimpleEditor(
    type: ActionType,
    showData: Boolean,
    onConfirm: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(type) { mutableStateOf(context.getString(type.labelRes())) }
    var data by rememberSaveable { mutableStateOf("") }
    val ready = title.isNotBlank() && (!showData || data.isNotBlank())
    EditorShell(
        type = type,
        body = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.common_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showData) {
                OutlinedTextField(
                    value = data,
                    onValueChange = { data = it },
                    label = { Text(type.dataLabelText()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        readyToSave = ready,
        onSave = {
            onConfirm(NoteAction(type, title.trim(), data.trim()))
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ContactBackedEditor(
    type: ActionType,
    pickWith: (androidx.activity.result.ActivityResultLauncher<Intent>) -> Unit,
    launcherFactory: @Composable ((ContactPick) -> Unit) -> androidx.activity.result.ActivityResultLauncher<Intent>,
    onConfirm: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(type) { mutableStateOf(context.getString(type.labelRes())) }
    var data by rememberSaveable { mutableStateOf("") }
    val launcher = launcherFactory { pick ->
        val prefixRes = when (type) {
            ActionType.CALL_NUMBER -> R.string.action_contact_verb_call
            ActionType.SEND_MESSAGE -> R.string.action_contact_verb_message
            ActionType.SEND_EMAIL -> R.string.action_contact_verb_email
            else -> null
        }
        if (prefixRes != null && pick.displayName.isNotBlank()) {
            title = context.getString(
                R.string.actions_contact_title_format,
                context.getString(prefixRes),
                pick.displayName,
            )
        }
        data = pick.data
    }
    val ready = title.isNotBlank() && data.isNotBlank()
    EditorShell(
        type = type,
        body = {
            OutlinedButton(
                onClick = { pickWith(launcher) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContactPhone, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.actions_pick_contacts))
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.common_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = data,
                onValueChange = { data = it },
                label = { Text(type.dataLabelText()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        readyToSave = ready,
        onSave = { onConfirm(NoteAction(type, title.trim(), data.trim())) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun AppBackedEditor(
    type: ActionType,
    onConfirm: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(type) { mutableStateOf(context.getString(type.labelRes())) }
    var pkg by rememberSaveable { mutableStateOf("") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val ready = title.isNotBlank() && pkg.isNotBlank()
    EditorShell(
        type = type,
        body = {
            OutlinedButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Apps, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (pkg.isBlank()) {
                        stringResource(R.string.actions_pick_app)
                    } else {
                        stringResource(R.string.actions_change_app)
                    },
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.common_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (pkg.isNotBlank()) {
                Text(
                    pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        readyToSave = ready,
        onSave = { onConfirm(NoteAction(type, title.trim(), pkg.trim(), extra = title.trim())) },
        onDismiss = onDismiss,
    )
    if (pickerOpen) {
        AppPickerDialog(
            title = stringResource(R.string.actions_pick_app),
            queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            onPick = { app ->
                pkg = app.packageName
                val defaultTitle = context.getString(type.labelRes())
                if (title.isBlank() || title == defaultTitle) {
                    title = context.getString(R.string.actions_open_app_title, app.label.toString())
                }
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ShortcutBackedEditor(
    type: ActionType,
    onConfirm: (NoteAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable(type) { mutableStateOf(context.getString(type.labelRes())) }
    var uri by rememberSaveable { mutableStateOf("") }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    val pickShortcut = rememberShortcutPickLauncher { pickedUri, label ->
        uri = pickedUri
        if (label.isNotBlank()) title = label
    }
    val ready = title.isNotBlank() && uri.isNotBlank()
    EditorShell(
        type = type,
        body = {
            OutlinedButton(
                onClick = { appPickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AppShortcut, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (uri.isBlank()) {
                        stringResource(R.string.actions_pick_shortcut)
                    } else {
                        stringResource(R.string.actions_change_shortcut)
                    },
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.common_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uri.isNotBlank()) {
                Text(
                    uri.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        readyToSave = ready,
        onSave = { onConfirm(NoteAction(type, title.trim(), uri, extra = title.trim())) },
        onDismiss = onDismiss,
    )
    if (appPickerOpen) {
        AppPickerDialog(
            title = stringResource(R.string.actions_pick_app_shortcut),
            queryIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).addCategory(Intent.CATEGORY_DEFAULT),
            onPick = { app ->
                appPickerOpen = false
                pickShortcut(app.componentName)
            },
            onDismiss = { appPickerOpen = false },
        )
    }
}

@Composable
private fun EditorShell(
    type: ActionType,
    body: @Composable () -> Unit,
    readyToSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        title = stringResource(type.labelRes()),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            TextButton(enabled = readyToSave, onClick = onSave) { Text(stringResource(R.string.common_add)) }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(type.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            body()
        }
    }
}
