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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.bikram.remember.ui.components.RememberDropdownMenuItem
import dev.bikram.remember.ui.components.RememberFilledTonalIconButton
import dev.bikram.remember.ui.components.rememberResponsiveActionButtonSize
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

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
    val spatialSpec =
        reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter =
                expandVertically(animationSpec = spatialSpec, expandFrom = Alignment.Bottom) +
                    fadeIn(animationSpec = fadeInSpec),
            exit =
                shrinkVertically(animationSpec = spatialSpec, shrinkTowards = Alignment.Bottom) +
                    fadeOut(animationSpec = fadeOutSpec),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraExtraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                val exitLabel = stringResource(R.string.home_select_exit_cd)
                val exitInteractionSource = remember { MutableInteractionSource() }
                val exitColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                val tagLabel = stringResource(R.string.home_bulk_tag)
                val contentDescriptionTag = stringResource(R.string.home_bulk_tag_cd)
                val tagInteractionSource = remember { MutableInteractionSource() }
                val markDoneLabel = stringResource(R.string.edit_bottom_bar_mark_done)
                val contentDescriptionMarkDone = stringResource(R.string.home_bulk_mark_done_cd)
                val markDoneInteractionSource = remember { MutableInteractionSource() }
                val archiveLabel = stringResource(R.string.edit_bottom_bar_archive)
                val contentDescriptionArchive = stringResource(R.string.home_bulk_archive_cd)
                val archiveInteractionSource = remember { MutableInteractionSource() }
                val trashLabel = stringResource(R.string.home_bulk_trash)
                val contentDescriptionTrash = stringResource(R.string.home_bulk_trash_cd)
                val trashInteractionSource = remember { MutableInteractionSource() }
                val trashColors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                val actionButtonSize = rememberResponsiveActionButtonSize()
                ButtonGroup(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onClearSelection,
                                modifier = Modifier.size(actionButtonSize).animateWidth(exitInteractionSource),
                                interactionSource = exitInteractionSource,
                                tooltipLabel = exitLabel,
                                colors = exitColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "close",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = exitLabel },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(exitLabel) },
                                onClick = {
                                    onClearSelection()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onTagSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(tagInteractionSource),
                                interactionSource = tagInteractionSource,
                                tooltipLabel = tagLabel,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "label",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = contentDescriptionTag },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(tagLabel) },
                                onClick = {
                                    onTagSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onMarkDoneSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(markDoneInteractionSource),
                                interactionSource = markDoneInteractionSource,
                                tooltipLabel = markDoneLabel,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "check_circle",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = contentDescriptionMarkDone },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(markDoneLabel) },
                                onClick = {
                                    onMarkDoneSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onArchiveSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(archiveInteractionSource),
                                interactionSource = archiveInteractionSource,
                                tooltipLabel = archiveLabel,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "archive",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = contentDescriptionArchive },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(archiveLabel) },
                                onClick = {
                                    onArchiveSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            RememberFilledTonalIconButton(
                                onClick = onTrashSelected,
                                modifier = Modifier.size(actionButtonSize).animateWidth(trashInteractionSource),
                                interactionSource = trashInteractionSource,
                                tooltipLabel = trashLabel,
                                colors = trashColors,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "delete",
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = contentDescriptionTrash },
                                )
                            }
                        },
                        menuContent = { menuState ->
                            RememberDropdownMenuItem(
                                text = { Text(trashLabel) },
                                onClick = {
                                    onTrashSelected()
                                    menuState.dismiss()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
