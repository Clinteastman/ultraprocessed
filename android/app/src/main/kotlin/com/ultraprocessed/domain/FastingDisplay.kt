package com.ultraprocessed.domain

import com.ultraprocessed.data.entities.FastingProfile
import com.ultraprocessed.data.entities.ScheduleType
import java.util.Calendar

/**
 * Pure-function helpers for rendering a fasting profile's "what's
 * happening right now" state on the phone home screen. Mirrors the
 * dashboard's FastingStatus.svelte logic so both surfaces show the
 * same copy.
 */
object FastingDisplay {

    enum class Mood { Eating, Fasting, Restricted, Normal, None }

    data class State(
        val title: String,
        val sub: String,
        val mood: Mood
    )

    /**
     * Current display state given the active profile and the current time.
     * Returns null if no profile or inactive (caller renders an empty
     * placeholder / call to action).
     */
    fun compute(profile: FastingProfile?, now: Calendar = Calendar.getInstance()): State? {
        if (profile == null || !profile.active) return null

        return when (profile.scheduleType) {
            ScheduleType.SIXTEEN_EIGHT,
            ScheduleType.EIGHTEEN_SIX,
            ScheduleType.TWENTY_FOUR,
            ScheduleType.OMAD,
            ScheduleType.CUSTOM -> tre(profile, now)

            ScheduleType.FIVE_TWO,
            ScheduleType.FOUR_THREE,
            ScheduleType.ADF -> weekly(profile, now)
        }
    }

    private fun tre(profile: FastingProfile, now: Calendar): State {
        val start = profile.eatingWindowStartMinutes
        val end = profile.eatingWindowEndMinutes
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val inWindow = minutesNow in start..end
        return if (inWindow) {
            val close = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, end)
            }
            State(
                title = "Eating window open",
                sub = "Closes at ${timeOf(end)} · ${diffParts(close.timeInMillis, now.timeInMillis)} left",
                mood = Mood.Eating
            )
        } else {
            val nextOpen = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, start)
                if (minutesNow > end) add(Calendar.DAY_OF_YEAR, 1)
            }
            State(
                title = "Fasting",
                sub = "Eat next at ${timeOf(start)} · ${diffParts(nextOpen.timeInMillis, now.timeInMillis)} to go",
                mood = Mood.Fasting
            )
        }
    }

    private fun weekly(profile: FastingProfile, now: Calendar): State {
        val mask = profile.restrictedDaysMask
        if (mask == 0) {
            return State("Fasting plan active", "No restricted days configured.", Mood.None)
        }
        val todayBit = 1 shl dayMon0(now)
        val todayRestricted = (mask and todayBit) != 0
        if (todayRestricted) {
            val cap = profile.restrictedKcalTarget ?: 500
            val reset = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            return State(
                title = "Restricted day · $cap kcal cap",
                sub = "Resets at midnight · ${diffParts(reset.timeInMillis, now.timeInMillis)} to go",
                mood = Mood.Restricted
            )
        }
        val next = nextRestrictedDay(mask, now)
        if (next == null) return State("Fasting plan active", "No restricted days configured.", Mood.None)
        val dayLabel = next.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault()) ?: ""
        return State(
            title = "Normal eating today",
            sub = "Next restricted day: $dayLabel · ${diffParts(next.timeInMillis, now.timeInMillis)}",
            mood = Mood.Normal
        )
    }

    /** Mon = 0, Sun = 6 (matches restricted_days_mask bit positions). */
    private fun dayMon0(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun nextRestrictedDay(mask: Int, from: Calendar): Calendar? {
        for (i in 1..7) {
            val cand = (from.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if ((mask and (1 shl dayMon0(cand))) != 0) return cand
        }
        return null
    }

    private fun timeOf(minutes: Int): String {
        val h = (minutes / 60).toString().padStart(2, '0')
        val m = (minutes % 60).toString().padStart(2, '0')
        return "$h:$m"
    }

    private fun diffParts(toMs: Long, fromMs: Long): String {
        val diff = (toMs - fromMs).coerceAtLeast(0L)
        val totalMin = (diff / 60_000).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h == 0) "${m}m" else "${h}h ${m.toString().padStart(2, '0')}m"
    }
}
