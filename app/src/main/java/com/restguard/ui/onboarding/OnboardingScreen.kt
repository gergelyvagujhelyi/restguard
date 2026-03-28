package com.restguard.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.Manifest
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.ui.common.rememberHealthConnectPermissionLauncher
import com.restguard.ui.common.rememberMultiplePermissionLauncher
import com.restguard.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val permissionType: PermissionType?,
)

enum class PermissionType { HEALTH, CALENDAR, CONTACTS, NOTIFICATIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // ─── Permission launchers ───────────────────────────────
    val requestHealthPermissions = rememberHealthConnectPermissionLauncher { granted ->
        viewModel.onPermissionResult(PermissionType.HEALTH, granted.isNotEmpty())
    }

    val requestStandardPermissions = rememberMultiplePermissionLauncher { results ->
        val calendarGranted = results[Manifest.permission.READ_CALENDAR] == true &&
            results[Manifest.permission.WRITE_CALENDAR] == true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (calendarGranted) viewModel.onPermissionResult(PermissionType.CALENDAR, true)
        if (notificationGranted) viewModel.onPermissionResult(PermissionType.NOTIFICATIONS, true)
    }

    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.FavoriteBorder,
            title = "Protect Your Energy",
            description = "RestGuard monitors your stress levels and helps you manage your " +
                "schedule before burnout hits. It combines health signals with calendar " +
                "analysis to keep you at your best.",
            permissionType = null,
        ),
        OnboardingPage(
            icon = Icons.Default.MonitorHeart,
            title = "Health Data Access",
            description = "We use sleep, heart rate, and HRV data from Health Connect to " +
                "estimate your physiological stress. This data never leaves your device.",
            permissionType = PermissionType.HEALTH,
        ),
        OnboardingPage(
            icon = Icons.Default.CalendarMonth,
            title = "Calendar Access",
            description = "We analyze your meeting schedule to predict overload and find " +
                "better time slots. Calendar data is processed locally.",
            permissionType = PermissionType.CALENDAR,
        ),
        OnboardingPage(
            icon = Icons.Default.Notifications,
            title = "Stay Informed",
            description = "A persistent notification shows your current stress level. " +
                "We'll alert you when we have suggestions to reduce your load.",
            permissionType = PermissionType.NOTIFICATIONS,
        ),
        OnboardingPage(
            icon = Icons.Default.Shield,
            title = "Your Data, Your Control",
            description = "All health and calendar data is processed on-device. Only " +
                "meeting metadata (not descriptions) may be sent to our AI for importance " +
                "classification — you can disable this in settings. You can pause " +
                "monitoring, export, or delete your data at any time.",
            permissionType = null,
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    onRequestPermission = { type ->
                        when (type) {
                            PermissionType.HEALTH -> requestHealthPermissions()
                            PermissionType.CALENDAR -> requestStandardPermissions(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR,
                                )
                            )
                            PermissionType.NOTIFICATIONS -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestStandardPermissions(
                                        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                                    )
                                } else {
                                    viewModel.onPermissionResult(PermissionType.NOTIFICATIONS, true)
                                }
                            }
                            PermissionType.CONTACTS -> requestStandardPermissions(
                                arrayOf(Manifest.permission.READ_CONTACTS)
                            )
                        }
                    },
                    isPermissionGranted = when (pages[page].permissionType) {
                        PermissionType.HEALTH -> state.healthGranted
                        PermissionType.CALENDAR -> state.calendarGranted
                        PermissionType.NOTIFICATIONS -> state.notificationGranted
                        PermissionType.CONTACTS -> false
                        null -> false
                    },
                )
            }

            // Page indicator + navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { i ->
                        val isActive = pagerState.currentPage == i
                        Surface(
                            modifier = Modifier.size(if (isActive) 10.dp else 8.dp),
                            shape = RoundedCornerShape(50),
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        ) {}
                    }
                }

                // Navigation button
                val isLast = pagerState.currentPage == pages.size - 1
                Button(
                    onClick = {
                        if (isLast) {
                            viewModel.completeOnboarding()
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                ) {
                    Text(if (isLast) "Get Started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    onRequestPermission: (PermissionType) -> Unit,
    isPermissionGranted: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            page.icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        page.permissionType?.let { type ->
            Spacer(Modifier.height(24.dp))
            if (isPermissionGranted) {
                AssistChip(
                    onClick = {},
                    label = { Text("Granted") },
                    leadingIcon = {
                        Icon(Icons.Default.CheckCircle, null, tint = StressLow)
                    },
                )
            } else {
                OutlinedButton(onClick = { onRequestPermission(type) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
