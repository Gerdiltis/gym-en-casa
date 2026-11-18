package com.gymencasa.ui.select

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.domain.EquipmentFilter
import com.gymencasa.domain.PlanAlgorithm
import com.gymencasa.domain.SuggestionEngine
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.WorkoutMode
import com.gymencasa.ui.components.ExerciseThumb
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectUi(
    val selected: List<Exercise> = emptyList(),
    val durationMin: Int = 30,
    val estimatedMin: Int = 0,
    val thumbs: Map<String, String?> = emptyMap(),
    val doses: Map<String, Pair<Int, Int>> = emptyMap(),
)

@HiltViewModel
class ManualSelectViewModel @Inject constructor(
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val workouts: WorkoutRepository,
    private val draftStore: DayDraftStore,
) : ViewModel() {

    val ui: StateFlow<SelectUi> = combine(draftStore.draft, exercises.exercises, settings.settings) { d, all, s ->
        val bySlug = all.associateBy { it.slug }
        val selected = d.selectedSlugs.mapNotNull(bySlug::get)
        val planned = selected.map { PlanAlgorithm.assign(it, d.goal, s.level) }
        SelectUi(
            selected = selected,
            durationMin = d.durationMin,
            estimatedMin = (PlanAlgorithm.totalSeconds(planned, s.restBetweenSetsSeconds) + 59) / 60,
            thumbs = selected.associate { it.slug to exercises.thumbUri(it) },
            doses = planned.associate { it.exercise.slug to (it.sets to it.workSeconds) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectUi())

    init {
        if (draftStore.draft.value.selectedSlugs.isEmpty()) suggest()
    }

    fun suggest() {
        viewModelScope.launch {
            val s = settings.current()
            val d = draftStore.draft.value
            val available = EquipmentFilter.available(exercises.all(), s.equipment)
            val recent = workouts.lastWorkoutSlugs()
            val picks = SuggestionEngine.suggest(available, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, d.focus, recent)
            draftStore.update { it.copy(selectedSlugs = picks.map(Exercise::slug)) }
        }
    }

    fun remove(slug: String) = draftStore.update { it.copy(selectedSlugs = it.selectedSlugs - slug) }

    fun buildPlan(title: String, onReady: () -> Unit) {
        viewModelScope.launch {
            val s = settings.current()
            val d = draftStore.draft.value
            val bySlug = exercises.all().associateBy { it.slug }
            val picks = d.selectedSlugs.mapNotNull(bySlug::get)
            if (picks.isEmpty()) return@launch
            val plan = PlanAlgorithm.build(picks, d.goal, s.level, s.restBetweenSetsSeconds, d.durationMin, WorkoutMode.MANUAL, title, allowDrop = false)
            draftStore.update { it.copy(plan = plan, usedFallback = false) }
            onReady()
        }
    }
}

@Composable
fun ManualSelectScreen(nav: Navigator, vm: ManualSelectViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val title = stringResource(R.string.manual_title)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(stringResource(R.string.select_title), subtitle = stringResource(R.string.select_suggested, ui.durationMin))
            val over = ui.estimatedMin > ui.durationMin
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.select_time_estimate, ui.estimatedMin, ui.durationMin),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.select_selected_count, ui.selected.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { (ui.estimatedMin.toFloat() / ui.durationMin.coerceAtLeast(1)).coerceIn(0f, 1f) },
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::suggest, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.select_reshuffle))
                }
                OutlinedButton(onClick = { nav.go(Route.LibraryPicker) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.select_add_more))
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            if (ui.selected.isEmpty()) {
                item { Text(stringResource(R.string.select_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(ui.selected, key = { it.slug }) { ex ->
                val dose = ui.doses[ex.slug]
                ListItem(
                    headlineContent = { Text(ex.name) },
                    supportingContent = { dose?.let { Text(stringResource(R.string.sets_by_seconds, it.first, it.second)) } },
                    leadingContent = { ExerciseThumb(ui.thumbs[ex.slug]) },
                    trailingContent = {
                        IconButton(onClick = { vm.remove(ex.slug) }) { Icon(Icons.Filled.Close, stringResource(R.string.plan_remove)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Button(
            onClick = { vm.buildPlan(title) { nav.go(Route.Plan) } },
            enabled = ui.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
        ) { Text(stringResource(R.string.select_continue).uppercase()) }
    }
}
