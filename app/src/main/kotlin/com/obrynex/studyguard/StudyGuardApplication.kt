package com.obrynex.studyguard

import android.app.Application
import com.obrynex.studyguard.di.ServiceLocator

/**
 * Application entry-point.
 *
 * Initialises [ServiceLocator] (manual DI) so that singleton dependencies
 * — Room database, DataStore-backed caches, AIEngineManager — are ready
 * before any Activity or ViewModel is created.
 */
class StudyGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
