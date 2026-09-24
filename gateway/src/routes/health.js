'use strict';

const express = require('express');
const router = express.Router();

/**
 * GET /health
 *
 * Gateway health-check endpoint.
 * Returns service name, version, backend URL, and timestamp.
 */
router.get('/', (req, res) => {
  const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

  res.json({
    status: 'UP',
    service: 'gateway',
    version: '1.0.0',
    phase: 'Phase 10 — Runtime Gateway',
    backendUrl: BACKEND_URL,
    timestamp: new Date().toISOString(),
  });
});

module.exports = router;
