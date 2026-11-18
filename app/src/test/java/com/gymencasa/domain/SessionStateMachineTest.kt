package com.gymencasa.domain

import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.domain.session.Phase
import com.gymencasa.domain.session.SessionEffect
import com.gymencasa.domain.session.SessionStateMachine as SM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {
    private val plan = DayPlan(
        title = "t", goal = Goal.FUERZA, mode = WorkoutMode.MANUAL, durationTargetMin = 10, restSeconds = 20,
        items = listOf(PlannedExercise(ex("a"), sets = 2, workSeconds = 30), PlannedExercise(ex("b"), sets = 2, workSeconds = 40)),
    )

    @Test
    fun `full flow preview - work - rest - await - finish`() {
        var s = SM.start(plan, nowMs = 0)
        assertEquals(Phase.PREVIEW, s.phase)
        assertEquals(10_000, s.remainingMs(0))

        var t = SM.tick(s, 10_000); s = t.state
        assertEquals(Phase.WORK, s.phase); assertEquals(0, s.setIndex)
        assertTrue(SessionEffect.WorkStarted in t.effects)

        t = SM.tick(s, 40_000); s = t.state
        assertEquals(Phase.REST, s.phase)
        assertTrue(SessionEffect.SetFinished in t.effects)
        assertEquals(20_000, s.remainingMs(40_000))

        s = SM.tick(s, 60_000).state
        assertEquals(Phase.WORK, s.phase); assertEquals(1, s.setIndex)

        t = SM.tick(s, 90_000); s = t.state
        assertEquals(Phase.AWAIT_NEXT, s.phase)
        // waits for the user, time does not advance it
        assertEquals(Phase.AWAIT_NEXT, SM.tick(s, 999_999).state.phase)

        s = SM.startNext(s, 100_000).state
        assertEquals(Phase.PREVIEW, s.phase); assertEquals(1, s.exerciseIndex)

        s = SM.tick(s, 110_000).state // work set 1
        s = SM.tick(s, 150_000).state // rest
        s = SM.tick(s, 170_000).state // work set 2
        t = SM.tick(s, 210_000); s = t.state
        assertEquals(Phase.FINISHED, s.phase)
        assertTrue(SessionEffect.SessionFinished in t.effects)
        assertEquals(4, s.completed.size)
        assertTrue(s.completed.none { it.skipped })
    }

    @Test
    fun `several expired phases are processed in one tick without drift`() {
        val s = SM.start(plan, 0)
        // preview 10s + work 30s + rest 20s = 60s -> second work set started exactly at 60s
        val after = SM.tick(s, 65_000).state
        assertEquals(Phase.WORK, after.phase); assertEquals(1, after.setIndex)
        assertEquals(25_000, after.remainingMs(65_000))
    }

    @Test
    fun `pause freezes the timer`() {
        var s = SM.tick(SM.start(plan, 0), 10_000).state // work, 30s
        s = SM.pause(s, 15_000)
        assertEquals(25_000, s.remainingMs(50_000))
        assertEquals(Phase.WORK, SM.tick(s, 50_000).state.phase)
        s = SM.resume(s, 50_000)
        assertEquals(25_000, s.remainingMs(50_000))
    }

    @Test
    fun `skip work marks the set as skipped`() {
        var s = SM.tick(SM.start(plan, 0), 10_000).state
        s = SM.skip(s, 12_000).state
        assertEquals(Phase.REST, s.phase)
        assertTrue(s.completed.single().skipped)
    }

    @Test
    fun `countdown ticks once per second in the last three seconds`() {
        var s = SM.tick(SM.start(plan, 0), 10_000).state // work 30s ends at 40s
        val ticks = mutableListOf<Int>()
        var now = 36_000L
        while (now < 40_000) {
            val t = SM.tick(s, now); s = t.state
            t.effects.filterIsInstance<SessionEffect.CountdownTick>().forEach { ticks += it.secondsLeft }
            now += 100
        }
        assertEquals(listOf(3, 2, 1), ticks)
    }
}
