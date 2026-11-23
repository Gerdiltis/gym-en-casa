package com.gymencasa.ui.auth

import androidx.compose.foundation.shape.RoundedCornerShape
import com.gymencasa.ui.components.IconBadge
import com.gymencasa.ui.components.GradientButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gymencasa.R
import com.gymencasa.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUi(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui
    val isConfigured: Boolean get() = auth.isConfigured

    fun submit(email: String, password: String, signUp: Boolean) {
        if (_ui.value.loading) return
        _ui.value = AuthUi(loading = true)
        viewModelScope.launch {
            runCatching { if (signUp) auth.signUp(email, password) else auth.signIn(email, password) }
                .onFailure { e -> _ui.update { AuthUi(error = e.message?.lineSequence()?.firstOrNull() ?: "?") } }
                .onSuccess { _ui.update { AuthUi() } }
        }
    }

    fun localMode() = auth.useLocalMode()
}

@Composable
fun AuthScreen(vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var signUp by rememberSaveable { mutableStateOf(false) }
    val passwordOk = password.length >= 6

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        IconBadge(Icons.Filled.FitnessCenter, MaterialTheme.colorScheme.primary, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.auth_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))

        if (!vm.isConfigured) {
            Text(stringResource(R.string.auth_not_configured), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            GradientButton(stringResource(R.string.auth_local_mode), modifier = Modifier.fillMaxWidth(), onClick = vm::localMode)
            return@Column
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it }, singleLine = true,
            label = { Text(stringResource(R.string.auth_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true,
            label = { Text(stringResource(R.string.auth_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { if (signUp && password.isNotEmpty() && !passwordOk) Text(stringResource(R.string.auth_password_too_short)) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let {
            Text(stringResource(R.string.auth_error, it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (ui.loading) {
            CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp), strokeWidth = 3.dp)
        } else {
            GradientButton(
                stringResource(if (signUp) R.string.auth_sign_up else R.string.auth_sign_in),
                enabled = email.contains('@') && passwordOk, modifier = Modifier.fillMaxWidth(),
            ) { vm.submit(email, password, signUp) }
        }
        TextButton(onClick = { signUp = !signUp }) {
            Text(stringResource(if (signUp) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_sign_up))
        }
    }
}
