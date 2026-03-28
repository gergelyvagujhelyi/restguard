package com.restguard.ui.reschedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.RescheduleOption
import com.restguard.ui.theme.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleScreen(
    eventId: String,
    onBack: () -> Unit = {},
    viewModel: RescheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(eventId) {
        viewModel.loadOptions(eventId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reschedule Options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Finding optimal time slots...")
                    }
                }
            }

            state.options.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No available slots found in the next 10 days.")
                }
            }

            state.isConfirmed -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StressLow,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Meeting rescheduled!", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Attendees will be notified.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onBack) { Text("Done") }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // Event summary
                    state.eventTitle?.let { title ->
                        item {
                            Text(
                                "Rescheduling: $title",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Slots are ranked by predicted stress and schedule density.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(state.options) { option ->
                        RescheduleOptionCard(
                            option = option,
                            isSelected = state.selectedOptionId == option.id,
                            onClick = { viewModel.selectOption(option.id) },
                        )
                    }

                    // Confirm button
                    if (state.selectedOptionId != null) {
                        item {
                            // Message preview
                            state.messageDraft?.let { draft ->
                                Card(shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(
                                            "Message to Attendees",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(draft.body, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.confirmReschedule() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isProcessing,
                            ) {
                                if (state.isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.Check, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Confirm Reschedule & Notify")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RescheduleOptionCard(
    option: RescheduleOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val stressColor = when {
        option.dayStressScore >= 80 -> StressExtreme
        option.dayStressScore >= 56 -> StressHigh
        option.dayStressScore >= 31 -> StressModerate
        else -> StressLow
    }

    val timeFormat = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
            )
        } else null,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (option.rank <= 2) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "#${option.rank}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = if (option.rank <= 2) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.proposedStart.format(timeFormat),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        "Stress: ${option.dayStressScore}/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = stressColor,
                    )
                    Text(" • ", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${option.meetingCountOnDay} meetings",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    option.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
