import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { DockerServer, nextMessage, record } from "./harness";

/**
 * The self-hosting Docker image end to end. Opt-in — it needs Docker and a built image:
 *
 *     docker build -t fijerena-sync:test . && RUN_DOCKER_TESTS=1 npx vitest run test/docker.test.ts
 *
 * Each container gets its own named volume, removed afterwards.
 */
const enabled = process.env.RUN_DOCKER_TESTS === "1";

describe.runIf(enabled)("Docker image", () => {
  const server = new DockerServer();
  beforeAll(() => server.start(), 60_000);
  afterAll(() => server.remove(), 60_000);

  it("serves the API: accounts, records, pairing and the WebSocket", async () => {
    expect((await server.request("GET", "/info")).body).toMatchObject({ service: "fijerena-sync", protocol: 1, setupSecretRequired: false });

    const tv = await server.createAccount("tv");
    const ws = server.socket(tv.deviceToken);
    expect(await nextMessage(ws)).toEqual({ head: 0 });
    ws.send("ping");
    expect(await nextMessage(ws)).toBe("pong");

    const notified = nextMessage(ws);
    const push = await server.request("POST", "/changes", { token: tv.deviceToken, body: { records: [record("a", 1), record("b", 2)] } });
    expect(push.body).toMatchObject({ head: 2, accepted: 2 });
    expect(await notified).toEqual({ head: 2 });
    ws.close();

    const { body: pairing } = await server.request("POST", "/pairings", { token: tv.deviceToken });
    const { body: phone } = await server.request("POST", "/pair", { body: { code: pairing.code, deviceName: "phone" } });
    const pulled = await server.request("GET", "/changes?since=0", { token: phone.deviceToken });
    expect(pulled.body.records.map((r: { key: string }) => r.key)).toEqual(["a", "b"]);
  });

  it("keeps the data on its volume across a container restart", async () => {
    const { deviceToken } = await server.createAccount();
    await server.request("POST", "/changes", { token: deviceToken, body: { records: [record("durable", 1)] } });

    await server.stop();
    await server.start();

    const all = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(all.body.records.map((r: { key: string }) => r.key)).toEqual(["durable"]);
  }, 60_000);
});

describe.runIf(enabled)("Docker image with a setup secret", () => {
  const server = new DockerServer({ SETUP_SECRET: "letmein" });
  beforeAll(() => server.start(), 60_000);
  afterAll(() => server.remove(), 60_000);

  it("creates accounts only with the secret", async () => {
    expect((await server.request("GET", "/info")).body.setupSecretRequired).toBe(true);
    expect((await server.request("POST", "/accounts", { body: {} })).status).toBe(401);
    await expect(server.createAccount("tv", { "x-setup-secret": "letmein" })).resolves.toHaveProperty("deviceToken");
  });
});
