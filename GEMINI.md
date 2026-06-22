See [AGENTS.md](AGENTS.md) for all AI agent instructions.

## Device Discovery Directive
- **Network Segment:** The development network is `192.168.68.0/24`.
- **NVIDIA Shields & Sony Bravia:** IPs drift across sessions (DHCP) — do not assume a fixed address. Run `adb mdns services` first; it lists every adb-over-network device currently broadcasting, including ones that moved to a new IP. Fall back to `arp -a | grep 192.168.68.` only if mdns finds nothing.
- **Verification:** Always verify device type with `getprop ro.build.characteristics` (or cross-reference `product:`/`model:` from `adb devices -l`) before deployment — never assume identity from IP or port number alone.
