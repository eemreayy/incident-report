# Multi-stage: Node builds, nginx serves. The runtime image carries no Node and
# no source, only static files and one config.

FROM node:24-alpine AS build

WORKDIR /build

# Dependencies first, in their own layer. Source changes far more often than
# package-lock.json, so this install stays cached across ordinary edits - the
# same layering idea the backend image uses.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# `npm run build` type-checks before bundling, so a type error fails the image
# build rather than shipping. Tests are not run here: the image build is not the
# quality gate, `npm run verify` is - the same split the backend Dockerfile makes
# by packaging with -DskipTests.
RUN npm run build


# nginx-unprivileged rather than plain nginx: the process runs as a non-root
# user, matching the backend image which drops to uid 1001.
FROM nginxinc/nginx-unprivileged:alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

# Checks the served application, not the proxied API - the frontend being
# healthy and the backend being reachable are two different statements, and
# compose already waits for the backend on its own health check.
#
# 127.0.0.1, not localhost: /etc/hosts maps localhost to ::1 as well, wget tries
# that first, and nginx listens on IPv4 only - so the check failed against a
# server that was serving correctly the whole time.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:3000/ || exit 1
