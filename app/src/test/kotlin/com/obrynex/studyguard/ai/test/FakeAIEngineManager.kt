package com.obrynex.studyguard.ai.test

import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ai.LocalAiEngine
import com.obrynex.studyguard.ai.ModelHashCache
import com.obrynex.studyguard.ai.ValidationFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drop-in [AIEngineManager] replacement for unit and integration tests.
 *
 * Usage — inject into the ViewModel under test:
 * ```kotlin
 * val fake = FakeAIEngineManager()
 * val vm   = AiTutorViewModel(manager = fake)
 *
 * fake.emitState(AIModelState.Ready)
 * ```
 *
 * All public properties mirror [AIEngineManager] so the ViewModel under test
 * compiles without modification.
 */
class FakeAIEngineManager : AIEngineManager(
    // Pass a null context — the fake never touches the filesystem.
    context    = android.app.Application(),   // replaced in subclasses with a real mock if needed
    hashCache  = FakeModelHashCache(),
    chunkBytes = 1024
) {
    // ── Mutable state ──────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<AIModelState>(AIModelState.Idle)

    /** Publicly visible StateFlow — mirrors production API. */
    override val state: StateFlow<AIModelState> = _state.asStateFlow()

    /** Call from tests to drive state transitions. */
    fun emitState(newState: AIModelState) {
        _state.value = newState
    }

    // ── Controllable behaviour ─────────────────────────────────────────────────

    /**
     * What [validate] should do:
     *  - [ValidateBehaviour.GoReady]   → emits Validating → Loading → Ready
     *  - [ValidateBehaviour.GoFailed]  → emits Validating → Failed(reason)
     *  - [ValidateBehaviour.GoNotFound]→ emits Validating → NotFound
     *  - [ValidateBehaviour.DoNothing] → no state change (tests debounce logic)
     */
    var validateBehaviour: ValidateBehaviour = ValidateBehaviour.GoReady

    sealed class ValidateBehaviour {
        object DoNothing   : ValidateBehaviour()
        object GoReady     : ValidateBehaviour()
        object GoNotFound  : ValidateBehaviour()
        data class GoFailed(val failure: ValidationFailure) : ValidateBehaviour()
    }

    /** How many times [validate] was invoked. */
    var validateCallCount: Int = 0
        private set

    /** How many times [requestReInit] was invoked. */
    var reInitCallCount: Int = 0
        private set

    /** How many times [releaseEngine] was invoked. */
    var releaseCallCount: Int = 0
        private set

    // ── Overrides ──────────────────────────────────────────────────────────────

    override suspend fun validate() {
        validateCallCount++
        when (val b = validateBehaviour) {
            is ValidateBehaviour.DoNothing  -> Unit
            is ValidateBehaviour.GoReady    -> {
                _state.value = AIModelState.Validating
                _state.value = AIModelState.Loading
                lastLoadTimeMs = 42L
                _state.value = AIModelState.Ready
            }
            is ValidateBehaviour.GoNotFound -> {
                _state.value = AIModelState.Validating
                _state.value = AIModelState.NotFound
            }
            is ValidateBehaviour.GoFailed   -> {
                _state.value = AIModelState.Validating
                _state.value = AIModelState.Failed(b.failure)
            }
        }
    }

    override suspend fun requestReInit() {
        reInitCallCount++
        _state.value = AIModelState.Idle
        validate()
    }

    override fun releaseEngine() {
        releaseCallCount++
        _state.value = AIModelState.Idle
    }

    /** Overridden to always return null — tests that need a live engine supply one via [setFakeEngine]. */
    override fun getEngine(): LocalAiEngine? = fakeEngine

    private var fakeEngine: LocalAiEngine? = null

    /** Inject a mock [LocalAiEngine] for tests that exercise [AiTutorViewModel.send]. */
    fun setFakeEngine(engine: LocalAiEngine?) {
        fakeEngine = engine
    }

    // ── Diagnostics ────────────────────────────────────────────────────────────

    override var lastLoadTimeMs   : Long       = -1L
    override var lastError        : Throwable? = null
    override var lastComputedHash : String?    = null

    override val modelFilePath: String = "/fake/path/models/gemma-2b-it-cpu-int4.bin"

    // ── Reset ──────────────────────────────────────────────────────────────────

    /** Resets all call counters and state — call between tests. */
    fun reset() {
        validateCallCount = 0
        reInitCallCount   = 0
        releaseCallCount  = 0
        fakeEngine        = null
        lastLoadTimeMs    = -1L
        lastError         = null
        lastComputedHash  = null
        _state.value      = AIModelState.Idle
    }
}
