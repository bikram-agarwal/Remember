package dev.bikram.remember.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.RememberButton
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import dev.bikram.remember.ui.theme.reducedMotionEnterTransition
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingTitleScreen(
    onLetsBegin: () -> Unit,
) {
    var iconVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var bylineVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        iconVisible = true
        delay(140)
        titleVisible = true
        delay(120)
        bylineVisible = true
        delay(100)
        buttonVisible = true
    }

    val scheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    val enterSpatialSpec = reducedMotionAwareSpec(motionScheme.defaultSpatialSpec<IntOffset>())
    val enterFadeSpec = reducedMotionAwareSpec(motionScheme.defaultEffectsSpec<Float>())
    val iconEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight ->
                    fullHeight / 3
                }
        }
    val blockEnter =
        remember(enterSpatialSpec, enterFadeSpec) {
            fadeIn(animationSpec = enterFadeSpec) +
                slideInVertically(animationSpec = enterSpatialSpec) { fullHeight ->
                    fullHeight / 2
                }
        }

    val useTwoColumns = isSmallLandscape()
    Box(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingHeader(
                        visible = iconVisible,
                        titleVisible = titleVisible,
                        enter = reducedMotionEnterTransition(iconEnter),
                        blockEnter = reducedMotionEnterTransition(blockEnter),
                        useTwoColumns = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    OnboardingByline(
                        visible = bylineVisible,
                        enter = reducedMotionEnterTransition(blockEnter),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingLetsBeginButton(
                        visible = buttonVisible,
                        enter = reducedMotionEnterTransition(blockEnter),
                        onClick = onLetsBegin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            OnboardingHeader(
                visible = iconVisible,
                titleVisible = titleVisible,
                enter = reducedMotionEnterTransition(iconEnter),
                blockEnter = reducedMotionEnterTransition(blockEnter),
                useTwoColumns = false,
                modifier = Modifier.align(Alignment.Center),
            )

            OnboardingByline(
                visible = bylineVisible,
                enter = reducedMotionEnterTransition(blockEnter),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp),
            )

            OnboardingLetsBeginButton(
                visible = buttonVisible,
                enter = reducedMotionEnterTransition(blockEnter),
                onClick = onLetsBegin,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
            )
        }
    }
}

@Composable
private fun OnboardingHeader(
    visible: Boolean,
    titleVisible: Boolean,
    enter: EnterTransition,
    blockEnter: EnterTransition,
    useTwoColumns: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (useTwoColumns) 16.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIconImage(
                modifier =
                    Modifier
                        .size(if (useTwoColumns) 96.dp else 120.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
            )
            Spacer(Modifier.height(if (useTwoColumns) 16.dp else 24.dp))
            AnimatedVisibility(
                visible = titleVisible,
                enter = blockEnter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(if (useTwoColumns) 4.dp else 8.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingByline(
    visible: Boolean,
    enter: EnterTransition,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraExtraLarge,
            color = scheme.surfaceContainerHigh,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.me_600),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_byline),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingLetsBeginButton(
    visible: Boolean,
    enter: EnterTransition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
    ) {
        RememberButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraExtraLarge,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_lets_begin),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            RememberMaterialRoundedSymbol(
                name = "arrow_forward",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
