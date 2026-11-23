package com.gymencasa.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraft
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.PlanGenerator
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.ui.components.FloatingPill
import com.gymencasa.ui.components.GradientButton
import com.gymencasa.ui.components.PillButton
import com.gymencasa.ui.components.RoundIconButton
import com.gymencasa.ui.components.TopPillBar
import com.gymencasa.ui.components.softShadow
import com.gymencasa.ui.labelRes
import com.gymencasa.ui.muscleDotColor
import com.gymencasa.ui.muscleList
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import com.gymencasa.ui.theme.LocalGymExtras
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PlanUi(val draft: DayDraft = DayDraft(), val regenerating: Boolean = false)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val draftStore: DayDraftStore,
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    private val generator: PlanGenerator,
) : ViewModel() {
    private val regenerating = MutableStateFlow(false)
    val ui: StateFlow<PlanUi> = combine(draftStore.draft, regenerating) { d, r -> PlanUi(d, r) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlanUi(draftStore.draft.value))

    fun thumb(item: PlannedExercise) = exercises.thumbUri(item.exercise)

    private fun edit(transform: (MutableList<PlannedExercise>) -> Unit) = draftStore.update { d ->
        val plan = d.plan ?: return@update d
        val items = plan.items.toMutableList().also(transform)
        d.copy(plan = plan.copy(items = items), selectedSlugs = items.map { it.exercise.slug })
    }

    fun changeSets(i: Int, delta: Int) = edit { it[i] = it[i].copy(sets = (it[i].sets + delta).coerceIn(1, PlanAlgorithm.MAX_SETS)) }
    fun changeSeconds(i: Int, delta: Int) = edit { it[i] = it[i].copy(workSeconds = (it[i].workSeconds + delta).coerceIn(10, 300)) }
    fun remove(i: Int) = edit { it.removeAt(i) }
    fun move(i: Int, delta: Int) = edit {
        val j = i + delta
        if (j in it.indices) { val tmp = it[i]; it[i] = it[j]; it[j] = tmp }
    }

    /** "Otra propuesta" for AI plans: asks again, avoiding the exercises currently shown. */
    fun regenerate(defaultTitle: String) {
        if (regenerating.value) return
        val current = draftStore.draft.value.plan?.items?.map { it.exercise.slug }?.toSet().orEmpty()
        regenerating.value = true
        viewModelScope.launch {
            try { generator.generateAi(defaultTitle, avoid = current) } finally { regenerating.value = false }
        }
    }

    fun saveRoutine(name: String, done: () -> Unit) {
        val plan = draftStore.draft.value.plan ?: return
        viewModelScope.launch { workouts.saveRoutine(name.ifBlank { plan.title }, plan.copy(title = name)); done() }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun PlanScreen(nav: Navigator, vm: PlanViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft = ui.draft
    val plan = draft.plan ?: return
    var saveOpen by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.plan_routine_saved)
    val totalMin = (PlanAlgorithm.totalSeconds(plan.items, plan.restSeconds) + 59) / 60
    val isAi = plan.mode == WorkoutMode.AI
    val goalLabel = stringResource(plan.goal.labelRes)
    val defaultTitle = stringResource(R.string.ai_title_default, goalLabel)
    val at = if (draft.generatedAt > 0) Instant.ofEpochMilli(draft.generatedAt).atZone(ZoneId.systemDefault()).format(timeFormat) else ""

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopPillBar(
                title = stringResource(R.string.plan_title),
                subtitle = stringResource(if (isAi) R.string.plan_subtitle_ai else R.string.plan_subtitle_manual),
                onBack = { nav.back() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                trailing = { RoundIconButton(Icons.Filled.BookmarkAdd, stringResource(R.string.plan_save_routine)) { saveOpen = true } },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                if (at.isNotEmpty()) {
                    Text(stringResource(R.string.plan_today_at, at), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
                }
                // User's request as a red bubble
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        stringResource(R.string.plan_request, goalLabel, plan.durationTargetMin, stringResource(draft.focus.labelRes)),
                        color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                            .background(LocalGymExtras.current.heroGradient)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                // Assistant answer
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                    Column(
                        Modifier.weight(1f).softShadow(shape).clip(shape).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                    ) {
                        Text(
                            buildAnnotatedString {
                                append(stringResource(R.string.plan_answer_ready) + " ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(plan.title) }
                                append(". " + stringResource(R.string.plan_answer_summary, plan.items.size, totalMin, plan.restSeconds))
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (draft.usedFallback) {
                            Text(stringResource(R.string.home_ai_fallback), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (totalMin > plan.durationTargetMin) {
                            Text(stringResource(R.string.plan_over_time, totalMin - plan.durationTargetMin), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                        }
                        Column(
                            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(LocalGymExtras.current.chip).padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            if (plan.items.isEmpty()) Text(stringResource(R.string.plan_empty), modifier = Modifier.padding(12.dp))
                            plan.items.forEachIndexed { i, item ->
                                val key = item.exercise.slug
                                PlanRow(
                                    item = item, expanded = expanded == key, last = i == plan.items.lastIndex, first = i == 0,
                                    onToggle = { expanded = if (expanded == key) null else key },
                                    onSets = { vm.changeSets(i, it) }, onSeconds = { vm.changeSeconds(i, it) },
                                    onMove = { vm.move(i, it) }, onRemove = { vm.remove(i); expanded = null },
                                )
                            }
                        }
                        Text(stringResource(R.string.plan_tap_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Spacer(Modifier.height(110.dp))
            }
        }
        // Floating action pill, always visible (like the reference's bottom bar)
        FloatingPill(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
            GradientButton(stringResource(R.string.plan_start), icon = Icons.Filled.PlayArrow, height = 52.dp,
                enabled = plan.items.isNotEmpty(), modifier = Modifier.weight(1f)) { nav.go(Route.Session) }
            Spacer(Modifier.width(8.dp))
            if (ui.regenerating) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp) }
            } else {
                PillButton(stringResource(if (isAi) R.string.plan_another else R.string.plan_change), icon = Icons.Filled.Refresh, height = 52.dp) {
                    if (isAi) vm.regenerate(defaultTitle) else nav.go(Route.ManualSelect)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }

    if (saveOpen) {
        var name by remember { mutableStateOf(plan.title) }
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text(stringResource(R.string.plan_save_routine)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.plan_routine_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveRoutine(name) { scope.launch { snackbar.showSnackbar(savedMsg) } }
                    saveOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PlanRow(
    item: PlannedExercise, expanded: Boolean, first: Boolean, last: Boolean,
    onToggle: () -> Unit, onSets: (Int) -> Unit, onSeconds: (Int) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(muscleDotColor(item.exercise.muscleGroups.firstOrNull())))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(muscleList(item.exercise.muscleGroups), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                stringResource(R.string.sets_by_seconds, item.sets, item.workSeconds),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper(stringResource(R.string.plan_sets), "${item.sets}", { onSets(-1) }, { onSets(1) })
                    Stepper(stringResource(R.string.plan_seconds), stringResource(R.string.seconds_short, item.workSeconds), { onSeconds(-5) }, { onSeconds(5) })
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    RoundIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.plan_move_up), size = 36.dp, container = MaterialTheme.colorScheme.surface, enabled = !first) { onMove(-1) }
                    RoundIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.plan_move_down), size = 36.dp, container = MaterialTheme.colorScheme.surface, enabled = !last) { onMove(1) }
                    RoundIconButton(Icons.Filled.Close, stringResource(R.string.plan_remove), size = 36.dp, container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.primary, onClick = onRemove)
                }
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Filled.Remove, null, size = 32.dp, container = MaterialTheme.colorScheme.surface, onClick = onMinus)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
            RoundIconButton(Icons.Filled.Add, null, size = 32.dp, container = MaterialTheme.colorScheme.surface, onClick = onPlus)
        }
    }
}
