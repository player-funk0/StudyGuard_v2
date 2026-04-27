package com.obrynex.studyguard.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obrynex.studyguard.data.db.StudySession
import com.obrynex.studyguard.data.db.StudySessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val dao: StudySessionDao
) : ViewModel() {

    fun sessionById(id: Long): Flow<StudySession?> = dao.sessionById(id)

    fun delete(session: StudySession) {
        viewModelScope.launch { dao.delete(session) }
    }
}
