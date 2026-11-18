package com.gymencasa.data

import com.gymencasa.domain.model.DayPlan
import com.gymencasa.domain.model.Focus
import com.gymencasa.domain.model.Goal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Today's choices, shared by Home → selection → plan → session. */
data class DayDraft(
    val durationMin: Int = 30,
    val goal: Goal = Goal.TONIFICAR,
    val focus: Focus = Focus.CUERPO_COMPLETO,
    val selectedSlugs: List<String> = emptyList(),
    val plan: DayPlan? = null,
    /** Set when the AI was unavailable and the local engine was used instead. */
    val usedFallback: Boolean = false,
    /** True once the goal has been aligned with the user's profile (e.g. labour prep in pregnancy mode). */
    val goalSynced: Boolean = false,
)

@Singleton
class DayDraftStore @Inject constructor() {
    private val _draft = MutableStateFlow(DayDraft())
    val draft: StateFlow<DayDraft> = _draft

    fun update(transform: (DayDraft) -> DayDraft) = _draft.update(transform)
}
