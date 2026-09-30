/**
 * Fijerena live sync server — see docs/plans/20260929_live-sync-plan.md → Server.
 *
 * The worker only routes: every account is one Durable Object ([Account]) holding that account's
 * records, devices and WebSockets. It never sees anything readable — keys are HMACs and payloads
 * ciphertext, both made on the devices.
 */
import { Account } from "./account";
import { PROTOCOL_VERSION, error, json, randomId, safeEqual, splitCredential } from "./util";

export { Account };

export interface Env {
  ACCOUNT: DurableObjectNamespace<Account>;
  /** If set, creating an account requires it in the `X-Setup-Secret` header. */
  SETUP_SECRET?: string;
  /** Overrides the 90-day tombstone retention (tests). */
  TOMBSTONE_RETENTION_MS?: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const route = `${request.method} ${url.pathname}`;

    if (route === "GET /info") {
      return json({ service: "fijerena-sync", protocol: PROTOCOL_VERSION, setupSecretRequired: Boolean(env.SETUP_SECRET) });
    }

    if (route === "POST /accounts") {
      if (env.SETUP_SECRET && !safeEqual(request.headers.get("x-setup-secret") ?? "", env.SETUP_SECRET)) {
        return error(401, "setup secret required");
      }
      const accountId = randomId();
      return forward(env, accountId, request, "/internal/create", { "x-account-id": accountId });
    }

    if (route === "POST /pair") {
      // The code is in the body; peek at it for routing, then pass the body on untouched.
      const body = await request.clone().json().catch(() => null) as { code?: string } | null;
      const code = splitCredential(body?.code);
      if (!code) return error(400, "invalid pairing code");
      return forward(env, code.accountId, request, "/internal/pair");
    }

    // Only this worker may reach the Durable Object's internal routes.
    if (url.pathname.startsWith("/internal/")) return error(404, "not found");

    // Everything else acts on one account, as one of its devices.
    const token = splitCredential(request.headers.get("authorization")?.replace(/^Bearer\s+/i, ""));
    if (!token) return error(401, "missing or malformed device token");
    return forward(env, token.accountId, request, url.pathname);
  },
} satisfies ExportedHandler<Env>;

function forward(env: Env, accountId: string, request: Request, path: string, headers: Record<string, string> = {}): Promise<Response> {
  const stub = env.ACCOUNT.get(env.ACCOUNT.idFromName(accountId));
  const url = new URL(request.url);
  url.pathname = path;
  const forwarded = new Request(url.toString(), request);
  forwarded.headers.delete("x-account-id");
  for (const [name, value] of Object.entries(headers)) forwarded.headers.set(name, value);
  forwarded.headers.set("x-retention-ms", env.TOMBSTONE_RETENTION_MS ?? "");
  return stub.fetch(forwarded);
}
