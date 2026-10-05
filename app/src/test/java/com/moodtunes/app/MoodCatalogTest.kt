package com.moodtunes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color

class MoodCatalogTest {
    @Test fun moodMixIsBoundedAndStartsWithRegionalLanguages() {
        assertEquals(8, moods.size)
        assertEquals(20, MoodStore.SEARCH_PAGE_SIZE)
        assertEquals(60, MoodStore.MAX_CACHED_TRACKS_PER_MOOD)
        assertEquals(listOf("Telugu", "Tamil"), moodCatalogLanguages)
        assertEquals(moods.size, moods.map { it.name }.distinct().size)
    }

    @Test fun everyMoodHasAStreamingSearchPhrase() {
        val expected = setOf("Happy", "Sad", "Calm", "Angry", "Stressed", "Energetic", "Tired", "Neutral")
        assertEquals(expected, moods.map { it.name }.toSet())
        assertTrue(songs.isEmpty())
    }

    @Test fun moodSongsPutTeluguThenTamilBeforeOtherLanguages() {
        fun track(id: Int, language: String) = Song(id, "Track $id", "Artist", "Album", language,
            "Music", "Happy", Color(0xFF43D5BD), sourceId = id.toString())
        val mixed = listOf(track(1, "Independent"), track(2, "Tamil"), track(3, "Telugu"),
            track(4, "Independent"), track(5, "Telugu"), track(2, "Tamil"))
        assertEquals(listOf(3, 5, 2, 1, 4), prioritizeMoodSongs(mixed).map { it.id })
    }
}
