import { useState } from 'react';
import ErrorBanner from '../common/ErrorBanner.jsx';

const HTTP_METHODS = ['GET','POST','PUT','PATCH','DELETE'];

/**
 * RegisterEndpointForm
 * Inline form to add an endpoint to a specific API version.
 *
 * CreateEndpointRequest body: { apiVersionId, httpMethod, path, description, requestSchema, responseSchema }
 */
export default function RegisterEndpointForm({ versionId, onSuccess, onCancel }) {
  const [method,   setMethod]   = useState('POST');
  const [path,     setPath]     = useState('');
  const [desc,     setDesc]     = useState('');
  const [reqSch,   setReqSch]   = useState('');
  const [respSch,  setRespSch]  = useState('');
  const [busy,     setBusy]     = useState(false);
  const [error,    setError]    = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!path.trim()) { setError('Path is required.'); return; }
    setBusy(true);
    try {
      const { createEndpoint } = await import('../../api/apis.js');
      const ep = await createEndpoint({
        apiVersionId: versionId,
        httpMethod: method,
        path: path.trim(),
        description: desc.trim() || null,
        requestSchema: reqSch.trim() || null,
        responseSchema: respSch.trim() || null,
      });
      setPath(''); setDesc(''); setReqSch(''); setRespSch('');
      onSuccess(ep);
    } catch (err) {
      setError(err.message || 'Failed to register endpoint');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={handleSubmit} noValidate>
      <h3 className="form-title">Add Endpoint</h3>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <div className="form-row">
        <div className="form-field form-field--sm">
          <label htmlFor="ep-method" className="label">Method *</label>
          <select id="ep-method" className="form-select" value={method} onChange={e => setMethod(e.target.value)}>
            {HTTP_METHODS.map(m => <option key={m}>{m}</option>)}
          </select>
        </div>
        <div className="form-field form-field--grow">
          <label htmlFor="ep-path" className="label">Path *</label>
          <input id="ep-path" className="form-input" type="text" value={path}
            onChange={e => setPath(e.target.value)} placeholder="/v1/customer" maxLength={500} required />
        </div>
      </div>
      <div className="form-field">
        <label htmlFor="ep-desc" className="label">Description</label>
        <textarea id="ep-desc" className="form-textarea" rows={2} value={desc}
          onChange={e => setDesc(e.target.value)} placeholder="Optional…" maxLength={2000} />
      </div>
      <div className="form-field">
        <label htmlFor="ep-req" className="label">Request Schema (JSON)</label>
        <textarea id="ep-req" className="form-textarea form-textarea--mono" rows={4} value={reqSch}
          onChange={e => setReqSch(e.target.value)} placeholder='{"name":"string","mobile":"string"}' />
      </div>
      <div className="form-field">
        <label htmlFor="ep-resp" className="label">Response Schema (JSON)</label>
        <textarea id="ep-resp" className="form-textarea form-textarea--mono" rows={4} value={respSch}
          onChange={e => setRespSch(e.target.value)} placeholder='{"id":"integer","name":"string"}' />
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Adding…' : 'Add Endpoint'}
        </button>
        {onCancel && <button type="button" className="btn btn-secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
