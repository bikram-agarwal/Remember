package dev.bikram.remember.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
    ) {
        AnimatedVisibility(
            visible = iconVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = reducedMotionEnterTransition(iconEnter),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconImage(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                )
                Spacer(Modifier.height(24.dp))
                AnimatedVisibility(
                    visible = titleVisible,
                    enter = reducedMotionEnterTransition(blockEnter),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = bylineVisible,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            enter = reducedMotionEnterTransition(blockEnter),
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

        AnimatedVisibility(
            visible = buttonVisible,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 40.dp),
            enter = reducedMotionEnterTransition(blockEnter),
        ) {
            RememberButton(
                onClick = onLetsBegin,
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
}
