package com.gymencasa.ui.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.DayDraftStore
import com.gymencasa.data.PlanGenerator
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.domain.ExerciseFilter
import com.gymencasa.domain.model.Equipment
import com.gymencasa.ui.components.GradientButton
import com.gymencasa.ui.components.PillChip
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.components.TopPillBar
import com.gymencasa.ui.icon
import com.gymencasa.ui.labelRes
import com.gymencasa.ui.navigation.Navigator
import com.gymencasa.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionEquipmentUi(
    /** Equipment in the profile, in picker order. */
    val owned: List<Equipment> = emptyList(),
    val selected: Set<String> = emptySet(),
    val availableCount: Int = 0,
    val loading: Boolean = false,
)

@HiltViewModel
class SessionEquipmentViewModel @Inject constructor(
    settings: SettingsRepository,
    exercises: ExerciseRepository,
    private val draftStore: DayDraftStore,
    private val generator: PlanGenerator,
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    val draft = draftStore.draft

    val ui: StateFlow<SessionEquipmentUi> = combine(
        settings.settings, draftStore.draft, flow { emit(exercises.all()) }, loading,
    ) { s, d, all, busy ->
        val owned = (Equipment.gym + Equipment.household).filter { it.slug in s.equipment }
        val today = ExerciseFilter.forSession(s, d.sessionEquipment)
        SessionEquipmentUi(owned, today.equipment, ExerciseFilter.available(all, today).size, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionEquipmentUi())

    fun toggle(slug: String) = draftStore.update { d ->
        val current = ui.value.selected
        d.copy(sessionEquipment = if (slug in current) current - slug else current + slug)
    }

    fun selectAll(all: Boolean) = draftStore.update { d ->
        d.copy(sessionEquipment = if (all) ui.value.owned.map(Equipment::slug).toSet() else emptySet())
    }

    fun generateAi(defaultTitle: String, onReady: () -> Unit) {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            try { if (generator.generateAi(defaultTitle)) onReady() } finally { loading.value = false }
        }
    }

    fun startManual(onReady: () -> Unit) {
        // New equipment → fresh suggestion in the manual selection.
        draftStore.update { it.copy(selectedSlugs = emptyList(), plan = null) }
        onReady()
    }
}

/** "Material de hoy": the user picks which of their equipment to use in this session (AI or manual). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionEquipmentScreen(ai: Boolean, nav: Navigator, vm: SessionEquipmentViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.ai_title_default, stringResource(draft.goal.labelRes))
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            TopPillBar(title = stringResource(R.string.session_equipment_title), onBack = { nav.back() }, modifier = Modifier.padding(vertical = 8.dp))
            ScreenHeader(stringResource(R.string.session_equipment_header), subtitle = stringResource(R.string.session_equipment_subtitle))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                PillChip(stringResource(R.string.session_equipment_all), ui.selected.size == ui.owned.size) { vm.selectAll(true) }
                PillChip(stringResource(R.string.session_equipment_none), ui.selected.isEmpty()) { vm.selectAll(false) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.owned.forEach { eq ->
                    val on = eq.slug in ui.selected
                    PillChip(stringResource(eq.labelRes), on, icon = if (on) Icons.Filled.Check else eq.icon) { vm.toggle(eq.slug) }
                }
            }
            Text(
                stringResource(R.string.session_equipment_count, ui.availableCount),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
        val enabled = ui.availableCount > 0 && !ui.loading
        if (ai) {
            GradientButton(
                if (ui.loading) stringResource(R.string.home_ai_working) else stringResource(R.string.home_generate_ai),
                Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), icon = Icons.Filled.AutoAwesome, enabled = enabled,
            ) { vm.generateAi(defaultTitle) { nav.go(Route.Plan) } }
        } else {
            GradientButton(
                stringResource(R.string.session_equipment_manual),
                Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), icon = Icons.Filled.TouchApp, enabled = enabled,
            ) { vm.startManual { nav.go(Route.ManualSelect) } }
        }
    }
}
