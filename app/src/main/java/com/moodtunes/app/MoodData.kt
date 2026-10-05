package com.moodtunes.app

import android.content.Context
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth

data class Mood(val name: String, val face: String, val color: Color)
val moods = listOf(Mood("Happy", "😊", Color(0xFFF4D35E)), Mood("Sad", "😢", Color(0xFF6C8BEF)), Mood("Calm", "😌", Color(0xFF43D5BD)), Mood("Angry", "😠", Color(0xFFFF735C)), Mood("Stressed", "😣", Color(0xFFAE86E8)), Mood("Energetic", "🤩", Color(0xFFFF9E45)), Mood("Tired", "😴", Color(0xFF83A8C8)), Mood("Neutral", "😐", Color(0xFFB3BDB4)))
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val language: String,
    val genre: String,
    val mood: String,
    val color: Color,
    val duration: Int = 32,
    val audioResId: Int? = null,
    val sourceId: String? = null,
    val artworkUrl: String? = null,
    val externalUrl: String? = null,
    val previewUrl: String? = null,
    val streamUrl: String? = null
)
// Kept as an empty compatibility catalog while all music is sourced from Audius/user saves.
val songs = emptyList<Song>()
val moodCatalogLanguages = listOf("Telugu", "Tamil")
data class UserPlaylist(val name: String, val description: String, val ids: List<Int>, val private: Boolean, val documentId: String = "", val trackIds: List<String> = emptyList())
data class WellnessEntry(val title: String, val value: String, val type: String)

internal fun prioritizeMoodSongs(tracks: List<Song>): List<Song> =
    (moodCatalogLanguages.flatMap { language -> tracks.filter { it.language == language } } +
        tracks.filter { it.language !in moodCatalogLanguages })
        .distinctBy { it.sourceId ?: it.id }.take(MoodStore.MAX_CACHED_TRACKS_PER_MOOD)

/** One app-owned player. Route changes never recreate the playback engine. */
class MoodStore(val context: Context) {
    private val prefs = context.getSharedPreferences("moodtunes", Context.MODE_PRIVATE)
    val backend = FirebaseBackend(context)
    private val audiusApi = AudiusApiService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingSong: Song? = null
    var route by mutableStateOf("Splash")
    var signedIn by mutableStateOf(backend.currentUser != null)
    var name by mutableStateOf(prefs.getString("name", "Listener") ?: "Listener")
    var email by mutableStateOf(prefs.getString("email", "") ?: "")
    var languages by mutableStateOf(prefs.getString("languages", "Telugu,Tamil")!!.split(",").filter { it.isNotEmpty() }.toSet())
    var genres by mutableStateOf(prefs.getString("genres", "Instrumental")!!.split(",").filter { it.isNotEmpty() }.toSet())
    var artists by mutableStateOf(prefs.getStringSet("artists", emptySet())!!.toSet())
    var goals by mutableStateOf(prefs.getStringSet("goals", emptySet())!!.toSet())
    var mood by mutableStateOf(moods[2])
    var queue by mutableStateOf<List<Song>>(emptyList())
    var queueIsFromAudius by mutableStateOf(false)
    var onlineCatalog by mutableStateOf<List<Song>>(emptyList())
    // The catalog can be a large JSON document. Loading it on the main thread made
    // the first frame wait for parsing, so hydrate it after the UI is visible.
    var moodCatalog by mutableStateOf<Map<String, List<Song>>>(emptyMap())
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searchLoading by mutableStateOf(false)
    private var searchJob: Job? = null
    private var moodSelectionJob: Job? = null
    var current by mutableStateOf<Song?>(null)
    var playing by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var position by mutableStateOf(0f)
    var duration by mutableStateOf(32f)
    var volume by mutableStateOf(.65f)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var dialog by mutableStateOf<String?>(null)
    var liked by mutableStateOf(prefs.getStringSet("liked", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet())
    var likedSongs by mutableStateOf(readLikedSongs())
    var recent by mutableStateOf(prefs.getString("recent", "")!!.split(",").mapNotNull { it.toIntOrNull() })
    var recentSearches by mutableStateOf(prefs.getString("searches", "")!!.split("|").filter { it.isNotBlank() })
    var playlists by mutableStateOf(readArray("playlists").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { json -> UserPlaylist(json.getString("name"), json.optString("description"), json.getJSONArray("ids").let { ids -> (0 until ids.length()).map { j -> ids.getInt(j) } }, json.optBoolean("private", true), json.optString("documentId"), json.optJSONArray("trackIds")?.let { ids -> (0 until ids.length()).map { j -> ids.getString(j) } } ?: emptyList()) } } })
    var sessions by mutableStateOf(readArray("sessions").let { a -> (0 until a.length()).map { a.getJSONObject(it) } })
    var journal by mutableStateOf(readArray("journal").let { a -> (0 until a.length()).map { a.getJSONObject(it) } })
    var wellnessEntries by mutableStateOf<List<WellnessEntry>>(emptyList())
    var feedback by mutableStateOf(readArray("feedback").let { a -> (0 until a.length()).map { a.getJSONObject(it) } })
    var selectedPlaylist by mutableStateOf<UserPlaylist?>(null)
    var listeningSeconds by mutableStateOf(prefs.getInt("listening", 0))
    var notifications by mutableStateOf(prefs.getBoolean("notifications", true))
    var reducedMotion by mutableStateOf(prefs.getBoolean("reducedMotion", false))
    var discoMode by mutableStateOf(prefs.getBoolean("discoMode", true))
    var themeMode by mutableStateOf(prefs.getString("themeMode", "Ambient") ?: "Ambient")
    var activeSession: Long? = null
    private var excluded = mutableSetOf<Int>()
    private var cloudReady = false
    private var favoriteDocumentIds = emptyMap<Int, String>()
    private var activeHistoryDocumentId: String? = null
    private var lightingMode = "beat"
    var recentTrackIds by mutableStateOf<List<String>>(emptyList())
    private var activeSyncUid: String? = null
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.uid
        if (uid == null) {
            if (activeSyncUid != null) { backend.stopListening(); activeSyncUid = null; signedIn = false }
        } else if (uid != activeSyncUid) {
            liked = emptySet(); likedSongs = emptyList(); playlists = emptyList(); sessions = emptyList(); journal = emptyList(); wellnessEntries = emptyList()
            signedIn = true
            refreshFromCloud { if (route == "Login") route = "Home" }
        }
    }
    private var cloudSyncJob: Job? = null

    init {
        if (prefs.getInt("moodCatalogVersion", 0) != 3) {
            prefs.edit().remove("moodCatalog").remove("moodCatalogOffsets").putInt("moodCatalogVersion", 3).apply()
        }
        if (signedIn) refreshFromCloud()
        if (backend.configured) FirebaseAuth.getInstance().addAuthStateListener(authListener)
        connectPlayer()
        scope.launch {
            val cached = withContext(Dispatchers.IO) { readMoodCatalog() }
            // Preserve anything fetched while the disk cache was being decoded.
            moodCatalog = cached + moodCatalog
            persistMoodCatalog()
        }
        scope.launch { while (isActive) { delay(1000); controller?.let { player -> position = player.currentPosition.coerceAtLeast(0) / 1000f; if (player.isPlaying) { listeningSeconds++; if (listeningSeconds % 10 == 0) { prefs.edit().putInt("listening", listeningSeconds).apply(); syncCloud() } } } } }
    }
    private fun connectPlayer() {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.volume = volume
                mediaController.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(value: Boolean) { playing = value; loading = false; syncCloud() }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val changed = mediaItem?.mediaId?.toIntOrNull()?.let { id -> queue.firstOrNull { it.id == id } } ?: return
                        if (changed.id != current?.id) {
                            current = changed
                            duration = mediaController.duration.takeIf { it > 0 }?.div(1000f) ?: changed.duration.toFloat()
                            position = 0f
                            rememberStartedTrack(changed)
                        }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        loading = state == Player.STATE_BUFFERING
                        if (mediaController.duration > 0) duration = mediaController.duration / 1000f
                        if (state == Player.STATE_ENDED && !repeat && queue.isNotEmpty()) scope.launch { skip(1) }
                    }
                    override fun onPlayerError(playbackError: PlaybackException) { loading = false; playing = false; error = "Playback failed: ${playbackError.errorCodeName}" }
                })
                pendingSong?.let { pendingSong = null; startPlayback(it) }
            }.onFailure { error = "Could not connect to Android media playback."; loading = false }
        }, ContextCompat.getMainExecutor(context))
    }
    private fun readArray(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
    private fun readLikedSongs(): List<Song> = runCatching {
        val array = JSONArray(prefs.getString("likedSongs", "[]"))
        (0 until array.length()).map { index -> songFromJson(array.getJSONObject(index)) }
    }.getOrDefault(emptyList())
    private fun readMoodCatalog(): Map<String, List<Song>> = runCatching {
        val root = JSONObject(prefs.getString("moodCatalog", "{}"))
        root.keys().asSequence().associateWith { moodName ->
            val array = root.optJSONArray(moodName) ?: JSONArray()
            prioritizeMoodSongs((0 until array.length()).mapNotNull { index -> runCatching { songFromJson(array.getJSONObject(index)) }.getOrNull() })
        }
    }.getOrDefault(emptyMap())
    private fun persistMoodCatalog() {
        val root = JSONObject()
        moodCatalog.forEach { (moodName, tracks) -> root.put(moodName, JSONArray(tracks.map(::songToJson))) }
        prefs.edit().putString("moodCatalog", root.toString()).apply()
    }
    private fun songToJson(song: Song) = JSONObject()
        .put("id", song.id).put("title", song.title).put("artist", song.artist).put("album", song.album)
        .put("language", song.language).put("genre", song.genre).put("mood", song.mood)
        .put("color", song.color.value.toLong()).put("duration", song.duration)
        .put("audioResId", song.audioResId ?: JSONObject.NULL).put("sourceId", song.sourceId ?: JSONObject.NULL)
        .put("artworkUrl", song.artworkUrl ?: JSONObject.NULL).put("externalUrl", song.externalUrl ?: JSONObject.NULL)
        .put("previewUrl", song.previewUrl ?: JSONObject.NULL).put("streamUrl", song.streamUrl ?: JSONObject.NULL)
    private fun songFromJson(json: JSONObject) = Song(
        id = json.getInt("id"), title = json.getString("title"), artist = json.optString("artist"),
        album = json.optString("album"), language = json.optString("language"), genre = json.optString("genre"),
        mood = json.optString("mood"), color = Color(json.optLong("color", 0xFF43D5BD)),
        duration = json.optInt("duration", 0), audioResId = json.optInt("audioResId").takeIf { !json.isNull("audioResId") },
        sourceId = json.optString("sourceId").takeIf { !json.isNull("sourceId") },
        artworkUrl = json.optString("artworkUrl").takeIf { !json.isNull("artworkUrl") },
        externalUrl = json.optString("externalUrl").takeIf { !json.isNull("externalUrl") },
        previewUrl = json.optString("previewUrl").takeIf { !json.isNull("previewUrl") },
        streamUrl = json.optString("streamUrl").takeIf { !json.isNull("streamUrl") }
    )
    private fun songFromCloud(row: Map<String, Any?>): Song? {
        val trackId = row["trackId"] as? String ?: row["documentId"] as? String ?: return null
        val id = if (row["source"] == "audius") trackId.hashCode() else trackId.toIntOrNull() ?: trackId.hashCode()
        return Song(id, row["title"] as? String ?: return null, row["artist"] as? String ?: "",
        row["album"] as? String ?: "", row["language"] as? String ?: "", row["genre"] as? String ?: "",
            row["mood"] as? String ?: "", Color(0xFF43D5BD), (row["seconds"] as? Number)?.toInt() ?: 32,
            sourceId = trackId, artworkUrl = row["artwork"] as? String,
            externalUrl = row["externalUrl"] as? String, previewUrl = row["previewUrl"] as? String,
            streamUrl = (row["streamUrl"] as? String) ?: if (row["source"] == "audius")
                "https://api.audius.co/v1/tracks/${Uri.encode(trackId)}/stream?app_name=MoodTunes" else null)
    }
    private fun trackId(song: Song) = song.sourceId ?: song.id.toString()
    private fun favoriteFields(song: Song): Map<String, Any> = mapOf(
        "trackId" to trackId(song), "title" to song.title, "artist" to song.artist,
        "album" to song.album, "mood" to song.mood, "seconds" to song.duration,
        "artwork" to (song.artworkUrl ?: ""), "source" to (if (song.sourceId != null) "audius" else "android")
    )
    private fun saveObjects(key: String, values: List<JSONObject>) { prefs.edit().putString(key, JSONArray(values).toString()).apply() }
    private fun jsonValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { key -> jsonValue(value.get(key)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }
    private fun jsonMap(value: JSONObject): Map<String, Any?> = jsonValue(value) as Map<String, Any?>
    // Player position and search history stay local; shared data is written by each user action.
    private fun syncCloud(immediate: Boolean = false) = Unit
    private fun refreshFromCloud(after: () -> Unit = {}) {
        activeSyncUid = backend.currentUser?.uid ?: return
        var opened = false
        backend.listen(
            profile = { data ->
                name = data["name"] as? String ?: name
                email = data["email"] as? String ?: email
                if (!opened) { opened = true; after() }
            },
            favorites = { rows ->
                favoriteDocumentIds = rows.mapNotNull { row ->
                    val track = row["trackId"] as? String ?: row["documentId"] as? String
                    val id = if (row["source"] == "audius") track?.hashCode() else track?.toIntOrNull() ?: track?.hashCode()
                    val doc = row["documentId"] as? String
                    if (id != null && doc != null) id to doc else null
                }.toMap()
                likedSongs = rows.mapNotNull { row -> songFromCloud(row) }
                liked = likedSongs.map { it.id }.toSet()
                recent = recentTrackIds.mapNotNull { track -> likedSongs.find { it.sourceId == track }?.id ?: track.toIntOrNull() }
                prefs.edit().putStringSet("liked", liked.map(Int::toString).toSet()).putString("likedSongs", JSONArray(likedSongs.map(::songToJson)).toString()).apply()
                if (BuildConfig.DEBUG) android.util.Log.d("SYNC_DEBUG", "state favorites=${likedSongs.size} recent=${recent.size}")
            },
            playlists = { rows ->
                val localPlaylists = playlists.associateBy { it.documentId }
                playlists = rows.mapNotNull { row ->
                    val title = row["name"] as? String ?: row["title"] as? String ?: return@mapNotNull null
                    val documentId = row["documentId"] as? String ?: ""
                    val local = localPlaylists[documentId]
                    val rawTracks = row["trackIds"] as? List<*> ?: row["tracks"] as? List<*> ?: row["songIds"] as? List<*> ?: emptyList<Any>()
                    val tracks = rawTracks.mapNotNull { item -> when (item) {
                        is String -> item
                        is Number -> item.toString()
                        is Map<*, *> -> (item["trackId"] ?: item["id"])?.toString()
                        else -> null
                    } }
                    val known = allKnownSongs().associateBy(::trackId)
                    UserPlaylist(title, row["description"] as? String ?: local?.description ?: "", tracks.mapNotNull { known[it]?.id ?: it.toIntOrNull() },
                        row["private"] as? Boolean ?: local?.private ?: true, documentId, tracks)
                }
                savePlaylists(false)
                if (BuildConfig.DEBUG) android.util.Log.d("SYNC_DEBUG", "state playlists=${playlists.size}")
            },
            moods = { rows ->
                val localSessions = sessions.associateBy { it.optString("documentId") }
                sessions = rows.map { row ->
                    val docId = row["documentId"] as? String ?: ""
                    val local = localSessions[docId]
                    JSONObject().put("documentId", docId).put("id", local?.optLong("id") ?: (row["id"] as? Number)?.toLong() ?: docId.hashCode().toLong())
                    .put("initial", row["mood"] ?: row["initialMood"] ?: row["initial"] ?: "")
                    .put("final", local?.optString("final")?.takeIf { it.isNotBlank() } ?: row["finalMood"] ?: row["final"] ?: "In progress")
                    .put("started", (row["createdAt"] as? Timestamp)?.toDate()?.time ?: (row["startedAt"] as? Number)?.toLong() ?: (row["started"] as? Number)?.toLong() ?: 0L)
                    .put("ended", row["endedAt"] ?: row["ended"] ?: JSONObject.NULL)
                    .put("helpful", local?.opt("helpful") ?: row["helpful"] ?: JSONObject.NULL)
                    .put("songs", local?.optJSONArray("songs") ?: JSONArray(row["songsPlayed"] as? List<*> ?: row["songs"] as? List<*> ?: emptyList<Any>()))
                }
                saveObjects("sessions", sessions)
                if (BuildConfig.DEBUG) android.util.Log.d("SYNC_DEBUG", "state moods=${sessions.size}")
            },
            wellness = { rows ->
                wellnessEntries = rows.mapNotNull { row ->
                    val title = row["title"] as? String ?: row["name"] as? String ?: row["type"] as? String ?: return@mapNotNull null
                    WellnessEntry(title, row["value"]?.toString() ?: row["text"]?.toString() ?: "", row["type"] as? String ?: "")
                }
                journal = rows.filter { it["type"] == "journal" || it["text"] is String }.map { row -> JSONObject()
                    .put("text", row["value"] ?: row["text"] ?: "").put("mood", row["mood"] ?: "")
                    .put("time", (row["createdAt"] as? Timestamp)?.toDate()?.time ?: (row["time"] as? Number)?.toLong() ?: 0L) }
                saveObjects("journal", journal)
                if (BuildConfig.DEBUG) android.util.Log.d("SYNC_DEBUG", "state wellness=${wellnessEntries.size} journal=${journal.size}")
            },
            settings = { data ->
                recentTrackIds = (data["recent"] as? List<*>)?.filterIsInstance<String>() ?: recentTrackIds
                recent = recentTrackIds.mapNotNull { track -> allKnownSongs().find { it.sourceId == track }?.id ?: track.toIntOrNull() }
                (data["selectedSong"] as? Map<*, *>)?.let { selected ->
                    if (!playing && !loading) current = songFromCloud(selected.entries.mapNotNull { (key, value) ->
                        (key as? String)?.let { it to value }
                    }.toMap() + ("trackId" to selected["id"])) ?: current
                }
                languages = (data["languages"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: languages
                genres = (data["genres"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: genres
                artists = (data["artists"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: artists
                goals = (data["goals"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: goals
                notifications = data["notifications"] as? Boolean ?: notifications
                reducedMotion = data["reducedMotion"] as? Boolean ?: reducedMotion
                discoMode = data["discoMode"] as? Boolean ?: discoMode
                themeMode = data["themeMode"] as? String ?: themeMode
                (data["volume"] as? Number)?.toFloat()?.let { volume = (it / 100f).coerceIn(0f, 1f); controller?.volume = volume }
                lightingMode = data["lightingMode"] as? String ?: lightingMode
                discoMode = lightingMode != "off"
                cloudReady = true
                if (BuildConfig.DEBUG) android.util.Log.d("SYNC_DEBUG", "state preferences volume=$volume lightingMode=$lightingMode recent=${recent.size}")
            },
            failure = { error = it }
        )
    }
    private fun applyCloudState(data: Map<String, Any?>) {
        fun ids(key: String) = (data[key] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        name = data["name"] as? String ?: name; email = data["email"] as? String ?: email
        liked = ids("likedSongIds").toSet(); recent = ids("recentSongIds"); recentSearches = (data["recentSearches"] as? List<*>)?.filterIsInstance<String>() ?: recentSearches
        playlists = (data["playlists"] as? List<*>)?.mapNotNull { raw -> (raw as? Map<*, *>)?.let { UserPlaylist(it["name"] as? String ?: return@let null, it["description"] as? String ?: "", (it["songIds"] as? List<*>)?.mapNotNull { id -> (id as? Number)?.toInt() } ?: emptyList(), it["private"] as? Boolean ?: true) } } ?: playlists
        sessions = (data["moodSessions"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { map -> JSONObject(map) } } ?: sessions
        journal = (data["journalEntries"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { map -> JSONObject(map) } } ?: journal
        feedback = (data["songFeedback"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { map -> JSONObject(map) } } ?: feedback
        (data["settings"] as? Map<*, *>)?.let { settings ->
            languages = (settings["languages"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: languages
            genres = (settings["genres"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: genres
            artists = (settings["artists"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: artists
            goals = (settings["goals"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: goals
            notifications = settings["notifications"] as? Boolean ?: notifications; reducedMotion = settings["reducedMotion"] as? Boolean ?: reducedMotion; discoMode = settings["discoMode"] as? Boolean ?: discoMode; themeMode = settings["themeMode"] as? String ?: themeMode
        }
        (data["player"] as? Map<*, *>)?.let { player ->
            mood = moods.find { it.name == player["selectedMood"] } ?: mood
        val knownSongs = (likedSongs + onlineCatalog + queue + moodCatalog.values.flatten() + songs).distinctBy { it.sourceId ?: it.id }
            current = knownSongs.find { it.id == (player["currentSongId"] as? Number)?.toInt() }
            queue = (player["queueIds"] as? List<*>)?.mapNotNull { id -> knownSongs.find { it.id == (id as? Number)?.toInt() } } ?: queue
            position = (player["positionSeconds"] as? Number)?.toFloat() ?: position; volume = (player["volume"] as? Number)?.toFloat() ?: volume
            shuffle = player["shuffle"] as? Boolean ?: shuffle; repeat = player["repeat"] as? Boolean ?: repeat
        }
        listeningSeconds = (data["listeningSeconds"] as? Number)?.toInt() ?: listeningSeconds
        prefs.edit().putString("name", name).putString("email", email).putStringSet("liked", liked.map { it.toString() }.toSet()).putString("recent", recent.joinToString(",")).putString("searches", recentSearches.joinToString("|")).putInt("listening", listeningSeconds).apply()
        savePlaylists(false); saveObjects("sessions", sessions); saveObjects("journal", journal); saveObjects("feedback", feedback)
    }
    fun login(newName: String, newEmail: String, remember: Boolean = true) {
        backend.stopListening()
        liked = emptySet(); likedSongs = emptyList(); playlists = emptyList(); sessions = emptyList(); journal = emptyList()
        name = newName.ifBlank { newEmail.substringBefore('@').replaceFirstChar { it.uppercase() } }; email = newEmail
        signedIn = true
        prefs.edit().putBoolean("signedIn", remember).putString("name", name).putString("email", email).apply()
        refreshFromCloud { route = "Home" }
    }
    fun onboard() { prefs.edit().putBoolean("onboarded", true).putString("languages", languages.joinToString(",")).putString("genres", genres.joinToString(",")).putStringSet("artists", artists).putStringSet("goals", goals).apply(); saveSettings(); route = "Home" }
    fun logout() { controller?.run { stop(); clearMediaItems() }; playing = false; loading = false; current = null; if (activeSession != null) finishSession("Not recorded", null); backend.logout(); cloudReady = false; signedIn = false; liked = emptySet(); likedSongs = emptyList(); playlists = emptyList(); sessions = emptyList(); journal = emptyList(); prefs.edit().clear().apply(); route = "Login" }
    fun selectMood(value: Mood) {
        if (mood.name == value.name && queueIsFromAudius && queue.isNotEmpty() &&
            moodCatalogLanguages.all { language -> queue.any { it.language == language } }) {
            route = "Mood playlist"
            loading = false
            if (current == null) play(queue.first())
            return
        }
        if (activeSession != null) finishSession("Not recorded", null)
        mood = value
        moodSelectionJob?.cancel()
        error = null
        excluded.clear()
        queue = emptyList()
        queueIsFromAudius = false
        loading = true
        val id = System.currentTimeMillis(); activeSession = id
        sessions = sessions + JSONObject().put("id", id).put("initial", value.name).put("started", id).put("songs", JSONArray())
        saveObjects("sessions", sessions)
        activeHistoryDocumentId = backend.createMood(value.name)
        activeHistoryDocumentId?.let { docId -> sessions = sessions.map { if (it.optLong("id") == id) JSONObject(it.toString()).put("documentId", docId) else it }; saveObjects("sessions", sessions) }
        route = "Mood playlist"
        val cached = prioritizeMoodSongs(moodCatalog[value.name].orEmpty())
        if (cached.isNotEmpty() && moodCatalogLanguages.all { language -> cached.any { it.language == language } }) {
            queue = cached
            queueIsFromAudius = true
            loading = false
            play(cached.first(), cached)
            onlineCatalog = (onlineCatalog + cached).distinctBy { it.sourceId ?: it.id }
        } else {
            moodSelectionJob = scope.launch {
                val firstBatch = fetchMoodTracks(value)
                // A slower response for a previously tapped mood must not replace the latest mix.
                if (mood.name != value.name) return@launch
                if (firstBatch.isEmpty()) {
                    loading = false
                    error = "Audius could not load tracks right now. Check your connection and try again."
                } else {
                    saveMoodTracks(value.name, firstBatch)
                    queue = firstBatch
                    queueIsFromAudius = true
                    loading = false
                    play(firstBatch.first(), firstBatch)
                }
            }
        }
    }

    private suspend fun fetchMoodTracks(value: Mood): List<Song> {
        val (regionalTracks, generalTracks) = coroutineScope {
            val general = async {
                audiusTracks(audiusQueryForMood(value.name), SEARCH_PAGE_SIZE)
            }
            moodCatalogLanguages.map { language ->
                async {
                    // Audius searches containing both a language and a mood often return nothing.
                    // Search by language first, then rank the verified tracks for this mood.
                    val primary = audiusTracks(language, SEARCH_PAGE_SIZE).filter { it.matchesLanguage(language) }
                    val supplemental = if (primary.size < 5) {
                        audiusTracks("$language songs", SEARCH_PAGE_SIZE).filter { it.matchesLanguage(language) }
                    } else emptyList()
                    language to (primary + supplemental).distinctBy { it.audiusId }
                }
            }.awaitAll().flatMap { (language, tracks) ->
                tracks.map { language to it }
            }.distinctBy { it.second.audiusId } to general.await()
        }

        fun ranked(tracks: List<AudiusTrack>) = tracks.groupBy { it.moodScore(value.name) }
            .toSortedMap(compareByDescending<Int> { it })
            .values.flatMap { sameScore -> sameScore.shuffled() }

        val regionalSongs = moodCatalogLanguages.flatMap { language ->
            ranked(regionalTracks.filter { it.first == language }.map { it.second })
                .map { it.toSong(value, language) }
        }
        val regionalIds = regionalTracks.mapTo(mutableSetOf()) { it.second.audiusId }
        val generalFallback = ranked(generalTracks.filterNot { it.audiusId in regionalIds })
            .map { it.toSong(value) }
        return prioritizeMoodSongs(regionalSongs + generalFallback)
    }

    private fun saveMoodTracks(moodName: String, tracks: List<Song>) {
        moodCatalog = moodCatalog + (moodName to prioritizeMoodSongs(moodCatalog[moodName].orEmpty() + tracks))
        persistMoodCatalog()
    }

    private suspend fun audiusTracks(query: String, limit: Int): List<AudiusTrack> =
        when (val result = audiusApi.searchTracks(query, limit)) {
            is AudiusSearchResult.Success -> result.tracks
            is AudiusSearchResult.Failure -> emptyList()
        }

    private fun AudiusTrack.matchesLanguage(language: String): Boolean {
        val text = "$title $tags $description $genre".lowercase()
        val regionalMarkers = when (language) {
            "Telugu" -> listOf("telugu", "tollywood", "prabhas")
            "Tamil" -> listOf("tamil", "kollywood")
            else -> listOf(language.lowercase())
        }
        return regionalMarkers.any { it in text }
    }

    private fun AudiusTrack.moodScore(selectedMood: String): Int {
        val text = "$title $mood $tags $description $genre".lowercase()
        val matchingMoodLabels = when (selectedMood) {
            "Happy" -> setOf("excited", "upbeat", "empowering", "easygoing", "romantic", "cool")
            "Sad" -> setOf("melancholy", "tender", "romantic", "stirring")
            "Calm" -> setOf("peaceful", "easygoing", "tender", "romantic", "sophisticated", "cool")
            "Angry" -> setOf("fiery", "aggressive", "defiant", "rowdy")
            "Energetic" -> setOf("excited", "fiery", "empowering", "rowdy", "cool")
            "Stressed" -> setOf("peaceful", "tender", "easygoing", "sophisticated")
            "Tired" -> setOf("peaceful", "melancholy", "easygoing", "tender")
            else -> setOf("cool", "easygoing", "peaceful", "sophisticated")
        }
        val keywords = when (selectedMood) {
            "Happy" -> listOf("happy", "upbeat", "excited", "empowering", "party", "dance", "joy")
            "Sad" -> listOf("sad", "melancholy", "emotional", "heartbreak", "tender", "cry")
            "Calm" -> listOf("calm", "peaceful", "ambient", "chill", "easygoing", "melody", "relax")
            "Angry" -> listOf("angry", "fiery", "aggressive", "defiant", "rowdy", "rage")
            "Energetic" -> listOf("energetic", "excited", "fiery", "empowering", "workout", "dance", "fast")
            "Stressed" -> listOf("peaceful", "relax", "meditation", "calm", "tender", "ambient")
            "Tired" -> listOf("sleep", "peaceful", "ambient", "slow", "dream", "easygoing")
            else -> listOf("cool", "easygoing", "chill", "melody", "instrumental")
        }
        return keywords.count { it in text } + if (mood.lowercase() in matchingMoodLabels) 2 else 0
    }

    private fun audiusQueryForMood(moodName: String): String = when (moodName) {
        "Happy" -> "happy upbeat"
        "Sad" -> "sad emotional"
        "Romantic" -> "romantic love"
        "Calm" -> "calm ambient"
        "Angry" -> "angry intense"
        "Energetic" -> "energetic dance"
        "Stressed" -> "relaxing meditation"
        "Tired" -> "sleep peaceful"
        else -> "chill melody"
    }

    fun allKnownSongs(): List<Song> = (likedSongs + onlineCatalog + queue + moodCatalog.values.flatten() + songs)
        .distinctBy { it.sourceId ?: it.id }

    private fun AudiusTrack.toSong(selectedMood: Mood, trackLanguage: String = "Independent") = Song(
        id = audiusId.hashCode(),
        title = title,
        artist = artist,
        album = album,
        language = trackLanguage,
        genre = genre,
        mood = selectedMood.name,
        color = selectedMood.color,
        duration = durationSeconds,
        artworkUrl = artworkUrl,
        sourceId = audiusId,
        externalUrl = externalUrl,
        streamUrl = streamUrl
    )
    fun searchOnline(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { searchResults = emptyList(); searchLoading = false; return }
        searchJob = scope.launch {
            delay(300)
            searchLoading = true
            val localMatches = allKnownSongs()
                .filter { "${it.title} ${it.artist} ${it.album} ${it.language} ${it.genre} ${it.mood}".contains(query, true) }
            val remote = when (val result = audiusApi.searchTracks(query, 25)) {
                is AudiusSearchResult.Success -> result.tracks.map { it.toSong(mood) }
                is AudiusSearchResult.Failure -> emptyList()
            }
            searchResults = (localMatches + remote).distinctBy { it.sourceId ?: it.id }
            onlineCatalog = (onlineCatalog + remote).distinctBy { it.sourceId ?: it.id }
            searchLoading = false
        }
    }
    fun play(song: Song, newQueue: List<Song>? = null) {
        if (newQueue != null) queue = newQueue
        if (song !in queue) queue = listOf(song)
        current = song; loading = true; playing = false; position = 0f; error = null
        if (controller == null) pendingSong = song else startPlayback(song)
        rememberStartedTrack(song)
        backend.savePreferences(mapOf("selectedSong" to mapOf("id" to trackId(song), "title" to song.title,
            "artist" to song.artist, "album" to song.album, "mood" to song.mood,
            "seconds" to song.duration, "time" to "", "artwork" to (song.artworkUrl ?: ""),
            "source" to (if (song.sourceId != null) "audius" else "android"), "bpm" to 0),
            "recent" to (listOf(trackId(song)) + recentTrackIds + recent.map { id -> allKnownSongs().find { it.id == id }?.let(::trackId) ?: id.toString() }).distinct().take(30)))
        syncCloud()
    }
    private fun rememberStartedTrack(song: Song) {
        recent = (listOf(song.id) + recent).distinct().take(30); prefs.edit().putString("recent", recent.joinToString(",")).apply()
        activeSession?.let { id -> sessions = sessions.map { old -> if (old.optLong("id") == id) JSONObject(old.toString()).apply { getJSONArray("songs").put(song.id) } else old }; saveObjects("sessions", sessions) }
    }
    private fun mediaItemForSong(song: Song): MediaItem? {
        val playbackUri = when {
            song.audioResId != null -> Uri.parse("android.resource://${context.packageName}/${song.audioResId}")
            song.streamUrl != null -> Uri.parse(song.streamUrl)
            song.previewUrl != null -> Uri.parse(song.previewUrl)
            else -> return null
        }
        val artworkUri = song.artworkUrl?.let(Uri::parse)
            ?: Uri.parse("android.resource://${context.packageName}/${R.drawable.mood_music_background}")
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(playbackUri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).setArtworkUri(artworkUri).build())
            .build()
    }
    private fun startPlayback(song: Song) {
        val playable = queue.mapNotNull(::mediaItemForSong)
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        if (playable.isEmpty()) {
            loading = false
            playing = false
            error = "No playable audio is available for ${song.title}."
            return
        }
        controller?.apply {
            setMediaItems(playable, index.coerceAtMost(playable.lastIndex), 0L)
            repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = this@MoodStore.volume
            prepare()
            play()
        }
    }
    fun toggle() { if (loading) return; controller?.let { if (it.isPlaying) it.pause() else it.play(); playing = it.isPlaying; syncCloud() } ?: current?.let { play(it) } }
    fun skip(direction: Int) { if (queue.isEmpty()) return; val candidates = queue.filter { it.id !in excluded }; if (candidates.isEmpty()) { error = "No more tracks in this mix. Choose a new mood."; return }; val index = candidates.indexOf(current); play(if (shuffle && candidates.size > 1) candidates.filter { it != current }.random() else candidates[Math.floorMod(index + direction, candidates.size)]) }
    fun seek(value: Float) { position = value; controller?.seekTo((value * 1000).toLong()); syncCloud() }
    fun setAudioVolume(value: Float) { volume = value.coerceIn(0f, 1f); controller?.volume = volume; backend.savePreferences(mapOf("volume" to (volume * 100).toInt())) }
    fun toggleMute() { setAudioVolume(if (volume > 0f) 0f else .65f) }
    fun toggleShuffle() { shuffle = !shuffle; syncCloud() }
    fun toggleRepeat() { repeat = !repeat; controller?.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; syncCloud() }
    fun toggleTheme() { themeMode = if (themeMode == "Ambient") "AMOLED" else "Ambient"; saveSettings() }
    fun saveSearch(query: String) { recentSearches = (listOf(query.trim()) + recentSearches).filter { it.isNotBlank() }.distinct().take(8); prefs.edit().putString("searches", recentSearches.joinToString("|")).apply(); syncCloud() }
    fun like(song: Song) {
        if (song.id in liked) { liked -= song.id; likedSongs = likedSongs.filterNot { it.id == song.id } }
        else { liked += song.id; likedSongs = (likedSongs + song).distinctBy { it.id } }
        prefs.edit().putStringSet("liked", liked.map(Int::toString).toSet())
            .putString("likedSongs", JSONArray(likedSongs.map(::songToJson)).toString()).apply()
        backend.setFavorite(favoriteDocumentIds[song.id] ?: trackId(song), favoriteFields(song), song.id in liked)
        syncCloud()
    }
    fun change(reason: String) { current?.let { excluded.add(it.id); feedback = feedback + JSONObject().put("song", it.id).put("reason", reason).put("time", System.currentTimeMillis()); saveObjects("feedback", feedback); syncCloud() }; dialog = null; skip(1) }
    fun finishSession(final: String, helpful: Boolean?) {
        loading = false
        sessions = sessions.map { old -> if (old.optLong("id") == activeSession) JSONObject(old.toString()).put("final", final).put("helpful", helpful ?: JSONObject.NULL).put("ended", System.currentTimeMillis()) else old }
        saveObjects("sessions", sessions); activeSession = null; activeHistoryDocumentId = null; controller?.pause(); playing = false; dialog = null; syncCloud()
    }
    fun saveJournal(text: String, selected: String) { val now = System.currentTimeMillis(); journal = journal + JSONObject().put("text", text).put("mood", selected).put("time", now); saveObjects("journal", journal); backend.saveWellness(now.toString(), mapOf("title" to "Journal entry", "type" to "journal", "value" to text, "mood" to selected)) }
    fun createPlaylist(title: String, description: String, private: Boolean) {
        backend.newPlaylist(title) { id ->
            playlists = playlists + UserPlaylist(title, description, emptyList(), private, id)
            savePlaylists()
        }
    }
    fun addToPlaylist(index: Int, song: Song) {
        playlists = playlists.mapIndexed { i, p -> if (i == index) p.copy(ids = (p.ids + song.id).distinct(), trackIds = (p.trackIds + trackId(song)).distinct()) else p }
        playlists.getOrNull(index)?.let { if (it.documentId.isNotBlank()) backend.savePlaylist(it.documentId, mapOf("trackIds" to it.trackIds)) }
        savePlaylists(); dialog = null
    }
    private fun savePlaylists(sync: Boolean = true) { prefs.edit().putString("playlists", JSONArray(playlists.map { JSONObject().put("name", it.name).put("description", it.description).put("ids", JSONArray(it.ids)).put("private", it.private).put("documentId", it.documentId).put("trackIds", JSONArray(it.trackIds)) }).toString()).apply(); if (sync) syncCloud() }
    fun saveSettings() { prefs.edit().putBoolean("notifications", notifications).putBoolean("reducedMotion", reducedMotion).putBoolean("discoMode", discoMode).putString("themeMode", themeMode).putString("name", name).putString("languages", languages.joinToString(",")).putString("genres", genres.joinToString(",")).apply(); backend.savePreferences(mapOf("languages" to languages.toList(), "genres" to genres.toList(), "artists" to artists.toList(), "goals" to goals.toList(), "notifications" to notifications, "reducedMotion" to reducedMotion, "themeMode" to themeMode)) }
    fun changeDiscoMode(enabled: Boolean) { discoMode = enabled; lightingMode = if (enabled) "beat" else "off"; backend.savePreferences(mapOf("lightingMode" to lightingMode)); saveSettings() }
    fun close() { if (backend.configured) FirebaseAuth.getInstance().removeAuthStateListener(authListener); backend.stopListening(); scope.cancel(); controller?.release(); controller = null; controllerFuture?.cancel(false); prefs.edit().putInt("listening", listeningSeconds).apply() }

    companion object {
        const val SEARCH_PAGE_SIZE = 20
        const val MAX_CACHED_TRACKS_PER_MOOD = 60
    }
}

