package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.logic.CheckInValidator
import com.example.data.UserSettings
import com.example.ui.viewmodels.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val isTimeValid by viewModel.isTimeValid.collectAsState()
    val isLocationValid by viewModel.isLocationValid.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    var showPermissionDialog by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionGranted = granted
        if (granted) {
            viewModel.fetchLocation()
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            viewModel.fetchLocation()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Denied") },
            text = { Text("Location permission is required to verify your check-ins at the dojo. Please enable it in the app settings.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    var showCelebration by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showCelebration) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "celebration_scale",
        finishedListener = { showCelebration = false }
    )

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val isOnCooldown = CheckInValidator.isOnCooldown(nowMillis, userSettings.lastCheckInMillis)
    val remainingHours = CheckInValidator.getRemainingCooldownHours(nowMillis, userSettings.lastCheckInMillis)
    val canCheckIn = isTimeValid && isLocationValid && !isOnCooldown

    val nextTrainingSummary by viewModel.nextTrainingSummary.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "DojoStreak",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Streak Flame Visual
        Box(modifier = Modifier.scale(scale)) {
            StreakCounterVisual(streak = userSettings.currentStreak)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Check-in status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Training Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                StatusRow(
                    label = "Scheduled Time Context",
                    isValid = isTimeValid
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "At Dojo Location",
                    isValid = isLocationValid
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { 
                        viewModel.performCheckIn() 
                        showCelebration = true
                        nowMillis = System.currentTimeMillis()
                    },
                    enabled = canCheckIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(if (canCheckIn) "Check-In!" else "Check-In Unavailable", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (isOnCooldown) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You have already checked in recently. Next check-in available in ~${remainingHours} hours.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                } else if (!isTimeValid || !isLocationValid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            !isTimeValid && !isLocationValid -> "No training scheduled right now, and you are not at a selected gym."
                            !isTimeValid -> "No training scheduled at this time."
                            else -> "You are not at the scheduled gym."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    if (nextTrainingSummary != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = nextTrainingSummary!!,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = { 
            if (!locationPermissionGranted) {
                locationPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            } else {
                viewModel.fetchLocation() 
            }
        }) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Location")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh GPS")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = onNavigateToSetup) {
            Text("Edit Dojo & Schedule")
        }
    }
}

@Composable
fun StatusRow(label: String, isValid: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (isValid) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Valid", tint = Color(0xFF3DDC84))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = "Invalid", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun StreakCounterVisual(streak: Int) {
    val primaryColor = if (streak > 0) Color(0xFFFF5722) else Color(0xFFBDBDBD)
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Text(
                text = "DAY STREAK",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                color = primaryColor
            )
        }
    }
}
