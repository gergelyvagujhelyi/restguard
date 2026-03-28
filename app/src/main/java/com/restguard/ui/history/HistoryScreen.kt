package com.restguard.ui.history

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restguard.domain.model.StressSample
import com.restguard.domain.service.MeetingTypeInsight
import com.restguard.ui.theme.*

data class HistoryUiState(
    val isLoading: Boolean = true,
    val recentSamples: List<StressSample> = emptyList(),
    val meetingInsights: List<MeetingTypeInsight> = emptyList(),
    val averageScore: Int = 0,
    val trend: String = "stable", // "improving", "worsening", "stable"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History & Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                // ─── Summary Card ────────────────────────
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "Average Stress: ${state.averageScore}/100",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val trendIcon = when (state.trend) {
                                "improving" -> Icons.Default.TrendingDown
                                "worsening" -> Icons.Default.TrendingUp
                                else -> Icons.Default.TrendingFlat
                            }
                            val trendColor = when (state.trend) {
                                "improving" -> StressLow
                                "worsening" -> StressHigh
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(trendIcon, null, tint = trendColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Trend: ${state.trend}",
                                    color = trendColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // ─── Stress Chart ────────────────────────
                if (state.recentSamples.size >= 2) {
                    item {
                        Text(
                            "Stress Over Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        StressChart(
                            samples = state.recentSamples.takeLast(24),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        )
                    }
                }

                // ─── Meeting Insights ────────────────────
                if (state.meetingInsights.isNotEmpty()) {
                    item {
                        Text(
                            "Meeting Stress Patterns",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "How different meeting types affect your stress level.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(state.meetingInsights) { insight ->
                        MeetingInsightCard(insight)
                    }
                }

                if (state.recentSamples.isEmpty() && state.meetingInsights.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BarChart,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Not enough data yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "Insights will appear after a few days of monitoring.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StressChart(samples: List<StressSample>, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Canvas(modifier = modifier.padding(16.dp)) {
            if (samples.size < 2) return@Canvas

            val maxScore = 100f
            val stepX = size.width / (samples.size - 1).toFloat()

            val path = Path()
            samples.forEachIndexed { i, sample ->
                val x = i * stepX
                val y = size.height * (1f - sample.score / maxScore)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Threshold lines
            val thresholds = listOf(30 to StressLow, 55 to StressModerate, 80 to StressHigh)
            thresholds.forEach { (threshold, color) ->
                val y = size.height * (1f - threshold / maxScore)
                drawLine(
                    color = color.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            // Stress line
            drawPath(
                path = path,
                color = Color(0xFF42A5F5),
                style = Stroke(width = 3f),
            )

            // Data points
            samples.forEachIndexed { i, sample ->
                val x = i * stepX
                val y = size.height * (1f - sample.score / maxScore)
                val pointColor = when {
                    sample.score >= 80 -> StressExtreme
                    sample.score >= 56 -> StressHigh
                    sample.score >= 31 -> StressModerate
                    else -> StressLow
                }
                drawCircle(color = pointColor, radius = 4f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun MeetingInsightCard(insight: MeetingTypeInsight) {
    val color = when {
        insight.isStressful -> StressHigh
        insight.isRelaxing -> StressLow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (insight.isStressful) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                null,
                tint = color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    insight.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Avg stress change: ${if (insight.averageStressChange > 0) "+" else ""}${"%.1f".format(insight.averageStressChange)} pts " +
                        "(${insight.sampleCount} samples)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
