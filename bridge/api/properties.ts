import type {VercelRequest, VercelResponse} from "@vercel/node";
import {del} from "@vercel/blob";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {consumeRateLimit} from "../lib/rate-limit.js";
import {firestore} from "../lib/firebase-admin.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {propertyBundles, safeId} from "../lib/validation.js";

const FREE_PROPERTY_LIMIT = 2;
const RELATED_COLLECTIONS = ["income", "expenses", "valuations", "documents", "activity", "conversations"];

function rootUpdate(propertyCount: number) {
  return {
    schemaVersion: 2,
    revision: randomUUID(),
    updatedAt: FieldValue.serverTimestamp(),
    updatedAtEpochMillis: Date.now(),
    "entityCounts.properties": propertyCount,
  };
}

async function createProperties(userId: string, records: ReturnType<typeof propertyBundles>) {
  const db = firestore();
  const user = db.doc(`users/${userId}`);
  const properties = user.collection("properties");
  const actualCount = (await properties.count().get()).data().count;
  const quota = user.collection("settings").doc("quota");
  const subscription = user.collection("settings").doc("subscription");

  await db.runTransaction(async (transaction) => {
    const [quotaSnapshot, subscriptionSnapshot, ...propertySnapshots] = await Promise.all([
      transaction.get(quota),
      transaction.get(subscription),
      ...records.map((record) => transaction.get(properties.doc(record.property.id as string))),
    ]);
    if (propertySnapshots.some((snapshot) => snapshot.exists)) {
      throw new ApiError(409, "property_exists");
    }
    const quotaCount = quotaSnapshot.exists ? Number(quotaSnapshot.get("propertyCount") ?? 0) : actualCount;
    const currentCount = Math.max(actualCount, quotaCount);
    const isPro = subscriptionSnapshot.get("planTier") === "PRO" && subscriptionSnapshot.get("status") !== "Expired";
    if (!isPro && currentCount + records.length > FREE_PROPERTY_LIMIT) {
      throw new ApiError(409, "plan_limit_reached", "Upgrade to Pro to add another property");
    }

    for (const record of records) {
      const propertyId = record.property.id as string;
      transaction.create(properties.doc(propertyId), record.property);
      for (const income of record.incomeEntries) {
        transaction.create(user.collection("income").doc(income.id as string), income);
      }
      for (const expense of record.expenseEntries) {
        transaction.create(user.collection("expenses").doc(expense.id as string), expense);
      }
      const activityId = `activity_${randomUUID()}`;
      transaction.create(user.collection("activity").doc(activityId), {
        id: activityId,
        kind: "PROPERTY_ADDED",
        title: records.length === 1 ? "Property added" : "Property imported",
        detail: record.property.name,
        amount: null,
        timestamp: new Date().toISOString().slice(0, 10),
        propertyId,
      });
    }
    const nextCount = currentCount + records.length;
    transaction.set(quota, {propertyCount: nextCount, updatedAt: FieldValue.serverTimestamp()}, {merge: true});
    transaction.set(user, rootUpdate(nextCount), {merge: true});
  });
}

async function deleteProperty(userId: string, propertyId: string) {
  const db = firestore();
  const user = db.doc(`users/${userId}`);
  const property = user.collection("properties").doc(propertyId);
  if (!(await property.get()).exists) throw new ApiError(404, "property_not_found");

  const related = await Promise.all(RELATED_COLLECTIONS.map((name) =>
    user.collection(name).where("propertyId", "==", propertyId).get(),
  ));
  const storagePaths = related[3].docs
    .map((document) => document.get("storagePath"))
    .filter((path): path is string => typeof path === "string" && path.length > 0);

  const batch = db.batch();
  batch.delete(property);
  related.forEach((snapshot) => snapshot.docs.forEach((document) => batch.delete(document.ref)));
  const remaining = Math.max(0, (await user.collection("properties").count().get()).data().count - 1);
  batch.set(user.collection("settings").doc("quota"), {
    propertyCount: remaining,
    updatedAt: FieldValue.serverTimestamp(),
  }, {merge: true});
  batch.set(user, rootUpdate(remaining), {merge: true});
  await batch.commit();

  if (storagePaths.length > 0) {
    await del(storagePaths).catch((error) => console.error("Property file cleanup failed", {
      message: error instanceof Error ? error.message : "unknown_error",
    }));
  }
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);
  if (request.method === "GET") {
    response.status(200).json({service: "portico-properties", status: "ready", freeLimit: FREE_PROPERTY_LIMIT});
    return;
  }
  if (request.method !== "POST" && request.method !== "DELETE") {
    response.setHeader("Allow", "GET, POST, DELETE");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "properties");
    if (request.method === "POST") {
      const records = propertyBundles(bodyObject(request).records);
      await createProperties(userId, records);
      response.status(201).json({created: records.length});
    } else {
      const rawId = Array.isArray(request.query.id) ? request.query.id[0] : request.query.id;
      await deleteProperty(userId, safeId(rawId, "propertyId"));
      response.status(200).json({deleted: true});
    }
  } catch (error) {
    sendError(response, error, "Property operation failed");
  }
}
