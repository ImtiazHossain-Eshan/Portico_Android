import type {VercelRequest, VercelResponse} from "@vercel/node";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {firestore} from "../lib/firebase-admin.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {sendUserNotification} from "../lib/notifications.js";

const plans = {
  plan_pro_monthly: {name: "Pro", amountMinor: 1_200, currency: "USD", interval: "month"},
  plan_pro_yearly: {name: "Pro annual", amountMinor: 12_000, currency: "USD", interval: "year"},
} as const;

const sandboxTokens: Record<string, {
  status: "SUCCEEDED" | "DECLINED" | "FAILED";
  last4: string;
  brand: string;
  reason?: string;
}> = {
  succeeds: {status: "SUCCEEDED", last4: "4242", brand: "Visa"},
  declined: {status: "DECLINED", last4: "0002", brand: "Visa", reason: "Card declined"},
  insufficient: {status: "DECLINED", last4: "9995", brand: "Visa", reason: "Insufficient funds"},
  expired: {status: "DECLINED", last4: "0069", brand: "Visa", reason: "Expired card"},
  processing_error: {status: "FAILED", last4: "0119", brand: "Visa", reason: "Processing error"},
};

function renewal(interval: string): string {
  const date = new Date();
  if (interval === "year") date.setUTCFullYear(date.getUTCFullYear() + 1);
  else date.setUTCMonth(date.getUTCMonth() + 1);
  return date.toISOString().slice(0, 10);
}

function rootUpdate() {
  return {
    schemaVersion: 2,
    revision: randomUUID(),
    updatedAt: FieldValue.serverTimestamp(),
    updatedAtEpochMillis: Date.now(),
  };
}

async function checkout(userId: string, body: Record<string, unknown>) {
  if ((process.env.PORTICO_BILLING_MODE ?? "sandbox") !== "sandbox") {
    throw new ApiError(503, "billing_provider_not_configured");
  }
  const planId = typeof body.planId === "string" ? body.planId : "";
  const plan = plans[planId as keyof typeof plans];
  if (!plan) throw new ApiError(400, "invalid_plan");
  const sandboxToken = typeof body.sandboxToken === "string" ? body.sandboxToken : "";
  const outcome = sandboxTokens[sandboxToken];
  if (!outcome) {
    throw new ApiError(400, "sandbox_card_required", "Use one of the displayed sandbox cards; never enter a real card");
  }

  const db = firestore();
  const user = db.doc(`users/${userId}`);
  const paymentId = `pay_${randomUUID()}`;
  const today = new Date().toISOString().slice(0, 10);
  const payment = {
    id: paymentId,
    userId,
    planId,
    amountMinor: plan.amountMinor,
    currency: plan.currency,
    status: outcome.status,
    date: today,
    cardLast4: outcome.last4,
    cardBrand: outcome.brand,
    failureReason: outcome.reason ?? null,
    mode: "sandbox",
    createdAt: FieldValue.serverTimestamp(),
  };
  const subscription = {
    planTier: "PRO",
    planId,
    status: "Active",
    startDate: today,
    renewsOn: renewal(plan.interval),
    cancelAtPeriodEnd: false,
    source: "sandbox_server",
  };

  await db.runTransaction(async (transaction) => {
    transaction.create(db.doc(`payments/${paymentId}`), payment);
    transaction.create(user.collection("payments").doc(paymentId), payment);
    if (outcome.status === "SUCCEEDED") {
      transaction.set(user.collection("settings").doc("subscription"), subscription);
    }
    const activityId = `activity_${randomUUID()}`;
    transaction.create(user.collection("activity").doc(activityId), {
      id: activityId,
      kind: "PLAN_CHANGED",
      title: outcome.status === "SUCCEEDED" ? `Subscribed to ${plan.name}` : `Payment ${outcome.status.toLowerCase()}`,
      detail: `${plan.name} · $${(plan.amountMinor / 100).toFixed(2)} · ${outcome.brand} ending ${outcome.last4}`,
      amount: null,
      timestamp: today,
      propertyId: null,
    });
    transaction.set(user, rootUpdate(), {merge: true});
  });

  if (outcome.status === "SUCCEEDED") {
    await sendUserNotification(userId, "subscription", "Portico Pro is active", `${plan.name} is ready in your workspace.`)
      .catch((error) => console.error("Subscription notification failed", {
        message: error instanceof Error ? error.message : "unknown_error",
      }));
  }
  return {payment: {...payment, createdAt: undefined}, subscription: outcome.status === "SUCCEEDED" ? subscription : null};
}

async function changeSubscription(userId: string, action: string) {
  const db = firestore();
  const user = db.doc(`users/${userId}`);
  const reference = user.collection("settings").doc("subscription");
  let updated: Record<string, unknown> = {};

  await db.runTransaction(async (transaction) => {
    const [subscriptionSnapshot, propertyCountSnapshot] = await Promise.all([
      transaction.get(reference),
      user.collection("properties").count().get(),
    ]);
    const current = subscriptionSnapshot.data() ?? {
      planTier: "FREE", planId: "plan_free", status: "Active", startDate: "", renewsOn: null, cancelAtPeriodEnd: false,
    };
    switch (action) {
      case "cancel":
        if (current.planTier !== "PRO") throw new ApiError(409, "subscription_not_active");
        updated = {...current, status: "Cancels at period end", cancelAtPeriodEnd: true};
        break;
      case "resume":
        if (current.planTier !== "PRO") throw new ApiError(409, "subscription_not_active");
        updated = {...current, status: "Active", cancelAtPeriodEnd: false};
        break;
      case "downgrade":
        if (propertyCountSnapshot.data().count > 2) {
          throw new ApiError(409, "property_limit_reached", "Remove properties until two remain before switching to Free");
        }
        updated = {planTier: "FREE", planId: "plan_free", status: "Active", startDate: new Date().toISOString().slice(0, 10), renewsOn: null, cancelAtPeriodEnd: false};
        break;
      default:
        throw new ApiError(400, "invalid_subscription_action");
    }
    transaction.set(reference, updated);
    transaction.set(user, rootUpdate(), {merge: true});
  });
  return updated;
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);
  if (request.method === "GET") {
    response.status(200).json({service: "portico-subscription", status: "ready", billingMode: process.env.PORTICO_BILLING_MODE ?? "sandbox"});
    return;
  }
  if (request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    const body = bodyObject(request);
    const action = typeof body.action === "string" ? body.action : "";
    if (action === "checkout") {
      response.status(200).json(await checkout(userId, body));
    } else {
      response.status(200).json({subscription: await changeSubscription(userId, action)});
    }
  } catch (error) {
    sendError(response, error, "Subscription operation failed");
  }
}
