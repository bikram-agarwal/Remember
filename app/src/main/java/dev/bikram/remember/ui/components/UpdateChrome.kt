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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.theme.RoundedPolygonShape
import dev.bikram.remember.ui.theme.pillShape

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

@Composable
fun UpdateFloatingFab(
    state: UpdateChromeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == UpdateChromeState.Hidden) return

    val label = stringResource(R.string.update_fab_label)
    val shape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }
    RememberFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        tooltipLabel = label,
    ) {
        RememberMaterialRoundedSymbol(
            name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
            size = 28.dp,
            weight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateFloatingBar(
    state: UpdateChromeState,
    onCheckClick: () -> Unit,
    onDismissAvailable: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == UpdateChromeState.Hidden) return

    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val iconShape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }
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

    Surface(
        modifier = modifier,
        shape = pillShape,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = iconShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RememberMaterialRoundedSymbol(
                            name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
                            size = 24.dp,
                            weight = FontWeight.Medium,
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (body != null) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                when (state) {
                    UpdateChromeState.Available -> {
                        RememberButton(
                            onClick = onCheckClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                        ) {
                            Text(stringResource(R.string.update_bar_available_action))
                        }
                        val closeLabel = stringResource(R.string.main_fab_close)
                        RememberIconButton(
                            onClick = onDismissAvailable,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .semantics { contentDescription = closeLabel },
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "close",
                                size = 20.dp,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                    is UpdateChromeState.Downloading -> Unit
                    UpdateChromeState.ReadyToInstall ->
                        RememberButton(
                            onClick = onInstallClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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
