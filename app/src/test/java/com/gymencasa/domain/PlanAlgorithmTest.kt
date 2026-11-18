package com.gymencasa.domain

import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import com.gymencasa.domain.model.WorkoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAlgorithmTest {
    private val squat = ex("sentadilla")

    @Test
    fun `base doses per goal for intermediate`() {
        assertEquals(4 to 40, PlanAlgorithm.assign(squat, Goal.FUERZA, Level.INTERMEDIO).let { it.sets to it.workSeconds })
        assertEquals(3 to 30, PlanAlgorithm.assign(squat, Goal.CARDIO, Level.INTERMEDIO).let { it.sets to it.workSeconds })
        assertEquals(3 to 45, PlanAlgorithm.assign(squat, Goal.TONIFICAR, Level.INTERMEDIO).let { it.sets to it.workSeconds })
        assertEquals(2 to 40, PlanAlgorithm.assign(squat, Goal.MOVILIDAD, Level.INTERMEDIO).let { it.sets to it.workSeconds })
    }

    @Test
    fun `level adjusts sets and seconds`() {
        val beginner = PlanAlgorithm.assign(squat, Goal.FUERZA, Level.PRINCIPIANTE)
        assertEquals(3, beginner.sets); assertEquals(30, beginner.workSeconds)
        val advanced = PlanAlgorithm.assign(squat, Goal.FUERZA, Level.AVANZADO)
        assertEquals(5, advanced.sets); assertEquals(50, advanced.workSeconds)
        // never below the minimum number of sets
        assertEquals(2, PlanAlgorithm.assign(squat, Goal.MOVILIDAD, Level.PRINCIPIANTE).sets)
    }

    @Test
    fun `unilateral doubles work and recovery keeps gentle dose`() {
        assertEquals(90, PlanAlgorithm.assign(ex("zancada", unilateral = true), Goal.TONIFICAR, Level.INTERMEDIO).workSeconds)
        val foam = PlanAlgorithm.assign(ex("foam", type = ExerciseType.RECUPERACION), Goal.FUERZA, Level.INTERMEDIO)
        assertEquals(2, foam.sets); assertEquals(40, foam.workSeconds)
    }

    @Test
    fun `estimate uses global rest`() {
        val item = PlanAlgorithm.assign(squat, Goal.FUERZA, Level.INTERMEDIO) // 4 x 40
        assertEquals(10 + 160 + 3 * 30 + 15, PlanAlgorithm.estimateSeconds(item, restSeconds = 30))
        assertEquals(10 + 160 + 3 * 60 + 15, PlanAlgorithm.estimateSeconds(item, restSeconds = 60))
    }

    @Test
    fun `plan is trimmed to fit the duration`() {
        val plan = PlanAlgorithm.build(catalog.take(10), Goal.FUERZA, Level.INTERMEDIO, 30, 15, WorkoutMode.AI, "t")
        assertTrue(PlanAlgorithm.totalSeconds(plan.items, 30) <= 15 * 60)
        assertTrue(plan.items.all { it.sets >= PlanAlgorithm.MIN_SETS })
        assertEquals(30, plan.restSeconds)
    }

    @Test
    fun `manual plans keep the user's exercises`() {
        val picks = catalog.take(10)
        val plan = PlanAlgorithm.build(picks, Goal.FUERZA, Level.INTERMEDIO, 30, 10, WorkoutMode.MANUAL, "t", allowDrop = false)
        assertEquals(10, plan.items.size)
        assertTrue(plan.items.all { it.sets == PlanAlgorithm.MIN_SETS })
    }
}
