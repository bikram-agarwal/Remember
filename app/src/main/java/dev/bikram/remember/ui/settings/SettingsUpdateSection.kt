package dev.bikram.remember.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.data.UpdateCheckSchedule
import dev.bikram.remember.ui.common.MarkdownText
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberOutlinedButton
import dev.bikram.remember.ui.components.RememberSwitch
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.theme.compactControlShape
import dev.bikram.remember.ui.theme.pillShape
import dev.bikram.remember.update.RememberUpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateCheckBottomSheetContent(
    maxSheetHeight: Dp,
    isCheckingUpdate: Boolean,
    updateInfo: RememberUpdateInfo?,
    updateCheckFinishedWithoutResult: Boolean,
    downloadProgress: Float?,
    changelogState: ChangelogUiState,
    showGithubExtraUi: Boolean,
    usePlayInAppUpdates: Boolean,
    onDownloadClick: (RememberUpdateInfo) -> Unit,
    onSkipVersionClick: () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isChangelogReady = changelogState is ChangelogUiState.Ready
    val outerScrollState = rememberScrollState()
    val outerScrollable = outerScrollState.maxValue > 0 && outerScrollState.maxValue != Int.MAX_VALUE
    val scheme = MaterialTheme.colorScheme
    val outerModifier =
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .let { modifier ->
                if (isLandscape && isChangelogReady) {
                    modifier.height(maxSheetHeight).verticalScroll(outerScrollState, enabled = outerScrollable)
                } else {
                    modifier.heightIn(max = maxSheetHeight)
                }
            }
    Column(outerModifier) {
        if (isCheckingUpdate) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
        } else {
            when {
                downloadProgress != null -> {
                    UpdateSheetDownloadProgressBar(downloadProgress = downloadProgress)
                }
                updateInfo != null -> {
                    val availableUpdate = updateInfo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "system_update",
                            size = 40.dp,
                            tint = scheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (usePlayInAppUpdates && availableUpdate.isPlayStoreUpdateInProgress) {
                            Text(
                                text = stringResource(R.string.settings_update_play_in_progress_body),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = stringResource(R.string.settings_update_available, availableUpdate.versionName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onDownloadClick(availableUpdate) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = pillShape,
                        ) {
                            Text(
                                text =
                                    if (usePlayInAppUpdates && availableUpdate.isPlayStoreUpdateInProgress) {
                                        stringResource(R.string.settings_update_resume_play)
                                    } else {
                                        stringResource(R.string.settings_download_install, availableUpdate.versionName)
                                    },
                                maxLines = 1,
                            )
                        }
                        if (showGithubExtraUi && availableUpdate.remoteApkAssetUpdatedAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onSkipVersionClick) {
                                Text(stringResource(R.string.settings_update_skip_version))
                            }
                        }
                    }
                }
                updateCheckFinishedWithoutResult -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        UpToDatePhoneIcon()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_up_to_date),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (changelogState != ChangelogUiState.Hidden) {
            Spacer(Modifier.height(12.dp))
        }
        val pagerCoroutineScope = rememberCoroutineScope()
        when (changelogState) {
            ChangelogUiState.Hidden -> {}
            ChangelogUiState.Loading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(8.dp),
                        shape = compactControlShape,
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
            is ChangelogUiState.Ready -> {
                val readyMarkdown = changelogState.text
                val changelogPages = remember(readyMarkdown) { splitChangelogIntoPages(readyMarkdown) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    if (changelogPages.size <= 1) {
                        val singleScroll = rememberScrollState()
                        val singleScrollable = singleScroll.maxValue > 0 && singleScroll.maxValue != Int.MAX_VALUE
                        val singleModifier =
                            Modifier
                                .fillMaxWidth()
                                .let { modifier ->
                                    if (isLandscape) {
                                        modifier.wrapContentHeight().padding(8.dp)
                                    } else {
                                        modifier.heightIn(max = maxSheetHeight * 0.72f).padding(8.dp)
                                    }
                                }
                        Surface(
                            modifier = singleModifier,
                            shape = compactControlShape,
                            color = scheme.surfaceContainerLow,
                            contentColor = scheme.onSurface,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .let { modifier ->
                                            if (isLandscape) {
                                                modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)
                                            } else {
                                                modifier.fillMaxSize().verticalScroll(singleScroll, enabled = singleScrollable).padding(16.dp)
                                            }
                                        },
                            ) {
                                MarkdownText(
                                    markdown = readyMarkdown,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        val changelogPagerState = rememberPagerState(pageCount = { changelogPages.size })
                        val pagerContainerModifier =
                            Modifier
                                .fillMaxWidth()
                                .let { modifier ->
                                    if (isLandscape) {
                                        modifier.wrapContentHeight().padding(horizontal = 8.dp, vertical = 2.dp)
                                    } else {
                                        modifier.height(maxSheetHeight * 0.72f).padding(horizontal = 8.dp, vertical = 2.dp)
                                    }
                                }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .padding(horizontal = 2.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val canGoBack = changelogPagerState.currentPage > 0
                                val canGoForward = changelogPagerState.currentPage < changelogPages.lastIndex
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                enabled = canGoBack,
                                                onClick = {
                                                    pagerCoroutineScope.launch {
                                                        changelogPagerState.animateScrollToPage(
                                                            changelogPagerState.currentPage - 1,
                                                        )
                                                    }
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "arrow_back",
                                        size = 20.dp,
                                        tint =
                                            if (canGoBack) {
                                                scheme.primary
                                            } else {
                                                scheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }
                                Text(
                                    text =
                                        stringResource(
                                            R.string.settings_changelog_page_indicator,
                                            changelogPagerState.currentPage + 1,
                                            changelogPages.size,
                                        ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 6.dp),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                enabled = canGoForward,
                                                onClick = {
                                                    pagerCoroutineScope.launch {
                                                        changelogPagerState.animateScrollToPage(
                                                            changelogPagerState.currentPage + 1,
                                                        )
                                                    }
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RememberMaterialRoundedSymbol(
                                        name = "arrow_forward",
                                        size = 20.dp,
                                        tint =
                                            if (canGoForward) {
                                                scheme.primary
                                            } else {
                                                scheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }
                            }
                            Surface(
                                modifier = pagerContainerModifier,
                                shape = compactControlShape,
                                color = scheme.surfaceContainerLow,
                                contentColor = scheme.onSurface,
                            ) {
                                HorizontalPager(
                                    state = changelogPagerState,
                                    modifier =
                                        Modifier
                                            .let { modifier ->
                                                if (isLandscape) {
                                                    modifier.fillMaxWidth().wrapContentHeight()
                                                } else {
                                                    modifier.fillMaxSize()
                                                }
                                            },
                                ) { pageIndex ->
                                    val innerScroll = rememberScrollState()
                                    val innerScrollable = innerScroll.maxValue > 0 && innerScroll.maxValue != Int.MAX_VALUE
                                    Column(
                                        modifier =
                                            Modifier
                                                .let { modifier ->
                                                    if (isLandscape) {
                                                        modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)
                                                    } else {
                                                        modifier.fillMaxSize().verticalScroll(innerScroll, enabled = innerScrollable).padding(16.dp)
                                                    }
                                                },
                                    ) {
                                        MarkdownText(
                                            markdown = changelogPages[pageIndex],
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ChangelogUiState.Failed -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        shape = compactControlShape,
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface,
                    ) {
                        Text(
                            text = changelogState.message,
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UpToDatePhoneIcon() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = "smartphone",
            size = 40.dp,
            tint = primary,
            filled = false,
        )
        RememberMaterialRoundedSymbol(
            name = "check_circle",
            size = 22.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
            tint = primary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheetDownloadProgressBar(downloadProgress: Float) {
    val scheme = MaterialTheme.colorScheme
    val buttonHeight = 48.dp
    val shape = pillShape
    val label =
        when {
            downloadProgress == -1f -> stringResource(R.string.settings_installing)
            downloadProgress == -2f -> stringResource(R.string.settings_downloading)
            else ->
                stringResource(
                    R.string.settings_downloading_percent,
                    downloadProgress.toInt().coerceIn(0, 100),
                )
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.onSurface.copy(alpha = 0.12f)),
        )
        when {
            downloadProgress >= 0f && downloadProgress <= 100f -> {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((downloadProgress / 100f).coerceIn(0f, 1f))
                        .align(Alignment.CenterStart)
                        .background(scheme.primary.copy(alpha = 0.85f)),
                )
            }
            downloadProgress == -1f || downloadProgress == -2f -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.primary.copy(alpha = 0.22f)),
                )
            }
        }
        if (downloadProgress == -1f || downloadProgress == -2f) {
            LinearWavyProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(4.dp),
                color = scheme.primary.copy(alpha = 0.48f),
                trackColor = Color.Transparent,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateSheetChangelog(changelogState: ChangelogUiState) {
    if (changelogState == ChangelogUiState.Hidden) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
    ) {
        when (changelogState) {
            ChangelogUiState.Hidden -> Unit
            ChangelogUiState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is ChangelogUiState.Ready -> {
                val changelogPages = remember(changelogState.text) { splitChangelogIntoPages(changelogState.text) }
                if (changelogPages.size <= 1) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                    ) {
                        MarkdownText(
                            markdown = changelogPages.firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { changelogPages.size })
                    val pagerCoroutineScope = rememberCoroutineScope()
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val canGoBack = pagerState.currentPage > 0
                            val canGoForward = pagerState.currentPage < changelogPages.lastIndex
                            RememberOutlinedButton(
                                onClick = {
                                    pagerCoroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                enabled = canGoBack,
                            ) {
                                Text(stringResource(R.string.settings_changelog_previous))
                            }
                            Text(
                                text =
                                    stringResource(
                                        R.string.settings_changelog_page_indicator,
                                        pagerState.currentPage + 1,
                                        changelogPages.size,
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            RememberOutlinedButton(
                                onClick = {
                                    pagerCoroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                enabled = canGoForward,
                            ) {
                                Text(stringResource(R.string.settings_changelog_next))
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(420.dp),
                        ) { pageIndex ->
                            val innerScrollState = rememberScrollState()
                            val innerScrollable = innerScrollState.maxValue > 0 && innerScrollState.maxValue != Int.MAX_VALUE
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(innerScrollState, enabled = innerScrollable)
                                        .padding(4.dp),
                            ) {
                                MarkdownText(
                                    markdown = changelogPages[pageIndex],
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            is ChangelogUiState.Failed -> {
                Text(
                    text = changelogState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdateDownloadProgress(downloadProgress: Float) {
    when {
        downloadProgress == -1f -> {
            Text(
                text = stringResource(R.string.settings_installing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        downloadProgress < 0f -> {
            Text(
                text = stringResource(R.string.settings_downloading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        else -> {
            Text(
                text = stringResource(R.string.settings_downloading_percent, downloadProgress.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(
                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun UpdateCheckScheduleDropdown(
    selected: UpdateCheckSchedule,
    onSelect: (UpdateCheckSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_update_check_frequency),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RememberOutlinedButton(onClick = { expanded = true }) {
            Text(updateScheduleSummaryBeforeColon(selected))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                UpdateCheckSchedule.entries.forEach { option ->
                    RememberDropdownMenuItem(
                        text = { Text(updateScheduleLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun UpdateSettingsToggleItem(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
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

private fun summaryLabelBeforeColon(fullScheduleLabel: String): String {
    val colonIndex = fullScheduleLabel.indexOf(':')
    return if (colonIndex >= 0) {
        fullScheduleLabel.substring(0, colonIndex).trim()
    } else {
        fullScheduleLabel
    }
}

@Composable
private fun updateScheduleSummaryBeforeColon(schedule: UpdateCheckSchedule): String = summaryLabelBeforeColon(updateScheduleLabel(schedule))

@Composable
private fun updateScheduleLabel(schedule: UpdateCheckSchedule): String =
    when (schedule) {
        UpdateCheckSchedule.AT_APP_START -> stringResource(R.string.settings_update_schedule_app_start)
        UpdateCheckSchedule.DAILY_AT_21 -> stringResource(R.string.settings_update_schedule_daily_21)
        UpdateCheckSchedule.WEEKLY_MONDAY_AT_21 -> stringResource(R.string.settings_update_schedule_monday_21)
        UpdateCheckSchedule.NEVER -> stringResource(R.string.settings_update_schedule_never)
    }

internal sealed class ChangelogUiState {
    data object Hidden : ChangelogUiState()

    data object Loading : ChangelogUiState()

    data class Ready(
        val text: String,
    ) : ChangelogUiState()

    data class Failed(
        val message: String,
    ) : ChangelogUiState()
}
