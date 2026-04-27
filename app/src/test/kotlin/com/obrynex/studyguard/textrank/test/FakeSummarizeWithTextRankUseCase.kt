package com.obrynex.studyguard.textrank.test

import com.obrynex.studyguard.textrank.SummarizeWithTextRankUseCase

/**
 * Controllable [SummarizeWithTextRankUseCase] for unit tests.
 *
 * ### Usage
 * ```kotlin
 * val fakeTextRank = FakeSummarizeWithTextRankUseCase(fixedResult = "الجملة المستخرجة")
 * val vm = AiTutorViewModel(manager = fakeManager, textRank = fakeTextRank)
 * ```
 *
 * Setting [fixedResult] lets tests assert on the exact fallback reply without
 * running the real PageRank algorithm (which depends on sentence count and
 * stop-word lists that would make test inputs brittle).
 *
 * [invokeCount] lets tests verify how many times the summarizer was called.
 */
class FakeSummarizeWithTextRankUseCase(
    val fixedResult: String = "نتيجة تجريبية من TextRank"
) : SummarizeWithTextRankUseCase() {

    /** How many times [invoke] was called. */
    var invokeCount: Int = 0
        private set

    /** The last [text] argument passed to [invoke], or null if never called. */
    var lastInput: String? = null
        private set

    /** The last [topN] argument passed to [invoke]. */
    var lastTopN: Int = -1
        private set

    /**
     * Returns [fixedResult] immediately without running any algorithm.
     * Records call metadata for assertions.
     */
    override operator fun invoke(text: String, topN: Int): String {
        invokeCount++
        lastInput = text
        lastTopN  = topN
        return fixedResult
    }

    /** Resets all recorded state between tests. */
    fun reset() {
        invokeCount = 0
        lastInput   = null
        lastTopN    = -1
    }
}
