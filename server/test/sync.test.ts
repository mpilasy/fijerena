import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Server, nextMessage, record } from "./harness";

/**
 * The sync server end to end, over HTTP and WebSocket, as self-hosting runs it (workerd, Durable
 * Objects on local disk). See docs/plans/archive/20260929_live-sync-plan.md → Server, Endpoints.
 */
describe("sync server", () => {
  const server = new Server({ TOMBSTONE_RETENTION_MS: String(365 * 24 * 60 * 60 * 1000) });
  beforeAll(() => server.start(), 20_000);
  afterAll(() => server.stop());

  it("reports its protocol", async () => {
    const { status, body } = await server.request("GET", "/info");
    expect(status).toBe(200);
    expect(body).toMatchObject({ service: "fijerena-sync", protocol: 1, setupSecretRequired: false });
  });

  it("refuses requests without a valid device token", async () => {
    expect((await server.request("GET", "/changes")).status).toBe(401);
    expect((await server.request("GET", "/changes", { token: "garbage" })).status).toBe(401);
    const { accountId } = await server.createAccount();
    expect((await server.request("GET", "/changes", { token: `${accountId}.wrong` })).status).toBe(401);
  });

  it("does not expose the object's internal routes", async () => {
    const { deviceToken } = await server.createAccount();
    expect((await server.request("POST", "/internal/create", { token: deviceToken, body: {} })).status).toBe(404);
  });

  it("stores pushed records and hands them out in order, in pages", async () => {
    const { deviceToken } = await server.createAccount();
    const push = await server.request("POST", "/changes", {
      token: deviceToken,
      body: { records: [record("a", 10), record("b", 11), record("c", 12)] },
    });
    expect(push.body).toMatchObject({ head: 3, accepted: 3, rejected: [] });

    const first = await server.request("GET", "/changes?since=0&limit=2", { token: deviceToken });
    expect(first.body.records.map((r: { key: string }) => r.key)).toEqual(["a", "b"]);
    expect(first.body.more).toBe(true);
    const rest = await server.request("GET", `/changes?since=${first.body.records[1].seq}&limit=2`, { token: deviceToken });
    expect(rest.body.records.map((r: { key: string }) => r.key)).toEqual(["c"]);
    expect(rest.body.more).toBe(false);
    expect(rest.body.records[0]).toMatchObject({ updatedAt: 12, deleted: false, payload: "cipher-c-12" });
  });

  it("keeps one row per item, and never lets an older or equal version win", async () => {
    const { deviceToken } = await server.createAccount();
    await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("x", 100)] } });

    const stale = await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("x", 90), record("x", 100)] } });
    expect(stale.body.accepted).toBe(0);
    expect(stale.body.rejected).toEqual([
      { key: "x", reason: "stale" },
      { key: "x", reason: "stale" },
    ]);

    await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("x", 110)] } });
    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(all.body.records).toHaveLength(1);
    expect(all.body.records[0]).toMatchObject({ key: "x", seq: 2, updatedAt: 110 });
  });

  it("stores a deletion with its payload, and a provider deletion drops the provider's records", async () => {
    const { deviceToken } = await server.createAccount();
    await server.request("POST", "/changes", {
      token: deviceToken,
      body: {
        records: [
          record("prov", 1, { providerTag: "P" }),
          record("fav", 2, { providerTag: "P", profileTag: "K" }),
          record("other", 3, { providerTag: "Q" }),
        ],
      },
    });
    await server.request("POST", "/changes", {
      token: deviceToken,
      body: { records: [{ key: "prov", providerTag: "P", updatedAt: 5, deleted: true, payload: "which-provider", cascade: "provider" }] },
    });
    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    const keys = all.body.records.map((r: { key: string }) => r.key);
    expect(keys).toEqual(["other", "prov"]);
    // The payload stays: the key is one-way, so it is how devices learn what was deleted.
    expect(all.body.records[1]).toMatchObject({ deleted: true, payload: "which-provider" });
  });

  it("rejects malformed batches", async () => {
    const { deviceToken } = await server.createAccount();
    for (const bad of [{ records: "nope" }, {}, { records: Array.from({ length: 501 }, (_, i) => record(`k${i}`, 1)) }]) {
      expect((await server.request("POST", "/changes", { token: deviceToken, body: bad })).status).toBe(400);
    }
  });

  it("rejects invalid records one by one and keeps the rest of the batch", async () => {
    const { deviceToken } = await server.createAccount();
    const invalidRecords = [
      { key: "", updatedAt: 1, deleted: false, payload: "x" },
      { key: "k1", updatedAt: -1, deleted: false, payload: "x" },
      { key: "k2", updatedAt: 1, deleted: false },
      { key: "k3", updatedAt: 1, deleted: true },
      { key: "k4", updatedAt: 1, deleted: false, payload: "x", cascade: "provider" },
      { key: "big", updatedAt: 1, deleted: false, payload: "x".repeat(64 * 1024 + 1) },
      { key: "future", updatedAt: Date.now() + 2 * 24 * 60 * 60 * 1000, deleted: false, payload: "x" },
    ];
    const push = await server.request("POST", "/changes", {
      token: deviceToken,
      body: { records: [record("before", 1), ...invalidRecords, record("after", 2)] },
    });
    expect(push.status).toBe(200);
    expect(push.body.accepted).toBe(2);
    expect(push.body.rejected.map((r: { key: string }) => r.key)).toEqual(["", "k1", "k2", "k3", "k4", "big", "future"]);
    for (const r of push.body.rejected) expect(r.reason).toMatch(/^invalid: /);
    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(all.body.records.map((r: { key: string }) => r.key)).toEqual(["before", "after"]);
  });

  // F-22: one oversized record used to fail the whole batch with a 400; the device retried the
  // same oldest-first batch every pass and never pushed anything again. See
  // docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md F-22.
  it("takes a payload at the size limit, rejects one past it, and the device's next push goes through", async () => {
    const { deviceToken } = await server.createAccount();
    const limit = 64 * 1024;
    const push = await server.request("POST", "/changes", {
      token: deviceToken,
      body: {
        records: [
          record("small", 1),
          record("at-limit", 2, { payload: "x".repeat(limit) }),
          record("oversized", 3, { payload: "x".repeat(limit + 1) }),
          record("after", 4),
        ],
      },
    });
    expect(push.status).toBe(200);
    expect(push.body).toMatchObject({ head: 3, accepted: 3 });
    expect(push.body.rejected).toHaveLength(1);
    expect(push.body.rejected[0]).toMatchObject({ key: "oversized" });
    expect(push.body.rejected[0].reason).toMatch(/^invalid: payload/);

    const next = await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("later", 5)] } });
    expect(next.body).toMatchObject({ head: 4, accepted: 1, rejected: [] });
    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(all.body.records.map((r: { key: string }) => r.key)).toEqual(["small", "at-limit", "after", "later"]);
  });

  it("keeps accounts apart", async () => {
    const a = await server.createAccount();
    const b = await server.createAccount();
    await server.request("POST", "/changes", { token: a.deviceToken, body: { records: [record("secret", 1)] } });
    expect((await server.request("GET", "/changes?since=0", { token: b.deviceToken })).body.records).toEqual([]);
  });

  it("pairs a new device with a single-use code", async () => {
    const tv = await server.createAccount("tv");
    const { body: pairing } = await server.request("POST", "/pairings", { token: tv.deviceToken });
    expect(pairing.code.startsWith(`${tv.accountId}.`)).toBe(true);

    const { status, body: phone } = await server.request("POST", "/pair", { body: { code: pairing.code, deviceName: "phone" } });
    expect(status).toBe(201);
    expect(phone.accountId).toBe(tv.accountId);

    await server.request("POST", "/changes", { token: tv.deviceToken, body: { records: [record("shared", 1)] } });
    const pulled = await server.request("GET", "/changes?since=0", { token: phone.deviceToken });
    expect(pulled.body.records.map((r: { key: string }) => r.key)).toEqual(["shared"]);

    expect((await server.request("POST", "/pair", { body: { code: pairing.code, deviceName: "again" } })).status).toBe(403);
    expect((await server.request("POST", "/pair", { body: { code: "nonsense" } })).status).toBe(400);
  });

  it("lists devices and revokes one, closing its socket", async () => {
    const tv = await server.createAccount("tv");
    const { body: pairing } = await server.request("POST", "/pairings", { token: tv.deviceToken });
    const { body: phone } = await server.request("POST", "/pair", { body: { code: pairing.code, deviceName: "phone" } });

    const { body: list } = await server.request("GET", "/devices", { token: tv.deviceToken });
    expect(list.devices.map((d: { name: string; current: boolean }) => [d.name, d.current])).toEqual([
      ["tv", true],
      ["phone", false],
    ]);

    const ws = server.socket(phone.deviceToken);
    await nextMessage(ws); // the current head, on connect
    const closed = new Promise<number>((r) => ws.once("close", (code) => r(code)));
    expect((await server.request("DELETE", `/devices/${phone.deviceId}`, { token: tv.deviceToken })).status).toBe(200);
    expect(await closed).toBe(4001);
    expect((await server.request("GET", "/changes?since=0", { token: phone.deviceToken })).status).toBe(401);
  });

  it("lets a device leave by revoking itself", async () => {
    const { deviceToken, deviceId } = await server.createAccount("tv");
    expect((await server.request("DELETE", `/devices/${deviceId}`, { token: deviceToken })).status).toBe(200);
    expect((await server.request("GET", "/changes?since=0", { token: deviceToken })).status).toBe(401);
  });

  it("counts a socket's pings as the device being seen", async () => {
    const tv = await server.createAccount("tv");
    const { body: pairing } = await server.request("POST", "/pairings", { token: tv.deviceToken });
    const { body: phone } = await server.request("POST", "/pair", { body: { code: pairing.code, deviceName: "phone" } });
    const lastSeen = async () =>
      (await server.request("GET", "/devices", { token: tv.deviceToken })).body.devices.find((d: { id: string }) => d.id === phone.deviceId).lastSeen;

    const ws = server.socket(phone.deviceToken);
    await nextMessage(ws);
    const before = await lastSeen();
    await new Promise((r) => setTimeout(r, 20));
    ws.send("ping");
    await nextMessage(ws);
    expect(await lastSeen()).toBeGreaterThan(before);
    ws.close();
  });

  it("tells connected devices the new head, and answers pings", async () => {
    const { deviceToken } = await server.createAccount();
    const ws = server.socket(deviceToken);
    expect(await nextMessage(ws)).toEqual({ head: 0 });

    ws.send("ping");
    expect(await nextMessage(ws)).toBe("pong");

    const notified = nextMessage(ws);
    await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("k", 1)] } });
    expect(await notified).toEqual({ head: 1 });
    ws.close();
  });

  it("hands an account to a device without a camera, once", async () => {
    const phone = await server.createAccount("phone");
    // The TV opens a handoff (no account yet) and shows its id in a QR code.
    const opened = await server.request("POST", "/handoffs", { body: {} });
    expect(opened.status).toBe(201);
    const { handoffId } = opened.body;
    expect((await server.request("GET", `/handoffs/${handoffId}`)).body).toMatchObject({ ready: false });

    // Only a device of an account can fill it, and only once.
    expect((await server.request("POST", `/handoffs/${handoffId}`, { body: { sealed: "x", senderKey: "y" } })).status).toBe(401);
    const filled = await server.request("POST", `/handoffs/${handoffId}`, { token: phone.deviceToken, body: { sealed: "for-tv", senderKey: "phone-pub" } });
    expect(filled.status).toBe(200);
    expect((await server.request("POST", `/handoffs/${handoffId}`, { token: phone.deviceToken, body: { sealed: "again", senderKey: "z" } })).status).toBe(409);

    // The TV collects it — and nobody can afterwards.
    expect((await server.request("GET", `/handoffs/${handoffId}`)).body).toEqual({ ready: true, sealed: "for-tv", senderKey: "phone-pub" });
    expect((await server.request("GET", `/handoffs/${handoffId}`)).status).toBe(404);
    expect((await server.request("GET", "/handoffs/0123456789abcdef0123456789abcdef")).status).toBe(404);
  });

  it("keeps everything across a restart", async () => {
    const { deviceToken } = await server.createAccount();
    await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("durable", 1)] } });
    await server.stop();
    await server.start();
    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(all.body.records.map((r: { key: string }) => r.key)).toEqual(["durable"]);
  });
});

describe("sync server with a setup secret", () => {
  const server = new Server({ SETUP_SECRET: "letmein" });
  beforeAll(() => server.start(), 20_000);
  afterAll(() => server.stop());

  it("creates accounts only with the secret", async () => {
    expect((await server.request("GET", "/info")).body.setupSecretRequired).toBe(true);
    expect((await server.request("POST", "/accounts", { body: {} })).status).toBe(401);
    expect((await server.request("POST", "/accounts", { body: {}, headers: { "x-setup-secret": "nope" } })).status).toBe(401);
    await expect(server.createAccount("tv", { "x-setup-secret": "letmein" })).resolves.toHaveProperty("deviceToken");
  });
});

describe("sync server purging old tombstones", () => {
  const server = new Server({ TOMBSTONE_RETENTION_MS: "1" });
  beforeAll(() => server.start(), 20_000);
  afterAll(() => server.stop());

  it("drops expired tombstones and sends a device that missed them to a full resync", async () => {
    const { deviceToken } = await server.createAccount();
    await server.request("POST", "/changes", {
      token: deviceToken,
      body: { records: [record("keep", 1), record("gone", 2, { deleted: true }), record("later", 3)] },
    });
    await new Promise((r) => setTimeout(r, 10));

    // A device that saw only "keep" (seq 1) missed the tombstone: only a full resync is correct.
    expect((await server.request("GET", "/changes?since=1", { token: deviceToken })).status).toBe(410);
    const full = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(full.body.records.map((r: { key: string }) => r.key)).toEqual(["keep", "later"]);
    // One already past the tombstone carries on.
    expect((await server.request("GET", "/changes?since=3", { token: deviceToken })).status).toBe(200);
  });
});
