package com.restguard.ui.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.CheckInTrigger
import com.restguard.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    trigger: CheckInTrigger = CheckInTrigger.USER_INITIATED,
    onBack: () -> Unit = {},
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(trigger) {
        viewModel.setTrigger(trigger)
    }

    // Auto-navigate back after save
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How Are You Feeling?") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ─── Energy Level ────────────────────────────
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Energy Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        energyLabel(state.energyLevel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        (1..5).forEach { level ->
                            val selected = state.energyLevel == level
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setEnergyLevel(level) },
                                label = { Text("$level") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = energyColor(level),
                                ),
                            )
                        }
                    }
                }
            }

            // ─── Mood Level ──────────────────────────────
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Stress / Mood",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        moodLabel(state.moodLevel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        (1..5).forEach { level ->
                            val selected = state.moodLevel == level
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setMoodLevel(level) },
                                label = { Text(moodEmoji(level)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = moodColor(level),
                                ),
                            )
                        }
                    }
                }
            }

            // ─── Optional Note ───────────────────────────
            OutlinedTextField(
                value = state.note,
                onValueChange = { viewModel.setNote(it) },
                label = { Text("Optional note") },
                placeholder = { Text("What's on your mind?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            // ─── Trigger Info ────────────────────────────
            if (state.trigger != CheckInTrigger.USER_INITIATED) {
                Text(
                    triggerLabel(state.trigger),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))

            // ─── Submit ──────────────────────────────────
            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Submit Check-In")
            }

            // ─── Footer ─────────────────────────────────
            Text(
                "Check-in #${state.recentCount + 1} — your responses help calibrate stress scoring",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun energyLabel(level: Int) = when (level) {
    1 -> "Exhausted"
    2 -> "Tired"
    3 -> "Okay"
    4 -> "Good"
    5 -> "Energized"
    else -> ""
}

private fun moodLabel(level: Int) = when (level) {
    1 -> "Very stressed"
    2 -> "Somewhat stressed"
    3 -> "Neutral"
    4 -> "Fairly calm"
    5 -> "Calm & happy"
    else -> ""
}

private fun moodEmoji(level: Int) = when (level) {
    1 -> "1"
    2 -> "2"
    3 -> "3"
    4 -> "4"
    5 -> "5"
    else -> ""
}

private fun triggerLabel(trigger: com.restguard.domain.model.CheckInTrigger) = when (trigger) {
    com.restguard.domain.model.CheckInTrigger.SCHEDULED -> "Periodic check-in"
    com.restguard.domain.model.CheckInTrigger.POST_MEETING -> "Post-meeting check-in"
    com.restguard.domain.model.CheckInTrigger.NOTIFICATION_TAP -> "From notification"
    com.restguard.domain.model.CheckInTrigger.USER_INITIATED -> ""
}

@Composable
private fun energyColor(level: Int) = when (level) {
    1 -> StressExtreme
    2 -> StressHigh
    3 -> StressModerate
    4 -> StressLow
    5 -> StressLow
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun moodColor(level: Int) = when (level) {
    1 -> StressExtreme
    2 -> StressHigh
    3 -> StressModerate
    4 -> StressLow
    5 -> StressLow
    else -> MaterialTheme.colorScheme.primary
}
