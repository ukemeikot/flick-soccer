package io.github.ukemeikot.flicksoccer.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MatchResult(
    val scoreA: Int,
    val scoreB: Int,
    val vsAi: Boolean,
    val turnNumber: Int,
)

@Serializable
data class MatchHistory(
    val results: List<MatchResult> = emptyList(),
) {
    val played: Int get() = results.size
    val winsA: Int get() = results.count { it.scoreA > it.scoreB }
    val winsB: Int get() = results.count { it.scoreB > it.scoreA }
    val draws: Int get() = results.count { it.scoreA == it.scoreB }
    val last: MatchResult? get() = results.lastOrNull()
}

/** Persists completed match results as JSON. */
interface MatchHistoryRepository {
    fun history(): MatchHistory
    fun record(result: MatchResult)
    fun clear()
}

class MatchHistoryRepositoryImpl(
    private val settings: Settings = Settings(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MatchHistoryRepository {

    override fun history(): MatchHistory {
        val raw = settings.getStringOrNull(KEY) ?: return MatchHistory()
        return runCatching { json.decodeFromString<MatchHistory>(raw) }.getOrDefault(MatchHistory())
    }

    override fun record(result: MatchResult) {
        val updated = history().copy(results = history().results + result)
        settings.putString(KEY, json.encodeToString(updated))
    }

    override fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "match_history"
    }
}
