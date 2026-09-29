import { useState } from 'react';
import ErrorBanner from '../common/ErrorBanner.jsx';

/**
 * RegisterVersionForm
 * Inline form to add a new version to an existing API.
 *
 * CreateApiVersionRequest body: { apiId, version, baseUrl, description }
 */
export default function RegisterVersionForm({ apiId, onSuccess, onCancel }) {
  const [version, setVersion] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [desc,    setDesc]    = useState('');
  const [busy,    setBusy]    = useState(false);
  const [error,   setError]   = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!version.trim()) { setError('Version label is required.'); return; }
    setBusy(true);
    try {
      const { createVersion } = await import('../../api/apis.js');
      const v = await createVersion({
        apiId,
        version: version.trim(),
        baseUrl: baseUrl.trim() || null,
        description: desc.trim() || null,
      });
      setVersion(''); setBaseUrl(''); setDesc('');
      onSuccess(v);
    } catch (err) {
      setError(err.message || 'Failed to register version');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={handleSubmit} noValidate>
      <h3 className="form-title">Add Version</h3>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <div className="form-field">
        <label htmlFor="ver-label" className="label">Version Label *</label>
        <input id="ver-label" className="form-input" type="text" value={version}
          onChange={e => setVersion(e.target.value)} placeholder="e.g. V1" maxLength={50} required />
      </div>
      <div className="form-field">
        <label htmlFor="ver-url" className="label">Base URL</label>
        <input id="ver-url" className="form-input" type="text" value={baseUrl}
          onChange={e => setBaseUrl(e.target.value)} placeholder="e.g. http://localhost:8081" maxLength={500} />
      </div>
      <div className="form-field">
        <label htmlFor="ver-desc" className="label">Description</label>
        <textarea id="ver-desc" className="form-textarea" rows={2} value={desc}
          onChange={e => setDesc(e.target.value)} placeholder="Optional…" maxLength={2000} />
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Adding…' : 'Add Version'}
        </button>
        {onCancel && <button type="button" className="btn btn-secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
