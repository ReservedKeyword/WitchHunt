package com.reservedkeyword.witchhunt.http.api

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import io.ktor.server.cio.*
import io.ktor.server.engine.*

fun WitchHuntPlugin.startHttpServer(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val listenPort = configManager.getConfig().api.port
    logger.info("Starting HTTP server on port $listenPort...")

    return embeddedServer(CIO, port = listenPort) {
        configureRouting(this@startHttpServer)
    }.start(wait = false)
}
