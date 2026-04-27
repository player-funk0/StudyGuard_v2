package com.obrynex.studyguard.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.db.GetStreakUseCase
import com.obrynex.studyguard.data.db.StudySession
import com.obrynex.studyguard.data.db.StudySessionDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackerUiState(
    val sessions      : List<StudySession> = emptyList(),
    val streak        : Int                = 0,
    val isRunning     : Boolean            = false,
    val sessionStart  : Long?              = null
)

class TrackerViewModel(
    application : Application,
    private val dao       : StudySessionDao,
    private val getStreak : GetStreakUseCase
) : AndroidViewModel(application) {

    val sessions: StateFlow<List<StudySession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var sessionStartMs: Long? = null

    init {
        refreshStreak()
    }

    fun startSession() {
        if (_isRunning.value) return
        sessionStartMs = System.currentTimeMillis()
        _isRunning.value = true
    }

    fun stopSession(subject: String = "", notes: String = "") {
        val start = sessionStartMs ?: return
        _isRunning.value = false
        sessionStartMs = null
        viewModelScope.launch {
            dao.insert(
                StudySession(
                    startMs = start,
                    endMs   = System.currentTimeMillis(),
                    subject = subject,
                    notes   = notes
                )
            )
            refreshStreak()
        }
    }

    fun deleteSession(session: StudySession) {
        viewModelScope.launch {
            dao.delete(session)
            refreshStreak()
        }
    }

    private fun refreshStreak() {
        viewModelScope.launch {
            _streak.value = getStreak()
        }
    }
}
