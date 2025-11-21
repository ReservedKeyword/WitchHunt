package com.reservedkeyword.witchhunt.models.config

data class RestrictionsCategory(
    val canBreakBlocks: Boolean,
    val canOpenChests: Boolean,
    val canPlaceBlocks: Boolean,
    val canUseCrafting: Boolean
)
