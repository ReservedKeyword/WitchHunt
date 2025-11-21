package com.reservedkeyword.witchhunt.http.webhook

import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.http.webhook.WebhookEvent
import com.reservedkeyword.witchhunt.utils.asyncDispatcher
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withContext
import java.net.ConnectException

class WebhookClient(private val plugin: WitchHuntPlugin) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 3000
            requestTimeoutMillis = 5000
        }
    }

    private val webhookUrl = plugin.configManager.getConfig().api.twitchBotWebhookUrl

    fun close() {
        httpClient.close()
    }

    suspend fun sendEvent(event: String, data: Map<String, String>? = null) = withContext(plugin.asyncDispatcher()) {
        try {
            plugin.logger.info("Sending webhook event to Twitch bot: $event")

            val payload = WebhookEvent(
                event = event,
                data = data ?: emptyMap()
            )

            val httpResponse = httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (httpResponse.status.isSuccess()) {
                plugin.logger.info("Webhook $event was successfully sent to Twitch bot!")
            } else {
                plugin.logger.warning("Webhook $event was not sent, got ${httpResponse.status} from Twitch bot")
            }
        } catch (_: ConnectException) {
            plugin.logger.warning("Failed to connect to Twitch bot at $webhookUrl")
        } catch (_: HttpRequestTimeoutException) {
            plugin.logger.warning("Request timed out for event: $event")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send $event webhook: ${e.message}")
        }
    }
}