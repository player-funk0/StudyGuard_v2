package com.obrynex.studyguard.textrank

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TextRankSummarizer].
 *
 * Tests are organized by function layer so a failure pinpoints exactly which
 * step of the pipeline is broken: tokenization → similarity → PageRank → output.
 *
 * No Android, coroutine, or IO dependencies — runs on the JVM directly.
 */
class TextRankSummarizerTest {

    // ── splitSentences ─────────────────────────────────────────────────────────

    @Test
    fun `splitSentences returns single element for one sentence`() {
        val result = TextRankSummarizer.splitSentences("هذه جملة واحدة فقط")
        assertEquals(1, result.size)
        assertEquals("هذه جملة واحدة فقط", result[0])
    }

    @Test
    fun `splitSentences splits on Arabic question mark`() {
        val text   = "ما هو الإسلام؟ الإسلام دين عظيم"
        val result = TextRankSummarizer.splitSentences(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `splitSentences splits on Latin period`() {
        val text   = "First sentence. Second sentence. Third sentence"
        val result = TextRankSummarizer.splitSentences(text)
        assertEquals(3, result.size)
    }

    @Test
    fun `splitSentences trims whitespace from each sentence`() {
        val result = TextRankSummarizer.splitSentences("  leading   . trailing  ")
        assertTrue(result.all { it == it.trim() })
    }

    @Test
    fun `splitSentences ignores empty segments`() {
        val result = TextRankSummarizer.splitSentences("sentence.....")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.isNotEmpty() })
    }

    // ── tokenize ───────────────────────────────────────────────────────────────

    @Test
    fun `tokenize strips Arabic stop-words`() {
        val tokens = TextRankSummarizer.tokenize("هذا هو الكتاب المفيد")
        // "هذا" and "هو" are stop-words; "الكتاب" and "المفيد" are not
        assertFalse(tokens.contains("هذا"))
        assertFalse(tokens.contains("هو"))
    }

    @Test
    fun `tokenize removes punctuation`() {
        val tokens = TextRankSummarizer.tokenize("مرحبا، كيف حالك؟")
        tokens.forEach { token ->
            assertFalse("Token '$token' contains punctuation", token.any { it in "،؛!?؟," })
        }
    }

    @Test
    fun `tokenize returns empty set for all-stop-word sentence`() {
        val tokens = TextRankSummarizer.tokenize("هذا هو ما في")
        // All four are stop-words or too short
        assertTrue(tokens.isEmpty() || tokens.all { it.length <= 2 })
    }

    @Test
    fun `tokenize strips Arabic diacritics`() {
        val withDiacritics    = TextRankSummarizer.tokenize("الكِتَابُ مُفِيدٌ")
        val withoutDiacritics = TextRankSummarizer.tokenize("الكتاب مفيد")
        assertEquals(withoutDiacritics, withDiacritics)
    }

    // ── buildSimilarityMatrix ─────────────────────────────────────────────────

    @Test
    fun `similarity matrix is symmetric`() {
        val sentences = listOf(
            setOf("دراسة", "علوم", "رياضيات"),
            setOf("علوم", "فيزياء", "كيمياء"),
            setOf("تاريخ", "جغرافيا", "أدب")
        )
        val mat = TextRankSummarizer.buildSimilarityMatrix(sentences)

        for (i in sentences.indices) {
            for (j in sentences.indices) {
                assertEquals(
                    "mat[$i][$j] ≠ mat[$j][$i]",
                    mat[i][j], mat[j][i], 1e-10
                )
            }
        }
    }

    @Test
    fun `diagonal of similarity matrix is zero`() {
        val sentences = listOf(
            setOf("الرياضيات", "دراسة"),
            setOf("الفيزياء", "علوم")
        )
        val mat = TextRankSummarizer.buildSimilarityMatrix(sentences)
        assertEquals(0.0, mat[0][0], 1e-10)
        assertEquals(0.0, mat[1][1], 1e-10)
    }

    @Test
    fun `identical sentences have higher similarity than disjoint ones`() {
        val same     = listOf(setOf("الدراسة", "مهمة", "جداً"), setOf("الدراسة", "مهمة", "جداً"))
        val disjoint = listOf(setOf("الدراسة", "مهمة", "جداً"), setOf("طعام", "ماء", "هواء"))

        val simSame     = TextRankSummarizer.buildSimilarityMatrix(same)[0][1]
        val simDisjoint = TextRankSummarizer.buildSimilarityMatrix(disjoint)[0][1]

        assertTrue("Same sentences should have higher similarity", simSame > simDisjoint)
    }

    @Test
    fun `disjoint sentences have zero similarity`() {
        val sentences = listOf(
            setOf("رياضيات", "جبر", "هندسة"),
            setOf("تاريخ", "أدب", "فلسفة")
        )
        val mat = TextRankSummarizer.buildSimilarityMatrix(sentences)
        assertEquals(0.0, mat[0][1], 1e-10)
    }

    // ── pageRank ───────────────────────────────────────────────────────────────

    @Test
    fun `pageRank scores sum to approximately 1`() {
        val mat = arrayOf(
            doubleArrayOf(0.0, 0.5, 0.3),
            doubleArrayOf(0.5, 0.0, 0.4),
            doubleArrayOf(0.3, 0.4, 0.0)
        )
        val scores = TextRankSummarizer.pageRank(mat)
        assertEquals(1.0, scores.sum(), 0.05)
    }

    @Test
    fun `pageRank returns uniform scores for all-zero matrix`() {
        val mat = Array(3) { DoubleArray(3) { 0.0 } }
        val scores = TextRankSummarizer.pageRank(mat)
        val expected = 1.0 / 3
        scores.forEach { score ->
            assertEquals(expected, score, 0.02)
        }
    }

    @Test
    fun `pageRank gives higher score to more connected node`() {
        // Node 0 and node 1 are both connected to node 2, but not to each other
        val mat = arrayOf(
            doubleArrayOf(0.0, 0.0, 0.9),
            doubleArrayOf(0.0, 0.0, 0.9),
            doubleArrayOf(0.9, 0.9, 0.0)
        )
        val scores = TextRankSummarizer.pageRank(mat)
        // Node 2 should score highest
        assertTrue("Node 2 should have highest score", scores[2] > scores[0])
        assertTrue("Node 2 should have highest score", scores[2] > scores[1])
    }

    // ── end-to-end summarize ───────────────────────────────────────────────────

    @Test
    fun `summarize returns input unchanged when shorter than topN sentences`() {
        val text   = "جملة قصيرة جداً لا تحتاج تلخيصاً."
        val result = TextRankSummarizer.summarize(text, topN = 5)
        assertEquals(text.trim(), result)
    }

    @Test
    fun `summarize returns at most topN sentences`() {
        val text = (1..20).joinToString(". ") {
            "الجملة رقم $it تحتوي على معلومات مفيدة جداً حول موضوع مختلف تماماً"
        }
        val result = TextRankSummarizer.summarize(text, topN = 3)
        val sentenceCount = result.split("\n").count { it.isNotBlank() }
        assertTrue("Expected ≤ 3 sentences, got $sentenceCount", sentenceCount <= 3)
    }

    @Test
    fun `summarize output sentences appear in original order`() {
        val sentences = (1..10).map {
            "هذه الجملة رقم $it وتتحدث عن موضوع الدراسة والعلوم المختلفة"
        }
        val text   = sentences.joinToString(". ")
        val result = TextRankSummarizer.summarize(text, topN = 3)

        // Extract sentence numbers from result and verify they're ascending
        val nums = Regex("""رقم (\d+)""")
            .findAll(result)
            .map { it.groupValues[1].toInt() }
            .toList()

        assertEquals("Sentences should be in ascending order", nums.sorted(), nums)
    }

    @Test
    fun `summarize handles empty string gracefully`() {
        val result = TextRankSummarizer.summarize("", topN = 3)
        assertTrue(result.isEmpty() || result.isBlank())
    }

    @Test
    fun `summarize does not throw on single long sentence`() {
        val longSentence = "كلمة ".repeat(300).trim()
        assertDoesNotThrow { TextRankSummarizer.summarize(longSentence, topN = 2) }
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e::class.simpleName}: ${e.message}")
        }
    }
}
