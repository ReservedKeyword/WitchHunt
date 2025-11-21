package com.reservedkeyword.witchhunt.models.http.api

import kotlinx.serialization.Serializable

@Serializable
data class SuccessResponse(
    val success: Boolean,
    val message: String? = null
)
