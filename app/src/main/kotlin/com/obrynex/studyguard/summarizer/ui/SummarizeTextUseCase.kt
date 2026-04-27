package com.obrynex.studyguard.summarizer.ui

import com.obrynex.studyguard.ai.AIEngineManager
import com.obrynex.studyguard.ai.AIModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Summarises a block of text using the on-device AI engine when it is
 * [AIModelState.Ready], or falls back to a simple sentence-extraction
 * heuristic when the engine is unavailable.
 */
class SummarizeTextUseCase(private val aiManager: AIEngineManager) {

    /**
     * Emits partial tokens as they arrive from the AI engine, or emits a
     * single fallback string if the engine is not ready.
     */
    operator fun invoke(text: String): Flow<String> = flow {
        if (aiManager.state.value is AIModelState.Ready) {
            val engine = aiManager.getEngine() ?: run {
                emit(fallbackSummarize(text))
                return@flow
            }
            val prompt = buildPrompt(text)
            engine.generateStream(prompt).collect { token -> emit(token) }
        } else {
            emit(fallbackSummarize(text))
        }
    }

    private fun buildPrompt(text: String): String =
        "Summarise the following text in 3-5 concise bullet points:\n\n$text"

    /** Very simple extractive fallback — first 3 sentences. */
    private fun fallbackSummarize(text: String): String =
        text.split(Regex("(?<=[.!?])\\s+"))
            .take(3)
            .joinToString(" ")
            .ifBlank { text.take(300) }
}
