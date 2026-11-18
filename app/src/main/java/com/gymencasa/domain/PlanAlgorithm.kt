package com.gymencasa.domain

import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.domain.model.WorkoutMode

/**
 * Deterministic sets / work-time assignment. Rest between sets is never decided here:
 * it always comes from the user's global setting.
 */
object PlanAlgorithm {
    const val PREVIEW_SECONDS = 10
    const val TRANSITION_SECONDS = 15
    const val MIN_SETS = 2
    const val MAX_SETS = 6
    const val MIN_WORK_SECONDS = 20

    private data class Base(val sets: Int, val work: Int)

    private fun baseFor(goal: Goal): Base = when (goal) {
        Goal.FUERZA -> Base(4, 40)
        Goal.CARDIO -> Base(3, 30)
        Goal.TONIFICAR -> Base(3, 45)
        Goal.MOVILIDAD -> Base(2, 40)
    }

    fun assign(exercise: Exercise, goal: Goal, level: Level): PlannedExercise {
        // Mobility / foam-roller work keeps its own gentle dose whatever the day's goal.
        val effectiveGoal = when (exercise.type) {
            ExerciseType.MOVILIDAD, ExerciseType.RECUPERACION -> Goal.MOVILIDAD
            else -> goal
        }
        val base = baseFor(effectiveGoal)
        val (setDelta, workDelta) = when (level) {
            Level.PRINCIPIANTE -> -1 to -10
            Level.INTERMEDIO -> 0 to 0
            Level.AVANZADO -> 1 to 10
        }
        val sets = (base.sets + setDelta).coerceIn(MIN_SETS, MAX_SETS)
        var work = (base.work + workDelta).coerceAtLeast(MIN_WORK_SECONDS)
        if (exercise.unilateral) work *= 2
        return PlannedExercise(exercise, sets, work)
    }

    fun estimateSeconds(item: PlannedExercise, restSeconds: Int): Int =
        PREVIEW_SECONDS + item.sets * item.workSeconds + restSeconds * (item.sets - 1) + TRANSITION_SECONDS

    fun totalSeconds(items: List<PlannedExercise>, restSeconds: Int): Int =
        items.sumOf { estimateSeconds(it, restSeconds) }

    /**
     * Builds the day's plan from [exercises] ordered by priority (first = most important).
     * Trims sets (never below [MIN_SETS]) and then, if [allowDrop], drops the lowest-priority
     * exercises until the plan fits in [durationMin].
     */
    fun build(
        exercises: List<Exercise>,
        goal: Goal,
        level: Level,
        restSeconds: Int,
        durationMin: Int,
        mode: WorkoutMode,
        title: String,
        allowDrop: Boolean = true,
    ): DayPlan {
        val items = exercises.map { assign(it, goal, level) }.toMutableList()
        fit(items, restSeconds, durationMin * 60, allowDrop)
        return DayPlan(title, goal, mode, durationMin, restSeconds, items)
    }

    /** Re-fits an already built (possibly user-edited) plan into its duration target. */
    fun refit(plan: DayPlan, allowDrop: Boolean): DayPlan {
        val items = plan.items.toMutableList()
        fit(items, plan.restSeconds, plan.durationTargetMin * 60, allowDrop)
        return plan.copy(items = items)
    }

    private fun fit(items: MutableList<PlannedExercise>, rest: Int, budget: Int, allowDrop: Boolean) {
        while (totalSeconds(items, rest) > budget) {
            // Remove a set from the exercise that has the most sets, preferring lower priority (later) ones.
            val idx = items.indices.reversed()
                .filter { items[it].sets > MIN_SETS }
                .maxByOrNull { items[it].sets }
            if (idx != null) {
                items[idx] = items[idx].copy(sets = items[idx].sets - 1)
            } else if (allowDrop && items.size > 1) {
                items.removeAt(items.lastIndex)
            } else {
                break
            }
        }
    }
}
