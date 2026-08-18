import type {VercelRequest} from "@vercel/node";
import {createRemoteJWKSet, jwtVerify} from "jose";

let cachedIssuer = "";
let cachedJwks: ReturnType<typeof createRemoteJWKSet> | null = null;

export function clerkIssuer(): string {
  const issuer = process.env.CLERK_ISSUER?.trim().replace(/\/$/, "");
  if (!issuer) throw new Error("Missing server environment: CLERK_ISSUER");
  return issuer;
}

function clerkJwks(issuer: string) {
  if (!cachedJwks || cachedIssuer !== issuer) {
    cachedIssuer = issuer;
    cachedJwks = createRemoteJWKSet(new URL(`${issuer}/.well-known/jwks.json`));
  }
  return cachedJwks;
}

function bearerToken(authorization: string | undefined): string {
  if (!authorization?.startsWith("Bearer ")) return "";
  return authorization.slice("Bearer ".length).trim();
}

export async function verifyClerkRequest(request: VercelRequest): Promise<string> {
  const token = bearerToken(request.headers.authorization);
  if (!token) throw new ClerkAuthorizationError("missing_session_token");

  try {
    const issuer = clerkIssuer();
    const {payload} = await jwtVerify(token, clerkJwks(issuer), {
      issuer,
      algorithms: ["RS256"],
    });
    const userId = payload.sub;
    if (!userId || userId.length > 128 || !/^[A-Za-z0-9_-]+$/.test(userId)) {
      throw new ClerkAuthorizationError("invalid_user_identity");
    }
    return userId;
  } catch (error) {
    if (error instanceof ClerkAuthorizationError) throw error;
    throw new ClerkAuthorizationError("invalid_session_token");
  }
}

export class ClerkAuthorizationError extends Error {
  constructor(public readonly code: string) {
    super(code);
  }
}
