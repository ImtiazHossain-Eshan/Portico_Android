import {firestore} from "./firebase-admin.js";
import {ApiError} from "./http.js";

/*
 * Per-user rate limiting.
 *
 * Serverless functions have no shared memory, so an in-process counter would
 * reset on every cold start and differ between concurrent instances. The limit
 * therefore lives in Firestore, in one document per member, and is applied
 * inside a transaction so two simultaneous requests cannot both pass a limit
 * with one slot left.
 *
 * This runs after Clerk verification, so it throttles an authenticated account
 * rather than raw traffic. Unauthenticated floods never reach here: they fail
 * JWT verification first, and Vercel's own platform protections sit in front.
 *
 * The cost is one transaction per request, which is the price of a limit that
 * is actually enforced rather than one that looks enforced.
 */

export interface RateLimitRule {
  /** Requests allowed inside one window. */
  readonly limit: number;
  /** Window length in milliseconds. */
  readonly windowMillis: number;
}

export const RATE_LIMITS = {
  "firebase-token": {limit: 60, windowMillis: 60_000},
  files: {limit: 40, windowMillis: 60_000},
  properties: {limit: 60, windowMillis: 60_000},
  subscription: {limit: 20, windowMillis: 60_000},
  notifications: {limit: 30, windowMillis: 60_000},
  workspace: {limit: 10, windowMillis: 60_000},
  account: {limit: 5, windowMillis: 300_000},
  assistant: {limit: 20, windowMillis: 60_000},
  market: {limit: 60, windowMillis: 60_000},
  organizations: {limit: 40, windowMillis: 60_000},
  // Opening a gateway session writes an order and calls out to SSLCommerz, so
  // it is deliberately tighter than ordinary reads.
  // One checkout now makes several settle attempts, because the gateway is
  // not immediately ready to answer for a transaction it just redirected from.
  payment: {limit: 40, windowMillis: 300_000},
} as const satisfies Record<string, RateLimitRule>;

export type RateLimitRoute = keyof typeof RATE_LIMITS;

interface WindowState {
  count: number;
  windowStart: number;
}

/**
 * Consumes one slot for [route] on behalf of [userId].
 *
 * Throws a 429 ApiError carrying retry-after seconds when the window is spent.
 * A Firestore failure is deliberately allowed through rather than locking the
 * member out of their own portfolio: the limiter protects the backend from
 * abuse, and failing closed here would turn a datastore blip into an outage.
 */
export async function consumeRateLimit(
  userId: string,
  route: RateLimitRoute,
): Promise<void> {
  const rule = RATE_LIMITS[route];
  const reference = firestore().doc(`users/${userId}/settings/rateLimit`);
  const now = Date.now();

  let retryAfterMillis = 0;
  try {
    await firestore().runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      const routes = (snapshot.exists ? snapshot.get("routes") : undefined) as
        | Record<string, WindowState>
        | undefined;
      const current = routes?.[route];

      const expired = !current || now - current.windowStart >= rule.windowMillis;
      const state: WindowState = expired
        ? {count: 1, windowStart: now}
        : {count: current.count + 1, windowStart: current.windowStart};

      if (state.count > rule.limit) {
        retryAfterMillis = state.windowStart + rule.windowMillis - now;
        return;
      }

      transaction.set(
        reference,
        {routes: {...(routes ?? {}), [route]: state}},
        {merge: true},
      );
    });
  } catch (error) {
    console.error("Rate limit check failed, allowing request", {
      route,
      message: error instanceof Error ? error.message : "unknown_error",
    });
    return;
  }

  if (retryAfterMillis > 0) {
    throw new ApiError(
      429,
      "rate_limited",
      `Too many ${route} requests. Retry in ${Math.ceil(retryAfterMillis / 1000)}s.`,
    );
  }
}
