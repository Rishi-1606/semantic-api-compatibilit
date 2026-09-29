import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { checkBackendHealth, checkGatewayHealth, BACKEND_URL, GATEWAY_URL } from '../api/health.js';
import { listApis, getVersionsForApi } from '../api/apis.js';
import StatusBadge from '../components/common/StatusBadge.jsx';
import PageHeader from '../components/layout/PageHeader.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';

/**
 * DashboardPage  /
 * Overview of service health + registered APIs + summary stats.
 */
export default function DashboardPage() {
  const navigate = useNavigate();

  // Service health state
  const [backendResult,  setBackendResult]  = useState(null);
  const [gatewayResult,  setGatewayResult]  = useState(null);
  const [healthLoading,  setHealthLoading]  = useState(false);
  const [lastChecked,    setLastChecked]    = useState(null);

  // API registry state
  const [apis,        setApis]        = useState([]);
  const [versionsMap, setVersionsMap] = useState({});  // apiId -> ApiVersionResponse[]
  const [apiError,    setApiError]    = useState(null);

  // -- Health checks ------------------------------------------

  const runHealthChecks = useCallback(async () => {
    setHealthLoading(true);
    const [backend, gateway] = await Promise.all([
      checkBackendHealth(),
      checkGatewayHealth(),
    ]);
    setBackendResult(backend);
    setGatewayResult(gateway);
    setLastChecked(new Date().toLocaleTimeString());
    setHealthLoading(false);
  }, []);

  useEffect(() => { runHealthChecks(); }, [runHealthChecks]);

  // -- API list -----------------------------------------------

  useEffect(() => {
    (async () => {
      try {
        const data = await listApis();
        setApis(data);
        // Load versions for each API for the summary stat
        const vmap = {};
        await Promise.all(data.map(async api => {
          try {
            vmap[api.id] = await getVersionsForApi(api.id);
          } catch { vmap[api.id] = []; }
        }));
        setVersionsMap(vmap);
      } catch (err) {
        setApiError(err.message);
      }
    })();
  }, []);

  const allUp  = backendResult?.ok && gatewayResult?.ok;
  const anyDown = (backendResult && !backendResult.ok) || (gatewayResult && !gatewayResult.ok);

  const totalVersions = Object.values(versionsMap).reduce((s, v) => s + v.length, 0);

  return (
    <div className="page">
      <PageHeader
        title="Dashboard"
        subtitle="System overview and service health"
        actions={
          <button id="dashboard-refresh-btn" className="btn btn-secondary" onClick={runHealthChecks} disabled={healthLoading}>
            {healthLoading ? '? Checking…' : '? Refresh'}
          </button>
        }
      />

      {/* -- Status banner -- */}
      {!healthLoading && (backendResult || gatewayResult) && (
        <div className={`status-banner ${allUp ? 'banner-up' : anyDown ? 'banner-down' : 'banner-partial'}`}>
          {allUp ? '?? All services operational' : anyDown ? '?? One or more services unavailable' : '?? Checking…'}
          {lastChecked && <span className="last-checked"> · Last checked: {lastChecked}</span>}
        </div>
      )}

      {/* -- Summary stats -- */}
      <div className="stats-row">
        <div className="stat-card"><span className="stat-num">{apis.length}</span><span className="stat-label">APIs</span></div>
        <div className="stat-card"><span className="stat-num">{totalVersions}</span><span className="stat-label">Versions</span></div>
        <div className="stat-card" onClick={() => navigate('/analysis')} style={{ cursor: 'pointer' }}>
          <span className="stat-num">?</span><span className="stat-label">Analysis</span>
        </div>
        <div className="stat-card" onClick={() => navigate('/rules')} style={{ cursor: 'pointer' }}>
          <span className="stat-num">?</span><span className="stat-label">Rules</span>
        </div>
      </div>

      {/* -- Service health cards -- */}
      <section className="section">
        <h2 className="section-title">Service Health</h2>
        <div className="cards-grid">
          <ServiceCard name="Spring Boot Backend" url={BACKEND_URL}
            healthUrl={`${BACKEND_URL}/api/health`} result={backendResult} loading={healthLoading} />
          <ServiceCard name="Node.js Gateway"    url={GATEWAY_URL}
            healthUrl={`${GATEWAY_URL}/health`}   result={gatewayResult} loading={healthLoading} />
          <div className="service-card card-up">
            <div className="card-header">
              <span className="service-icon">?</span>
              <h2 className="service-name">React Frontend</h2>
              <span className="badge badge-up">UP</span>
            </div>
            <div className="card-body">
              <div className="info-row"><span className="label">URL</span><span className="value">http://localhost:5173</span></div>
              <div className="info-row"><span className="label">Note</span><span className="value">You are viewing it right now</span></div>
            </div>
          </div>
        </div>
      </section>

      {/* -- Registered APIs -- */}
      <section className="section">
        <div className="section-header">
          <h2 className="section-title">Registered APIs</h2>
          <button className="btn btn-primary" onClick={() => navigate('/apis')}>Manage APIs</button>
        </div>
        <ErrorBanner message={apiError} />
        {apis.length === 0 && !apiError ? (
          <p className="muted-text">No APIs registered yet. <button className="link-btn" onClick={() => navigate('/apis')}>Register one ?</button></p>
        ) : (
          <div className="cards-grid">
            {apis.map(api => (
              <div key={api.id} className="api-card" onClick={() => navigate(`/apis/${api.id}`)} role="button" tabIndex={0}>
                <div className="api-card-header">
                  <span className="api-card-icon">??</span>
                  <h3 className="api-card-name">{api.name}</h3>
                  <span className="api-card-badge">{api.versionCount} version{api.versionCount !== 1 ? 's' : ''}</span>
                </div>
                {api.description && <p className="api-card-desc">{api.description}</p>}
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function ServiceCard({ name, url, healthUrl, result, loading }) {
  return (
    <div className={`service-card ${result?.ok ? 'card-up' : loading ? 'card-loading' : 'card-down'}`}>
      <div className="card-header">
        <span className="service-icon">{result?.ok ? '?' : loading ? '?' : '?'}</span>
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
  );
}
