/*
 * Shared billing facts.
 *
 * Prices are stated in taka rather than converted from the dollar figure at
 * request time: SSLCommerz converts non-BDT currencies at its own rate, which
 * would mean the amount charged never quite equals the amount displayed, and
 * amount matching on the IPN could not be an equality check.
 */

export const GATEWAY_PLANS = {
  plan_pro_monthly: {name: "Portico Pro, monthly", amountBdt: 1_200, interval: "month"},
  plan_pro_yearly: {name: "Portico Pro, annual", amountBdt: 12_000, interval: "year"},
} as const;

export type GatewayPlanId = keyof typeof GATEWAY_PLANS;

/**
 * Absolute origin of this deployment, needed because SSLCommerz calls us back
 * and cannot be handed a relative path. Vercel sets VERCEL_URL without a
 * scheme, so PORTICO_PUBLIC_URL wins when it is set.
 */
export function publicBaseUrl(): string {
  const explicit = process.env.PORTICO_PUBLIC_URL?.trim().replace(/\/$/, "");
  if (explicit) return explicit;
  const vercel = process.env.VERCEL_URL?.trim();
  if (vercel) return `https://${vercel.replace(/^https?:\/\//, "").replace(/\/$/, "")}`;
  throw new Error("Missing server environment: PORTICO_PUBLIC_URL");
}

/** Renewal date one interval out, matching the sandbox path's behaviour. */
export function renewalDate(interval: string): string {
  const date = new Date();
  if (interval === "year") date.setUTCFullYear(date.getUTCFullYear() + 1);
  else date.setUTCMonth(date.getUTCMonth() + 1);
  return date.toISOString().slice(0, 10);
}
