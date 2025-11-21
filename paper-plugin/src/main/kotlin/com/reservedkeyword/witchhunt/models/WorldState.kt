package com.reservedkeyword.witchhunt.models

import org.bukkit.World

data class WorldState(
    val activeWorld: World? = null,
    val lobbyWorld: World,
    val nextWorld: World? = null,
    val pregenerationInProgress: Boolean = false
) {
    fun clearActiveWorld(): WorldState = copy(activeWorld = null)

    fun withActiveWorld(world: World): WorldState = copy(
        activeWorld = world,
        nextWorld = null
    )

    fun withNextWorld(world: World): WorldState = copy(
        nextWorld = world,
        pregenerationInProgress = false
    )

    fun withPregenerationStarted(): WorldState = copy(
        pregenerationInProgress = true
    )
}
