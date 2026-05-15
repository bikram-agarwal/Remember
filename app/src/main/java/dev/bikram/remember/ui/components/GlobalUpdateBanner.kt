package dev.bikram.remember.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.update.PlayInAppUpdateBannerUiState
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun UpdateAvailableGlobalBanner(
    modifier: Modifier = Modifier,
    onOpenSettingsClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val expressiveShape = MaterialTheme.shapes.extraLargeIncreased
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = expressiveShape,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RememberMaterialRoundedSymbol(
                name = "new_releases",
                size = 40.dp,
                tint = scheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_banner_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_banner_available_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onOpenSettingsClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
            ) {
                Text(stringResource(R.string.update_banner_available_action))
            }
        }
    }
}

@Composable
fun SwipeDismissableUpdatePromoBanner(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 96.dp.toPx() } }
    val dragAccumulatedPx = remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .fillMaxWidth()
            .offset { IntOffset(dragAccumulatedPx.floatValue.roundToInt(), 0) }
            .pointerInput(dismissThresholdPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragDelta ->
                        dragAccumulatedPx.floatValue += dragDelta
                    },
                    onDragEnd = {
                        if (dragAccumulatedPx.floatValue.absoluteValue >= dismissThresholdPx) {
                            onDismiss()
                        }
                        dragAccumulatedPx.floatValue = 0f
                    },
                    onDragCancel = {
                        dragAccumulatedPx.floatValue = 0f
                    },
                )
            },
    ) {
        UpdateAvailableGlobalBanner(
            modifier = Modifier.fillMaxWidth(),
            onOpenSettingsClick = onOpenSettingsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayStoreGlobalUpdateBanner(
    state: PlayInAppUpdateBannerUiState,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val expressiveShape = MaterialTheme.shapes.extraLargeIncreased
    when (state) {
        is PlayInAppUpdateBannerUiState.Hidden -> Unit
        is PlayInAppUpdateBannerUiState.Downloading -> {
            OutlinedCard(
                modifier = modifier.fillMaxWidth(),
                shape = expressiveShape,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "system_update",
                        size = 40.dp,
                        tint = scheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.play_update_banner_downloading_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        val progressLabel =
                            if (state.indeterminateProgress) {
                                stringResource(R.string.play_update_banner_downloading)
                            } else {
                                val downloaded = Formatter.formatFileSize(context, state.bytesDownloaded)
                                val total = Formatter.formatFileSize(context, state.totalBytesToDownload)
                                stringResource(R.string.play_update_banner_downloading_bytes, downloaded, total)
                            }
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.indeterminateProgress || state.totalBytesToDownload <= 0L) {
                            LinearWavyProgressIndicator(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(expressiveShape),
                                color = scheme.primary,
                                trackColor = scheme.surfaceContainerHighest,
                            )
                        } else {
                            val fraction =
                                (state.bytesDownloaded.toFloat() / state.totalBytesToDownload.toFloat())
                                    .coerceIn(0f, 1f)
                            LinearWavyProgressIndicator(
                                progress = { fraction },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(expressiveShape),
                                color = scheme.primary,
                                trackColor = scheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            }
        }
        PlayInAppUpdateBannerUiState.ReadyToInstall -> {
            OutlinedCard(
                modifier = modifier.fillMaxWidth(),
                shape = expressiveShape,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "new_releases",
                        size = 40.dp,
                        tint = scheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.play_update_banner_install_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.play_update_banner_install_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onInstallClick,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.play_update_banner_install_action),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
