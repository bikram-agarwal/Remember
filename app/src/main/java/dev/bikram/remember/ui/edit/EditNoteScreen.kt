package dev.bikram.remember.ui.edit
import androidx.compose.material3.IconButton

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import dev.bikram.remember.ui.common.HeroFramingEditorDialog
import dev.bikram.remember.R
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.ui.modifiers.PillBottomBarHeight
import dev.bikram.remember.ui.modifiers.applyToFullBleedLayer
import dev.bikram.remember.ui.modifiers.rememberProgressiveBlurStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun EditNoteRoute(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    appScope: CoroutineScope,
    noteId: Long?,
    prefillBody: String = "",
    onBack: () -> Unit,
) {
    val vm: EditNoteViewModel = viewModel(
        key = "editNote-${noteId ?: 0L}",
        factory = EditNoteViewModel.factory(repository, themePrefs, noteId, prefillBody),
    )

    val sharedScope = dev.bikram.remember.ui.nav.LocalSharedTransitionScope.current
    val navScope = dev.bikram.remember.ui.nav.LocalNavAnimatedVisibilityScope.current
    val sharedModifier = if (sharedScope != null && navScope != null && noteId != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "note-card-${noteId}"),
                animatedVisibilityScope = navScope
            )
        }
    } else Modifier

    androidx.compose.foundation.layout.Box(modifier = sharedModifier.fillMaxSize()) {
        EditNoteScreen(
            vm = vm,
            repository = repository,
            appScope = appScope,
            editorNoteKey = noteId ?: 0L,
            existing = noteId != null,
            onTrash = {
                appScope.launch { vm.trashCurrent() }
                onBack()
            },
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalRichTextApi::class)
@Composable
fun EditNoteScreen(
    vm: EditNoteViewModel,
    repository: NoteRepository,
    appScope: CoroutineScope,
    editorNoteKey: Long,
    existing: Boolean,
    onTrash: () -> Unit,
    onBack: () -> Unit,
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)

    var reminderPickerOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerOpen by rememberSaveable { mutableStateOf(false) }
    var actionsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var tagsPickerOpen by rememberSaveable { mutableStateOf(false) }
    var attachmentsPickerOpen by rememberSaveable { mutableStateOf(false) }

    var pendingHeroSession by remember { mutableStateOf<Pair<String, File?>?>(null) }
    val launchHeroImagePick = rememberHeroImagePickThenCopy { uriString, copiedFile ->
        pendingHeroSession = uriString to copiedFile
    }
    var pictureViewer by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val titlePlaceholder = if (existing) {
        stringResource(R.string.edit_note_title_existing)
    } else {
        stringResource(R.string.common_title)
    }
    val bodyPlaceholder = stringResource(R.string.edit_note_body_placeholder)

    val blurStyle = rememberProgressiveBlurStyle(bottomExtra = PillBottomBarHeight * 0)
    val blurMod = remember(blurStyle) { blurStyle?.applyToFullBleedLayer() ?: Modifier }

    var isEditMode by remember(existing) { mutableStateOf(!existing) }
    val richTextState = rememberRichTextState()
    val undoController = remember(editorNoteKey) { UndoRedoController() }

    val bridge = rememberEditorBodyBridge(
        vm = vm,
        richTextState = richTextState,
        undoController = undoController,
        isEditMode = isEditMode,
        appScope = appScope,
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            val context = androidx.compose.ui.platform.LocalContext.current
            EditNoteTopBarSection(
                vm = vm,
                scrollBehavior = scrollBehavior,
                titlePlaceholder = titlePlaceholder,
                existing = existing,
                isEditMode = isEditMode,
                onBack = onBack,
                onToggleEditMode = {
                    val wasEditing = isEditMode
                    isEditMode = !isEditMode
                    if (wasEditing && !isEditMode) {
                        appScope.launch {
                            if (vm.saveIfNeeded()) {
                                android.widget.Toast.makeText(context, "Saved", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onTrash = onTrash,
            )
        },
        bottomBar = {
            EditNoteBottomBarSection(
                richTextState = richTextState,
                undoController = undoController,
                bridge = bridge,
                isEditMode = isEditMode,
            )
        },
    ) { padding ->
        EditNoteScrollableContent(
            vm = vm,
            scrollBehavior = scrollBehavior,
            horizontalPadding = 20.dp,
            padding = padding,
            richTextState = richTextState,
            bodyPlaceholder = bodyPlaceholder,
            isEditMode = isEditMode,
            onOpenReminder = { reminderPickerOpen = true },
            onOpenPicture = launchHeroImagePick,
            onViewPictureFull = { uri, revision ->
                pictureViewer = uri to revision
            },
            onOpenIcon = { iconPickerOpen = true },
            onOpenActions = { actionsPickerOpen = true },
            onOpenTags = { tagsPickerOpen = true },
            onOpenAttachments = { attachmentsPickerOpen = true },
            blurModifier = blurMod,
        )

        // Pickers each collect only the slice they need, lazily, so they impose no overhead
        // when closed. Inlined here (instead of dispatched from a giant 21-parameter function)
        // because the dispatch wrapper offered no reuse and made every open/dismiss callback
        // travel three layers down.
        if (reminderPickerOpen) {
            val reminderAt by vm.reminderAt.collectAsState()
            val recurrence by vm.recurrence.collectAsState()
            ReminderPickerSheet(
                initialMillis = reminderAt,
                initialRule = recurrence,
                onConfirm = { at, rule ->
                    vm.setReminder(at, rule)
                    reminderPickerOpen = false
                },
                onDismiss = { reminderPickerOpen = false },
            )
        }
        if (iconPickerOpen) {
            val iconKey by vm.iconKey.collectAsState()
            IconPicker(
                current = iconKey,
                onPick = {
                    vm.setIconKey(it)
                    iconPickerOpen = false
                },
                onDismiss = { iconPickerOpen = false },
            )
        }
        if (actionsPickerOpen) {
            val actions by vm.actions.collectAsState()
            ActionPicker(
                current = actions,
                onConfirm = {
                    vm.setActions(it)
                    actionsPickerOpen = false
                },
                onDismiss = { actionsPickerOpen = false },
            )
        }
        if (tagsPickerOpen) {
            val tags by vm.tags.collectAsState()
            TagEditorSheet(
                initial = tags,
                repository = repository,
                onConfirm = { newTags, newColors ->
                    vm.saveTagsWithColors(newTags, newColors)
                    tagsPickerOpen = false
                },
                onDismiss = { tagsPickerOpen = false },
            )
        }
        if (attachmentsPickerOpen) {
            val attachments by vm.attachments.collectAsState()
            AttachmentsSheet(
                attachments = attachments,
                onDismiss = { attachmentsPickerOpen = false },
                onAdd = vm::addAttachment,
                onRemove = vm::removeAttachment,
            )
        }
        pendingHeroSession?.let { (pickedUri, copiedFile) ->
            HeroFramingEditorDialog(
                imageUri = pickedUri,
                pendingCopiedFile = copiedFile,
                initialFraming = null,
                onDismiss = {
                    copiedFile?.delete()
                    pendingHeroSession = null
                },
                onConfirm = { framing ->
                    vm.setHeroWithFraming(pickedUri, framing)
                    pendingHeroSession = null
                },
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = pictureViewer != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.8f),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.8f),
    ) {
        val viewer = pictureViewer
        if (viewer != null) {
            androidx.activity.compose.BackHandler {
                pictureViewer = null
            }
            val imageRequest = dev.bikram.remember.ui.common.rememberHeroImageRequest(viewer.first, viewer.second, maxSidePx = 4096)
            androidx.compose.material3.Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxSize()
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    coil3.compose.AsyncImage(
                        model = imageRequest,
                        contentDescription = "Note hero image",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    dev.bikram.remember.ui.components.RememberIconButton(
                        onClick = { pictureViewer = null },
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.TopEnd)
                            .padding(8.dp)
                            .windowInsetsPadding(WindowInsets.systemBars),
                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.45f),
                            contentColor = Color.White,
                        ),
                    ) {
                        dev.bikram.remember.ui.common.RememberMaterialRoundedSymbol(
                            name = "close",
                            size = 24.dp,
                            tint = Color.White,
                            weight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
