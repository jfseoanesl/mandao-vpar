# --- Compilación de la PWA ---
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# --- Servidor web: Caddy sirve la PWA y hace de proxy a /api y /ws (HTTPS automático en producción) ---
FROM caddy:2-alpine
COPY --from=build /src/dist/frontend/browser /srv
COPY Caddyfile /etc/caddy/Caddyfile
