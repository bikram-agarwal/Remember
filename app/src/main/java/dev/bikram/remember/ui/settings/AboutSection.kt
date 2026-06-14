package dev.bikram.remember.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.bikram.remember.BuildConfig
import dev.bikram.remember.R
import dev.bikram.remember.diagnostics.DiagnosticLog
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.common.isLandscape
import dev.bikram.remember.ui.common.isSmallLandscape
import dev.bikram.remember.ui.components.AboutAuthorPhoto
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.theme.pillShape
import kotlinx.coroutines.launch

/**
 * Static "About" section. Includes the section header (no expand/collapse since the
 * content is short and the header doubles as the entry to introductory help) and the
 * full about block: app name + version, app icon and author photo, store/repo pills,
 * and a "share diagnostic log" button. Pulled out of [SettingsRoute] in audit 3.1 so
 * the giants aren't quite so giant.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AboutSection(
    onOpenIntro: () -> Unit,
    onLaunchPlayReview: (onFlowFinished: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onDevModeActivated: () -> Unit = {},
    devModeEnabled: Boolean = false,
    // The empty-detail-pane filler shows just the card, without the section header
    // (and its diagnostics button).
    showHeader: Boolean = true,
    showHeaderTitle: Boolean = true,
) {
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()
    val context = LocalContext.current
    val diagnosticsChooserTitle = stringResource(R.string.settings_share_diagnostics_chooser)
    val shareDiagnostics = rememberDiagnosticsShareAction(context, diagnosticsChooserTitle)
    Column(modifier = modifier) {
        if (showHeader) {
            val diagnosticsButton: @Composable () -> Unit = {
                RememberIconButton(
                    onClick = shareDiagnostics,
                    tooltipLabel = stringResource(R.string.settings_share_diagnostics),
                    modifier =
                        Modifier.size(
                            rememberResponsiveActionButtonSize(
                                defaultSize = 40.dp,
                                compactSize = 38.dp,
                                ultraCompactSize = 36.dp,
                            ),
                        ),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "bug_report",
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        weight = FontWeight.Medium,
                    )
                }
            }
            if (showHeaderTitle) {
                SettingsStaticSectionHeader(
                    materialSymbolName = "info",
                    title = stringResource(R.string.settings_section_about),
                    trailingContent = if (!isLandscape) diagnosticsButton else null,
                )
            } else if (!isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    diagnosticsButton()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        AboutSettingsBlock(
            onOpenIntro = onOpenIntro,
            onLaunchPlayReview = onLaunchPlayReview,
            onDevModeActivated = onDevModeActivated,
            devModeEnabled = devModeEnabled,
            isLandscape = isLandscape,
            isSmallLandscape = isSmallLandscape,
            shareDiagnostics = shareDiagnostics,
        )
    }
}

@Composable
private fun rememberDiagnosticsShareAction(
    context: Context,
    diagnosticsChooserTitle: String,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(context, diagnosticsChooserTitle, scope) {
        {
            scope.launch {
                DiagnosticLog.record(context, "Diagnostic log shared from Settings")
                val diagnosticsFile = DiagnosticLog.createShareFile(context)
                val diagnosticsUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        diagnosticsFile,
                    )
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        setDataAndType(diagnosticsUri, "text/plain")
                        putExtra(Intent.EXTRA_STREAM, diagnosticsUri)
                        putExtra(Intent.EXTRA_TITLE, diagnosticsFile.name)
                        putExtra(Intent.EXTRA_SUBJECT, diagnosticsFile.name)
                        clipData = ClipData.newUri(context.contentResolver, diagnosticsFile.name, diagnosticsUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                runCatching {
                    context.startActivity(Intent.createChooser(shareIntent, diagnosticsChooserTitle))
                }.onFailure { throwable ->
                    DiagnosticLog.record(context, "Diagnostic log share sheet failed", throwable)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AboutSettingsBlock(
    onOpenIntro: () -> Unit,
    onLaunchPlayReview: (onFlowFinished: () -> Unit) -> Unit,
    onDevModeActivated: () -> Unit = {},
    devModeEnabled: Boolean = false,
    isLandscape: Boolean = false,
    isSmallLandscape: Boolean = false,
    shareDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val profileUrl = stringResource(R.string.about_author_github_profile_url)
    val buildFlavorLabel =
        when (BuildConfig.FLAVOR) {
            "github" -> stringResource(R.string.build_flavor_github)
            "playstore" -> stringResource(R.string.build_flavor_playstore)
            else -> BuildConfig.FLAVOR
        }
    val buildTypeLabel =
        when (BuildConfig.BUILD_TYPE) {
            "debug" -> stringResource(R.string.build_type_debug)
            "devRelease" -> stringResource(R.string.build_type_dev_release)
            "release" -> stringResource(R.string.build_type_release)
            else -> BuildConfig.BUILD_TYPE
        }
    val buildVariantToastText = stringResource(R.string.about_build_variant_format, buildFlavorLabel, buildTypeLabel)
    var devModeTapCount by remember { mutableIntStateOf(0) }
    var lastAlreadyUnlockedToastMs by remember { mutableLongStateOf(0L) }
    val copyAboutLink =
        remember(context) {
            { url: String ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(resources.getString(R.string.clipboard_link_label), url),
                )
                Toast.makeText(context, resources.getString(R.string.toast_about_link_copied), Toast.LENGTH_SHORT).show()
            }
        }
    val iconShape = MaterialTheme.shapes.extraLarge
    val authorShape = MaterialTheme.shapes.large
    val aboutPillShape = pillShape
    var playStoreAboutUsesListingOnly by remember { mutableStateOf(false) }

    val pillPadding = if (isSmallLandscape) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding
    val pillTextStyle = if (isSmallLandscape) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val pillIconSize = if (isSmallLandscape) 18.dp else 20.dp
    val pillIconSpacer = if (isSmallLandscape) 6.dp else 8.dp

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.ONLY) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isLandscape) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = if (isSmallLandscape) 4.dp else 8.dp, end = if (isSmallLandscape) 4.dp else 8.dp),
                    ) {
                        RememberIconButton(
                            onClick = shareDiagnostics,
                            tooltipLabel = stringResource(R.string.settings_share_diagnostics),
                            modifier = Modifier.size(40.dp),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "bug_report",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.primary,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isSmallLandscape) 16.dp else 20.dp)
                            .padding(
                                top = if (isSmallLandscape) 20.dp else 24.dp,
                                bottom = if (isSmallLandscape) 16.dp else 8.dp,
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.app_version_format,
                                stringResource(R.string.app_name),
                                BuildConfig.VERSION_NAME,
                            ),
                        modifier =
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (devModeEnabled) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastAlreadyUnlockedToastMs > 2_500L) {
                                            lastAlreadyUnlockedToastMs = now
                                            Toast.makeText(context, resources.getString(R.string.dev_options_already_unlocked), Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        devModeTapCount++
                                        if (devModeTapCount >= 7) {
                                            devModeTapCount = 0
                                            onDevModeActivated()
                                        } else if (devModeTapCount >= 3) {
                                            val tapsLeft = 7 - devModeTapCount
                                            Toast
                                                .makeText(
                                                    context,
                                                    resources.getQuantityString(
                                                        R.plurals.dev_options_taps_remaining,
                                                        tapsLeft,
                                                        tapsLeft,
                                                    ),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    devModeTapCount = 0
                                    Toast
                                        .makeText(context, buildVariantToastText, Toast.LENGTH_SHORT)
                                        .show()
                                },
                            ),
                        style = if (isSmallLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 8.dp else 10.dp))
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = if (isSmallLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 20.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallLandscape) 8.dp else 12.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconImage(
                            modifier =
                                Modifier
                                    .size(if (isSmallLandscape) 64.dp else 84.dp)
                                    .clip(iconShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            onOpenIntro()
                                        },
                                    ),
                        )
                        Spacer(Modifier.width(if (isSmallLandscape) 16.dp else 20.dp))
                        AboutAuthorPhoto(
                            modifier =
                                Modifier
                                    .size(if (isSmallLandscape) 64.dp else 84.dp)
                                    .clip(authorShape)
                                    .tapSoundCombinedClickable(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, profileUrl.toUri()))
                                            }
                                        },
                                        onLongClick = { copyAboutLink(profileUrl) },
                                    ),
                        )
                    }
                    Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 20.dp))
                    Text(
                        text = stringResource(R.string.settings_byline),
                        style = if (isSmallLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(if (isSmallLandscape) 14.dp else 24.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(if (isSmallLandscape) 8.dp else 12.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (BuildConfig.FLAVOR == "github") {
                            Surface(
                                shape = aboutPillShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .tapSoundCombinedClickable(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, playStoreListingUrl.toUri()),
                                                    )
                                                }
                                            },
                                            onLongClick = { copyAboutLink(playStoreListingUrl) },
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(pillPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    AboutPlayStoreIcon(tint = MaterialTheme.colorScheme.primary, size = pillIconSize)
                                    Spacer(Modifier.width(pillIconSpacer))
                                    Text(
                                        text = stringResource(R.string.settings_rate_on_play_store),
                                        style = pillTextStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            }
                            if (githubRepoForSourceLink.isNotEmpty()) {
                                val repoUrl = "https://github.com/$githubRepoForSourceLink"
                                Surface(
                                    shape = aboutPillShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier =
                                        Modifier
                                            .clip(aboutPillShape)
                                            .tapSoundCombinedClickable(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, repoUrl.toUri()))
                                                    }
                                                },
                                                onLongClick = { copyAboutLink(repoUrl) },
                                            ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(pillPadding),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_github_mark),
                                            contentDescription = null,
                                            modifier = Modifier.size(pillIconSize),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(pillIconSpacer))
                                        Text(
                                            text = stringResource(R.string.settings_star_on_github),
                                            style = pillTextStyle,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = aboutPillShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier =
                                    Modifier
                                        .clip(aboutPillShape)
                                        .tapSoundCombinedClickable(
                                            onClick = {
                                                if (playStoreAboutUsesListingOnly) {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, playStoreListingUrl.toUri()),
                                                        )
                                                    }
                                                } else {
                                                    onLaunchPlayReview {
                                                        playStoreAboutUsesListingOnly = true
                                                    }
                                                }
                                            },
                                            onLongClick = { copyAboutLink(playStoreListingUrl) },
                                        ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(pillPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    AboutPlayStoreIcon(tint = MaterialTheme.colorScheme.onPrimary, size = pillIconSize)
                                    Spacer(Modifier.width(pillIconSpacer))
                                    Text(
                                        text = stringResource(R.string.settings_rate_on_play_store),
                                        style = pillTextStyle,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                }
                            }
                            if (githubRepoForSourceLink.isNotEmpty()) {
                                val repoUrl = "https://github.com/$githubRepoForSourceLink"
                                Surface(
                                    shape = aboutPillShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier =
                                        Modifier
                                            .clip(aboutPillShape)
                                            .tapSoundCombinedClickable(
                                                onClick = {
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, repoUrl.toUri()))
                                                    }
                                                },
                                                onLongClick = { copyAboutLink(repoUrl) },
                                            ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(pillPadding),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_github_mark),
                                            contentDescription = null,
                                            modifier = Modifier.size(pillIconSize),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(pillIconSpacer))
                                        Text(
                                            text = stringResource(R.string.settings_star_on_github),
                                            style = pillTextStyle,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (isSmallLandscape) 12.dp else 24.dp))
                    AboutOtherAppsAndLinks(
                        context = context,
                        copyAboutLink = copyAboutLink,
                        isSmallLandscape = isSmallLandscape,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutPlayStoreIcon(
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Icon(
        painter = painterResource(R.drawable.ic_google_play_mark),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
}

@Composable
private fun AboutOtherAppsAndLinks(
    context: Context,
    copyAboutLink: (String) -> Unit,
    isSmallLandscape: Boolean,
) {
    val filePipeStoreUrl =
        stringResource(
            if (BuildConfig.FLAVOR == "github") {
                R.string.settings_about_filepipe_github_url
            } else {
                R.string.settings_about_filepipe_play_store_url
            },
        )
    val obtainXStoreUrl = stringResource(R.string.settings_about_obtainx_github_url)
    val websiteUrl = stringResource(R.string.settings_about_remember_website_url)
    val privacyUrl = stringResource(R.string.settings_about_remember_privacy_url)
    val termsUrl = stringResource(R.string.settings_about_remember_terms_url)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_other_apps),
            style = if (isSmallLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(if (isSmallLandscape) 6.dp else 8.dp))
        AboutAppStoreButton(
            iconResId = R.drawable.filepipe_logo,
            name = stringResource(R.string.settings_about_filepipe_name),
            tagline = stringResource(R.string.settings_about_filepipe_tagline),
            url = filePipeStoreUrl,
            accentColor = Color(0xFF5967D8),
            context = context,
            copyAboutLink = copyAboutLink,
            isSmallLandscape = isSmallLandscape,
        )
        if (BuildConfig.FLAVOR == "github") {
            Spacer(Modifier.height(if (isSmallLandscape) 6.dp else 8.dp))
            AboutAppStoreButton(
                iconResId = R.drawable.obtainx_logo,
                name = stringResource(R.string.settings_about_obtainx_name),
                tagline = stringResource(R.string.settings_about_obtainx_tagline),
                url = obtainXStoreUrl,
                accentColor = Color(0xFF7C55D9),
                context = context,
                copyAboutLink = copyAboutLink,
                isSmallLandscape = isSmallLandscape,
            )
        }
        Spacer(Modifier.height(if (isSmallLandscape) 10.dp else 14.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(if (isSmallLandscape) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutTextLink(
                label = stringResource(R.string.settings_about_website),
                url = websiteUrl,
                context = context,
                copyAboutLink = copyAboutLink,
                isSmallLandscape = isSmallLandscape,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_privacy_policy),
                url = privacyUrl,
                context = context,
                copyAboutLink = copyAboutLink,
                isSmallLandscape = isSmallLandscape,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_terms),
                url = termsUrl,
                context = context,
                copyAboutLink = copyAboutLink,
                isSmallLandscape = isSmallLandscape,
            )
        }
    }
}

@Composable
private fun AboutAppStoreButton(
    iconResId: Int,
    name: String,
    tagline: String,
    url: String,
    accentColor: Color,
    context: Context,
    copyAboutLink: (String) -> Unit,
    isSmallLandscape: Boolean,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = accentColor.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .tapSoundCombinedClickable(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    onLongClick = { copyAboutLink(url) },
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isSmallLandscape) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(if (isSmallLandscape) 36.dp else 40.dp)
                        .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(if (isSmallLandscape) 8.dp else 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = if (isSmallLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isSmallLandscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            RememberMaterialRoundedSymbol(
                name = "chevron_right",
                size = if (isSmallLandscape) 18.dp else 20.dp,
                tint = accentColor.copy(alpha = 0.86f),
                weight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AboutLinkSeparator() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
}

@Composable
private fun AboutTextLink(
    label: String,
    url: String,
    context: Context,
    copyAboutLink: (String) -> Unit,
    isSmallLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier =
            modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .tapSoundCombinedClickable(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    onLongClick = { copyAboutLink(url) },
                ),
        style = if (isSmallLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
