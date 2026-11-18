package com.gymencasa.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.gymencasa.R
import com.gymencasa.ui.AppViewModel
import com.gymencasa.ui.Gate
import com.gymencasa.ui.auth.AuthScreen
import com.gymencasa.ui.equipment.EquipmentEditScreen
import com.gymencasa.ui.equipment.OnboardingScreen
import com.gymencasa.ui.history.HistoryScreen
import com.gymencasa.ui.home.HomeScreen
import com.gymencasa.ui.library.ExerciseDetailScreen
import com.gymencasa.ui.library.LibraryScreen
import com.gymencasa.ui.plan.PlanScreen
import com.gymencasa.ui.routines.RoutinesScreen
import com.gymencasa.ui.select.ManualSelectScreen
import com.gymencasa.ui.session.SessionScreen
import com.gymencasa.ui.settings.SettingsScreen
import com.gymencasa.ui.summary.SummaryScreen
import com.gymencasa.ui.update.UpdateDialog

@Composable
fun GymNavRoot(appViewModel: AppViewModel) {
    val gate by appViewModel.gate.collectAsStateWithLifecycle()
    val update by appViewModel.update.collectAsStateWithLifecycle()
    when (gate) {
        Gate.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Gate.AUTH -> AuthScreen()
        Gate.ONBOARDING -> OnboardingScreen()
        Gate.APP -> {
            MainNav()
            UpdateDialog(initial = update, onDismissInitial = appViewModel::dismissUpdate)
        }
    }
}

private data class Tab(val route: Route, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, R.string.tab_today, Icons.Filled.Bolt),
    Tab(Route.Library, R.string.tab_library, Icons.Filled.FitnessCenter),
    Tab(Route.History, R.string.tab_history, Icons.Filled.History),
    Tab(Route.Settings, R.string.tab_settings, Icons.Filled.Settings),
)

/** Navigation actions handed to screens. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun go(route: Route) { backStack.add(route) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun replaceTop(route: Route) { backStack.removeAt(backStack.lastIndex); backStack.add(route) }
    fun resetTo(vararg routes: Route) { backStack.clear(); backStack.addAll(routes) }
}

@Composable
private fun MainNav() {
    val backStack = rememberNavBackStack(Route.Home)
    val nav = Navigator(backStack)
    val current = backStack.lastOrNull()
    val showBar = current in topLevelRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { if (current != tab.route) nav.resetTo(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline),
            onBack = { nav.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Route.Home> { HomeScreen(nav) }
                entry<Route.Library> { LibraryScreen(nav, pickerMode = false) }
                entry<Route.LibraryPicker> { LibraryScreen(nav, pickerMode = true) }
                entry<Route.History> { HistoryScreen(nav) }
                entry<Route.Settings> { SettingsScreen(nav) }
                entry<Route.ManualSelect> { ManualSelectScreen(nav) }
                entry<Route.ExerciseDetail> { key -> ExerciseDetailScreen(key.slug, nav) }
                entry<Route.Plan> { PlanScreen(nav) }
                entry<Route.Session> { SessionScreen(nav) }
                entry<Route.Summary> { key -> SummaryScreen(key.workoutId, nav) }
                entry<Route.Routines> { RoutinesScreen(nav) }
                entry<Route.EquipmentEdit> { EquipmentEditScreen(nav) }
            },
        )
    }
}
