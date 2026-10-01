import { DurableObject } from "cloudflare:workers";
import type { Env } from "./index";
import { error, json, randomId, randomSecret, sha256, splitCredential } from "./util";

const DAY_MS = 24 * 60 * 60 * 1000;
const DEFAULT_RETENTION_MS = 90 * DAY_MS;
const PAIRING_TTL_MS = 10 * 60 * 1000;
const MAX_BATCH = 500;
const MAX_PAGE = 1000;
const MAX_PAYLOAD = 64 * 1024;
const PURGE_INTERVAL_MS = 60 * 60 * 1000;

/**
 * A record as it travels: opaque key and tags (HMACs), device clock, and the ciphertext payload —
 * deletions included, since the key is one-way and the payload is where devices find out which
 * item it was.
 */
interface WireRecord {
  key: string;
  providerTag?: string | null;
  profileTag?: string | null;
  updatedAt: number;
  deleted: boolean;
  payload: string;
  /** On a deletion: also drop every other record of the same provider or profile. */
  cascade?: "provider" | "profile";
}

type StoredRecord = {
  key: string;
  provider_tag: string | null;
  profile_tag: string | null;
  seq: number;
  updated_at: number;
  deleted: number;
  payload: string | null;
};

type Device = {
  id: string;
  name: string;
  created_at: number;
  last_seen: number;
  revoked: number;
};

/**
 * One account: its records (one row per item, not per edit), its devices and pairing codes, and
 * the WebSockets of its connected devices. See docs/plans/20260929_live-sync-plan.md → Storage,
 * Endpoints.
 *
 * WebSockets use the hibernation API, and the 30 s client pings are answered by an auto-response,
 * so an idle connection never wakes the object.
 */
export class Account extends DurableObject<Env> {
  private readonly sql: SqlStorage;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS records (
        key TEXT PRIMARY KEY,
        provider_tag TEXT,
        profile_tag TEXT,
        seq INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        deleted INTEGER NOT NULL,
        payload TEXT,
        stored_at INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS records_seq ON records(seq);
      CREATE INDEX IF NOT EXISTS records_provider ON records(provider_tag);
      CREATE INDEX IF NOT EXISTS records_profile ON records(profile_tag);
      CREATE INDEX IF NOT EXISTS records_tombstones ON records(deleted, stored_at);
      CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY,
        token_hash TEXT NOT NULL UNIQUE,
        name TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        last_seen INTEGER NOT NULL,
        revoked INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS pairings (
        code_hash TEXT PRIMARY KEY,
        expires_at INTEGER NOT NULL
      );
      CREATE TABLE IF NOT EXISTS meta (
        k TEXT PRIMARY KEY,
        v INTEGER NOT NULL
      );
    `);
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    this.purgeTombstones(Number(request.headers.get("x-retention-ms")) || DEFAULT_RETENTION_MS);

    switch (`${request.method} ${url.pathname}`) {
      case "POST /internal/create":
        return this.createAccount(request);
      case "POST /internal/pair":
        return this.pair(request);
    }

    const auth = await this.authenticate(request);
    if (!auth) return error(401, "invalid or revoked device token");
    const { device, accountId } = auth;

    const route = `${request.method} ${url.pathname}`;
    if (route === "POST /changes") return this.push(request);
    if (route === "GET /changes") return this.pull(url);
    if (route === "GET /ws") return this.openSocket(request, device);
    if (route === "POST /pairings") return this.createPairing(accountId);
    if (route === "GET /devices") return this.listDevices(device);
    const handoff = url.pathname.match(/^\/handoffs\/([0-9a-f]{32})$/);
    if (request.method === "POST" && handoff) {
      // A device of this account hands the account to a joining device (see Handoff).
      return this.env.HANDOFF.get(this.env.HANDOFF.idFromName(handoff[1])).fetch(
        new Request(new URL("/internal/fill", request.url).toString(), { method: "POST", body: await request.text() }),
      );
    }
    const revoke = url.pathname.match(/^\/devices\/([0-9a-f]{32})$/);
    if (request.method === "DELETE" && revoke) return this.revoke(revoke[1]);
    return error(404, "not found");
  }

  // --- Accounts, devices, pairing ---

  private async createAccount(request: Request): Promise<Response> {
    if (this.sql.exec("SELECT 1 FROM devices LIMIT 1").toArray().length > 0) return error(409, "account exists");
    const accountId = request.headers.get("x-account-id")!;
    const name = await this.deviceName(request);
    const { deviceId, token } = await this.addDevice(accountId, name);
    return json({ accountId, deviceId, deviceToken: token }, 201);
  }

  private async pair(request: Request): Promise<Response> {
    const body = (await request.json().catch(() => null)) as { code?: string; deviceName?: string } | null;
    const code = splitCredential(body?.code);
    if (!code) return error(400, "invalid pairing code");
    const hash = await sha256(code.secret);
    const pairing = this.sql.exec<{ expires_at: number }>("SELECT expires_at FROM pairings WHERE code_hash = ?", hash).toArray()[0];
    // Single use, whatever happens next.
    this.sql.exec("DELETE FROM pairings WHERE code_hash = ? OR expires_at < ?", hash, Date.now());
    if (!pairing || pairing.expires_at < Date.now()) return error(403, "pairing code expired or already used");
    const { deviceId, token } = await this.addDevice(code.accountId, cleanName(body?.deviceName));
    return json({ accountId: code.accountId, deviceId, deviceToken: token }, 201);
  }

  private async createPairing(accountId: string): Promise<Response> {
    const secret = randomSecret();
    const expiresAt = Date.now() + PAIRING_TTL_MS;
    this.sql.exec("INSERT INTO pairings (code_hash, expires_at) VALUES (?, ?)", await sha256(secret), expiresAt);
    return json({ code: `${accountId}.${secret}`, expiresAt }, 201);
  }

  /**
   * An idle device with the app open only pings its socket, and pings are answered without waking
   * this object — so they never reach `last_seen`. The socket remembers the last one.
   */
  private lastSeen(device: Device): number {
    const pings = this.ctx.getWebSockets(device.id).map((ws) => this.ctx.getWebSocketAutoResponseTimestamp(ws)?.getTime() ?? 0);
    return Math.max(device.last_seen, ...pings);
  }

  private listDevices(current: Device): Response {
    const devices = this.sql.exec<Device>("SELECT id, name, created_at, last_seen, revoked FROM devices ORDER BY created_at").toArray();
    return json({
      devices: devices.map((d) => ({
        id: d.id,
        name: d.name,
        createdAt: d.created_at,
        lastSeen: this.lastSeen(d),
        revoked: d.revoked === 1,
        current: d.id === current.id,
      })),
    });
  }

  private revoke(deviceId: string): Response {
    const changed = this.sql.exec("UPDATE devices SET revoked = 1 WHERE id = ?", deviceId).rowsWritten;
    if (changed === 0) return error(404, "no such device");
    for (const ws of this.ctx.getWebSockets(deviceId)) ws.close(4001, "device revoked");
    return json({ revoked: deviceId });
  }

  private async addDevice(accountId: string, name: string): Promise<{ deviceId: string; token: string }> {
    const deviceId = randomId();
    const secret = randomSecret();
    const now = Date.now();
    this.sql.exec(
      "INSERT INTO devices (id, token_hash, name, created_at, last_seen) VALUES (?, ?, ?, ?, ?)",
      deviceId,
      await sha256(secret),
      name,
      now,
      now,
    );
    return { deviceId, token: `${accountId}.${secret}` };
  }

  private async authenticate(request: Request): Promise<{ device: Device; accountId: string } | null> {
    const token = splitCredential(request.headers.get("authorization")?.replace(/^Bearer\s+/i, ""));
    if (!token) return null;
    const device = this.sql
      .exec<Device>("SELECT id, name, created_at, last_seen, revoked FROM devices WHERE token_hash = ?", await sha256(token.secret))
      .toArray()[0];
    if (!device || device.revoked === 1) return null;
    this.sql.exec("UPDATE devices SET last_seen = ? WHERE id = ?", Date.now(), device.id);
    return { device, accountId: token.accountId };
  }

  private async deviceName(request: Request): Promise<string> {
    const body = (await request.json().catch(() => null)) as { deviceName?: string } | null;
    return cleanName(body?.deviceName);
  }

  // --- Records ---

  private async push(request: Request): Promise<Response> {
    const body = (await request.json().catch(() => null)) as { records?: WireRecord[] } | null;
    const records = body?.records;
    if (!Array.isArray(records) || records.length > MAX_BATCH) return error(400, `records must be an array of at most ${MAX_BATCH}`);
    for (const record of records) {
      const problem = invalid(record);
      if (problem) return error(400, problem);
    }

    const rejected: { key: string; reason: string }[] = [];
    let accepted = 0;
    this.ctx.storage.transactionSync(() => {
      for (const r of records) {
        const existing = this.sql.exec<{ updated_at: number }>("SELECT updated_at FROM records WHERE key = ?", r.key).toArray()[0];
        // A delayed upload must not roll state back: an older or equal version loses.
        if (existing && existing.updated_at >= r.updatedAt) {
          rejected.push({ key: r.key, reason: "stale" });
          continue;
        }
        const seq = this.nextSeq();
        this.sql.exec(
          "INSERT OR REPLACE INTO records (key, provider_tag, profile_tag, seq, updated_at, deleted, payload, stored_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
          r.key,
          r.providerTag ?? null,
          r.profileTag ?? null,
          seq,
          r.updatedAt,
          r.deleted ? 1 : 0,
          r.payload,
          Date.now(),
        );
        if (r.deleted && r.cascade === "provider" && r.providerTag) {
          this.sql.exec("DELETE FROM records WHERE provider_tag = ? AND key != ?", r.providerTag, r.key);
        }
        if (r.deleted && r.cascade === "profile" && r.profileTag) {
          this.sql.exec("DELETE FROM records WHERE profile_tag = ? AND key != ?", r.profileTag, r.key);
        }
        accepted++;
      }
    });

    const head = this.head();
    if (accepted > 0) this.broadcast(head);
    return json({ head, accepted, rejected });
  }

  private pull(url: URL): Response {
    const since = Number(url.searchParams.get("since") ?? "0");
    const limit = Math.min(Number(url.searchParams.get("limit") ?? "500") || 500, MAX_PAGE);
    if (!Number.isInteger(since) || since < 0) return error(400, "since must be a non-negative integer");
    // Tombstones this device never saw have been purged: only a full resync is correct now.
    if (since > 0 && since < this.getMeta("purged_through")) return json({ resync: true }, 410);

    const rows = this.sql
      .exec<StoredRecord>(
        "SELECT key, provider_tag, profile_tag, seq, updated_at, deleted, payload FROM records WHERE seq > ? ORDER BY seq LIMIT ?",
        since,
        limit + 1,
      )
      .toArray();
    const page = rows.slice(0, limit);
    return json({
      head: this.head(),
      more: rows.length > limit,
      records: page.map((r) => ({
        key: r.key,
        providerTag: r.provider_tag,
        profileTag: r.profile_tag,
        seq: r.seq,
        updatedAt: r.updated_at,
        deleted: r.deleted === 1,
        payload: r.payload,
      })),
    });
  }

  // --- WebSocket ---

  private openSocket(request: Request, device: Device): Response {
    if (request.headers.get("upgrade")?.toLowerCase() !== "websocket") return error(426, "expected a websocket upgrade");
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server, [device.id]);
    server.send(JSON.stringify({ head: this.head() }));
    return new Response(null, { status: 101, webSocket: client });
  }

  /** Clients only listen; anything they send other than the auto-answered ping is ignored. */
  async webSocketMessage(): Promise<void> {}

  async webSocketClose(ws: WebSocket, code: number): Promise<void> {
    try {
      ws.close(code === 1005 ? 1000 : code, "closing");
    } catch {
      // already closed
    }
  }

  private broadcast(head: number): void {
    const message = JSON.stringify({ head });
    for (const ws of this.ctx.getWebSockets()) {
      try {
        ws.send(message);
      } catch {
        // a socket closing under us; it reconnects and pulls
      }
    }
  }

  // --- Bookkeeping ---

  private nextSeq(): number {
    const seq = this.head() + 1;
    this.setMeta("head", seq);
    return seq;
  }

  private head(): number {
    return this.getMeta("head");
  }

  private getMeta(k: string): number {
    return this.sql.exec<{ v: number }>("SELECT v FROM meta WHERE k = ?", k).toArray()[0]?.v ?? 0;
  }

  private setMeta(k: string, v: number): void {
    this.sql.exec("INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)", k, v);
  }

  /** Tombstones older than the retention go, at most once an hour; `purged_through` marks the gap. */
  private purgeTombstones(retentionMs: number): void {
    const now = Date.now();
    if (now - this.getMeta("last_purge") < PURGE_INTERVAL_MS && retentionMs === DEFAULT_RETENTION_MS) return;
    this.setMeta("last_purge", now);
    const cutoff = now - retentionMs;
    const newest = this.sql
      .exec<{ s: number | null }>("SELECT MAX(seq) AS s FROM records WHERE deleted = 1 AND stored_at < ?", cutoff)
      .toArray()[0]?.s;
    if (newest == null) return;
    this.sql.exec("DELETE FROM records WHERE deleted = 1 AND stored_at < ?", cutoff);
    this.setMeta("purged_through", Math.max(this.getMeta("purged_through"), newest));
  }
}

function cleanName(name: unknown): string {
  return typeof name === "string" && name.trim() ? name.trim().slice(0, 64) : "Device";
}

function invalid(r: WireRecord): string | null {
  if (!r || typeof r.key !== "string" || r.key.length === 0 || r.key.length > 256) return "record key must be 1-256 characters";
  if (!Number.isSafeInteger(r.updatedAt) || r.updatedAt < 0) return "updatedAt must be a non-negative integer";
  if (typeof r.deleted !== "boolean") return "deleted must be a boolean";
  if (typeof r.payload !== "string" || r.payload.length > MAX_PAYLOAD) return `payload must be a string of at most ${MAX_PAYLOAD} characters`;
  if (r.cascade !== undefined && (!r.deleted || (r.cascade !== "provider" && r.cascade !== "profile"))) return "cascade is only for provider or profile deletions";
  for (const tag of [r.providerTag, r.profileTag]) {
    if (tag != null && (typeof tag !== "string" || tag.length > 256)) return "tags must be strings of at most 256 characters";
  }
  return null;
}
