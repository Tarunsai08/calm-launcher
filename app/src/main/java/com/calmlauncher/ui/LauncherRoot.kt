package com.calmlauncher.ui

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calmlauncher.ActivityEvent
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmTheme
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.feature.digest.DigestScreen
import com.calmlauncher.feature.digest.NotificationRulesScreen
import com.calmlauncher.feature.focus.FocusScreen
import com.calmlauncher.feature.focus.RulesScreen
import com.calmlauncher.feature.home.HomeScreen
import com.calmlauncher.feature.insights.InsightsScreen
import com.calmlauncher.feature.onboarding.OnboardingScreen
import com.calmlauncher.feature.settings.AboutScreen
import com.calmlauncher.feature.settings.CategoriesScreen
import com.calmlauncher.feature.settings.FavoritesScreen
import com.calmlauncher.feature.settings.HiddenAppsScreen
import com.calmlauncher.feature.settings.OemHelpScreen
import com.calmlauncher.feature.settings.PermissionExplainerScreen
import com.calmlauncher.feature.settings.SettingsScreen
import com.calmlauncher.feature.tools.NotesScreen
import com.calmlauncher.feature.tools.TodosScreen
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** State of the home overlay (drawer/search), hoisted so gestures and effects can drive it. */
class HomeOverlayState(initialOpen: Boolean = false) {
    var drawerOpen by mutableStateOf(initialOpen)
    var focusSearchToken by mutableIntStateOf(0)

    /** Text typed on a hardware keyboard at home, handed to the search field once it opens. */
    var pendingQuery by mutableStateOf<String?>(null)

    fun openDrawer(focusSearch: Boolean) {
        drawerOpen = true
        if (focusSearch) focusSearchToken++
    }

    fun close() {
        drawerOpen = false
    }
}

@Composable
fun LauncherRoot(
    settings: LauncherSettings,
    activityEvents: SharedFlow<ActivityEvent>,
    initialRoute: String?,
) {
    val vm: LauncherViewModel = hiltViewModel()
    val nav = rememberNavController()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var drawerSaved by rememberSaveable { mutableStateOf(false) }
    val overlay = remember { HomeOverlayState(drawerSaved) }
    val scope = rememberCoroutineScope()
    val motion = CalmTheme.motion
    val currentSettings by rememberUpdatedState(settings)

    LaunchedEffect(overlay.drawerOpen) { drawerSaved = overlay.drawerOpen }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                UiEffect.OpenDrawer -> overlay.openDrawer(focusSearch = false)
                UiEffect.OpenSearch -> overlay.openDrawer(focusSearch = true)
                is UiEffect.Navigate -> nav.navigateSingleTop(effect.route)
                is UiEffect.Message -> Toast.makeText(context, effect.textRes, Toast.LENGTH_SHORT).show()
                UiEffect.LockNeedsSetup -> {
                    Toast.makeText(context, R.string.lock_needs_setup, Toast.LENGTH_LONG).show()
                    nav.navigateSingleTop(Routes.settings("gestures"))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        initialRoute?.let { nav.navigateSingleTop(it) }
        activityEvents.collect { event ->
            when (event) {
                ActivityEvent.HomePressed -> scope.launch {
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    val atHome = nav.currentDestination?.route == Routes.HOME
                    if (atHome) {
                        overlay.close()
                    } else if (currentSettings.onboardingDone) {
                        nav.popBackStack(Routes.HOME, inclusive = false)
                        overlay.close()
                    }
                }
                is ActivityEvent.OpenRoute -> nav.navigateSingleTop(event.route)
            }
        }
    }

    val start = if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING
    val enter: EnterTransition = fadeIn(tween(motion.scaled(motion.medium)))
    val exit: ExitTransition = fadeOut(tween(motion.scaled(motion.fast)))
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(vm = vm, settings = settings, overlay = overlay, navigate = { nav.navigateSingleTop(it) })
        }
        composable(
            Routes.SETTINGS_PATTERN,
            arguments = listOf(navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            SettingsScreen(
                initialSection = entry.arguments?.getString("section"),
                onBack = { nav.popBackStack() },
                navigate = { nav.navigateSingleTop(it) },
            )
        }
        composable(Routes.HIDDEN_APPS) { HiddenAppsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(onBack = { nav.popBackStack() }, openRules = { nav.navigateSingleTop(Routes.rules(it)) })
        }
        composable(Routes.FAVORITES) { FavoritesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.FOCUS) {
            FocusScreen(onBack = { nav.popBackStack() }, navigate = { nav.navigateSingleTop(it) })
        }
        composable(
            Routes.RULES_PATTERN,
            arguments = listOf(navArgument("target") { type = NavType.StringType }),
        ) { entry ->
            RulesScreen(
                target = entry.arguments?.getString("target").orEmpty(),
                onBack = { nav.popBackStack() },
                navigate = { nav.navigateSingleTop(it) },
            )
        }
        composable(Routes.INSIGHTS) { InsightsScreen(onBack = { nav.popBackStack() }, navigate = { nav.navigateSingleTop(it) }) }
        composable(Routes.NOTES) { NotesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TODOS) { TodosScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DIGEST) { DigestScreen(onBack = { nav.popBackStack() }, navigate = { nav.navigateSingleTop(it) }) }
        composable(Routes.NOTIFICATION_RULES) {
            NotificationRulesScreen(onBack = { nav.popBackStack() }, navigate = { nav.navigateSingleTop(it) })
        }
        composable(Routes.OEM_HELP) { OemHelpScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }, navigate = { nav.navigateSingleTop(it) }) }
        composable(
            Routes.PERMISSION_EXPLAINER_PATTERN,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            PermissionExplainerScreen(kind = entry.arguments?.getString("kind").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}

fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
