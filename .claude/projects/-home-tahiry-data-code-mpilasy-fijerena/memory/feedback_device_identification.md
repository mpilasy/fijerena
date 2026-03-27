---
name: Device identification thoroughness
description: When identifying Android devices, always pull the market name (ro.oplus.market.name, ro.product.marketname, etc.) — not just ro.product.model which is often a codename.
type: feedback
---

When identifying a connected Android device, always get the human-readable market name, not just the internal model code. Use properties like `ro.product.marketname`, `ro.oplus.market.name`, `ro.vendor.oplus.market.name`, or `ro.config.marketing_name` in addition to `ro.product.model`.

**Why:** User was frustrated when I lazily reported "CPH2655" and guessed "OnePlus Nord CE4 Lite" instead of pulling the actual market name property which clearly said "OnePlus 13".

**How to apply:** When asked to identify a device, always run a broad property query upfront including market name fields, build fingerprint, and Android version. Don't guess from model numbers.
