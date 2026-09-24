#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Phase 10 end-to-end demo script.
  Assumes all services are running:
    Spring Boot (backend)  → http://localhost:8080
    Node.js Gateway        → http://localhost:3001
    Mock API V1            → http://localhost:8081
    Mock API V2            → http://localhost:8082
#>

$GW   = "http://localhost:3001"
$V1   = "http://localhost:8081"
$V2   = "http://localhost:8082"
$SB   = "http://localhost:8080"

function Invoke-Demo { param([string]$Label, [string]$Cmd)
  Write-Host ""
  Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
  Write-Host "  $Label" -ForegroundColor Yellow
  Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Cyan
  Invoke-Expression $Cmd
}

# ── Step 1: Gateway health ────────────────────────────────────────────
Invoke-Demo "Step 1: Gateway health (should show Phase 10)" `
  "curl -s $GW/health | ConvertFrom-Json | Format-List"

# ── Step 2: Prove V2 rejects untransformed V1 request ────────────────
Invoke-Demo "Step 2: V2 rejects raw V1 body (expected: 400)" `
  "curl -s -X POST $V2/v2/customer -H 'Content-Type: application/json' -d '{""name"":""Rishi"",""mobile"":""9876543210"",""email"":""rishi@example.com""}' | ConvertFrom-Json | Format-List"

# ── Step 3: V1 accepts V1 request directly ───────────────────────────
Invoke-Demo "Step 3: V1 accepts raw V1 body directly (expected: 201)" `
  "curl -s -X POST $V1/v1/customer -H 'Content-Type: application/json' -d '{""name"":""Rishi"",""mobile"":""9876543210"",""email"":""rishi@example.com""}' | ConvertFrom-Json | Format-List"

# ── Step 4: Check APPROVED rules exist in backend ────────────────────
Invoke-Demo "Step 4: Confirm APPROVED rules in backend (should list rules)" `
  "curl -s '$SB/api/transformation-rules/endpoint-pair?srcEndpointId=1&tgtEndpointId=2&ruleStatus=APPROVED' | ConvertFrom-Json | Format-Table id,operation,sourcePath,targetPath,ruleStatus -AutoSize"

# ── Step 5: Send V1 request through gateway ───────────────────────────
Invoke-Demo "Step 5: V1 request through Gateway (expected: 201, V1 response format)" `
  "curl -s -X POST $GW/proxy/customer/v1 -H 'Content-Type: application/json' -d '{""name"":""Rishi"",""mobile"":""9876543210"",""email"":""rishi@example.com""}' | ConvertFrom-Json | Format-List"

# ── Step 6: Second request — should use cache ─────────────────────────
Invoke-Demo "Step 6: Second V1 request (cache HIT — no backend calls expected in logs)" `
  "curl -s -X POST $GW/proxy/customer/v1 -H 'Content-Type: application/json' -d '{""name"":""TestUser"",""mobile"":""1234567890"",""email"":""test@example.com""}' | ConvertFrom-Json | Format-List"

# ── Step 7: Security — reject all rules, gateway returns 502 ──────────
Invoke-Demo "Step 7: (Manual) Reject all rules in backend, then retry gateway → expect 502 NO_RULES" `
  "Write-Host 'Reject rules manually in backend UI, wait for cache TTL (60s), then retry Step 5.' -ForegroundColor Magenta"

Write-Host ""
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  Phase 10 Demo Complete" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════" -ForegroundColor Green
