package com.reservedkeyword.witchhunt.models

data class HuntGameState(
    val activeHuntSession: HuntSessionState? = null,
    val attemptHistory: List<GameAttempt> = emptyList(),
    val currentAttempt: GameAttempt? = null,
    val isActive: Boolean = false,
    val isPaused: Boolean = false
) {
    fun clearHuntSession(): HuntGameState = copy(activeHuntSession = null)

    fun withEndedAttempt(attemptOutcome: AttemptOutcome): HuntGameState {
        val endedAttempt = currentAttempt?.withEnd(attemptOutcome) ?: return this

        return copy(
            currentAttempt = null,
            activeHuntSession = null,
            attemptHistory = attemptHistory + endedAttempt,
            isActive = false,
            isPaused = false
        )
    }

    fun withHunterEncounter(hunterEncounter: HunterEncounter): HuntGameState {
        val updatedAttempt = currentAttempt?.withHunterEncounter(hunterEncounter) ?: return this

        return copy(
            currentAttempt = updatedAttempt
        )
    }

    fun withHuntSession(huntSessionState: HuntSessionState): HuntGameState = copy(activeHuntSession = huntSessionState)

    fun withNewAttempt(gameAttempt: GameAttempt): HuntGameState = copy(
        activeHuntSession = null,
        currentAttempt = gameAttempt,
        isActive = true,
        isPaused = false
    )

    fun withPaused(): HuntGameState = copy(
        activeHuntSession = null,
        isPaused = true
    )

    fun withResumed(): HuntGameState = copy(isPaused = false)
}
