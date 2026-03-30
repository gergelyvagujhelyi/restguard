package com.restguard.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.ui.theme.*
import java.io.File

data class SettingsUiState(
    val monitoringPaused: Boolean = false,
    val llmEnabled: Boolean = true,
    val workStartHour: Int = 9,
    val workEndHour: Int = 17,
    val dataRetentionDays: Int = 90,
    val healthPermission: Boolean = false,
    val calendarPermission: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val exportJson: String? = null, // non-null = ready to share
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val dataDeleted: Boolean = false,
    val availableCalendars: List<com.restguard.domain.model.CalendarInfo> = emptyList(),
    val selectedCalendarIds: Set<String>? = null, // null = all, empty = none
    val googleAccounts: Set<String> = emptySet(),
    val googleSignInError: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onGoogleSignInResult(result.data)
    }

    // Handle export data sharing
    LaunchedEffect(state.exportJson) {
        state.exportJson?.let { json ->
            val file = File(context.cacheDir, "restguard_export.json")
            file.writeText(json)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export RestGuard Data"))
            viewModel.clearExportData()
        }
    }

    // Delete confirmation dialog
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete All Data?") },
            text = {
                Text(
                    "This will permanently delete all stress samples, predictions, " +
                        "meeting insights, check-ins, feedback, and personalization data. " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteAllData() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Monitoring ─────────────────────────────
            SectionHeader("Monitoring")

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    SettingsRow(
                        icon = Icons.Default.Pause,
                        title = "Pause Monitoring",
                        subtitle = "Stop stress tracking and notifications",
                    ) {
                        Switch(
                            checked = state.monitoringPaused,
                            onCheckedChange = { viewModel.setMonitoringPaused(it) },
                        )
                    }
                }
            }

            // ─── AI ─────────────────────────────────────
            SectionHeader("AI & Classification")

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    SettingsRow(
                        icon = Icons.Default.SmartToy,
                        title = "AI Importance Assessment",
                        subtitle = "Use AI to classify meeting importance. When off, " +
                            "only rule-based classification is used.",
                    ) {
                        Switch(
                            checked = state.llmEnabled,
                            onCheckedChange = { viewModel.setLlmEnabled(it) },
                        )
                    }
                    if (state.llmEnabled) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "When enabled, meeting titles and metadata (not descriptions or " +
                                "attendee names) are sent to our AI service for classification. " +
                                "No health data is ever sent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ─── Schedule ───────────────────────────────
            SectionHeader("Schedule Preferences")

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Working Hours",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.workStartHour}:00")
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = state.workStartHour.toFloat(),
                            onValueChange = { viewModel.setWorkStart(it.toInt()) },
                            valueRange = 6f..12f,
                            steps = 5,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.workEndHour}:00")
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = state.workEndHour.toFloat(),
                            onValueChange = { viewModel.setWorkEnd(it.toInt()) },
                            valueRange = 15f..22f,
                            steps = 6,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ─── Connected Accounts ─────────────────────
            SectionHeader("Connected Accounts")

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Connect calendar providers for direct API access. " +
                            "Useful on devices where calendars don't sync to the system provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    state.googleAccounts.forEach { email ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.AccountCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Google Calendar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.signOutGoogle(email) }) {
                                Text("Sign Out")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { googleSignInLauncher.launch(viewModel.getGoogleSignInIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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

                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Microsoft — Coming Soon")
                    }
                }
            }

            // ─── Calendar Selection ─────────────────────
            if (state.availableCalendars.isNotEmpty()) {
                SectionHeader("Calendars")

                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Select which calendars to include in stress analysis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))

                        // "All calendars" toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = state.selectedCalendarIds == null,
                                onCheckedChange = { checked ->
                                    if (checked) viewModel.setSelectedCalendars(null)
                                    else viewModel.setSelectedCalendars(emptySet())
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "All calendars",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        val selected = state.selectedCalendarIds
                        state.availableCalendars.forEach { cal ->
                            val isSelected = selected == null || selected.contains(cal.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleCalendar(cal.id, checked)
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    modifier = Modifier.size(12.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    color = androidx.compose.ui.graphics.Color(cal.color or 0xFF000000.toInt()),
                                ) {}
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        cal.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        cal.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Privacy ────────────────────────────────
            SectionHeader("Privacy & Data")

            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Data Retention: ${state.dataRetentionDays} days",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Stress samples and recommendations older than this are automatically deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = state.dataRetentionDays.toFloat(),
                        onValueChange = { viewModel.setRetentionDays(it.toInt()) },
                        valueRange = 7f..365f,
                    )

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    OutlinedButton(
                        onClick = { viewModel.exportData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isExporting,
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isExporting) "Exporting..." else "Export My Data")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.showDeleteConfirmation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isDeleting && !state.dataDeleted,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                state.dataDeleted -> "Data Deleted"
                                state.isDeleting -> "Deleting..."
                                else -> "Delete All My Data"
                            }
                        )
                    }
                }
            }

            // ─── About ──────────────────────────────────
            Card(
                onClick = onAbout,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("About RestGuard", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Version ${com.restguard.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ─── Disclaimer ─────────────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Wellness Disclaimer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "RestGuard is a wellness assistant designed to help you manage your " +
                            "schedule and energy levels. It is not a medical device, does not " +
                            "provide medical diagnoses, and should not replace professional " +
                            "medical advice. If you are experiencing persistent stress, anxiety, " +
                            "or health concerns, please consult a healthcare professional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}
