package ru.krugzaboty.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.krugzaboty.app.ui.diary.DiaryScreen
import ru.krugzaboty.app.ui.medications.MedicationsScreen
import ru.krugzaboty.app.ui.onboarding.OnboardingScreen
import ru.krugzaboty.app.ui.profile.ProfileScreen
import ru.krugzaboty.app.ui.today.TodayScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val MEDICATIONS = "medications"
    const val DIARY = "diary"
    const val PROFILE = "profile"
    const val PAYWALL = "paywall/{trigger}"
    fun paywall(trigger: String) = "paywall/$trigger"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab(Routes.TODAY, "Сегодня", Icons.Filled.Home),
    Tab(Routes.MEDICATIONS, "Лекарства", Icons.Filled.Medication),
    Tab(Routes.DIARY, "Дневник", Icons.Filled.CalendarMonth),
    Tab(Routes.PROFILE, "Профиль", Icons.Filled.Person),
)

@Composable
fun KrugNavHost(startOnboarded: Boolean, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val showBottomBar = tabs.any { tab -> backStack?.destination?.hierarchy?.any { it.route == tab.route } }

    Scaffold(bottomBar = {
        if (showBottomBar) NavigationBar {
            tabs.forEach { tab ->
                val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigate(tab.route) { launchSingleTop = true; restoreState = true } },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                )
            }
        }
    }) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startOnboarded) Routes.TODAY else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.TODAY) {
                TodayScreen(onAddMedication = {
                    navController.navigate(Routes.MEDICATIONS) { launchSingleTop = true }
                })
            }
            composable(Routes.MEDICATIONS) { MedicationsScreen(onOpenPaywall = { navController.navigate(Routes.paywall(it)) }) }
            composable(Routes.DIARY) { DiaryScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
            composable(Routes.PAYWALL) { /* PaywallScreen — E11-02 (docs/02 §12) */ Text("Paywall — E11-02") }
        }
    }
}
