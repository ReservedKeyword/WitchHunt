package com.reservedkeyword.witchhunt.models

import java.time.Instant

data class HunterEncounter(
    val endedAt: Instant,
    val damageDealt: Double = 0.0,
    val hunterMinecraftName: String,
    val hunterTwitchName: String,
    val joinedAt: Instant? = null,
    val outcome: HunterOutcome
)
