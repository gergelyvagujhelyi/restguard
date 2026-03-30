package com.restguard.ui.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.CalendarSource
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
    val healthConnectAvailable = requestHealthPermissions != null

    val requestStandardPermissions = rememberMultiplePermissionLauncher { results ->
        val calendarGranted = results[Manifest.permission.READ_CALENDAR] == true &&
            results[Manifest.permission.WRITE_CALENDAR] == true
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        if (calendarGranted) viewModel.onPermissionResult(PermissionType.CALENDAR, true)
        if (notificationGranted) viewModel.onPermissionResult(PermissionType.NOTIFICATIONS, true)
    }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onGoogleSignInResult(result.data)
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
                if (pages[page].permissionType == PermissionType.CALENDAR) {
                    CalendarOnboardingPage(
                        page = pages[page],
                        state = state,
                        onRequestPermission = {
                            requestStandardPermissions(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR,
                                )
                            )
                        },
                        onGoogleSignIn = {
                            googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                        },
                    )
                } else {
                    OnboardingPageContent(
                        page = pages[page],
                        onRequestPermission = { type ->
                            when (type) {
                                PermissionType.HEALTH -> requestHealthPermissions?.invoke()
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
                                else -> {}
                            }
                        },
                        isPermissionGranted = when (pages[page].permissionType) {
                            PermissionType.HEALTH -> state.healthGranted
                            PermissionType.NOTIFICATIONS -> state.notificationGranted
                            PermissionType.CONTACTS -> false
                            else -> false
                        },
                        isUnavailable = pages[page].permissionType == PermissionType.HEALTH && !healthConnectAvailable,
                    )
                }
            }

            // Page indicator + navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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

// ─── Calendar-specific onboarding page ─────────────────────

@Composable
private fun CalendarOnboardingPage(
    page: OnboardingPage,
    state: OnboardingUiState,
    onRequestPermission: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            page.icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        // System calendar permission
        if (state.calendarGranted) {
            AssistChip(
                onClick = {},
                label = { Text("Calendar permission granted") },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = StressLow) },
            )
        } else {
            OutlinedButton(onClick = onRequestPermission) {
                Text("Grant Calendar Permission")
            }
        }

        // Show found system calendars
        val systemCalendars = state.systemCalendars.filter { it.source == CalendarSource.SYSTEM }
        if (systemCalendars.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Found ${systemCalendars.size} calendar${if (systemCalendars.size != 1) "s" else ""} on device:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            systemCalendars.forEach { cal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(10.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = androidx.compose.ui.graphics.Color(cal.color or 0xFF000000.toInt()),
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${cal.displayName} (${cal.accountName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (state.calendarGranted) {
            Spacer(Modifier.height(12.dp))
            Text(
                "No calendars found on device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Google Calendar direct sign-in
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            "Sign in for direct calendar access",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Required on Samsung devices or if your calendars don't appear above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        // Show connected Google accounts and their calendars
        state.googleAccounts.forEach { email ->
            AssistChip(
                onClick = {},
                label = { Text(email) },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = StressLow) },
            )

            val googleCalendars = state.systemCalendars.filter {
                it.source == CalendarSource.GOOGLE_API && it.accountName == email
            }
            if (googleCalendars.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                googleCalendars.forEach { cal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = androidx.compose.ui.graphics.Color(cal.color or 0xFF000000.toInt()),
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            cal.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = onGoogleSignIn) {
            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.googleAccounts.isEmpty()) "Sign in with Google" else "Add Google Account")
        }

        state.googleSignInError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Microsoft placeholder
        OutlinedButton(
            onClick = {},
            enabled = false,
        ) {
            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Microsoft — Coming Soon")
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Generic onboarding page ───────────────────────────────

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    onRequestPermission: (PermissionType) -> Unit,
    isPermissionGranted: Boolean,
    isUnavailable: Boolean = false,
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
            when {
                isUnavailable -> {
                    AssistChip(
                        onClick = {},
                        label = { Text("Health Connect not installed") },
                        leadingIcon = {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )
                }
                isPermissionGranted -> {
                    AssistChip(
                        onClick = {},
                        label = { Text("Granted") },
                        leadingIcon = {
                            Icon(Icons.Default.CheckCircle, null, tint = StressLow)
                        },
                    )
                }
                else -> {
                    OutlinedButton(onClick = { onRequestPermission(type) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
