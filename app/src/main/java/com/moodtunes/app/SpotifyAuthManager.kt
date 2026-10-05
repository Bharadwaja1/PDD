package com.moodtunes.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

sealed class SpotifyAuthState {
    object NotConnected : SpotifyAuthState()
    object Connecting : SpotifyAuthState()
    data class Connected(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAt: Long = 0L,
        val userDisplayName: String? = null
    ) : SpotifyAuthState()
    data class Error(val message: String) : SpotifyAuthState()
}

class SpotifyAuthManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("spotify_auth_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val httpClient = OkHttpClient()

    var state by mutableStateOf<SpotifyAuthState>(SpotifyAuthState.NotConnected)
        private set

    init {
        loadSavedTokens()
    }

    private fun loadSavedTokens() {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)

        if (!accessToken.isNullOrEmpty()) {
            if (expiresAt > System.currentTimeMillis()) {
                state = SpotifyAuthState.Connected(accessToken, refreshToken, expiresAt, displayName)
            } else if (!refreshToken.isNullOrEmpty()) {
                scope.launch(Dispatchers.IO) {
                    refreshAccessToken(refreshToken)
                }
            } else {
                state = SpotifyAuthState.NotConnected
            }
        } else {
            state = SpotifyAuthState.NotConnected
        }
    }

    fun startAuthorization(activityContext: Context = context) {
        try {
            val verifier = generateCodeVerifier()
            val challenge = generateCodeChallenge(verifier)
            val authState = UUID.randomUUID().toString().take(16)

            prefs.edit()
                .putString(KEY_PENDING_VERIFIER, verifier)
                .putString(KEY_PENDING_STATE, authState)
                .apply()

            state = SpotifyAuthState.Connecting

            val uri = Uri.parse(AUTH_ENDPOINT).buildUpon()
                .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("state", authState)
                .appendQueryParameter("scope", SCOPES)
                .build()

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(intent)
        } catch (e: Exception) {
            state = SpotifyAuthState.Error(e.localizedMessage ?: "Failed to start Spotify authorization.")
        }
    }

    fun handleCallbackUri(uri: Uri?) {
        if (uri == null || uri.scheme != "moodtunes-login" || uri.host != "callback") return

        val error = uri.getQueryParameter("error")
        if (error != null) {
            state = SpotifyAuthState.Error("Spotify authorization canceled or denied.")
            return
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        val pendingState = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)

        if (code.isNullOrEmpty() || verifier.isNullOrEmpty()) {
            state = SpotifyAuthState.Error("Missing authorization code or verifier.")
            return
        }

        if (returnedState != null && pendingState != null && returnedState != pendingState) {
            state = SpotifyAuthState.Error("State mismatch error during authorization.")
            return
        }

        prefs.edit()
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_STATE)
            .apply()

        state = SpotifyAuthState.Connecting
        scope.launch(Dispatchers.IO) {
            exchangeCodeForToken(code, verifier)
        }
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String) {
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                .add("code_verifier", verifier)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    state = SpotifyAuthState.Error("Token exchange failed (${response.code}).")
                }
                return
            }

            val json = JSONObject(responseBody)
            val accessToken = json.getString("access_token")
            val refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null
            val expiresIn = json.getLong("expires_in")
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

            val displayName = fetchUserProfile(accessToken)

            saveTokens(accessToken, refreshToken, expiresAt, displayName)

            withContext(Dispatchers.Main) {
                state = SpotifyAuthState.Connected(accessToken, refreshToken, expiresAt, displayName)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                state = SpotifyAuthState.Error("Authentication failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!forceRefresh && !accessToken.isNullOrBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_MARGIN_MS) {
            return accessToken
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) {
            withContext(Dispatchers.Main) { state = SpotifyAuthState.NotConnected }
            return null
        }
        return refreshAccessToken(refreshToken)
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? {
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                withContext(Dispatchers.Main) {
                    state = SpotifyAuthState.NotConnected
                }
                return null
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else refreshToken
            val expiresIn = json.getLong("expires_in")
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

            val displayName = prefs.getString(KEY_DISPLAY_NAME, null) ?: fetchUserProfile(newAccessToken)

            saveTokens(newAccessToken, newRefreshToken, expiresAt, displayName)

            withContext(Dispatchers.Main) {
                state = SpotifyAuthState.Connected(newAccessToken, newRefreshToken, expiresAt, displayName)
            }
            return newAccessToken
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                state = SpotifyAuthState.NotConnected
            }
            return null
        }
    }

    private fun fetchUserProfile(accessToken: String): String? {
        return try {
            val request = Request.Builder()
                .url(ME_ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.has("display_name") && !json.isNull("display_name")) json.getString("display_name") else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveTokens(accessToken: String, refreshToken: String?, expiresAt: Long, displayName: String?) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
            if (displayName != null) putString(KEY_DISPLAY_NAME, displayName) else remove(KEY_DISPLAY_NAME)
            apply()
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        state = SpotifyAuthState.NotConnected
    }

    companion object {
        const val REDIRECT_URI = "moodtunes-login://callback"
        const val SCOPES = "user-read-private user-read-email user-read-playback-state"

        private const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
        private const val ME_ENDPOINT = "https://api.spotify.com/v1/me"

        private const val KEY_ACCESS_TOKEN = "spotify_access_token"
        private const val KEY_REFRESH_TOKEN = "spotify_refresh_token"
        private const val KEY_EXPIRES_AT = "spotify_expires_at"
        private const val KEY_DISPLAY_NAME = "spotify_display_name"
        private const val KEY_PENDING_VERIFIER = "spotify_pending_verifier"
        private const val KEY_PENDING_STATE = "spotify_pending_state"
        private const val TOKEN_EXPIRY_MARGIN_MS = 60_000L

        fun generateCodeVerifier(): String {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        fun generateCodeChallenge(codeVerifier: String): String {
            val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
