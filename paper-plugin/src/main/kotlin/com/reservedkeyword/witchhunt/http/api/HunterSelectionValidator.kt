package com.reservedkeyword.witchhunt.http.api

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import io.ktor.http.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class HunterSelectionValidator(private val plugin: WitchHuntPlugin) {
    fun getStreamerPlayer(): Result<Player> {
        val streamerUsername = plugin.configManager.getConfig().streamerUsername
        val streamerPlayer = Bukkit.getPlayer(streamerUsername)

        return if (streamerPlayer != null) {
            Result.success(streamerPlayer)
        } else {
            Result.failure(
                ValidationException(
                    statueCode = HttpStatusCode.BadRequest,
                    message = "Streamer is currently not online"
                )
            )
        }
    }

    fun validateGameState(): Result<Unit> {
        if (!plugin.huntManager.isGameActive()) {
            return Result.failure(
                ValidationException(
                    statueCode = HttpStatusCode.BadRequest,
                    message = "No active game in progress"
                )
            )
        }

        if (plugin.huntManager.isGamePaused()) {
            return Result.failure(
                ValidationException(
                    statueCode = HttpStatusCode.ServiceUnavailable,
                    message = "Game is active but paused"
                )
            )
        }

        if (plugin.huntManager.currentState.activeHuntSession != null) {
            return Result.failure(
                ValidationException(
                    statueCode = HttpStatusCode.Conflict,
                    message = "A hunt is already currently in progress"
                )
            )
        }

        return Result.success(Unit)
    }
}
