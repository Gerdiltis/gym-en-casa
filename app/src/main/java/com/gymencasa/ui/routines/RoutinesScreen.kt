package com.gymencasa.ui.routines

import com.gymencasa.ui.components.TopPillBar
import com.gymencasa.ui.components.GradientButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gymencasa.ui.components.CardListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gymencasa.data.local.RoutineEntity
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.Goal
import com.gymencasa.domain.model.PlannedExercise
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val draftStore: DayDraftStore,
) : ViewModel() {
    val routines: StateFlow<List<RoutineEntity>> = workouts.routines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Loads a saved routine as today's plan. Rest always comes from the current global setting. */
    fun use(id: String, onReady: () -> Unit) {
        viewModelScope.launch {
            val saved = workouts.routine(id) ?: return@launch
            val bySlug = exercises.all().associateBy { it.slug }
            val items = saved.items.mapNotNull { i -> bySlug[i.exerciseSlug]?.let { PlannedExercise(it, i.sets, i.workSeconds) } }
            if (items.isEmpty()) return@launch
            val plan = DayPlan(saved.routine.name, Goal.fromSlug(saved.routine.goal), WorkoutMode.MANUAL,
                saved.routine.durationTargetMin, settings.current().restBetweenSetsSeconds, items)
            draftStore.update { it.copy(plan = plan, durationMin = plan.durationTargetMin, goal = plan.goal, usedFallback = false, generatedAt = System.currentTimeMillis()) }
            onReady()
        }
    }

    fun delete(id: String) { viewModelScope.launch { workouts.deleteRoutine(id) } }
}

@Composable
fun RoutinesScreen(nav: Navigator, vm: RoutinesViewModel = hiltViewModel()) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopPillBar(title = stringResource(R.string.routines_title), onBack = { nav.back() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (routines.isEmpty()) {
                item { Text(stringResource(R.string.routines_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(routines, key = { it.id }) { r ->
                CardListItem(
                    headlineContent = { Text(r.name) },
                    supportingContent = { Text(stringResource(R.string.minutes_short, r.durationTargetMin)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.delete(r.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                            GradientButton(stringResource(R.string.routines_use), height = 40.dp) { vm.use(r.id) { nav.go(Route.Plan) } }
                        }
                    },
                    modifier = Modifier
                )
            }
        }
    }
}
