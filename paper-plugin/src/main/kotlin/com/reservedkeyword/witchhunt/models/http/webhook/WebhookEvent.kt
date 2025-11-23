package com.reservedkeyword.witchhunt.models.http.webhook

sealed class WebhookEvent(val eventName: String) {
    abstract fun toData(): Map<String, String>

    data object GameEnded : WebhookEvent("game-ended") {
        override fun toData(): Map<String, String> = emptyMap()
    }

    data class GamePaused(val attemptNumber: Int) : WebhookEvent("game-paused") {
        override fun toData(): Map<String, String> = mapOf(
            "attemptNumber" to attemptNumber.toString()
        )
    }

    data class GameResumed(val attemptNumber: Int) : WebhookEvent("game-resumed") {
        override fun toData(): Map<String, String> = mapOf(
            "attemptNumber" to attemptNumber.toString()
        )
    }

    data class GameStarted(val attemptNumber: Int, val worldSeed: Long) : WebhookEvent("game-started") {
        override fun toData(): Map<String, String> = mapOf(
            "attemptNumber" to attemptNumber.toString(),
            "worldSeed" to worldSeed.toString()
        )
    }

    data class HunterJoined(val huntDurationMillis: Long) : WebhookEvent("hunter-joined") {
        override fun toData(): Map<String, String> = mapOf(
            "huntDurationMillis" to huntDurationMillis.toString()
        )
    }

    data object HunterLeft : WebhookEvent("hunter-left") {
        override fun toData(): Map<String, String> = emptyMap()
    }

    data object NoShowImmediateReselect : WebhookEvent("no-show-immediate-reselect") {
        override fun toData(): Map<String, String> = emptyMap()
    }

    data object StreamerDied : WebhookEvent("streamer-died") {
        override fun toData(): Map<String, String> = emptyMap()
    }

    data object StreamerVictory : WebhookEvent("streamer-victory") {
        override fun toData(): Map<String, String> = emptyMap()
    }
}
