package com.moodtunes.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp

class FirebaseBackend(private val context: Context) {
    val configured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()
    val currentUser get() = if (configured) FirebaseAuth.getInstance().currentUser else null

    fun login(email: String, password: String, done: (String?, String?) -> Unit) {
        if (!configured) return done(null, "Add app/google-services.json from Firebase Console, then rebuild the app.")
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (!task.isSuccessful) done(null, task.exception?.localizedMessage ?: "Login failed")
            else loadName(task.result.user?.uid.orEmpty(), email, done)
        }
    }

    fun signup(name: String, email: String, password: String, done: (String?, String?) -> Unit) {
        if (!configured) return done(null, "Add app/google-services.json from Firebase Console, then rebuild the app.")
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                val message = if (task.exception is FirebaseAuthUserCollisionException) {
                    "An account already exists for this email. Log in instead."
                } else {
                    task.exception?.localizedMessage ?: "Account creation failed"
                }
                done(null, message)
                return@addOnCompleteListener
            }

            val user = task.result?.user
            if (user == null) {
                done(null, "Account creation failed")
            } else FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .set(mapOf("name" to name, "email" to email, "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "createdAt" to (user.metadata?.creationTimestamp?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) } ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnSuccessListener { done(name, null) }
                .addOnFailureListener { done(null, it.localizedMessage ?: "Profile creation failed") }
        }
    }

    fun resetPassword(email: String, done: (String?) -> Unit) {
        if (!configured) return done("Firebase is not configured.")
        if (email.isBlank()) return done("Enter your email address first.")
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener { done(it.localizedMessage ?: "Could not send the reset email") }
    }

    private fun loadName(uid: String, email: String, done: (String?, String?) -> Unit) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val missing = mutableMapOf<String, Any>()
                    if (snapshot.getString("name") == null) missing["name"] = currentUser?.displayName ?: email.substringBefore('@')
                    if (snapshot.getString("email") == null) missing["email"] = email
                    if (snapshot.getString("photoUrl") == null) missing["photoUrl"] = currentUser?.photoUrl?.toString() ?: ""
                    if (snapshot.getTimestamp("createdAt") == null) missing["createdAt"] = currentUser?.metadata?.creationTimestamp?.let {
                        Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                    } ?: FieldValue.serverTimestamp()
                    if (missing.isEmpty()) done(snapshot.getString("name") ?: "", null)
                    else snapshot.reference.set(missing + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                        .addOnSuccessListener { done(snapshot.getString("name") ?: missing["name"] as? String ?: "", null) }
                        .addOnFailureListener { done(null, it.localizedMessage ?: "Could not update profile") }
                } else {
                    val authUser = currentUser
                    val created = authUser?.metadata?.creationTimestamp?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) } ?: FieldValue.serverTimestamp()
                    val fallbackName = authUser?.displayName ?: email.substringBefore('@')
                    snapshot.reference.set(mapOf("name" to fallbackName, "email" to email, "photoUrl" to (authUser?.photoUrl?.toString() ?: ""),
                        "createdAt" to created, "updatedAt" to FieldValue.serverTimestamp()))
                        .addOnSuccessListener { done(fallbackName, null) }
                        .addOnFailureListener { done(null, it.localizedMessage ?: "Could not create profile") }
                }
            }
            .addOnFailureListener { done(email.substringBefore('@'), null) }
    }

    private val listeners = mutableListOf<ListenerRegistration>()
    private fun userRef() = currentUser?.uid?.let { FirebaseFirestore.getInstance().collection("users").document(it) }

    fun stopListening() { listeners.forEach { it.remove() }; listeners.clear() }

    private fun trace(path: String, id: String, data: Map<String, Any?>?, fromCache: Boolean, pendingWrites: Boolean) {
        if (BuildConfig.DEBUG) Log.d("SYNC_DEBUG", "path=$path id=$id data=$data fromCache=$fromCache pendingWrites=$pendingWrites")
    }

    private fun traceError(path: String, error: Exception) {
        if (BuildConfig.DEBUG) Log.e("SYNC_DEBUG", "path=$path listener error", error)
    }

    fun listen(
        profile: (Map<String, Any?>) -> Unit,
        favorites: (List<Map<String, Any?>>) -> Unit,
        playlists: (List<Map<String, Any?>>) -> Unit,
        moods: (List<Map<String, Any?>>) -> Unit,
        wellness: (List<Map<String, Any?>>) -> Unit,
        settings: (Map<String, Any?>) -> Unit,
        failure: (String) -> Unit
    ) {
        stopListening()
        val user = userRef() ?: return failure("Firebase session expired. Log in again.")
        if (BuildConfig.DEBUG) Log.d("SYNC_DEBUG", "UID=${currentUser?.uid} listener root=${user.path}")
        listeners += user.addSnapshotListener { snapshot, error ->
            if (error != null) { traceError(user.path, error); failure(error.localizedMessage ?: "Profile sync failed") }
            else {
                trace(user.path, snapshot?.id ?: user.id, snapshot?.data, snapshot?.metadata?.isFromCache ?: false, snapshot?.metadata?.hasPendingWrites() ?: false)
                profile(snapshot?.data.orEmpty())
            }
        }
        fun collection(path: String, receive: (List<Map<String, Any?>>) -> Unit) {
            val collection = user.collection(path)
            if (BuildConfig.DEBUG) Log.d("SYNC_DEBUG", "UID=${currentUser?.uid} listener path=${collection.path}")
            listeners += collection.addSnapshotListener { snapshot, error ->
                if (error != null) { traceError(collection.path, error); failure(error.localizedMessage ?: "$path sync failed") }
                else {
                    if (BuildConfig.DEBUG) Log.d("SYNC_DEBUG", "path=${collection.path} triggered size=${snapshot?.size()} fromCache=${snapshot?.metadata?.isFromCache} pendingWrites=${snapshot?.metadata?.hasPendingWrites()}")
                    snapshot?.documents?.forEach { trace(collection.path, it.id, it.data, snapshot.metadata.isFromCache, it.metadata.hasPendingWrites()) }
                    receive(snapshot?.documents?.map { it.data.orEmpty() + ("documentId" to it.id) }.orEmpty())
                }
            }
        }
        collection("favorites", favorites)
        collection("playlists", playlists)
        collection("moodHistory", moods)
        collection("wellnessData", wellness)
        val preferences = user.collection("preferences").document("settings")
        if (BuildConfig.DEBUG) Log.d("SYNC_DEBUG", "UID=${currentUser?.uid} listener path=${preferences.path}")
        listeners += preferences.addSnapshotListener { snapshot, error ->
            if (error != null) { traceError(preferences.path, error); failure(error.localizedMessage ?: "Preferences sync failed") }
            else {
                trace(preferences.path, preferences.id, snapshot?.data, snapshot?.metadata?.isFromCache ?: false, snapshot?.metadata?.hasPendingWrites() ?: false)
                settings(snapshot?.data.orEmpty())
            }
        }
    }

    fun saveProfile(name: String, email: String, done: (String?) -> Unit) {
        val user = currentUser ?: return done("Firebase session expired. Log in again.")
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .set(mapOf("name" to name, "email" to email, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }

    fun setFavorite(id: String, fields: Map<String, Any?>, liked: Boolean) {
        val ref = userRef()?.collection("favorites")?.document(id) ?: return
        if (liked) ref.set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()) else ref.delete()
    }
    fun newPlaylist(name: String, done: (String) -> Unit) {
        val ref = userRef()?.collection("playlists")?.document() ?: return
        ref.set(mapOf("name" to name, "trackIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        done(ref.id)
    }
    fun savePlaylist(id: String, fields: Map<String, Any?>) {
        userRef()?.collection("playlists")?.document(id)?.set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }
    fun createMood(mood: String): String? {
        val ref = userRef()?.collection("moodHistory")?.document() ?: return null
        ref.set(mapOf("mood" to mood, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        return ref.id
    }
    fun saveWellness(id: String, fields: Map<String, Any?>) {
        userRef()?.collection("wellnessData")?.document(id)?.set(fields + mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }
    fun savePreferences(fields: Map<String, Any?>) {
        userRef()?.collection("preferences")?.document("settings")?.set(fields + ("updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }
    fun logout() { stopListening(); if (configured) FirebaseAuth.getInstance().signOut() }
}
