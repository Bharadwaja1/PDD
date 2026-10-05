package com.moodtunes.app

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.sin

@Composable
fun HomePage(store: MoodStore) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    PageHeading("${if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"}, ${store.name}", "How are you\nfeeling?", "There’s a sound for every state of mind.")
    MoodGrid(store)
    Spacer(Modifier.height(24.dp))
    GlassSurface(Modifier.fillMaxWidth().clickable { store.selectMood(moods[2]) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A MOMENT FOR YOU", color = Lime, fontSize = 9.sp, letterSpacing = 2.sp)
                Text("Slow down.\nTune in.", fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                Text("Your calm mix  ↗", color = Soft, fontSize = 12.sp)
            }
            CrystalHeadphones(Modifier.size(140.dp))
        }
    }
    val known = store.allKnownSongs()
    if (known.isNotEmpty()) {
        MusicShelf(store, "Made for your mood", known.filter { it.mood == store.mood.name })
        if (store.recent.isNotEmpty()) MusicShelf(store, "Recently played", store.recent.mapNotNull { id -> known.find { it.id == id } })
        MusicShelf(store, "Telugu favorites", known.filter { it.language == "Telugu" }.take(8))
        MusicShelf(store, "Recommended for you", known.filter { it.language in store.languages }.takeLast(6))
    }
    SectionTitle("Your playlists", "View library") { store.route = "Library" }
    if (store.playlists.isEmpty()) EmptyState("Your next collection starts here", "Save the sounds you want to come back to.") else store.playlists.take(3).forEach { PlaylistCard(store, it) }
}

@Composable
fun MoodPage(store: MoodStore) {
    PageHeading("A little check-in", "Meet your mood.", "Choose what feels closest. Your mix starts with you.")
    MoodGrid(store)
    Spacer(Modifier.height(24.dp))
    GlassSurface(Modifier.fillMaxWidth()) { Text("Feelings change. Your music can too.", fontWeight = FontWeight.SemiBold); Text("Every selection saves a listening session to your mood history.", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)); SmallAction("Explore my mood history  →") { store.route = "History" } }
}

@Composable
fun MoodPlaylistPage(store: MoodStore) {
    val sourceDescription = if (store.queueIsFromAudius) "Audius tracks" else "tracks"
    PageHeading("Music for your mood", "Feeling ${store.mood.name.lowercase()}.", "A little company for right now. ${store.queue.size} $sourceDescription.")
    if (store.loading) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime); Text("Finding music for your mood…", color = Soft, modifier = Modifier.padding(vertical = 14.dp)) }
    store.queue.firstOrNull()?.let { song ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song, Modifier.size(120.dp)); Column(Modifier.padding(start = 20.dp)) { Text("${store.mood.face}  ${store.mood.name} mix", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Telugu first, Tamil next, then more music", color = Soft, fontSize = 11.sp); SmallAction("Open player  ↗") { store.route = "Player" } }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (store.queue.isEmpty()) EmptyState("Your mix is empty", "Select another mood to discover more sounds.")
    if (store.queueIsFromAudius) {
        (moodCatalogLanguages + store.queue.map { it.language }
            .filterNot { it in moodCatalogLanguages }.distinct()).forEach { language ->
            val languageSongs = store.queue.filter { it.language == language }
            if (languageSongs.isNotEmpty()) {
                SectionTitle("$language songs", "${languageSongs.size} tracks")
                languageSongs.forEach { SongRow(store, it, store.queue) }
            }
        }
    } else store.queue.forEach { SongRow(store, it) }
    if (store.queueIsFromAudius) Text("Full tracks are streamed from the Audius open music catalog.", color = Soft, fontSize = 10.sp, modifier = Modifier.padding(vertical = 16.dp))
    if (store.activeSession != null) PrimaryButton("End session & check in") { store.dialog = "feedback" }
}

@Composable
fun DiscoverPage(store: MoodStore) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(query) { store.searchOnline(query) }
    PageHeading("Follow your curiosity", "Discover.", "A new favorite might be one tap away.")
    FormInput("Search songs, artists, albums…", query, { query = it })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Songs", "Artists", "Albums", "Playlists").forEach { item -> FilterChip(tab == item, { tab = item }, label = { Text(item) }) } }
    if (query.isNotBlank()) {
        val result = store.searchResults
        SectionTitle("Search results", "Save search") { store.saveSearch(query) }
        if (store.searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = Lime)
        when (tab) {
            "Artists" -> { val artists = result.distinctBy { it.artist }; if (artists.isEmpty() && !store.searchLoading) EmptyState("No artists found", "Try another artist name."); artists.forEach { song -> GlassSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp)) { Text(song.artist); SmallAction("Play tracks") { val artistSongs = result.filter { it.artist == song.artist }; store.play(artistSongs.first(), artistSongs) } } } }
            "Albums" -> { val albums = result.distinctBy { it.album }; if (albums.isEmpty()) EmptyState("No albums found", "Try a mood like calm."); albums.forEach { song -> GlassSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { store.queue = result.filter { it.album == song.album }; store.route = "Mood playlist" }) { Text(song.album); Text("${result.count { it.album == song.album }} tracks", color = Soft, fontSize = 12.sp) } } }
            "Playlists" -> { val lists = store.playlists.filter { it.name.contains(query, true) }; if (lists.isEmpty()) EmptyState("No playlists found", "Create one in your library."); lists.forEach { PlaylistCard(store, it) } }
            else -> { if (result.isEmpty() && !store.searchLoading) EmptyState("No results yet", "Try a song, artist, Telugu, Tamil, Hindi, or a mood."); result.forEach { SongRow(store, it, result) } }
        }
    } else {
        if (store.recentSearches.isNotEmpty()) { SectionTitle("Recent searches"); store.recentSearches.forEach { term -> SmallAction(term) { query = term } } }
        SectionTitle("Trending searches")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Telugu", "Calm", "Vennela").forEach { FilterChip(false, { query = it }, label = { Text(it) }) } }
        MusicShelf(store, "Fresh from Audius", store.onlineCatalog.asReversed().distinctBy { it.sourceId }.take(12))
        SectionTitle("Mood mixes"); MoodGrid(store)
        SectionTitle("Genres"); ChoiceGrid(listOf("Instrumental", "Electronic"), emptySet()) { query = it.firstOrNull() ?: "" }
        MusicShelf(store, "Recommended", store.moodCatalog[store.mood.name].orEmpty().take(12))
    }
}

@Composable
fun PlayerPage(store: MoodStore) {
    val song = store.current
    if (song == null) { EmptyState("Nothing playing yet", "Choose a mood or select a song to begin."); SmallAction("Find my mix") { store.route = "Mood" }; return }
    val discoPalette = listOf(Color(0xFFFF3D81), Color(0xFF7C4DFF), Color(0xFF00D9FF), Color(0xFF00E676), Color(0xFFFFD740), Color(0xFFFF6D00))
    val discoTarget = if (store.discoMode && store.playing && !store.reducedMotion) discoPalette[Math.floorMod(store.position.toInt() + song.id, discoPalette.size)] else song.color
    val playerColor by animateColorAsState(discoTarget, tween(if (store.reducedMotion) 0 else 450), label = "player beat color")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("NOW PLAYING", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp); Text(song.album, color = Soft, fontSize = 12.sp) }
        IconButton({ store.like(song) }, Modifier.clip(CircleShape).background(Color.White.copy(alpha = .07f)).accessible(if (song.id in store.liked) "Unlike song" else "Like song")) {
            Icon(if (song.id in store.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (song.id in store.liked) Lime else Color.White)
        }
        IconButton({ store.dialog = "queue" }, Modifier.accessible("Open queue")) { Icon(Icons.Default.QueueMusic, null) }
    }
    Spacer(Modifier.height(22.dp))
    Box(Modifier.fillMaxWidth().heightIn(max = 365.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(.94f).clip(CircleShape).background(playerColor.copy(alpha = .16f)).border(2.dp, playerColor.copy(alpha = .65f), CircleShape))
        Box(Modifier.fillMaxSize(.79f).clip(CircleShape).background(Color.Black.copy(alpha = .20f)).border(12.dp, Color.White.copy(alpha = .045f), CircleShape), contentAlignment = Alignment.Center) {
            Cover(song, Modifier.fillMaxSize(.82f).clip(CircleShape))
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(song.title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("${song.artist}  ·  ${song.album}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
    }
    Waveform(store)
    Slider(store.position, { store.seek(it) }, modifier = Modifier.accessible("Playback position"), valueRange = 0f..store.duration.coerceAtLeast(1f), enabled = !store.loading, colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color.White.copy(alpha = .13f)))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clockText(store.position), color = Soft, fontSize = 11.sp); Text(clockText(store.duration), color = Soft, fontSize = 11.sp) }
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton({ store.toggleShuffle() }, Modifier.accessible("Shuffle")) { Icon(Icons.Default.Shuffle, null, tint = if (store.shuffle) Lime else Soft) }
        IconButton({ store.skip(-1) }, Modifier.size(52.dp).accessible("Previous song")) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(30.dp)) }
        Box(Modifier.size(92.dp).clip(CircleShape).background(Lime.copy(alpha = .12f)).border(2.dp, Lime.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
            Button({ store.toggle() }, modifier = Modifier.size(70.dp).accessible(if (store.playing) "Pause" else "Play"), enabled = !store.loading, shape = CircleShape, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) {
                if (store.loading) CircularProgressIndicator(Modifier.size(24.dp), color = Ink) else Icon(if (store.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(35.dp))
            }
        }
        IconButton({ store.skip(1) }, Modifier.size(52.dp).accessible("Next song")) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(30.dp)) }
        IconButton({ store.toggleRepeat() }, Modifier.accessible("Repeat")) { Icon(Icons.Default.Repeat, null, tint = if (store.repeat) Lime else Soft) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ store.toggleMute() }, Modifier.accessible(if (store.volume > 0f) "Mute" else "Unmute")) { Icon(if (store.volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, tint = if (store.volume > 0f) Soft else Lime) }; Slider(store.volume, { store.setAudioVolume(it) }, Modifier.weight(1f)); Text("${(store.volume * 100).toInt()}%", color = Soft, fontSize = 11.sp) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { SmallAction("Add to playlist") { store.dialog = "add" } }
    if (store.activeSession != null) OutlinedButton({ store.dialog = "feedback" }, Modifier.fillMaxWidth()) { Text("End session & check in") }
}

@Composable
fun LikedPage(store: MoodStore) {
    val likedSongs = (store.likedSongs + store.allKnownSongs().filter { it.id in store.liked }).distinctBy { it.id }
    PageHeading("Songs close to you", "Liked songs.", "Every track you heart, together in one place.")
    if (likedSongs.isEmpty()) {
        EmptyState("Nothing saved yet", "Tap the heart on any song or in the player to keep it here.")
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Discover music") { store.route = "Search" }
    } else {
        GlassSurface(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Lime, modifier = Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) { Text("Your favorites", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${likedSongs.size} saved tracks", color = Soft, fontSize = 12.sp) }
                IconButton({ store.play(likedSongs.first(), likedSongs) }) { Icon(Icons.Default.PlayArrow, "Play liked songs", tint = Lime) }
            }
        }
        likedSongs.forEach { SongRow(store, it, likedSongs) }
    }
}

@Composable
fun Waveform(store: MoodStore) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 6.28f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "wave phase")
    Canvas(Modifier.fillMaxWidth().height(40.dp).padding(top = 16.dp)) {
        repeat(45) { i -> val height = (0.2f + .8f * kotlin.math.abs(sin(i * 1.7f + if (store.playing && !store.reducedMotion) phase else 0f))) * size.height
            drawLine(if (i / 45f < store.position / store.duration) Lime else Color.White.copy(alpha = .16f), androidx.compose.ui.geometry.Offset(i * size.width / 45, (size.height - height) / 2), androidx.compose.ui.geometry.Offset(i * size.width / 45, (size.height + height) / 2), 3f)
        }
    }
}

@Composable
fun LibraryPage(store: MoodStore) {
    var tab by rememberSaveable { mutableStateOf("Playlists") }
    PageHeading("Keep your favorites close", "Your library.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Playlists", "Liked songs", "Recent", "Albums").forEach { FilterChip(tab == it, { tab = it }, label = { Text(it) }) } }
    Spacer(Modifier.height(16.dp))
    when (tab) {
        "Playlists" -> { PrimaryButton("+ Create playlist") { store.dialog = "create" }; Spacer(Modifier.height(16.dp)); if (store.playlists.isEmpty()) EmptyState("A collection of your own", "Create a playlist, then add songs from the player."); store.playlists.forEach { PlaylistCard(store, it) } }
        "Liked songs" -> { val list = (store.likedSongs + store.allKnownSongs().filter { it.id in store.liked }).distinctBy { it.id }; if (list.isEmpty()) EmptyState("Your favorites live here", "Tap the heart on any track to save it."); list.forEach { SongRow(store, it, list) } }
        "Recent" -> { val known = store.allKnownSongs(); val list = store.recent.mapNotNull { id -> known.find { it.id == id } }; if (list.isEmpty() && store.recentTrackIds.isEmpty()) EmptyState("Your listening story starts here", "Play your first mix to see it here."); list.forEach { SongRow(store, it, list) }; val shown = list.map { it.sourceId ?: it.id.toString() }.toSet(); store.recentTrackIds.filterNot { it in shown }.forEach { Text("Track $it", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) } }
        else -> { val likedCatalog = (store.likedSongs + store.allKnownSongs().filter { it.id in store.liked }).distinctBy { it.id }; val list = likedCatalog.distinctBy { it.album }; if (list.isEmpty()) EmptyState("No saved albums yet", "Albums containing your liked tracks appear here."); list.forEach { MusicShelf(store, it.album, likedCatalog.filter { s -> s.album == it.album }) } }
    }
}

@Composable
fun PlaylistCard(store: MoodStore, playlist: UserPlaylist) {
    GlassSurface(Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { store.selectedPlaylist = playlist; store.route = "Playlist" }) {
        Text("▥  ${playlist.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("${playlist.trackIds.size.coerceAtLeast(playlist.ids.size)} tracks · ${if (playlist.private) "Private" else "Public preference"}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun PlaylistPage(store: MoodStore) {
    val playlist = store.playlists.find { it.name == store.selectedPlaylist?.name } ?: return
    var query by rememberSaveable { mutableStateOf("") }
    PageHeading("Your collection", playlist.name, playlist.description)
    val catalog = store.allKnownSongs()
    val tracks = catalog.filter { it.id in playlist.ids || (it.sourceId ?: it.id.toString()) in playlist.trackIds }
    if (tracks.isEmpty() && playlist.trackIds.isEmpty()) EmptyState("Let’s add your first song", "Search the catalog below and tap + to add a track.")
    tracks.forEach { SongRow(store, it, tracks) }
    val knownTrackIds = tracks.map { it.sourceId ?: it.id.toString() }.toSet()
    playlist.trackIds.filterNot { it in knownTrackIds }.forEach { trackId ->
        Text("Track $trackId", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    SectionTitle("Add music"); FormInput("Search the catalog", query, { query = it })
    catalog.filter { it.title.contains(query, true) && it.id !in playlist.ids }.take(12).forEach { song ->
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(song.title, fontSize = 13.sp); Text(song.language, color = Soft, fontSize = 11.sp) }; SmallAction("+ Add") { store.addToPlaylist(store.playlists.indexOf(playlist), song) } }
    }
}
