package com.gymencasa.data.repository

import com.gymencasa.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthState { LOADING, SIGNED_OUT, SIGNED_IN }

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    /** Local-only mode: used when Supabase is not configured in this build. */
    private val localMode = MutableStateFlow(false)

    val isConfigured: Boolean get() = supabase.isConfigured

    val state: Flow<AuthState> = run {
        val remote: Flow<AuthState> = supabase.client?.auth?.sessionStatus?.map {
            when (it) {
                is SessionStatus.Authenticated -> AuthState.SIGNED_IN
                is SessionStatus.Initializing -> AuthState.LOADING
                else -> AuthState.SIGNED_OUT
            }
        } ?: flowOf(AuthState.SIGNED_OUT)
        combine(remote, localMode) { r, local -> if (local) AuthState.SIGNED_IN else r }
    }

    val userId: String? get() = supabase.client?.auth?.currentUserOrNull()?.id

    val email: String? get() = supabase.client?.auth?.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String) {
        val client = supabase.client ?: error("Supabase no configurado")
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        val client = supabase.client ?: error("Supabase no configurado")
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        // With email confirmation disabled the user is signed in right away; otherwise sign in explicitly.
        if (client.auth.currentUserOrNull() == null) signIn(email, password)
    }

    fun useLocalMode() {
        localMode.value = true
    }

    suspend fun signOut() {
        localMode.value = false
        supabase.client?.auth?.signOut()
    }
}
