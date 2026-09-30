# Self-hosted Fijerena sync server — see docs/plans/20260929_live-sync-plan.md → Self-hosting.
# workerd is pinned (package-lock.json): its local Durable Object storage is experimental, so back up
# /data before upgrading the image.
FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:24-bookworm-slim
WORKDIR /app
COPY --from=build /app/node_modules/workerd ./node_modules/workerd
COPY --from=build /app/node_modules/@cloudflare ./node_modules/@cloudflare
COPY workerd/config.capnp ./config.capnp
COPY --from=build /app/dist/worker.js ./worker.js
VOLUME /data
EXPOSE 8787
# SETUP_SECRET (optional): when set, creating an account needs it in the X-Setup-Secret header.
CMD ["node_modules/workerd/bin/workerd", "serve", "config.capnp", "--experimental"]
