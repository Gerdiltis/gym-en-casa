package com.gymencasa.ui.plan

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.ui.components.ExerciseThumb
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.muscleList
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUi(val plan: DayPlan? = null, val usedFallback: Boolean = false)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val draftStore: DayDraftStore,
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    val ui: StateFlow<PlanUi> = draftStore.draft.map { PlanUi(it.plan, it.usedFallback) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlanUi(draftStore.draft.value.plan, draftStore.draft.value.usedFallback))

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

    fun saveRoutine(name: String, done: () -> Unit) {
        val plan = ui.value.plan ?: return
        viewModelScope.launch { workouts.saveRoutine(name.ifBlank { plan.title }, plan.copy(title = name)); done() }
    }
}

@Composable
fun PlanScreen(nav: Navigator, vm: PlanViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val plan = ui.plan ?: return
    var saveOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.plan_routine_saved)
    val totalMin = (PlanAlgorithm.totalSeconds(plan.items, plan.restSeconds) + 59) / 60

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(plan.title, subtitle = stringResource(R.string.plan_title)) {
                IconButton(onClick = { saveOpen = true }) { Icon(Icons.Filled.BookmarkAdd, stringResource(R.string.plan_save_routine)) }
            }
            Text(stringResource(R.string.plan_total, totalMin, plan.durationTargetMin), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.plan_rest, plan.restSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (totalMin > plan.durationTargetMin) {
                Text(stringResource(R.string.plan_over_time, totalMin - plan.durationTargetMin), color = MaterialTheme.colorScheme.error)
            }
            if (ui.usedFallback) {
                Text(stringResource(R.string.home_ai_fallback), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            if (plan.items.isEmpty()) item { Text(stringResource(R.string.plan_empty)) }
            itemsIndexed(plan.items, key = { _, it -> it.exercise.slug }) { i, item ->
                PlanItemCard(
                    index = i, item = item, thumb = vm.thumb(item), isFirst = i == 0, isLast = i == plan.items.lastIndex,
                    onSets = { vm.changeSets(i, it) }, onSeconds = { vm.changeSeconds(i, it) },
                    onMove = { vm.move(i, it) }, onRemove = { vm.remove(i) },
                )
            }
        }
        Button(
            onClick = { nav.go(Route.Session) },
            enabled = plan.items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(60.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.plan_start).uppercase(), style = MaterialTheme.typography.titleLarge)
        }
    }
    SnackbarHost(snackbar)

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
private fun PlanItemCard(
    index: Int, item: PlannedExercise, thumb: String?, isFirst: Boolean, isLast: Boolean,
    onSets: (Int) -> Unit, onSeconds: (Int) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                ExerciseThumb(thumb, size = 52.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(muscleList(item.exercise.muscleGroups), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.plan_move_up)) }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.plan_move_down)) }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.plan_remove)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(stringResource(R.string.plan_sets), "${item.sets}", { onSets(-1) }, { onSets(1) })
                Stepper(stringResource(R.string.plan_seconds), stringResource(R.string.seconds_short, item.workSeconds), { onSeconds(-5) }, { onSeconds(5) })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, null) }
            Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, null) }
        }
    }
}
