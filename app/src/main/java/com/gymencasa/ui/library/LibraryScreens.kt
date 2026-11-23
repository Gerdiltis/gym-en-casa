package com.gymencasa.ui.library

import com.gymencasa.ui.components.TopPillBar
import com.gymencasa.ui.components.GradientButton
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
import com.gymencasa.ui.components.CardListItem
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
import com.gymencasa.domain.ExerciseFilter
import com.gymencasa.domain.model.Exercise
import com.gymencasa.domain.model.ExerciseType
import com.gymencasa.ui.components.ExerciseThumb
import com.gymencasa.ui.components.ExerciseVideo
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.MuscleFilter
import com.gymencasa.ui.equipmentRequirementLabel
import com.gymencasa.ui.muscleList
import com.gymencasa.domain.model.Pregnancy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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

/** [blockedByPregnancy]: the user has the equipment but is in pregnancy mode and the exercise is not suitable. */
data class LibraryItem(val exercise: Exercise, val available: Boolean, val missing: Set<String>, val thumb: String?, val blockedByPregnancy: Boolean = false)

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
                val missing = ExerciseFilter.missing(ex, s.equipment)
                val blocked = s.pregnant && !ExerciseFilter.suitableForPregnancy(ex)
                LibraryItem(ex, missing.isEmpty() && !blocked, missing, exercises.thumbUri(ex), blocked)
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
    var muscle by rememberSaveable { mutableStateOf<MuscleFilter?>(null) }
    var pregnancyOnly by rememberSaveable { mutableStateOf(false) }

    val filtered = ui.items.filter { item ->
        (showUnavailable && !pickerMode || item.available) &&
            (type == null || item.exercise.type == type) &&
            (muscle == null || item.exercise.muscleGroups.any { it in muscle!!.slugs }) &&
            (!pregnancyOnly || ExerciseFilter.suitableForPregnancy(item.exercise)) &&
            (query.isBlank() || item.exercise.name.contains(query.trim(), ignoreCase = true))
    }.sortedBy { !it.available }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (pickerMode) TopPillBar(title = stringResource(R.string.select_add_more), onBack = { nav.back() }, modifier = Modifier.padding(vertical = 8.dp))
            else ScreenHeader(stringResource(R.string.library_title))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.library_search)) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RedChip(type == null, stringResource(R.string.library_all)) { type = null } }
            items(ExerciseType.entries) { t -> RedChip(type == t, stringResource(t.labelRes)) { type = if (type == t) null else t } }
            item { RedChip(pregnancyOnly, stringResource(R.string.pregnancy_filter)) { pregnancyOnly = !pregnancyOnly } }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MuscleFilter.entries) { m -> RedChip(muscle == m, stringResource(m.label)) { muscle = if (muscle == m) null else m } }
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
                CardListItem(
                    headlineContent = { Text(ex.name) },
                    supportingContent = {
                        Column {
                            Text(muscleList(ex.muscleGroups))
                            when {
                                item.missing.isNotEmpty() -> {
                                    val names = item.missing.map { equipmentRequirementLabel(it) }
                                    Text(stringResource(R.string.library_missing, names.joinToString()), color = MaterialTheme.colorScheme.error)
                                }
                                item.blockedByPregnancy -> Text(stringResource(R.string.library_not_for_pregnancy), color = MaterialTheme.colorScheme.error)
                                ex.pregnancy == Pregnancy.PARTO -> Text(stringResource(R.string.pregnancy_badge_parto),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    leadingContent = { ExerciseThumb(item.thumb) },
                    trailingContent = if (pickerMode) {
                        {
                            Checkbox(checked = selected, onCheckedChange = { vm.toggle(ex.slug) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        }
                    } else null,
                    modifier = Modifier
                        .alpha(if (item.available) 1f else 0.5f)
                        .clickable { if (pickerMode) vm.toggle(ex.slug) else nav.go(Route.ExerciseDetail(ex.slug)) }
                        
                )
            }
        }
        if (pickerMode) {
            GradientButton(stringResource(R.string.select_selected_count, ui.selected.size), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) { nav.back() }
        }
    }
}

@Composable
fun ExerciseDetailScreen(slug: String, nav: Navigator, vm: LibraryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ex = ui.items.firstOrNull { it.exercise.slug == slug }?.exercise ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        TopPillBar(title = ex.name, subtitle = stringResource(ex.type.labelRes), onBack = { nav.back() }, modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        ExerciseVideo(vm.videoUri(ex), vm.thumbUri(ex), Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(ex.description, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        MuscleChips(ex.muscleGroups)
        Spacer(Modifier.height(8.dp))
        val eq = ex.equipment.map { equipmentRequirementLabel(it) }
        Text(stringResource(R.string.exercise_equipment, eq.joinToString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.exercise_difficulty, ex.difficulty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ex.unilateral) Text(stringResource(R.string.exercise_unilateral), color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(when (ex.pregnancy) {
                Pregnancy.PARTO -> R.string.pregnancy_detail_parto
                Pregnancy.APTO -> R.string.pregnancy_detail_apto
                Pregnancy.NO -> R.string.pregnancy_detail_no
            }),
            color = if (ex.pregnancy == Pregnancy.NO) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp).navigationBarsPadding())
    }
}

/** Primary muscle in red, the rest in grey. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChips(muscles: List<String>) {
    if (muscles.isEmpty()) return
    Text(stringResource(R.string.muscles_primary).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AssistChip(
        onClick = {}, label = { Text(muscleLabel(muscles.first())) },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onPrimary),
        border = null,
    )
    if (muscles.size > 1) {
        Text(stringResource(R.string.muscles_secondary).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            muscles.drop(1).forEach { m ->
                AssistChip(onClick = {}, label = { Text(muscleLabel(m)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
}
