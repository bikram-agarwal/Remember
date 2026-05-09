package dev.bikram.remember.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.bikram.remember.ui.components.AboutAuthorPhoto
import dev.bikram.remember.ui.components.AppIconImage
import dev.bikram.remember.ui.components.RememberIconButton
import dev.bikram.remember.ui.components.settings.GroupPosition
import dev.bikram.remember.ui.components.settings.GroupedListColumn
import dev.bikram.remember.ui.components.settings.GroupedListItem
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable

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
) {
    val context = LocalContext.current
    val diagnosticsChooserTitle = stringResource(R.string.settings_share_diagnostics_chooser)
    val shareDiagnostics = rememberDiagnosticsShareAction(context, diagnosticsChooserTitle)
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SettingsStaticSectionHeader(
            materialSymbolName = "info",
            title = stringResource(R.string.settings_section_about),
            trailingContent = {
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
            },
        )
        Spacer(Modifier.height(8.dp))
        AboutSettingsBlock(
            onOpenIntro = onOpenIntro,
            onLaunchPlayReview = onLaunchPlayReview,
        )
    }
}

@Composable
private fun rememberDiagnosticsShareAction(
    context: Context,
    diagnosticsChooserTitle: String,
): () -> Unit =
    remember(context, diagnosticsChooserTitle) {
        {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutSettingsBlock(
    onOpenIntro: () -> Unit,
    onLaunchPlayReview: (onFlowFinished: () -> Unit) -> Unit,
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
    val aboutPillShape = MaterialTheme.shapes.extraExtraLarge
    var playStoreAboutUsesListingOnly by remember { mutableStateOf(false) }

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.ONLY) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
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
                            onClick = {},
                            onLongClick = {
                                Toast
                                    .makeText(context, buildVariantToastText, Toast.LENGTH_SHORT)
                                    .show()
                            },
                        ),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconImage(
                        modifier =
                            Modifier
                                .size(84.dp)
                                .clip(iconShape)
                                .tapSoundClickable(onClick = onOpenIntro),
                    )
                    Spacer(Modifier.width(20.dp))
                    AboutAuthorPhoto(
                        modifier =
                            Modifier
                                .size(84.dp)
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
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.settings_byline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
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
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
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
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary,
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
                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "store",
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_rate_on_play_store),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        if (githubRepoForSourceLink.isNotEmpty()) {
                            Spacer(Modifier.width(12.dp))
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
                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_github_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.settings_star_on_github),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
