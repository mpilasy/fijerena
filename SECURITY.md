# Security Policy

## Supported Versions

Only the latest release gets security fixes. Before you report a problem, make sure that it occurs
in the [latest release](https://github.com/mpilasy/fijerena/releases/latest), or on `main`.

This policy covers the two apps (TV and phone) and the live sync server in `server/`. The
development tools in `tools/` are not for production use, and are not covered.

## Report a Vulnerability

Do not open a public issue for a security problem.

Report it privately through GitHub: open the repository's
[Security tab](https://github.com/mpilasy/fijerena/security) and click **Report a vulnerability**.
Only the maintainer can see the report.

Give as much of this as you can:

- the app (TV, phone or sync server) and its version;
- the device and Android version, or how the sync server runs (Cloudflare Workers or workerd);
- what an attacker can do, and what they must have or know first;
- the steps to make the problem occur, or a proof of concept.

## What Happens Next

Fijerena is a project of one maintainer who works on it in their free time, so the times below are
targets, not promises.

1. You get a first answer within 7 days.
2. The maintainer confirms the problem, or tells you why it is not a vulnerability.
3. A fix goes into a new release. The advisory is published after the release, with credit to you
   unless you ask for none.

Please give the maintainer time to release a fix before you tell others about the problem.

## Scope Notes

- Fijerena provides no content. The security of the sources that you add (Xtream Codes panels,
  M3U lists, Jellyfin servers) is the responsibility of their operators.
- Live sync data is encrypted end to end. A way to read or change sync data without the household
  key is a vulnerability. See [PRIVACY.md](PRIVACY.md) for what the apps send and where.
