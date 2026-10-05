# MoodTunes for Android

A native Jetpack Compose music and wellness app with a dark cyan/emerald music background. Open this directory in Android Studio and run the `app` configuration (Android 7.0 / API 24 or newer).

## Try the complete listening journey

1. Configure Firebase using [backend/README.md](backend/README.md), then create or log in to an account.
2. Select a mood on Home. The app loads a randomized Telugu/Tamil mix from Audius and starts playing.
4. Open the player, skip, choose another track from Queue, and like it.
5. Choose **Change song**, select a reason, and hear a replacement.
6. Choose **End session & check in**, select **Better**, answer whether music helped, and save.
7. Review the session in **My mood**, accessible from Profile.

Library supports playlists, adding tracks, favorites, recent tracks, and albums containing liked tracks. Discover supports catalog search. Wellness includes a breathing timer, journaling, and calming listening mixes. Navigation and configuration changes preserve the app-owned player. Preferences, likes, playlists, listening time, feedback, and journal entries persist locally.

## Catalog and demo boundaries

- Each of the eight moods targets 100 unique Telugu/Tamil tracks. Playable stream URLs, metadata, and artwork are retrieved from Audius and cached locally; the available count depends on the live public catalog.
- The app contains no copied commercial song files. Full tracks are streamed from their Audius catalog URLs.
- Authentication uses Firebase Email/Password only. The app requires `app/google-services.json`; there is no local login fallback.
- Network access is required for catalog loading and Firebase cloud sync. Public sharing and push delivery are not configured.
- Glass is rendered using translucent layered surfaces and highlights; it does not use a full scene refraction shader.
- Album artwork is original procedural vector artwork. Uploaded covers and profile photographs are not implemented.

## Structure

- `MoodData.kt`: models, demo catalog, persistent app state, playback, and original audio synthesis.
- `GlassComponents.kt`: reusable visual components, album covers, mood controls, and music rows.
- `AuthPages.kt`: splash, local demo login/signup, and onboarding.
- `MusicPages.kt`: home, discovery, queues, player, and library.
- `WellnessPages.kt`: breathing, journal, history, profile, and preferences.
- `AppDialogs.kt`: playlist creation, queue, replacement reasons, and end-session feedback.
- `MainActivity.kt`: theme, application shell, navigation, and mini-player.

## Verification

Run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`.

Unit tests check the eight-mood, 100-track Telugu/Tamil catalog configuration. Live catalog playback and visual behavior require internet access and an emulator or connected Android device.
