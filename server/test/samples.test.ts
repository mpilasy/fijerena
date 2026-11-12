import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { Server, nextMessage } from "./harness";

/**
 * The server against the sample messages that the app's tests read too (SyncWireSamplesTest in
 * core/network), so the two sides cannot drift. The samples are under
 * core/network/src/test/resources/fixtures/sync/. See the provider data tests plan, PD-Q6.
 */
const samples = resolve(dirname(fileURLToPath(import.meta.url)), "../../core/network/src/test/resources/fixtures/sync");
const sample = (name: string) => JSON.parse(readFileSync(resolve(samples, name), "utf8"));

describe("sync server and the shared samples", () => {
  const server = new Server();
  beforeAll(() => server.start(), 20_000);
  afterAll(() => server.stop());

  it("answers /info as the sample", async () => {
    expect((await server.request("GET", "/info")).body).toEqual(sample("var-info-sample.json"));
  });

  it("takes the sample push and gives the sample pull and head", async () => {
    const { deviceToken } = await server.createAccount();

    const push = await server.request("POST", "/changes", { token: deviceToken, body: sample("var-push-request-sample.json") });
    expect(push.body).toEqual(sample("var-push-response-sample.json"));

    const pull = await server.request("GET", "/changes?since=0", { token: deviceToken });
    expect(pull.body).toEqual(sample("var-pull-response-sample.json"));

    const ws = server.socket(deviceToken);
    expect(await nextMessage(ws)).toEqual(sample("var-head-sample.json"));
    ws.close();
  });

  it("hands over a filled handoff as the sample", async () => {
    const { deviceToken } = await server.createAccount("phone");
    const { handoffId } = (await server.request("POST", "/handoffs", { body: {} })).body;
    const { sealed, senderKey } = sample("var-handoff-collected-sample.json");
    await server.request("POST", `/handoffs/${handoffId}`, { token: deviceToken, body: { sealed, senderKey } });

    expect((await server.request("GET", `/handoffs/${handoffId}`)).body).toEqual(sample("var-handoff-collected-sample.json"));
  });
});
