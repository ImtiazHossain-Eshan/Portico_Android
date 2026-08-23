import type {VercelRequest, VercelResponse} from "@vercel/node";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {consumeRateLimit} from "../lib/rate-limit.js";
import {firestore} from "../lib/firebase-admin.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {sendUserNotification} from "../lib/notifications.js";
import {
  createSession,
  isSettled,
  matchesOrder,
  queryByTransactionId,
  sslcommerzConfig,
  validatePayment,
  type ValidationResult,
} from "../lib/sslcommerz.js";
import {GATEWAY_PLANS, publicBaseUrl, renewalDate, type GatewayPlanId} from "../lib/billing.js";

/*
 * The whole SSLCommerz surface in one function, dispatched on ?mode=.
 *
 * Three endpoints would read better, and that is how this was first written.
 * Vercel's Hobby plan allows twelve Serverless Functions per deployment and the
 * project was already at ten, so init, ipn and return share one entry point.
 * The URLs handed to SSLCommerz carry the mode, so nothing is guessed from the
 * shape of a request.
 *
 *   ?mode=init    POST, Clerk-authenticated, opens a gateway session
 *   ?mode=ipn     POST, public, the gateway's server-to-server callback
 *   ?mode=return  GET,  public, where the member's browser lands
 */

const APP_SCHEME = "portico";

function firstValue(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

// ---------------------------------------------------------------- init

async function startCheckout(userId: string, body: Record<string, unknown>) {
  if ((process.env.PORTICO_BILLING_MODE ?? "sandbox") !== "sslcommerz") {
    throw new ApiError(409, "gateway_billing_not_enabled",
      "Set PORTICO_BILLING_MODE=sslcommerz to use the gateway");
  }

  const planId = typeof body.planId === "string" ? body.planId : "";
  const plan = GATEWAY_PLANS[planId as GatewayPlanId];
  if (!plan) throw new ApiError(400, "invalid_plan");

  const config = sslcommerzConfig();
  const base = publicBaseUrl();
  const tranId = `ptc_${randomUUID().replace(/-/g, "")}`;
  const db = firestore();
  const user = db.doc(`users/${userId}`);

  const order = {
    tranId,
    userId,
    planId,
    amountBdt: plan.amountBdt,
    currency: "BDT",
    status: "PENDING",
    mode: config.sandbox ? "sslcommerz_sandbox" : "sslcommerz_live",
    createdAt: FieldValue.serverTimestamp(),
    createdAtEpochMillis: Date.now(),
  };

  // The order is written before the member leaves. The IPN arrives from the
  // public internet carrying a transaction id, and without a record already on
  // our side there is nothing to check its amount against.
  await db.runTransaction(async (transaction) => {
    transaction.create(db.doc(`orders/${tranId}`), order);
    transaction.create(user.collection("payments").doc(tranId), {
      id: tranId,
      planId,
      amountMinor: plan.amountBdt * 100,
      currency: "BDT",
      status: "PENDING",
      date: new Date().toISOString().slice(0, 10),
      cardLast4: "",
      cardBrand: "",
      failureReason: null,
      mode: order.mode,
      createdAt: FieldValue.serverTimestamp(),
    });
  });

  const session = await createSession(config, {
    tranId,
    amountBdt: plan.amountBdt,
    productName: plan.name,
    successUrl: `${base}/api/payment?mode=return&tran_id=${tranId}&result=success`,
    failUrl: `${base}/api/payment?mode=return&tran_id=${tranId}&result=fail`,
    cancelUrl: `${base}/api/payment?mode=return&tran_id=${tranId}&result=cancel`,
    ipnUrl: `${base}/api/ipn`,
    customerName: typeof body.customerName === "string" && body.customerName.trim()
      ? body.customerName.trim().slice(0, 60)
      : "Portico member",
    customerEmail: typeof body.customerEmail === "string" && body.customerEmail.includes("@")
      ? body.customerEmail.trim().slice(0, 120)
      : "member@portico.app",
  });

  await db.doc(`orders/${tranId}`).set({sessionKey: session.sessionKey}, {merge: true});

  return {
    tranId,
    gatewayPageUrl: session.gatewayPageUrl,
    amountBdt: plan.amountBdt,
    currency: "BDT",
    sandbox: config.sandbox,
  };
}

// ----------------------------------------------------------------- ipn

function parseIpnBody(request: VercelRequest): Record<string, unknown> {
  if (typeof request.body === "string") {
    if (request.body.trimStart().startsWith("{")) {
      try {
        return JSON.parse(request.body) as Record<string, unknown>;
      } catch {
        return {};
      }
    }
    return Object.fromEntries(new URLSearchParams(request.body));
  }
  return (request.body ?? {}) as Record<string, unknown>;
}

function field(body: Record<string, unknown>, key: string): string {
  const value = body[key];
  return typeof value === "string" ? value : "";
}

/*
 * Everything in the IPN body is untrusted: it is an unauthenticated POST from
 * the public internet. It is treated as a hint that something happened. The
 * only fact acted on is what the validation API says when asked directly,
 * matched against the order written before the member reached the gateway.
 *
 * The two ways this could lose money, both closed here:
 *   a forged POST claiming success       -> answered by validatePayment()
 *   a real payment for the wrong amount  -> answered by matchesOrder()
 */
async function settle(tranId: string, valId: string): Promise<string> {
  const config = sslcommerzConfig();
  const validation = await validatePayment(config, valId);
  return applyValidation(tranId, validation, valId, "IPN");
}

/**
 * Settle an order against an answer already obtained from SSLCommerz.
 *
 * Shared by both directions on purpose. The IPN pushes a val_id and the return
 * hop makes us pull by transaction id, but what may be credited, and on what
 * evidence, must not depend on which of the two arrived first.
 */
async function applyValidation(
  tranId: string,
  validation: ValidationResult,
  valId: string,
  source: string,
): Promise<string> {
  const config = sslcommerzConfig();
  const db = firestore();
  const orderReference = db.doc(`orders/${tranId}`);
  const snapshot = await orderReference.get();
  if (!snapshot.exists) {
    console.log(`${source}: no such order`, {tranId});
    return "unknown_transaction";
  }

  const order = snapshot.data() as {
    userId: string;
    planId: string;
    amountBdt: number;
    currency: string;
    status: string;
  };

  // The IPN can fire more than once. Crediting twice is worse than not
  // crediting at all, so a settled order is a no-op.
  if (order.status !== "PENDING") {
    console.log(`${source}: order already settled`, {tranId, status: order.status});
    return "already_settled";
  }

  console.log(`${source}: validated`, {
    tranId,
    gatewayStatus: validation.status,
    gatewayAmount: validation.amountBdt,
    gatewayCurrency: validation.currency,
    gatewayTranId: validation.tranId,
    orderAmount: order.amountBdt,
    orderCurrency: order.currency,
  });

  if (!isSettled(validation.status) || !matchesOrder(validation, {
    tranId,
    amountBdt: order.amountBdt,
    currency: order.currency,
  })) {
    await orderReference.set({
      status: "REJECTED",
      gatewayStatus: validation.status,
      settledAt: FieldValue.serverTimestamp(),
    }, {merge: true});
    await db.doc(`users/${order.userId}/payments/${tranId}`).set({
      status: "FAILED",
      failureReason: isSettled(validation.status) ? "Amount mismatch" : "Payment not completed",
    }, {merge: true});
    console.log(`${source}: rejected`, {
      tranId,
      settled: isSettled(validation.status),
      amountMatch: Math.round(validation.amountBdt * 100) === Math.round(order.amountBdt * 100),
      currencyMatch: validation.currency === order.currency,
      tranIdMatch: validation.tranId === tranId,
    });
    return "rejected";
  }

  const plan = GATEWAY_PLANS[order.planId as GatewayPlanId];
  const today = new Date().toISOString().slice(0, 10);
  const user = db.doc(`users/${order.userId}`);

  await db.runTransaction(async (transaction) => {
    const fresh = await transaction.get(orderReference);
    if ((fresh.data()?.status ?? "") !== "PENDING") return;

    transaction.set(orderReference, {
      status: "VALID",
      valId: valId || null,
      gatewayStatus: validation.status,
      riskLevel: validation.riskLevel,
      settledAt: FieldValue.serverTimestamp(),
    }, {merge: true});

    transaction.set(user.collection("payments").doc(tranId), {
      status: "SUCCEEDED",
      cardBrand: validation.cardType || "SSLCommerz",
      cardLast4: validation.cardLast4,
      failureReason: null,
    }, {merge: true});

    transaction.set(user.collection("settings").doc("subscription"), {
      planTier: "PRO",
      planId: order.planId,
      status: "Active",
      startDate: today,
      renewsOn: renewalDate(plan?.interval ?? "month"),
      cancelAtPeriodEnd: false,
      source: config.sandbox ? "sslcommerz_sandbox" : "sslcommerz",
    });

    const activityId = `activity_${randomUUID()}`;
    transaction.create(user.collection("activity").doc(activityId), {
      id: activityId,
      kind: "PLAN_CHANGED",
      title: `Subscribed to ${plan?.name ?? "Portico Pro"}`,
      detail: `BDT ${order.amountBdt.toFixed(2)} via SSLCommerz${config.sandbox ? " sandbox" : ""}`,
      amount: null,
      timestamp: today,
      propertyId: null,
    });

    transaction.set(user, {
      schemaVersion: 2,
      revision: randomUUID(),
      updatedAt: FieldValue.serverTimestamp(),
      updatedAtEpochMillis: Date.now(),
    }, {merge: true});
  });

  await sendUserNotification(
    order.userId,
    "subscription",
    "Portico Pro is active",
    `${plan?.name ?? "Portico Pro"} is ready in your workspace.`,
  ).catch((error) => console.error("Subscription notification failed", {
    message: error instanceof Error ? error.message : "unknown_error",
  }));

  console.log(`${source}: settled`, {tranId, planId: order.planId, amountBdt: order.amountBdt});
  return "settled";
}

async function handleIpn(request: VercelRequest, response: VercelResponse) {
  const body = parseIpnBody(request);
  const tranId = field(body, "tran_id");
  const valId = field(body, "val_id");

  if (!tranId || !valId) {
    response.status(400).json({error: "missing_transaction_reference"});
    return;
  }

  try {
    const outcome = await settle(tranId, valId);
    // Always 200 once the reference parses. SSLCommerz retries on a non-2xx,
    // and a rejected payment is a decided outcome, not a delivery failure.
    response.status(200).json({received: true, outcome});
  } catch (error) {
    console.error("IPN settlement failed", {
      tranId,
      message: error instanceof Error ? error.message : "unknown_error",
    });
    // A 500 here is honest: we could not decide, so a retry is wanted.
    response.status(500).json({error: "settlement_failed"});
  }
}

// -------------------------------------------------------------- return

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/*
 * Where the member's browser lands. This decides nothing, and cannot: the
 * browser is not a trusted channel and the redirect can be replayed or
 * hand-typed. Crediting happens in the IPN alone. All this does is bounce back
 * into the app with the transaction id so the app knows what to ask about.
 *
 * A short page rather than a bare 302, because some gateway webviews will not
 * follow a redirect to a custom scheme.
 */
function renderReturn(request: VercelRequest, response: VercelResponse) {
  const tranId = firstValue(request.query.tran_id).replace(/[^A-Za-z0-9_]/g, "").slice(0, 80);
  const rawResult = firstValue(request.query.result);
  const result = ["success", "fail", "cancel"].includes(rawResult) ? rawResult : "fail";

  const deepLink = `${APP_SCHEME}://payment?tran_id=${encodeURIComponent(tranId)}&result=${result}`;
  const heading = result === "success"
    ? "Payment received"
    : result === "cancel" ? "Payment cancelled" : "Payment not completed";
  const detail = result === "success"
    ? "Returning you to Portico. Your plan updates once the payment is confirmed."
    : "Returning you to Portico. Nothing has been charged.";

  response.setHeader("Content-Type", "text/html; charset=utf-8");
  response.status(200).send(`<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(heading)}</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
         font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
         background:#0B0B0C; color:#F5F4F2; text-align:center; padding:24px; }
  main { max-width: 22rem; }
  h1 { font-size:1.25rem; margin:0 0 .5rem; }
  p { margin:0 0 1.5rem; line-height:1.5; opacity:.75; font-size:.95rem; }
  a { display:inline-block; padding:.7rem 1.4rem; border-radius:999px;
      background:#E8A33D; color:#1A1917; text-decoration:none; font-weight:600; }
</style>
</head>
<body>
<main>
  <h1>${escapeHtml(heading)}</h1>
  <p>${escapeHtml(detail)}</p>
  <a href="${escapeHtml(deepLink)}">Return to Portico</a>
</main>
<script>
  setTimeout(function () { window.location.replace(${JSON.stringify(deepLink)}); }, 400);
</script>
</body>
</html>`);
}

// ------------------------------------------------------------ dispatch

export default async function handler(request: VercelRequest, response: VercelResponse) {
  const mode = firstValue(request.query.mode);

  if (mode === "return") {
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    renderReturn(request, response);
    return;
  }

  if (mode === "ipn") {
    response.setHeader("Cache-Control", "no-store");
    if (request.method === "GET") {
      response.status(200).json({service: "portico-payment-ipn", status: "ready"});
      return;
    }
    if (request.method !== "POST") {
      response.setHeader("Allow", "GET, POST");
      response.status(405).json({error: "method_not_allowed"});
      return;
    }
    await handleIpn(request, response);
    return;
  }

  /*
   * Called by the app when the member returns from the gateway.
   *
   * The redirect proves nothing, so this does not read a result from it. It
   * takes only the transaction id, checks the order belongs to the caller, and
   * asks SSLCommerz directly what happened. The IPN may still arrive later and
   * will find the order already settled, which is a no-op.
   */
  if (mode === "confirm") {
    privateJson(response);
    if (request.method !== "POST") {
      response.setHeader("Allow", "POST");
      response.status(405).json({error: "method_not_allowed"});
      return;
    }
    try {
      const userId = await verifyClerkRequest(request);
      await consumeRateLimit(userId, "payment");
      const body = bodyObject(request);
      const tranId = typeof body.tranId === "string"
        ? body.tranId.replace(/[^A-Za-z0-9_]/g, "").slice(0, 80)
        : "";
      if (!tranId) throw new ApiError(400, "missing_tran_id");

      const db = firestore();
      const snapshot = await db.doc(`orders/${tranId}`).get();
      if (!snapshot.exists) throw new ApiError(404, "unknown_transaction");
      // An order id is unguessable, but ownership is still checked: a leaked id
      // must not let anyone else drive someone's subscription.
      if ((snapshot.data()?.userId ?? "") !== userId) throw new ApiError(403, "not_your_transaction");

      const status = snapshot.data()?.status ?? "";
      if (status !== "PENDING") {
        response.status(200).json({outcome: "already_settled", status});
        return;
      }

      const validation = await queryByTransactionId(sslcommerzConfig(), tranId);
      if (!validation) {
        // Not an error: the gateway may not have recorded it yet.
        response.status(200).json({outcome: "not_found_yet", status: "PENDING"});
        return;
      }
      const outcome = await applyValidation(tranId, validation, "", "CONFIRM");
      response.status(200).json({outcome, gatewayStatus: validation.status});
    } catch (error) {
      sendError(response, error, "Payment confirmation failed");
    }
    return;
  }

  privateJson(response);

  if (request.method === "GET") {
    response.status(200).json({
      service: "portico-payment",
      status: "ready",
      billingMode: process.env.PORTICO_BILLING_MODE ?? "sandbox",
      configured: Boolean(process.env.SSLCZ_STORE_ID && process.env.SSLCZ_STORE_PASSWD),
      modes: ["init", "ipn", "return", "confirm"],
    });
    return;
  }
  if (request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "payment");
    response.status(200).json(await startCheckout(userId, bodyObject(request)));
  } catch (error) {
    sendError(response, error, "Payment initiation failed");
  }
}
