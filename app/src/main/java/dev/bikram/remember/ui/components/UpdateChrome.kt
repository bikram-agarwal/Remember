package dev.bikram.remember.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.ResponsiveActionLayout
import dev.bikram.remember.ui.common.responsiveActionLayout

sealed interface UpdateChromeState {
    data object Hidden : UpdateChromeState

    data object Available : UpdateChromeState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val indeterminateProgress: Boolean,
    ) : UpdateChromeState

    data object ReadyToInstall : UpdateChromeState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateFloatingBar(
    state: UpdateChromeState,
    onCheckClick: () -> Unit,
    @Suppress("UnusedParameter") onDismissAvailable: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
    shadowAlpha: Float = 1f,
    iconContainerSize: Dp = 44.dp,
    contentScale: Float = 1f,
) {
    if (state == UpdateChromeState.Hidden) return

    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val (title, body) =
        when (state) {
            UpdateChromeState.Hidden -> return
            UpdateChromeState.Available ->
                Pair(
                    stringResource(R.string.update_bar_available_title),
                    null,
                )
            is UpdateChromeState.Downloading -> {
                val progressLabel =
                    if (state.indeterminateProgress || state.totalBytesToDownload <= 0L) {
                        stringResource(R.string.play_update_bar_downloading)
                    } else {
                        val downloaded = Formatter.formatFileSize(context, state.bytesDownloaded)
                        val total = Formatter.formatFileSize(context, state.totalBytesToDownload)
                        stringResource(R.string.play_update_bar_downloading_bytes, downloaded, total)
                    }
                Pair(
                    stringResource(R.string.play_update_bar_downloading_title),
                    progressLabel,
                )
            }
            UpdateChromeState.ReadyToInstall ->
                Pair(
                    stringResource(R.string.play_update_bar_install_title),
                    stringResource(R.string.play_update_bar_install_subtitle),
                )
        }

    AlertBarSurface(
        contentAlpha = contentAlpha,
        shadowAlpha = shadowAlpha,
        modifier = modifier,
    ) {
        val rowMinHeight = 64.dp * contentScale
        val rowStartPadding = 14.dp * contentScale
        val rowEndPadding = 10.dp * contentScale
        val rowVerticalPadding = 8.dp * contentScale
        val rowSpacing = 12.dp * contentScale
        val symbolSize = 28.dp * contentScale
        val buttonHorizontalPadding = 18.dp * contentScale
        val buttonVerticalPadding = 8.dp * contentScale
        val progressHeight = 8.dp * contentScale
        BoxWithConstraints {
            val stackedAction =
                responsiveActionLayout(
                    availableWidth = maxWidth,
                    effectiveFontScale = LocalDensity.current.fontScale,
                    itemCount = 2,
                ) == ResponsiveActionLayout.STACKED
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = rowMinHeight)
                            .padding(
                                start = rowStartPadding,
                                end = rowEndPadding,
                                top = rowVerticalPadding,
                                bottom = rowVerticalPadding,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                ) {
                    Surface(
                        modifier = Modifier.size(iconContainerSize),
                        shape = CircleShape,
                        color = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
                                size = symbolSize,
                                weight = FontWeight.Medium,
                                tint = scheme.onPrimaryContainer,
                            )
                        }
                    }
                    AlertBarText(
                        title = title,
                        body = body,
                        modifier = Modifier.weight(1f),
                        contentScale = contentScale,
                    )

                    if (!stackedAction) {
                        when (state) {
                            UpdateChromeState.Available -> {
                                RememberButton(
                                    onClick = onCheckClick,
                                    contentPadding =
                                        PaddingValues(
                                            horizontal = buttonHorizontalPadding,
                                            vertical = buttonVerticalPadding,
                                        ),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = scheme.primary,
                                            contentColor = scheme.onPrimary,
                                        ),
                                ) {
                                    RememberActionLabel(stringResource(R.string.update_bar_available_action))
                                }
                            }
                            is UpdateChromeState.Downloading -> Unit
                            UpdateChromeState.ReadyToInstall ->
                                RememberButton(
                                    onClick = onInstallClick,
                                    contentPadding =
                                        PaddingValues(
                                            horizontal = buttonHorizontalPadding,
                                            vertical = buttonVerticalPadding,
                                        ),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = scheme.primary,
                                            contentColor = scheme.onPrimary,
                                        ),
                                ) {
                                    RememberActionLabel(stringResource(R.string.play_update_bar_install_action))
                                }
                            UpdateChromeState.Hidden -> Unit
                        }
                    }
                }

                if (stackedAction) {
                    when (state) {
                        UpdateChromeState.Available -> {
                            RememberButton(
                                onClick = onCheckClick,
                                modifier =
                                    Modifier
                                        .align(Alignment.End)
                                        .padding(
                                            start = rowStartPadding + iconContainerSize + rowSpacing,
                                            end = rowEndPadding,
                                            bottom = rowVerticalPadding,
                                        ),
                                contentPadding =
                                    PaddingValues(
                                        horizontal = buttonHorizontalPadding,
                                        vertical = buttonVerticalPadding,
                                    ),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = scheme.primary,
                                        contentColor = scheme.onPrimary,
                                    ),
                            ) {
                                RememberActionLabel(stringResource(R.string.update_bar_available_action))
                            }
                        }
                        is UpdateChromeState.Downloading -> Unit
                        UpdateChromeState.ReadyToInstall -> {
                            RememberButton(
                                onClick = onInstallClick,
                                modifier =
                                    Modifier
                                        .align(Alignment.End)
                                        .padding(
                                            start = rowStartPadding + iconContainerSize + rowSpacing,
                                            end = rowEndPadding,
                                            bottom = rowVerticalPadding,
                                        ),
                                contentPadding =
                                    PaddingValues(
                                        horizontal = buttonHorizontalPadding,
                                        vertical = buttonVerticalPadding,
                                    ),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = scheme.primary,
                                        contentColor = scheme.onPrimary,
                                    ),
                            ) {
                                RememberActionLabel(stringResource(R.string.play_update_bar_install_action))
                            }
                        }
                        UpdateChromeState.Hidden -> Unit
                    }
                }

                if (state is UpdateChromeState.Downloading) {
                    if (state.indeterminateProgress || state.totalBytesToDownload <= 0L) {
                        LinearWavyProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(progressHeight),
                            color = scheme.primary,
                            trackColor = scheme.primaryContainer.copy(alpha = 0.28f),
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
                                    .height(progressHeight),
                            color = scheme.primary,
                            trackColor = scheme.primaryContainer.copy(alpha = 0.28f),
                        )
                    }
                }
            }
        }
    }
}
