package com.restguard.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.*
import com.restguard.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onRecommendationClick: (Recommendation) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RestGuard") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                // ─── Stress Card ────────────────────────────
                item {
                    StressCard(
                        stress = state.currentStress,
                        level = state.stressLevel,
                    )
                }

                // ─── Predictions ────────────────────────────
                if (state.predictions.isNotEmpty()) {
                    item {
                        Text(
                            "Upcoming Days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(state.predictions) { prediction ->
                        PredictionCard(prediction)
                    }
                }

                // ─── Recommendations ────────────────────────
                if (state.recommendations.isNotEmpty()) {
                    item {
                        Text(
                            if (state.stressLevel == StressLevel.EXTREME)
                                "Urgent: Consider These Actions"
                            else
                                "Suggestions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.stressLevel == StressLevel.EXTREME)
                                StressExtreme else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    items(state.recommendations) { rec ->
                        RecommendationCard(
                            recommendation = rec,
                            onClick = { onRecommendationClick(rec) },
                            onDismiss = { viewModel.dismissRecommendation(rec.id) },
                        )
                    }
                }

                // ─── Error ──────────────────────────────────
                state.error?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StressCard(stress: StressSample?, level: StressLevel) {
    val bgColor by animateColorAsState(
        targetValue = when (level) {
            StressLevel.LOW -> StressLowBg
            StressLevel.MODERATE -> StressModerateBg
            StressLevel.HIGH -> StressHighBg
            StressLevel.EXTREME -> StressExtremeBg
        },
        label = "stressBg",
    )

    val accentColor = when (level) {
        StressLevel.LOW -> StressLow
        StressLevel.MODERATE -> StressModerate
        StressLevel.HIGH -> StressHigh
        StressLevel.EXTREME -> StressExtreme
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Score circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${stress?.score ?: "—"}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        level.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (stress != null) {
                        Text(
                            "Confidence: ${(stress.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stress.missingSources.isNotEmpty()) {
                            Text(
                                "Missing: ${stress.missingSources.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Component breakdown
            stress?.components?.let { comp ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ComponentChip("Body", comp.physiological, accentColor)
                    ComponentChip("Calendar", comp.calendarPressure, accentColor)
                    ComponentChip("Pattern", comp.historicalPattern, accentColor)
                }
            }
        }
    }
}

@Composable
private fun ComponentChip(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PredictionCard(prediction: StressPrediction) {
    val stressColor = when {
        prediction.predictedScore >= 80 -> StressExtreme
        prediction.predictedScore >= 56 -> StressHigh
        prediction.predictedScore >= 31 -> StressModerate
        else -> StressLow
    }

    Card(
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(stressColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${prediction.predictedScore}",
                    fontWeight = FontWeight.Bold,
                    color = stressColor,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    prediction.date.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    prediction.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: Recommendation,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val icon = when (recommendation.type) {
        RecommendationType.NO_ACTION -> Icons.Default.CheckCircle
        RecommendationType.STRESS_RELIEF_ACTIVITY -> Icons.Default.FavoriteBorder
        RecommendationType.SUGGEST_RESCHEDULE -> Icons.Default.DateRange
        RecommendationType.SUGGEST_CANCEL -> Icons.Default.Cancel
        RecommendationType.URGENT_SAME_DAY_INTERVENTION -> Icons.Default.Warning
    }

    val containerColor = if (recommendation.isExtremeStressMode) {
        StressExtremeBg
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (recommendation.isExtremeStressMode) StressExtreme
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                recommendation.event?.let { event ->
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    recommendation.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recommendation.stressReduction > 0) {
                    Text(
                        "Est. stress reduction: ~${recommendation.stressReduction} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = StressLow,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
