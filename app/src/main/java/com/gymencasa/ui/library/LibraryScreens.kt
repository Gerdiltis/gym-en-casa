package com.gymencasa.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.gymencasa.domain.EquipmentFilter
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.ui.components.ExerciseThumb
import com.gymencasa.ui.components.ExerciseVideo
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.equipmentLabelRes
import com.gymencasa.ui.home.RedChip
import com.gymencasa.ui.labelRes
import com.gymencasa.ui.muscleLabel
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryItem(val exercise: Exercise, val available: Boolean, val missing: Set<String>, val thumb: String?)

data class LibraryUi(val items: List<LibraryItem> = emptyList(), val selected: Set<String> = emptySet())

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val exercises: ExerciseRepository,
    settings: SettingsRepository,
    private val draftStore: DayDraftStore,
) : ViewModel() {
    val ui: StateFlow<LibraryUi> = combine(exercises.exercises, settings.settings, draftStore.draft) { all, s, d ->
        LibraryUi(
            items = all.map { ex ->
                val missing = EquipmentFilter.missing(ex, s.equipment)
                LibraryItem(ex, missing.isEmpty(), missing, exercises.thumbUri(ex))
            },
            selected = d.selectedSlugs.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun toggle(slug: String) = draftStore.update {
        it.copy(selectedSlugs = if (slug in it.selectedSlugs) it.selectedSlugs - slug else it.selectedSlugs + slug)
    }

    fun find(slug: String): Exercise? = ui.value.items.firstOrNull { it.exercise.slug == slug }?.exercise
    fun videoUri(ex: Exercise) = exercises.videoUri(ex)
    fun thumbUri(ex: Exercise) = exercises.thumbUri(ex)
}

@Composable
fun LibraryScreen(nav: Navigator, pickerMode: Boolean, vm: LibraryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var showUnavailable by rememberSaveable { mutableStateOf(false) }

    val filtered = ui.items.filter { item ->
        (showUnavailable && !pickerMode || item.available) &&
            (type == null || item.exercise.type == type) &&
            (query.isBlank() || item.exercise.name.contains(query.trim(), ignoreCase = true))
    }.sortedBy { !it.available }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            ScreenHeader(stringResource(R.string.library_title))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.library_search)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RedChip(type == null, stringResource(R.string.library_all)) { type = null } }
            items(ExerciseType.entries) { t -> RedChip(type == t, stringResource(t.labelRes)) { type = if (type == t) null else t } }
        }
        if (!pickerMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.library_show_unavailable), Modifier.weight(1f))
                Switch(checked = showUnavailable, onCheckedChange = { showUnavailable = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                item { Text(stringResource(R.string.library_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(filtered, key = { it.exercise.slug }) { item ->
                val ex = item.exercise
                val selected = ex.slug in ui.selected
                ListItem(
                    headlineContent = { Text(ex.name) },
                    supportingContent = {
                        if (!item.available) {
                            val names = item.missing.map { slug -> equipmentLabelRes(slug)?.let { stringResource(it) } ?: slug }
                            Text(stringResource(R.string.library_missing, names.joinToString()), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(ex.muscleGroups.joinToString { muscleLabel(it) })
                        }
                    },
                    leadingContent = { ExerciseThumb(item.thumb) },
                    trailingContent = if (pickerMode) {
                        {
                            Checkbox(checked = selected, onCheckedChange = { vm.toggle(ex.slug) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .alpha(if (item.available) 1f else 0.5f)
                        .clickable { if (pickerMode) vm.toggle(ex.slug) else nav.go(Route.ExerciseDetail(ex.slug)) }
                        .padding(horizontal = 8.dp),
                )
            }
        }
        if (pickerMode) {
            Button(onClick = { nav.back() }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp)) {
                Text(stringResource(R.string.select_selected_count, ui.selected.size).uppercase())
            }
        }
    }
}

@Composable
fun ExerciseDetailScreen(slug: String, nav: Navigator, vm: LibraryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ex = ui.items.firstOrNull { it.exercise.slug == slug }?.exercise ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        ScreenHeader(ex.name)
        ExerciseVideo(vm.videoUri(ex), vm.thumbUri(ex), Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(ex.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.exercise_muscles, ex.muscleGroups.joinToString { muscleLabel(it) }), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val eq = ex.equipment.map { s -> equipmentLabelRes(s)?.let { stringResource(it) } ?: s }
        Text(stringResource(R.string.exercise_equipment, eq.joinToString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.exercise_difficulty, ex.difficulty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ex.unilateral) Text(stringResource(R.string.exercise_unilateral), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}
