package com.obrynex.studyguard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single focused study session recorded by the Tracker feature.
 *
 * @param id          Auto-generated primary key.
 * @param startMs     Session start time in epoch milliseconds.
 * @param endMs       Session end time in epoch milliseconds.
 * @param subject     Optional subject label entered by the user.
 * @param notes       Optional free-text notes for the session.
 */
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_ms")
    val startMs: Long,

    @ColumnInfo(name = "end_ms")
    val endMs: Long,

    @ColumnInfo(name = "subject")
    val subject: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = ""
) {
    /** Duration in milliseconds. */
    val durationMs: Long get() = endMs - startMs

    /** Duration in whole minutes. */
    val durationMinutes: Long get() = durationMs / 60_000L
}
