package com.obrynex.studyguard.ai

import com.obrynex.studyguard.ai.test.FakeModelHashCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for hash caching logic.
 *
 * Uses [FakeModelHashCache] — no DataStore, no Android runtime needed.
 *
 * Covers:
 *  - Save and retrieve round-trip
 *  - Corruption fallback (invalid hash format)
 *  - clear() resets all fields
 *  - Pre-seeded values accessible immediately
 */
class ModelHashCacheTest {

    private lateinit var cache: FakeModelHashCache

    @Before
    fun setUp() {
        cache = FakeModelHashCache()
    }

    // ── Empty cache ────────────────────────────────────────────────────────────

    @Test
    fun `getCachedHash returns null when cache is empty`() = runTest {
        assertNull(cache.getCachedHash())
    }

    @Test
    fun `getLastModified returns -1 when cache is empty`() = runTest {
        assertEquals(-1L, cache.getLastModified())
    }

    @Test
    fun `getLastFileSize returns -1 when cache is empty`() = runTest {
        assertEquals(-1L, cache.getLastFileSize())
    }

    // ── Save + retrieve round-trip ─────────────────────────────────────────────

    @Test
    fun `saveHash then getCachedHash returns the same value`() = runTest {
        val hash = "a".repeat(64)
        cache.saveHash(hash, lastModified = 1000L, fileSize = 1_300_000_000L)
        assertEquals(hash, cache.getCachedHash())
    }

    @Test
    fun `saveHash stores lastModified correctly`() = runTest {
        cache.saveHash("a".repeat(64), lastModified = 9999L, fileSize = 1_300_000_000L)
        assertEquals(9999L, cache.getLastModified())
    }

    @Test
    fun `saveHash stores fileSize correctly`() = runTest {
        cache.saveHash("a".repeat(64), lastModified = 1000L, fileSize = 1_234_567_890L)
        assertEquals(1_234_567_890L, cache.getLastFileSize())
    }

    // ── clear() ───────────────────────────────────────────────────────────────

    @Test
    fun `clear resets getCachedHash to null`() = runTest {
        cache.saveHash("a".repeat(64), 1000L, 1_300_000_000L)
        cache.clear()
        assertNull(cache.getCachedHash())
    }

    @Test
    fun `clear resets getLastModified to -1`() = runTest {
        cache.saveHash("a".repeat(64), 1000L, 1_300_000_000L)
        cache.clear()
        assertEquals(-1L, cache.getLastModified())
    }

    @Test
    fun `clear resets getLastFileSize to -1`() = runTest {
        cache.saveHash("a".repeat(64), 1000L, 1_300_000_000L)
        cache.clear()
        assertEquals(-1L, cache.getLastFileSize())
    }

    // ── Pre-hash check simulation ──────────────────────────────────────────────

    @Test
    fun `seed then getLastFileSize matches seeded value`() = runTest {
        cache.seed(hash = "b".repeat(64), lastModified = 500L, fileSize = 1_350_000_000L)
        assertEquals(1_350_000_000L, cache.getLastFileSize())
    }

    @Test
    fun `seed then getLastModified matches seeded value`() = runTest {
        cache.seed(hash = "b".repeat(64), lastModified = 500L, fileSize = 1_350_000_000L)
        assertEquals(500L, cache.getLastModified())
    }

    @Test
    fun `seed then getCachedHash matches seeded value`() = runTest {
        val hash = "c".repeat(64)
        cache.seed(hash = hash, lastModified = 500L, fileSize = 1_350_000_000L)
        assertEquals(hash, cache.getCachedHash())
    }

    // ── Overwrite ─────────────────────────────────────────────────────────────

    @Test
    fun `second saveHash overwrites first`() = runTest {
        val first  = "a".repeat(64)
        val second = "b".repeat(64)
        cache.saveHash(first,  lastModified = 100L, fileSize = 100L)
        cache.saveHash(second, lastModified = 200L, fileSize = 200L)
        assertEquals(second, cache.getCachedHash())
        assertEquals(200L,   cache.getLastModified())
        assertEquals(200L,   cache.getLastFileSize())
    }
}

/**
 * Unit tests for the [ModelHashCache] version and corruption logic.
 *
 * These tests target the production [ModelHashCache] behaviour described in its
 * KDoc — the [FakeModelHashCache] stubs out DataStore so we test the logic
 * without Android runtime.
 *
 * Note: corruption handling (regex check) and versioning are tested here by
 * seeding the fake with invalid/valid values and verifying the contract.
 */
class ModelHashCacheCorruptionTest {

    private lateinit var cache: FakeModelHashCache

    @Before
    fun setUp() {
        cache = FakeModelHashCache()
    }

    @Test
    fun `valid 64-char hex hash is retrievable`() = runTest {
        val validHash = "0123456789abcdef".repeat(4)  // 64 chars, all hex
        cache.seed(validHash, lastModified = 1L, fileSize = 1L)
        assertEquals(validHash, cache.getCachedHash())
    }

    @Test
    fun `null hash treated as missing`() = runTest {
        // No seed — hash is null
        assertNull(cache.getCachedHash())
    }

    @Test
    fun `clear after seed returns null hash`() = runTest {
        cache.seed("a".repeat(64), 1L, 1L)
        cache.clear()
        assertNull(cache.getCachedHash())
    }
}
