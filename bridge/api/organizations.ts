import type {VercelRequest, VercelResponse} from "@vercel/node";
import {FieldValue} from "firebase-admin/firestore";
import {randomUUID} from "node:crypto";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {firestore} from "../lib/firebase-admin.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {consumeRateLimit} from "../lib/rate-limit.js";
import {safeId} from "../lib/validation.js";

/*
 * Organizations, members, roles and permissions (blueprint sections 26 and 39).
 *
 * Role-based access control only means anything if the server enforces it. A
 * client that decides its own role is a client that can promote itself, so
 * every mutation here re-reads the caller's membership from Firestore and
 * checks the rank before doing anything.
 *
 * The rank ladder mirrors OrgRole in the Android app:
 *   OWNER 4 > ADMIN 3 > ANALYST 2 > VIEWER 1
 *
 * Two rules hold throughout. Nobody may grant a role at or above their own,
 * which stops an admin minting a second owner. And an organization always has
 * exactly one owner, so the last one cannot be removed or demoted by accident.
 */

const RANK: Record<string, number> = {OWNER: 4, ADMIN: 3, ANALYST: 2, VIEWER: 1};

interface Membership {
  organizationId: string;
  userId: string;
  role: string;
  name: string;
  email: string;
  joinedAt: string;
}

function rankOf(role: string): number {
  return RANK[role] ?? 0;
}

function validRole(value: unknown): string {
  const role = String(value ?? "").toUpperCase();
  if (!(role in RANK)) throw new ApiError(400, "invalid_role");
  return role;
}

function displayName(value: unknown, fallback: string): string {
  const name = String(value ?? "").trim();
  if (name.length > 120) throw new ApiError(400, "name_too_long");
  return name || fallback;
}

/** The caller's membership, or a 403 if they are not in the organization. */
async function requireMembership(
  organizationId: string,
  userId: string,
): Promise<Membership> {
  const snapshot = await firestore()
    .doc(`organizations/${organizationId}/members/${userId}`)
    .get();
  if (!snapshot.exists) throw new ApiError(403, "not_a_member");
  return snapshot.data() as Membership;
}

function requireRank(membership: Membership, minimum: number) {
  if (rankOf(membership.role) < minimum) throw new ApiError(403, "insufficient_role");
}

/** Resolves an email to a Clerk user id, so an invitation reaches a real account. */
async function clerkUserByEmail(email: string): Promise<{id: string; name: string} | null> {
  const secret = process.env.CLERK_SECRET_KEY?.trim();
  if (!secret) throw new ApiError(503, "server_not_configured", "Invitations need a server key.");

  const url = `https://api.clerk.com/v1/users?email_address=${encodeURIComponent(email)}&limit=1`;
  const response = await fetch(url, {headers: {Authorization: `Bearer ${secret}`}});
  if (!response.ok) {
    console.error("Clerk lookup failed", {status: response.status});
    throw new ApiError(502, "directory_unavailable");
  }
  const users = await response.json() as {
    id: string;
    first_name?: string;
    last_name?: string;
  }[];
  const user = users[0];
  if (!user) return null;
  return {
    id: user.id,
    name: [user.first_name, user.last_name].filter(Boolean).join(" ").trim(),
  };
}

async function listMembers(organizationId: string): Promise<Membership[]> {
  const snapshot = await firestore()
    .collection(`organizations/${organizationId}/members`)
    .get();
  return snapshot.docs
    .map((document) => document.data() as Membership)
    .sort((a, b) => rankOf(b.role) - rankOf(a.role));
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "organizations");
    const db = firestore();

    // ------------------------------------------------------------------ read
    if (request.method === "GET") {
      const memberships = await db
        .collectionGroup("members")
        .where("userId", "==", userId)
        .get();

      const organizations = await Promise.all(
        memberships.docs.map(async (document) => {
          const membership = document.data() as Membership;
          const org = await db.doc(`organizations/${membership.organizationId}`).get();
          if (!org.exists) return null;
          return {
            ...org.data(),
            id: membership.organizationId,
            role: membership.role,
            members: await listMembers(membership.organizationId),
          };
        }),
      );

      response.status(200).json({organizations: organizations.filter(Boolean)});
      return;
    }

    if (request.method !== "POST") {
      response.setHeader("Allow", "GET, POST");
      response.status(405).json({error: "method_not_allowed"});
      return;
    }

    // ----------------------------------------------------------- mutations
    const body = bodyObject(request);
    const action = String(body.action ?? "");

    switch (action) {
      case "create": {
        const name = displayName(body.name, "");
        if (!name) throw new ApiError(400, "missing_name");

        const organizationId = randomUUID();
        const now = new Date().toISOString();
        const batch = db.batch();
        batch.set(db.doc(`organizations/${organizationId}`), {
          name,
          ownerId: userId,
          createdAt: now,
          createdAtServer: FieldValue.serverTimestamp(),
        });
        // The creator is the owner. An organization without one cannot be
        // administered, so this is written in the same batch, not afterwards.
        batch.set(db.doc(`organizations/${organizationId}/members/${userId}`), {
          organizationId,
          userId,
          role: "OWNER",
          name: displayName(body.memberName, "Owner"),
          email: String(body.memberEmail ?? ""),
          joinedAt: now,
        });
        await batch.commit();

        // The read path spreads the stored document, so it carries ownerId and
        // createdAt. This one is hand-built and was missing both, which left
        // the Created row blank on screen until something forced a refresh.
        response.status(201).json({
          id: organizationId,
          name,
          ownerId: userId,
          createdAt: now,
          role: "OWNER",
          members: await listMembers(organizationId),
        });
        return;
      }

      case "invite": {
        const organizationId = safeId(body.organizationId, "organizationId");
        const membership = await requireMembership(organizationId, userId);
        requireRank(membership, RANK.ADMIN);

        const role = validRole(body.role);
        // Nobody hands out a role at or above their own.
        if (rankOf(role) >= rankOf(membership.role)) {
          throw new ApiError(403, "role_exceeds_your_own");
        }

        const email = String(body.email ?? "").trim().toLowerCase();
        if (!email.includes("@")) throw new ApiError(400, "invalid_email");

        const invitee = await clerkUserByEmail(email);
        if (!invitee) throw new ApiError(404, "no_such_account", "Nobody signs in with that address.");

        const reference = db.doc(`organizations/${organizationId}/members/${invitee.id}`);
        if ((await reference.get()).exists) throw new ApiError(409, "already_a_member");

        await reference.set({
          organizationId,
          userId: invitee.id,
          role,
          name: invitee.name || email,
          email,
          joinedAt: new Date().toISOString(),
        });

        response.status(200).json({members: await listMembers(organizationId)});
        return;
      }

      case "role": {
        const organizationId = safeId(body.organizationId, "organizationId");
        const membership = await requireMembership(organizationId, userId);
        requireRank(membership, RANK.ADMIN);

        const targetId = safeId(body.userId, "userId");
        const role = validRole(body.role);
        const target = await requireMembership(organizationId, targetId);

        // You cannot promote past yourself, nor demote somebody senior.
        if (rankOf(role) >= rankOf(membership.role)) throw new ApiError(403, "role_exceeds_your_own");
        if (rankOf(target.role) >= rankOf(membership.role)) throw new ApiError(403, "target_outranks_you");
        if (target.role === "OWNER") throw new ApiError(403, "owner_role_is_fixed");

        await db.doc(`organizations/${organizationId}/members/${targetId}`).update({role});
        response.status(200).json({members: await listMembers(organizationId)});
        return;
      }

      case "remove": {
        const organizationId = safeId(body.organizationId, "organizationId");
        const membership = await requireMembership(organizationId, userId);
        const targetId = safeId(body.userId, "userId");

        // Leaving is always allowed; removing somebody else needs rank.
        const leaving = targetId === userId;
        if (!leaving) requireRank(membership, RANK.ADMIN);

        const target = await requireMembership(organizationId, targetId);
        if (target.role === "OWNER") {
          throw new ApiError(403, "owner_cannot_be_removed", "Transfer ownership first.");
        }
        if (!leaving && rankOf(target.role) >= rankOf(membership.role)) {
          throw new ApiError(403, "target_outranks_you");
        }

        await db.doc(`organizations/${organizationId}/members/${targetId}`).delete();
        response.status(200).json({
          members: leaving ? [] : await listMembers(organizationId),
          left: leaving,
        });
        return;
      }

      default:
        throw new ApiError(400, "unknown_action");
    }
  } catch (error) {
    sendError(response, error, "Organization request failed");
  }
}
