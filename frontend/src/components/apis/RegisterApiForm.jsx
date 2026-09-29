import { useState } from 'react';
import ErrorBanner from '../common/ErrorBanner.jsx';

/**
 * RegisterApiForm
 * Inline form to create a new API.
 * On submit calls onSuccess(newApi) with the created ApiResponse.
 *
 * CreateApiRequest body: { name, description }
 */
export default function RegisterApiForm({ onSuccess, onCancel }) {
  const [name, setName]   = useState('');
  const [desc, setDesc]   = useState('');
  const [busy, setBusy]   = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) { setError('API name is required.'); return; }
    setBusy(true);
    try {
      const { createApi } = await import('../../api/apis.js');
      const api = await createApi({ name: name.trim(), description: desc.trim() || null });
      setName(''); setDesc('');
      onSuccess(api);
    } catch (err) {
      setError(err.message || 'Failed to register API');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={handleSubmit} noValidate>
      <h3 className="form-title">Register New API</h3>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <div className="form-field">
        <label htmlFor="api-name" className="label">Name *</label>
        <input
          id="api-name"
          className="form-input"
          type="text"
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="e.g. Customer API"
          maxLength={255}
          required
        />
      </div>
      <div className="form-field">
        <label htmlFor="api-desc" className="label">Description</label>
        <textarea
          id="api-desc"
          className="form-textarea"
          rows={3}
          value={desc}
          onChange={e => setDesc(e.target.value)}
          placeholder="Optional description…"
          maxLength={2000}
        />
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Registering…' : 'Register API'}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
