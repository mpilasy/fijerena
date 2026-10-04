# Self-hosted Fijerena sync server: the same worker as on Cloudflare, run by workerd.
# Durable Objects are stored as SQLite files under /data — mount a volume there and back it up.
# See docs/plans/archive/20260929_live-sync-plan.md → Self-hosting.
using Workerd = import "/workerd/workerd.capnp";

const config :Workerd.Config = (
  services = [
    (name = "main", worker = .syncWorker),
    (name = "disk", disk = (path = "/data", writable = true)),
  ],
  sockets = [(name = "http", address = "*:8787", http = (), service = "main")],
);

const syncWorker :Workerd.Worker = (
  modules = [(name = "worker.js", esModule = embed "worker.js")],
  compatibilityDate = "2026-09-01",
  durableObjectNamespaces = [
    (className = "Account", uniqueKey = "fijerena-sync-account", enableSql = true),
    (className = "Handoff", uniqueKey = "fijerena-sync-handoff", enableSql = true),
  ],
  # Experimental in workerd: pin the image's workerd version and back up /data before upgrading.
  durableObjectStorage = (localDisk = "disk"),
  bindings = [
    (name = "ACCOUNT", durableObjectNamespace = "Account"),
    (name = "HANDOFF", durableObjectNamespace = "Handoff"),
    (name = "SETUP_SECRET", fromEnvironment = "SETUP_SECRET"),
    # Tests shorten the 90-day tombstone retention; leave unset in production.
    (name = "TOMBSTONE_RETENTION_MS", fromEnvironment = "TOMBSTONE_RETENTION_MS"),
  ],
);
