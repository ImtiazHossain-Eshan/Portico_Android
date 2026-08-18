import type {VercelRequest, VercelResponse} from "@vercel/node";
import {ClerkAuthorizationError} from "./clerk-auth.js";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message = code,
  ) {
    super(message);
  }
}

export function privateJson(response: VercelResponse) {
  response.setHeader("Cache-Control", "private, no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
}

export function bodyObject(request: VercelRequest): Record<string, unknown> {
  const length = Number(request.headers["content-length"] ?? 0);
  if (length > 1_000_000) throw new ApiError(413, "request_too_large");

  const value = typeof request.body === "string"
    ? JSON.parse(request.body) as unknown
    : request.body;
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new ApiError(400, "invalid_request");
  }
  return value as Record<string, unknown>;
}

export function sendError(response: VercelResponse, error: unknown, label: string) {
  if (error instanceof ApiError) {
    response.status(error.status).json({error: error.code, message: error.message});
    return;
  }
  if (error instanceof ClerkAuthorizationError) {
    response.status(401).json({error: error.code});
    return;
  }
  const message = error instanceof Error ? error.message : "unknown_error";
  console.error(label, {message});
  const unavailable = message.startsWith("Missing server environment");
  response.status(unavailable ? 503 : 500).json({
    error: unavailable ? "server_not_configured" : "server_error",
  });
}
