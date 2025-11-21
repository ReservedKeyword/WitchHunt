package com.reservedkeyword.witchhunt.models

import java.time.Duration
import java.time.Instant

data class GameAttempt(
    val attemptNumber: Int,
    val endedAt: Instant? = null,
    val hunterKills: List<HunterEncounter> = emptyList(),
    val outcome: AttemptOutcome = AttemptOutcome.IN_PROGRESS,
    val seed: Long,
    val startedAt: Instant,
    val streamerName: String,
    val worldName: String
) {
    val durationSeconds: Long?
        get() = endedAt?.let { endedTime ->
            Duration.between(startedAt, endedTime).seconds
        }

    fun withEnd(outcome: AttemptOutcome, endTime: Instant = Instant.now()): GameAttempt = copy(
        endedAt = endTime,
        outcome = outcome
    )

    fun withHunterEncounter(encounter: HunterEncounter): GameAttempt = copy(
        hunterKills = hunterKills + encounter
    )
}