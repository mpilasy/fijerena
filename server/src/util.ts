/** Protocol version reported by GET /info; bump on any incompatible change. */
export const PROTOCOL_VERSION = 1;

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

export function error(status: number, message: string): Response {
  return json({ error: message }, status);
}

/** 32 random bytes, base64url — device tokens and pairing codes. */
export function randomSecret(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return base64url(bytes);
}

/** 16 random bytes, hex — account and device ids. */
export function randomId(): string {
  return [...crypto.getRandomValues(new Uint8Array(16))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function sha256(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Constant-time string comparison, for the setup secret. */
export function safeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  if (ea.length !== eb.length) return false;
  let diff = 0;
  for (let i = 0; i < ea.length; i++) diff |= ea[i] ^ eb[i];
  return diff === 0;
}

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Device tokens and pairing codes are `<accountId>.<secret>`: the worker routes on the account id
 * without any lookup, and only the account's own Durable Object — which stores the secret's hash —
 * can tell whether it is valid.
 */
export function splitCredential(credential: string | null | undefined): { accountId: string; secret: string } | null {
  if (!credential) return null;
  const dot = credential.indexOf(".");
  if (dot <= 0 || dot === credential.length - 1) return null;
  const accountId = credential.slice(0, dot);
  if (!/^[0-9a-f]{32}$/.test(accountId)) return null;
  return { accountId, secret: credential.slice(dot + 1) };
}
