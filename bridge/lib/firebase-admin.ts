import {cert, getApps, initializeApp} from "firebase-admin/app";
import {getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";

const requiredEnvironment = [
  "FIREBASE_PROJECT_ID",
  "FIREBASE_CLIENT_EMAIL",
  "FIREBASE_PRIVATE_KEY",
] as const;

export function firebaseApp() {
  const existing = getApps()[0];
  if (existing) return existing;

  const missing = requiredEnvironment.filter((key) => !process.env[key]?.trim());
  if (missing.length > 0) {
    throw new Error(`Missing server environment: ${missing.join(", ")}`);
  }

  return initializeApp({
    credential: cert({
      projectId: process.env.FIREBASE_PROJECT_ID!,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL!,
      privateKey: process.env.FIREBASE_PRIVATE_KEY!.replace(/\\n/g, "\n"),
    }),
  });
}

export function firestore() {
  return getFirestore(firebaseApp());
}

export function messaging() {
  return getMessaging(firebaseApp());
}
