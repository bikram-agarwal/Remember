package dev.bikram.remember.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.ui.common.HeroFramedImage
import dev.bikram.remember.ui.common.HeroFraming
import dev.bikram.remember.ui.common.MarkdownText
import dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol
import dev.bikram.remember.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import dev.bikram.remember.ui.edit.NoteIcon
import dev.bikram.remember.ui.feedback.tapSoundClickable
import dev.bikram.remember.ui.feedback.tapSoundCombinedClickable
import dev.bikram.remember.ui.theme.LocalHeroOnCards
import dev.bikram.remember.ui.theme.MorphPolygonShape
import dev.bikram.remember.ui.theme.elevatedCardColors
import dev.bikram.remember.ui.theme.reducedMotionAwareSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backward-compatible wrapper that accepts the raw [NoteWithItems] and converts it to
 * the immutable [NoteCardUiModel] before delegating to the model-based composable.
 * Prefer the [NoteCardUiModel] overload from new call sites so the UI tree stays
 * skippable on identical data.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteCard(
    note: NoteWithItems,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    NoteCard(
        model = remember(note) { note.toNoteCardUiModel() },
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        selected = selected,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteCard(
    model: NoteCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val cardColors = elevatedCardColors()
    val visibleTags = model.visibleTags
    val hasTagStrip = visibleTags.isNotEmpty()
    val heroEnabled = LocalHeroOnCards.current
    val heroPictureUri = model.pictureUri?.takeIf { heroEnabled }
    val showHero = heroPictureUri != null
    val surface = MaterialTheme.colorScheme.surface
    val cardShape = MaterialTheme.shapes.medium
    val starredIconDescription = stringResource(R.string.notecard_starred_cd)

    // Pre-resolve every fragment of the merged TalkBack announcement so the body of
    // [remember] below can stay context-free. The five child icon contentDescriptions
    // (starred, picture, attachment, reminder, recurring) further down the tree stay
    // in place; with [Modifier.semantics(mergeDescendants = true)] + an explicit parent
    // contentDescription the parent's string is what TalkBack reads, so the children
    // become harmless ornaments instead of getting announced one-by-one.
    val cdNotePrefix = stringResource(R.string.notecard_cd_note, model.title)
    val cdListPrefix = stringResource(R.string.notecard_cd_list, model.title)
    val cdSeparator = stringResource(R.string.notecard_cd_separator)
    val cdTagsTemplate = stringResource(R.string.notecard_cd_tags)
    val cdSelected = stringResource(R.string.notecard_cd_selected)
    val cdCompleted = stringResource(R.string.notecard_cd_completed)
    val cdReminderForAnnouncement = stringResource(R.string.notecard_reminder_cd)
    val cdRecurringForAnnouncement = stringResource(R.string.notecard_recurring_cd)
    val cdPictureForAnnouncement = stringResource(R.string.notecard_picture_cd)
    val cdAttachmentForAnnouncement = stringResource(R.string.notecard_attachment_cd)
    val noteCardAnnouncement =
        remember(
            model.kind,
            model.title,
            model.body,
            model.completed,
            model.starred,
            model.reminderAt,
            model.recurring,
            model.pictureUri,
            model.hasAttachment,
            model.visibleTags,
            selected,
        ) {
            buildList {
                add(if (model.kind == NoteKind.LIST) cdListPrefix else cdNotePrefix)
                if (selected) add(cdSelected)
                if (model.completed) add(cdCompleted)
                // Body preview is meaningful for note cards; list cards visually show
                // checklist items, not the body, so we skip it for those.
                if (model.kind != NoteKind.LIST && model.body.isNotBlank()) {
                    val firstLine =
                        model.body.lineSequence().firstOrNull { it.isNotBlank() }
                            ?: model.body
                    add(firstLine.trim().take(120))
                }
                if (model.visibleTags.isNotEmpty()) {
                    add(cdTagsTemplate.format(model.visibleTags.joinToString(", ")))
                }
                if (model.reminderAt != null) add(cdReminderForAnnouncement)
                if (model.recurring) add(cdRecurringForAnnouncement)
                if (model.starred) add(starredIconDescription)
                if (model.pictureUri != null) add(cdPictureForAnnouncement)
                if (model.hasAttachment) add(cdAttachmentForAnnouncement)
            }.joinToString(cdSeparator)
        }

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val sharedModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-card-${model.id}"),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }
    val sharedTitleModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "note-title-${model.id}"),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }
    val sharedIconModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = "note-icon-${model.id}"),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }

    // Selection and Completed progresses are both Animatables (initialized at 0 +
    // LaunchedEffect that animates to the current target) instead of
    // animateFloatAsState. animateFloatAsState's initial value equals its first
    // targetValue, which would skip the bloom whenever a card mounts already-selected
    // or already-completed -- typical when LazyColumn briefly evicts a row at the
    // visible boundary as the bottom selection action bar slides in or the Done
    // section header reflows the list. The pattern below tweens from 0 in those
    // remount cases and survives same-key recomposition normally.
    val animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    val selectionAnimatable = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        selectionAnimatable.animateTo(
            targetValue = if (selected) 1f else 0f,
            animationSpec = animationSpec,
        )
    }
    val selectionProgress = selectionAnimatable.value

    val completedAnimatable = remember { Animatable(0f) }
    LaunchedEffect(model.completed) {
        completedAnimatable.animateTo(
            targetValue = if (model.completed) 1f else 0f,
            animationSpec = animationSpec,
        )
    }
    val completedProgress = completedAnimatable.value
    // 1.0 when active -> 0.65 when fully completed; tweens with completedProgress.
    val completedCardAlpha = 1f - 0.35f * completedProgress

    // 1.0 when active -> 0.70 when fully completed; tweens with completedProgress.
    val completedSaturation = 1f - 0.30f * completedProgress

    /**
     * Done-badge progress factors out the selection bloom: when a completed card is
     * selected, the selection badge owns the TopEnd corner and the inline done badge
     * fades out so the two indicators don't visually overlap. Outside selection mode
     * (selectionProgress = 0), this just equals completedProgress.
     */
    val doneBadgeProgress = completedProgress * (1f - selectionProgress)

    // Done-state saturation reduction is applied as a RenderEffect on the card layer.
    // The dim alpha itself is kept inside the opaque Surface (see content below), so
    // swipe action backgrounds cannot bleed through completed cards while dragging.
    val completedRenderEffect =
        remember(completedSaturation) {
            if (completedSaturation >= 0.999f) {
                null
            } else {
                val matrix = ColorMatrix().apply { setToSaturation(completedSaturation) }
                val androidFilter = ColorFilter.colorMatrix(matrix).asAndroidColorFilter()
                android.graphics.RenderEffect
                    .createColorFilterEffect(androidFilter)
                    .asComposeRenderEffect()
            }
        }

    val selectionBorderColor = MaterialTheme.colorScheme.primary
    val selectionBorder =
        if (selected) {
            Modifier.border(BorderStroke(2.dp, selectionBorderColor), cardShape)
        } else {
            Modifier
        }
    val starredBorder =
        if (model.starred && !selected) {
            Modifier.border(BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.70f)), cardShape)
        } else {
            Modifier
        }
    val clickableModifier =
        if (onLongClick != null) {
            Modifier.tapSoundCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
            )
        } else {
            Modifier.tapSoundClickable(onClick = onClick)
        }
    // Subtle yellow wash on starred cards: blend ~7% of the star-yellow swatch into
    // the card's base container color so the card reads as starred at a glance without
    // competing with selection highlight, picture hero, or tag accents.
    val tintedContainerColor =
        if (model.starred) {
            lerp(cardColors.containerColor, Color(0xFFFFD54F), 0.07f)
        } else {
            cardColors.containerColor
        }
    val completedContainerColor =
        tintedContainerColor
            .copy(alpha = completedCardAlpha)
            .compositeOver(MaterialTheme.colorScheme.background)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    renderEffect = completedRenderEffect
                }.then(sharedModifier)
                .clip(cardShape)
                .then(starredBorder)
                .then(selectionBorder)
                .then(clickableModifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = noteCardAnnouncement
                },
        shape = cardShape,
        color = completedContainerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = completedCardAlpha
                    },
        ) {
            if (showHero) {
                HeroBackground(
                    uri = heroPictureUri,
                    framing =
                        remember(model.pictureHeroFraming) {
                            HeroFraming.fromJsonString(model.pictureHeroFraming)
                        },
                    cacheRevision = model.pictureCacheRevision,
                    scrimTop = surface.copy(alpha = 0.20f),
                    scrimBottom = surface.copy(alpha = 0.48f),
                )
            }
            // Watermark star for starred cards. Tilted ~-15deg, low alpha, parked at
            // the top-end. It deliberately sits *behind* the selection check overlay
            // and the trash "30 days left" chip (both painted later in this Box / by
            // the call site), so those affordances always win the corner. The star is
            // ornament-only -- TalkBack ignores it because the parent Surface already
            // declares mergeDescendants and contentDescription includes "Starred".
            if (model.starred) {
                RememberMaterialRoundedSymbol(
                    name = "star",
                    filled = true,
                    size = 96.dp,
                    tint = Color(0xFFFFD54F),
                    weight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                rotationZ = -15f
                                alpha = 0.13f
                            },
                )
            }
            Row(
                modifier =
                    if (hasTagStrip && !showHero) {
                        Modifier.height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)
                    } else {
                        Modifier
                    },
            ) {
                if (hasTagStrip && !showHero) {
                    TagAccentCardStrip(tags = visibleTags)
                }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (val headerIcon = model.icon) {
                            is NoteIcon.Symbol ->
                                RememberMaterialRoundedSymbol(
                                    name = headerIcon.name,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.then(sharedIconModifier).alpha(0.75f),
                                )
                            is NoteIcon.Drawable ->
                                Icon(
                                    painterResource(headerIcon.resId),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier =
                                        Modifier
                                            .size(18.dp)
                                            .then(sharedIconModifier)
                                            .alpha(0.75f),
                                )
                            is NoteIcon.Emoji ->
                                Text(
                                    text = headerIcon.text,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                    modifier = Modifier.then(sharedIconModifier).alpha(0.85f),
                                )
                            NoteIcon.ListPlaceholder ->
                                RememberMaterialRoundedSymbol(
                                    name = DEFAULT_LIST_HEADER_SYMBOL,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.then(sharedIconModifier).alpha(0.65f),
                                )
                            NoteIcon.NotePlaceholder ->
                                RememberMaterialRoundedSymbol(
                                    name = DEFAULT_NOTE_HEADER_SYMBOL,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.then(sharedIconModifier).alpha(0.65f),
                                )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f).then(sharedTitleModifier)) {
                            Text(
                                text =
                                    model.title.ifBlank {
                                        if (model.kind == NoteKind.NOTE) {
                                            stringResource(R.string.edit_note_title_new)
                                        } else {
                                            stringResource(R.string.edit_list_title_new)
                                        }
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration =
                                    if (model.completed) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (doneBadgeProgress > 0f) {
                            Spacer(Modifier.width(6.dp))
                            // Cookie4Sided -> Sunny morph mirrors the selection badge's
                            // visual language (small polygon + check) but uses a distinct
                            // shape pair and the tertiary color so the two states read
                            // as different. Driven by [doneBadgeProgress] -- not raw
                            // [completedProgress] -- so the badge fades out when the
                            // card is selected, ceding the corner to the selection check.
                            val completedMorph =
                                remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Clover4Leaf) }
                            val completedBadgeShape = MorphPolygonShape(completedMorph, doneBadgeProgress)
                            Box(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            val s = 0.5f + 0.5f * doneBadgeProgress
                                            scaleX = s
                                            scaleY = s
                                            alpha = doneBadgeProgress
                                        }.clip(completedBadgeShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "check",
                                    size = 12.dp,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    weight = FontWeight.Medium,
                                )
                            }
                        }
                        // Starred was previously a small inline star at the title's
                        // trailing edge. It overlapped with the top-end selection
                        // check and trash days-left chip. The starred cue now lives
                        // as a low-alpha tilted watermark in the card's top-right
                        // corner (rendered on the outer Box below) plus a subtle
                        // yellow tint on the card surface; the announcement still
                        // includes "Starred" via the parent contentDescription.
                    }
                    Spacer(Modifier.height(6.dp))
                    when (model.kind) {
                        NoteKind.NOTE -> {
                            if (model.body.isNotBlank()) {
                                MarkdownText(
                                    markdown = model.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(0.82f),
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.common_empty_note),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            }
                        }
                        NoteKind.LIST ->
                            ChecklistPreview(
                                items = model.checklistPreviewItems,
                                hiddenCount = model.checklistHiddenItemCount,
                            )
                    }
                    MetadataRow(model = model, visibleTags = visibleTags)
                }
            }
            // Selection badge: shape morph + bloom. [selectionProgress] is hoisted to
            // the top of NoteCard (alongside [completedProgress]) so the same value
            // drives both the rendering here and the [doneBadgeProgress] computation
            // that fades the inline done badge out when this badge fades in.
            if (selectionProgress > 0f) {
                val selectionMorph =
                    remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie7Sided) }
                val selectionBadgeShape = MorphPolygonShape(selectionMorph, selectionProgress)
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(9.dp)
                            .size(26.dp)
                            .graphicsLayer {
                                val s = 0.5f + 0.5f * selectionProgress
                                scaleX = s
                                scaleY = s
                                alpha = selectionProgress
                            }.clip(selectionBadgeShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "check",
                        size = 15.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        weight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HeroBackground(
    uri: String,
    framing: HeroFraming?,
    cacheRevision: Long,
    scrimTop: Color,
    scrimBottom: Color,
) {
    HeroFramedImage(
        imageUri = uri,
        framing = framing,
        cacheRevision = cacheRevision,
        imageAlpha = 1f,
        modifier = Modifier.matchParentSize(),
    )
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(colors = listOf(scrimTop, scrimBottom))),
    )
}

@Composable
private fun MetadataRow(
    model: NoteCardUiModel,
    visibleTags: List<String>,
) {
    val tags = visibleTags.take(3)
    val extraTags = (visibleTags.size - tags.size).coerceAtLeast(0)
    val reminderAt = model.reminderAt
    val isRecurring = model.recurring
    val hasPicture = model.pictureUri != null
    val hasAttachment = model.hasAttachment
    val anyMetadata = tags.isNotEmpty() || reminderAt != null || hasPicture || hasAttachment
    if (!anyMetadata) return

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag -> TagMini(tag) }
            if (extraTags > 0) {
                Text(
                    text = stringResource(R.string.notecard_extra_tags, extraTags),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.alpha(0.7f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPicture) {
                val cdPicture = stringResource(R.string.notecard_picture_cd)
                RememberMaterialRoundedSymbol(
                    name = "image",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdPicture }
                            .alpha(0.6f),
                )
            }
            if (hasAttachment) {
                val cdAttachment = stringResource(R.string.notecard_attachment_cd)
                RememberMaterialRoundedSymbol(
                    name = "attach_file",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdAttachment }
                            .alpha(0.6f),
                )
            }
            // Reminder slot - last in the row (rightmost). Replaces the old generic
            // notification icon with a short "MMM d" date string so the user reads the
            // due date directly off the card. When the note is recurring, a tiny
            // repeat icon precedes the date as a glanceable indicator.
            if (reminderAt != null) {
                val cdReminder = stringResource(R.string.notecard_reminder_cd)
                RememberMaterialRoundedSymbol(
                    name = "notifications",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .semantics { contentDescription = cdReminder }
                            .alpha(0.68f),
                )
                if (isRecurring) {
                    val cdRecurring = stringResource(R.string.notecard_recurring_cd)
                    RememberMaterialRoundedSymbol(
                        name = "repeat",
                        size = 14.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        weight = FontWeight.Medium,
                        modifier =
                            Modifier
                                .semantics { contentDescription = cdRecurring }
                                .alpha(0.68f),
                    )
                }
                Text(
                    text = formatShortReminderDate(reminderAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .alpha(0.85f),
                )
            }
        }
    }
}

/**
 * Short, glanceable due-date label used on note cards. "MMM d" by default ("Apr 27"),
 * which is readable at the small card-metadata size without consuming a year column
 * for dates the user is most likely to care about (the next 12 months). Anything
 * past that horizon falls back to "MMM yyyy" so the card never silently shows a
 * date in the wrong year.
 */
private fun formatShortReminderDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val twelveMonthsMs = 365L * 24 * 60 * 60 * 1000
    val pattern = if (kotlin.math.abs(epochMillis - now) > twelveMonthsMs) "MMM yyyy" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
}

@Composable
private fun TagMini(label: String) {
    TagChipFilled(tag = label, compact = true)
}

@Composable
private fun ChecklistPreview(
    items: List<NoteCardChecklistItemUiModel>,
    hiddenCount: Int,
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.common_empty_list),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0.5f),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indent children one gutter's width to mirror the editor hierarchy.
                if (item.depth > 0) Spacer(Modifier.width(16.dp))
                RememberMaterialRoundedSymbol(
                    name = if (item.checked) "check_circle" else "radio_button_unchecked",
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    weight = FontWeight.Medium,
                    modifier = Modifier.alpha(if (item.checked) 0.6f else 0.85f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.alpha(if (item.checked) 0.55f else 0.92f),
                )
            }
        }
        if (hiddenCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.notecard_extra_items, hiddenCount, hiddenCount),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}
