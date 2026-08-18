import type {VercelRequest, VercelResponse} from "@vercel/node";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {registerDevice, unregisterDevice} from "../lib/notifications.js";

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);
  if (request.method === "GET") {
    response.status(200).json({service: "portico-notifications", status: "ready"});
    return;
  }
  if (request.method !== "POST" && request.method !== "DELETE") {
    response.setHeader("Allow", "GET, POST, DELETE");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    const body = bodyObject(request);
    const installationId = typeof body.installationId === "string" ? body.installationId.trim() : "";
    if (installationId.length < 10 || installationId.length > 256) throw new ApiError(400, "invalid_installation_id");

    if (request.method === "DELETE") {
      await unregisterDevice(userId, installationId);
    } else {
      const platform = typeof body.platform === "string" ? body.platform.slice(0, 32) : "android";
      const appVersion = typeof body.appVersion === "string" ? body.appVersion.slice(0, 32) : "unknown";
      await registerDevice(userId, installationId, platform, appVersion);
    }
    response.status(200).json({registered: request.method === "POST"});
  } catch (error) {
    sendError(response, error, "Notification registration failed");
  }
}
