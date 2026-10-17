package com.ashrdev.aznd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashrdev.aznd.data.AppDatabase
import com.ashrdev.aznd.data.manage.DataApp
import com.ashrdev.aznd.data.manage.DataRegistry
import com.ashrdev.aznd.data.manage.DataTransfer
import com.ashrdev.aznd.ui.home.HomeScreen
import com.ashrdev.aznd.ui.manage.LocalDataTransfer
import com.ashrdev.aznd.ui.manage.LocalManageData
import com.ashrdev.aznd.ui.manage.ManageDataLauncher
import com.ashrdev.aznd.ui.manage.ManageDataScreen
import com.ashrdev.aznd.ui.manage.ManageDataViewModel
import com.ashrdev.aznd.ui.manage.ManagePage
import com.ashrdev.aznd.ui.navigation.Destination
import com.ashrdev.aznd.ui.settings.SettingsScreen
import com.ashrdev.aznd.ui.settings.AppearanceScreen
import com.ashrdev.aznd.data.workout.TrainingRepository
import com.ashrdev.aznd.data.workout.WorkoutSettingsStore
import com.ashrdev.aznd.ui.workout.BuilderViewModel
import com.ashrdev.aznd.ui.workout.CustomExerciseFormScreen
import com.ashrdev.aznd.data.body.RoomBodyweightProvider
import com.ashrdev.aznd.data.workout.EMPTY_WORKOUT_ID
import com.ashrdev.aznd.domain.Muscle
import com.ashrdev.aznd.data.macro.MacroTargetsStore
import com.ashrdev.aznd.ui.body.BodyViewModel
import com.ashrdev.aznd.ui.macro.MacroScreen
import com.ashrdev.aznd.ui.macro.MacroViewModel
import com.ashrdev.aznd.ui.body.BodyScreen
import com.ashrdev.aznd.ui.body.BodyHistoryScreen
import com.ashrdev.aznd.ui.body.CompositionHistoryScreen
import com.ashrdev.aznd.ui.body.CompositionMetric
import com.ashrdev.aznd.ui.workout.ExerciseViewerScreen
import com.ashrdev.aznd.ui.workout.LocalOpenCustomExercise
import com.ashrdev.aznd.ui.workout.CustomExerciseFormViewModel
import com.ashrdev.aznd.ui.workout.MuscleStatsScreen
import com.ashrdev.aznd.ui.workout.MuscleStatsViewModel
import com.ashrdev.aznd.ui.workout.CustomExerciseListScreen
import com.ashrdev.aznd.ui.workout.CustomExerciseListViewModel
import com.ashrdev.aznd.ui.workout.LoggerViewModel
import com.ashrdev.aznd.ui.workout.WorkoutBuilderScreen
import com.ashrdev.aznd.ui.workout.WorkoutSessionScreen
import com.ashrdev.aznd.ui.streaks.SavedDayDetailScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysForStreakScreen
import com.ashrdev.aznd.ui.streaks.SavedDaysScreen
import com.ashrdev.aznd.ui.streaks.StreakDetailScreen
import com.ashrdev.aznd.ui.streaks.StreakEditorScreen
import com.ashrdev.aznd.ui.streaks.StreakViewModel
import com.ashrdev.aznd.ui.streaks.StreakViewModelFactory
import com.ashrdev.aznd.ui.streaks.StreaksScreen
import com.ashrdev.aznd.ui.theme.AzndTheme
import com.ashrdev.aznd.ui.theme.ThemeStore
import com.ashrdev.aznd.ui.workout.ExerciseProgressScreen
import com.ashrdev.aznd.ui.workout.WorkoutHistoryListScreen
import com.ashrdev.aznd.ui.workout.SessionDetailScreen
import com.ashrdev.aznd.ui.workout.WorkoutHistoryScreen
import com.ashrdev.aznd.ui.workout.WorkoutsScreen
import com.ashrdev.aznd.ui.common.GraphRangeStore
import com.ashrdev.aznd.ui.common.LocalGraphRangeStore
import com.ashrdev.aznd.ui.workout.WorkoutGraphViewModel
import com.ashrdev.aznd.ui.workout.WorkoutStatsScreen
import com.ashrdev.aznd.ui.workout.WorkoutViewModel
import com.ashrdev.aznd.ui.workout.WorkoutViewModelFactory
import com.ashrdev.aznd.ui.workout.WorkoutDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeStore.init(this)
        val database = AppDatabase.getInstance(this)
        val dao = database.workoutDao()
        val streakDao = database.streakDao()
        val trainingRepository = TrainingRepository(
            database.exerciseDao(), database.templateDao(), database.sessionDao(),
            bodyweight = RoomBodyweightProvider(database.bodyDao()), legacy = dao
        )
        val workoutSettings = WorkoutSettingsStore(this)
        val graphRange = GraphRangeStore(this)
        val macroTargets = MacroTargetsStore(this)
        val dataTransfer = DataTransfer(DataRegistry.create(this, database, trainingRepository, macroTargets))
        setContent {
            AzndTheme {
                CompositionLocalProvider(LocalGraphRangeStore provides graphRange) {
                  val navController = rememberNavController()
                  val workoutViewModel: WorkoutViewModel =
                      viewModel(factory = WorkoutViewModelFactory(dao, trainingRepository))
                  val streakViewModel: StreakViewModel =
                      viewModel(factory = StreakViewModelFactory(streakDao))

                  // Bumped by the Home button so the Home screen jumps to its middle "Home" page.
                  // (Plain Back leaves you on whichever page you came from.)
                  var homeSignal by remember { mutableIntStateOf(0) }

                  // Navigation guards. A tap that lands during a screen transition (or a fast
                  // double tap) is ignored, so a screen can't be pushed or popped twice, and the
                  // last screen can never be popped away (that leaves a blank black app).
                  fun settled(): Boolean =
                      navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

                  fun navigateTo(
                      route: String,
                      popUpToRoute: String? = null,
                      popUpInclusive: Boolean = false
                  ) {
                      if (!settled()) return
                      navController.navigate(route) {
                          launchSingleTop = true
                          if (popUpToRoute != null) {
                              popUpTo(popUpToRoute) { inclusive = popUpInclusive }
                          }
                      }
                  }

                  fun popToRoute(route: String): Boolean {
                      if (!settled()) return false
                      return navController.popBackStack(route, false)
                  }

                  // Shared by every screen's bottom bar.
                  val goBack: () -> Unit = {
                      if (settled() && navController.previousBackStackEntry != null) {
                          navController.popBackStack()
                      }
                  }
                  val goHome: () -> Unit = { if (popToRoute(Destination.Home.route)) homeSignal++ }
                  val openSettings: () -> Unit = { navigateTo(Destination.Settings.route) }

                  // Very subtle fade. The screen underneath is always fully opaque, so the window
                  // background (black) can never show through mid-transition.
                  val manageLauncher = remember(navController) {
                      ManageDataLauncher(
                          openForCurrentScreen = {
                              val route = navController.currentBackStackEntry?.destination?.route
                              navigateTo(Destination.ManageData.createRoute(DataApp.fromRoute(route)))
                          },
                          open = { app, page -> navigateTo(Destination.ManageData.createRoute(app, page)) }
                      )
                  }

                  CompositionLocalProvider(
                      LocalManageData provides manageLauncher,
                      LocalDataTransfer provides dataTransfer
                  ) {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.Home.route,
                        enterTransition = { fadeIn(animationSpec = tween(140)) },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { fadeOut(animationSpec = tween(140)) }
                    ) {
                        composable(Destination.Home.route) {
                            HomeScreen(
                                workoutViewModel = workoutViewModel,
                                streakViewModel = streakViewModel,
                                homeSignal = homeSignal,
                                onNavigate = { route -> navigateTo(route) }
                            )
                        }
                        composable(Destination.Settings.route) {
                            SettingsScreen(
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                onOpenAppearance = { navigateTo(Destination.Appearance.route) },
                                workoutSettings = workoutSettings
                            )
                        }
                        composable(Destination.Appearance.route) {
                            AppearanceScreen(
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }

                        composable(
                            route = Destination.ManageData.route,
                            arguments = listOf(
                                navArgument("app") {
                                    type = NavType.StringType
                                    defaultValue = "all"
                                },
                                navArgument("page") {
                                    type = NavType.StringType
                                    defaultValue = "delete"
                                }
                            )
                        ) { backStackEntry ->
                            val manageVm: ManageDataViewModel =
                                viewModel(factory = ManageDataViewModel.factory(dataTransfer))
                            ManageDataScreen(
                                viewModel = manageVm,
                                initialApp = DataApp.fromId(backStackEntry.arguments?.getString("app")),
                                initialPage = ManagePage.fromId(backStackEntry.arguments?.getString("page")),
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }

                        // ---- Workouts ----
                        composable(Destination.Workouts.route) {
                            CompositionLocalProvider(
                                LocalOpenCustomExercise provides { navigateTo(Destination.CustomExerciseForm.createRoute(null)) }
                            ) {
                                WorkoutsScreen(
                                    viewModel = workoutViewModel,
                                    onOpenWorkout = { id ->
                                        navigateTo(Destination.WorkoutDetail.createRoute(id))
                                    },
                                    onAddWorkout = {
                                        navigateTo(Destination.WorkoutBuilder.createRoute(null))
                                    },
                                    onOpenSession = { id -> navigateTo(Destination.WorkoutSession.createRoute(id)) },
                                    onOpenWorkoutStats = { id -> navigateTo(Destination.WorkoutStats.createRoute(id)) },
                                    onOpenExercise = { id -> navigateTo(Destination.ExerciseViewer.createRoute(id)) },
                                    onBack = goBack,
                                    onHome = goHome,
                                    onOpenSettings = openSettings
                                )
                            }
                        }
                        composable(Destination.WorkoutHistoryList.route) {
                            WorkoutHistoryListScreen(
                                viewModel = workoutViewModel,
                                onOpenWorkout = { id ->
                                    navigateTo(Destination.WorkoutHistory.createRoute(id))
                                },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.WorkoutHistory.route,
                            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                            WorkoutHistoryScreen(
                                workoutId = workoutId,
                                viewModel = workoutViewModel,
                                onOpenSession = { id ->
                                    navigateTo(Destination.SessionDetail.createRoute(id))
                                },
                                onOpenStats = { id ->
                                    navigateTo(Destination.WorkoutStats.createRoute(id))
                                },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.WorkoutStats.route,
                            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                            val graphVm: WorkoutGraphViewModel = viewModel(
                                factory = WorkoutGraphViewModel.factory(
                                    trainingRepository, workoutId, workoutSettings.settings.value.trackRpe
                                )
                            )
                            WorkoutStatsScreen(
                                graphViewModel = graphVm,
                                workoutId = workoutId,
                                viewModel = workoutViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                useTargets = workoutSettings.settings.value.targetReps
                            )
                        }
                        composable(
                            route = Destination.WorkoutDetail.route,
                            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                            WorkoutDetailScreen(
                                workoutId = workoutId,
                                viewModel = workoutViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                onEdit = { id -> navigateTo(Destination.WorkoutBuilder.createRoute(id)) },
                                onOpenSession = { id -> navigateTo(Destination.WorkoutSession.createRoute(id)) },
                                onViewExercise = { exId ->
                                    navigateTo(Destination.ExerciseViewer.createRoute(exId))
                                }
                            )
                        }
                        composable(
                            route = Destination.ExerciseViewer.route,
                            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: -1L
                            ExerciseViewerScreen(
                                exerciseId = exerciseId,
                                viewModel = workoutViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                onEdit = { id -> navigateTo(Destination.CustomExerciseForm.createRoute(id)) }
                            )
                        }
                        composable(
                            route = Destination.ExerciseProgress.route,
                            arguments = listOf(
                                navArgument("workoutId") { type = NavType.LongType },
                                navArgument("exerciseId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                            val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: -1L
                            ExerciseProgressScreen(
                                workoutId = workoutId,
                                exerciseId = exerciseId,
                                viewModel = workoutViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                useTargets = workoutSettings.settings.value.targetReps
                            )
                        }
                        composable(
                            route = Destination.WorkoutSession.route,
                            arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                            val workoutName by produceState(initialValue = "", workoutId) {
                                value = if (workoutId == EMPTY_WORKOUT_ID) "Empty workout"
                                else workoutViewModel.getWorkout(workoutId)?.workout?.name.orEmpty()
                            }
                            val loggerVm: LoggerViewModel =
                                viewModel(factory = LoggerViewModel.factory(trainingRepository, workoutSettings))
                            WorkoutSessionScreen(
                                workoutId = workoutId,
                                workoutName = workoutName,
                                viewModel = loggerVm,
                                onFinished = goBack,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.SessionDetail.route,
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: -1L
                            SessionDetailScreen(
                                sessionId = sessionId,
                                viewModel = workoutViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.WorkoutBuilder.route,
                            arguments = listOf(navArgument("workoutId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            })
                        ) { backStackEntry ->
                            val workoutId = backStackEntry.arguments
                                ?.getLong("workoutId")
                                ?.takeIf { it != -1L }
                            // One builder per screen instance: it survives rotation, but opening the
                            // editor again starts from the saved template, never from an earlier session's edits.
                            val builderVm: BuilderViewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                factory = BuilderViewModel.factory(trainingRepository, workoutSettings)
                            )
                            // The name must be known before the screen starts (it seeds the text field).
                            val initialName by produceState<String?>(
                                initialValue = if (workoutId == null) "" else null,
                                workoutId
                            ) {
                                value = workoutId?.let { workoutViewModel.getWorkout(it)?.workout?.name.orEmpty() }.orEmpty()
                            }
                            initialName?.let { loadedName ->
                              CompositionLocalProvider(
                                LocalOpenCustomExercise provides { navigateTo(Destination.CustomExerciseForm.createRoute(null)) }
                              ) {
                                WorkoutBuilderScreen(
                                    workoutId = workoutId,
                                    initialName = loadedName,
                                    viewModel = builderVm,
                                    onSaveRecord = { name -> workoutViewModel.saveWorkoutRecord(workoutId, name) },
                                    onDelete = if (workoutId == null) null else ({
                                        workoutViewModel.deleteWorkout(workoutId)
                                        popToRoute(Destination.Workouts.route)
                                    }),
                                    deleteTemplate = trainingRepository::deleteTemplate,
                                    onDone = goBack,
                                    onHome = goHome,
                                    onOpenSettings = openSettings
                                )
                              }
                            }
                        }

                        composable(
                            route = Destination.MuscleStats.route,
                            arguments = listOf(navArgument("muscle") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val muscle = backStackEntry.arguments?.getString("muscle")
                                ?.let { n -> Muscle.entries.firstOrNull { it.name == n } }
                            if (muscle != null) {
                                val statsVm: MuscleStatsViewModel = viewModel(
                                    factory = MuscleStatsViewModel.factory(
                                        trainingRepository, muscle, workoutSettings.settings.value.trackRpe
                                    )
                                )
                                MuscleStatsScreen(
                                    viewModel = statsVm,
                                    onBack = goBack,
                                    onHome = goHome,
                                    onOpenSettings = openSettings
                                )
                            }
                        }
                        composable(Destination.Macro.route) {
                            val macroVm: MacroViewModel = viewModel(
                                factory = MacroViewModel.factory(
                                    database.foodDao(), database.ingredientDao(), database.bodyDao(), trainingRepository, workoutSettings.settings.value.trackRpe
                                )
                            )
                            MacroScreen(
                                viewModel = macroVm,
                                targets = macroTargets,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(Destination.BodyHistory.route) {
                            val bodyVm: BodyViewModel = viewModel(factory = BodyViewModel.factory(database.bodyDao()))
                            BodyHistoryScreen(
                                viewModel = bodyVm,
                                onOpenComposition = { metric -> navigateTo(Destination.BodyCompositionHistory.createRoute(metric)) },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.BodyCompositionHistory.route,
                            arguments = listOf(navArgument("metric") { type = NavType.StringType })
                        ) { entry ->
                            val bodyVm: BodyViewModel = viewModel(factory = BodyViewModel.factory(database.bodyDao()))
                            CompositionHistoryScreen(
                                metric = CompositionMetric.fromRouteId(entry.arguments?.getString("metric")),
                                viewModel = bodyVm,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(Destination.Body.route) {
                            val bodyVm: BodyViewModel = viewModel(factory = BodyViewModel.factory(database.bodyDao()))
                            BodyScreen(
                                viewModel = bodyVm,
                                onOpenMuscle = { muscle -> navigateTo(Destination.MuscleStats.createRoute(muscle)) },
                                onOpenCompositionHistory = { metric -> navigateTo(Destination.BodyCompositionHistory.createRoute(metric)) },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(Destination.CustomExerciseList.route) {
                            val listVm: CustomExerciseListViewModel =
                                viewModel(factory = CustomExerciseListViewModel.factory(trainingRepository))
                            CustomExerciseListScreen(
                                viewModel = listVm,
                                onAdd = { navigateTo(Destination.CustomExerciseForm.createRoute(null)) },
                                onEdit = { id -> navigateTo(Destination.CustomExerciseForm.createRoute(id)) },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.CustomExerciseForm.route,
                            arguments = listOf(navArgument("exerciseId") {
                                type = NavType.LongType
                                defaultValue = -1L
                            })
                        ) { backStackEntry ->
                            val editId = backStackEntry.arguments?.getLong("exerciseId")?.takeIf { it != -1L }
                            val customVm: CustomExerciseFormViewModel =
                                viewModel(factory = CustomExerciseFormViewModel.factory(trainingRepository, editId))
                            CustomExerciseFormScreen(
                                viewModel = customVm,
                                onSaved = goBack,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }

                        // ---- Streaks ----
                        composable(Destination.Streaks.route) {
                            StreaksScreen(
                                viewModel = streakViewModel,
                                onOpenStreak = { id -> navigateTo(Destination.StreakDetail.createRoute(id)) },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(Destination.StreakEditor.route) {
                            StreakEditorScreen(
                                viewModel = streakViewModel,
                                onDone = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.StreakDetail.route,
                            arguments = listOf(navArgument("streakId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                            StreakDetailScreen(
                                streakId = streakId,
                                viewModel = streakViewModel,
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings,
                                onOpenSavedDays = {
                                    navigateTo(Destination.SavedDaysForStreak.createRoute(streakId))
                                },
                                onSaveDay = { sId, date ->
                                    navigateTo(Destination.SavedDayDetail.createRoute(sId, date))
                                }
                            )
                        }
                        composable(Destination.SavedDays.route) {
                            SavedDaysScreen(
                                viewModel = streakViewModel,
                                onOpenStreak = { id ->
                                    navigateTo(Destination.SavedDaysForStreak.createRoute(id))
                                },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.SavedDaysForStreak.route,
                            arguments = listOf(navArgument("streakId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                            SavedDaysForStreakScreen(
                                streakId = streakId,
                                viewModel = streakViewModel,
                                onOpenDay = { sId, date ->
                                    navigateTo(Destination.SavedDayDetail.createRoute(sId, date))
                                },
                                onBack = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                        composable(
                            route = Destination.SavedDayDetail.route,
                            arguments = listOf(
                                navArgument("streakId") { type = NavType.LongType },
                                navArgument("date") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val streakId = backStackEntry.arguments?.getLong("streakId") ?: -1L
                            val date = backStackEntry.arguments?.getString("date") ?: ""
                            SavedDayDetailScreen(
                                streakId = streakId,
                                date = date,
                                viewModel = streakViewModel,
                                onDone = goBack,
                                onHome = goHome,
                                onOpenSettings = openSettings
                            )
                        }
                    }
                  }
                }
            }
        }
    }
}
