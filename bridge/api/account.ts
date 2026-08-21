import type {VercelRequest, VercelResponse} from "@vercel/node";
import {getAuth} from "firebase-admin/auth";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
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

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);

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
