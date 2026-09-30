import { spawn, type ChildProcess } from "node:child_process";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import WebSocket from "ws";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

/**
 * The sync server as self-hosting runs it: workerd with workerd/config.capnp, the built worker,
 * and Durable Objects on local disk — here in a fresh temporary directory and on a free port.
 */
export class Server {
  private process: ChildProcess | null = null;
  readonly dataDir: string;
  private readonly dir: string;
  port = 0;

  constructor(private readonly env: Record<string, string> = {}) {
    this.dir = mkdtempSync(join(tmpdir(), "fijerena-sync-"));
    this.dataDir = join(this.dir, "data");
    mkdirSync(this.dataDir);
    copyFileSync(join(root, "dist", "worker.js"), join(this.dir, "worker.js"));
  }

  get url() {
    return `http://127.0.0.1:${this.port}`;
  }

  async start(): Promise<void> {
    this.port = await freePort();
    const config = readFileSync(join(root, "workerd", "config.capnp"), "utf8")
      .replace('path = "/data"', `path = "${this.dataDir}"`)
      .replace('"*:8787"', `"127.0.0.1:${this.port}"`);
    writeFileSync(join(this.dir, "config.capnp"), config);
    this.process = spawn(join(root, "node_modules", ".bin", "workerd"), ["serve", join(this.dir, "config.capnp"), "--experimental"], {
      env: { ...process.env, ...this.env },
      stdio: ["ignore", "ignore", "inherit"],
    });
    for (let i = 0; i < 100; i++) {
      try {
        if ((await fetch(`${this.url}/info`)).ok) return;
      } catch {
        // not up yet
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    throw new Error("workerd did not start");
  }

  async stop(): Promise<void> {
    if (!this.process) return;
    const exited = new Promise((r) => this.process!.once("exit", r));
    this.process.kill();
    await exited;
    this.process = null;
  }

  async request(method: string, path: string, options: { token?: string; body?: unknown; headers?: Record<string, string> } = {}) {
    const response = await fetch(this.url + path, {
      method,
      headers: {
        ...(options.token ? { authorization: `Bearer ${options.token}` } : {}),
        ...(options.body !== undefined ? { "content-type": "application/json" } : {}),
        ...options.headers,
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null };
  }

  /** A new account; returns its first device's credentials. */
  async createAccount(
    deviceName = "tv",
    headers: Record<string, string> = {},
  ): Promise<{ accountId: string; deviceId: string; deviceToken: string }> {
    const { status, body } = await this.request("POST", "/accounts", { body: { deviceName }, headers });
    if (status !== 201) throw new Error(`createAccount: ${status} ${JSON.stringify(body)}`);
    return body;
  }

  socket(token: string): WebSocket {
    return new WebSocket(`ws://127.0.0.1:${this.port}/ws`, { headers: { authorization: `Bearer ${token}` } });
  }
}

export function record(key: string, updatedAt: number, extra: Record<string, unknown> = {}) {
  return { key, updatedAt, deleted: false, payload: `cipher-${key}-${updatedAt}`, ...extra };
}

/** The next message on [ws]: "pong", or the parsed JSON. */
export function nextMessage(ws: WebSocket): Promise<unknown> {
  return new Promise((resolve, reject) => {
    ws.once("message", (data) => {
      const text = data.toString();
      resolve(text === "pong" ? "pong" : JSON.parse(text));
    });
    ws.once("error", reject);
  });
}

function freePort(): Promise<number> {
  return new Promise((resolve) => {
    const server = createServer();
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address() as { port: number };
      server.close(() => resolve(port));
    });
  });
}
