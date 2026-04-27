package com.obrynex.studyguard.data.db

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Calculates the current daily study streak from the persisted session list.
 *
 * A "streak" is the number of consecutive calendar days (ending today or
 * yesterday) on which at least one study session was recorded.
 */
class GetStreakUseCase(private val dao: StudySessionDao) {

    /**
     * Returns the current streak count in days.
     *
     * @return 0 if no sessions exist or the streak was broken.
     */
    suspend operator fun invoke(): Int {
        val sessions = dao.getAllSessionsOnce()
        if (sessions.isEmpty()) return 0

        val studiedDays = sessions
            .map { epochMsToDay(it.startMs) }
            .toSortedSet()
            .toList()
            .reversed() // most-recent first

        val todayDay = epochMsToDay(System.currentTimeMillis())
        val yesterdayDay = todayDay - 1

        // Streak must include today or yesterday; otherwise it's already broken.
        if (studiedDays.first() < yesterdayDay) return 0

        var streak = 0
        var expected = studiedDays.first()
        for (day in studiedDays) {
            if (day == expected) {
                streak++
                expected--
            } else {
                break
            }
        }
        return streak
    }

    /** Converts epoch-ms to a "day number" (days since epoch) using local time. */
    private fun epochMsToDay(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }
}
