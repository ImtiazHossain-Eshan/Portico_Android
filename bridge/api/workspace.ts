import type {VercelRequest, VercelResponse} from "@vercel/node";
import {del} from "@vercel/blob";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {firestore} from "../lib/firebase-admin.js";
import {privateJson, sendError} from "../lib/http.js";

const RECORD_COLLECTIONS = [
  "properties", "income", "expenses", "valuations", "documents",
  "activity", "notifications", "conversations",
];

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);
  if (request.method === "GET") {
    response.status(200).json({service: "portico-workspace", status: "ready"});
    return;
  }
  if (request.method !== "DELETE") {
    response.setHeader("Allow", "GET, DELETE");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    const db = firestore();
    const user = db.doc(`users/${userId}`);
    const snapshots = await Promise.all(RECORD_COLLECTIONS.map((name) => user.collection(name).get()));
    const documentSnapshot = snapshots[RECORD_COLLECTIONS.indexOf("documents")];
    const storagePaths = documentSnapshot.docs
      .map((document) => document.get("storagePath"))
      .filter((path): path is string => typeof path === "string" && path.length > 0);

    const references = snapshots.flatMap((snapshot) => snapshot.docs.map((document) => document.ref));
    for (let offset = 0; offset < references.length; offset += 400) {
      const batch = db.batch();
      references.slice(offset, offset + 400).forEach((reference) => batch.delete(reference));
      await batch.commit();
    }
    await Promise.all([
      user.collection("settings").doc("quota").set({propertyCount: 0, updatedAt: FieldValue.serverTimestamp()}),
      user.set({
        revision: randomUUID(),
        updatedAt: FieldValue.serverTimestamp(),
        updatedAtEpochMillis: Date.now(),
        entityCounts: {
          properties: 0, income: 0, expenses: 0, valuations: 0,
          documents: 0, activity: 0, notifications: 0, conversations: 0,
        },
      }, {merge: true}),
      storagePaths.length > 0
        ? del(storagePaths).catch((error) => console.error("Workspace file cleanup failed", {
          message: error instanceof Error ? error.message : "unknown_error",
        }))
        : Promise.resolve(),
    ]);
    response.status(200).json({deletedRecords: references.length});
  } catch (error) {
    sendError(response, error, "Workspace erasure failed");
  }
}
