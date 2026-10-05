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

data class SpotifyTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val spotifyUri: String,
    val externalUrl: String?,
    val durationMs: Long,
    val previewUrl: String?
)

sealed class SpotifySearchResult {
    data class Success(val tracks: List<SpotifyTrack>) : SpotifySearchResult()
    data class Failure(val message: String) : SpotifySearchResult()
}

class SpotifyApiService(
    private val authManager: SpotifyAuthManager,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun searchTracks(query: String, limit: Int = 10): SpotifySearchResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Spotify request started: track search for '$query'")
        val token = authManager.getValidAccessToken()
            ?: return@withContext SpotifySearchResult.Failure("Spotify is not connected. Connect Spotify and try again.")

        val firstResult = executeSearch(query, limit, token)
        if (firstResult is HttpResult.Unauthorized) {
            Log.d(TAG, "Spotify access token was rejected; attempting PKCE token refresh")
            val refreshedToken = authManager.getValidAccessToken(forceRefresh = true)
                ?: return@withContext SpotifySearchResult.Failure("Your Spotify session expired. Reconnect Spotify and try again.")
            return@withContext when (val retry = executeSearch(query, limit, refreshedToken)) {
                is HttpResult.Tracks -> SpotifySearchResult.Success(retry.items)
                is HttpResult.Error -> SpotifySearchResult.Failure(retry.message)
                HttpResult.Unauthorized -> SpotifySearchResult.Failure("Your Spotify session expired. Reconnect Spotify and try again.")
            }
        }

        when (firstResult) {
            is HttpResult.Tracks -> SpotifySearchResult.Success(firstResult.items)
            is HttpResult.Error -> SpotifySearchResult.Failure(firstResult.message)
            HttpResult.Unauthorized -> SpotifySearchResult.Failure("Your Spotify session expired. Reconnect Spotify and try again.")
        }
    }

    private fun executeSearch(query: String, limit: Int, accessToken: String): HttpResult {
        val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            // Development Mode apps are limited to 10 results per search as of 2026.
            .addQueryParameter("limit", limit.coerceIn(1, MAX_SEARCH_LIMIT).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Spotify response status: ${response.code}")
                when (response.code) {
                    401 -> HttpResult.Unauthorized
                    403 -> {
                        val spotifyMessage = response.body?.string().orEmpty().spotifyErrorMessage()
                        Log.w(TAG, "Spotify search forbidden: ${spotifyMessage ?: "no error message"}")
                        HttpResult.Error(
                            "Spotify blocked this account from using the app. " +
                                "The app owner needs Spotify Premium, and this Spotify email must be added " +
                                "in the Developer Dashboard under Users Management."
                        )
                    }
                    429 -> {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull()
                        val suffix = retryAfter?.let { " Try again in $it seconds." }.orEmpty()
                        HttpResult.Error("Spotify rate limit reached.$suffix")
                    }
                    else -> if (!response.isSuccessful) {
                        HttpResult.Error("Spotify search failed (HTTP ${response.code}).")
                    } else {
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) return@use HttpResult.Error("Spotify returned an empty response.")
                        parseTracks(body)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Spotify network error: ${e.javaClass.simpleName}: ${e.localizedMessage}")
            HttpResult.Error("Could not reach Spotify. Check your internet connection and try again.")
        } catch (e: JSONException) {
            Log.e(TAG, "Malformed Spotify response: ${e.localizedMessage}")
            HttpResult.Error("Spotify returned an unexpected response.")
        } catch (e: Exception) {
            Log.e(TAG, "Spotify search error: ${e.javaClass.simpleName}: ${e.localizedMessage}")
            HttpResult.Error("Spotify search could not be completed.")
        }
    }

    private fun parseTracks(body: String): HttpResult.Tracks {
        val root = JSONObject(body)
        val items = root.getJSONObject("tracks").getJSONArray("items")
        Log.d(TAG, "Spotify tracks returned: ${items.length()}")
        val tracks = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val spotifyId = item.optString("id")
                val title = item.optString("name")
                if (spotifyId.isBlank() || title.isBlank()) continue
                val artists = item.optJSONArray("artists")
                val artistNames = buildList {
                    if (artists != null) for (artistIndex in 0 until artists.length()) {
                        artists.optJSONObject(artistIndex)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                val album = item.optJSONObject("album")
                val images = album?.optJSONArray("images")
                val artworkUrl = images?.optJSONObject(0)?.optNullableString("url")
                add(
                    SpotifyTrack(
                        spotifyId = spotifyId,
                        title = title,
                        artist = artistNames.joinToString(", ").ifBlank { "Unknown artist" },
                        album = album?.optString("name").orEmpty().ifBlank { "Spotify" },
                        artworkUrl = artworkUrl,
                        spotifyUri = item.optString("uri"),
                        externalUrl = item.optJSONObject("external_urls")?.optNullableString("spotify"),
                        durationMs = item.optLong("duration_ms", 0L).coerceAtLeast(0L),
                        previewUrl = item.optNullableString("preview_url")
                    )
                )
            }
        }
        Log.d(TAG, "Spotify parsing result: ${tracks.size} valid tracks")
        return HttpResult.Tracks(tracks)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun String.spotifyErrorMessage(): String? = runCatching {
        JSONObject(this).optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
    }.getOrNull()

    private sealed class HttpResult {
        data class Tracks(val items: List<SpotifyTrack>) : HttpResult()
        data class Error(val message: String) : HttpResult()
        object Unauthorized : HttpResult()
    }

    companion object {
        private const val TAG = "SpotifyApi"
        private const val SEARCH_ENDPOINT = "https://api.spotify.com/v1/search"
        private const val MAX_SEARCH_LIMIT = 10
    }
}
