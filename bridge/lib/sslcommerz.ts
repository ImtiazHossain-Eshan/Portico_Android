/*
 * SSLCommerz gateway client.
 *
 * The flow is a hosted redirect, not a card API: we open a session, the member
 * pays on SSLCommerz's own page, and the result arrives twice, once as a
 * server-to-server IPN and once as a browser redirect. Only the IPN is trusted,
 * and only after calling the validation API back, because the IPN body itself
 * is an unauthenticated POST from the public internet.
 *
 * Store credentials never leave the server.
 */

const SANDBOX_GATEWAY = "https://sandbox-gw.sslcommerz.com/gwprocess/v4/api.php";
const SANDBOX_VALIDATOR = "https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php";
const SANDBOX_QUERY = "https://sandbox.sslcommerz.com/validator/api/merchantTransIDvalidationAPI.php";
const LIVE_GATEWAY = "https://securepay.sslcommerz.com/gwprocess/v4/api.php";
const LIVE_VALIDATOR = "https://securepay.sslcommerz.com/validator/api/validationserverAPI.php";
const LIVE_QUERY = "https://securepay.sslcommerz.com/validator/api/merchantTransIDvalidationAPI.php";

/** SSLCommerz rejects anything outside this band. */
export const MIN_BDT = 10;
export const MAX_BDT = 500_000;

export interface SslcommerzConfig {
  storeId: string;
  storePassword: string;
  gatewayUrl: string;
  validatorUrl: string;
  queryUrl: string;
  sandbox: boolean;
}

export function sslcommerzConfig(): SslcommerzConfig {
  const storeId = process.env.SSLCZ_STORE_ID?.trim();
  const storePassword = process.env.SSLCZ_STORE_PASSWD?.trim();
  if (!storeId || !storePassword) {
    throw new Error("Missing server environment: SSLCZ_STORE_ID and SSLCZ_STORE_PASSWD");
  }
  // Anything other than an explicit "false" stays on sandbox. Going live should
  // be a deliberate act, never a default that a missing variable turns on.
  const sandbox = (process.env.SSLCZ_SANDBOX ?? "true").toLowerCase() !== "false";
  return {
    storeId,
    storePassword,
    gatewayUrl: sandbox ? SANDBOX_GATEWAY : LIVE_GATEWAY,
    validatorUrl: sandbox ? SANDBOX_VALIDATOR : LIVE_VALIDATOR,
    queryUrl: sandbox ? SANDBOX_QUERY : LIVE_QUERY,
    sandbox,
  };
}

export interface SessionRequest {
  tranId: string;
  amountBdt: number;
  productName: string;
  successUrl: string;
  failUrl: string;
  cancelUrl: string;
  ipnUrl: string;
  customerName: string;
  customerEmail: string;
  /** Optional. Falls back to a valid-format placeholder. */
  customerPhone?: string;
}

export interface SessionResult {
  gatewayPageUrl: string;
  sessionKey: string;
}

/** SSLCommerz wants form-encoded input and answers with JSON. */
export async function createSession(
  config: SslcommerzConfig,
  request: SessionRequest,
): Promise<SessionResult> {
  if (!Number.isFinite(request.amountBdt) || request.amountBdt < MIN_BDT || request.amountBdt > MAX_BDT) {
    throw new Error(`amount_out_of_range: ${request.amountBdt}`);
  }

  const form = new URLSearchParams({
    store_id: config.storeId,
    store_passwd: config.storePassword,
    total_amount: request.amountBdt.toFixed(2),
    currency: "BDT",
    tran_id: request.tranId,
    success_url: request.successUrl,
    fail_url: request.failUrl,
    cancel_url: request.cancelUrl,
    ipn_url: request.ipnUrl,
    product_name: request.productName,
    product_category: "subscription",
    product_profile: "non-physical-goods",
    shipping_method: "NO",
    cus_name: request.customerName,
    cus_email: request.customerEmail,
    /*
     * SSLCommerz requires these fields, and Portico does not collect any of
     * them, so they are placeholders rather than member data invented from
     * something else.
     *
     * The phone number is the one that has to be plausible. It is not decorative:
     * the gateway sends the one-time password to it and validates the code
     * against it, so an obviously invalid number leaves the OTP step
     * unpassable. Bangladeshi mobile numbers are eleven digits beginning 01.
     */
    cus_add1: "Dhaka",
    cus_city: "Dhaka",
    cus_postcode: "1207",
    cus_country: "Bangladesh",
    cus_phone: request.customerPhone?.trim() || "01700000000",
  });

  const response = await fetch(config.gatewayUrl, {
    method: "POST",
    headers: {"Content-Type": "application/x-www-form-urlencoded"},
    body: form.toString(),
  });
  if (!response.ok) throw new Error(`gateway_http_${response.status}`);

  const payload = await response.json() as Record<string, unknown>;
  const status = typeof payload.status === "string" ? payload.status : "";
  const gatewayPageUrl = typeof payload.GatewayPageURL === "string" ? payload.GatewayPageURL : "";
  if (status !== "SUCCESS" || !gatewayPageUrl) {
    const reason = typeof payload.failedreason === "string" ? payload.failedreason : status || "unknown";
    throw new Error(`gateway_session_failed: ${reason}`);
  }
  return {
    gatewayPageUrl,
    sessionKey: typeof payload.sessionkey === "string" ? payload.sessionkey : "",
  };
}

export interface ValidationResult {
  status: string;
  tranId: string;
  /** Present on query results; the IPN supplies its own. */
  valId?: string;
  amountBdt: number;
  currency: string;
  cardType: string;
  cardLast4: string;
  riskLevel: string;
}

/**
 * The only statement about a payment we are willing to act on. Called with the
 * val_id from an IPN, it asks SSLCommerz directly what happened.
 */
export async function validatePayment(
  config: SslcommerzConfig,
  valId: string,
): Promise<ValidationResult> {
  const query = new URLSearchParams({
    val_id: valId,
    store_id: config.storeId,
    store_passwd: config.storePassword,
    format: "json",
  });
  const response = await fetch(`${config.validatorUrl}?${query.toString()}`);
  if (!response.ok) throw new Error(`validator_http_${response.status}`);

  const payload = await response.json() as Record<string, unknown>;
  const text = (key: string) => (typeof payload[key] === "string" ? payload[key] as string : "");
  const cardNumber = text("card_no");
  return {
    status: text("status"),
    tranId: text("tran_id"),
    amountBdt: Number(payload.amount ?? 0),
    currency: text("currency"),
    cardType: text("card_type"),
    cardLast4: cardNumber.replace(/\D/g, "").slice(-4),
    riskLevel: text("risk_level"),
  };
}

/** SSLCommerz reports a settled payment as either VALID or VALIDATED. */
export function isSettled(status: string): boolean {
  return status === "VALID" || status === "VALIDATED";
}

/**
 * Whether a validated payment may be credited.
 *
 * Comparing the amount is the step their documentation calls out: the IPN says
 * what was paid, our record says what was owed, and only a match means the
 * member actually paid for the thing they are about to receive. Currency is
 * checked too, since a gateway misconfiguration would otherwise let 1200 INR
 * settle an order for 1200 BDT.
 */
export function matchesOrder(
  validation: ValidationResult,
  order: {tranId: string; amountBdt: number; currency: string},
): boolean {
  if (validation.tranId !== order.tranId) return false;
  if (validation.currency !== order.currency) return false;
  // Money compared in whole poisha, never as floats.
  return Math.round(validation.amountBdt * 100) === Math.round(order.amountBdt * 100);
}

/**
 * Ask SSLCommerz what happened to a transaction, by our own transaction id.
 *
 * The IPN is a push and can be late, dropped, or in the sandbox's case simply
 * never sent. Waiting on it means a member who paid successfully sits looking
 * at a spinner. This is the pull equivalent: same question, same authority,
 * asked when the member comes back rather than when the gateway gets round to
 * telling us.
 *
 * Security is unchanged. This still asks SSLCommerz directly with the store
 * credentials and still returns their answer, never the client's.
 */
export async function queryByTransactionId(
  config: SslcommerzConfig,
  tranId: string,
): Promise<ValidationResult | null> {
  const query = new URLSearchParams({
    tran_id: tranId,
    store_id: config.storeId,
    store_passwd: config.storePassword,
    format: "json",
  });
  const response = await fetch(`${config.queryUrl}?${query.toString()}`);
  if (!response.ok) throw new Error(`query_http_${response.status}`);

  const payload = await response.json() as Record<string, unknown>;
  const elements = Array.isArray(payload.element) ? payload.element : [];
  if (elements.length === 0) return null;

  // More than one row means the member retried. The settled one is the answer;
  // an earlier failure must not override a later success.
  const rows = elements as Record<string, unknown>[];
  const chosen = rows.find((row) => isSettled(String(row.status ?? ""))) ?? rows[0];
  const text = (key: string) => (typeof chosen[key] === "string" ? chosen[key] as string : "");
  const cardNumber = text("card_no");
  return {
    status: text("status"),
    tranId: text("tran_id"),
    valId: text("val_id"),
    amountBdt: Number(chosen.amount ?? 0),
    currency: text("currency"),
    cardType: text("card_type"),
    cardLast4: cardNumber.replace(/\D/g, "").slice(-4),
    riskLevel: text("risk_level"),
  };
}

/** The val_id from a query row, needed to record what settled the order. */
export function valueIdOf(validation: ValidationResult & {valId?: string}): string {
  return validation.valId ?? "";
}
