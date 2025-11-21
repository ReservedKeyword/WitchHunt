package com.reservedkeyword.witchhunt.models.http.api

import kotlinx.serialization.Serializable

@Serializable
data class HunterSelectionRequest(
    val minecraftUsername: String,
    val twitchUsername: String
)
