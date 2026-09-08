package com.example.ui.viewmodel

data class TimerState(
    val isRunning: Boolean = false,
    val endTimeMs: Long = 0L,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val durationMinutes: Int = 5
)

data class StopwatchState(
    val isRunning: Boolean = false,
    val startAtMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    val currentElapsedMs: Long = 0L,
    val laps: List<Long> = emptyList()
)

data class RunningPillInfo(
    val visible: Boolean = false,
    val kind: String = "", // "timer" or "sw"
    val text: String = ""
)
