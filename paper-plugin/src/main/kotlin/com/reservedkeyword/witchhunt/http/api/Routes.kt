package com.reservedkeyword.witchhunt.http.api

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.game.HuntSession
import com.reservedkeyword.witchhunt.models.HuntSessionState
import com.reservedkeyword.witchhunt.models.http.api.HunterSelectionRequest
import com.reservedkeyword.witchhunt.models.http.api.StatusResponse
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bukkit.entity.Player
import java.time.Instant

fun Application.configureRouting(plugin: WitchHuntPlugin) {
    install(ContentNegotiation) {
        json()
    }

    val selectionValidator = HunterSelectionValidator(plugin)

    routing {
        get("/api/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        get("/api/hunt/status") {
            call.handleRequest {
                val huntStatus = StatusResponse(
                    currentAttempt = plugin.huntManager.getCurrentAttemptNumber(),
                    gameActive = plugin.huntManager.isGameActive(),
                    worldReady = plugin.huntManager.isWorldReady()
                )

                call.respond(HttpStatusCode.OK, huntStatus)
            }
        }

        post("/api/hunt/select") {
            call.handleRequest {
                val selectionRequest = call.receive<HunterSelectionRequest>()
                plugin.logger.info("Received hunter selection request. Minecraft username: ${selectionRequest.minecraftUsername}, Twitch username: ${selectionRequest.twitchUsername}")

                selectionValidator.validateGameState().getOrElse { error ->
                    if (error is ValidationException) {
                        call.respondError(
                            statusCode = error.statueCode,
                            message = error.message
                        )
                    } else {
                        throw error
                    }

                    return@handleRequest
                }

                val streamerPlayer = selectionValidator.getStreamerPlayer().getOrElse { error ->
                    if (error is ValidationException) {
                        call.respondError(
                            statusCode = error.statueCode,
                            message = error.message
                        )
                    } else {
                        throw error
                    }

                    return@handleRequest
                }

                startHuntSession(
                    plugin = plugin,
                    selectionRequest = selectionRequest,
                    streamerPlayer = streamerPlayer
                )

                call.respondSuccess("Hunt session was started")
            }
        }
    }
}

private suspend fun startHuntSession(
    plugin: WitchHuntPlugin,
    selectionRequest: HunterSelectionRequest,
    streamerPlayer: Player
) {
    val config = plugin.configManager.getConfig()

    val huntSession = HuntSession(
        config = config,
        minecraftUsername = selectionRequest.minecraftUsername,
        plugin = plugin,
        streamerPlayer = streamerPlayer,
        twitchUsername = selectionRequest.twitchUsername
    )

    val huntSessionState = HuntSessionState(
        expiresAt = Instant.now().plusMillis(config.timing.joinTimeoutMillis),
        hunterMinecraftName = selectionRequest.minecraftUsername,
        hunterTwitchName = selectionRequest.twitchUsername,
        startedAt = Instant.now()
    )

    plugin.huntManager.setActiveHuntSession(
        huntSession = huntSession,
        huntSessionState = huntSessionState
    )

    huntSession.start()
    plugin.logger.info("Hunt session started for ${selectionRequest.twitchUsername} (Minecraft: ${selectionRequest.minecraftUsername})")
}
