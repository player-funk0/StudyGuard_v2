package com.obrynex.studyguard.wellbeing

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WellbeingScreen(vm: WellbeingViewModel) {
    val state by vm.uiState.collectAsState()

    // Breathing circle pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val scale by infiniteTransition.animateFloat(
        initialValue    = if (state.isRunning && state.phase == BreathPhase.INHALE) 1f else 1.2f,
        targetValue     = if (state.isRunning && state.phase == BreathPhase.EXHALE) 1f else 1.2f,
        animationSpec   = infiniteRepeatable(
            animation = tween(4_000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier              = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(24.dp)
    ) {
        Text("Wellbeing", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        // Motivation quote
        Card(
            modifier  = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Text(
                text      = "\"${state.motivationQuote}\"",
                modifier  = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(8.dp))

        // Breathing circle
        Surface(
            modifier = Modifier
                .size(180.dp)
                .scale(if (state.isRunning) scale else 1f),
            shape  = CircleShape,
            color  = when (state.phase) {
                BreathPhase.INHALE  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                BreathPhase.HOLD    -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                BreathPhase.EXHALE  -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                BreathPhase.REST    -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = when (state.phase) {
                            BreathPhase.INHALE -> "Inhale"
                            BreathPhase.HOLD   -> "Hold"
                            BreathPhase.EXHALE -> "Exhale"
                            BreathPhase.REST   -> if (state.isRunning) "Rest" else "Ready"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 20.sp
                    )
                    if (state.isRunning && state.secondsLeft > 0) {
                        Text(
                            text     = "${state.secondsLeft}s",
                            style    = MaterialTheme.typography.labelLarge,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.isRunning) {
            Text(
                text  = "Cycle ${state.cycleCount + 1} of 4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Control buttons
        if (!state.isRunning) {
            Button(
                onClick  = vm::startBreathing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Box Breathing (4×4×4×4)")
            }
        } else {
            OutlinedButton(
                onClick  = vm::stop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }
        }

        Text(
            text  = "Box breathing helps reduce stress and\nimprove focus before a study session.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
