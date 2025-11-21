package com.reservedkeyword.witchhunt.models.http.api

import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val currentAttempt: Int?,
    val gameActive: Boolean,
    val worldReady: Boolean
)
