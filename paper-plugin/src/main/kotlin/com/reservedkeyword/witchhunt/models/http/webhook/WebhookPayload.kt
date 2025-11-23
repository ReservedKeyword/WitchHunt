package com.reservedkeyword.witchhunt.models.http.webhook

import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val event: String,
    val data: Map<String, String>
)
