package dev.bikram.remember.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.feedback.rememberPlayTapSound
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable

@Composable
internal fun BackupFolderSettingsToggleItem(
    title: String,
    subtitle: String,
    infoTooltipText: String? = null,
    infoContentDescription: String? = null,
    checked: Boolean,
    switchEnabled: Boolean,
    onDisabledInteraction: (() -> Unit)?,
    onCheckedChange: (Boolean) -> Unit,
) {
    val switchInteractive = switchEnabled || onDisabledInteraction != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable {
                    if (!switchEnabled) {
                        onDisabledInteraction?.invoke()
                    } else {
                        onCheckedChange(!checked)
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (infoTooltipText != null && infoContentDescription != null) {
                    SettingsInfoDropdown(
                        tipText = infoTooltipText,
                        contentDescription = infoContentDescription,
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        RememberSwitch(
            checked = checked,
            onCheckedChange = { enabled ->
                when {
                    switchEnabled -> onCheckedChange(enabled)
                    onDisabledInteraction != null && enabled -> onDisabledInteraction.invoke()
                    else -> Unit
                }
            },
            enabled = switchInteractive,
            thumbContent =
                if (checked) {
                    {
                        RememberMaterialRoundedSymbol(
                            name = "check",
                            size = SwitchDefaults.IconSize,
                            weight = FontWeight.Bold,
                        )
                    }
                } else {
                    null
                },
        )
    }
}

@Composable
internal fun SettingsInfoDropdown(
    tipText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val playTap = rememberPlayTapSound()
    Box(modifier = modifier) {
        IconButton(
            onClick = {
                playTap()
                menuExpanded = true
            },
            modifier = Modifier.size(32.dp),
        ) {
            RememberMaterialRoundedSymbol(
                name = "info",
                size = 20.dp,
                tint = iconTint,
                weight = FontWeight.Medium,
                filled = false,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(max = 260.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 236.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun BackupFolderPickerItem(
    title: String,
    subtitle: String,
    accessibilityLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val playTap = rememberPlayTapSound()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundCombinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        RememberOutlinedButton(onClick = {
            playTap()
            onClick()
        }) {
            RememberMaterialRoundedSymbol(
                name = "folder_open",
                size = 18.dp,
                weight = FontWeight.Medium,
                modifier =
                    Modifier.semantics {
                        contentDescription = accessibilityLabel
                    },
            )
        }
    }
}
