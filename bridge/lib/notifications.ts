import {createHash, randomUUID} from "node:crypto";
import {FieldValue} from "firebase-admin/firestore";
import {firestore, messaging} from "./firebase-admin.js";

function installationDocumentId(installationId: string): string {
  return createHash("sha256").update(installationId).digest("hex").slice(0, 40);
}

export async function registerDevice(userId: string, installationId: string, platform: string, appVersion: string) {
  await firestore().doc(`users/${userId}/devices/${installationDocumentId(installationId)}`).set({
    installationId,
    platform,
    appVersion,
    enabled: true,
    updatedAt: FieldValue.serverTimestamp(),
  }, {merge: true});
}

export async function unregisterDevice(userId: string, installationId: string) {
  await firestore().doc(`users/${userId}/devices/${installationDocumentId(installationId)}`).delete();
}

export async function sendUserNotification(
  userId: string,
  type: string,
  title: string,
  message: string,
) {
  const db = firestore();
  const now = new Date().toISOString().slice(0, 10);
  const notification = {
    id: `notification_${randomUUID()}`,
    type,
    title,
    message,
    timestamp: now,
    read: false,
  };
  await db.doc(`users/${userId}/notifications/${notification.id}`).set(notification);

  const devices = await db.collection(`users/${userId}/devices`)
    .where("enabled", "==", true)
    .limit(20)
    .get();
  if (devices.empty) return;

  const installationIds = devices.docs
    .map((document) => document.get("installationId"))
    .filter((installationId): installationId is string => typeof installationId === "string" && installationId.length > 10);
  if (installationIds.length === 0) return;

  const result = await messaging().sendEachForMulticast({
    fids: installationIds,
    notification: {title, body: message},
    data: {type},
    android: {priority: "high", notification: {channelId: "portfolio_updates"}},
  });

  const staleDeletes = result.responses.flatMap((response, index) => {
    if (response.success) return [];
    const code = response.error?.code ?? "";
    if (!code.includes("registration-token-not-registered") && !code.includes("invalid-registration-token")) return [];
    return [db.doc(`users/${userId}/devices/${installationDocumentId(installationIds[index])}`).delete()];
  });
  await Promise.all(staleDeletes);
}
