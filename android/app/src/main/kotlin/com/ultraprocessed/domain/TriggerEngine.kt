package com.ultraprocessed.domain

import com.ultraprocessed.core.Http
import com.ultraprocessed.data.dao.ConsumptionWithFood
import com.ultraprocessed.data.entities.BowelEvent
import com.ultraprocessed.data.entities.FodmapLevel
import com.ultraprocessed.data.entities.FoodEntry
import com.ultraprocessed.data.entities.SymptomEvent
import com.ultraprocessed.sync.TriggerReportDto
import com.ultraprocessed.sync.TriggerScoreDto
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Local port of backend/app/services/triggers.py. Used when the backend is
 * not configured (or reachable). Same algorithm — slightly simplified — so
 * a self-hosted user without a server still gets the leaderboard.
 *
 * Why duplicate the backend?
 *  - The app is local-first; a fresh user with no backend should still
 *    see their own correlations.
 *  - The server's result is preferred when available because the server
 *    sees data from other devices too. The fallback only kicks in when
 *    `SyncCoordinator.fetchTriggerReport` returns null.
 *
 * Cost is O(flares * meals_in_window), tiny in practice (<1000 of each).
 */
object TriggerEngine {

    const val DEFAULT_LOOKBACK_HOURS = 48.0
    const val DEFAULT_MIN_LAG_MINUTES = 30.0
    const val DEFAULT_HALF_LIFE_HOURS = 12.0
    const val DEFAULT_FLARE_SEVERITY_THRESHOLD = 2
    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val MILLIS_PER_MINUTE = 60_000.0

    /** Public entry. Returns a [TriggerReportDto] so the UI can reuse the
     *  same rendering path as the server response. */
    fun compute(
        bowels: List<BowelEvent>,
        symptoms: List<SymptomEvent>,
        consumption: List<ConsumptionWithFood>,
        fromMs: Long,
        toMs: Long,
        lookbackHours: Double = DEFAULT_LOOKBACK_HOURS,
        minLagMinutes: Double = DEFAULT_MIN_LAG_MINUTES,
        halfLifeHours: Double = DEFAULT_HALF_LIFE_HOURS,
        severityThreshold: Int = DEFAULT_FLARE_SEVERITY_THRESHOLD,
        limit: Int = 25
    ): TriggerReportDto {
        val flares = collectFlares(bowels, symptoms, severityThreshold)
        val foodsScored = if (flares.isEmpty()) emptyList() else scoreFoods(
            flares = flares,
            consumption = consumption,
            lookbackHours = lookbackHours,
            minLagMinutes = minLagMinutes,
            halfLifeHours = halfLifeHours
        )
        return TriggerReportDto(
            fromDt = isoFromMs(fromMs),
            toDt = isoFromMs(toMs),
            lookbackHours = lookbackHours,
            halfLifeHours = halfLifeHours,
            flareSeverityThreshold = severityThreshold,
            flaresConsidered = flares.size,
            foodsScored = foodsScored.size,
            foods = foodsScored.take(limit)
        )
    }

    // ---- Flare collection ----

    private data class Flare(
        val occurredAtMs: Long,
        val severity01: Double, // 0..1
        val sourceUuid: String
    )

    private fun collectFlares(
        bowels: List<BowelEvent>,
        symptoms: List<SymptomEvent>,
        severityThreshold: Int
    ): List<Flare> {
        val out = mutableListOf<Flare>()
        for (s in symptoms) {
            if (s.severity >= severityThreshold) {
                out += Flare(s.occurredAt, s.severity / 3.0, s.clientUuid)
            }
        }
        for (b in bowels) {
            val isFlare = b.bristol in listOf(1, 2, 6, 7) || b.urgency >= 2 || b.pain >= 2
            if (!isFlare) continue
            // Severity rolls Bristol extremity + urgency + pain into 0..1.
            val bristolExtremity = (4 - b.bristol).absoluteValue / 3.0
            val sev = maxOf(bristolExtremity, b.urgency / 3.0, b.pain / 3.0)
            out += Flare(b.occurredAt, sev, b.clientUuid)
        }
        return out.sortedBy { it.occurredAtMs }
    }

    // ---- Scoring ----

    private data class Accum(
        var score: Double = 0.0,
        val flareUuids: MutableSet<String> = mutableSetOf(),
        var lastFlareMs: Long? = null
    )

    private fun scoreFoods(
        flares: List<Flare>,
        consumption: List<ConsumptionWithFood>,
        lookbackHours: Double,
        minLagMinutes: Double,
        halfLifeHours: Double
    ): List<TriggerScoreDto> {
        val accums = mutableMapOf<String, Accum>()
        val foodsByUuid = consumption.associateBy({ it.log.foodClientUuid }, { it.food })
        val lookbackMs = (lookbackHours * MILLIS_PER_HOUR).toLong()
        val minLagMs = (minLagMinutes * MILLIS_PER_MINUTE).toLong()

        for (flare in flares) {
            val windowStart = flare.occurredAtMs - lookbackMs
            val windowEnd = flare.occurredAtMs - minLagMs
            if (windowEnd < windowStart) continue
            for (cf in consumption) {
                val eatenAt = cf.log.eatenAt
                if (eatenAt !in windowStart..windowEnd) continue
                val food = cf.food
                val hoursBefore = (flare.occurredAtMs - eatenAt) / MILLIS_PER_HOUR
                val recency = exp(-hoursBefore / halfLifeHours)
                val fodmap = severityScore(food.fodmapLevel)
                val grams = portionGrams(cf, food)
                val portion = sqrt(max(grams, 1.0) / 100.0)
                val contribution = recency * fodmap * portion * flare.severity01
                val acc = accums.getOrPut(food.clientUuid) { Accum() }
                acc.score += contribution
                acc.flareUuids += flare.sourceUuid
                if (acc.lastFlareMs == null || flare.occurredAtMs > (acc.lastFlareMs ?: Long.MIN_VALUE)) {
                    acc.lastFlareMs = flare.occurredAtMs
                }
            }
        }

        // Baseline counts: how often this food appeared outside any flare window.
        val flareWindows = flares.map { (it.occurredAtMs - lookbackMs) to it.occurredAtMs }
        val baselineCounts = mutableMapOf<String, Int>()
        for (cf in consumption) {
            val eatenAt = cf.log.eatenAt
            val inWindow = flareWindows.any { (start, end) -> eatenAt in start..end }
            if (!inWindow) {
                baselineCounts[cf.log.foodClientUuid] = (baselineCounts[cf.log.foodClientUuid] ?: 0) + 1
            }
        }

        return accums.entries
            .mapNotNull { (uuid, acc) ->
                val food = foodsByUuid[uuid] ?: return@mapNotNull null
                val flareCount = acc.flareUuids.size
                val damped = acc.score * ln(1.0 + flareCount)
                TriggerScoreDto(
                    foodClientUuid = uuid,
                    foodName = food.name,
                    foodBrand = food.brand,
                    fodmapLevel = food.fodmapLevel,
                    fodmapTags = parseTags(food.fodmapTagsJson),
                    novaClass = food.novaClass,
                    score = roundTo(damped, 3),
                    flareCount = flareCount,
                    baselineCount = baselineCounts[uuid] ?: 0,
                    lastFlareAt = acc.lastFlareMs?.let { isoFromMs(it) },
                    sampleFlares = emptyList()
                )
            }
            .sortedByDescending { it.score }
    }

    private fun severityScore(level: FodmapLevel): Double = when (level) {
        FodmapLevel.UNKNOWN -> 0.25
        FodmapLevel.LOW -> 0.1
        FodmapLevel.MODERATE -> 0.6
        FodmapLevel.HIGH -> 1.0
    }

    private fun portionGrams(cf: ConsumptionWithFood, food: FoodEntry): Double {
        cf.log.gramsEaten?.let { return it }
        food.gramsPerUnit?.let { return it * (cf.log.percentageEaten / 100.0) }
        food.packageGrams?.let { return it * (cf.log.percentageEaten / 100.0) }
        return 100.0 * (cf.log.percentageEaten / 100.0)
    }

    private fun parseTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            Http.Json.decodeFromString(ListSerializer(String.serializer()), json)
        }.getOrDefault(emptyList())
    }

    private fun roundTo(v: Double, decimals: Int): Double {
        var f = 1.0
        repeat(decimals) { f *= 10.0 }
        return kotlin.math.round(v * f) / f
    }

    private fun isoFromMs(ms: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))
}
