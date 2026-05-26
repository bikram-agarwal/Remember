package dev.bikram.remember.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bikram.remember.ui.common.HERO_MASK_ASPECT_RATIO
import dev.bikram.remember.ui.common.HeroFramedImage
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.components.EditorShelfNotice
import dev.bikram.remember.ui.components.EditorShelfNoticeState
import dev.bikram.remember.ui.components.NoteShelfState
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.modifiers.rememberExpressiveOverscrollEffect
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec

internal object EditorContentBodyDefaults {
    val HorizontalPadding = 24.dp
    val HeroTopSpacing = 16.dp
    val ShelfNoticeTopSpacing = 16.dp
    val BodyTopSpacing = 16.dp
    val BottomSpacing = 40.dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorContentBodyColumn(
    modifier: Modifier,
    horizontalPadding: Dp,
    padding: PaddingValues,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    shelfState: NoteShelfState,
    bottomPadding: Dp,
    heroContent: (@Composable ColumnScope.() -> Unit)?,
    bodyContent: @Composable ColumnScope.() -> Unit,
    optionsContent: @Composable ColumnScope.() -> Unit,
) {
    val overscrollEffect = rememberExpressiveOverscrollEffect()
    val scrollModifier =
        Modifier.verticalScroll(
            state = scrollState,
            enabled = scrollEnabled,
            overscrollEffect = overscrollEffect,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .then(modifier)
                .clipToBounds()
                .overscroll(overscrollEffect)
                .then(scrollModifier)
                .padding(horizontal = horizontalPadding),
    ) {
        Spacer(Modifier.height(padding.calculateTopPadding()))
        heroContent?.invoke(this)
        EditorShelfNoticeBlock(shelfState = shelfState)
        Spacer(Modifier.height(EditorContentBodyDefaults.BodyTopSpacing))
        bodyContent()
        optionsContent()
        Spacer(Modifier.height(EditorContentBodyDefaults.BottomSpacing + bottomPadding))
    }
}

internal fun LazyListScope.editorContentHeaderItems(
    padding: PaddingValues,
    shelfState: NoteShelfState,
    heroContent: (@Composable () -> Unit)?,
    bodyTopSpacing: Dp = EditorContentBodyDefaults.BodyTopSpacing,
) {
    item(key = "top_padding") {
        Spacer(Modifier.height(padding.calculateTopPadding()))
    }
    if (heroContent != null) {
        item(key = "picture_hero") {
            Spacer(Modifier.height(EditorContentBodyDefaults.HeroTopSpacing))
            heroContent()
        }
    }
    if (shelfState != NoteShelfState.ACTIVE) {
        item(key = "editor_shelf_notice") {
            EditorShelfNoticeBlock(shelfState = shelfState)
        }
    }
    item(key = "body_top_spacing") {
        Spacer(Modifier.height(bodyTopSpacing))
    }
}

internal fun LazyListScope.editorContentOptionsItem(
    padding: PaddingValues,
    bottomExtra: Dp = 0.dp,
    optionsContent: @Composable () -> Unit,
) {
    item(key = "options_panel") {
        optionsContent()
        Spacer(
            Modifier.height(
                EditorContentBodyDefaults.BottomSpacing +
                    padding.calculateBottomPadding() +
                    bottomExtra,
            ),
        )
    }
}

@Composable
internal fun EditorShelfNoticeBlock(shelfState: NoteShelfState) {
    when (shelfState) {
        NoteShelfState.ARCHIVED -> {
            Spacer(Modifier.height(EditorContentBodyDefaults.ShelfNoticeTopSpacing))
            EditorShelfNotice(
                state = EditorShelfNoticeState.ARCHIVED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NoteShelfState.TRASHED -> {
            Spacer(Modifier.height(EditorContentBodyDefaults.ShelfNoticeTopSpacing))
            EditorShelfNotice(
                state = EditorShelfNoticeState.TRASHED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NoteShelfState.ACTIVE -> {}
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorContentPictureHero(
    uri: String,
    pictureRevision: Long,
    pictureHeroFraming: String?,
    viewerOpen: Boolean,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val framing = remember(pictureHeroFraming) { HeroFraming.fromJsonString(pictureHeroFraming) }
    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current

    Box(modifier = modifier) {
        val heroFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
        val heroFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
        AnimatedVisibility(
            visible = !viewerOpen,
            enter = fadeIn(animationSpec = heroFadeInSpec),
            exit = fadeOut(animationSpec = heroFadeOutSpec),
        ) {
            val sharedModifier =
                if (sharedScope != null) {
                    with(sharedScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "hero-image-$uri"),
                            animatedVisibilityScope = this@AnimatedVisibility,
                        )
                    }
                } else {
                    Modifier
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(sharedModifier)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .tapSoundClickable(onClick = onOpenFull),
            ) {
                HeroFramedImage(
                    imageUri = uri,
                    framing = framing,
                    cacheRevision = pictureRevision,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
