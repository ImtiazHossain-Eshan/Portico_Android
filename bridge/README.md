# Portico Firebase bridge

These Vercel Functions verify Clerk session JWTs for two server-only services:

- `/api/firebase-token` returns a short-lived Firebase custom token whose UID is
  the Clerk user ID.
- `/api/files` uploads, downloads and deletes private Vercel Blob objects. The
  server derives `users/{clerkUserId}/...`; client-supplied owner paths are
  rejected.

Required Vercel environment variables:

- `CLERK_ISSUER`: Clerk Frontend API origin, without a trailing slash.
- `FIREBASE_PROJECT_ID`: `portico-489`.
- `FIREBASE_CLIENT_EMAIL`: service-account email from Firebase.
- `FIREBASE_PRIVATE_KEY`: matching service-account private key.

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

Both endpoints support `GET` without a resource path as a non-secret health
check. All identity exchange and file operations require a valid Clerk bearer
token. File uploads accept PDF, JPEG, PNG or WebP up to 4 MB.
