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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

    val showing = if (state.phase == Phase.AWAIT_NEXT) state.next?.exercise ?: state.current.exercise else state.current.exercise
    val remainingMs = state.remainingMs(ui.nowMs)
    val secondsLeft = ((remainingMs + 999) / 1000).toInt()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: progress through the session + close.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.session_exercise_of, state.exerciseIndex + 1, state.plan.items.size).uppercase(),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { askQuit() }) { Icon(Icons.Filled.Close, stringResource(R.string.session_quit_confirm)) }
        }
        LinearProgressIndicator(
            progress = { (state.exerciseIndex + if (state.phase == Phase.AWAIT_NEXT) 1f else 0f) / state.plan.items.size },
            modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(Modifier.height(12.dp))
        Text(showing.name.uppercase(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(muscleList(showing.muscleGroups), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        ExerciseVideo(
            vm.videoUri(showing), vm.thumbUri(showing),
            Modifier.fillMaxWidth(if (state.phase == Phase.AWAIT_NEXT) 0.75f else 0.85f),
            playing = !state.isPaused,
        )
        Spacer(Modifier.height(16.dp))

        when (state.phase) {
            Phase.PREVIEW, Phase.WORK, Phase.REST -> {
                val label = when (state.phase) {
                    Phase.PREVIEW -> stringResource(R.string.session_get_ready)
                    Phase.WORK -> stringResource(R.string.session_work)
                    else -> stringResource(R.string.session_rest)
                }
                val ringColor = if (state.phase == Phase.REST) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                TimerRing(
                    progress = state.progress(ui.nowMs), color = ringColor,
                    pulse = state.phase != Phase.REST && secondsLeft <= 3 && !state.isPaused,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label.uppercase(), style = MaterialTheme.typography.titleLarge, color = ringColor)
                        Text("$secondsLeft", style = TimerStyle, color = MaterialTheme.colorScheme.onSurface)
                        if (state.phase == Phase.REST) {
                            Text(stringResource(R.string.session_next_set, state.setIndex + 2, state.current.sets),
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (state.phase == Phase.WORK) {
                            Text(stringResource(R.string.session_set_of, state.setIndex + 1, state.current.sets),
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.isPaused) Text(stringResource(R.string.session_paused), color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = vm::togglePause, modifier = Modifier.height(56.dp)) {
                        Icon(if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(if (state.isPaused) R.string.session_resume else R.string.session_pause).uppercase())
                    }
                    FilledTonalButton(onClick = vm::skip, modifier = Modifier.height(56.dp)) {
                        Icon(Icons.Filled.SkipNext, null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.session_skip).uppercase())
                    }
                }
            }

            Phase.AWAIT_NEXT -> {
                Text(stringResource(R.string.session_exercise_done).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                state.next?.let { n ->
                    Text(stringResource(R.string.session_next_up, n.exercise.name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.sets_by_seconds, n.sets, n.workSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = vm::startNext, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.session_start_next).uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }

            Phase.FINISHED -> Text(stringResource(R.string.summary_title).uppercase(), style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { keepGoing() },
            title = { Text(stringResource(R.string.session_quit_title)) },
            text = { Text(stringResource(R.string.session_quit_text)) },
            confirmButton = { TextButton(onClick = { confirmQuit = false; vm.quit() }) { Text(stringResource(R.string.session_quit_confirm)) } },
            dismissButton = { TextButton(onClick = { keepGoing() }) { Text(stringResource(R.string.session_keep_going)) } },
        )
    }
}

@Composable
private fun TimerRing(progress: Float, color: Color, pulse: Boolean, content: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "pulse")
    val glow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "glow",
    )
    Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + (if (pulse) 4.dp.toPx() else 0f)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            if (pulse) {
                drawCircle(color.copy(alpha = 0.15f + 0.25f * glow), radius = size.minDimension / 2)
            }
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * (1f - progress), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}
