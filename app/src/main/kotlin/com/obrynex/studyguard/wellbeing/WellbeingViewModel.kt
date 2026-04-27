package com.obrynex.studyguard.wellbeing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BreathPhase { INHALE, HOLD, EXHALE, REST }

data class WellbeingUiState(
    val phase          : BreathPhase = BreathPhase.REST,
    val secondsLeft    : Int         = 0,
    val isRunning      : Boolean     = false,
    val cycleCount     : Int         = 0,
    val motivationQuote: String      = quotes.random()
)

class WellbeingViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(WellbeingUiState())
    val uiState: StateFlow<WellbeingUiState> = _uiState.asStateFlow()

    fun startBreathing() {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(isRunning = true, cycleCount = 0) }
        viewModelScope.launch {
            repeat(BOX_CYCLES) { cycle ->
                runPhase(BreathPhase.INHALE,  INHALE_SECS)
                runPhase(BreathPhase.HOLD,    HOLD_SECS)
                runPhase(BreathPhase.EXHALE,  EXHALE_SECS)
                runPhase(BreathPhase.REST,    REST_SECS)
                _uiState.update { it.copy(cycleCount = cycle + 1) }
            }
            _uiState.update {
                it.copy(isRunning = false, phase = BreathPhase.REST, secondsLeft = 0,
                    motivationQuote = quotes.random())
            }
        }
    }

    fun stop() {
        _uiState.update { it.copy(isRunning = false, phase = BreathPhase.REST, secondsLeft = 0) }
    }

    private suspend fun runPhase(phase: BreathPhase, durationSecs: Int) {
        repeat(durationSecs) { tick ->
            _uiState.update { it.copy(phase = phase, secondsLeft = durationSecs - tick) }
            delay(1_000L)
        }
    }

    companion object {
        private const val BOX_CYCLES   = 4
        private const val INHALE_SECS  = 4
        private const val HOLD_SECS    = 4
        private const val EXHALE_SECS  = 4
        private const val REST_SECS    = 4

        private val quotes = listOf(
            "Consistency is the key to mastery.",
            "Every expert was once a beginner.",
            "Small progress is still progress.",
            "Your future self will thank you.",
            "Focus on the process, not perfection.",
            "Rest is part of the work.",
            "One step at a time — keep going!"
        )
    }
}
