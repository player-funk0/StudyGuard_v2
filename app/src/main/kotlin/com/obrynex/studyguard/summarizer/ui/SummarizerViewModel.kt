package com.obrynex.studyguard.summarizer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SummarizerUiState(
    val inputText   : String         = "",
    val summary     : String         = "",
    val isLoading   : Boolean        = false,
    val engineState : AIModelState   = AIModelState.Idle,
    val error       : String?        = null
)

class SummarizerViewModel(
    private val useCase   : SummarizeTextUseCase,
    private val aiManager : AIEngineManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummarizerUiState())
    val uiState: StateFlow<SummarizerUiState> = _uiState.asStateFlow()

    private var summarizeJob: Job? = null

    init {
        viewModelScope.launch {
            aiManager.state.collect { state ->
                _uiState.update { it.copy(engineState = state) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun summarize() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Please enter some text to summarise.") }
            return
        }
        summarizeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, summary = "", error = null) }
        summarizeJob = viewModelScope.launch {
            try {
                useCase(text).collect { token ->
                    _uiState.update { it.copy(summary = it.summary + token) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Summarisation failed.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun cancel() {
        summarizeJob?.cancel()
        _uiState.update { it.copy(isLoading = false) }
    }

    fun reset() {
        summarizeJob?.cancel()
        _uiState.value = SummarizerUiState(engineState = _uiState.value.engineState)
    }
}
