package com.reservedkeyword.witchhunt.game

import com.reservedkeyword.witchhunt.models.AttemptOutcome
import com.reservedkeyword.witchhunt.models.HuntSessionState
import com.reservedkeyword.witchhunt.models.HunterEncounter
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
