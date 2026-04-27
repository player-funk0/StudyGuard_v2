package com.obrynex.studyguard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySession): Long

    @Delete
    suspend fun delete(session: StudySession)

    @Query("SELECT * FROM study_sessions ORDER BY start_ms DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    fun sessionById(id: Long): Flow<StudySession?>

    /** Returns all sessions ordered by start_ms ASC — used for streak calculation. */
    @Query("SELECT * FROM study_sessions ORDER BY start_ms ASC")
    suspend fun getAllSessionsOnce(): List<StudySession>

    /** Total minutes studied in the given day (epoch-ms window). */
    @Query(
        """
        SELECT COALESCE(SUM(end_ms - start_ms), 0)
        FROM study_sessions
        WHERE start_ms >= :dayStartMs AND start_ms < :dayEndMs
        """
    )
    suspend fun totalMsInDay(dayStartMs: Long, dayEndMs: Long): Long
}
