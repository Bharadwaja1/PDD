package com.moodtunes.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class AudiusTrack(
    val audiusId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val durationSeconds: Int,
    val genre: String,
    val mood: String,
    val tags: String,
    val description: String,
    val streamUrl: String,
    val externalUrl: String?
)

sealed class AudiusSearchResult {
    data class Success(val tracks: List<AudiusTrack>) : AudiusSearchResult()
    data class Failure(val message: String) : AudiusSearchResult()
}

/** Read-only Audius catalog access. Public search and streaming need no user login. */
class AudiusApiService(private val httpClient: OkHttpClient = OkHttpClient()) {
    suspend fun searchTracks(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        fresh: Boolean = false
    ): AudiusSearchResult = withContext(Dispatchers.IO) {
        val cacheKey = "${query.trim().lowercase()}|$limit|$offset"
        responseCache[cacheKey]?.takeIf { !fresh && System.currentTimeMillis() - it.savedAt < CACHE_TTL_MS }?.let {
            return@withContext AudiusSearchResult.Success(it.tracks)
        }
        val url = "$API_BASE/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            .addQueryParameter("offset", offset.coerceAtLeast(0).toString())
            .addQueryParameter("app_name", APP_NAME)
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Audius search failed with HTTP ${response.code}: ${body.take(300)}")
                    return@withContext AudiusSearchResult.Failure("Audius music search is temporarily unavailable.")
                }
                val items = JSONObject(body).optJSONArray("data")
                    ?: return@withContext AudiusSearchResult.Failure("Audius returned an unexpected response.")
                val tracks = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val id = item.optString("id")
                        val title = item.optString("title")
                        if (id.isBlank() || title.isBlank() || item.optBoolean("is_streamable", true).not()) continue
                        val user = item.optJSONObject("user")
                        val artwork = item.optJSONObject("artwork")
                        val permalink = item.optString("permalink").takeIf(String::isNotBlank)
                        add(
                            AudiusTrack(
                                audiusId = id,
                                title = title,
                                artist = user?.optString("name").orEmpty().ifBlank { "Audius artist" },
                                album = item.optString("album").ifBlank { "Audius" },
                                // List rows are small; the 150px asset avoids decoding a
                                // 480px bitmap for every visible track.
                                artworkUrl = artwork?.optString("150x150")?.takeIf(String::isNotBlank)
                                    ?: artwork?.optString("480x480")?.takeIf(String::isNotBlank),
                                durationSeconds = item.optInt("duration", 0).coerceAtLeast(0),
                                genre = item.optString("genre").ifBlank { "Music" },
                                mood = item.optString("mood"),
                                tags = item.optString("tags"),
                                description = item.optString("description"),
                                streamUrl = "$API_BASE/tracks/$id/stream?app_name=$APP_NAME",
                                externalUrl = permalink?.let { "https://audius.co$it" }
                            )
                        )
                    }
                }
                responseCache[cacheKey] = CacheEntry(System.currentTimeMillis(), tracks)
                AudiusSearchResult.Success(tracks)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Audius network error", e)
            AudiusSearchResult.Failure("Could not reach Audius. Check your internet connection.")
        } catch (e: JSONException) {
            Log.e(TAG, "Audius response parsing error", e)
            AudiusSearchResult.Failure("Audius returned an unexpected response.")
        } catch (e: Exception) {
            Log.e(TAG, "Audius search error", e)
            AudiusSearchResult.Failure("Music search could not be completed.")
        }
    }

    companion object {
        private const val TAG = "AudiusApi"
        private const val API_BASE = "https://api.audius.co/v1"
        private const val APP_NAME = "MoodTunes"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private val responseCache = ConcurrentHashMap<String, CacheEntry>()
    }

    private data class CacheEntry(val savedAt: Long, val tracks: List<AudiusTrack>)
}
