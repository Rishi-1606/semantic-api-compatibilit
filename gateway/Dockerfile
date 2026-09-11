# ──────────────────────────────────────────────────
# Dockerfile for Node.js API Gateway
# ──────────────────────────────────────────────────

FROM node:20-alpine

WORKDIR /app

# Install dependencies first (layer cache)
COPY package*.json ./
RUN npm ci --omit=dev

# Copy source
COPY src/ src/

EXPOSE 3001

CMD ["node", "src/index.js"]
