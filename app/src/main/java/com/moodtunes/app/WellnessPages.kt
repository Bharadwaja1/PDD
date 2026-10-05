package com.moodtunes.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
fun WellnessPage(store: MoodStore) {
    PageHeading("A softer kind of rhythm", "Space to\njust be.", "Small moments for your everyday wellbeing.")
    GlassSurface(Modifier.fillMaxWidth()) {
        Text("ONE BREATH AT A TIME", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Come back to now.", fontWeight = FontWeight.Bold, fontSize = 25.sp, modifier = Modifier.padding(vertical = 14.dp))
        Text("A gentle breathing rhythm. Go at your own pace.", color = Soft, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp)); PrimaryButton("Start breathing  →") { store.route = "Breathing" }
    }
    SectionTitle("Find your moment")
    listOf("Meditation" to "Quiet sounds, open space", "Relaxation" to "Let the day settle", "Focus" to "A little less distraction", "Sleep" to "Ease into your evening", "Mood Journal" to "Put your feelings into words").forEach { (title, detail) ->
        GlassSurface(Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
            if (title == "Mood Journal") store.route = "Journal" else store.selectMood(moods.first { it.name == if (title == "Sleep") "Tired" else "Calm" })
        }) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium); Text(detail, color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (store.wellnessEntries.isNotEmpty()) {
        SectionTitle("Your wellness data")
        store.wellnessEntries.forEach { entry -> GlassSurface(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(entry.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (entry.value.isNotBlank()) Text(entry.value, color = Soft, fontSize = 12.sp)
        } }
    }
    Text("A space for reflection and relaxation, not medical advice or diagnosis.", color = Soft, fontSize = 11.sp)
}

@Composable
fun BreathingPage(store: MoodStore) {
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val part = elapsed % 12
    val label = if (part < 4) "Breathe in" else if (part < 6) "Hold gently" else "Breathe out"
    val scale by animateFloatAsState(if (part < 6) 1f else .68f, tween(if (store.reducedMotion) 0 else if (part < 6) 4000 else 6000, easing = LinearEasing), label = "breath")
    LaunchedEffect(paused) { while (!paused) { delay(1000); elapsed++ } }
    PageHeading("A moment of stillness", "Just breathe.", "Follow the rhythm only as it feels comfortable.")
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(230.dp).scale(scale).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFCEEEC8).copy(alpha = .45f), Color(0xFF75AD9D).copy(alpha = .1f)))).border(1.dp, Color.White.copy(alpha = .3f), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (paused) "Take your time" else label, fontSize = 22.sp, fontWeight = FontWeight.Medium); Text(clockText(elapsed.toFloat()), color = Soft) }
    }
    PrimaryButton(if (paused) "Resume" else "Pause") { paused = !paused }
    TextButton({ store.saveJournal("Completed a ${elapsed / 60}m ${elapsed % 60}s breathing session.", "Calm"); store.route = "Wellness" }, Modifier.fillMaxWidth()) { Text("End session") }
}

@Composable
fun JournalPage(store: MoodStore) {
    var text by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(store.mood) }
    var saved by remember { mutableStateOf(false) }
    PageHeading("Your words, your space", "How are you\nfeeling?")
    MoodGrid(store) { selected = it; store.mood = it }
    SectionTitle("What’s on your mind?")
    OutlinedTextField(text, { text = it; saved = false }, Modifier.fillMaxWidth().height(180.dp), placeholder = { Text("No right words needed. Start anywhere.") }, shape = GlassShape)
    Spacer(Modifier.height(18.dp))
    PrimaryButton(if (saved) "Entry saved ✓" else "Save entry", enabled = text.isNotBlank()) { store.saveJournal(text.trim(), selected.name); text = ""; saved = true }
    SmallAction("Read my journal  →") { store.route = "History" }
}

@Composable
fun HistoryPage(store: MoodStore) {
    PageHeading("Your emotional soundtrack", "My mood.", "A reflection of your check-ins, in your own time.")
    val common = store.sessions.groupingBy { it.optString("initial") }.eachCount().maxByOrNull { it.value }?.key ?: "—"
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("${store.sessions.size}", fontSize = 26.sp, color = Lime); Text("Check-ins", color = Soft, fontSize = 11.sp) }
            Column { Text("${store.listeningSeconds / 60} min", fontSize = 26.sp); Text("Listening", color = Soft, fontSize = 11.sp) }
            Column { Text(common, fontSize = 22.sp); Text("Most selected", color = Soft, fontSize = 11.sp) }
        }
    }
    SectionTitle("Mood over time")
    if (store.sessions.isEmpty()) EmptyState("Your story is still unfolding", "Select a mood on Home to record your first session.")
    store.sessions.takeLast(20).reversed().forEach { entry -> GlassSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text("${entry.optString("initial")}  →  ${entry.optString("final", "In progress")}", fontWeight = FontWeight.Medium)
        Text(timestamp(entry.optLong("started")), color = Soft, fontSize = 11.sp)
        Text("${entry.optJSONArray("songs")?.length() ?: 0} tracks played${if (!entry.isNull("helpful")) " · Music helped: ${if (entry.optBoolean("helpful")) "Yes" else "No"}" else ""}", color = Soft, fontSize = 11.sp)
    } }
    SectionTitle("Your journal", "Write") { store.route = "Journal" }
    if (store.journal.isEmpty()) EmptyState("A little room for your thoughts", "Your saved journal entries will appear here.")
    store.journal.reversed().forEach { entry -> GlassSurface(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(entry.optString("mood"), color = Lime, fontSize = 12.sp); Text(entry.optString("text"), modifier = Modifier.padding(vertical = 8.dp), fontSize = 14.sp); Text(timestamp(entry.optLong("time")), color = Soft, fontSize = 10.sp)
    } }
    Text("These are personal reflections, not medical assessments.", color = Soft, fontSize = 11.sp)
}

@Composable
fun ProfilePage(store: MoodStore) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf(store.name) }
    LaunchedEffect(store.name, editing) { if (!editing) draftName = store.name }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> store.notifications = granted; store.saveSettings(); if (!granted) store.error = "Notification permission was not granted. Enable it in Android Settings to see player controls." }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(104.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF11BCEA), Color(0xFF00DF9A)))).border(3.dp, Color.White.copy(alpha = .22f), CircleShape), contentAlignment = Alignment.Center) {
                Text(store.name.take(1).uppercase(), color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp)); Text(store.name, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text(store.email, color = Soft, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(22.dp))
    if (editing) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Text("Edit profile", fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp))
            FormInput("Display name", draftName, { draftName = it })
            FormInput("Email", store.email, {})
            PrimaryButton(if (saving) "Saving…" else "Save changes", enabled = draftName.isNotBlank() && !saving) {
                saving = true
                store.backend.saveProfile(draftName.trim(), store.email) { failure -> saving = false; if (failure == null) { store.name = draftName.trim(); store.saveSettings(); editing = false } else store.error = failure }
            }
        }
    } else Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OutlinedButton({ editing = true }, Modifier.height(50.dp), shape = CircleShape) { Text("Edit profile") } }
    SectionTitle("Your music")
    GlassSurface(Modifier.fillMaxWidth()) {
        ProfileRow("Liked songs", "${store.liked.size} saved") { store.route = "Liked" }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); ProfileRow("Listening history", "${store.listeningSeconds / 60} minutes") { store.route = "History" }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); ProfileRow("Playlists", "${store.playlists.size} collections") { store.route = "Library" }
    }
    SectionTitle("Integrations")
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Audius", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Free full-track streaming · no login required", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Text("Connected", color = Soft, fontSize = 12.sp)
        }
    }
    SectionTitle("Settings")
    GlassSurface(Modifier.fillMaxWidth()) {
        ProfileRow("Theme", store.themeMode) { store.toggleTheme() }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) { Text("Player notifications", Modifier.weight(1f)); Switch(store.notifications, { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else { store.notifications = enabled; store.saveSettings() } }) }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) { Text("Reduce motion", Modifier.weight(1f)); Switch(store.reducedMotion, { store.reducedMotion = it; store.saveSettings() }) }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Beat-reactive colors"); Text("Disco glow while music plays", color = Soft, fontSize = 11.sp) }; Switch(store.discoMode, { store.changeDiscoMode(it) }) }
        HorizontalDivider(color = Color.White.copy(alpha = .08f)); ProfileRow("Contact support", "Email") { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@moodtunes.app?subject=MoodTunes%20Support"))) }.onFailure { store.error = "No email application is available." } }
    }
    TextButton({ store.logout() }, Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Log out", color = Color(0xFFFFAAA3)) }
}

@Composable
private fun ProfileRow(title: String, value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = action), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp); Text(value, color = Soft, fontSize = 12.sp); Text("  ›", color = Lime, fontSize = 20.sp)
    }
}

@Composable
fun SettingsPage(store: MoodStore) {
    PageHeading("A space that feels like you", "Settings.")
    FormInput("Display name", store.name, { store.name = it })
    SectionTitle("Favorite genres")
    ChoiceGrid(listOf("Instrumental", "Electronic", "Melody", "Lo-fi", "Chill", "Classical"), store.genres) { store.genres = it }
    SectionTitle("Playback")
    Text("Volume", color = Soft); Slider(store.volume, { store.setAudioVolume(it) })
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Repeat current track", Modifier.weight(1f)); Switch(store.repeat, { store.toggleRepeat() }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Notification preference", Modifier.weight(1f)); Switch(store.notifications, { store.notifications = it }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Reduce motion", Modifier.weight(1f)); Switch(store.reducedMotion, { store.reducedMotion = it }) }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Beat-reactive colors", Modifier.weight(1f)); Switch(store.discoMode, { store.discoMode = it }) }
    SectionTitle("Privacy & account")
    Text("Your preferences, playlists, and wellness entries sync through your Firebase account. Saved data may also be cached on this device.", color = Soft, fontSize = 12.sp)
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Save preferences", store.name.isNotBlank()) { store.saveSettings(); store.route = "Profile" }
    TextButton({ store.logout() }, Modifier.fillMaxWidth()) { Text("Log out") }
}
