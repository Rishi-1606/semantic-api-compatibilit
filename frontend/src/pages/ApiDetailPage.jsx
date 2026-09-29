import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  getApi, getVersionsForApi, getEndpointsForVersion,
  analyzeEndpointSchema, runNlpOnEndpoint, getEndpointSchema,
} from '../api/apis.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import RegisterVersionForm from '../components/apis/RegisterVersionForm.jsx';
import RegisterEndpointForm from '../components/apis/RegisterEndpointForm.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';
import JsonBlock from '../components/common/JsonBlock.jsx';

/**
 * ApiDetailPage  /apis/:apiId
 * Shows API details, versions, endpoints, schema fields.
 */
export default function ApiDetailPage() {
  const { apiId }  = useParams();
  const navigate   = useNavigate();

  const [api,       setApi]       = useState(null);
  const [versions,  setVersions]  = useState([]);
  const [endpoints, setEndpoints] = useState({});   // versionId -> EndpointResponse[]
  const [schema,    setSchema]    = useState({});    // endpointId -> SchemaFieldResponse[]
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState(null);

  const [showVerForm,  setShowVerForm]  = useState(false);
  const [activeVer,    setActiveVer]    = useState(null); // versionId for add-endpoint form
  const [busyEp,       setBusyEp]       = useState({});   // endpointId -> 'analyze'|'nlp'|null

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [apiData, versionData] = await Promise.all([
        getApi(Number(apiId)),
        getVersionsForApi(Number(apiId)),
      ]);
      setApi(apiData);
      setVersions(versionData);
      // Load endpoints for each version
      const epMap = {};
      await Promise.all(versionData.map(async v => {
        try { epMap[v.id] = await getEndpointsForVersion(v.id); }
        catch { epMap[v.id] = []; }
      }));
      setEndpoints(epMap);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [apiId]);

  useEffect(() => { load(); }, [load]);

  const handleNewVersion = (v) => {
    setVersions(prev => [...prev, v]);
    setEndpoints(prev => ({ ...prev, [v.id]: [] }));
    setShowVerForm(false);
  };

  const handleNewEndpoint = (ep) => {
    setEndpoints(prev => ({
      ...prev,
      [ep.apiVersionId]: [...(prev[ep.apiVersionId] || []), ep],
    }));
    setActiveVer(null);
  };

  const handleAnalyze = async (epId) => {
    setBusyEp(prev => ({ ...prev, [epId]: 'analyze' }));
    try {
      await analyzeEndpointSchema(epId);
      await runNlpOnEndpoint(epId);
      const fields = await getEndpointSchema(epId);
      setSchema(prev => ({ ...prev, [epId]: fields }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyEp(prev => ({ ...prev, [epId]: null }));
    }
  };

  if (loading) return <div className="page"><p className="muted-text">Loading…</p></div>;
  if (error)   return <div className="page"><ErrorBanner message={error} /></div>;
  if (!api)    return <div className="page"><p className="muted-text">API not found.</p></div>;

  return (
    <div className="page">
      <PageHeader
        title={api.name}
        subtitle={api.description || 'No description'}
        actions={
          <button className="btn btn-secondary" onClick={() => navigate('/apis')}>? Back</button>
        }
      />

      <div className="section-header">
        <h2 className="section-title">Versions ({versions.length})</h2>
        <button className="btn btn-primary" id="add-version-btn" onClick={() => setShowVerForm(f => !f)}>
          {showVerForm ? '? Cancel' : '+ Add Version'}
        </button>
      </div>

      {showVerForm && (
        <div className="section">
          <RegisterVersionForm apiId={Number(apiId)} onSuccess={handleNewVersion} onCancel={() => setShowVerForm(false)} />
        </div>
      )}

      {versions.length === 0 ? (
        <EmptyState icon="??" title="No versions yet" message="Add the first version above." />
      ) : (
        versions.map(ver => (
          <section key={ver.id} className="section version-section">
            <div className="version-header">
              <div>
                <span className="version-label">{ver.version}</span>
                {ver.baseUrl && <span className="version-url"> — {ver.baseUrl}</span>}
              </div>
              <button
                className="btn btn-secondary btn-sm"
                id={`add-endpoint-btn-${ver.id}`}
                onClick={() => setActiveVer(activeVer === ver.id ? null : ver.id)}
              >
                {activeVer === ver.id ? '? Cancel' : '+ Add Endpoint'}
              </button>
            </div>

            {activeVer === ver.id && (
              <RegisterEndpointForm versionId={ver.id} onSuccess={handleNewEndpoint} onCancel={() => setActiveVer(null)} />
            )}

            {(endpoints[ver.id] || []).length === 0 ? (
              <p className="muted-text">No endpoints registered.</p>
            ) : (
              (endpoints[ver.id] || []).map(ep => (
                <div key={ep.id} className="endpoint-card">
                  <div className="endpoint-header">
                    <span className={`http-method method-${ep.httpMethod.toLowerCase()}`}>{ep.httpMethod}</span>
                    <code className="endpoint-path">{ep.path}</code>
                    <button
                      className="btn btn-secondary btn-sm"
                      id={`analyze-ep-${ep.id}`}
                      onClick={() => handleAnalyze(ep.id)}
                      disabled={busyEp[ep.id] === 'analyze'}
                    >
                      {busyEp[ep.id] ? '? Analyzing…' : '? Analyze Schema + NLP'}
                    </button>
                  </div>
                  {ep.description && <p className="muted-text">{ep.description}</p>}

                  {schema[ep.id] && schema[ep.id].length > 0 && (
                    <div className="schema-fields">
                      <span className="label">Extracted Fields ({schema[ep.id].length})</span>
                      <div className="table-wrap">
                        <table className="data-table">
                          <thead>
                            <tr><th>Field</th><th>Type</th><th>Schema</th><th>Required</th></tr>
                          </thead>
                          <tbody>
                            {schema[ep.id].map(f => (
                              <tr key={f.id} className="data-tr">
                                <td><code>{f.fieldPath}</code></td>
                                <td>{f.fieldType}</td>
                                <td>{f.schemaType}</td>
                                <td>{f.required ? '?' : '—'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                  {ep.requestSchema && (
                    <JsonBlock label="Request Schema" value={ep.requestSchema} />
                  )}
                </div>
              ))
            )}
          </section>
        ))
      )}
    </div>
  );
}
