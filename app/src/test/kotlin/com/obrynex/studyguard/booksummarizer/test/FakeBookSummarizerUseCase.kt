package com.obrynex.studyguard.booksummarizer.test

import com.obrynex.studyguard.booksummarizer.BookSummarizeProgress
import com.obrynex.studyguard.booksummarizer.BookSummarizerUseCase
import com.obrynex.studyguard.booksummarizer.TextChunker
import com.obrynex.studyguard.ai.LocalAiEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Controllable [BookSummarizerUseCase] for unit tests.
 *
 * Rather than touching the Gemma model file, this fake emits a scripted
 * sequence of [BookSummarizeProgress] events that mirrors what the real use
 * case would emit for a two-chunk input.
 *
 * ### Modes
 *
 * | [behaviour]           | Effect                                               |
 * |-----------------------|------------------------------------------------------|
 * | [Behaviour.Succeed]   | Emits a realistic 2-chunk → reduce → done sequence.  |
 * | [Behaviour.Fail]      | Throws [simulatedError] inside the flow.             |
 * | [Behaviour.Empty]     | Emits only [BookSummarizeProgress.Done] with "".     |
 *
 * Usage:
 * ```kotlin
 * val fakeFactory: (LocalAiEngine) -> BookSummarizerUseCase =
 *     { _ -> FakeBookSummarizerUseCase() }
 * val vm = BookSummarizerViewModel(manager = fakeManager, useCaseFactory = fakeFactory)
 * ```
 */
class FakeBookSummarizerUseCase(
    var behaviour     : Behaviour  = Behaviour.Succeed,
    var simulatedError: Throwable  = RuntimeException("simulated inference error"),
    var fakeSummary   : String     = "هذا ملخص تجريبي ناجح من النموذج المزيف."
) : BookSummarizerUseCase(
    // BookSummarizerUseCase constructor — engine is unused by the fake,
    // but the class is non-abstract so we pass a null-safe placeholder.
    engine  = NoOpLocalAiEngine,
    chunker = TextChunker()
) {
    sealed class Behaviour {
        object Succeed : Behaviour()
        object Fail    : Behaviour()
        object Empty   : Behaviour()
    }

    /** How many times [summarize] was called. */
    var summarizeCallCount: Int = 0
        private set

    /** The last text passed to [summarize]. */
    var lastText: String? = null
        private set

    override fun summarize(text: String, levelLabel: String): Flow<BookSummarizeProgress> = flow {
        summarizeCallCount++
        lastText = text

        when (behaviour) {
            is Behaviour.Empty -> {
                emit(BookSummarizeProgress.Done(summary = ""))
            }
            is Behaviour.Fail -> {
                emit(BookSummarizeProgress.Starting(totalChunks = 1))
                throw simulatedError
            }
            is Behaviour.Succeed -> {
                emit(BookSummarizeProgress.Starting(totalChunks = 2))

                emit(BookSummarizeProgress.SummarizingChunk(0, 2, ""))
                emit(BookSummarizeProgress.SummarizingChunk(0, 2, "ملخص الجزء الأول."))

                emit(BookSummarizeProgress.SummarizingChunk(1, 2, ""))
                emit(BookSummarizeProgress.SummarizingChunk(1, 2, "ملخص الجزء الثاني."))

                emit(BookSummarizeProgress.Reducing)
                emit(BookSummarizeProgress.ReducingPartial(fakeSummary.take(10)))
                emit(BookSummarizeProgress.ReducingPartial(fakeSummary))
                emit(BookSummarizeProgress.Done(summary = fakeSummary))
            }
        }
    }

    fun reset() {
        summarizeCallCount = 0
        lastText           = null
        behaviour          = Behaviour.Succeed
    }
}

// ── No-op engine placeholder ───────────────────────────────────────────────────

/**
 * A [LocalAiEngine]-shaped object whose constructor does not access the
 * filesystem. Passed to [BookSummarizerUseCase] only so the class can
 * instantiate; the fake overrides [summarize] so [engine] is never used.
 *
 * We need this because [LocalAiEngine] is a concrete class, not an interface.
 * Using a real instance here would require the Gemma model to be present.
 */
private object NoOpLocalAiEngine : LocalAiEngine(android.app.Application()) {
    // All methods are unreachable because FakeBookSummarizerUseCase.summarize
    // overrides the only call-site. The object exists purely to satisfy the
    // constructor parameter.
}
