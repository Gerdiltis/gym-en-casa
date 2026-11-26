package com.gymencasa.domain

import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import com.gymencasa.domain.model.Pregnancy
import kotlin.random.Random

/**
 * Local exercise suggestion: balanced across muscle groups, avoiding what was done last time,
 * and sized so that the resulting plan fits the time the user has today.
 * Also used as the fallback when the AI is unavailable.
 */
object SuggestionEngine {

    fun preferredTypes(goal: Goal): Set<ExerciseType> = when (goal) {
        Goal.FUERZA -> setOf(ExerciseType.FUERZA, ExerciseType.CORE)
        Goal.CARDIO -> setOf(ExerciseType.CARDIO, ExerciseType.FUERZA, ExerciseType.CORE)
        Goal.TONIFICAR -> setOf(ExerciseType.FUERZA, ExerciseType.CORE, ExerciseType.CARDIO)
        Goal.MOVILIDAD -> setOf(ExerciseType.MOVILIDAD, ExerciseType.RECUPERACION)
        Goal.PARTO -> setOf(ExerciseType.MOVILIDAD, ExerciseType.FUERZA, ExerciseType.CORE)
    }

    /** Candidates in preferred order: goal types, focus, not done last time, balanced by muscle group. */
    fun rank(
        available: List<Exercise>,
        goal: Goal,
        focus: Focus = Focus.CUERPO_COMPLETO,
        recentSlugs: Set<String> = emptySet(),
        random: Random = Random.Default,
        pregnant: Boolean = false,
    ): List<Exercise> {
        val types = preferredTypes(goal)
        var pool = available.filter { it.type in types }
        if (focus != Focus.CUERPO_COMPLETO) {
            val focused = pool.filter { ex -> ex.muscleGroups.any { it in focus.muscleGroups } }
            if (focused.isNotEmpty()) pool = focused
        }
        if (pool.isEmpty()) pool = available
        if (pool.isEmpty()) return emptyList()

        // Prefer exercises not done last time, but fall back to them if the pool is small.
        val fresh = pool.filterNot { it.slug in recentSlugs }.shuffled(random)
        val repeated = pool.filter { it.slug in recentSlugs }.shuffled(random)

        // Round-robin over primary muscle groups for balance.
        var ordered = roundRobin(fresh) + roundRobin(repeated)
        // Labour preparation (and pregnancy mode in general): about 2 of every 3 picks are labour-prep exercises.
        if (goal == Goal.PARTO || pregnant) ordered = prioritizeLabourPrep(ordered)
        return ordered
    }

    fun suggest(
        available: List<Exercise>,
        goal: Goal,
        level: Level,
        restSeconds: Int,
        durationMin: Int,
        focus: Focus = Focus.CUERPO_COMPLETO,
        recentSlugs: Set<String> = emptySet(),
        random: Random = Random.Default,
        pregnant: Boolean = false,
    ): List<Exercise> {
        val budget = durationMin * 60
        val ordered = rank(available, goal, focus, recentSlugs, random, pregnant)
        if (ordered.isEmpty()) return emptyList()
        val chosen = mutableListOf<Exercise>()
        for (ex in ordered) {
            val candidate = chosen + ex
            val total = PlanAlgorithm.totalSeconds(candidate.map { PlanAlgorithm.assign(it, goal, level, pregnant) }, restSeconds)
            if (total <= budget * 1.1) chosen += ex
            if (total >= budget * 0.9) break
        }
        if (chosen.isEmpty()) chosen += ordered.first()
        return chosen
    }

    /** Circuit: exactly [count] exercises (or all available if fewer), balanced the same way. */
    fun suggestCount(
        available: List<Exercise>,
        goal: Goal,
        count: Int,
        focus: Focus = Focus.CUERPO_COMPLETO,
        recentSlugs: Set<String> = emptySet(),
        random: Random = Random.Default,
        pregnant: Boolean = false,
    ): List<Exercise> = rank(available, goal, focus, recentSlugs, random, pregnant).take(count.coerceAtLeast(1))

    private fun prioritizeLabourPrep(exercises: List<Exercise>): List<Exercise> {
        val prep = ArrayDeque(exercises.filter { it.pregnancy == Pregnancy.PARTO })
        val other = ArrayDeque(exercises.filter { it.pregnancy != Pregnancy.PARTO })
        val out = mutableListOf<Exercise>()
        while (prep.isNotEmpty() || other.isNotEmpty()) {
            repeat(2) { prep.removeFirstOrNull()?.let(out::add) }
            other.removeFirstOrNull()?.let(out::add)
        }
        return out
    }

    private fun roundRobin(exercises: List<Exercise>): List<Exercise> {
        val buckets = exercises.groupBy { it.muscleGroups.firstOrNull() ?: "otros" }
            .values.map { ArrayDeque(it) }
        val out = mutableListOf<Exercise>()
        while (buckets.any { it.isNotEmpty() }) {
            for (b in buckets) b.removeFirstOrNull()?.let(out::add)
        }
        return out
    }
}
