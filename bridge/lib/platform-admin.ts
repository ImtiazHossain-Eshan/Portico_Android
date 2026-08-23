import {firestore} from "./firebase-admin.js";
import {ApiError} from "./http.js";

/*
 * Platform administration.
 *
 * Every other endpoint in this bridge answers only for the caller's own records.
 * This one deliberately reads across every account, which makes the privilege
 * check the whole security story rather than a detail of it.
 *
 * Admin is granted by an environment variable and nothing else. It cannot be
 * set from inside the app, requested through an endpoint, or written to
 * Firestore by any client, because a privilege that can be granted by the thing
 * it governs is not a privilege. Changing who is an admin means changing a
 * deployment variable, which is exactly as difficult as it should be.
 */

export function adminUserIds(): string[] {
  return (process.env.PORTICO_ADMIN_USER_IDS ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

/** Throws unless the caller is on the allowlist. */
export function requirePlatformAdmin(userId: string): void {
  const allowed = adminUserIds();
  if (allowed.length === 0) {
    throw new ApiError(503, "admin_not_configured",
      "No platform administrators are configured for this deployment");
  }
  if (!allowed.includes(userId)) {
    // Deliberately the same shape as any other refusal. Confirming that an
    // admin surface exists is itself information.
    throw new ApiError(403, "not_a_platform_administrator");
  }
}

export interface PlatformOverview {
  users: number;
  properties: number;
  documents: number;
  payments: number;
  proSubscriptions: number;
  orders: {total: number; settled: number; pending: number; rejected: number};
  gatewayVolumeBdt: number;
}

/**
 * Counts across every tenant.
 *
 * Collection group queries are used rather than walking each member's
 * subcollections: the aggregate is one indexed count per collection instead of
 * one read per record, so the cost does not grow with the size of any single
 * portfolio.
 */
export async function platformOverview(
  proSubscriptions = 0,
  memberCount?: number,
): Promise<PlatformOverview> {
  const db = firestore();

  /*
   * Each count answers for itself. A collection group aggregate can fail on a
   * missing index, and one unavailable number is not a reason to show no
   * console at all: a zero beside working figures is legible, an error page is
   * not. The cause is logged rather than swallowed.
   */
  const countOf = async (label: string, run: () => Promise<number>): Promise<number> => {
    try {
      return await run();
    } catch (error) {
      console.error("Platform count unavailable", {
        label,
        message: error instanceof Error ? error.message : "unknown",
      });
      return 0;
    }
  };

  const [syncedWorkspaces, properties, documents, payments] = await Promise.all([
    countOf("users", async () => (await db.collection("users").count().get()).data().count),
    countOf("properties", async () => (await db.collectionGroup("properties").count().get()).data().count),
    countOf("documents", async () => (await db.collectionGroup("documents").count().get()).data().count),
    countOf("payments", async () => (await db.collectionGroup("payments").count().get()).data().count),
  ]);

  let settled = 0;
  let pending = 0;
  let rejected = 0;
  let volume = 0;
  let total = 0;
  try {
    const orders = await db.collection("orders").get();
    total = orders.size;
    orders.forEach((doc) => {
      const data = doc.data();
      const status = String(data.status ?? "");
      if (status === "VALID") {
        settled += 1;
        volume += Number(data.amountBdt ?? 0);
      } else if (status === "PENDING") {
        pending += 1;
      } else {
        rejected += 1;
      }
    });
  } catch (error) {
    console.error("Platform orders unavailable", {
      message: error instanceof Error ? error.message : "unknown",
    });
  }

  return {
    /*
     * Clerk owns identity, so it decides how many members exist. Firestore
     * counts workspaces that have synced at least once, which is a smaller
     * number for anyone who signed up and never opened the app: reporting it
     * as "total users" put a 3 on the overview beside a list of 4.
     */
    users: memberCount ?? syncedWorkspaces,
    properties,
    documents,
    payments,
    // Counted from the member list rather than a filtered collection group
    // query, which would need an index created by hand.
    proSubscriptions,
    orders: {total, settled, pending, rejected},
    gatewayVolumeBdt: volume,
  };
}

export interface PlatformUser {
  userId: string;
  name: string;
  email: string;
  planTier: string;
  properties: number;
  banned: boolean;
  lastSignInAt: string;
  createdAt: string;
}

function clerkSecret(): string {
  const secret = process.env.CLERK_SECRET_KEY?.trim();
  if (!secret) throw new Error("Missing server environment: CLERK_SECRET_KEY");
  return secret;
}

async function clerk(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`https://api.clerk.com/v1${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${clerkSecret()}`,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}

/**
 * The real member list.
 *
 * Clerk owns identity, so it is the source for who exists, what they are called
 * and whether they are locked out. Firestore owns the portfolio, so it is the
 * source for plan and property count. Neither system is asked a question the
 * other one answers.
 */
export async function platformUsers(limit = 50): Promise<PlatformUser[]> {
  const response = await clerk(`/users?limit=${Math.min(limit, 100)}&order_by=-created_at`);
  if (!response.ok) throw new ApiError(502, "clerk_unavailable", `Clerk returned ${response.status}`);

  const list = await response.json() as Array<Record<string, unknown>>;
  const db = firestore();

  return Promise.all(list.map(async (entry) => {
    const userId = String(entry.id ?? "");
    const emails = Array.isArray(entry.email_addresses) ? entry.email_addresses : [];
    const primary = emails[0] as Record<string, unknown> | undefined;
    const first = String(entry.first_name ?? "").trim();
    const last = String(entry.last_name ?? "").trim();

    const [subscription, properties] = await Promise.all([
      db.doc(`users/${userId}/settings/subscription`).get(),
      db.collection(`users/${userId}/properties`).count().get(),
    ]);

    return {
      userId,
      name: `${first} ${last}`.trim(),
      email: String(primary?.email_address ?? ""),
      planTier: String(subscription.data()?.planTier ?? "FREE"),
      properties: properties.data().count,
      banned: entry.banned === true,
      lastSignInAt: entry.last_sign_in_at ? new Date(Number(entry.last_sign_in_at)).toISOString().slice(0, 10) : "",
      createdAt: entry.created_at ? new Date(Number(entry.created_at)).toISOString().slice(0, 10) : "",
    };
  }));
}

/** Sets a member's plan tier. Server-owned, so it is written here or nowhere. */
export async function setPlatformPlan(userId: string, tier: "FREE" | "PRO"): Promise<void> {
  const db = firestore();
  const today = new Date().toISOString().slice(0, 10);
  await db.doc(`users/${userId}/settings/subscription`).set({
    planTier: tier,
    planId: tier === "PRO" ? "plan_pro_monthly" : "plan_free",
    status: "Active",
    startDate: today,
    renewsOn: tier === "PRO" ? null : null,
    cancelAtPeriodEnd: false,
    source: "platform_admin",
  }, {merge: true});
}

/**
 * Locks or unlocks sign-in through Clerk.
 *
 * Banning is reversible and does not touch the member's records, which is why
 * it is the strongest action offered here. Deleting somebody else's account is
 * deliberately absent: it is irreversible and there is no reading of "platform
 * administration" that requires it from a phone.
 */
export async function setPlatformBan(userId: string, banned: boolean): Promise<void> {
  const response = await clerk(`/users/${userId}/${banned ? "ban" : "unban"}`, {method: "POST"});
  if (!response.ok) {
    throw new ApiError(502, "clerk_action_failed", `Clerk returned ${response.status}`);
  }
}
