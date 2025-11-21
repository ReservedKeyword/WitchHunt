package com.reservedkeyword.witchhunt.models.http.webhook

import kotlinx.serialization.Serializable

@Serializable
data class WebhookEvent(
    val event: String,
    val data: Map<String, String>
)
