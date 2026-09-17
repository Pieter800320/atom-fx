package com.pieter.atomfx.data

import android.content.Context
import com.pieter.atomfx.data.model.Signals
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val STALE_AFTER: Duration = Duration.ofMinutes(90) // hourly scan cadence + buffer, Architecture §8.4
private const val CACHE_FILE_NAME = "signals_cache.json"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Fetches `signals.json` over plain `HttpURLConnection` (Architecture §7's "dependency-light,
 * no heavyweight SDK" style — one GET doesn't need a full HTTP client library), caches the last
 * good response to a plain file, and never returns nothing: a failed fetch falls back to the
 * cache (flagged stale); no cache at all is the only path to [SignalsResult.Unavailable].
 *
 * [urlProvider] reads the current URL each call (Functional Spec §9's "Data source" setting) —
 * a lambda rather than a fixed value so a Settings change takes effect on the very next refresh
 * with no repository re-creation.
 */
class SignalsRepository(context: Context, private val urlProvider: () -> String = { DEFAULT_SIGNALS_URL }) {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

    suspend fun fetch(): SignalsResult = withContext(Dispatchers.IO) {
        val networkBody = runCatching { httpGet(urlProvider()) }
            .onFailure { android.util.Log.w("SignalsRepository", "fetch failed, falling back to cache", it) }
            .getOrNull()
        val networkSignals = networkBody?.let(::parseOrNull)

        // 2026-09-17 bugfix — the cache is only overwritten by a response that actually
        // parsed. Previously the raw download was cached unconditionally before parsing was
        // even attempted, so one malformed response (a single field of an unexpected type
        // anywhere in the large, fast-changing signals.json shape) would both fail this fetch
        // AND permanently poison the offline fallback until the next successful fetch — a
        // transient bad field could blank the whole app rather than degrading to the last
        // good snapshot the app already knows how to show (Stale). A parse failure now falls
        // back to the cache exactly like a network failure already did.
        val signals = if (networkSignals != null) {
            cacheFile.writeText(networkBody)
            networkSignals
        } else {
            if (networkBody != null) {
                android.util.Log.w("SignalsRepository", "network response failed to parse, falling back to cache")
            }
            cacheFile.takeIf { it.exists() }?.readText()?.let(::parseOrNull)
                ?: return@withContext SignalsResult.Unavailable
        }

        if (isFresh(signals.updated)) SignalsResult.Fresh(signals) else SignalsResult.Stale(signals)
    }

    private fun parseOrNull(body: String): Signals? =
        runCatching { json.decodeFromString(Signals.serializer(), body) }
            .onFailure { android.util.Log.w("SignalsRepository", "parse failed", it) }
            .getOrNull()

    private fun isFresh(updated: String?): Boolean {
        val timestamp = updated ?: return false
        val instant = runCatching { OffsetDateTime.parse(timestamp).toInstant() }.getOrNull() ?: return false
        return Duration.between(instant, Instant.now()) <= STALE_AFTER
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
