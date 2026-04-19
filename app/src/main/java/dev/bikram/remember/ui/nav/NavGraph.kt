package dev.bikram.remember.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavController
import dev.bikram.remember.data.InteractionPrefs
import dev.bikram.remember.data.NoteKind
import dev.bikram.remember.data.NoteRepository
import dev.bikram.remember.data.NoteWithItems
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.ui.edit.EditListRoute
import dev.bikram.remember.ui.edit.EditNoteRoute
import dev.bikram.remember.ui.main.MainTabScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

object Routes {
    const val MAIN = "main"
    const val EDIT_NOTE = "editNote"
    const val EDIT_LIST = "editList"
    const val ARG_ID = "id"
    const val ARG_PREFILL = "prefill"
    fun editNote(id: Long?, prefill: String = ""): String {
        val p = if (prefill.isNotEmpty()) "&$ARG_PREFILL=${Uri.encode(prefill)}" else ""
        return "$EDIT_NOTE?${ARG_ID}=${id ?: -1L}$p"
    }
    fun editList(id: Long?) = "$EDIT_LIST?${ARG_ID}=${id ?: -1L}"
}

@Composable
fun RememberNavGraph(
    repository: NoteRepository,
    themePrefs: ThemePrefs,
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(launchFlow) {
        launchFlow?.collectLatest { action ->
            if (action == null) return@collectLatest
            try {
                when (action) {
                    is LaunchAction.NewNote -> navController.navigate(Routes.editNote(null, action.prefill))
                    LaunchAction.NewList -> navController.navigate(Routes.editList(null))
                    is LaunchAction.OpenNote -> {
                        val note = repository.get(action.id) ?: return@collectLatest
                        navController.openEditRouteFor(note)
                    }
                }
            } finally {
                launchFlow.value = null
            }
        }
    }

    androidx.compose.animation.SharedTransitionLayout {
        NavHost(navController = navController, startDestination = Routes.MAIN) {

            composable(Routes.MAIN) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable
                ) {
                    MainTabScaffold(
                        repository = repository,
                        themePrefs = themePrefs,
                        interactionPrefs = interactionPrefs,
                        onCreateNote = { navController.navigate(Routes.editNote(null)) },
                        onCreateList = { navController.navigate(Routes.editList(null)) },
                        onOpenNote = { note -> navController.openEditRouteFor(note) },
                    )
                }
            }

            composable(
                route = "${Routes.EDIT_NOTE}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}",
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(Routes.ARG_PREFILL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                val prefill = entry.arguments?.getString(Routes.ARG_PREFILL).orEmpty()
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable
                ) {
                    EditNoteRoute(
                        repository = repository,
                        themePrefs = themePrefs,
                        appScope = appScope,
                        noteId = id.takeIf { it > 0 },
                        prefillBody = prefill,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(
                route = "${Routes.EDIT_LIST}?${Routes.ARG_ID}={${Routes.ARG_ID}}",
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable
                ) {
                    EditListRoute(
                        repository = repository,
                        themePrefs = themePrefs,
                        appScope = appScope,
                        noteId = id.takeIf { it > 0 },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavController.openEditRouteFor(note: NoteWithItems) {
    val route = when (note.note.kind) {
        NoteKind.NOTE -> Routes.editNote(note.note.id)
        NoteKind.LIST -> Routes.editList(note.note.id)
    }
    navigate(route)
}
