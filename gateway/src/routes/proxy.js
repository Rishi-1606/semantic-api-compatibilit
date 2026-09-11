'use strict';

const express = require('express');
const router = express.Router();

/**
 * Proxy router — Phase 10 implementation.
 *
 * In Phase 1 this is a stub that returns 501 Not Implemented.
 *
 * Full implementation (Phase 10) will:
 *   1. Parse :endpoint and :version from the URL
 *   2. Retrieve approved transformation rules from the Spring Boot backend
 *   3. Transform the incoming V1 request to the target version's format
 *   4. Forward the transformed request to the target API
 *   5. Transform the response back to the source version's format
 *   6. Return the transformed response to the client
 *
 * Example future request:
 *   POST /proxy/customer/v1
 *   Body: { "name": "Rishi", "mobile": "9876543210" }
 *
 * Future response after transformation to V2:
 *   { "fullName": "Rishi", "phoneNumber": "9876543210" }
 */
router.all('/:endpoint/:version', (req, res) => {
  const { endpoint, version } = req.params;

  console.log(`[Gateway] Proxy request received: ${req.method} /${endpoint}/${version}`);

  res.status(501).json({
    status: 'NOT_IMPLEMENTED',
    message: 'Proxy transformation engine not yet implemented — coming in Phase 10',
    phase: '10',
    request: {
      method: req.method,
      endpoint,
      version,
      body: req.body,
    },
    timestamp: new Date().toISOString(),
  });
});

module.exports = router;
