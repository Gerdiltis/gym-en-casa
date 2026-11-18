package com.gymencasa.domain

import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SuggestionEngineTest {
    private val bigCatalog = (1..40).map { i ->
        ex("e$i", muscles = listOf(listOf("piernas", "pecho", "espalda", "core", "hombros")[i % 5]),
            type = if (i % 4 == 0) ExerciseType.CORE else ExerciseType.FUERZA)
    }

    @Test
    fun `suggestion fits duration within 10 percent`() {
        for (minutes in listOf(10, 15, 20, 30, 45, 60)) {
            val picks = SuggestionEngine.suggest(bigCatalog, Goal.TONIFICAR, Level.INTERMEDIO, 30, minutes, random = Random(1))
            val total = PlanAlgorithm.totalSeconds(picks.map { PlanAlgorithm.assign(it, Goal.TONIFICAR, Level.INTERMEDIO) }, 30)
            assertTrue("$minutes min -> $total s", total <= minutes * 60 * 1.1)
            assertTrue("$minutes min -> $total s", total >= minutes * 60 * 0.9 - 300) // within one exercise
        }
    }

    @Test
    fun `suggestion is balanced across muscle groups`() {
        val picks = SuggestionEngine.suggest(bigCatalog, Goal.TONIFICAR, Level.INTERMEDIO, 30, 20, random = Random(2))
        val groups = picks.map { it.muscleGroups.first() }
        assertTrue(groups.distinct().size >= minOf(5, picks.size))
    }

    @Test
    fun `avoids last workout when possible`() {
        val recent = bigCatalog.take(20).map { it.slug }.toSet()
        val picks = SuggestionEngine.suggest(bigCatalog, Goal.TONIFICAR, Level.INTERMEDIO, 30, 20, recentSlugs = recent, random = Random(3))
        assertFalse(picks.any { it.slug in recent })
    }

    @Test
    fun `mobility goal picks mobility and recovery only`() {
        val picks = SuggestionEngine.suggest(catalog, Goal.MOVILIDAD, Level.INTERMEDIO, 30, 10, random = Random(4))
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.all { it.type == ExerciseType.MOVILIDAD || it.type == ExerciseType.RECUPERACION })
    }

    @Test
    fun `focus filters muscle groups`() {
        val picks = SuggestionEngine.suggest(bigCatalog, Goal.FUERZA, Level.INTERMEDIO, 30, 15, focus = Focus.TREN_INFERIOR, random = Random(5))
        assertTrue(picks.all { it.muscleGroups.first() == "piernas" })
    }

    @Test
    fun `empty catalog gives empty suggestion`() {
        assertEquals(emptyList<Any>(), SuggestionEngine.suggest(emptyList(), Goal.FUERZA, Level.INTERMEDIO, 30, 15))
    }
}

class SuggestionEnginePregnancyTest {
    private val pool = (1..30).map { i ->
        ex("e$i", muscles = listOf(listOf("piernas", "gluteos", "espalda", "core", "aductores")[i % 5]),
            type = if (i % 3 == 0) ExerciseType.MOVILIDAD else ExerciseType.FUERZA,
            pregnancy = if (i % 2 == 0) com.gymencasa.domain.model.Pregnancy.PARTO else com.gymencasa.domain.model.Pregnancy.APTO)
    }

    @Test
    fun `labour goal prioritizes labour-prep exercises and fits duration`() {
        for (minutes in listOf(15, 20, 30)) {
            val picks = SuggestionEngine.suggest(pool, Goal.PARTO, Level.INTERMEDIO, 30, minutes, random = Random(minutes), pregnant = true)
            val prep = picks.count { it.pregnancy == com.gymencasa.domain.model.Pregnancy.PARTO }
            assertTrue("$minutes min: $prep of ${picks.size}", prep * 3 >= picks.size * 2 - 1)
            val total = PlanAlgorithm.totalSeconds(picks.map { PlanAlgorithm.assign(it, Goal.PARTO, Level.INTERMEDIO, true) }, 30)
            assertTrue(total <= minutes * 60 * 1.1)
        }
    }
}
