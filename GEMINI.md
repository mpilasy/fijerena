See [AGENTS.md](AGENTS.md) for all AI agent instructions.

## Device Discovery Directive
- **Network Segment:** The development network is `192.168.68.0/24`.
- **NVIDIA Shields:** If not already connected via ADB, use `arp -a | grep 192.168.68.` to find them or try connecting to `192.168.68.39`, `192.168.68.40`, and `192.168.68.51` (Sony Bravia) directly.
- **Verification:** Always verify device type with `getprop ro.build.characteristics` before deployment.
