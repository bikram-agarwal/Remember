package dev.bikram.remember.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.R
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeSelectionActionBar(
    visible: Boolean,
    onClearSelection: () -> Unit,
    onTagSelected: () -> Unit,
    onMarkDoneSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onTrashSelected: () -> Unit,
    bottomPadding: Dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                ButtonGroup(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val exitLabel = stringResource(R.string.home_select_exit_cd)
                    val exitInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onClearSelection,
                        modifier = Modifier.animateWidth(exitInteractionSource),
                        interactionSource = exitInteractionSource,
                        tooltipLabel = exitLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "close",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = exitLabel },
                        )
                    }
                    val tagLabel = stringResource(R.string.home_bulk_tag)
                    val contentDescriptionTag = stringResource(R.string.home_bulk_tag_cd)
                    val tagInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onTagSelected,
                        modifier = Modifier.animateWidth(tagInteractionSource),
                        interactionSource = tagInteractionSource,
                        tooltipLabel = tagLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "label",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = contentDescriptionTag },
                        )
                    }
                    val markDoneLabel = stringResource(R.string.edit_bottom_bar_mark_done)
                    val contentDescriptionMarkDone = stringResource(R.string.home_bulk_mark_done_cd)
                    val markDoneInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onMarkDoneSelected,
                        modifier = Modifier.animateWidth(markDoneInteractionSource),
                        interactionSource = markDoneInteractionSource,
                        tooltipLabel = markDoneLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "check_circle",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = contentDescriptionMarkDone },
                        )
                    }
                    val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                    val contentDescriptionArchive = stringResource(R.string.home_bulk_archive_cd)
                    val archiveInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onArchiveSelected,
                        modifier = Modifier.animateWidth(archiveInteractionSource),
                        interactionSource = archiveInteractionSource,
                        tooltipLabel = archiveLabel,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "archive",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = contentDescriptionArchive },
                        )
                    }
                    val trashLabel = stringResource(R.string.home_bulk_trash)
                    val contentDescriptionTrash = stringResource(R.string.home_bulk_trash_cd)
                    val trashInteractionSource = remember { MutableInteractionSource() }
                    RememberFilledTonalIconButton(
                        onClick = onTrashSelected,
                        modifier = Modifier.animateWidth(trashInteractionSource),
                        interactionSource = trashInteractionSource,
                        tooltipLabel = trashLabel,
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "delete",
                            weight = FontWeight.Medium,
                            modifier = Modifier.semantics { contentDescription = contentDescriptionTrash },
                        )
                    }
                }
            }
        }
    }
}
