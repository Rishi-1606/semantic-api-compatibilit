import { useState, useEffect } from 'react';
import { listApis, getVersionsForApi, getEndpointsForVersion } from '../api/apis.js';
import { getRulesForReport } from '../api/rules.js';
import { getReportsForVersion } from '../api/compatibility.js';
import { testTransformation } from '../api/tester.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import JsonBlock from '../components/common/JsonBlock.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';
import EmptyState from '../components/common/EmptyState.jsx';

/**
 * TesterPage  /tester
 * Developer tool to preview how a single transformation rule transforms sample JSON.
 * Calls POST /api/transform/test — no external API involved.
 *
 * This is a Phase 12 frontend preview facility. The Phase 10 Node.js gateway
 * remains the authoritative runtime transformation engine.
 */
export default function TesterPage() {
  const [allRules,   setAllRules]   = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [inputJson,  setInputJson]  = useState('{\n  "name": "Rishi",\n  "mobile": "9876543210"\n}');
  const [result,     setResult]     = useState(null);
  const [busy,       setBusy]       = useState(false);
  const [error,      setError]      = useState(null);
  const [loading,    setLoading]    = useState(true);

  // -- Load all rules (from all reports) ------------------------

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const apis = await listApis();
        const versionsArrays = await Promise.all(apis.map(a => getVersionsForApi(a.id).catch(() => [])));
        const reportMap = {};
        await Promise.all(versionsArrays.flat().map(async v => {
          try {
            const vReports = await getReportsForVersion(v.id);
            vReports.forEach(r => { reportMap[r.id] = r; });
          } catch { /* no reports */ }
        }));
        const rules = [];
        await Promise.all(Object.keys(reportMap).map(async rid => {
          try {
            const ruleList = await getRulesForReport(Number(rid));
            rules.push(...ruleList);
          } catch { /* no rules */ }
        }));
        // Deduplicate: keep only APPROVED rules, one per unique sourcePath+targetPath+operation
        // (highest confidence wins when there are duplicates across multiple reports)
        const approvedOnly = rules.filter(r => r.ruleStatus === 'APPROVED');
        const bestByKey = new Map();
        approvedOnly.forEach(r => {
          const key = `${r.sourcePath}|${r.targetPath}|${r.operation}`;
          const existing = bestByKey.get(key);
          if (!existing || r.confidence > existing.confidence) {
            bestByKey.set(key, r);
          }
        });
        setAllRules([...bestByKey.values()]);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleTest = async (e) => {
    e.preventDefault();
    if (!selectedId) { setError('Select a rule first.'); return; }
    setError(null);
    setBusy(true);
    setResult(null);
    try {
      const res = await testTransformation(Number(selectedId), inputJson);
      setResult(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const selectedRule = allRules.find(r => String(r.id) === String(selectedId));

  return (
    <div className="page">
      <PageHeader
        title="Transformation Tester"
        subtitle="Preview how a rule transforms sample JSON (no external API call)"
      />

      <div className="section">
        <p className="muted-text">
          This tool applies a single rule's operation in-process and shows the result.
          For end-to-end runtime testing, use the{' '}
          <a href="http://localhost:3001" target="_blank" rel="noreferrer" className="link">
            Node.js Gateway (port 3001)
          </a>.
        </p>
      </div>

      <form className="tester-layout" onSubmit={handleTest} noValidate>
        {/* -- Left panel -- */}
        <div className="tester-panel">
          <div className="form-field">
            <label htmlFor="rule-selector" className="label">Select Rule</label>
            <select
              id="rule-selector"
              className="form-select"
              value={selectedId}
              onChange={e => { setSelectedId(e.target.value); setResult(null); }}
              disabled={loading}
            >
              <option value="">— {loading ? 'Loading rules…' : 'Select a rule'} —</option>
              {allRules.map(r => (
                <option key={r.id} value={r.id}>
                  #{r.id} · {r.sourcePath} ? {r.targetPath} · {r.operation} [{r.ruleStatus}]
                </option>
              ))}
            </select>
          </div>

          {selectedRule && (
            <div className="rule-preview-info">
              <span className="label">Operation:</span> {selectedRule.operation} &nbsp;
              <span className="label">Confidence:</span> {Math.round(selectedRule.confidence * 100)}% &nbsp;
              <span className="label">Status:</span> {selectedRule.ruleStatus}
            </div>
          )}

          <div className="form-field">
            <label htmlFor="input-json" className="label">Input JSON</label>
            <textarea
              id="input-json"
              className="form-textarea form-textarea--mono form-textarea--lg"
              rows={12}
              value={inputJson}
              onChange={e => { setInputJson(e.target.value); setResult(null); }}
              placeholder='{"name": "Rishi", "mobile": "9876543210"}'
              spellCheck={false}
            />
          </div>

          <ErrorBanner message={error} onDismiss={() => setError(null)} />

          <div className="form-actions">
            <button
              type="submit"
              id="run-test-btn"
              className="btn btn-primary"
              disabled={busy || !selectedId}
            >
              {busy ? '? Running…' : '? Run Test'}
            </button>
          </div>
        </div>

        {/* -- Right panel -- */}
        <div className="tester-panel">
          {!result ? (
            <EmptyState icon="??" title="Output will appear here"
              message="Select a rule, provide input JSON, and click Run Test." />
          ) : (
            <>
              <div className={`tester-result-header ${result.success ? 'result-success' : 'result-failure'}`}>
                {result.success ? '? Transformation Succeeded' : '?? Transformation Note'}
              </div>
              {result.errorMessage && (
                <div className="tester-error-msg">{result.errorMessage}</div>
              )}
              <JsonBlock label="Input" value={result.inputJson} />
              {result.outputJson && (
                <JsonBlock label="Output" value={result.outputJson} />
              )}
            </>
          )}
        </div>
      </form>
    </div>
  );
}
