package com.restguard.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restguard.domain.model.ActivitySuggestion
import com.restguard.domain.model.ActivityType
import com.restguard.domain.service.ActivitySuggester
import com.restguard.ui.theme.*

/**
 * Shows stress-relief activities tailored to current stress level and available time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    stressScore: Int,
    onBack: () -> Unit = {},
    onStartActivity: (ActivitySuggestion) -> Unit = {},
) {
    val suggester = remember { ActivitySuggester() }
    val activities = remember(stressScore) { suggester.suggestAll(stressScore) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stress Relief Activities") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            stressScore >= 80 -> StressExtremeBg
                            stressScore >= 56 -> StressHighBg
                            stressScore >= 31 -> StressModerateBg
                            else -> StressLowBg
                        },
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "Current stress: $stressScore/100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Here are activities that can help you recover. " +
                                "Even a few minutes of intentional rest makes a difference.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(activities) { activity ->
                ActivityCard(
                    activity = activity,
                    onStart = { onStartActivity(activity) },
                )
            }

            if (activities.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No activities to suggest right now. You're doing great!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: ActivitySuggestion,
    onStart: () -> Unit,
) {
    val icon = when (activity.type) {
        ActivityType.BREATHING_EXERCISE -> Icons.Default.Air
        ActivityType.SHORT_WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        ActivityType.HYDRATION_REMINDER -> Icons.Default.WaterDrop
        ActivityType.SCREEN_BREAK -> Icons.Default.VisibilityOff
        ActivityType.STRETCH -> Icons.Default.FitnessCenter
        ActivityType.POWER_NAP -> Icons.Default.Bedtime
        ActivityType.EVENING_RECOVERY -> Icons.Default.NightsStay
    }

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        activity.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${activity.durationMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                activity.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StressLow),
            ) {
                Text("Start")
            }
        }
    }
}
