package com.gymencasa.ui.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.domain.model.Equipment
import com.gymencasa.domain.model.Level
import com.gymencasa.domain.model.UserSettings
import com.gymencasa.ui.components.ScreenHeader
import com.gymencasa.ui.components.SectionTitle
import com.gymencasa.ui.icon
import com.gymencasa.ui.labelRes
import com.gymencasa.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EquipmentViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val current: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(equipment: Set<String>, name: String? = null, level: Level? = null, finishOnboarding: Boolean = false, done: () -> Unit = {}) {
        viewModelScope.launch {
            settings.update { s ->
                s.copy(
                    equipment = equipment,
                    displayName = name ?: s.displayName,
                    level = level ?: s.level,
                    onboarded = s.onboarded || finishOnboarding,
                )
            }
            done()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentPicker(selected: Set<String>, onToggle: (String) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.equipment_gym))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.gym.forEach { EquipmentChip(it, it.slug in selected) { onToggle(it.slug) } }
        }
        SectionTitle(stringResource(R.string.equipment_household))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Equipment.household.forEach { EquipmentChip(it, it.slug in selected) { onToggle(it.slug) } }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.equipment_none_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EquipmentChip(eq: Equipment, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(eq.labelRes), modifier = Modifier.padding(vertical = 10.dp)) },
        leadingIcon = { Icon(if (selected) Icons.Filled.Check else eq.icon, null, Modifier.size(20.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LevelPicker(selected: Level, onSelect: (Level) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Level.entries.forEach { level ->
            FilterChip(
                selected = level == selected, onClick = { onSelect(level) },
                label = { Text(stringResource(level.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
fun OnboardingScreen(vm: EquipmentViewModel = hiltViewModel()) {
    val current by vm.current.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(Level.INTERMEDIO) }
    LaunchedEffect(current != null) {
        current?.let { selected = it.equipment; name = it.displayName; level = it.level }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ScreenHeader(stringResource(R.string.equipment_title), subtitle = stringResource(R.string.equipment_subtitle))
            SectionTitle(stringResource(R.string.onboarding_name))
            OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionTitle(stringResource(R.string.onboarding_level))
            LevelPicker(level) { level = it }
            EquipmentPicker(selected) { slug -> selected = if (slug in selected) selected - slug else selected + slug }
            Spacer(Modifier.height(24.dp))
        }
        Button(
            onClick = { vm.save(selected, name.trim(), level, finishOnboarding = true) },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
        ) { Text(stringResource(R.string.action_continue).uppercase()) }
    }
}

@Composable
fun EquipmentEditScreen(nav: Navigator, vm: EquipmentViewModel = hiltViewModel()) {
    val current by vm.current.collectAsStateWithLifecycle()
    val initial = remember(current != null) { current?.equipment ?: emptySet() }
    var selected by rememberSaveable(initial) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ScreenHeader(stringResource(R.string.equipment_title), subtitle = stringResource(R.string.equipment_subtitle))
            EquipmentPicker(selected) { slug -> selected = if (slug in selected) selected - slug else selected + slug }
        }
        Button(
            onClick = { vm.save(selected) { nav.back() } },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
        ) { Text(stringResource(R.string.action_save).uppercase()) }
    }
}
