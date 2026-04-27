package com.obrynex.studyguard.di

import android.content.Context
import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.ModelHashCache
import com.obrynex.studyguard.data.db.AppDatabase
import com.obrynex.studyguard.data.db.GetStreakUseCase
import com.obrynex.studyguard.data.db.StudySessionDao
import com.obrynex.studyguard.summarizer.ui.SummarizeTextUseCase

/**
 * Manual dependency-injection container (Service Locator pattern).
 *
 * [init] must be called in [com.obrynex.studyguard.StudyGuardApplication.onCreate]
 * before any ViewModel or screen accesses these singletons.
 *
 * Design principles:
 *  - Lazy initialisation — nothing is created until first access.
 *  - All properties are `val` after [init]; the graph is immutable at runtime.
 *  - [ViewModelProviders] is the only consumer of this object.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** Must be called once from Application.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Database ─────────────────────────────────────────────────────────────

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val studySessionDao: StudySessionDao by lazy {
        database.studySessionDao()
    }

    // ── Use-cases ─────────────────────────────────────────────────────────────

    val getStreakUseCase: GetStreakUseCase by lazy {
        GetStreakUseCase(studySessionDao)
    }

    val summarizeTextUseCase: SummarizeTextUseCase by lazy {
        SummarizeTextUseCase(aiEngineManager)
    }

    // ── AI engine ─────────────────────────────────────────────────────────────

    val modelHashCache: ModelHashCache by lazy {
        ModelHashCache(appContext)
    }

    val aiEngineManager: AIEngineManager by lazy {
        AIEngineManager(
            context   = appContext,
            hashCache = modelHashCache
        )
    }
}
