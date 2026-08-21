import type {VercelRequest, VercelResponse} from "@vercel/node";
import {verifyClerkRequest} from "../lib/clerk-auth.js";
import {ApiError, bodyObject, privateJson, sendError} from "../lib/http.js";
import {consumeRateLimit} from "../lib/rate-limit.js";

/*
 * Gemma-backed portfolio assistant.
 *
 * Two things make this safe to run at all.
 *
 * First, the API key lives here and only here. The blueprint's security section
 * is explicit that model keys must never reach the mobile client, and a key
 * inside an APK is a published key no matter how it is obfuscated.
 *
 * Second, the client sends *computed figures*, not records. The model receives
 * "three properties, 4.1% net yield, 28% of gross going to tax" -- never an
 * address, a tenant, a document, or a purchase price tied to a name. That is
 * enough to answer questions about performance and nowhere near enough to
 * reconstruct someone's holdings.
 *
 * The device keeps its own analyst and uses it whenever this endpoint is off,
 * unreachable, or declined, so the feature degrades to the previous behaviour
 * rather than to an error.
 */

const DEFAULT_MODEL = "gemma-3-27b-it";
const ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";

/** Hard ceiling on what one question may carry, before the model ever sees it. */
const MAX_QUESTION_CHARS = 500;
const MAX_CONTEXT_CHARS = 4_000;

const SYSTEM_BRIEF = [
  "You are Portico's real-estate investment analyst.",
  "Answer only from the PORTFOLIO FACTS supplied with the question.",
  "Never invent a figure. If the facts do not contain what is needed, say so plainly and name what is missing.",
  "Show the arithmetic when you use it, in the form 'gross 12,000 less costs 3,400 = net 8,600'.",
  "Be direct and short: at most 150 words, no preamble, no bullet lists unless comparing properties.",
  "Currency amounts keep the currency given in the facts. Never convert.",
  "You are not a licensed financial or tax adviser; do not present guidance as advice.",
].join(" ");

function text(value: unknown, label: string, limit: number): string {
  if (typeof value !== "string") throw new ApiError(400, `invalid_${label}`);
  const trimmed = value.trim();
  if (!trimmed) throw new ApiError(400, `missing_${label}`);
  if (trimmed.length > limit) throw new ApiError(413, `${label}_too_long`);
  return trimmed;
}

export default async function handler(request: VercelRequest, response: VercelResponse) {
  privateJson(response);

  if (request.method === "GET") {
    response.status(200).json({
      service: "portico-assistant",
      status: process.env.GEMMA_API_KEY?.trim() ? "ready" : "not_configured",
      model: process.env.GEMMA_MODEL?.trim() || DEFAULT_MODEL,
    });
    return;
  }
  if (request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    response.status(405).json({error: "method_not_allowed"});
    return;
  }

  try {
    const userId = await verifyClerkRequest(request);
    await consumeRateLimit(userId, "assistant");

    const apiKey = process.env.GEMMA_API_KEY?.trim();
    if (!apiKey) {
      // Not an error: the device analyst covers this case. The client reads
      // the code and falls back without showing a failure.
      throw new ApiError(503, "assistant_not_configured", "Cloud analysis is not enabled.");
    }

    const body = bodyObject(request);
    const question = text(body.question, "question", MAX_QUESTION_CHARS);
    const facts = text(body.context, "context", MAX_CONTEXT_CHARS);
    const model = process.env.GEMMA_MODEL?.trim() || DEFAULT_MODEL;

    // Gemma on the Generative Language API takes no separate system role, so
    // the brief is prepended to the single user turn.
    const prompt = `${SYSTEM_BRIEF}\n\nPORTFOLIO FACTS\n${facts}\n\nQUESTION\n${question}`;

    const upstream = await fetch(`${ENDPOINT}/${encodeURIComponent(model)}:generateContent`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{role: "user", parts: [{text: prompt}]}],
        generationConfig: {temperature: 0.2, maxOutputTokens: 400, topP: 0.9},
      }),
    });

    if (!upstream.ok) {
      const detail = await upstream.text().catch(() => "");
      console.error("Gemma call failed", {status: upstream.status, detail: detail.slice(0, 300)});
      throw new ApiError(
        upstream.status === 429 ? 429 : 502,
        upstream.status === 429 ? "model_rate_limited" : "model_unavailable",
        "The analyst could not be reached.",
      );
    }

    const payload = await upstream.json() as {
      candidates?: {content?: {parts?: {text?: string}[]}}[];
    };
    const answer = payload.candidates?.[0]?.content?.parts
      ?.map((part) => part.text ?? "")
      .join("")
      .trim();

    if (!answer) throw new ApiError(502, "empty_model_response", "The analyst returned nothing.");

    response.status(200).json({answer, model});
  } catch (error) {
    sendError(response, error, "Assistant request failed");
  }
}
