package com.moodtunes.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDialogs(store: MoodStore) {
    when (store.dialog) {
        "change" -> AlertDialog(onDismissRequest = { store.dialog = null }, title = { Text("Why another song?") }, text = {
            Column { listOf("Not matching my mood", "Don’t like this song", "Heard it too often", "Don’t like this artist", "Just change it").forEach { reason -> TextButton({ store.change(reason) }, Modifier.fillMaxWidth()) { Text(reason) } } }
        }, confirmButton = {}, dismissButton = { TextButton({ store.dialog = null }) { Text("Cancel") } })
        "queue" -> AlertDialog(onDismissRequest = { store.dialog = null }, title = { Text("Up next") }, text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { store.queue.forEach { SongRow(store, it) } } }, confirmButton = { TextButton({ store.dialog = null }) { Text("Done") } })
        "add" -> AlertDialog(onDismissRequest = { store.dialog = null }, title = { Text("Add to playlist") }, text = {
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                if (store.playlists.isEmpty()) Text("Create a playlist first, then add this track.")
                store.playlists.forEachIndexed { i, p -> TextButton({ store.current?.let { store.addToPlaylist(i, it) } }) { Text(p.name) } }
                TextButton({ store.dialog = "create" }) { Text("+ New playlist") }
            }
        }, confirmButton = { TextButton({ store.dialog = null }) { Text("Cancel") } })
        "create" -> CreatePlaylistDialog(store)
        "feedback" -> FeedbackDialog(store)
    }
}

@Composable
private fun CreatePlaylistDialog(store: MoodStore) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = { store.dialog = null }, title = { Text("Your new collection") }, text = {
        Column { FormInput("Playlist name", name, { name = it }); FormInput("Description", description, { description = it }); Row { Checkbox(private, { private = it }); Text("Private playlist", Modifier.padding(top = 12.dp)) }; Text("Cover art is generated from your music. Sharing is not connected in this offline build.") }
    }, confirmButton = { TextButton({ store.createPlaylist(name.trim(), description.trim(), private); store.dialog = if (store.current != null) "add" else null }, enabled = name.isNotBlank() && store.playlists.none { it.name.equals(name.trim(), true) }) { Text("Create") } }, dismissButton = { TextButton({ store.dialog = null }) { Text("Cancel") } })
}

@Composable
private fun FeedbackDialog(store: MoodStore) {
    var feeling by remember { mutableStateOf("") }
    var helpful by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(onDismissRequest = { store.dialog = null }, title = { Text("How are you feeling now?") }, text = {
        Column { ChoiceGrid(listOf("Very Low", "Low", "Neutral", "Better", "Great"), setOf(feeling)) { feeling = it.lastOrNull() ?: "" }; Spacer(Modifier.height(16.dp)); Text("Did this music help?"); Row { FilterChip(helpful == true, { helpful = true }, label = { Text("Yes") }); Spacer(Modifier.width(12.dp)); FilterChip(helpful == false, { helpful = false }, label = { Text("No") }) } }
    }, confirmButton = { TextButton({ store.finishSession(feeling, helpful); store.route = "History" }, enabled = feeling.isNotBlank() && helpful != null) { Text("Save check-in") } }, dismissButton = { TextButton({ store.dialog = null }) { Text("Keep listening") } })
}
