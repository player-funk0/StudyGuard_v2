package com.obrynex.studyguard.ai.test

import com.obrynex.studyguard.ai.ModelHashCache

/**
 * In-memory [ModelHashCache] replacement for unit tests.
 * No DataStore, no Android context required.
 *
 * Overrides all suspend functions including [getSnapshot] so that
 * [AIEngineManager.computeOrCachedSha256] — which now calls [getSnapshot]
 * instead of three separate reads — is fully exercised without I/O.
 */
class FakeModelHashCache : ModelHashCache(
    // Null context — the fake never touches DataStore.
    context = android.app.Application()
) {
    private var hash         : String? = null
    private var lastModified : Long    = -1L
    private var fileSize     : Long    = -1L

    // ── Primary override — single-read snapshot ────────────────────────────────

    /**
     * Returns a [Snapshot] built from in-memory fields.
     * No DataStore, no version/corruption checks — the fake always returns
     * whatever was last saved via [saveHash] or [seed].
     */
    override suspend fun getSnapshot(): Snapshot =
        Snapshot(hash = hash, lastModified = lastModified, fileSize = fileSize)

    // ── Convenience overrides (delegate to getSnapshot) ────────────────────────

    override suspend fun getCachedHash(): String? = getSnapshot().hash
    override suspend fun getLastModified(): Long  = getSnapshot().lastModified
    override suspend fun getLastFileSize(): Long  = getSnapshot().fileSize

    // ── Write / clear ──────────────────────────────────────────────────────────

    override suspend fun saveHash(hash: String, lastModified: Long, fileSize: Long) {
        this.hash         = hash
        this.lastModified = lastModified
        this.fileSize     = fileSize
    }

    override suspend fun clear() {
        hash         = null
        lastModified = -1L
        fileSize     = -1L
    }

    /** Seed the cache with pre-existing values — useful for testing the pre-hash-check path. */
    fun seed(hash: String, lastModified: Long, fileSize: Long) {
        this.hash         = hash
        this.lastModified = lastModified
        this.fileSize     = fileSize
    }
}
