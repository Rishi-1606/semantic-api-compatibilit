'use strict';

require('dotenv').config();

const express = require('express');
const cors = require('cors');
const morgan = require('morgan');

const healthRouter = require('./routes/health');
const proxyRouter = require('./routes/proxy');

const app = express();

// ─────────────────────────────────────────────────────────
// Middleware
// ─────────────────────────────────────────────────────────

// CORS — allow the React frontend and any external clients
app.use(cors({
  origin: [
    'http://localhost:5173',   // React dev server (Vite)
    'http://localhost:3000',   // React dev server (CRA fallback)
    'http://localhost:8080',   // Spring Boot backend
  ],
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Api-Version'],
  credentials: true,
}));

// HTTP request logging
app.use(morgan('combined'));

// JSON body parsing
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// ─────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────
app.use('/health', healthRouter);
app.use('/proxy', proxyRouter);

// ─────────────────────────────────────────────────────────
// 404 handler
// ─────────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).json({
    status: 'ERROR',
    message: `Route ${req.method} ${req.path} not found`,
    timestamp: new Date().toISOString(),
  });
});

// ─────────────────────────────────────────────────────────
// Global error handler
// ─────────────────────────────────────────────────────────
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error('[Gateway] Unhandled error:', err);
  res.status(err.status || 500).json({
    status: 'ERROR',
    message: err.message || 'Internal gateway error',
    timestamp: new Date().toISOString(),
  });
});

// ─────────────────────────────────────────────────────────
// Start server
// ─────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3001;
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

app.listen(PORT, () => {
  console.log(`[Gateway] ✅  Node.js API Gateway running on port ${PORT}`);
  console.log(`[Gateway]    Backend URL: ${BACKEND_URL}`);
  console.log(`[Gateway]    Health:      http://localhost:${PORT}/health`);
  console.log(`[Gateway]    Proxy:       http://localhost:${PORT}/proxy/:endpoint/:version`);
});

module.exports = app; // Export for testing
