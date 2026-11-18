package com.gymencasa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymencasa.data.repository.AuthRepository
import com.gymencasa.data.repository.AuthState
import com.gymencasa.data.repository.ExerciseRepository
import com.gymencasa.data.repository.SettingsRepository
import com.gymencasa.data.repository.WorkoutRepository
import com.gymencasa.update.AvailableUpdate
import com.gymencasa.update.GitHubUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Gate { LOADING, AUTH, ONBOARDING, APP }

@HiltViewModel
class AppViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
    private val updater: GitHubUpdater,
) : ViewModel() {

    val darkTheme: StateFlow<Boolean> = settings.darkTheme.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val gate: StateFlow<Gate> = combine(auth.state, settings.settings) { a, s ->
        when (a) {
            AuthState.LOADING -> Gate.LOADING
            AuthState.SIGNED_OUT -> Gate.AUTH
            AuthState.SIGNED_IN -> if (s.onboarded) Gate.APP else Gate.ONBOARDING
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.LOADING)

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update

    init {
        viewModelScope.launch {
            exercises.ensureSeeded()
            exercises.refreshFromRemote()
        }
        viewModelScope.launch {
            auth.state.distinctUntilChanged().filter { it == AuthState.SIGNED_IN }.collect {
                settings.pullProfile()
                exercises.refreshFromRemote()
                workouts.requestSync()
            }
        }
        viewModelScope.launch { _update.value = runCatching { updater.check() }.getOrNull() }
    }

    fun dismissUpdate() { _update.value = null }
}
