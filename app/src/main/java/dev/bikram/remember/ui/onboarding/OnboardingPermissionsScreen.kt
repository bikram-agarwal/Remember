package dev.bikram.remember.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.components.RememberTextButton
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingPermissionsScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager =
        remember {
            context.getSystemService(Context.POWER_SERVICE) as PowerManager
        }
    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var ignoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    DisposableEffect(lifecycleOwner, context, powerManager) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        val useTwoColumns = isSmallLandscape()
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Left column: graphics, title, subtitle, verified footer
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PermissionsHeroIllustration(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_permissions_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.onboarding_permissions_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "verified_user",
                                size = 14.dp,
                                tint = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.onboarding_permissions_change_anytime_footer),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Right column: the two permissions cards and the button at the bottom
                    Column(
                        modifier =
                            Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        Spacer(Modifier.height(12.dp))
                        PermissionStatusCard(
                            granted = notificationsGranted,
                            title = stringResource(R.string.onboarding_permissions_notifications_title),
                            body = stringResource(R.string.onboarding_permissions_notifications_body),
                            iconName = "notifications",
                            statusText = stringResource(R.string.onboarding_permissions_status_recommended),
                            actionText =
                                if (notificationsGranted) {
                                    stringResource(R.string.onboarding_permissions_enabled)
                                } else {
                                    stringResource(R.string.onboarding_permissions_allow_notifications)
                                },
                            actionEnabled = !notificationsGranted,
                            primaryAction = true,
                            compact = true,
                            onAction = {
                                when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED ->
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                        context.startActivity(notificationSettingsIntent(context))
                                    else ->
                                        notificationsGranted = true
                                }
                            },
                        )
                        PermissionStatusCard(
                            granted = ignoringBatteryOptimizations,
                            title = stringResource(R.string.onboarding_permissions_reliable_title),
                            body = stringResource(R.string.onboarding_permissions_reliable_body),
                            iconName = "timer",
                            statusText = stringResource(R.string.onboarding_permissions_status_optional),
                            actionText =
                                if (ignoringBatteryOptimizations) {
                                    stringResource(R.string.onboarding_permissions_enabled)
                                } else {
                                    stringResource(R.string.onboarding_permissions_improve_reliability)
                                },
                            actionEnabled = !ignoringBatteryOptimizations,
                            primaryAction = false,
                            compact = true,
                            onAction = {
                                runCatching {
                                    context.startActivity(batteryOptimizationIntent(context))
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            },
                        )

                        val bottomCtaShape = MaterialTheme.shapes.extraExtraLarge
                        val bottomCtaPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        if (notificationsGranted) {
                            RememberButton(
                                onClick = onContinue,
                                modifier = Modifier.fillMaxWidth(),
                                shape = bottomCtaShape,
                                contentPadding = bottomCtaPadding,
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_permissions_continue),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                                RememberMaterialRoundedSymbol(
                                    name = "arrow_forward",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        } else {
                            RememberTextButton(
                                onClick = onContinue,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.onboarding_permissions_skip_for_now),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 26.dp)
                            .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.statusBarsPadding())
                        PermissionsHeroIllustration(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.onboarding_permissions_title),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.onboarding_permissions_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                        Spacer(Modifier.height(22.dp))
                        PermissionStatusCard(
                            granted = notificationsGranted,
                            title = stringResource(R.string.onboarding_permissions_notifications_title),
                            body = stringResource(R.string.onboarding_permissions_notifications_body),
                            iconName = "notifications",
                            statusText = stringResource(R.string.onboarding_permissions_status_recommended),
                            actionText =
                                if (notificationsGranted) {
                                    stringResource(R.string.onboarding_permissions_enabled)
                                } else {
                                    stringResource(R.string.onboarding_permissions_allow_notifications)
                                },
                            actionEnabled = !notificationsGranted,
                            primaryAction = true,
                            onAction = {
                                when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED ->
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    !NotificationManagerCompat.from(context).areNotificationsEnabled() ->
                                        context.startActivity(notificationSettingsIntent(context))
                                    else ->
                                        notificationsGranted = true
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        PermissionStatusCard(
                            granted = ignoringBatteryOptimizations,
                            title = stringResource(R.string.onboarding_permissions_reliable_title),
                            body = stringResource(R.string.onboarding_permissions_reliable_body),
                            iconName = "timer",
                            statusText = stringResource(R.string.onboarding_permissions_status_optional),
                            actionText =
                                if (ignoringBatteryOptimizations) {
                                    stringResource(R.string.onboarding_permissions_enabled)
                                } else {
                                    stringResource(R.string.onboarding_permissions_improve_reliability)
                                },
                            actionEnabled = !ignoringBatteryOptimizations,
                            primaryAction = false,
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.98f)
                                    .align(Alignment.CenterHorizontally),
                            onAction = {
                                runCatching {
                                    context.startActivity(batteryOptimizationIntent(context))
                                }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            },
                        )
                        Spacer(Modifier.height(140.dp))
                    }
                }

                val bottomCtaShape = MaterialTheme.shapes.extraExtraLarge
                val bottomCtaPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "verified_user",
                            size = 18.dp,
                            tint = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_permissions_change_anytime_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    if (notificationsGranted) {
                        RememberButton(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomCtaShape,
                            contentPadding = bottomCtaPadding,
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_permissions_continue),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            RememberMaterialRoundedSymbol(
                                name = "arrow_forward",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    } else {
                        RememberTextButton(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = scheme.onSurfaceVariant,
                                ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_permissions_skip_for_now),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsHeroIllustration(
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier.drawBehind {
                drawCircle(
                    color = scheme.primary.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.33f,
                    center = Offset(size.width * 0.46f, size.height * 0.56f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                drawCircle(
                    color = scheme.primary.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width * 0.62f, size.height * 0.48f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        DecorativeStar(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 64.dp, y = 48.dp),
        )
        DecorativeStar(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-42).dp, y = 92.dp),
            size = 22.dp,
        )
        DecorativeDot(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 36.dp, y = 16.dp),
            size = 6.dp,
        )
        DecorativeDot(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-86).dp, y = (-52).dp),
            size = 4.dp,
        )

        Surface(
            modifier =
                Modifier
                    .size(92.dp)
                    .offset(y = (-62).dp),
            shape = CircleShape,
            color = scheme.primaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                RememberMaterialRoundedSymbol(
                    name = "calendar_month",
                    size = 46.dp,
                    tint = scheme.onPrimaryContainer,
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .size(58.dp)
                    .offset(x = (-72).dp, y = (-12).dp),
            shape = CircleShape,
            color = scheme.primary,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                RememberMaterialRoundedSymbol(
                    name = "notifications",
                    size = 31.dp,
                    tint = scheme.onPrimary,
                    filled = false,
                )
            }
        }

        ChecklistIllustrationCard(
            modifier =
                Modifier
                    .width(104.dp)
                    .height(128.dp)
                    .offset(x = 52.dp, y = (-12).dp)
                    .graphicsLayer { rotationZ = 10f },
        )
        NoteIllustrationCard(
            modifier =
                Modifier
                    .width(146.dp)
                    .height(78.dp)
                    .offset(x = (-32).dp, y = 62.dp)
                    .graphicsLayer { rotationZ = 4f },
        )
    }
}

@Composable
private fun ChecklistIllustrationCard(
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = scheme.primaryContainer,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check_circle",
                        size = 16.dp,
                        tint = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                    IllustrationLine(
                        modifier =
                            Modifier
                                .height(4.dp)
                                .weight(1f),
                        color = scheme.onPrimaryContainer.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteIllustrationCard(
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHighest.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(scheme.primary, CircleShape),
                )
                IllustrationLine(
                    modifier =
                        Modifier
                            .height(4.dp)
                            .fillMaxWidth(0.72f),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
            IllustrationLine(
                modifier =
                    Modifier
                        .height(4.dp)
                        .fillMaxWidth(0.54f),
                color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun IllustrationLine(
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier.background(color, MaterialTheme.shapes.extraExtraLarge),
    )
}

@Composable
private fun DecorativeDot(
    modifier: Modifier = Modifier,
    size: Dp = 4.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), CircleShape),
    )
}

@Composable
private fun DecorativeStar(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val sparkleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    Box(
        modifier =
            modifier
                .size(size)
                .drawBehind {
                    val centerX = this.size.width / 2f
                    val centerY = this.size.height / 2f
                    val radius = this.size.minDimension / 2f
                    val innerRadius = radius * 0.28f
                    val sparklePath =
                        Path().apply {
                            moveTo(centerX, centerY - radius)
                            lineTo(centerX + innerRadius, centerY - innerRadius)
                            lineTo(centerX + radius, centerY)
                            lineTo(centerX + innerRadius, centerY + innerRadius)
                            lineTo(centerX, centerY + radius)
                            lineTo(centerX - innerRadius, centerY + innerRadius)
                            lineTo(centerX - radius, centerY)
                            lineTo(centerX - innerRadius, centerY - innerRadius)
                            close()
                        }
                    drawPath(sparklePath, sparkleColor)
                },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionStatusCard(
    granted: Boolean,
    title: String,
    body: String,
    iconName: String,
    statusText: String,
    actionText: String,
    actionEnabled: Boolean,
    primaryAction: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val spatialSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec<Float>())
    val colorSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec<Color>())
    val actionSpatialSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec<IntOffset>())
    val actionFadeInSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec<Float>())
    val actionFadeOutSpec = reducedMotionAwareSpec(motionScheme.fastEffectsSpec<Float>())
    val cardPadding = if (compact) (if (primaryAction) 12.dp else 10.dp) else (if (primaryAction) 16.dp else 10.dp)
    val cardShape = if (compact) MaterialTheme.shapes.large else (if (primaryAction) MaterialTheme.shapes.largeIncreased else MaterialTheme.shapes.large)
    val iconSize = if (compact) (if (primaryAction) 46.dp else 40.dp) else (if (primaryAction) 54.dp else 44.dp)
    val bodyStartPadding = iconSize + 10.dp
    val topBodySpacing = if (compact) (if (primaryAction) 8.dp else 6.dp) else (if (primaryAction) 12.dp else 7.dp)
    val bottomActionSpacing = if (compact) (if (primaryAction) 10.dp else 8.dp) else (if (primaryAction) 14.dp else 9.dp)
    val actionVerticalPadding = if (compact) 10.dp else (if (primaryAction) 13.dp else 10.dp)
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spatialSpec,
        label = "permissionCardScale",
    )
    val iconPulseScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spatialSpec,
        label = "permissionIconPulse",
    )
    val borderColor by animateColorAsState(
        targetValue =
            if (granted) {
                scheme.outlineVariant.copy(alpha = 0.18f)
            } else {
                scheme.surfaceTint.copy(alpha = 0.24f)
            },
        animationSpec = colorSpec,
        label = "permissionCardBorder",
    )
    val containerColor =
        if (granted) {
            scheme.surfaceContainer.copy(alpha = 0.72f)
        } else {
            scheme.surfaceContainerHigh.copy(alpha = 0.92f)
        }
    val titleColor = if (granted) scheme.onSurface.copy(alpha = 0.70f) else scheme.onSurface
    val bodyColor = if (granted) scheme.onSurfaceVariant.copy(alpha = 0.70f) else scheme.onSurfaceVariant
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .scale(scale),
        shape = cardShape,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (granted) 0.dp else 1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PermissionIconBadge(
                    granted = granted,
                    iconName = iconName,
                    iconSize = iconSize,
                    pulseScale = iconPulseScale,
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                StatusPill(
                    text =
                        if (granted) {
                            stringResource(R.string.onboarding_permissions_enabled_status)
                        } else {
                            statusText
                        },
                    emphasized = primaryAction && !granted,
                    enabled = granted,
                )
            }
            Spacer(Modifier.height(topBodySpacing))
            Text(
                text = body,
                modifier = Modifier.padding(start = bodyStartPadding),
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor,
            )
            if (!granted) {
                Spacer(Modifier.height(bottomActionSpacing))
                AnimatedContent(
                    targetState = actionEnabled,
                    transitionSpec = {
                        (
                            slideInVertically(animationSpec = actionSpatialSpec) { it / 2 } +
                                fadeIn(animationSpec = actionFadeInSpec)
                        ) togetherWith
                            (
                                slideOutVertically(animationSpec = actionSpatialSpec) { -it / 2 } +
                                    fadeOut(animationSpec = actionFadeOutSpec)
                            )
                    },
                    label = "permissionCardAction",
                ) { currentActionEnabled ->
                    if (primaryAction) {
                        RememberButton(
                            onClick = onAction,
                            enabled = currentActionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = actionVerticalPadding),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primaryContainer,
                                    contentColor = scheme.onPrimaryContainer,
                                ),
                        ) {
                            Text(
                                text = actionText,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        RememberButton(
                            onClick = onAction,
                            enabled = currentActionEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = actionVerticalPadding),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.surfaceContainerHighest,
                                    contentColor = scheme.onSurfaceVariant,
                                ),
                        ) {
                            Text(
                                text = actionText,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionIconBadge(
    granted: Boolean,
    iconName: String,
    iconSize: Dp,
    pulseScale: Float,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (granted) scheme.primary else scheme.primaryContainer
    val iconColor = if (granted) scheme.onPrimary else scheme.onPrimaryContainer
    Surface(
        modifier =
            Modifier
                .size(iconSize)
                .scale(pulseScale),
        shape = CircleShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RememberMaterialRoundedSymbol(
                name = iconName,
                size = 25.dp,
                tint = iconColor,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    emphasized: Boolean,
    enabled: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val enabledStatusGreen =
        if (scheme.surface.luminance() < 0.5f) {
            Color(0xFF7FBC8A)
        } else {
            Color(0xFF1B5E20)
        }
    Surface(
        shape = MaterialTheme.shapes.extraExtraLarge,
        color =
            if (enabled) {
                scheme.surfaceContainerHighest.copy(alpha = 0.78f)
            } else if (emphasized) {
                scheme.primaryContainer
            } else {
                scheme.surfaceContainerHighest
            },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized || enabled) FontWeight.Bold else FontWeight.Medium,
            color =
                if (enabled) {
                    enabledStatusGreen
                } else if (emphasized) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onSurfaceVariant
                },
        )
    }
}

private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

private fun batteryOptimizationIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
    }
