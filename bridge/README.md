# Portico Firebase bridge

This Vercel Function verifies a Clerk session JWT and returns a short-lived
Firebase custom token whose UID is the Clerk user ID. The Android app then uses
that Firebase identity with the owner-only Firestore rules.

Required Vercel environment variables:

- `CLERK_ISSUER`: Clerk Frontend API origin, without a trailing slash.
- `FIREBASE_PROJECT_ID`: `portico-489`.
- `FIREBASE_CLIENT_EMAIL`: service-account email from Firebase.
- `FIREBASE_PRIVATE_KEY`: matching service-account private key.

Set all four values in Vercel for Production, Preview, and Development. Never
commit a service-account JSON file or a populated `.env` file.

After deployment, configure Android with:

```properties
FIREBASE_TOKEN_BRIDGE_URL=https://YOUR-PROJECT.vercel.app/api/firebase-token
```

The endpoint supports `GET` as a non-secret health check and requires a valid
Clerk bearer token for `POST`.
