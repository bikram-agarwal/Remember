package dev.bikram.remember.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

@Immutable
data class AlertChromeSummary(
    val count: Int,
    val iconName: String,
    val isNotificationPriority: Boolean,
)

fun alertChromeSummary(
    updateState: UpdateChromeState,
    blockedReminderCount: Int,
): AlertChromeSummary {
    val hasNotificationAlert = blockedReminderCount > 0
    val hasUpdateAlert = updateState != UpdateChromeState.Hidden
    return AlertChromeSummary(
        count = (if (hasNotificationAlert) 1 else 0) + (if (hasUpdateAlert) 1 else 0),
        iconName =
            when {
                hasNotificationAlert -> "notifications_off"
                updateState == UpdateChromeState.ReadyToInstall -> "download_done"
                hasUpdateAlert -> "download"
                else -> "notifications"
            },
        isNotificationPriority = hasNotificationAlert,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlertFloatingFab(
    summary: AlertChromeSummary,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (summary.count <= 0) return

    val label = stringResource(R.string.main_alert_fab_label)
    val scheme = MaterialTheme.colorScheme
    val closedContainerColor =
        when {
            summary.isNotificationPriority -> scheme.error
            else -> scheme.primaryContainer
        }
    val closedContentColor =
        when {
            summary.isNotificationPriority -> scheme.onError
            else -> scheme.onPrimaryContainer
        }
    val openContainerColor = scheme.surfaceVariant
    val openContentColor = scheme.onSurfaceVariant
    val contentColor by animateColorAsState(
        targetValue = if (expanded) openContentColor else closedContentColor,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec()),
        label = "alert_fab_icon_color",
    )
    val fabMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val shapeProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "alert_fab_shape_morph",
    )
    val fabShape = MorphPolygonShape(fabMorph, shapeProgress)
    val density = LocalDensity.current
    val iconTravelPx = with(density) { 14.dp.toPx() }
    val alertIconAlpha = 1f - shapeProgress
    val chevronAlpha = shapeProgress
    Box(modifier = modifier) {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = { onClick() },
            containerColor =
                ToggleFloatingActionButtonDefaults.containerColor(
                    initialColor = closedContainerColor,
                    finalColor = openContainerColor,
                ),
            modifier =
                Modifier
                    .shadow(
                        elevation = 2.dp,
                        shape = fabShape,
                        clip = false,
                    ).clip(fabShape)
                    .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                RememberMaterialRoundedSymbol(
                    name = summary.iconName,
                    size = 28.dp,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = alertIconAlpha
                            translationY = -iconTravelPx * shapeProgress
                        },
                )
                RememberMaterialRoundedSymbol(
                    name = "chevron_right",
                    size = 28.dp,
                    tint = contentColor,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .offset(y = 1.dp)
                            .graphicsLayer {
                                alpha = chevronAlpha
                                rotationZ = 90f
                                translationY = iconTravelPx * (1f - shapeProgress)
                            },
                )
            }
        }
        AlertFabBadge(
            count = summary.count,
            expanded = expanded,
            notificationPriority = summary.isNotificationPriority,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-4).dp, y = 4.dp),
        )
    }
}

@Composable
private fun AlertFabBadge(
    count: Int,
    expanded: Boolean,
    notificationPriority: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor =
        when {
            expanded -> scheme.inverseSurface
            notificationPriority -> scheme.errorContainer
            else -> scheme.primary
        }
    val contentColor =
        when {
            expanded -> scheme.inverseOnSurface
            notificationPriority -> scheme.onErrorContainer
            else -> scheme.onPrimary
        }
    Surface(
        modifier = modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlertFloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summary: AlertChromeSummary,
    updateState: UpdateChromeState,
    blockedReminderCount: Int,
    onEnableReminderNotifications: () -> Unit,
    onUpdateClick: () -> Unit,
    onDismissUpdateAvailable: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    if (summary.count <= 0) return

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            AlertFloatingFab(
                summary = summary,
                expanded = expanded,
                onClick = { onExpandedChange(!expanded) },
            )
        },
        horizontalAlignment = horizontalAlignment,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            val density = LocalDensity.current
            val progress by
                transition.animateFloat(
                    transitionSpec = {
                        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
                    },
                    label = "alert_bars_unfurl",
                ) { state ->
                    if (state == EnterExitState.Visible) 1f else 0f
                }
            val exiting =
                if (transition.currentState == EnterExitState.Visible &&
                    transition.targetState == EnterExitState.PostExit
                ) {
                    true
                } else {
                    false
                }
            val barAlpha = if (exiting) progress else 1f
            Column(
                modifier =
                    Modifier
                        .widthIn(min = 332.dp, max = 392.dp)
                        .padding(start = 6.dp, end = 6.dp, bottom = 12.dp)
                        .graphicsLayer {
                            translationY = with(density) { 18.dp.toPx() } * (1f - progress)
                        },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (blockedReminderCount > 0) {
                    ReminderNotificationsBlockedBar(
                        reminderCount = blockedReminderCount,
                        contentAlpha = barAlpha,
                        shadowAlpha = barAlpha,
                        onEnableClick = onEnableReminderNotifications,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (updateState != UpdateChromeState.Hidden) {
                    UpdateFloatingBar(
                        state = updateState,
                        onCheckClick = onUpdateClick,
                        onDismissAvailable = onDismissUpdateAvailable,
                        onInstallClick = onInstallUpdate,
                        contentAlpha = barAlpha,
                        shadowAlpha = barAlpha,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderNotificationsBlockedBar(
    reminderCount: Int,
    contentAlpha: Float,
    shadowAlpha: Float,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AlertBarSurface(
        contentAlpha = contentAlpha,
        shadowAlpha = shadowAlpha,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = scheme.error,
                contentColor = scheme.onError,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RememberMaterialRoundedSymbol(
                        name = "notifications_off",
                        size = 28.dp,
                        weight = FontWeight.Medium,
                        tint = scheme.onError,
                    )
                }
            }
            AlertBarText(
                title = stringResource(R.string.main_reminder_notifications_disabled),
                body =
                    pluralStringResource(
                        R.plurals.main_reminders_due_this_week,
                        reminderCount,
                        reminderCount,
                    ),
                modifier = Modifier.weight(1f),
            )
            RememberButton(
                onClick = onEnableClick,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.common_enable),
                    maxLines = 1,
                )
            }
        }
    }
}
