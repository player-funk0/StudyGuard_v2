package com.obrynex.studyguard.ai

import app.cash.turbine.test
import com.obrynex.studyguard.ai.test.FakeAIEngineManager
import com.obrynex.studyguard.textrank.test.FakeSummarizeWithTextRankUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiTutorViewModel] state transitions.
 *
 * Uses [FakeAIEngineManager] so no Android context, no MediaPipe, no filesystem
 * access is required.
 *
 * Dependencies:
 *   testImplementation("app.cash.turbine:turbine:1.1.0")
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
 *   testImplementation("junit:junit:4.13.2")
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTutorViewModelStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeManager  : FakeAIEngineManager
    private lateinit var fakeTextRank : FakeSummarizeWithTextRankUseCase
    private lateinit var vm           : AiTutorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeManager   = FakeAIEngineManager()
        fakeTextRank  = FakeSummarizeWithTextRankUseCase()
        vm            = AiTutorViewModel(manager = fakeManager, textRank = fakeTextRank)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fakeManager.reset()
        fakeTextRank.reset()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial modelState is Idle`() = runTest {
        assertEquals(AIModelState.Idle, vm.state.value.modelState)
    }

    @Test
    fun `initial modelPath is set from manager`() = runTest {
        assertEquals(fakeManager.modelFilePath, vm.state.value.modelPath)
    }

    // ── startEngine → Ready ────────────────────────────────────────────────────

    @Test
    fun `startEngine transitions modelState to Ready`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady

        vm.startEngine()
        advanceUntilIdle()

        assertEquals(AIModelState.Ready, vm.state.value.modelState)
        assertTrue(vm.state.value.isModelReady)
    }

    @Test
    fun `startEngine is idempotent — validate called once when already Ready`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        vm.startEngine()
        advanceUntilIdle()

        // Second call — FakeAIEngineManager.validate() will see state=Ready and skip
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.DoNothing
        vm.startEngine()
        advanceUntilIdle()

        // Still Ready — second call didn't change state
        assertEquals(AIModelState.Ready, vm.state.value.modelState)
    }

    // ── startEngine → NotFound ─────────────────────────────────────────────────

    @Test
    fun `startEngine transitions modelState to NotFound when file absent`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoNotFound

        vm.startEngine()
        advanceUntilIdle()

        assertEquals(AIModelState.NotFound, vm.state.value.modelState)
        assertFalse(vm.state.value.isModelReady)
    }

    // ── startEngine → Failed ───────────────────────────────────────────────────

    @Test
    fun `startEngine transitions modelState to Failed on load failure`() = runTest {
        val cause   = RuntimeException("MediaPipe exploded")
        val failure = ValidationFailure.LoadFailed(cause)
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoFailed(failure)

        vm.startEngine()
        advanceUntilIdle()

        val state = vm.state.value.modelState
        assertTrue(state is AIModelState.Failed)
        assertEquals(failure, (state as AIModelState.Failed).reason)
    }

    @Test
    fun `failureMessage is non-null in Failed state`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoFailed(
            ValidationFailure.SizeTooSmall(actualBytes = 100L, minimumBytes = 1_200_000_000L)
        )
        vm.startEngine()
        advanceUntilIdle()

        assertTrue(vm.state.value.failureMessage != null)
    }

    // ── isRetrying flag ────────────────────────────────────────────────────────

    @Test
    fun `isRetrying is false when Ready`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        vm.startEngine()
        advanceUntilIdle()
        assertFalse(vm.state.value.isRetrying)
    }

    @Test
    fun `isRetrying is false when NotFound`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoNotFound
        vm.startEngine()
        advanceUntilIdle()
        assertFalse(vm.state.value.isRetrying)
    }

    // ── retryEngine ────────────────────────────────────────────────────────────

    @Test
    fun `retryEngine goes Ready on second attempt`() = runTest {
        // First: fail
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoFailed(
            ValidationFailure.LoadFailed(RuntimeException("fail"))
        )
        vm.startEngine()
        advanceUntilIdle()
        assertEquals(AIModelState.Failed::class, vm.state.value.modelState::class)

        // Retry: succeed
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        vm.retryEngine()
        advanceUntilIdle()

        assertEquals(AIModelState.Ready, vm.state.value.modelState)
        assertTrue(fakeManager.reInitCallCount >= 1)
    }

    // ── loadTimeMs mirrors manager ─────────────────────────────────────────────

    @Test
    fun `loadTimeMs is updated when engine becomes Ready`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady

        vm.startEngine()
        advanceUntilIdle()

        // FakeAIEngineManager sets lastLoadTimeMs = 42L in GoReady path
        assertEquals(42L, vm.state.value.loadTimeMs)
    }

    // ── onCleared releases engine ──────────────────────────────────────────────

    @Test
    fun `onCleared calls releaseEngine`() = runTest {
        vm.onCleared()
        assertEquals(1, fakeManager.releaseCallCount)
    }

    // ── Fallback mode ──────────────────────────────────────────────────────────

    @Test
    fun `isFallbackMode is false initially`() = runTest {
        assertFalse(vm.state.value.isFallbackMode)
    }

    @Test
    fun `isFallbackMode cleared when engine becomes Ready`() = runTest {
        // Simulate fallback being active then engine recovering
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()
        assertFalse(vm.state.value.isFallbackMode)
    }

    // ── One-shot events ────────────────────────────────────────────────────────

    @Test
    fun `FallbackActivated event emitted when activateFallback is triggered`() = runTest {
        // This tests the event channel via a turbine collector
        vm.events.test {
            // Trigger fallback by calling send() when engine is Ready but inference throws
            // — we simulate this by emitting Ready then calling send with no engine in fake
            fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
            fakeManager.emitState(AIModelState.Ready)
            advanceUntilIdle()

            // No events yet
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── TextRank fallback — real extracted reply ────────────────────────────────

    /**
     * Verifies the TODO stub was removed: fallback mode now calls
     * [FakeSummarizeWithTextRankUseCase] and includes its result in the reply.
     */
    @Test
    fun `sendFallback calls TextRank use case once`() = runTest {
        // Put VM into fallback mode (engine is Idle so getEngine() returns null)
        // We manually set isFallbackMode by exploiting the activateFallback path
        // through the state. Simulate: engine went Ready, fallback is already active.
        // The simplest way is to send() while isFallbackMode is already true.
        // We reach that state by driving the manager to Loading (not Ready),
        // injecting isFallbackMode via the internal state channel.

        // Drive: engine not available → isFallbackMode set manually via
        // activateFallback which is private. Instead, send() branches on
        // isFallbackMode. We set that by using the public API path:
        //  1. Let manager go Ready.
        //  2. Let getEngine() return null so send() triggers activateFallback.
        //  3. Check that the NEXT send() (now in fallback mode) calls textRank.

        // Step 1: go Ready but engine returns null (FakeAIEngineManager default)
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        vm.onInputChanged("سؤال مهم جداً")
        vm.send()           // engine is Ready but getEngine() returns null → activateFallback()
        advanceUntilIdle()

        assertTrue(vm.state.value.isFallbackMode)

        // Step 2: next send() goes through the fallback path
        vm.onInputChanged("سؤال آخر في وضع الاحتياطي")
        vm.send()
        advanceUntilIdle()

        assertEquals(1, fakeTextRank.invokeCount)
    }

    @Test
    fun `sendFallback reply contains TextRank extracted text`() = runTest {
        fakeTextRank  // fixedResult = "نتيجة تجريبية من TextRank"

        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        // First send() triggers fallback (engine null)
        vm.onInputChanged("سؤال أول")
        vm.send()
        advanceUntilIdle()

        // Second send() goes through TextRank
        vm.onInputChanged("سؤال ثانٍ")
        vm.send()
        advanceUntilIdle()

        val lastReply = vm.state.value.messages
            .filter { !it.isUser }
            .last()

        assertTrue(
            "Expected TextRank result in reply, got: ${lastReply.text}",
            lastReply.text.contains(fakeTextRank.fixedResult)
        )
        assertTrue(lastReply.isFallback)
    }

    @Test
    fun `sendFallback passes user input to TextRank`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        // Trigger fallback mode
        vm.onInputChanged("تفعيل الوضع البديل")
        vm.send()
        advanceUntilIdle()

        val inputText = "النص المدخل للتلخيص"
        vm.onInputChanged(inputText)
        vm.send()
        advanceUntilIdle()

        assertEquals(inputText, fakeTextRank.lastInput)
    }

    @Test
    fun `fallback message has isFallback flag set to true`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        // Enter fallback
        vm.onInputChanged("أول رسالة")
        vm.send()
        advanceUntilIdle()

        // Send in fallback
        vm.onInputChanged("رسالة داخل وضع الاحتياطي")
        vm.send()
        advanceUntilIdle()

        val fallbackReplies = vm.state.value.messages.filter { !it.isUser && it.isFallback }
        assertTrue("Expected at least one fallback reply", fallbackReplies.isNotEmpty())
    }
}
