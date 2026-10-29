@file:OptIn(ExperimentalMaterial3Api::class)

package app.podhod.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.podhod.ui.components.ConfirmDialog
import app.podhod.ui.components.LocalContainer
import app.podhod.ui.screens.exercises.ExercisesScreen
import app.podhod.ui.screens.history.HistoryScreen
import app.podhod.ui.screens.history.SessionDetailScreen
import app.podhod.ui.screens.home.WorkoutsScreen
import app.podhod.ui.screens.program.ProgramEditorScreen
import app.podhod.ui.screens.progress.ProgressScreen
import app.podhod.ui.screens.settings.SettingsScreen
import app.podhod.ui.screens.workout.WorkoutScreen
import kotlinx.coroutines.flow.first

object Routes {
    const val TABS = "tabs"
    const val WORKOUT = "workout/{id}"
    const val PROGRAM = "program/{id}"
    const val SESSION = "session/{id}"
    const val SETTINGS = "settings"

    fun workout(id: Long) = "workout/$id"

    /** id = 0 — новая программа. */
    fun program(id: Long) = "program/$id"

    fun session(id: Long) = "session/$id"
}

private val FADE_IN: EnterTransition = fadeIn(tween(120))
private val FADE_OUT: ExitTransition = fadeOut(tween(90))

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(
        navController = nav,
        startDestination = Routes.TABS,
        enterTransition = { FADE_IN },
        exitTransition = { FADE_OUT },
        popEnterTransition = { FADE_IN },
        popExitTransition = { FADE_OUT },
    ) {
        composable(Routes.TABS) { MainTabs(nav) }

        composable(Routes.WORKOUT, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            WorkoutScreen(
                sessionId = id,
                onBack = { nav.popBackStack() },
                onFinished = { sessionId ->
                    nav.navigate(Routes.session(sessionId)) {
                        popUpTo(Routes.WORKOUT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PROGRAM, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ProgramEditorScreen(programId = id.takeIf { it > 0L }, onClose = { nav.popBackStack() })
        }

        composable(Routes.SESSION, arguments = idArg) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            SessionDetailScreen(
                sessionId = id,
                onBack = { nav.popBackStack() },
                onOpenWorkout = { sessionId ->
                    nav.navigate(Routes.workout(sessionId)) {
                        popUpTo(Routes.SESSION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    WORKOUTS("Тренировки", Icons.Filled.FitnessCenter),
    EXERCISES("Упражнения", Icons.AutoMirrored.Filled.List),
    PROGRESS("Прогресс", Icons.Filled.BarChart),
    HISTORY("История", Icons.Filled.History),
}

/** Живёт, пока жив процесс: подсказка о незавершённой тренировке показывается один раз за запуск. */
private object LaunchState {
    @Volatile
    var resumePromptHandled = false
}

@Composable
private fun MainTabs(nav: NavHostController) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val container = LocalContainer.current
    var resumeSessionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        if (!LaunchState.resumePromptHandled) {
            LaunchState.resumePromptHandled = true
            val active = container.repository.observeActiveSession().first()
            if (active != null) resumeSessionId = active.id
        }
    }

    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        bottomBar = {
            if (!imeOpen) {
                NavigationBar {
                    Tab.values().forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.padding(padding).imePadding()) {
            when (Tab.values()[tabIndex]) {
                Tab.WORKOUTS -> WorkoutsScreen(
                    onOpenWorkout = { nav.navigate(Routes.workout(it)) },
                    onNewProgram = { nav.navigate(Routes.program(0L)) },
                    onEditProgram = { nav.navigate(Routes.program(it)) },
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
                Tab.EXERCISES -> ExercisesScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
                Tab.PROGRESS -> ProgressScreen(
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
                Tab.HISTORY -> HistoryScreen(
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
        }
    }

    resumeSessionId?.let { id ->
        ConfirmDialog(
            title = "Незавершённая тренировка",
            text = "Прошлая тренировка не была завершена. Все введённые данные сохранены. " +
                "Продолжить её? Завершить или отменить тренировку можно на вкладке «Тренировки».",
            confirmLabel = "Продолжить",
            dismissLabel = "Позже",
            onConfirm = {
                resumeSessionId = null
                nav.navigate(Routes.workout(id))
            },
            onDismiss = { resumeSessionId = null },
        )
    }
}
