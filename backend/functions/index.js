const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");

initializeApp();

exports.saveMoodSession = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const {initialMood, finalMood, helpful, songsPlayed = []} = request.data || {};
  if (typeof initialMood !== "string") throw new HttpsError("invalid-argument", "initialMood is required.");
  const ref = getFirestore().collection("users").doc(request.auth.uid).collection("moodSessions").doc();
  await ref.set({initialMood, finalMood: finalMood || null, helpful: helpful ?? null, songsPlayed, createdAt: FieldValue.serverTimestamp()});
  return {id: ref.id};
});

exports.health = onCall(() => ({service: "MoodTunes", status: "ok"}));
