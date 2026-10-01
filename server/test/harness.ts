import { execFileSync, spawn, type ChildProcess } from "node:child_process";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import WebSocket from "ws";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

/** A running sync server and the client calls the tests make against it. */
export abstract class ServerBase {
  port = 0;

  abstract start(): Promise<void>;

  abstract stop(): Promise<void>;

  get url() {
    return `http://127.0.0.1:${this.port}`;
  }

  protected async waitUntilUp(): Promise<void> {
    for (let i = 0; i < 150; i++) {
      try {
        if ((await fetch(`${this.url}/info`)).ok) return;
      } catch {
        // not up yet
      }
      await new Promise((r) => setTimeout(r, 100));
    }
    throw new Error("sync server did not start");
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

/**
 * The sync server as self-hosting runs it: workerd with workerd/config.capnp, the built worker,
 * and Durable Objects on local disk — here in a fresh temporary directory and on a free port.
 */
export class Server extends ServerBase {
  private process: ChildProcess | null = null;
  readonly dataDir: string;
  private readonly dir: string;

  constructor(private readonly env: Record<string, string> = {}) {
    super();
    this.dir = mkdtempSync(join(tmpdir(), "fijerena-sync-"));
    this.dataDir = join(this.dir, "data");
    mkdirSync(this.dataDir);
    copyFileSync(join(root, "dist", "worker.js"), join(this.dir, "worker.js"));
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
    await this.waitUntilUp();
  }

  async stop(): Promise<void> {
    if (!this.process) return;
    const exited = new Promise((r) => this.process!.once("exit", r));
    this.process.kill();
    await exited;
    this.process = null;
  }
}

/**
 * The Docker image (`docker build -t fijerena-sync:test .` first), on a free port with a fresh
 * named volume for /data. [stop] stops the container; [start] again restarts the same one, on the
 * same volume. [remove] deletes container and volume.
 */
export class DockerServer extends ServerBase {
  private readonly name = `fijerena-sync-test-${process.pid}-${Math.random().toString(36).slice(2, 8)}`;
  private created = false;

  constructor(
    private readonly env: Record<string, string> = {},
    private readonly image = "fijerena-sync:test",
  ) {
    super();
  }

  async start(): Promise<void> {
    if (!this.created) {
      this.port = await freePort();
      const envArgs = Object.entries(this.env).flatMap(([k, v]) => ["-e", `${k}=${v}`]);
      docker("volume", "create", this.name);
      docker("run", "-d", "--name", this.name, "-p", `127.0.0.1:${this.port}:8787`, "-v", `${this.name}:/data`, ...envArgs, this.image);
      this.created = true;
    } else {
      docker("start", this.name);
    }
    await this.waitUntilUp();
  }

  async stop(): Promise<void> {
    if (this.created) docker("stop", "-t", "5", this.name);
  }

  async remove(): Promise<void> {
    if (!this.created) return;
    docker("rm", "-f", this.name);
    docker("volume", "rm", this.name);
    this.created = false;
  }
}

function docker(...args: string[]): string {
  return execFileSync("docker", args, { encoding: "utf8" }).trim();
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
