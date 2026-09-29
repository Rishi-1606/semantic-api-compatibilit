import { useState } from 'react';
import ErrorBanner from '../common/ErrorBanner.jsx';

/**
 * RunAnalysisForm
 * Form to start a compatibility analysis run.
 * Loads all API versions, lets the user pick source + target,
 * then calls Phase 6 analyze + Phase 7 report in sequence.
 *
 * @param {Array}    allVersions  ApiVersionResponse[] flat list from all APIs
 * @param {function} onSuccess(reportId)  Called with the new report ID
 */
export default function RunAnalysisForm({ allVersions, onSuccess }) {
  const [srcId,  setSrcId]  = useState('');
  const [tgtId,  setTgtId]  = useState('');
  const [busy,   setBusy]   = useState(false);
  const [step,   setStep]   = useState('');
  const [error,  setError]  = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!srcId || !tgtId) { setError('Select both source and target versions.'); return; }
    if (srcId === tgtId)  { setError('Source and target must be different versions.'); return; }

    setBusy(true);
    try {
      const { runSemanticAnalysis, runCompatibilityReport } = await import('../../api/compatibility.js');

      setStep('Running Phase 6 semantic matching…');
      const session = await runSemanticAnalysis(Number(srcId), Number(tgtId));

      setStep('Running Phase 7 compatibility engine…');
      const report  = await runCompatibilityReport(session.id);

      setStep('');
      onSuccess(report.id);
    } catch (err) {
      setError(err.message || 'Analysis failed');
    } finally {
      setBusy(false);
      setStep('');
    }
  };

  const versionOptions = (allVersions || []).map(v => (
    <option key={v.id} value={v.id}>
      {v.apiName} — {v.version}{v.baseUrl ? ` (${v.baseUrl})` : ''}
    </option>
  ));

  return (
    <form className="inline-form" onSubmit={handleSubmit} noValidate>
      <h3 className="form-title">Run Compatibility Analysis</h3>
      <ErrorBanner message={error} onDismiss={() => setError(null)} />
      <div className="form-row">
        <div className="form-field form-field--grow">
          <label htmlFor="src-version" className="label">Source Version *</label>
          <select id="src-version" className="form-select" value={srcId}
            onChange={e => setSrcId(e.target.value)} required>
            <option value="">— Select source —</option>
            {versionOptions}
          </select>
        </div>
        <div className="form-field form-field--grow">
          <label htmlFor="tgt-version" className="label">Target Version *</label>
          <select id="tgt-version" className="form-select" value={tgtId}
            onChange={e => setTgtId(e.target.value)} required>
            <option value="">— Select target —</option>
            {versionOptions}
          </select>
        </div>
      </div>
      {step && <p className="step-indicator">? {step}</p>}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy} id="run-analysis-btn">
          {busy ? 'Analyzing…' : '? Run Analysis'}
        </button>
      </div>
    </form>
  );
}
