package com.reservedkeyword.witchhunt.http.api

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.game.HuntSession
import com.reservedkeyword.witchhunt.models.HuntSessionState
import com.reservedkeyword.witchhunt.models.http.api.HunterSelectionRequest
import com.reservedkeyword.witchhunt.models.http.api.StatusResponse
import com.reservedkeyword.witchhunt.models.http.api.SuccessResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bukkit.Bukkit
import java.time.Instant

fun Application.configureRouting(plugin: WitchHuntPlugin) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/api/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/api/hunt/status") {
            try {
                val huntStatus = StatusResponse(
                    currentAttempt = plugin.huntManager.getCurrentAttemptNumber(),
                    gameActive = plugin.huntManager.isGameActive(),
                    worldReady = plugin.huntManager.isWorldReady()
                )

                call.respond(HttpStatusCode.OK, huntStatus)
            } catch (e: Exception) {
                e.printStackTrace()

                call.respond(
                    HttpStatusCode.InternalServerError, SuccessResponse(
                        success = false,
                        message = e.message
                    )
                )
            }
        }

        post("/api/hunt/select") {
            try {
                val hunterSelectionRequest = call.receive<HunterSelectionRequest>()
                plugin.logger.info("Received hunter selection: ${hunterSelectionRequest.twitchUsername}")

                if (!plugin.huntManager.isGameActive()) {
                    plugin.logger.warning("Failed to select hunter, no active game in progress")

                    call.respond(
                        HttpStatusCode.BadRequest, SuccessResponse(
                            success = false,
                            message = "No active game"
                        )
                    )

                    return@post
                }

                if (plugin.huntManager.isGamePaused()) {
                    plugin.logger.warning("Failed to select hunter, game is currently in a paused state")

                    call.respond(
                        HttpStatusCode.ServiceUnavailable, SuccessResponse(
                            success = false,
                            message = "Game is paused"
                        )
                    )

                    return@post
                }

                if (plugin.huntManager.currentState.activeHuntSession != null) {
                    plugin.logger.warning("Failed to select hunter, an active hunt is already in progress")

                    call.respond(
                        HttpStatusCode.Conflict, SuccessResponse(
                            success = false,
                            message = "Hunt is already in progress"
                        )
                    )

                    return@post
                }

                val streamerUsername = plugin.configManager.getConfig().streamerUsername
                val streamerPlayer = Bukkit.getPlayer(streamerUsername)

                if (streamerPlayer == null) {
                    plugin.logger.warning("Failed to select hunter, streamer is currently not online")

                    call.respond(
                        HttpStatusCode.BadRequest, SuccessResponse(
                            success = false,
                            message = "Streamer not online"
                        )
                    )

                    return@post
                }

                val huntSession = HuntSession(
                    config = plugin.configManager.getConfig(),
                    minecraftUsername = hunterSelectionRequest.minecraftUsername,
                    plugin = plugin,
                    streamerPlayer = streamerPlayer,
                    twitchUsername = hunterSelectionRequest.twitchUsername
                )

                val huntSessionState = HuntSessionState(
                    expiresAt = Instant.now().plusMillis(plugin.configManager.getConfig().timing.joinTimeoutMillis),
                    hunterMinecraftName = hunterSelectionRequest.minecraftUsername,
                    hunterTwitchName = hunterSelectionRequest.twitchUsername,
                    startedAt = Instant.now()
                )

                plugin.huntManager.setActiveHuntSession(
                    huntSession = huntSession,
                    huntSessionState = huntSessionState
                )

                huntSession.start()
                plugin.logger.info("Hunt session started for ${hunterSelectionRequest.twitchUsername}!")

                call.respond(
                    HttpStatusCode.OK, SuccessResponse(
                        success = true,
                        message = "Hunt session started"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()

                call.respond(
                    HttpStatusCode.InternalServerError, SuccessResponse(
                        success = false,
                        message = e.message
                    )
                )
            }
        }
    }
}