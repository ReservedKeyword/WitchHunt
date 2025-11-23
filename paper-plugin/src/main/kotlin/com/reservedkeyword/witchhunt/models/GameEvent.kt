package com.reservedkeyword.witchhunt.models

import com.reservedkeyword.witchhunt.game.HuntSession
import org.bukkit.entity.Player

sealed class GameEvent {
    data class End(val attemptOutcome: AttemptOutcome) : GameEvent()

    object HuntSessionCleared : GameEvent()

    data class HuntSessionStarted(val huntSession: HuntSession, val huntSessionState: HuntSessionState) : GameEvent()

    data class HunterEncountered(val hunterEncounter: HunterEncounter) : GameEvent()

    object Pause : GameEvent()

    object Resume : GameEvent()

    data class Start(val streamerPlayer: Player) : GameEvent()
}
