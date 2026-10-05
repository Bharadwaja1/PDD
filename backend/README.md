# MoodTunes Firebase backend

The Android application authenticates directly with Firebase Authentication and stores basic profiles in Cloud Firestore. This folder contains deployable Firestore security rules and Cloud Functions.

## Required Firebase setup

1. Create a Firebase project and register Android package `com.example.app`.
2. Download `google-services.json` into `app/google-services.json`.
3. In Firebase Console, enable **Authentication → Email/Password**.
4. Create a Cloud Firestore database.
5. Install the Firebase CLI and authenticate: `npm install -g firebase-tools`, then `firebase login`.
6. From this `backend` folder, run `firebase use --add`, `cd functions`, `npm install`, return to `backend`, then run `firebase deploy`.

Without `app/google-services.json`, the Android app deliberately blocks login and displays the missing configuration. There is no local/demo authentication fallback.
