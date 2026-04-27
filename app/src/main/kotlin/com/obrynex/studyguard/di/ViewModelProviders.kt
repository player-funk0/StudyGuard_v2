package com.obrynex.studyguard.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.obrynex.studyguard.ai.AiTutorViewModel
import com.obrynex.studyguard.ai.DebugInfoViewModel
import com.obrynex.studyguard.booksummarizer.BookSummarizerViewModel
import com.obrynex.studyguard.islamic.ui.IslamicViewModel
import com.obrynex.studyguard.summarizer.ui.SummarizerViewModel
import com.obrynex.studyguard.textrank.SummarizeWithTextRankUseCase
import com.obrynex.studyguard.tracker.SessionDetailViewModel
import com.obrynex.studyguard.tracker.TrackerViewModel
import com.obrynex.studyguard.wellbeing.WellbeingViewModel

/**
 * Centralised [ViewModelProvider.Factory] wiring for the app's manual DI setup.
 *
 * This is the **only** file that imports [ServiceLocator] for ViewModel construction.
 */
object ViewModelProviders {

    val bookSummarizer: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BookSummarizerViewModel(manager = ServiceLocator.aiEngineManager)
        }
    }

    val aiTutor: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AiTutorViewModel(
                manager  = ServiceLocator.aiEngineManager,
                textRank = SummarizeWithTextRankUseCase()
            )
        }
    }

    val summarizer: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SummarizerViewModel(
                useCase   = ServiceLocator.summarizeTextUseCase,
                aiManager = ServiceLocator.aiEngineManager
            )
        }
    }

    val tracker: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            TrackerViewModel(
                application = app,
                dao         = ServiceLocator.studySessionDao,
                getStreak   = ServiceLocator.getStreakUseCase
            )
        }
    }

    val sessionDetail: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SessionDetailViewModel(dao = ServiceLocator.studySessionDao)
        }
    }

    val wellbeing: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            WellbeingViewModel(app = app)
        }
    }

    val islamic: ViewModelProvider.Factory = viewModelFactory {
        initializer { IslamicViewModel() }
    }

    /**
     * Debug / Diagnostics screen.
     * Now also threads the Application context so [DebugInfoViewModel] can read RAM info.
     */
    val debugInfo: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            DebugInfoViewModel(
                manager   = ServiceLocator.aiEngineManager,
                hashCache = ServiceLocator.modelHashCache,
                context   = app
            )
        }
    }
}
