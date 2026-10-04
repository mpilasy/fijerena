# Self-hosted Fijerena sync server — see docs/plans/archive/20260929_live-sync-plan.md → Self-hosting.
# workerd is pinned (package-lock.json): its local Durable Object storage is experimental, so back up
# /data before upgrading the image.
FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build \
    # workerd is a native binary; npm installs the one for this platform. Only it goes in the image.
    && cp node_modules/@cloudflare/workerd-linux-*/bin/workerd /app/workerd

FROM debian:bookworm-slim
WORKDIR /app
COPY --from=build /app/workerd /usr/local/bin/workerd
COPY workerd/config.capnp ./config.capnp
COPY --from=build /app/dist/worker.js ./worker.js
VOLUME /data
EXPOSE 8787
# SETUP_SECRET (optional): when set, creating an account needs it in the X-Setup-Secret header.
CMD ["workerd", "serve", "config.capnp", "--experimental"]
