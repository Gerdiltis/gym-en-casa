package com.gymencasa.domain.session

import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.PlannedExercise

/** ROUND_REST: circuit only, the longer rest between rounds. */
enum class Phase { PREVIEW, WORK, REST, ROUND_REST, AWAIT_NEXT, FINISHED }

data class CompletedSet(
    val exerciseSlug: String,
    val setIndex: Int,
    val plannedSeconds: Int,
    val skipped: Boolean,
)

/** Side effects the UI layer must perform (sound / vibration). */
sealed interface SessionEffect {
    /** Short tick during the last seconds of a countdown. */
    data class CountdownTick(val secondsLeft: Int) : SessionEffect
    /** A work set has just finished: long beep. */
    data object SetFinished : SessionEffect
    /** A timed phase has just started (work after preview / rest). */
    data object WorkStarted : SessionEffect
    data object SessionFinished : SessionEffect
}

/**
 * Immutable session state. Times are monotonic milliseconds (SystemClock.elapsedRealtime in the app),
 * so the timer never drifts and survives the screen being briefly off.
 */
data class SessionState(
    val plan: DayPlan,
    val exerciseIndex: Int = 0,
    val setIndex: Int = 0,
    /** Circuit only: current round, 0-based. In a circuit, during REST / ROUND_REST [current] is the exercise coming next. */
    val round: Int = 0,
    val phase: Phase = Phase.PREVIEW,
    val phaseStartMs: Long,
    val phaseDurationMs: Long,
    val pausedAtMs: Long? = null,
    val lastCountdownSecond: Int? = null,
    val completed: List<CompletedSet> = emptyList(),
    val startedAtMs: Long,
) {
    val current: PlannedExercise get() = plan.items[exerciseIndex]
    val next: PlannedExercise? get() = plan.items.getOrNull(exerciseIndex + 1)
    val isPaused: Boolean get() = pausedAtMs != null
    val isTimed: Boolean get() = phase == Phase.PREVIEW || phase == Phase.WORK || phase == Phase.REST || phase == Phase.ROUND_REST
    val isCircuit: Boolean get() = plan.isCircuit

    fun remainingMs(nowMs: Long): Long {
        if (!isTimed) return 0
        val reference = pausedAtMs ?: nowMs
        return (phaseStartMs + phaseDurationMs - reference).coerceAtLeast(0)
    }

    fun progress(nowMs: Long): Float =
        if (!isTimed || phaseDurationMs == 0L) 1f
        else 1f - remainingMs(nowMs).toFloat() / phaseDurationMs
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())

object SessionStateMachine {
    const val PREVIEW_MS = PlanAlgorithm.PREVIEW_SECONDS * 1000L
    private const val COUNTDOWN_FROM = 3

    fun start(plan: DayPlan, nowMs: Long): SessionState {
        require(plan.items.isNotEmpty()) { "Plan without exercises" }
        return SessionState(
            plan = plan,
            phase = Phase.PREVIEW,
            phaseStartMs = nowMs,
            phaseDurationMs = PREVIEW_MS,
            startedAtMs = nowMs,
        )
    }

    /** Advances every phase whose time has expired. Several phases may elapse in one tick. */
    fun tick(state: SessionState, nowMs: Long): Transition {
        if (state.isPaused || !state.isTimed) return Transition(state)
        var s = state
        val effects = mutableListOf<SessionEffect>()
        while (s.isTimed && s.remainingMs(nowMs) == 0L) {
            val end = s.phaseStartMs + s.phaseDurationMs
            val t = completePhase(s, end, skipped = false)
            s = t.state
            effects += t.effects
        }
        // Countdown beeps before a timed effort ends; in a circuit also before the next exercise starts on its own.
        if (s.phase == Phase.WORK || s.phase == Phase.PREVIEW || (s.isCircuit && (s.phase == Phase.REST || s.phase == Phase.ROUND_REST))) {
            val secondsLeft = ((s.remainingMs(nowMs) + 999) / 1000).toInt()
            if (secondsLeft in 1..COUNTDOWN_FROM && s.lastCountdownSecond != secondsLeft) {
                effects += SessionEffect.CountdownTick(secondsLeft)
                s = s.copy(lastCountdownSecond = secondsLeft)
            }
        }
        return Transition(s, effects)
    }

    /** User accepted to start the next exercise. */
    fun startNext(state: SessionState, nowMs: Long): Transition {
        if (state.phase != Phase.AWAIT_NEXT) return Transition(state)
        return Transition(
            state.copy(
                exerciseIndex = state.exerciseIndex + 1,
                setIndex = 0,
                phase = Phase.PREVIEW,
                phaseStartMs = nowMs,
                phaseDurationMs = PREVIEW_MS,
                lastCountdownSecond = null,
            ),
        )
    }

    /** Ends the current timed phase now (skip preview, skip rest, or skip a work set). */
    fun skip(state: SessionState, nowMs: Long): Transition {
        if (!state.isTimed) return Transition(state)
        val resumed = resume(state, nowMs)
        return completePhase(resumed, nowMs, skipped = state.phase == Phase.WORK)
    }

    fun pause(state: SessionState, nowMs: Long): SessionState =
        if (state.isTimed && !state.isPaused) state.copy(pausedAtMs = nowMs) else state

    fun resume(state: SessionState, nowMs: Long): SessionState {
        val pausedAt = state.pausedAtMs ?: return state
        return state.copy(phaseStartMs = state.phaseStartMs + (nowMs - pausedAt), pausedAtMs = null)
    }

    /** Stops the session early; finished sets are kept. */
    fun finishEarly(state: SessionState): SessionState = state.copy(phase = Phase.FINISHED, pausedAtMs = null)

    private fun completePhase(s: SessionState, endMs: Long, skipped: Boolean): Transition = when (s.phase) {
        Phase.PREVIEW -> Transition(
            s.copy(phase = Phase.WORK, setIndex = 0, phaseStartMs = endMs,
                phaseDurationMs = s.current.workSeconds * 1000L, lastCountdownSecond = null),
            listOf(SessionEffect.WorkStarted),
        )

        Phase.WORK -> if (s.isCircuit) completeCircuitWork(s, endMs, skipped) else {
            val done = s.completed + CompletedSet(s.current.exercise.slug, s.setIndex, s.current.workSeconds, skipped)
            val effects = mutableListOf<SessionEffect>(SessionEffect.SetFinished)
            val next = when {
                s.setIndex < s.current.sets - 1 -> s.copy(
                    phase = Phase.REST, phaseStartMs = endMs,
                    phaseDurationMs = s.plan.restSeconds * 1000L, completed = done, lastCountdownSecond = null,
                )

                s.exerciseIndex < s.plan.items.lastIndex -> s.copy(
                    phase = Phase.AWAIT_NEXT, phaseStartMs = endMs, phaseDurationMs = 0, completed = done,
                )

                else -> {
                    effects += SessionEffect.SessionFinished
                    s.copy(phase = Phase.FINISHED, phaseStartMs = endMs, phaseDurationMs = 0, completed = done)
                }
            }
            Transition(next, effects)
        }

        Phase.REST, Phase.ROUND_REST -> if (s.isCircuit) Transition(
            s.copy(phase = Phase.WORK, phaseStartMs = endMs, phaseDurationMs = s.current.workSeconds * 1000L, lastCountdownSecond = null),
            listOf(SessionEffect.WorkStarted),
        ) else Transition(
            s.copy(phase = Phase.WORK, setIndex = s.setIndex + 1, phaseStartMs = endMs,
                phaseDurationMs = s.current.workSeconds * 1000L, lastCountdownSecond = null),
            listOf(SessionEffect.WorkStarted),
        )

        Phase.AWAIT_NEXT, Phase.FINISHED -> Transition(s)
    }

    /** Circuit: next exercise of the round after the short rest; after the last one, the round rest; then done. */
    private fun completeCircuitWork(s: SessionState, endMs: Long, skipped: Boolean): Transition {
        val done = s.completed + CompletedSet(s.current.exercise.slug, s.round, s.current.workSeconds, skipped)
        val lastOfRound = s.exerciseIndex >= s.plan.items.lastIndex
        val lastRound = s.round >= s.plan.rounds - 1
        return when {
            !lastOfRound -> Transition(
                s.copy(phase = Phase.REST, exerciseIndex = s.exerciseIndex + 1, phaseStartMs = endMs,
                    phaseDurationMs = s.plan.restSeconds * 1000L, completed = done, lastCountdownSecond = null),
                listOf(SessionEffect.SetFinished),
            )
            !lastRound -> Transition(
                s.copy(phase = Phase.ROUND_REST, exerciseIndex = 0, round = s.round + 1, phaseStartMs = endMs,
                    phaseDurationMs = s.plan.roundRestSeconds.coerceAtLeast(5) * 1000L, completed = done, lastCountdownSecond = null),
                listOf(SessionEffect.SetFinished),
            )
            else -> Transition(
                s.copy(phase = Phase.FINISHED, phaseStartMs = endMs, phaseDurationMs = 0, completed = done),
                listOf(SessionEffect.SetFinished, SessionEffect.SessionFinished),
            )
        }
    }
}
