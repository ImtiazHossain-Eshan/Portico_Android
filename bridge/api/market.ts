import type {VercelRequest, VercelResponse} from "@vercel/node";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {firestore} from "../lib/firebase-admin.js";
import {privateJson, sendError} from "../lib/http.js";
import {consumeRateLimit} from "../lib/rate-limit.js";

/*
 * External property data (blueprint section 41).
 *
 * The blueprint models this domain as DataProvider -> PropertyListing /
 * ComparableProperty / MarketData, and is explicit that the architecture must
 * accommodate real portals later. What that means in practice is that the app
 * must not hold the figures.
 *
 * So it does not. Comparables, listings and market signals are served from
 * Firestore's `public` tree, which the rules make readable to any signed-in
 * member and writable by nobody. The device keeps its bundled set purely as a
 * fallback for the first run and for offline.
 *
 * Every response names its provider and says whether the figures were observed
 * or modelled. Today the seeded provider reports `modelled`, because inventing
 * `observed` would be the dishonest half of this feature. Pointing the same
 * endpoint at a real portal is a change of one collection, not a change of
 * shape: the client already renders whatever the provider claims.
 */

const CACHE_SECONDS = 900;

interface Provider {
  id: string;
  name: string;
  type: string;
  basis: "observed" | "modelled";
  updated: string;
}

const FALLBACK_PROVIDER: Provider = {
  id: "portico-reference",
  name: "Portico reference set",
  type: "internal",
  basis: "modelled",
  updated: "",
};

async function collection(path: string, limit: number): Promise<Record<string, unknown>[]> {
  const snapshot = await firestore().collection(path).limit(limit).get();
  return snapshot.docs.map((document) => ({id: document.id, ...document.data()}));
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);

  if (request.method !== "GET") {
    response.setHeader("Allow", "GET");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "market");

    const [providerSnapshot, comparables, listings, signals] = await Promise.all([
      firestore().doc("public/market").get(),
      collection("public/market/comparables", 200),
      collection("public/market/listings", 200),
      collection("public/market/signals", 100),
    ]);

    const provider: Provider = providerSnapshot.exists
      ? {...FALLBACK_PROVIDER, ...(providerSnapshot.data() as Partial<Provider>)}
      : FALLBACK_PROVIDER;

    // An empty tree is not an error. It means nobody has seeded the reference
    // set yet, and the client is expected to keep using its bundled copy.
    const empty = comparables.length === 0 && listings.length === 0 && signals.length === 0;

    response.setHeader("Cache-Control", `private, max-age=${CACHE_SECONDS}`);
    response.status(200).json({
      provider,
      populated: !empty,
      comparables,
      listings,
      signals,
    });
  } catch (error) {
    sendError(response, error, "Market data request failed");
  }
}
