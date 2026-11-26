package com.gymencasa.ui.session

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.gymencasa.ui.components.FloatingPill
import com.gymencasa.ui.components.GradientButton
import com.gymencasa.ui.components.PillShape
import com.gymencasa.ui.components.RoundIconButton
import com.gymencasa.ui.components.pageBackground
import com.gymencasa.ui.components.softShadow
import com.gymencasa.ui.theme.LocalGymExtras
import com.gymencasa.ui.theme.SectionStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.UserSettings
import com.gymencasa.domain.session.Phase
import com.gymencasa.domain.session.SessionState
import com.gymencasa.domain.session.SessionStateMachine
import com.gymencasa.ui.components.ExerciseVideo
import com.gymencasa.ui.muscleList
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import com.gymencasa.ui.theme.TimerStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionUi(val state: SessionState? = null, val nowMs: Long = 0, val savedWorkoutId: String? = null)

@HiltViewModel
class SessionViewModel @Inject constructor(
    draftStore: DayDraftStore,
    private val settings: SettingsRepository,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val beeper: Beeper,
) : ViewModel() {
    private val startWallMs = System.currentTimeMillis()
    private val _ui = MutableStateFlow(
        SessionUi(draftStore.draft.value.plan?.takeIf { it.items.isNotEmpty() }?.let { SessionStateMachine.start(it, now()) }, now()),
    )
    val ui: StateFlow<SessionUi> = _ui
    private val userSettings = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    private var saving = false

    init {
        viewModelScope.launch {
            while (isActive) {
                val s = _ui.value.state ?: break
                if (s.phase == Phase.FINISHED) { save(s, completed = true); break }
                val t = SessionStateMachine.tick(s, now())
                t.effects.forEach { beeper.play(it, userSettings.value.soundEnabled, userSettings.value.vibrationEnabled) }
                _ui.value = _ui.value.copy(state = t.state, nowMs = now())
                delay(100)
            }
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    fun videoUri(ex: Exercise) = exercises.videoUri(ex)
    fun thumbUri(ex: Exercise) = exercises.thumbUri(ex)

    private fun mutate(f: (SessionState) -> SessionState) {
        val s = _ui.value.state ?: return
        _ui.value = _ui.value.copy(state = f(s), nowMs = now())
    }

    fun togglePause() = mutate { if (it.isPaused) SessionStateMachine.resume(it, now()) else SessionStateMachine.pause(it, now()) }
    fun skip() = mutate { s ->
        val t = SessionStateMachine.skip(s, now())
        t.effects.forEach { beeper.play(it, userSettings.value.soundEnabled, userSettings.value.vibrationEnabled) }
        t.state
    }
    fun startNext() = mutate { SessionStateMachine.startNext(it, now()).state }

    fun quit() {
        val s = _ui.value.state ?: return
        val finished = SessionStateMachine.finishEarly(s)
        _ui.value = _ui.value.copy(state = finished)
        save(finished, completed = false)
    }

    private fun save(s: SessionState, completed: Boolean) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val id = workouts.saveWorkout(s.plan, startWallMs, System.currentTimeMillis(), s.completed, completed)
            _ui.value = _ui.value.copy(savedWorkoutId = id)
        }
    }

    override fun onCleared() { beeper.release() }
}

@Composable
fun SessionScreen(nav: Navigator, vm: SessionViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui.state ?: return
    var confirmQuit by remember { mutableStateOf(false) }
    // The timer stops while the "end session?" dialog is open, and resumes if we paused it.
    var pausedByDialog by remember { mutableStateOf(false) }
    val askQuit = {
        if (state.isTimed && !state.isPaused) { vm.togglePause(); pausedByDialog = true }
        confirmQuit = true
    }
    val keepGoing = {
        confirmQuit = false
        if (pausedByDialog) { vm.togglePause(); pausedByDialog = false }
    }

    // Keep the screen on for the whole session.
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    LaunchedEffect(ui.savedWorkoutId) {
        ui.savedWorkoutId?.let { nav.resetTo(Route.Home, Route.Summary(it)) }
    }
    BackHandler(enabled = state.phase != Phase.FINISHED) { askQuit() }

    val awaiting = state.phase == Phase.AWAIT_NEXT
    val showing = if (awaiting) state.next?.exercise ?: state.current.exercise else state.current.exercise
    val remainingMs = state.remainingMs(ui.nowMs)
    val secondsLeft = ((remainingMs + 999) / 1000).toInt()
    val total = state.plan.items.size

    Box(Modifier.fillMaxSize().pageBackground()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // "VOICE MODE"-style pill with the progress through the session
            Text(
                (if (state.isCircuit) stringResource(R.string.circuit_header, state.round + 1, state.plan.rounds, state.exerciseIndex + 1, total)
                else stringResource(R.string.session_exercise_of, state.exerciseIndex + 1, total)).uppercase(),
                style = SectionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp).softShadow(PillShape, 8.dp).clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 9.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { i ->
                    val done = i < state.exerciseIndex || (i == state.exerciseIndex && awaiting)
                    Box(
                        Modifier.size(width = if (i == state.exerciseIndex) 18.dp else 8.dp, height = 6.dp).clip(PillShape)
                            .background(if (done || i == state.exerciseIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ExerciseVideo(
                vm.videoUri(showing), vm.thumbUri(showing),
                Modifier.fillMaxWidth(if (awaiting) 0.7f else 0.62f),
                playing = !state.isPaused,
            )

            when (state.phase) {
                Phase.PREVIEW, Phase.WORK, Phase.REST, Phase.ROUND_REST -> {
                    val rest = state.phase == Phase.REST || state.phase == Phase.ROUND_REST
                    Spacer(Modifier.height(20.dp))
                    TimerRing(
                        progress = state.progress(ui.nowMs), rest = rest,
                        pulse = !rest && secondsLeft <= 3 && !state.isPaused,
                    ) {
                        Text("$secondsLeft", style = TimerStyle, color = MaterialTheme.colorScheme.onBackground)
                    }
                    val status = if (state.isCircuit) when (state.phase) {
                        Phase.PREVIEW -> stringResource(R.string.session_get_ready)
                        Phase.WORK -> stringResource(R.string.session_work) + " · " + stringResource(R.string.circuit_round_of, state.round + 1, state.plan.rounds)
                        Phase.ROUND_REST -> stringResource(R.string.circuit_round_rest_phase) + " · " +
                            stringResource(R.string.circuit_next_round, state.round + 1, state.plan.rounds)
                        else -> stringResource(R.string.session_rest)
                    } else when (state.phase) {
                        Phase.PREVIEW -> stringResource(R.string.session_get_ready)
                        Phase.WORK -> stringResource(R.string.session_work) + " · " + stringResource(R.string.session_set_of, state.setIndex + 1, state.current.sets)
                        else -> stringResource(R.string.session_rest) + " · " + stringResource(R.string.session_next_set, state.setIndex + 2, state.current.sets)
                    }
                    Text(
                        if (state.isPaused) stringResource(R.string.session_paused) else status,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (rest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    val upcoming = state.isCircuit && rest
                    Text(if (upcoming) stringResource(R.string.circuit_next, showing.name) else showing.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    Text(muscleList(showing.muscleGroups), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }

                Phase.AWAIT_NEXT -> {
                    Text(stringResource(R.string.session_exercise_done), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp))
                    state.next?.let { n ->
                        Text(stringResource(R.string.session_next_up, n.exercise.name), style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            muscleList(n.exercise.muscleGroups) + " · " + stringResource(R.string.sets_by_seconds, n.sets, n.workSeconds),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                    GradientButton(
                        stringResource(R.string.session_start_next), icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp), height = 60.dp, onClick = vm::startNext,
                    )
                }

                Phase.FINISHED -> Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
            }
            Spacer(Modifier.height(130.dp))
        }

        // Floating controls: close · big pause/play · skip
        FloatingPill(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)) {
            RoundIconButton(Icons.Filled.Close, stringResource(R.string.session_quit_confirm), size = 48.dp, onClick = askQuit)
            if (state.isTimed) {
                Spacer(Modifier.size(18.dp))
                Box(
                    Modifier.size(68.dp)
                        .shadow(14.dp, CircleShape, clip = false, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                        .clip(CircleShape).background(LocalGymExtras.current.heroGradient).clickable(onClick = vm::togglePause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(if (state.isPaused) R.string.session_resume else R.string.session_pause),
                        tint = Color.White, modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.size(18.dp))
                RoundIconButton(Icons.Filled.SkipNext, stringResource(R.string.session_skip), size = 48.dp, onClick = vm::skip)
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { keepGoing() },
            title = { Text(stringResource(R.string.session_quit_title)) },
            text = { Text(stringResource(R.string.session_quit_text)) },
            confirmButton = { TextButton(onClick = { confirmQuit = false; pausedByDialog = false; vm.quit() }) { Text(stringResource(R.string.session_quit_confirm)) } },
            dismissButton = { TextButton(onClick = { keepGoing() }) { Text(stringResource(R.string.session_keep_going)) } },
        )
    }
}

/** Big ring timer: red gradient while working, grey while resting, pulsing red in the last 3 s. */
@Composable
private fun TimerRing(progress: Float, rest: Boolean, pulse: Boolean, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val gradient = LocalGymExtras.current.heroGradient
    val restColor = MaterialTheme.colorScheme.outline
    val glowColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "glow",
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            if (pulse) drawCircle(glowColor.copy(alpha = 0.10f + 0.18f * glow), radius = size.minDimension / 2)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            val sweep = 360f * (1f - progress)
            if (rest) drawArc(restColor, -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            else drawArc(gradient, -90f, sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}
