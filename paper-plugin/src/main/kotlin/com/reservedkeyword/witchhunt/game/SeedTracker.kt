package com.reservedkeyword.witchhunt.game

import com.google.gson.GsonBuilder
import com.reservedkeyword.witchhunt.WitchHuntPlugin
import com.reservedkeyword.witchhunt.models.AttemptOutcome
import com.reservedkeyword.witchhunt.models.GameAttempt
import com.reservedkeyword.witchhunt.models.HunterOutcome
import com.reservedkeyword.witchhunt.models.tracking.AttemptHistoryData
import com.reservedkeyword.witchhunt.models.tracking.AttemptSummary
import com.reservedkeyword.witchhunt.utils.InstantAdapter
import com.reservedkeyword.witchhunt.utils.asyncDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class SeedTracker(private val plugin: WitchHuntPlugin) {
    private val historyFile = File(plugin.dataFolder, "attempt_history.json")

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .setPrettyPrinting()
        .create()

    fun generateSummary(gameAttempts: List<GameAttempt>): AttemptSummary {
        val completedAttempts = gameAttempts.filter { it.outcome != AttemptOutcome.IN_PROGRESS }

        return AttemptSummary(
            averageDurationSeconds = completedAttempts.mapNotNull { it.durationSeconds }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toLong(),
            cancelled = completedAttempts.count { it.outcome == AttemptOutcome.CANCELLED },
            deaths = completedAttempts.count { it.outcome == AttemptOutcome.DEATH },
            hunterKills = completedAttempts.sumOf { attempt ->
                attempt.hunterKills.count { it.outcome == HunterOutcome.KILLED_STREAMER }
            },
            longestAttemptSeconds = completedAttempts.mapNotNull { it.durationSeconds }.maxOrNull(),
            shortedAttemptSeconds = completedAttempts.mapNotNull { it.durationSeconds }.minOrNull(),
            totalAttempts = completedAttempts.size,
            totalHunterEncounters = completedAttempts.sumOf { it.hunterKills.size },
            victories = completedAttempts.count { it.outcome == AttemptOutcome.VICTORY }
        )
    }

    suspend fun loadHistory(): List<GameAttempt> = withContext(plugin.asyncDispatcher()) {
        if (!historyFile.exists()) {
            return@withContext emptyList()
        }

        try {
            val fileText = historyFile.readText()
            val jsonData = gson.fromJson(fileText, AttemptHistoryData::class.java)
            plugin.logger.info("Loaded ${jsonData.attempts.size} attempts from history")
            jsonData.attempts
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load attempt history: ${e.message}")
            emptyList()
        }
    }

    suspend fun saveHistory(gameAttempts: List<GameAttempt>) = withContext(plugin.asyncDispatcher()) {
        try {
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }

            val historyData = AttemptHistoryData(gameAttempts)
            val historyJson = gson.toJson(historyData)
            historyFile.writeText(historyJson)
            plugin.logger.info("Saved ${gameAttempts.size} game attempt(s) to history")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}