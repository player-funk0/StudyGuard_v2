package com.obrynex.studyguard.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefs: DataStore<Preferences>
    by preferencesDataStore(name = "studyguard_prefs")

/**
 * Thin wrapper over [DataStore] that exposes app-wide preferences as
 * suspension functions and [Flow]s.
 *
 * All keys are private — callers use the typed API only.
 */
object PrefsManager {

    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    /**
     * Emits `true` once the user has completed onboarding; `false` until then.
     */
    fun onboardingDone(context: Context): Flow<Boolean> =
        context.appPrefs.data.map { prefs ->
            prefs[KEY_ONBOARDING_DONE] ?: false
        }

    /** Persists the fact that onboarding has been completed. */
    suspend fun setOnboardingDone(context: Context) {
        context.appPrefs.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = true
        }
    }

    /** Resets onboarding state (useful for testing / debug builds). */
    suspend fun resetOnboarding(context: Context) {
        context.appPrefs.edit { prefs ->
            prefs.remove(KEY_ONBOARDING_DONE)
        }
    }
}
