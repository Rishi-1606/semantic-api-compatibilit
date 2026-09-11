# ─────────────────────────────────────────────────────
# Dockerfile for React Frontend
# Multi-stage: build with Node, serve with Nginx
# ─────────────────────────────────────────────────────

# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /app

# Accept build args for Vite env vars
ARG VITE_BACKEND_URL=http://localhost:8080
ARG VITE_GATEWAY_URL=http://localhost:3001
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
