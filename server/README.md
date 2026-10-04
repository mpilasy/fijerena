# Fijerena sync server

Live sync between Fijerena devices: one ordered log of end-to-end encrypted records per account,
plus a WebSocket that tells connected devices when it grows. The server never sees anything
readable — record keys are HMACs and payloads ciphertext, both made on the devices. Design:
[`docs/plans/archive/20260929_live-sync-plan.md`](../docs/plans/archive/20260929_live-sync-plan.md) → Server.

One TypeScript codebase, two ways to run it: a Cloudflare Worker with one Durable Object per
account, or the same worker in Docker under `workerd`, Cloudflare's open-source runtime.

## Develop and test

```sh
npm ci
npm test         # builds, then runs the integration tests against a real workerd
npm run typecheck
```

The tests start `workerd` with `workerd/config.capnp` — the self-hosted configuration — on a
temporary data directory, so they exercise exactly what the Docker image runs. The image itself
has its own opt-in tests (each container on a throwaway named volume, removed afterwards):

```sh
docker build -t fijerena-sync:test .
RUN_DOCKER_TESTS=1 npx vitest run test/docker.test.ts
```

The image is Debian slim plus the native `workerd` binary (~290 MB) — no Node at runtime.

## Deploy to Cloudflare

```sh
npx wrangler login
npx wrangler secret put SETUP_SECRET   # optional, see below
npm run deploy
```

## Self-host with Docker

```yaml
services:
  fijerena-sync:
    build: ./server            # or a pinned image
    restart: unless-stopped
    environment:
      SETUP_SECRET: change-me  # optional, see below
    volumes:
      - ./data:/data           # Durable Object SQLite files — back this up
    ports:
      - "8787:8787"
```

Behind Nginx Proxy Manager: proxy host → `http://<host>:8787` with **Websockets Support** on.
`workerd`'s local Durable Object storage is experimental: the version is pinned in
`package-lock.json`; back up `/data` before upgrading.

## Setup secret

When `SETUP_SECRET` is set, creating an account needs it in the `X-Setup-Secret` header, so a
server reachable from the internet can't be used by strangers. Adding devices to an existing
account (pairing) never needs it.

## API (protocol 1)

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /info` | — | `{service, protocol, setupSecretRequired}` — validates a server URL |
| `POST /accounts` `{deviceName}` | setup secret, if set | new account and its first device → `{accountId, deviceId, deviceToken}` |
| `POST /pairings` | device | one-time pairing code, valid 10 minutes → `{code, expiresAt}` |
| `POST /pair` `{code, deviceName}` | — | adds a device with a pairing code → `{accountId, deviceId, deviceToken}` |
| `POST /changes` `{records}` | device | up to 500 records → `{head, accepted, rejected}` |
| `GET /changes?since=&limit=` | device | records after `since`, in `seq` order → `{records, head, more}`, or `410 {resync: true}` |
| `GET /ws` | device | WebSocket: `{"head": n}` on connect and after every write; `ping` → `pong` |
| `POST /handoffs` | — | opens a handoff for a device that can't scan → `{handoffId, expiresAt}` (10 minutes) |
| `POST /handoffs/:id` `{sealed, senderKey}` | device | fills it: a pairing code and the account key, sealed to the joining device's key |
| `GET /handoffs/:id` | — | `{ready: false}`, or once only `{ready: true, sealed, senderKey}` |
| `GET /devices` | device | the account's devices |
| `DELETE /devices/:id` | device | revokes a device and closes its socket |

Devices authenticate with `Authorization: Bearer <deviceToken>`. A record is
`{key, providerTag?, profileTag?, updatedAt, deleted, payload, cascade?}`. The payload is required on
deletions too: keys are one-way, so the encrypted payload is where devices learn which item it
was. `updatedAt` is the
device's hybrid logical clock, and a write that isn't newer than the stored version is rejected as
`stale`. A record that fails validation (payload over 64 KiB, bad fields, `updatedAt` more than a day
ahead of the server's clock) is rejected on its own as
`invalid: <why>`; the rest of the batch still applies. Only a malformed batch (not an array, over
500 records) gets `400`. A deletion with `cascade: "provider"` or `"profile"` also drops every other record with the
same tag. Tombstones are purged after 90 days; a device whose `since` falls before the purge gets
`410` and must pull again from 0.
