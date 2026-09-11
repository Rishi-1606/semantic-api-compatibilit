import { useState, useEffect, useCallback } from 'react'
import { checkBackendHealth, checkGatewayHealth, BACKEND_URL, GATEWAY_URL } from './api/health'
import './App.css'

// ─── Small helper components ─────────────────────────────────────────────────

function StatusBadge({ ok, loading }) {
  if (loading) return <span className="badge badge-loading">Checking…</span>
  return ok
    ? <span className="badge badge-up">UP</span>
    : <span className="badge badge-down">DOWN</span>
}

function ServiceCard({ name, url, healthUrl, result, loading }) {
  return (
    <div className={`service-card ${result?.ok ? 'card-up' : loading ? 'card-loading' : 'card-down'}`}>
      <div className="card-header">
        <span className="service-icon">{result?.ok ? '✅' : loading ? '⏳' : '❌'}</span>
        <h2 className="service-name">{name}</h2>
        <StatusBadge ok={result?.ok} loading={loading} />
      </div>

      <div className="card-body">
        <div className="info-row">
          <span className="label">URL</span>
          <a href={url} target="_blank" rel="noreferrer" className="value link">{url}</a>
        </div>
        <div className="info-row">
          <span className="label">Health</span>
          <a href={healthUrl} target="_blank" rel="noreferrer" className="value link">{healthUrl}</a>
        </div>

        {result?.data && (
          <div className="response-block">
            <span className="label">Response</span>
            <pre className="json-pre">{JSON.stringify(result.data, null, 2)}</pre>
          </div>
        )}

        {result?.error && (
          <div className="error-block">
            <span className="label">Error</span>
            <span className="error-msg">{result.error}</span>
          </div>
        )}
      </div>
    </div>
  )
}

function PhaseCard({ phase, title, status }) {
  const cls = status === 'complete' ? 'phase-complete'
    : status === 'active' ? 'phase-active'
    : 'phase-pending'
  const icon = status === 'complete' ? '✅' : status === 'active' ? '🔄' : '⏳'
  return (
    <div className={`phase-item ${cls}`}>
      <span className="phase-icon">{icon}</span>
      <span className="phase-num">Phase {phase}</span>
      <span className="phase-title">{title}</span>
    </div>
  )
}

// ─── Main App ─────────────────────────────────────────────────────────────────

const PHASES = [
  { phase: 1,  title: 'Foundation — monorepo, services, health checks',  status: 'active' },
  { phase: 2,  title: 'Mock APIs V1 & V2',                                status: 'pending' },
  { phase: 3,  title: 'API Registry (persistence)',                        status: 'pending' },
  { phase: 4,  title: 'Schema Analyzer',                                   status: 'pending' },
  { phase: 5,  title: 'NLP Pipeline',                                      status: 'pending' },
  { phase: 6,  title: 'AI Semantic Matching',                              status: 'pending' },
  { phase: 7,  title: 'Compatibility Engine',                              status: 'pending' },
  { phase: 8,  title: 'Transformation Generator',                          status: 'pending' },
  { phase: 9,  title: 'Safety & Approval',                                 status: 'pending' },
  { phase: 10, title: 'Runtime Gateway',                                   status: 'pending' },
  { phase: 11, title: 'Response Transformation',                           status: 'pending' },
  { phase: 12, title: 'Frontend Dashboard',                                status: 'pending' },
  { phase: 13, title: 'Testing',                                           status: 'pending' },
]

export default function App() {
  const [backendResult, setBackendResult] = useState(null)
  const [gatewayResult, setGatewayResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [lastChecked, setLastChecked] = useState(null)

  const runHealthChecks = useCallback(async () => {
    setLoading(true)
    setBackendResult(null)
    setGatewayResult(null)

    const [backend, gateway] = await Promise.all([
      checkBackendHealth(),
      checkGatewayHealth(),
    ])

    setBackendResult(backend)
    setGatewayResult(gateway)
    setLastChecked(new Date().toLocaleTimeString())
    setLoading(false)
  }, [])

  // Auto-check on mount
  useEffect(() => {
    runHealthChecks()
  }, [runHealthChecks])

  const allUp = backendResult?.ok && gatewayResult?.ok
  const anyDown = (backendResult && !backendResult.ok) || (gatewayResult && !gatewayResult.ok)

  return (
    <div className="app">
      {/* ── Header ── */}
      <header className="app-header">
        <div className="header-content">
          <div className="header-title-group">
            <span className="header-icon">🔄</span>
            <div>
              <h1 className="header-title">Semantic API Compatibility</h1>
              <p className="header-subtitle">Automatic Transformation Framework</p>
            </div>
          </div>
          <div className="header-meta">
            <span className="phase-badge">Phase 1 — Foundation</span>
          </div>
        </div>
      </header>

      <main className="app-main">
        {/* ── System Status Banner ── */}
        {!loading && (backendResult || gatewayResult) && (
          <div className={`status-banner ${allUp ? 'banner-up' : anyDown ? 'banner-down' : 'banner-partial'}`}>
            {allUp
              ? '🟢 All services operational'
              : anyDown
              ? '🔴 One or more services are unavailable'
              : '🟡 Checking services…'}
            {lastChecked && <span className="last-checked"> · Last checked: {lastChecked}</span>}
          </div>
        )}

        {/* ── Service Health Cards ── */}
        <section className="section">
          <div className="section-header">
            <h2 className="section-title">Service Health</h2>
            <button
              className="refresh-btn"
              onClick={runHealthChecks}
              disabled={loading}
              id="refresh-health-btn"
            >
              {loading ? '⏳ Checking…' : '↻ Refresh'}
            </button>
          </div>

          <div className="cards-grid">
            <ServiceCard
              name="Spring Boot Backend"
              url={BACKEND_URL}
              healthUrl={`${BACKEND_URL}/api/health`}
              result={backendResult}
              loading={loading}
            />
            <ServiceCard
              name="Node.js Gateway"
              url={GATEWAY_URL}
              healthUrl={`${GATEWAY_URL}/health`}
              result={gatewayResult}
              loading={loading}
            />
            {/* Frontend is self — always up if you're seeing this */}
            <div className="service-card card-up">
              <div className="card-header">
                <span className="service-icon">✅</span>
                <h2 className="service-name">React Frontend</h2>
                <span className="badge badge-up">UP</span>
              </div>
              <div className="card-body">
                <div className="info-row">
                  <span className="label">URL</span>
                  <span className="value">http://localhost:5173</span>
                </div>
                <div className="info-row">
                  <span className="label">Note</span>
                  <span className="value">You're viewing it right now</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── Architecture Overview ── */}
        <section className="section">
          <h2 className="section-title">System Architecture</h2>
          <div className="arch-diagram">
            <div className="arch-box arch-frontend">
              <div className="arch-icon">⚛️</div>
              <div className="arch-label">React Dashboard</div>
              <div className="arch-port">:5173</div>
            </div>
            <div className="arch-arrow">↓ REST</div>
            <div className="arch-box arch-backend">
              <div className="arch-icon">☕</div>
              <div className="arch-label">Spring Boot Backend</div>
              <div className="arch-port">:8080</div>
            </div>
            <div className="arch-arrow">↓ JPA</div>
            <div className="arch-box arch-db">
              <div className="arch-icon">🐘</div>
              <div className="arch-label">PostgreSQL</div>
              <div className="arch-port">:5432</div>
            </div>
          </div>
          <div className="arch-separator">Runtime Traffic (Phase 10+)</div>
          <div className="arch-diagram">
            <div className="arch-box arch-client">
              <div className="arch-icon">📱</div>
              <div className="arch-label">V1 Client</div>
            </div>
            <div className="arch-arrow">→</div>
            <div className="arch-box arch-gateway">
              <div className="arch-icon">🔀</div>
              <div className="arch-label">Node.js Gateway</div>
              <div className="arch-port">:3001</div>
            </div>
            <div className="arch-arrow">→</div>
            <div className="arch-box arch-api">
              <div className="arch-icon">🎯</div>
              <div className="arch-label">Target API V2</div>
            </div>
          </div>
        </section>

        {/* ── Development Phases ── */}
        <section className="section">
          <h2 className="section-title">Development Phases</h2>
          <div className="phases-list">
            {PHASES.map(p => (
              <PhaseCard key={p.phase} {...p} />
            ))}
          </div>
        </section>

        {/* ── Quick Links ── */}
        <section className="section">
          <h2 className="section-title">Quick Links</h2>
          <div className="links-grid">
            <a href={`${BACKEND_URL}/api/health`} target="_blank" rel="noreferrer" className="link-card">
              <span className="link-icon">🏥</span>
              <span>Backend Health</span>
            </a>
            <a href={`${BACKEND_URL}/actuator/health`} target="_blank" rel="noreferrer" className="link-card">
              <span className="link-icon">📊</span>
              <span>Actuator Health</span>
            </a>
            <a href={`${BACKEND_URL}/h2-console`} target="_blank" rel="noreferrer" className="link-card">
              <span className="link-icon">🗄️</span>
              <span>H2 Console (dev)</span>
            </a>
            <a href={`${GATEWAY_URL}/health`} target="_blank" rel="noreferrer" className="link-card">
              <span className="link-icon">🔀</span>
              <span>Gateway Health</span>
            </a>
          </div>
        </section>
      </main>

      <footer className="app-footer">
        <p>Semantic API Compatibility &amp; Automatic Transformation Framework · Phase 1 Foundation</p>
      </footer>
    </div>
  )
}
