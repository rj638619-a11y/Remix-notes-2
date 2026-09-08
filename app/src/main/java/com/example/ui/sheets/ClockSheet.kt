package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme
import com.example.ui.viewmodel.StopwatchState
import com.example.ui.viewmodel.TimerState
import com.example.util.DateFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockSheet(
    initialKind: String = "timer", // "timer" or "sw"
    timerState: TimerState,
    stopwatchState: StopwatchState,
    onDismiss: () -> Unit,
    onSetTimerMinutes: (Int) -> Unit,
    onStartTimer: (Int) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onStartSw: () -> Unit,
    onPauseSw: () -> Unit,
    onResumeSw: () -> Unit,
    onLapSw: () -> Unit,
    onResetSw: () -> Unit
) {
    val colors = GlassTheme.colors
    var currentKind by remember { mutableStateOf(initialKind) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        scrimColor = colors.shadow.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Segment switch for Timer / Stopwatch
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.field)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ClockSegmentButton(
                    label = "Timer",
                    isSelected = currentKind == "timer",
                    onClick = { currentKind = "timer" }
                )
                ClockSegmentButton(
                    label = "Stopwatch",
                    isSelected = currentKind == "sw",
                    onClick = { currentKind = "sw" }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (currentKind == "timer") {
                TimerContent(
                    timerState = timerState,
                    onSetMinutes = onSetTimerMinutes,
                    onStart = onStartTimer,
                    onPause = onPauseTimer,
                    onResume = onResumeTimer,
                    onReset = onResetTimer
                )
            } else {
                StopwatchContent(
                    stopwatchState = stopwatchState,
                    onStart = onStartSw,
                    onPause = onPauseSw,
                    onResume = onResumeSw,
                    onLap = onLapSw,
                    onReset = onResetSw
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .clickable { onDismiss() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Close",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ClockSegmentButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) colors.card else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) colors.text else colors.textSecondary
        )
    }
}

@Composable
private fun TimerContent(
    timerState: TimerState,
    onSetMinutes: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit
) {
    val colors = GlassTheme.colors
    val isRunning = timerState.isRunning
    val isPaused = !isRunning && timerState.totalMs > 0
    val isIdle = !isRunning && timerState.totalMs == 0L

    val displayTime = if (isRunning || isPaused) {
        DateFormatter.fmtMS(timerState.remainingMs)
    } else {
        DateFormatter.fmtMS(timerState.durationMinutes * 60_000L)
    }

    val subtitle = when {
        isRunning -> "Ends at ${DateFormatter.fmtClock(timerState.endTimeMs)} — keeps counting in the background"
        isPaused -> "Paused · ${DateFormatter.fmtMS(timerState.remainingMs)} left"
        else -> "Pick a duration, then start"
    }

    Text(
        text = displayTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )

    Text(
        text = subtitle,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (isIdle) {
        // Preset duration chips
        val presets = listOf(1, 3, 5, 10, 15, 30, 45, 60)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center
        ) {
            for (m in presets) {
                val isSelected = timerState.durationMinutes == m
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) colors.chipOnBg else colors.field)
                        .clickable { onSetMinutes(m) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${m}m",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.chipOnTx else colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stepper
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(colors.field)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSetMinutes(maxOf(1, timerState.durationMinutes - 1)) },
                contentAlignment = Alignment.Center
            ) {
                Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            }

            Text(
                text = "${timerState.durationMinutes} min",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.text,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSetMinutes(minOf(999, timerState.durationMinutes + 1)) },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Control buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isIdle) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .clickable { onReset() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reset", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = colors.text)
            }
        }

        val primaryBg = colors.accentSecondary
        val primaryText = Color(0xFF231A00)
        val primaryLabel = when {
            isRunning -> "Pause"
            isPaused -> "Resume"
            else -> "Start"
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryBg)
                .clickable {
                    when {
                        isRunning -> onPause()
                        isPaused -> onResume()
                        else -> onStart(timerState.durationMinutes)
                    }
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(primaryLabel, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = primaryText)
        }
    }
}

@Composable
private fun StopwatchContent(
    stopwatchState: StopwatchState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit
) {
    val colors = GlassTheme.colors
    val isRunning = stopwatchState.isRunning
    val isPaused = !isRunning && stopwatchState.accumulatedMs > 0
    val isIdle = !isRunning && stopwatchState.accumulatedMs == 0L

    var liveElapsed by remember {
        mutableLongStateOf(
            if (isRunning) {
                stopwatchState.accumulatedMs + (System.currentTimeMillis() - stopwatchState.startAtMs)
            } else {
                stopwatchState.accumulatedMs
            }
        )
    }

    LaunchedEffect(isRunning, stopwatchState.startAtMs, stopwatchState.accumulatedMs) {
        if (isRunning) {
            while (true) {
                val now = System.currentTimeMillis()
                liveElapsed = stopwatchState.accumulatedMs + (now - stopwatchState.startAtMs)
                kotlinx.coroutines.delay(30)
            }
        } else {
            liveElapsed = stopwatchState.accumulatedMs
        }
    }

    val subtitle = when {
        isRunning -> "Running — keeps counting in the background"
        isPaused -> "Paused · ${stopwatchState.laps.size} lap${if (stopwatchState.laps.size == 1) "" else "s"} recorded"
        else -> "Ready when you are"
    }

    Text(
        text = DateFormatter.fmtSW(liveElapsed),
        fontFamily = FontFamily.Monospace,
        fontSize = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )

    Text(
        text = subtitle,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // Control buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isIdle) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .clickable { onReset() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Reset", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = colors.text)
            }
        }

        if (isRunning) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.field)
                    .clickable { onLap() }
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Lap", fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = colors.text)
            }
        }

        val primaryBg = colors.accentSecondary
        val primaryText = Color(0xFF231A00)
        val primaryLabel = when {
            isRunning -> "Pause"
            isPaused -> "Resume"
            else -> "Start"
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryBg)
                .clickable {
                    when {
                        isRunning -> onPause()
                        isPaused -> onResume()
                        else -> onStart()
                    }
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(primaryLabel, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = primaryText)
        }
    }

    if (stopwatchState.laps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState())
        ) {
            stopwatchState.laps.forEachIndexed { index, lapMs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lap ${stopwatchState.laps.size - index}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = DateFormatter.fmtSW(lapMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.hairline)
                )
            }
        }
    }
}
