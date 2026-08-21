import {del} from "@vercel/blob";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {firestore} from "./firebase-admin.js";

export const RECORD_COLLECTIONS = [
  "properties", "income", "expenses", "valuations", "documents",
  "activity", "notifications", "conversations",
] as const;

/** Collections holding server-owned facts, erased only on account deletion. */
const SERVER_OWNED_SETTINGS = ["subscription", "quota", "aiUsage", "rateLimit"] as const;

export interface ErasureResult {
  deletedRecords: number;
  deletedFiles: number;
}

/**
 * Removes every portfolio record a member owns, plus the private files those
 * records point at.
 *
 * [keepAccount] true resets the workspace but leaves the member signed in with
 * an empty portfolio; false is the account-deletion path, which also clears the
 * server-owned settings and the root user document.
 *
 * Firestore caps a batch at 500 writes, so references are committed in chunks.
 * Blob deletion is best-effort and logged rather than thrown: a file that
 * outlives its record is recoverable, but a half-erased portfolio is not.
 */
export async function eraseMemberData(
  userId: string,
  keepAccount: boolean,
): Promise<ErasureResult> {
  const db = firestore();
  const user = db.doc(`users/${userId}`);

  const snapshots = await Promise.all(
    RECORD_COLLECTIONS.map((name) => user.collection(name).get()),
  );

  const documentSnapshot = snapshots[RECORD_COLLECTIONS.indexOf("documents")];
  const storagePaths = documentSnapshot.docs
    .map((document) => document.get("storagePath"))
    .filter((path): path is string => typeof path === "string" && path.length > 0);

  const references = snapshots.flatMap((snapshot) =>
    snapshot.docs.map((document) => document.ref),
  );

  if (!keepAccount) {
    const settings = await user.collection("settings").get();
    references.push(...settings.docs.map((document) => document.ref));
    const devices = await user.collection("devices").get();
    references.push(...devices.docs.map((document) => document.ref));
    const payments = await user.collection("payments").get();
    references.push(...payments.docs.map((document) => document.ref));
  }

  for (let offset = 0; offset < references.length; offset += 400) {
    const batch = db.batch();
    references.slice(offset, offset + 400).forEach((reference) => batch.delete(reference));
    await batch.commit();
  }

  let deletedFiles = 0;
  if (storagePaths.length > 0) {
    try {
      await del(storagePaths);
      deletedFiles = storagePaths.length;
    } catch (error) {
      console.error("File cleanup failed", {
        message: error instanceof Error ? error.message : "unknown_error",
      });
    }
  }

  if (keepAccount) {
    await Promise.all([
      user.collection("settings").doc("quota").set({
        propertyCount: 0,
        updatedAt: FieldValue.serverTimestamp(),
      }),
      user.set({
        revision: randomUUID(),
        updatedAt: FieldValue.serverTimestamp(),
        updatedAtEpochMillis: Date.now(),
        entityCounts: Object.fromEntries(
          RECORD_COLLECTIONS.map((name) => [name, 0]),
        ),
      }, {merge: true}),
    ]);
  } else {
    await user.delete();
  }

  return {deletedRecords: references.length, deletedFiles};
}

export {SERVER_OWNED_SETTINGS};
