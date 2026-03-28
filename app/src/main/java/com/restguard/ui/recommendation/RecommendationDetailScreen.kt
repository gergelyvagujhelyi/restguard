package com.restguard.ui.recommendation

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.*
import com.restguard.ui.common.IntentActions
import com.restguard.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationDetailScreen(
    recommendationId: String,
    onBack: () -> Unit = {},
    onReschedule: (String) -> Unit = {},
    onContactPicker: (String) -> Unit = {},
    viewModel: RecommendationDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(recommendationId) {
        viewModel.load(recommendationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggestion Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        val recommendation = state.recommendation
        if (recommendation == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Type Badge ─────────────────────────────
            val typeLabel = when (recommendation.type) {
                RecommendationType.URGENT_SAME_DAY_INTERVENTION -> "URGENT — Same-Day Action"
                RecommendationType.SUGGEST_CANCEL -> "Suggested Cancellation"
                RecommendationType.SUGGEST_RESCHEDULE -> "Suggested Reschedule"
                RecommendationType.STRESS_RELIEF_ACTIVITY -> "Stress Relief Activity"
                RecommendationType.NO_ACTION -> "No Action Needed"
            }

            val badgeColor = if (recommendation.isExtremeStressMode) StressExtreme
            else MaterialTheme.colorScheme.primary

            AssistChip(
                onClick = {},
                label = { Text(typeLabel) },
                leadingIcon = {
                    Icon(
                        if (recommendation.isExtremeStressMode) Icons.Default.Warning
                        else Icons.Default.Info,
                        contentDescription = null,
                        tint = badgeColor,
                    )
                },
            )

            // ─── Event Info ─────────────────────────────
            recommendation.event?.let { event ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${event.startTime.toLocalTime()} – ${event.endTime.toLocalTime()}, " +
                                    "${event.startTime.toLocalDate()}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (event.attendees.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row {
                                Icon(Icons.Default.People, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${event.attendees.size} attendees",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        event.description?.let { desc ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ─── Why This Suggestion ────────────────────
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Why this suggestion",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        recommendation.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (recommendation.stressReduction > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Estimated stress reduction: ~${recommendation.stressReduction} points",
                            style = MaterialTheme.typography.labelMedium,
                            color = StressLow,
                        )
                    }
                }
            }

            // ─── Message Preview ────────────────────────
            state.messageDraft?.let { draft ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Message Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        draft.subject?.let { subj ->
                            Spacer(Modifier.height(8.dp))
                            Text("Subject: $subj", fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(draft.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Source: ${draft.source} • You can edit before sending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ─── Actions ────────────────────────────────
            when (recommendation.type) {
                RecommendationType.SUGGEST_RESCHEDULE,
                RecommendationType.URGENT_SAME_DAY_INTERVENTION -> {
                    recommendation.eventId?.let { eventId ->
                        Button(
                            onClick = { onReschedule(eventId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.DateRange, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Find New Time")
                        }
                    }
                }

                RecommendationType.SUGGEST_CANCEL -> {
                    val event = recommendation.event
                    val hasAttendees = event != null && event.attendees.isNotEmpty()

                    if (hasAttendees) {
                        Button(
                            onClick = {
                                viewModel.confirmCancel()
                                // Open email intent so user reviews before sending
                                val draft = state.messageDraft
                                if (draft != null && event != null) {
                                    IntentActions.composeEmail(
                                        context = context,
                                        to = event.attendees.map { it.email },
                                        subject = draft.subject ?: "Meeting cancelled: ${event.title}",
                                        body = draft.body,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Default.Cancel, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel Meeting & Notify Attendees")
                        }
                    } else {
                        // No attendees — need contact picker
                        Button(
                            onClick = { onContactPicker(recommendation.eventId ?: "") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PersonAdd, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Contact to Notify")
                        }
                    }
                }

                RecommendationType.STRESS_RELIEF_ACTIVITY -> {
                    Button(
                        onClick = { viewModel.acceptActivity() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StressLow,
                        ),
                    ) {
                        Icon(Icons.Default.FavoriteBorder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Activity")
                    }
                }

                else -> {}
            }

            // Dismiss
            OutlinedButton(
                onClick = {
                    viewModel.dismiss()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Dismiss Suggestion")
            }

            // Disclaimer
            Text(
                "RestGuard is a wellness assistant, not a medical tool. " +
                    "All actions require your confirmation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
