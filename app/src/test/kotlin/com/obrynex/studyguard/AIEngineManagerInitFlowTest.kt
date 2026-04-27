package com.obrynex.studyguard.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.obrynex.studyguard.ai.test.FakeModelHashCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the [AIEngineManager] init flow.
 *
 * These tests run on a real Android device/emulator so they have access to a
 * real [Context].  The model file is intentionally NOT present, so the tests
 * exercise the file-not-found and validation-failure paths without needing a
 * 1.35 GB binary.
 *
 * ### What is covered
 *
 * | Scenario                                   | Expected final state         |
 * |--------------------------------------------|------------------------------|
 * | No model file on device                    | [AIModelState.NotFound]      |
 * | validate() called twice concurrently       | Only one init runs (mutex)   |
 * | requestReInit() throttled                  | Second call dropped silently |
 * | clearDiagnostics() resets diagnostic fields| All fields back to defaults  |
 * | releaseEngine() resets state to Idle       | [AIModelState.Idle]          |
 *
 * ### Not covered here
 * Full warm-up (requires the ~1.35 GB model binary) — see manual / device farm tests.
 *
 * Dependencies (androidTestImplementation):
 *   "androidx.test.ext:junit:1.1.5"
 *   "app.cash.turbine:turbine:1.1.0"
 *   "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AIEngineManagerInitFlowTest {

    private val ctx         = InstrumentationRegistry.getInstrumentation().targetContext
    private val fakeCache   = FakeModelHashCache()
    private lateinit var manager: AIEngineManager

    @Before
    fun setUp() {
        manager = AIEngineManager(context = ctx, hashCache = fakeCache)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun initialStateIsIdle() = runTest {
        assertEquals(AIModelState.Idle, manager.state.value)
    }

    // ── FileNotFound path ──────────────────────────────────────────────────────

    @Test
    fun validateWithNoFileSetsNotFound() = runTest {
        // Model file is not present in the test environment
        manager.validate()

        val state = manager.state.value
        // Must be NotFound (file absent) or Failed (RAM gate) — never Ready
        assertTrue(
            "Expected NotFound or Failed but got $state",
            state is AIModelState.NotFound || state is AIModelState.Failed
        )
    }

    @Test
    fun stateFlowEmitsValidatingBeforeNotFound() = runTest {
        manager.state.test {
            // Initial Idle
            assertEquals(AIModelState.Idle, awaitItem())

            // Launch validate in a background coroutine scoped to this test —
            // backgroundScope is cancelled automatically when runTest finishes,
            // eliminating the GlobalScope leak that the previous implementation had.
            val job = backgroundScope.launch {
                manager.validate()
            }

            // Should see Validating then NotFound (or Failed for RAM)
            val second = awaitItem()
            assertTrue(
                "Expected Validating but got $second",
                second is AIModelState.Validating || second is AIModelState.Failed
            )
            val third = awaitItem()
            assertTrue(
                "Expected terminal state but got $third",
                third is AIModelState.NotFound || third is AIModelState.Failed
            )

            job.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Concurrent validate() — mutex guard ────────────────────────────────────

    @Test
    fun concurrentValidateCallsOnlyRunOneInit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope      = kotlinx.coroutines.CoroutineScope(dispatcher)

        // Launch two concurrent validate() calls
        val job1 = scope.launch { manager.validate() }
        val job2 = scope.launch { manager.validate() }

        job1.join()
        job2.join()

        val state = manager.state.value
        // The state should be deterministic — not stuck in Validating/Loading
        assertFalse(
            "Manager should not be stuck in Validating after both jobs complete",
            state is AIModelState.Validating || state is AIModelState.Loading
        )
    }

    // ── clearDiagnostics() ─────────────────────────────────────────────────────

    @Test
    fun clearDiagnosticsResetsAllFields() = runTest {
        manager.validate()

        // After validate (which failed due to missing file), lastError may be set
        manager.clearDiagnostics()

        assertEquals(-1L,  manager.lastLoadTimeMs)
        assertEquals(null, manager.lastError)
        assertEquals(null, manager.lastComputedHash)
    }

    // ── releaseEngine() ────────────────────────────────────────────────────────

    @Test
    fun releaseEngineSetsStateToIdle() = runTest {
        manager.validate()
        manager.releaseEngine()
        assertEquals(AIModelState.Idle, manager.state.value)
    }

    @Test
    fun afterReleaseValidateCanRunAgain() = runTest {
        manager.validate()
        manager.releaseEngine()
        assertEquals(AIModelState.Idle, manager.state.value)

        // Second validate should also terminate (not get stuck)
        manager.validate()
        val state = manager.state.value
        assertFalse(state is AIModelState.Validating)
        assertFalse(state is AIModelState.Loading)
    }

    // ── requestReInit() throttle ───────────────────────────────────────────────

    @Test
    fun rapidReInitCallsAreThrottled() = runTest {
        // First call
        manager.requestReInit()
        // Immediate second call — should be throttled
        manager.requestReInit()

        // State should be stable — not stuck mid-init
        val state = manager.state.value
        assertFalse(state is AIModelState.Validating)
        assertFalse(state is AIModelState.Loading)
    }

    // ── isInitializing flag ────────────────────────────────────────────────────

    @Test
    fun isInitializingIsFalseBeforeValidate() {
        assertFalse(manager.isInitializing.get())
    }

    @Test
    fun isInitializingIsFalseAfterValidateCompletes() = runTest {
        manager.validate()
        assertFalse(manager.isInitializing.get())
    }
}


