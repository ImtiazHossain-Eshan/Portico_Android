# Portico Firebase bridge

These Vercel Functions verify Clerk session JWTs for Portico's server-only
operations:

- `/api/firebase-token` returns a short-lived Firebase custom token whose UID is
  the Clerk user ID.
- `/api/files` uploads, downloads and deletes private Vercel Blob objects. The
  server derives `users/{clerkUserId}/...`; client-supplied owner paths are
  rejected.
- `/api/properties` validates property bundles and atomically enforces the Free
  plan's two-property limit before creating records.
- `/api/subscription` owns sandbox receipts, upgrades, cancellation, resume and
  downgrade. It accepts predefined sandbox outcome tokens, never card numbers.
- `/api/notifications` registers owner-bound Firebase Installation IDs for FCM.
- `/api/workspace` erases owner records and private files without deleting the
  Clerk identity or billing history.

Required Vercel environment variables:

- `CLERK_ISSUER`: Clerk Frontend API origin, without a trailing slash.
- `FIREBASE_PROJECT_ID`: `portico-489`.
- `FIREBASE_CLIENT_EMAIL`: service-account email from Firebase.
- `FIREBASE_PRIVATE_KEY`: matching service-account private key.
- `PORTICO_BILLING_MODE`: optional; defaults to `sandbox` and must never be
  presented as real billing.

Set all four values in Vercel for Production, Preview, and Development. Never
commit a service-account JSON file or a populated `.env` file.

Connect one **private** Vercel Blob store to the project for Production, Preview
and Development. Vercel injects `BLOB_READ_WRITE_TOKEN`; never copy it into the
Android project.

After deployment, configure Android with:

```properties
FIREBASE_TOKEN_BRIDGE_URL=https://YOUR-PROJECT.vercel.app/api/firebase-token
PORTICO_FILE_API_URL=https://YOUR-PROJECT.vercel.app/api/files
```

Every endpoint supports `GET` as a non-secret health check. Every mutation
requires a valid Clerk bearer token. File uploads accept PDF, JPEG, PNG or WebP
up to 4 MB. Publish the repository's `firestore.rules` alongside the bridge;
those rules are what prevent a modified APK from forging quotas or billing.

Portico Intelligence is deliberately on-device. This bridge exposes no AI
endpoint and receives no portfolio context, questions or conversation history.
