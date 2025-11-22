package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.models.HunterOutcome
import org.bukkit.Location
import java.time.Instant

sealed class SessionState {
    data class Active(val joinedAt: Instant, val spawnLocation: Location) : SessionState()

    data class Ended(val endedAt: Instant, val hunterOutcome: HunterOutcome, val joinedAt: Instant?) : SessionState()

    object WaitingForJoin : SessionState()
}
