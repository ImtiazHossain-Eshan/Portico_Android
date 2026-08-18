import type {VercelRequest, VercelResponse} from "@vercel/node";
import {del, get, put} from "@vercel/blob";
import {ClerkAuthorizationError, verifyClerkRequest} from "../lib/clerk-auth.js";

export const config = {api: {bodyParser: false}};

const MAX_FILE_BYTES = 4_000_000;
const ALLOWED_CONTENT_TYPES = new Set([
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

function singleHeader(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function safeSegment(value: string, fallback: string): string {
  const clean = value
    .normalize("NFKD")
    .replace(/[^A-Za-z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 100);
  return clean || fallback;
}

function decodedFileName(request: VercelRequest): string {
  const encoded = singleHeader(request.headers["x-portico-file-name"]);
  if (!encoded) return "document";
  try {
    return Buffer.from(encoded, "base64url").toString("utf8").slice(0, 160);
  } catch {
    return "document";
  }
}

function ownedPath(userId: string, value: unknown): string {
  const pathname = typeof value === "string" ? value : "";
  if (!pathname.startsWith(`users/${userId}/`) || pathname.includes("..")) {
    throw new ClerkAuthorizationError("file_not_owned");
  }
  return pathname;
}

async function readBody(request: VercelRequest, limit: number): Promise<Buffer> {
  const contentLength = Number(singleHeader(request.headers["content-length"]));
  if (Number.isFinite(contentLength) && contentLength > limit) throw new FileTooLargeError();

  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.length;
    if (total > limit) throw new FileTooLargeError();
    chunks.push(buffer);
  }
  const joined = Buffer.allocUnsafe(total);
  let offset = 0;
  for (const chunk of chunks) {
    joined.set(chunk, offset);
    offset += chunk.length;
  }
  return joined;
}

async function readJson(request: VercelRequest): Promise<Record<string, unknown>> {
  const body = await readBody(request, 16_384);
  try {
    return JSON.parse(body.toString("utf8")) as Record<string, unknown>;
  } catch {
    return {};
  }
}

class FileTooLargeError extends Error {}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  response.setHeader("Cache-Control", "private, no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");

  if (request.method === "GET" && !request.query.path) {
    response.status(200).json({
      service: "portico-private-files",
      status: process.env.BLOB_READ_WRITE_TOKEN || process.env.BLOB_STORE_ID ? "ready" : "not_configured",
      maxFileBytes: MAX_FILE_BYTES,
    });
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);

    if (request.method === "POST") {
      const contentType = singleHeader(request.headers["content-type"]).split(";")[0].trim().toLowerCase();
      if (!ALLOWED_CONTENT_TYPES.has(contentType)) {
        response.status(415).json({error: "unsupported_file_type"});
        return;
      }

      const body = await readBody(request, MAX_FILE_BYTES);
      if (body.length === 0) {
        response.status(400).json({error: "empty_file"});
        return;
      }

      const kind = safeSegment(singleHeader(request.headers["x-portico-file-kind"]), "documents");
      const resourceId = safeSegment(singleHeader(request.headers["x-portico-resource-id"]), "record");
      const originalName = decodedFileName(request);
      const fileName = safeSegment(originalName, "document");
      const blob = await put(`users/${userId}/${kind}/${resourceId}/${fileName}`, body, {
        access: "private",
        addRandomSuffix: true,
        contentType,
        cacheControlMaxAge: 60,
      });

      response.status(201).json({
        pathname: blob.pathname,
        fileName: originalName,
        contentType,
        size: body.length,
      });
      return;
    }

    if (request.method === "GET") {
      const pathname = ownedPath(userId, request.query.path);
      const result = await get(pathname, {access: "private", useCache: false});
      if (!result || result.statusCode !== 200) {
        response.status(404).json({error: "file_not_found"});
        return;
      }
      const bytes = Buffer.from(await new Response(result.stream).arrayBuffer());
      response.setHeader("Content-Type", result.blob.contentType || "application/octet-stream");
      response.setHeader("Content-Length", String(bytes.length));
      response.setHeader("Content-Disposition", result.blob.contentDisposition || "inline");
      response.status(200).end(bytes);
      return;
    }

    if (request.method === "DELETE") {
      const payload = await readJson(request);
      const pathname = ownedPath(userId, payload.path);
      await del(pathname);
      response.status(204).end();
      return;
    }

    response.setHeader("Allow", "GET, POST, DELETE");
    response.status(405).json({error: "method_not_allowed"});
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown_error";
    console.error("Private file operation failed", {message});
    if (error instanceof ClerkAuthorizationError) {
      response.status(error.code === "file_not_owned" ? 403 : 401).json({error: error.code});
    } else if (error instanceof FileTooLargeError) {
      response.status(413).json({error: "file_too_large", maxFileBytes: MAX_FILE_BYTES});
    } else if (message.startsWith("Missing server environment")) {
      response.status(503).json({error: "server_not_configured"});
    } else {
      response.status(500).json({error: "file_operation_failed"});
    }
  }
}
