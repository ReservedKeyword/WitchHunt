package com.reservedkeyword.witchhunt.models.config

data class LoadoutCategory(
    val armor: List<String>,
    val items: List<String>,
    val itemsPerCategory: Int,
    val weapons: List<String>
)
