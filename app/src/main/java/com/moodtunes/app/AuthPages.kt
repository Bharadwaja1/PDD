package com.moodtunes.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

@Composable
fun SplashPage(store: MoodStore) {
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (store.reducedMotion) 0 else 1100), label = "entrance")
    val infinite = rememberInfiniteTransition(label = "logo")
    val phase by infinite.animateFloat(0f, 6.28f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "logo wave")
    val logoScale by infinite.animateFloat(.94f, 1.04f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "logo pulse")
    LaunchedEffect(Unit) { shown = true; delay(2400); store.route = if (store.signedIn) "Home" else "Login" }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(30.dp).alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp)); Text("MOODTUNES", fontSize = 14.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f)); BrandLogo(Modifier.size(240.dp).scale(if (store.reducedMotion) 1f else logoScale), if (store.reducedMotion) 0f else phase)
        Spacer(Modifier.weight(1f))
        PageHeading("Find your frequency", "Feel it.\nHear it.", "Music for every mood.")
        PrimaryButton("Start your journey  →") { store.route = if (store.signedIn) "Home" else "Login" }
        Text("A little more in tune with you.", color = Soft, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
    }
}

@Composable
fun FormInput(label: String, value: String, update: (String) -> Unit, secret: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(value, update, Modifier.fillMaxWidth().padding(bottom = 10.dp), label = { Text(label, fontSize = 13.sp) }, singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else if (label.contains("Email")) KeyboardType.Email else KeyboardType.Text),
        trailingIcon = if (secret) { { TextButton({ visible = !visible }) { Text(if (visible) "Hide" else "Show", fontSize = 11.sp) } } } else null,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.White.copy(alpha = .16f), focusedBorderColor = Lime, unfocusedContainerColor = Color.White.copy(alpha = .035f)))
}

@Composable
fun AuthPage(store: MoodStore) {
    var signup by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var validation by remember { mutableStateOf<String?>(null) }
    var authenticating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) { BrandLogo(Modifier.size(54.dp)); Spacer(Modifier.width(12.dp)); Text("MoodTunes", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        GlassSurface(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            PageHeading("Your own listening space", if (signup) "Create account" else "Welcome back", "Your mood. Your music.")
            if (signup) FormInput("Full name", name, { name = it })
            FormInput("Email address", email, { email = it })
            FormInput("Password", password, { password = it }, true)
            if (signup) FormInput("Confirm password", confirm, { confirm = it }, true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(remember, { remember = it }); Text("Remember me", fontSize = 11.sp, modifier = Modifier.weight(1f))
                SmallAction("Forgot password?") { store.backend.resetPassword(email.trim()) { failure -> store.error = failure ?: "Password reset email sent to ${email.trim()}." } }
            }
            validation?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp)) }
            PrimaryButton(if (authenticating) "Connecting to Firebase…" else if (signup) "Create Account" else "Log In", enabled = !authenticating) {
                validation = when { !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."; password.length < 8 -> "Use at least 8 characters."; signup && name.isBlank() -> "Enter your name."; signup && password != confirm -> "Passwords don’t match."; else -> null }
                if (validation == null) {
                    authenticating = true
                    val done: (String?, String?) -> Unit = { remoteName, failure ->
                        authenticating = false
                        if (failure != null) validation = failure else store.login(remoteName ?: name, email.trim(), remember)
                    }
                    if (signup) store.backend.signup(name.trim(), email.trim(), password, done) else store.backend.login(email.trim(), password, done)
                }
            }
            TextButton({ signup = !signup; validation = null }, Modifier.fillMaxWidth()) { Text(if (signup) "Already a member? Log in" else "New to MoodTunes? Create account", fontSize = 12.sp) }
        }
        Text(if (store.backend.configured) "Secure authentication powered by Firebase" else "Firebase setup required: add app/google-services.json", color = if (store.backend.configured) Soft else Color(0xFFFFB4AB), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, modifier = Modifier.padding(18.dp))
    }
}

@Composable
fun ChoiceGrid(options: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { value -> FilterChip(selected = value in selected, onClick = { onChange(if (value in selected) selected - value else selected + value) }, label = { Text(value, fontSize = 12.sp) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } }
    }
}

@Composable
fun OnboardingPage(store: MoodStore) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val titles = listOf("Welcome to\nMoodTunes.", "Find your\nkind of sound.", "Meet your\nnext favorites.", "Make room\nfor yourself.", "Your MoodTunes\nis ready.")
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("◒ moodtunes", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("${step + 1} / 5", color = Soft) }
        LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), color = Lime)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PageHeading("Made around you", titles[step], "Pick a mood to hear Telugu and Tamil first, then more music.")
            when (step) {
                0 -> { CrystalHeadphones(Modifier.fillMaxWidth().height(240.dp)); Text("Check in with your mood, discover a fresh mix, and find a little space to breathe.", color = Soft) }
                1 -> ChoiceGrid(listOf("Melody", "Romantic", "Mass", "Folk", "Classical", "Lo-fi", "Devotional", "Electronic", "Rock", "Hip-Hop", "Instrumental", "Chill"), store.genres) { store.genres = it }
                2 -> { FormInput("Search artists", query, { query = it }); ChoiceGrid(listOf("MoodTunes Studio").filter { it.contains(query, true) }, store.artists) { store.artists = it } }
                3 -> ChoiceGrid(listOf("Relax", "Improve Mood", "Reduce Stress", "Focus", "Sleep", "Meditate"), store.goals) { store.goals = it }
                4 -> { CrystalHeadphones(Modifier.fillMaxWidth().height(220.dp)); GlassSurface { Text("Your first mix is waiting.", fontWeight = FontWeight.Bold); Text("Start with how you feel. We’ll take it from there.", color = Soft, fontSize = 13.sp) } }
            }
        }
        PrimaryButton(if (step == 4) "Start Listening  →" else "Continue  →") { if (step == 4) store.onboard() else step++ }
        if (step > 0) TextButton({ step-- }, Modifier.fillMaxWidth()) { Text("Back", color = Soft) }
    }
}
