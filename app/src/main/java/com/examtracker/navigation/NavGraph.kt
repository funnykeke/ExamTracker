package com.examtracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.examtracker.ui.add.AddExamScreen
import com.examtracker.ui.detail.ExamDetailScreen
import com.examtracker.ui.list.ExamListScreen
import com.examtracker.ui.table.TableScreen
import com.examtracker.ui.timeline.TimelineOverviewScreen

object Routes {
    const val LIST = "list"
    const val ADD = "add"
    const val DETAIL = "detail/{examId}"
    const val TABLE = "table"
    const val TIMELINE = "timeline"

    fun detail(examId: Long) = "detail/$examId"
}

private fun slideInRight(): EnterTransition =
    slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250))

private fun slideOutRight(): ExitTransition =
    slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(200))

private fun slideInLeft(): EnterTransition =
    slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(250))

private fun slideOutLeft(): ExitTransition =
    slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200))

private fun scaleEnter(): EnterTransition =
    scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    ) + fadeIn(tween(200))

private fun scaleExit(): ExitTransition =
    scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(200)
    ) + fadeOut(tween(150))

@Composable
fun ExamNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIST,
        enterTransition = { slideInRight() },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { slideInLeft() },
        popExitTransition = { slideOutRight() }
    ) {
        composable(
            Routes.LIST,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            ExamListScreen(
                onNavigateToAdd = {
                    navController.navigate(Routes.ADD)
                },
                onNavigateToDetail = { examId ->
                    navController.navigate(Routes.detail(examId))
                },
                onNavigateToTable = {
                    navController.navigate(Routes.TABLE)
                },
                onNavigateToTimeline = {
                    navController.navigate(Routes.TIMELINE)
                }
            )
        }

        composable(
            Routes.ADD,
            enterTransition = { scaleEnter() },
            popExitTransition = { scaleExit() }
        ) {
            AddExamScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onExamSaved = { examId ->
                    navController.navigate(Routes.detail(examId)) {
                        popUpTo(Routes.ADD) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("examId") { type = NavType.LongType }),
            enterTransition = { slideInRight() },
            popExitTransition = { slideOutRight() }
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getLong("examId") ?: return@composable
            ExamDetailScreen(
                examId = examId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            Routes.TABLE,
            enterTransition = { slideInRight() },
            popExitTransition = { slideOutRight() }
        ) {
            TableScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onExamClick = { examId ->
                    navController.navigate(Routes.detail(examId))
                }
            )
        }

        composable(
            Routes.TIMELINE,
            enterTransition = { slideInRight() },
            popExitTransition = { slideOutRight() }
        ) {
            TimelineOverviewScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetail = { examId ->
                    navController.navigate(Routes.detail(examId))
                }
            )
        }
    }
}
