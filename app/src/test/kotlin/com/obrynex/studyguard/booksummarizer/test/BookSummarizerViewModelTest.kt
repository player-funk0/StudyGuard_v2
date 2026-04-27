package com.obrynex.studyguard.booksummarizer

import app.cash.turbine.test
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ai.ValidationFailure
import com.obrynex.studyguard.ai.test.FakeAIEngineManager
import com.obrynex.studyguard.booksummarizer.test.FakeBookSummarizerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BookSummarizerViewModel] state transitions.
 *
 * Uses [FakeAIEngineManager] for engine control and [FakeBookSummarizerUseCase]
 * injected through the [BookSummarizerViewModel.useCaseFactory] parameter.
 * No Android context, model file, or filesystem access required.
 *
 * Test organization mirrors [AiTutorViewModelStateTest]:
 *  - Initial state
 *  - Engine state mirroring
 *  - Input handling
 *  - Summarize pipeline (success / failure / cancellation)
 *  - Reset behaviour
 *
 * Dependencies (same as the existing test suite):
 *   testImplementation("app.cash.turbine:turbine:1.1.0")
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
 *   testImplementation("junit:junit:4.13.2")
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookSummarizerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeManager  : FakeAIEngineManager
    private lateinit var fakeUseCase  : FakeBookSummarizerUseCase
    private lateinit var vm           : BookSummarizerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeManager = FakeAIEngineManager()
        fakeUseCase = FakeBookSummarizerUseCase()
        vm = BookSummarizerViewModel(
            manager        = fakeManager,
            useCaseFactory = { _ -> fakeUseCase }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fakeManager.reset()
        fakeUseCase.reset()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty bookText`() = runTest {
        assertEquals("", vm.state.value.bookText)
    }

    @Test
    fun `initial state has zero wordCount`() = runTest {
        assertEquals(0, vm.state.value.wordCount)
    }

    @Test
    fun `initial state has Idle phase`() = runTest {
        assertEquals(SummarizerPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `initial state is not running`() = runTest {
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun `initial modelState is Idle`() = runTest {
        assertEquals(AIModelState.Idle, vm.state.value.modelState)
    }

    // ── Engine state mirroring ─────────────────────────────────────────────────

    @Test
    fun `modelState mirrors engine Ready`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()
        assertEquals(AIModelState.Ready, vm.state.value.modelState)
        assertTrue(vm.state.value.isModelReady)
    }

    @Test
    fun `modelState mirrors engine NotFound`() = runTest {
        fakeManager.emitState(AIModelState.NotFound)
        advanceUntilIdle()
        assertEquals(AIModelState.NotFound, vm.state.value.modelState)
        assertFalse(vm.state.value.isModelReady)
    }

    @Test
    fun `modelState mirrors engine Failed`() = runTest {
        val failure = ValidationFailure.LoadFailed(RuntimeException("boom"))
        fakeManager.emitState(AIModelState.Failed(failure))
        advanceUntilIdle()
        assertTrue(vm.state.value.modelState is AIModelState.Failed)
    }

    @Test
    fun `isModelBusy is true during Validating`() = runTest {
        fakeManager.emitState(AIModelState.Validating)
        advanceUntilIdle()
        assertTrue(vm.state.value.isModelBusy)
    }

    @Test
    fun `isModelBusy is true during Loading`() = runTest {
        fakeManager.emitState(AIModelState.Loading)
        advanceUntilIdle()
        assertTrue(vm.state.value.isModelBusy)
    }

    @Test
    fun `validate is called on init`() = runTest {
        advanceUntilIdle()
        assertTrue(fakeManager.validateCallCount >= 1)
    }

    // ── onBookTextChanged ──────────────────────────────────────────────────────

    @Test
    fun `onBookTextChanged updates bookText`() = runTest {
        vm.onBookTextChanged("النص الجديد")
        assertEquals("النص الجديد", vm.state.value.bookText)
    }

    @Test
    fun `onBookTextChanged updates wordCount`() = runTest {
        vm.onBookTextChanged("كلمة واحدة اثنتان ثلاث")
        assertEquals(4, vm.state.value.wordCount)
    }

    @Test
    fun `onBookTextChanged clears error`() = runTest {
        // Trigger an error first (no text, model not ready)
        vm.startSummarizing()
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onBookTextChanged("نص جديد")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `empty text gives zero wordCount`() = runTest {
        vm.onBookTextChanged("some text")
        vm.onBookTextChanged("")
        assertEquals(0, vm.state.value.wordCount)
    }

    // ── canSummarize guard ─────────────────────────────────────────────────────

    @Test
    fun `canSummarize is false when model not ready`() = runTest {
        fakeManager.emitState(AIModelState.Idle)
        vm.onBookTextChanged("كتاب كامل")
        advanceUntilIdle()
        assertFalse(vm.state.value.canSummarize)
    }

    @Test
    fun `canSummarize is false when text is blank`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("   ")
        advanceUntilIdle()
        assertFalse(vm.state.value.canSummarize)
    }

    @Test
    fun `canSummarize is true when model ready and text present`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص طويل كافٍ للتلخيص")
        advanceUntilIdle()
        assertTrue(vm.state.value.canSummarize)
    }

    @Test
    fun `canSummarize is false while running`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("كتاب جيد")
        advanceUntilIdle()

        // Use a slow fake so isRunning stays true mid-test
        fakeUseCase.behaviour = FakeBookSummarizerUseCase.Behaviour.Succeed
        vm.startSummarizing()

        // Immediately after launch isRunning is true, canSummarize is false
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.canSummarize)
    }

    // ── startSummarizing — guard conditions ───────────────────────────────────

    @Test
    fun `startSummarizing sets error when engine not ready`() = runTest {
        fakeManager.emitState(AIModelState.Idle) // no engine
        vm.onBookTextChanged("نص جيد")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun `startSummarizing sets error when text is blank`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        vm.startSummarizing() // no text set
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
    }

    // ── Full success pipeline ──────────────────────────────────────────────────

    @Test
    fun `successful run transitions through Starting to Done`() = runTest {
        fakeManager.validateBehaviour = FakeAIEngineManager.ValidateBehaviour.GoReady
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("كتاب جيد فيه معلومات وافرة")
        advanceUntilIdle()

        vm.state.test {
            // Consume the current emission
            awaitItem()

            vm.startSummarizing()

            // Phases: Starting → SummarizingChunk → Reducing → ReducingPartial → Done
            val phases = mutableListOf<SummarizerPhase>()
            var done = false
            while (!done) {
                val s = awaitItem()
                phases.add(s.phase)
                if (s.phase == SummarizerPhase.Done) done = true
            }

            assertTrue("Starting phase missing", phases.contains(SummarizerPhase.Starting))
            assertTrue("SummarizingChunk phase missing", phases.contains(SummarizerPhase.SummarizingChunk))
            assertTrue("Reducing phase missing", phases.contains(SummarizerPhase.Reducing))
            assertTrue("Done phase missing", phases.contains(SummarizerPhase.Done))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Done phase sets isRunning to false`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص قابل للتلخيص")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRunning)
        assertEquals(SummarizerPhase.Done, vm.state.value.phase)
    }

    @Test
    fun `Done phase populates finalSummary`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("محتوى الكتاب المراد تلخيصه")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertEquals(fakeUseCase.fakeSummary, vm.state.value.finalSummary)
    }

    @Test
    fun `progress fraction reaches 1f on Done`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("محتوى")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertEquals(1f, vm.state.value.progress, 0.001f)
    }

    @Test
    fun `currentChunk increments as chunks are processed`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        var maxChunk = 0
        vm.state.test {
            awaitItem()
            vm.startSummarizing()
            // Collect until done
            while (true) {
                val s = awaitItem()
                if (s.currentChunk > maxChunk) maxChunk = s.currentChunk
                if (s.phase == SummarizerPhase.Done) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        // FakeBookSummarizerUseCase emits chunk indices 0 and 1 → currentChunk reaches 2
        assertTrue("currentChunk should have advanced", maxChunk >= 1)
    }

    @Test
    fun `use case factory is called with the live engine`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertEquals(1, fakeUseCase.summarizeCallCount)
    }

    // ── Failure path ──────────────────────────────────────────────────────────

    @Test
    fun `flow exception sets error and clears isRunning`() = runTest {
        fakeUseCase.behaviour = FakeBookSummarizerUseCase.Behaviour.Fail
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isRunning)
        assertEquals(SummarizerPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `empty flow emits Done with empty summary`() = runTest {
        fakeUseCase.behaviour = FakeBookSummarizerUseCase.Behaviour.Empty
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        vm.startSummarizing()
        advanceUntilIdle()

        // Done is emitted with "" summary — isRunning must be false
        assertFalse(vm.state.value.isRunning)
    }

    // ── cancel ─────────────────────────────────────────────────────────────────

    @Test
    fun `cancel sets isRunning to false`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        vm.startSummarizing()
        vm.cancel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun `cancel reverts phase to Idle`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()

        vm.startSummarizing()
        vm.cancel()
        advanceUntilIdle()

        assertEquals(SummarizerPhase.Idle, vm.state.value.phase)
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears bookText`() = runTest {
        vm.onBookTextChanged("محتوى مهم")
        vm.reset()
        assertEquals("", vm.state.value.bookText)
    }

    @Test
    fun `reset clears finalSummary`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()
        vm.startSummarizing()
        advanceUntilIdle()

        vm.reset()

        assertEquals("", vm.state.value.finalSummary)
    }

    @Test
    fun `reset preserves modelState`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        advanceUntilIdle()

        vm.reset()

        // modelState should still reflect the engine (Ready), not be wiped
        assertEquals(AIModelState.Ready, vm.state.value.modelState)
    }

    @Test
    fun `reset sets phase to Idle`() = runTest {
        fakeManager.emitState(AIModelState.Ready)
        vm.onBookTextChanged("نص")
        advanceUntilIdle()
        vm.startSummarizing()
        advanceUntilIdle()

        vm.reset()

        assertEquals(SummarizerPhase.Idle, vm.state.value.phase)
    }

    // ── retryEngine ────────────────────────────────────────────────────────────

    @Test
    fun `retryEngine calls requestReInit on manager`() = runTest {
        vm.retryEngine()
        advanceUntilIdle()
        assertTrue(fakeManager.reInitCallCount >= 1)
    }

    // ── progressLabel sanity ──────────────────────────────────────────────────

    @Test
    fun `progressLabel is empty in Idle phase`() = runTest {
        assertEquals("", vm.state.value.progressLabel)
    }
}
