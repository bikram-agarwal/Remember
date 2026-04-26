package dev.bikram.remember.ui.nav

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.bikram.remember.data.OnboardingPrefs
import dev.bikram.remember.data.ThemePrefs
import dev.bikram.remember.data.ViewOptionsPrefs
import dev.bikram.remember.di.LaunchAction
import dev.bikram.remember.googletasks.GoogleTasksImportRoute
import dev.bikram.remember.ui.edit.EditListRoute
import dev.bikram.remember.ui.edit.EditNoteRoute
import dev.bikram.remember.ui.main.MainTabScaffold
import dev.bikram.remember.ui.onboarding.OnboardingPermissionsScreen
import dev.bikram.remember.ui.onboarding.OnboardingTitleScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING_TITLE = "onboardingTitle"
    const val ONBOARDING_PERMISSIONS = "onboardingPermissions"
    const val MAIN = "main"
    const val EDIT_NOTE = "editNote"
    const val EDIT_LIST = "editList"
    const val GOOGLE_TASKS_IMPORT = "googleTasksImport"
    const val ARG_ID = "id"
    const val ARG_PREFILL = "prefill"
    const val ARG_FORCE_EDIT = "forceEdit"
    fun editNote(id: Long?, prefill: String = "", forceEdit: Boolean = false): String {
        val p = if (prefill.isNotEmpty()) "&$ARG_PREFILL=${Uri.encode(prefill)}" else ""
        val f = if (forceEdit) "&$ARG_FORCE_EDIT=true" else ""
        return "$EDIT_NOTE?${ARG_ID}=${id ?: -1L}$p$f"
    }
    fun editList(id: Long?, forceEdit: Boolean = false): String {
        val f = if (forceEdit) "&${ARG_FORCE_EDIT}=true" else ""
        return "$EDIT_LIST?${ARG_ID}=${id ?: -1L}$f"
    }
}

@Composable
fun RememberNavGraph(
    repository: NoteRepository,
    onboardingPrefs: OnboardingPrefs,
    themePrefs: ThemePrefs,
    viewOptionsPrefs: ViewOptionsPrefs,
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>? = null,
) {
    val navController = rememberNavController()
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val currentOnboardingState = onboardingState
    val onboardingScope = rememberCoroutineScope()

    if (currentOnboardingState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val lockedStartDestination = remember(currentOnboardingState.hasSeenIntro) {
        if (currentOnboardingState.hasSeenIntro) {
            Routes.MAIN
        } else {
            Routes.ONBOARDING_TITLE
        }
    }

    LaunchedEffect(launchFlow, currentOnboardingState.hasSeenIntro) {
        if (!currentOnboardingState.hasSeenIntro) return@LaunchedEffect
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
        NavHost(
            navController = navController,
            startDestination = lockedStartDestination,
        ) {

            composable(
                route = Routes.ONBOARDING_TITLE,
                enterTransition = {
                    if (initialState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                        slideInHorizontally { -it } + fadeIn()
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_PERMISSIONS) {
                        slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        null
                    }
                },
            ) {
                OnboardingTitleScreen(
                    onLetsBegin = {
                        navController.navigate(Routes.ONBOARDING_PERMISSIONS)
                    },
                )
            }

            composable(
                route = Routes.ONBOARDING_PERMISSIONS,
                enterTransition = {
                    if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                        slideInHorizontally { it } + fadeIn()
                    } else {
                        null
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_TITLE ||
                        targetState.destination.route == Routes.MAIN
                    ) {
                        slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        null
                    }
                },
                popEnterTransition = {
                    if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                        slideInHorizontally { it } + fadeIn()
                    } else {
                        null
                    }
                },
                popExitTransition = {
                    if (targetState.destination.route == Routes.ONBOARDING_TITLE) {
                        slideOutHorizontally { it } + fadeOut()
                    } else {
                        null
                    }
                },
            ) {
                OnboardingPermissionsScreen(
                    onContinue = {
                        onboardingScope.launch {
                            onboardingPrefs.markIntroSeen()
                            navController.navigate(Routes.MAIN) {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                            }
                        }
                    },
                )
            }

            composable(Routes.MAIN) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable
                ) {
                    MainTabScaffold(
                        repository = repository,
                        themePrefs = themePrefs,
                        viewOptionsPrefs = viewOptionsPrefs,
                        interactionPrefs = interactionPrefs,
                        onCreateNote = { navController.navigate(Routes.editNote(null)) },
                        onCreateList = { navController.navigate(Routes.editList(null)) },
                        onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                        onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                        onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                    )
                }
            }

            composable(
                route = "${Routes.EDIT_NOTE}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}",
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(Routes.ARG_PREFILL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_FORCE_EDIT) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                val prefill = entry.arguments?.getString(Routes.ARG_PREFILL).orEmpty()
                val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
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
                        forceEdit = forceEdit,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.GOOGLE_TASKS_IMPORT) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable,
                ) {
                    GoogleTasksImportRoute(onBack = { navController.popBackStack() })
                }
            }

            composable(
                route = "${Routes.EDIT_LIST}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}",
                arguments = listOf(
                    navArgument(Routes.ARG_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(Routes.ARG_FORCE_EDIT) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    LocalNavAnimatedVisibilityScope provides this@composable
                ) {
                    EditListRoute(
                        repository = repository,
                        themePrefs = themePrefs,
                        appScope = appScope,
                        noteId = id.takeIf { it > 0 },
                        forceEdit = forceEdit,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun NavController.openEditRouteFor(note: NoteWithItems, forceEdit: Boolean = false) {
    val route = when (note.note.kind) {
        NoteKind.NOTE -> Routes.editNote(note.note.id, forceEdit = forceEdit)
        NoteKind.LIST -> Routes.editList(note.note.id, forceEdit = forceEdit)
    }
    navigate(route)
}
