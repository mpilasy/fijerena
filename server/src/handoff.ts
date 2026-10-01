import { DurableObject } from "cloudflare:workers";
import type { Env } from "./index";
import { error, json } from "./util";

const HANDOFF_TTL_MS = 10 * 60 * 1000;
const MAX_SEALED = 8 * 1024;

/**
 * One handoff: how a device without a camera (a TV) joins an account. The joining device opens it
 * and shows a QR code with the handoff id and a one-time public key; a device already in the
 * account scans it and fills the handoff with a pairing code and the account key, encrypted to that
 * public key; the joining device collects it, once. The server only ever holds public keys and
 * ciphertext. See docs/plans/20260929_live-sync-plan.md → Security (pairing).
 *
 * Stored in memory and storage of its own object, keyed by the handoff id (128 random bits, the
 * only secret needed to collect it — and what it holds is unreadable without the private key).
 */
export class Handoff extends DurableObject<Env> {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const now = Date.now();
    const state = await this.ctx.storage.get<{ expiresAt: number; sealed?: string; senderKey?: string }>("handoff");
    if (state && state.expiresAt < now) {
      await this.ctx.storage.deleteAll();
      return error(410, "handoff expired");
    }

    switch (`${request.method} ${url.pathname}`) {
      case "POST /internal/open": {
        if (state) return error(409, "handoff exists");
        const expiresAt = now + HANDOFF_TTL_MS;
        await this.ctx.storage.put("handoff", { expiresAt });
        return json({ expiresAt }, 201);
      }
      case "POST /internal/fill": {
        if (!state) return error(404, "no such handoff");
        if (state.sealed) return error(409, "handoff already filled");
        const body = (await request.json().catch(() => null)) as { sealed?: unknown; senderKey?: unknown } | null;
        if (typeof body?.sealed !== "string" || body.sealed.length > MAX_SEALED || typeof body.senderKey !== "string" || body.senderKey.length > 1024) {
          return error(400, "sealed and senderKey must be strings");
        }
        await this.ctx.storage.put("handoff", { ...state, sealed: body.sealed, senderKey: body.senderKey });
        return json({ filled: true });
      }
      case "GET /internal/collect": {
        if (!state) return error(404, "no such handoff");
        if (!state.sealed) return json({ ready: false, expiresAt: state.expiresAt });
        // Collected once: whoever reads it first has it, and nobody can afterwards.
        await this.ctx.storage.deleteAll();
        return json({ ready: true, sealed: state.sealed, senderKey: state.senderKey });
      }
    }
    return error(404, "not found");
  }
}
