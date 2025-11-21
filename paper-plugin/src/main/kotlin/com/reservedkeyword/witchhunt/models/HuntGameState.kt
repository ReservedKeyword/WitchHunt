package com.reservedkeyword.witchhunt.models

data class HuntGameState(
    val activeHuntSession: HuntSessionState? = null,
    val attemptHistory: List<GameAttempt> = emptyList(),
    val currentAttempt: GameAttempt? = null,
    val isActive: Boolean = false,
    val isPaused: Boolean = false
) {
    fun clearHuntSession(): HuntGameState = copy(activeHuntSession = null)

    fun withEndedAttempt(outcome: AttemptOutcome): HuntGameState {
        val endedAttempt = currentAttempt?.withEnd(outcome) ?: return this

        return copy(
            currentAttempt = null,
            activeHuntSession = null,
            attemptHistory = attemptHistory + endedAttempt,
            isActive = false,
            isPaused = false
        )
    }

    fun withHunterEncounter(encounter: HunterEncounter): HuntGameState {
        val updatedAttempt = currentAttempt?.withHunterEncounter(encounter) ?: return this

        return copy(
            currentAttempt = updatedAttempt
        )
    }

    fun withHuntSession(session: HuntSessionState): HuntGameState = copy(activeHuntSession = session)

    fun withNewAttempt(attempt: GameAttempt): HuntGameState = copy(
        activeHuntSession = null,
        currentAttempt = attempt,
        isActive = true,
        isPaused = false
    )

    fun withPaused(): HuntGameState = copy(
        activeHuntSession = null,
        isPaused = true
    )

    fun withResumed(): HuntGameState = copy(isPaused = false)
}
