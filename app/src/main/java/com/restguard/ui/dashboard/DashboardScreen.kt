package com.restguard.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

    val stressColor by animateColorAsState(
        targetValue = when (state.stressLevel) {
            StressLevel.LOW -> StressLow
            StressLevel.MODERATE -> StressModerate
            StressLevel.HIGH -> StressHigh
            StressLevel.EXTREME -> StressExtreme
        },
        label = "stressColor",
    )

    if (state.isLoading) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Teal)
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Track gauge item height for continuous scroll progress
    var gaugeItemHeight by remember { mutableIntStateOf(1) }

    // Continuous collapse progress: 0 = gauge fully visible, 1 = gauge fully scrolled off
    val collapseProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val gaugeItem = info.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f // gauge not visible at all
            val scrolledOff = -gaugeItem.offset.toFloat()
            val height = gaugeItem.size.toFloat().coerceAtLeast(1f)
            (scrolledOff / height).coerceIn(0f, 1f)
        }
    }

    val bannerHeight = with(density) { 40.dp.toPx() }
    val score = state.currentStress?.score ?: 0

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // ─── Collapsing Banner Overlay ────────────────
        if (collapseProgress > 0f) {
            val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .graphicsLayer { alpha = collapseProgress }
                    .background(DarkBg) // full-width opaque bg covers corner gaps
                    .padding(horizontal = 16.dp)
                    .background(DarkBg, shape = topShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                stressColor.copy(alpha = 0.18f * collapseProgress),
                                stressColor.copy(alpha = 0.06f * collapseProgress),
                            ),
                        ),
                        shape = topShape,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(color = stressColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Stress Level: ${state.stressLevel.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$score/100",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            // ─── Gauge item (collapses into banner) ───────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { gaugeItemHeight = it.size.height },
                ) {
                    // Fade the arc as it scrolls off
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 1f - collapseProgress },
                    ) {
                        StressGauge(
                            score = score,
                            level = state.stressLevel,
                            stressColor = stressColor,
                        )
                    }
                }
            }

        // ─── Health Metrics Grid ───────────────────────
        item {
            val health = state.currentStress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    icon = Icons.Default.Favorite,
                    iconColor = Teal,
                    label = "HEART RATE",
                    value = health?.components?.let { "${72 + (it.physiological * 0.2).toInt()} bpm" } ?: "— bpm",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = Icons.Default.MonitorHeart,
                    iconColor = Teal,
                    label = "HRV",
                    value = health?.components?.let { "${(50 - it.physiological * 0.3).toInt()} ms" } ?: "— ms",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            val health2 = state.currentStress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    icon = Icons.Default.Bedtime,
                    iconColor = Teal,
                    label = "SLEEP QUALITY",
                    value = health2?.components?.let { "${100 - it.physiological}/100" } ?: "—/100",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    icon = Icons.Default.Bolt,
                    iconColor = Teal,
                    label = "RECOVERY",
                    value = health2?.components?.let { "${100 - it.physiological}/100" } ?: "—/100",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ─── Upcoming Meetings ─────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Upcoming Meetings (48h)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${state.predictions.sumOf { it.calendarPressureBreakdown.meetingCount }}+",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Teal,
                    )
                }
            }
        }

        // ─── Why This Status ───────────────────────────
        item {
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.clickable { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "WHY THIS STATUS?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Teal,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(12.dp))
                        state.currentStress?.components?.let { comp ->
                            WhyRow("Body signals", comp.physiological)
                            WhyRow("Calendar pressure", comp.calendarPressure)
                            WhyRow("Historical patterns", comp.historicalPattern)
                        }
                        if (state.currentStress?.missingSources?.isNotEmpty() == true) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Missing data: ${state.currentStress?.missingSources?.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        // ─── Suggested Actions ─────────────────────────
        if (state.recommendations.isNotEmpty()) {
            item {
                Text(
                    if (state.stressLevel == StressLevel.EXTREME)
                        "URGENT ACTIONS"
                    else
                        "SUGGESTED ACTIONS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (state.stressLevel == StressLevel.EXTREME) StressExtreme else Teal,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp),
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

        // ─── Upcoming Days ─────────────────────────────
        if (state.predictions.isNotEmpty()) {
            item {
                Text(
                    "UPCOMING DAYS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Teal,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(state.predictions) { prediction ->
                PredictionCard(prediction)
            }
        }

        // ─── Error ─────────────────────────────────────
        state.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
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
    } // Box
}

// ─── Circular Stress Gauge ──────────────────────────────────

@Composable
private fun StressGauge(score: Int, level: StressLevel, stressColor: Color) {
    val sweepAngle = (score / 100f) * 270f
    val bgArcColor = DarkCardBorder

    // 4-7-8 breathing glow: 4s inhale, 7s hold, 8s exhale (19s cycle)
    val infiniteTransition = rememberInfiniteTransition(label = "gaugePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 19000
                0.15f at 0 using LinearEasing               // start: lungs empty
                1.0f at 4000 using LinearEasing              // 4s steady inhale → full
                1.0f at 11000 using LinearEasing             // 7s hold at peak
                0.15f at 19000                               // 8s steady exhale → empty
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val canvasRadius = size.minDimension / 2f
                val arcRadius = canvasRadius * (200f / 240f) // arc fits in 200dp area
                val arcStroke = 7.dp.toPx()
                val arcInset = canvasRadius - arcRadius

                // Ambient haze — pulsating glow from center, 20% beyond the arc
                val hazeRadius = (arcRadius + arcStroke) * 1.20f
                val hazeStops = 16
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = Array(hazeStops) { i ->
                            val t = i.toFloat() / (hazeStops - 1)
                            // Smooth falloff: full at center, fading to zero at edge
                            val fade = (1f - t) * (1f - t)
                            val intensity = 0.8f * fade
                            t to stressColor.copy(alpha = intensity * pulseAlpha)
                        },
                        center = center,
                        radius = hazeRadius,
                    ),
                )

                // Arc area inset within the larger canvas
                val arcAreaSize = Size(arcRadius * 2, arcRadius * 2)
                val arcTopLeft = Offset(arcInset, arcInset)

                // Background track
                drawArc(
                    color = bgArcColor.copy(alpha = 0.35f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcAreaSize,
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round),
                )

                // Soft glow — 48 layers, wider spread, lighter near arc
                val glowSteps = 34
                val maxSpread = 34.dp.toPx()
                for (i in glowSteps downTo 1) {
                    val t = i.toFloat() / glowSteps
                    val spread = maxSpread * t * pulseAlpha
                    val alpha = 0.30f * (1f - t) * (1f - t) * (1f - t) * pulseAlpha
                    drawArc(
                        color = stressColor.copy(alpha = alpha),
                        startAngle = 135f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcAreaSize,
                        style = Stroke(width = arcStroke + spread, cap = StrokeCap.Round),
                    )
                }

                // Bright core arc
                drawArc(
                    color = stressColor,
                    startAngle = 135f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcAreaSize,
                    style = Stroke(width = arcStroke, cap = StrokeCap.Round),
                )

            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$score",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    "/100",
                    fontSize = 16.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

// ─── Health Metric Card ──────────────────────────────────────

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
    }
}

// ─── Why Row ─────────────────────────────────────────────────

@Composable
private fun WhyRow(label: String, value: Int) {
    val barColor = when {
        value >= 70 -> StressExtreme
        value >= 50 -> StressHigh
        value >= 30 -> StressModerate
        else -> StressLow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$value/100",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = barColor,
        )
    }
}

// ─── Prediction Card ─────────────────────────────────────────

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
        colors = CardDefaults.cardColors(containerColor = DarkCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mini gauge
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(40.dp)) {
                    drawArc(
                        color = DarkCardBorder,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = stressColor,
                        startAngle = 135f,
                        sweepAngle = (prediction.predictedScore / 100f) * 270f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Text(
                    "${prediction.predictedScore}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = stressColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    prediction.date.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    prediction.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

// ─── Recommendation Card ─────────────────────────────────────

@Composable
private fun RecommendationCard(
    recommendation: Recommendation,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (recommendation.isExtremeStressMode) StressExtremeBg else DarkCard,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            recommendation.event?.let { event ->
                Text(
                    when (recommendation.type) {
                        RecommendationType.SUGGEST_RESCHEDULE -> "Reschedule \"${event.title}\""
                        RecommendationType.SUGGEST_CANCEL -> "Cancel \"${event.title}\""
                        else -> event.title
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                recommendation.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            if (recommendation.stressReduction > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "~${recommendation.stressReduction} pts stress reduction",
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextSecondary)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Teal.copy(alpha = 0.15f),
                        contentColor = Teal,
                    ),
                ) {
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View")
                }
            }
        }
    }
}

// ─── Helper: access health from components ───────────────────
private val DashboardUiState.health get() = currentStress
