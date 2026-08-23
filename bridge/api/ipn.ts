import type {VercelRequest, VercelResponse} from "@vercel/node";
import payment from "./payment.js";

/*
 * A query-free address for the IPN callback.
 *
 * /api/payment?mode=ipn works, but this URL is typed into a dashboard field on
 * SSLCommerz's side, and a URL input that silently drops or escapes everything
 * after the "?" would break settlement in a way that looks like our bug. The
 * settlement logic stays in payment.ts; this only fixes the mode and delegates.
 *
 * A vercel.json rewrite would have avoided the extra function, but rewrites do
 * not apply to paths already claimed by the functions config, so the rewrite
 * returned 404. This deployment now uses 12 of the 12 functions the Hobby plan
 * allows, so anything new has to merge into an existing handler.
 */
export default async function handler(request: VercelRequest, response: VercelResponse) {
  request.query = {...request.query, mode: "ipn"};
  return payment(request, response);
}
