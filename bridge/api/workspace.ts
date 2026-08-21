import type {VercelRequest, VercelResponse} from "@vercel/node";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {consumeRateLimit} from "../lib/rate-limit.js";
import {eraseMemberData} from "../lib/erase.js";
import {privateJson, sendError} from "../lib/http.js";

/**
 * Workspace reset: clears every portfolio record but keeps the account. The
 * account-deletion path lives in account.ts and shares the same eraser.
 */
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
    await consumeRateLimit(userId, "workspace");
    const result = await eraseMemberData(userId, true);
    response.status(200).json(result);
  } catch (error) {
    sendError(response, error, "Workspace erasure failed");
  }
}
