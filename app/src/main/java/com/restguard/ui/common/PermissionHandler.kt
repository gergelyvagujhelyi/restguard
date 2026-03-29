package com.restguard.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*

/**
 * Composable-friendly permission handling for all required permissions.
 *
 * Usage:
 *   val permissionState = rememberPermissionState()
 *   permissionState.requestCalendar()
 */

data class PermissionState(
    val hasCalendar: Boolean = false,
    val hasContacts: Boolean = false,
    val hasNotification: Boolean = false,
    val hasHealthConnect: Boolean = false,
)

@Composable
fun rememberPermissionState(): MutableState<PermissionState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(checkPermissions(context)) }

    // Recheck when resuming
    DisposableEffect(Unit) {
        state.value = checkPermissions(context)
        onDispose { }
    }

    return state
}

fun checkPermissions(context: Context): PermissionState {
    return PermissionState(
        hasCalendar = hasPermission(context, Manifest.permission.READ_CALENDAR) &&
            hasPermission(context, Manifest.permission.WRITE_CALENDAR),
        hasContacts = hasPermission(context, Manifest.permission.READ_CONTACTS),
        hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true,
        // SDK availability only — use checkHealthConnectPermissions() for actual grant status
        hasHealthConnect = isHealthConnectAvailable(context),
    )
}

fun isHealthConnectAvailable(context: Context): Boolean {
    return try {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    } catch (_: Exception) {
        false
    }
}

/**
 * Check whether the app has been granted Health Connect read permissions.
 * Returns true only if at least one health permission is actually granted.
 */
suspend fun checkHealthConnectPermissions(context: Context): Boolean {
    if (!isHealthConnectAvailable(context)) return false
    return try {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        granted.containsAll(HEALTH_PERMISSIONS)
    } catch (_: Exception) {
        false
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

// ─── Health Connect permissions ─────────────────────────────

val HEALTH_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(RespiratoryRateRecord::class),
)

/**
 * Creates a launcher for Health Connect permission requests.
 */
@Composable
fun rememberHealthConnectPermissionLauncher(
    onResult: (Set<String>) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    if (!isHealthConnectAvailable(context)) return null

    val contract = PermissionController.createRequestPermissionResultContract()
    val launcher = rememberLauncherForActivityResult(contract) { granted ->
        onResult(granted)
    }
    return { launcher.launch(HEALTH_PERMISSIONS) }
}

/**
 * Creates a launcher for standard Android permission requests.
 */
@Composable
fun rememberMultiplePermissionLauncher(
    onResult: (Map<String, Boolean>) -> Unit,
): (Array<String>) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        onResult(results)
    }
    return { permissions -> launcher.launch(permissions) }
}
