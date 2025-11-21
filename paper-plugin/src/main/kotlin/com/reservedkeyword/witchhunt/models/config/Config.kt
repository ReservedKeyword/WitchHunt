package com.reservedkeyword.witchhunt.models.config

data class Config(
    val api: APICategory,
    val loadout: LoadoutCategory,
    val noShowBehavior: NoShowBehaviorCategory,
    val restrictions: RestrictionsCategory,
    val streamerUsername: String,
    val spawn: SpawnCategory,
    val timing: TimingCategory,
    val ui: UICategory
)
