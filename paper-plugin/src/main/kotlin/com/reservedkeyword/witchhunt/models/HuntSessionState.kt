package com.reservedkeyword.witchhunt.models

import java.time.Instant

data class HuntSessionState(
    val expiresAt: Instant,
    val hunterJoined: Boolean = false,
    val hunterMinecraftName: String,
    val hunterTwitchName: String,
    val startedAt: Instant
)
