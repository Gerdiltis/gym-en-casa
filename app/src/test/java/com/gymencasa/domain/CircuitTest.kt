package com.gymencasa.domain

import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.Pregnancy
import com.gymencasa.domain.model.SessionFormat
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.domain.session.Phase
import com.gymencasa.domain.session.SessionEffect
import com.gymencasa.domain.session.SessionStateMachine as SM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CircuitTest {
    // 2 exercises × 3 rounds, 30 s work, 10 s between exercises, 60 s between rounds
    private val plan = PlanAlgorithm.buildCircuit(
        listOf(ex("a"), ex("b")), Goal.TONIFICAR, workSeconds = 30, rounds = 3,
        restSeconds = 10, roundRestSeconds = 60, mode = WorkoutMode.MANUAL, title = "c",
    )

    @Test
    fun `buildCircuit makes one pass per exercise and computes the duration`() {
        assertEquals(SessionFormat.CIRCUIT, plan.format)
        assertEquals(3, plan.rounds)
        assertTrue(plan.items.all { it.sets == 1 && it.workSeconds == 30 })
        // preview 10 + 3 × (30+30+10) + 2 × 60 = 340 s
        assertEquals(340, PlanAlgorithm.planSeconds(plan))
        assertEquals(6, plan.durationTargetMin)
    }

    @Test
    fun `unilateral doubles and pregnancy caps seconds and rounds`() {
        val p = PlanAlgorithm.buildCircuit(listOf(ex("z", unilateral = true)), Goal.PARTO, 60, 8, 20, 60, WorkoutMode.MANUAL, "t", pregnant = true)
        assertEquals(PlanAlgorithm.PREGNANCY_MAX_ROUNDS, p.rounds)
        assertEquals(PlanAlgorithm.PREGNANCY_MAX_WORK_SECONDS * 2, p.items.single().workSeconds)
    }

    @Test
    fun `full circuit runs a, b, a, b, a, b with rests and round rests, no manual step`() {
        var s = SM.start(plan, 0)
        val trace = mutableListOf<String>()
        var now = 0L
        val effects = mutableListOf<SessionEffect>()
        while (s.phase != Phase.FINISHED && now < 1_000_000) {
            val t = SM.tick(s, now)
            effects += t.effects
            if (t.state.phase != s.phase || t.state.exerciseIndex != s.exerciseIndex) {
                trace += "${t.state.phase}:${t.state.current.exercise.slug}:${t.state.round}"
            }
            s = t.state
            assertFalse(s.phase == Phase.AWAIT_NEXT)
            now += 500
        }
        assertEquals(
            listOf(
                "WORK:a:0", "REST:b:0", "WORK:b:0", "ROUND_REST:a:1",
                "WORK:a:1", "REST:b:1", "WORK:b:1", "ROUND_REST:a:2",
                "WORK:a:2", "REST:b:2", "WORK:b:2", "FINISHED:b:2",
            ),
            trace,
        )
        assertEquals(listOf("a", "b", "a", "b", "a", "b"), s.completed.map { it.exerciseSlug })
        assertEquals(listOf(0, 0, 1, 1, 2, 2), s.completed.map { it.setIndex })
        assertEquals(6, effects.count { it == SessionEffect.SetFinished })
        assertTrue(SessionEffect.SessionFinished in effects)
        // finished at 340 s (±one tick)
        assertTrue(now in 340_000..341_000)
    }

    @Test
    fun `several phases elapse in one tick without drift`() {
        val s = SM.start(plan, 0)
        // preview 10 + a 30 + rest 10 = 50 s -> b working, 5 s in
        val after = SM.tick(s, 55_000).state
        assertEquals(Phase.WORK, after.phase)
        assertEquals("b", after.current.exercise.slug)
        assertEquals(25_000, after.remainingMs(55_000))
    }

    @Test
    fun `skip and pause work inside a circuit`() {
        var s = SM.tick(SM.start(plan, 0), 10_000).state          // working a
        s = SM.skip(s, 12_000).state
        assertEquals(Phase.REST, s.phase)
        assertTrue(s.completed.single().skipped)
        s = SM.pause(s, 13_000)
        assertEquals(Phase.REST, SM.tick(s, 60_000).state.phase)
        s = SM.resume(s, 60_000)
        assertEquals(9_000, s.remainingMs(60_000))
    }

    @Test
    fun `countdown also beeps before the next exercise starts`() {
        var s = SM.tick(SM.tick(SM.start(plan, 0), 10_000).state, 40_000).state  // rest before b, ends at 50 s
        val ticks = mutableListOf<Int>()
        var now = 46_000L
        while (now < 50_000) {
            val t = SM.tick(s, now); s = t.state
            t.effects.filterIsInstance<SessionEffect.CountdownTick>().forEach { ticks += it.secondsLeft }
            now += 100
        }
        assertEquals(listOf(3, 2, 1), ticks)
    }

    @Test
    fun `suggestCount returns exactly the requested number`() {
        val pool = (1..20).map { ex("e$it", muscles = listOf(listOf("piernas", "pecho", "core")[it % 3]), type = ExerciseType.FUERZA) }
        assertEquals(5, SuggestionEngine.suggestCount(pool, Goal.TONIFICAR, 5, random = Random(1)).size)
        assertEquals(20, SuggestionEngine.suggestCount(pool, Goal.TONIFICAR, 50, random = Random(1)).size)
        val prep = pool.mapIndexed { i, e -> if (i % 2 == 0) e.copy(pregnancy = Pregnancy.PARTO) else e }
        val picks = SuggestionEngine.suggestCount(prep, Goal.PARTO, 6, random = Random(2), pregnant = true)
        assertTrue(picks.count { it.pregnancy == Pregnancy.PARTO } >= 4)
    }
}
