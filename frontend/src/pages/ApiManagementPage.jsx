import { useState, useEffect, useCallback } from 'react';
import { listApis } from '../api/apis.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import ApiCard from '../components/apis/ApiCard.jsx';
import RegisterApiForm from '../components/apis/RegisterApiForm.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';

/**
 * ApiManagementPage  /apis
 * Lists all registered APIs and allows registering new ones.
 */
export default function ApiManagementPage() {
  const [apis,       setApis]       = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState(null);
  const [showForm,   setShowForm]   = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try { setApis(await listApis()); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleNewApi = (api) => {
    setApis(prev => [...prev, api]);
    setShowForm(false);
  };

  return (
    <div className="page">
      <PageHeader
        title="API Management"
        subtitle="Register and manage API versions and endpoints"
        actions={
          <button
            id="register-api-btn"
            className="btn btn-primary"
            onClick={() => setShowForm(f => !f)}
          >
            {showForm ? '? Cancel' : '+ Register API'}
          </button>
        }
      />

      {showForm && (
        <div className="section">
          <RegisterApiForm onSuccess={handleNewApi} onCancel={() => setShowForm(false)} />
        </div>
      )}

      <ErrorBanner message={error} onDismiss={() => setError(null)} />

      {loading ? (
        <p className="muted-text">Loading APIs…</p>
      ) : apis.length === 0 ? (
        <EmptyState icon="??" title="No APIs registered yet"
          message="Click 'Register API' to add your first API." />
      ) : (
        <div className="section">
          <div className="cards-grid">
            {apis.map(api => <ApiCard key={api.id} api={api} />)}
          </div>
        </div>
      )}
    </div>
  );
}
