import type {VercelRequest, VercelResponse} from "@vercel/node";
import {importPKCS8, SignJWT} from "jose";
import {ClerkAuthorizationError, verifyClerkRequest} from "../lib/clerk-auth.js";
import {consumeRateLimit} from "../lib/rate-limit.js";

const requiredEnvironment = [
  "CLERK_ISSUER",
  "FIREBASE_PROJECT_ID",
  "FIREBASE_CLIENT_EMAIL",
  "FIREBASE_PRIVATE_KEY",
] as const;

let cachedPrivateKeyMaterial = "";
let cachedPrivateKey: Awaited<ReturnType<typeof importPKCS8>> | null = null;

const FIREBASE_CUSTOM_TOKEN_AUDIENCE =
  "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";

function environment() {
  const missing = requiredEnvironment.filter((key) => !process.env[key]?.trim());
  if (missing.length > 0) {
    throw new Error(`Missing server environment: ${missing.join(", ")}`);
  }

  return {
    firebaseProjectId: process.env.FIREBASE_PROJECT_ID!,
    firebaseClientEmail: process.env.FIREBASE_CLIENT_EMAIL!,
    firebasePrivateKey: process.env.FIREBASE_PRIVATE_KEY!.replace(/\\n/g, "\n"),
  };
}

async function signingKey(privateKey: string) {
  if (!cachedPrivateKey || cachedPrivateKeyMaterial !== privateKey) {
    cachedPrivateKeyMaterial = privateKey;
    cachedPrivateKey = await importPKCS8(privateKey, "RS256");
  }
  return cachedPrivateKey;
}

async function firebaseCustomToken(userId: string) {
  const config = environment();
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({uid: userId, claims: {authProvider: "clerk"}})
    .setProtectedHeader({alg: "RS256", typ: "JWT"})
    .setIssuer(config.firebaseClientEmail)
    .setSubject(config.firebaseClientEmail)
    .setAudience(FIREBASE_CUSTOM_TOKEN_AUDIENCE)
    .setIssuedAt(now)
    .setExpirationTime(now + 60 * 60)
    .sign(await signingKey(config.firebasePrivateKey));
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");

  if (request.method === "GET") {
    response.status(200).json({service: "portico-firebase-bridge", status: "ready"});
    return;
  }

  if (request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const clerkUserId = await verifyClerkRequest(request);
    await consumeRateLimit(clerkUserId, "firebase-token");
    const token = await firebaseCustomToken(clerkUserId);
    response.status(200).json({token});
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown_error";
    console.error("Clerk to Firebase exchange failed", {message});
    const unavailable = message.startsWith("Missing server environment");
    const authCode = error instanceof ClerkAuthorizationError
      ? error.code
      : "invalid_session_token";
    response.status(unavailable ? 503 : 401).json({error: unavailable ? "server_not_configured" : authCode});
  }
}
