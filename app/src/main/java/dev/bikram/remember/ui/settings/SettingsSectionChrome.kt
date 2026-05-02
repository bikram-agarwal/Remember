package dev.bikram.remember.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.feedback.rememberPlayTapSound
import dev.bikram.remember.ui.feedback.tapSoundClickable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsExpandableSection(
    sectionKey: String,
    materialSymbolName: String,
    title: String,
    collapsedSectionKeys: Set<String>,
    onCollapsedSectionKeysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val collapsed = sectionKey in collapsedSectionKeys
    val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Column(modifier = modifier) {
        SettingsSectionHeader(
            materialSymbolName = materialSymbolName,
            title = title,
            collapsed = collapsed,
            onToggle = {
                onCollapsedSectionKeysChange(
                    if (collapsed) {
                        collapsedSectionKeys - sectionKey
                    } else {
                        collapsedSectionKeys + sectionKey
                    },
                )
            },
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter =
                expandVertically(
                    animationSpec = spatialSpec,
                    expandFrom = Alignment.Top,
                ) + fadeIn(fadeInSpec),
            exit =
                shrinkVertically(
                    animationSpec = spatialSpec,
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(fadeOutSpec),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
internal fun SettingsStaticSectionHeader(
    materialSymbolName: String,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSectionHeader(
    materialSymbolName: String,
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>(),
        label = "settings_section_chevron_rotation",
    )
    val contentDescriptionExpand = stringResource(R.string.section_expand_cd, title)
    val contentDescriptionCollapse = stringResource(R.string.section_collapse_cd, title)
    val headerInteractionSource = remember { MutableInteractionSource() }
    val playTap = rememberPlayTapSound()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .semantics {
                    contentDescription =
                        if (collapsed) {
                            contentDescriptionExpand
                        } else {
                            contentDescriptionCollapse
                        }
                }.clickable(
                    interactionSource = headerInteractionSource,
                    indication = LocalIndication.current,
                ) {
                    playTap()
                    onToggle()
                }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
            weight = FontWeight.Medium,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        RememberMaterialRoundedSymbol(
            name = "chevron_right",
            size = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.width(16.dp))
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
        RememberSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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

internal fun isPermissionLinked(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        Build.MANUFACTURER.lowercase() in setOf("google", "samsung", "nothing", "motorola")

@Composable
internal fun SettingsToggleSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDisabledInteraction: (() -> Unit)? = null,
) {
    Box {
        RememberSwitch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
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
        if (!enabled && onDisabledInteraction != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.extraExtraLarge)
                        .tapSoundClickable { onDisabledInteraction() },
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    materialSymbolName: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled) 1f else 0.55f
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { modifier -> if (enabled) modifier.tapSoundClickable { onChange(!checked) } else modifier }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RememberMaterialRoundedSymbol(
            name = materialSymbolName,
            size = 24.dp,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            weight = FontWeight.Medium,
        )
        Spacer(Modifier.size(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RememberSwitch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
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
