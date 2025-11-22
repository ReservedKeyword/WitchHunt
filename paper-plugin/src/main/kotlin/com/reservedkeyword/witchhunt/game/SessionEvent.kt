package com.reservedkeyword.witchhunt.game

import org.bukkit.entity.Player

sealed class SessionEvent {
    object Cancelled : SessionEvent()

    object HuntTimedOut : SessionEvent()

    data class HunterDied(val hunterPlayer: Player) : SessionEvent()

    data class HunterJoined(val hunterPlayer: Player) : SessionEvent()

    object JoinTimedOut : SessionEvent()

    object Started : SessionEvent()
}
