package com.moodtunes.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

val Lime = Color(0xFFC8FF32)
val Ink = Color(0xFF0D1605)
val Soft = Color(0xFFADB3BB)
val GlassShape = RoundedCornerShape(28.dp)
fun Modifier.accessible(label: String): Modifier = semantics { contentDescription = label }

@Composable
fun Atmosphere(color: Color = Lime, amoled: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Image(painterResource(R.drawable.mood_music_background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = if (amoled) .16f else .48f)
        Box(Modifier.matchParentSize().background(Color(if (amoled) 0xDD000000 else 0x99030C12)).drawBehind {
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = .20f), Color.Transparent), Offset(size.width * .82f, size.height * .13f), size.width * 1.05f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF8EA65B).copy(alpha = .10f), Color.Transparent), Offset(0f, size.height * .65f), size.width))
        })
        content()
    }
}

@Composable
fun BrandLogo(modifier: Modifier = Modifier, animatedPhase: Float = 0f) {
    Box(modifier.clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF13B9EC), Color(0xFF00E39A)))).border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val widths = floatArrayOf(.09f, .09f, .09f, .09f, .09f)
            val heights = floatArrayOf(.28f, .52f, .74f, .52f, .28f)
            heights.forEachIndexed { i, base ->
                val pulse = 1f + kotlin.math.sin(animatedPhase + i) * .05f
                val barH = size.height * base * pulse
                val x = size.width * (.27f + i * .115f)
                drawRoundRect(Color(0xFF071B28), Offset(x, (size.height - barH) / 2), Size(size.width * widths[i], barH), CornerRadius(size.width * .05f))
            }
        }
    }
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(18.dp, GlassShape, ambientColor = Color.Black.copy(alpha = .25f)).clip(GlassShape)
        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .105f), Color.White.copy(alpha = .035f))))
        .border(.7.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .23f), Color.White.copy(alpha = .04f))), GlassShape)
        .padding(20.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(label, color = Lime, fontSize = 12.sp) }
}

@Composable
fun PageHeading(eyebrow: String, title: String, subtitle: String? = null) {
    Text(eyebrow.uppercase(), color = Lime, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(9.dp))
    Text(title, fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, color = Color.White)
    if (subtitle != null) { Spacer(Modifier.height(10.dp)); Text(subtitle, color = Soft, fontSize = 13.sp, lineHeight = 20.sp) }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) SmallAction(action, onClick)
    }
}

@Composable
fun Cover(song: Song, modifier: Modifier = Modifier) {
    val artwork = rememberSongArtwork(song.artworkUrl)
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF182023)), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            Image(artwork.asImageBitmap(), contentDescription = "Album art for ${song.title}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(song.color.copy(alpha = .7f), Color(0xFF10171B))))
            val center = Offset(size.width * .52f, size.height * .49f)
            for (i in 0..9) {
                val radius = size.minDimension * (.12f + i * .033f)
                drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .5f), song.color.copy(alpha = .06f))), radius, center + Offset(i * 2f, 0f), style = Stroke(size.minDimension * .015f))
            }
            drawCircle(Color(0xFF172125), size.minDimension * .08f, center)
            drawLine(Color.White.copy(alpha = .25f), Offset(0f, size.height * .82f), Offset(size.width, size.height * .64f), 1f)
        }
        if (artwork == null) Text("M / ${song.id.toString().padStart(2, '0')}", color = Color.White.copy(alpha = .7f), fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
    }
}

@Composable
private fun rememberSongArtwork(url: String?): Bitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { songArtworkCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openConnection().apply { connectTimeout = 5000; readTimeout = 7000 }.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }
            }.getOrNull()?.also { songArtworkCache.put(url, it) }
        }
    }
    return bitmap
}

// Bound artwork memory instead of retaining every cover seen during a session.
private val songArtworkCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

@Composable
fun CrystalHeadphones(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawOval(Brush.radialGradient(listOf(Lime.copy(alpha = .12f), Color.Transparent)), Offset(w * .02f, h * .68f), Size(w * .96f, h * .3f))
        val rim = Brush.linearGradient(listOf(Color(0xFFECF8CC), Color(0xFF789445), Color(0xFF263423), Lime.copy(alpha = .8f)))
        drawArc(rim, 177f, 186f, false, Offset(w * .19f, h * .12f), Size(w * .62f, h * .68f), style = Stroke(w * .065f, cap = StrokeCap.Round))
        drawArc(Color(0xFF131C11), 181f, 178f, false, Offset(w * .23f, h * .17f), Size(w * .54f, h * .58f), style = Stroke(w * .032f))
        listOf(.2f, .8f).forEach { x ->
            drawRoundRect(rim, Offset(w * (x - .12f), h * .51f), Size(w * .24f, h * .32f), CornerRadius(w * .1f))
            drawOval(Color(0xFF111A11), Offset(w * (x - .085f), h * .55f), Size(w * .17f, h * .24f))
            drawOval(Brush.linearGradient(listOf(Color(0xFF405433), Color(0xFF141D13))), Offset(w * (x - .06f), h * .58f), Size(w * .12f, h * .18f))
        }
        drawArc(Color.White.copy(alpha = .65f), 195f, 107f, false, Offset(w * .18f, h * .10f), Size(w * .64f, h * .7f), style = Stroke(w * .008f))
    }
}

@Composable
fun SongRow(store: MoodStore, song: Song, source: List<Song> = store.queue) {
    val active = store.current?.id == song.id
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(alpha = if (active) .11f else .045f)).clickable { store.play(song, source) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(song, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = if (active) Lime else Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${song.language}", color = Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active && store.playing) Text("▂▆▃", color = Lime, fontSize = 13.sp) else Text(clockText(song.duration.toFloat()), color = Soft, fontSize = 10.sp)
        IconButton(onClick = { store.like(song) }, modifier = Modifier.accessible(if (song.id in store.liked) "Unlike ${song.title}" else "Like ${song.title}")) { Text(if (song.id in store.liked) "♥" else "♡", color = if (song.id in store.liked) Lime else Soft, fontSize = 23.sp) }
    }
}

@Composable
fun MoodGrid(store: MoodStore, onSelect: (Mood) -> Unit = { store.selectMood(it) }) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        moods.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { mood ->
                val selected = store.mood == mood
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (selected) mood.color.copy(alpha = .15f) else Color.White.copy(alpha = .045f))
                    .border(.7.dp, if (selected) mood.color.copy(alpha = .55f) else Color.Transparent, RoundedCornerShape(22.dp))
                    .clickable { onSelect(mood) }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mood.face, color = mood.color, fontSize = 27.sp)
                    Spacer(Modifier.height(8.dp)); Text(mood.name, fontSize = 10.sp, color = if (selected) Color.White else Soft)
                }
            }
        } }
    }
}

@Composable
fun MusicShelf(store: MoodStore, title: String, tracks: List<Song>) {
    SectionTitle(title)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        tracks.take(8).forEach { song -> Column(Modifier.width(145.dp).clickable { store.play(song, tracks); store.route = "Player" }) {
            Cover(song, Modifier.size(145.dp))
            Text(song.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 9.dp))
            Text("${song.language} · ${song.genre}", color = Soft, fontSize = 10.sp)
        } }
    }
}

@Composable
fun EmptyState(title: String, body: String) { GlassSurface(Modifier.fillMaxWidth()) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Text(body, color = Soft, fontSize = 13.sp) } }

fun timestamp(value: Long): String = java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(value))
fun clockText(value: Float): String = "%d:%02d".format(value.toInt() / 60, value.toInt() % 60)
