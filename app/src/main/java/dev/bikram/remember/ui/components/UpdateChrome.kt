package dev.bikram.remember.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol

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
        Column(modifier = Modifier.fillMaxWidth()) {
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
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
                            size = 28.dp,
                            weight = FontWeight.Medium,
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
                AlertBarText(
                    title = title,
                    body = body,
                    modifier = Modifier.weight(1f),
                )

                when (state) {
                    UpdateChromeState.Available -> {
                        RememberButton(
                            onClick = onCheckClick,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                        ) {
                            Text(stringResource(R.string.update_bar_available_action))
                        }
                    }
                    is UpdateChromeState.Downloading -> Unit
                    UpdateChromeState.ReadyToInstall ->
                        RememberButton(
                            onClick = onInstallClick,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.play_update_bar_install_action),
                                maxLines = 1,
                            )
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
                                .height(8.dp),
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
                                .height(8.dp),
                        color = scheme.primary,
                        trackColor = scheme.primaryContainer.copy(alpha = 0.28f),
                    )
                }
            }
        }
    }
}
