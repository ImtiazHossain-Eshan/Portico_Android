import type {VercelRequest, VercelResponse} from "@vercel/node";
import {getAuth} from "firebase-admin/auth";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {
  adminUserIds,
  platformOverview,
  platformUsers,
  requirePlatformAdmin,
  setPlatformBan,
  setPlatformPlan,
} from "../lib/platform-admin.js";
import {eraseMemberData} from "../lib/erase.js";
import {firebaseApp} from "../lib/firebase-admin.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {consumeRateLimit} from "../lib/rate-limit.js";

/*
 * Account deletion.
 *
 * Google Play requires an in-app route to delete an account for any app that
 * lets you create one, and the blueprint asks for it under Profile. Deleting
 * has to be complete or it is a lie: portfolio records, private files, the
 * Firebase identity, and the Clerk identity all go.
 *
 * Order matters. Data is erased first, because a member who loses their
 * identity but keeps their rows has orphaned records nobody can reach or
 * remove. Identity deletion is last and is the step allowed to fail loudly.
 */

const CONFIRMATION = "DELETE";

async function deleteClerkUser(userId: string): Promise<boolean> {
  const secret = process.env.CLERK_SECRET_KEY?.trim();
  if (!secret) {
    console.error("CLERK_SECRET_KEY missing: Clerk identity not deleted", {userId});
    return false;
  }

  const response = await fetch(`https://api.clerk.com/v1/users/${userId}`, {
    method: "DELETE",
    headers: {Authorization: `Bearer ${secret}`},
  });

  // A user Clerk has already forgotten is a success, not a failure.
  if (response.status === 404) return true;
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    console.error("Clerk deletion failed", {status: response.status, body: body.slice(0, 200)});
    return false;
  }
  return true;
}

async function deleteFirebaseUser(userId: string): Promise<boolean> {
  try {
    await getAuth(firebaseApp()).deleteUser(userId);
    return true;
  } catch (error) {
    const code = (error as {code?: string}).code;
    if (code === "auth/user-not-found") return true;
    console.error("Firebase identity deletion failed", {
      message: error instanceof Error ? error.message : "unknown_error",
    });
    return false;
  }
}

function firstValue(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

/*
 * Platform administration, dispatched on ?mode=admin.
 *
 * Reads across every tenant, so the allowlist check runs before anything else
 * and every action re-checks it. There is no admin session, no elevated token
 * and nothing cached: each request proves the caller is an administrator.
 */
async function handleAdmin(request: VercelRequest, response: VercelResponse) {
  if (request.method === "GET" && !request.headers.authorization) {
    // Non-secret health check. Reports whether administration is configured,
    // never who the administrators are.
    response.status(200).json({
      service: "portico-platform-admin",
      status: "ready",
      configured: adminUserIds().length > 0,
    });
    return;
  }

  const userId = await verifyClerkRequest(request);

  /*
   * Bootstrap. With no allowlist configured there is no administrator to ask,
   * so an authenticated caller is told their own id and nothing else. Telling
   * somebody who they already are leaks nothing, and it removes the one step
   * that otherwise sends an operator digging through a dashboard.
   */
  if (adminUserIds().length === 0) {
    response.status(200).json({
      service: "portico-platform-admin",
      configured: false,
      yourUserId: userId,
      hint: "Set PORTICO_ADMIN_USER_IDS to this value to enable platform administration",
    });
    return;
  }

  /*
   * A refusal answers 200 with the caller's own id rather than a bare 403.
   *
   * "Not an administrator" and "could not reach the server" are different
   * problems with the same symptom, and collapsing them sends an operator
   * debugging the wrong one. Returning the id also makes the mismatch visible:
   * the usual cause is an allowlist holding a different account's id.
   */
  if (!adminUserIds().includes(userId)) {
    response.status(200).json({
      service: "portico-platform-admin",
      configured: true,
      isAdministrator: false,
      yourUserId: userId,
      hint: "Add this id to PORTICO_ADMIN_USER_IDS to administer the platform",
    });
    return;
  }

  requirePlatformAdmin(userId);
  await consumeRateLimit(userId, "account");

  if (request.method === "GET") {
    const users = await platformUsers();
    const overview = await platformOverview(
      users.filter((user) => user.planTier === "PRO").length,
      users.length,
    );
    response.status(200).json({overview, users, actingAs: userId});
    return;
  }

  if (request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  const body = bodyObject(request);
  const action = typeof body.action === "string" ? body.action : "";
  const target = typeof body.userId === "string" ? body.userId.trim() : "";
  if (!target) throw new ApiError(400, "missing_user");

  // An administrator locking themselves out cannot undo it from inside the app,
  // so the one account they may not ban is their own.
  if (target === userId && (action === "ban" || action === "unban")) {
    throw new ApiError(409, "cannot_change_your_own_access");
  }

  switch (action) {
    case "setPlan": {
      const tier = body.tier === "PRO" ? "PRO" : "FREE";
      await setPlatformPlan(target, tier);
      break;
    }
    case "ban":
      await setPlatformBan(target, true);
      break;
    case "unban":
      await setPlatformBan(target, false);
      break;
    default:
      throw new ApiError(400, "invalid_admin_action");
  }

  const refreshed = await platformUsers();
  const overview = await platformOverview(
    refreshed.filter((user) => user.planTier === "PRO").length,
    refreshed.length,
  );
  response.status(200).json({overview, users: refreshed, actingAs: userId});
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);

  if (firstValue(request.query.mode) === "admin") {
    try {
      await handleAdmin(request, response);
    } catch (error) {
      sendError(response, error, "Platform administration failed");
    }
    return;
  }

  if (request.method === "GET") {
    response.status(200).json({service: "portico-account", status: "ready"});
    return;
  }
  if (request.method !== "DELETE") {
    response.setHeader("Allow", "GET, DELETE");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "account");

    // Typed confirmation, verified server-side. A client-only check is a
    // suggestion; this makes an accidental or replayed call impossible.
    const body = bodyObject(request);
    if (body.confirm !== CONFIRMATION) {
      throw new ApiError(
        400,
        "confirmation_required",
        `Send {"confirm":"${CONFIRMATION}"} to delete this account.`,
      );
    }

    const erased = await eraseMemberData(userId, false);
    const firebaseDeleted = await deleteFirebaseUser(userId);
    const clerkDeleted = await deleteClerkUser(userId);

    response.status(200).json({
      deletedRecords: erased.deletedRecords,
      deletedFiles: erased.deletedFiles,
      firebaseIdentityDeleted: firebaseDeleted,
      clerkIdentityDeleted: clerkDeleted,
      // The client signs out regardless: the portfolio is gone either way, and
      // a stale identity with no data is the recoverable half of the problem.
      complete: firebaseDeleted && clerkDeleted,
    });
  } catch (error) {
    sendError(response, error, "Account deletion failed");
  }
}
