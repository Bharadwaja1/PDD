package com.moodtunes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MoodViewModel : ViewModel() {
    var store: MoodStore? = null
    override fun onCleared() { store?.close() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
        val model = ViewModelProvider(this)[MoodViewModel::class.java]
        val store = model.store ?: MoodStore(applicationContext).also { model.store = it }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Ink, surface = Color(0xFF151A20), onPrimary = Ink, onSurface = Color.White)) {
                CompositionLocalProvider(LocalContentColor provides Color.White) { MoodApp(store) }
            }
        }
    }

}

@Composable
fun MoodApp(store: MoodStore) {
    val auth = store.route in listOf("Splash", "Login")
    BackHandler(store.route !in listOf("Splash", "Login", "Home")) { store.route = if (store.signedIn) "Home" else "Login" }
    Atmosphere(if (auth) Lime else store.mood.color, store.themeMode == "AMOLED") {
        if (auth) {
            when (store.route) { "Splash" -> SplashPage(store); else -> AuthPage(store) }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                AppHeader(store)
                key(store.route) { Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 26.dp)) {
                        key(store.route) {
                            when (store.route) {
                                "Home" -> HomePage(store)
                                "Discover", "Search" -> DiscoverPage(store)
                                "Liked" -> LikedPage(store)
                                "Mood" -> MoodPage(store)
                                "Mood playlist" -> MoodPlaylistPage(store)
                                "Player" -> PlayerPage(store)
                                "Library" -> LibraryPage(store)
                                "Playlist" -> PlaylistPage(store)
                                "Wellness" -> WellnessPage(store)
                                "Breathing" -> BreathingPage(store)
                                "Journal" -> JournalPage(store)
                                "History" -> HistoryPage(store)
                                "Profile" -> ProfilePage(store)
                                "Settings" -> ProfilePage(store)
                                "Notifications" -> { PageHeading("Your updates", "A little space for you"); EmptyState("You’re all caught up", "Your music and wellbeing moments will appear here. No notifications have been sent.") }
                            }
                        }
                    }
                } }
                if (store.current != null && store.route != "Player") MiniPlayer(store)
                NavigationDock(store)
            }
        }
        store.error?.let { message ->
            AlertDialog(onDismissRequest = { store.error = null }, title = { Text("MoodTunes") }, text = { Text(message) }, confirmButton = { TextButton({ store.error = null }) { Text("Got it") } })
        }
        AppDialogs(store)
    }
}

@Composable
private fun AppHeader(store: MoodStore) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (store.route !in listOf("Home", "Liked", "Search", "Discover", "History")) IconButton({ store.route = "Home" }, Modifier.accessible("Back to home")) { Text("‹", fontSize = 30.sp) }
        BrandLogo(Modifier.size(38.dp))
        Text("  MoodTunes", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-.5).sp)
        IconButton({ store.route = "Profile" }, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)).border(.7.dp, Color.White.copy(alpha = .16f), CircleShape).accessible("Open profile")) {
            Icon(Icons.Default.Person, null, tint = Lime)
        }
    }
}

@Composable
private fun NavigationDock(store: MoodStore) {
    val items = listOf(
        Triple("Home", Icons.Default.Home, "Home"),
        Triple("Liked", Icons.Default.Favorite, "Liked"),
        Triple("Search", Icons.Default.Search, "Search"),
        Triple("History", Icons.Default.History, "History")
    )
    Row(Modifier.padding(horizontal = 36.dp, vertical = 7.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Color(0xF20B2024)).border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(24.dp)).padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (route, icon, label) -> val active = store.route == route || (route == "Search" && store.route == "Discover")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { store.route = route }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (active) Lime else Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = if (active) Ink else Color.White.copy(alpha = .62f), modifier = Modifier.size(18.dp))
                }
                Text(label, fontSize = 8.sp, color = if (active) Lime else Soft, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MiniPlayer(store: MoodStore) {
    val song = store.current ?: return
    Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF252C2D)).clickable { store.route = "Player" }) {
        Row(Modifier.padding(start = 10.dp, top = 7.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song, Modifier.size(42.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(song.title, fontSize = 12.sp, maxLines = 1); Text(if (store.loading) "Preparing your music…" else song.artist, fontSize = 10.sp, color = Soft) }
            IconButton({ store.skip(-1) }, Modifier.accessible("Previous song")) { Icon(Icons.Default.SkipPrevious, null) }
            IconButton({ store.toggle() }, Modifier.accessible(if (store.playing) "Pause" else "Play"), enabled = !store.loading) { Icon(if (store.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Lime) }
            IconButton({ store.skip(1) }, Modifier.accessible("Next song")) { Icon(Icons.Default.SkipNext, null) }
        }
        LinearProgressIndicator(progress = { (store.position / store.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Lime, trackColor = Color.Transparent)
    }
}
