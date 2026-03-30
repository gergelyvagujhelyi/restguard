package com.restguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.restguard.notification.StressMonitorService
import com.restguard.notification.StressNotificationService
import com.restguard.domain.model.CheckInTrigger
import com.restguard.ui.activity.ActivityScreen
import com.restguard.ui.checkin.CheckInScreen
import com.restguard.ui.contact.ContactPickerScreen
import com.restguard.ui.dashboard.DashboardScreen
import com.restguard.ui.history.HistoryScreen
import com.restguard.ui.onboarding.OnboardingScreen
import com.restguard.ui.recommendation.RecommendationDetailScreen
import com.restguard.ui.reschedule.RescheduleScreen
import com.restguard.ui.settings.AboutScreen
import com.restguard.ui.settings.SettingsScreen
import com.restguard.ui.theme.RestGuardTheme
import com.restguard.worker.MeetingCompletionWorker
import com.restguard.worker.StressMonitorWorker
import com.restguard.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleWorker()

        setContent {
            val isOnboardingComplete by userPreferences.isOnboardingComplete
                .collectAsState(initial = true) // default true to avoid flash

            RestGuardTheme {
                RestGuardNavHost(
                    startFromSuggestions = intent?.action == StressNotificationService.ACTION_VIEW_SUGGESTIONS,
                    startFromCheckIn = intent?.action == StressNotificationService.ACTION_CHECK_IN,
                    isOnboardingComplete = isOnboardingComplete,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep-links from notification actions
        setIntent(intent)
    }

    private fun scheduleWorker() {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            StressMonitorWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            StressMonitorWorker.buildPeriodicRequest(),
        )
        workManager.enqueueUniquePeriodicWork(
            MeetingCompletionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            MeetingCompletionWorker.buildPeriodicRequest(),
        )
    }
}

@Composable
fun RestGuardNavHost(
    startFromSuggestions: Boolean = false,
    startFromCheckIn: Boolean = false,
    isOnboardingComplete: Boolean = true,
) {
    val startDestination = if (isOnboardingComplete) "dashboard" else "onboarding"
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Handle deep-link to check-in
    LaunchedEffect(startFromCheckIn) {
        if (startFromCheckIn) {
            navController.navigate("checkin/NOTIFICATION_TAP")
        }
    }

    // Bottom nav items
    val bottomTabs = listOf(
        Triple("dashboard", "Home", Icons.Default.Home),
        Triple("history", "Insights", Icons.Default.Insights),
        Triple("settings", "Settings", Icons.Default.Settings),
    )

    val showBottomBar = currentRoute in bottomTabs.map { it.first }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo("dashboard") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ─── Main tabs ──────────────────────────────

            composable("dashboard") {
                DashboardScreen(
                    onRecommendationClick = { rec ->
                        navController.navigate("recommendation/${rec.id}")
                    },
                )
            }

            composable("history") {
                HistoryScreen(onBack = { navController.popBackStack() })
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onAbout = { navController.navigate("about") },
                )
            }

            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            // ─── Onboarding ─────────────────────────────

            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate("dashboard") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                )
            }

            // ─── Recommendation detail ──────────────────

            composable(
                route = "recommendation/{recId}",
                arguments = listOf(navArgument("recId") { type = NavType.StringType }),
            ) { entry ->
                val recId = entry.arguments?.getString("recId") ?: return@composable
                RecommendationDetailScreen(
                    recommendationId = recId,
                    onBack = { navController.popBackStack() },
                    onReschedule = { eventId ->
                        navController.navigate("reschedule/$eventId")
                    },
                    onContactPicker = { eventId ->
                        navController.navigate("contactPicker/$eventId")
                    },
                )
            }

            // ─── Reschedule ─────────────────────────────

            composable(
                route = "reschedule/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val eventId = entry.arguments?.getString("eventId") ?: return@composable
                RescheduleScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                )
            }

            // ─── Contact picker ─────────────────────────

            composable(
                route = "contactPicker/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val eventId = entry.arguments?.getString("eventId") ?: return@composable
                ContactPickerScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onContactSelected = { contact, method ->
                        // Navigate back with result
                        navController.popBackStack()
                    },
                )
            }

            // ─── Activities ─────────────────────────────

            composable(
                route = "activities/{stressScore}",
                arguments = listOf(navArgument("stressScore") { type = NavType.IntType }),
            ) { entry ->
                val score = entry.arguments?.getInt("stressScore") ?: 50
                ActivityScreen(
                    stressScore = score,
                    onBack = { navController.popBackStack() },
                )
            }

            // ─── Check-In ───────────────────────────────

            composable(
                route = "checkin/{trigger}",
                arguments = listOf(navArgument("trigger") {
                    type = NavType.StringType
                    defaultValue = "USER_INITIATED"
                }),
            ) { entry ->
                val triggerName = entry.arguments?.getString("trigger") ?: "USER_INITIATED"
                val trigger = try {
                    CheckInTrigger.valueOf(triggerName)
                } catch (_: Exception) {
                    CheckInTrigger.USER_INITIATED
                }
                CheckInScreen(
                    trigger = trigger,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
