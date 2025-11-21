package com.reservedkeyword.witchhunt.models.tracking

data class AttemptSummary(
    val averageDurationSeconds: Long?,
    val cancelled: Int,
    val deaths: Int,
    val hunterKills: Int,
    val longestAttemptSeconds: Long?,
    val shortedAttemptSeconds: Long?,
    val totalAttempts: Int,
    val totalHunterEncounters: Int,
    val victories: Int
)
