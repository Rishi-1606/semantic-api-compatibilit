import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listApis, getVersionsForApi } from '../api/apis.js';
import { getReportsForVersion } from '../api/compatibility.js';
import { getRulesForReport, approveRule, rejectRule, autoApproveForReport } from '../api/rules.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import RuleRow from '../components/rules/RuleRow.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';

/**
 * RulesPage  /rules
 * Browse transformation rules for a report, approve or reject them.
 *
 * Supports ?reportId=<id> query param for direct linking from ReportDetailPage.
 */
export default function RulesPage() {
  const [searchParams] = useSearchParams();

  const [allReports,   setAllReports]   = useState([]);
  const [selectedId,   setSelectedId]   = useState(searchParams.get('reportId') || '');
  const [rules,        setRules]        = useState([]);
  const [loading,      setLoading]      = useState(false);
  const [reportsLoading, setReportsLoading] = useState(true);
  const [error,        setError]        = useState(null);
  const [autoBusy,     setAutoBusy]     = useState(false);
  const [autoResult,   setAutoResult]   = useState(null);

  // -- Load all reports for the selector ------------------------

  useEffect(() => {
    (async () => {
      setReportsLoading(true);
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
        setAllReports(Object.values(reportMap).sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)));
      } catch (err) {
        setError(err.message);
      } finally {
        setReportsLoading(false);
      }
    })();
  }, []);

  // -- Load rules for selected report ---------------------------

  const loadRules = useCallback(async (id) => {
    if (!id) return;
    setLoading(true);
    setError(null);
    setAutoResult(null);
    try {
      const data = await getRulesForReport(Number(id));
      setRules(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadRules(selectedId); }, [selectedId, loadRules]);

  // -- Approve / Reject -----------------------------------------

  const handleApprove = async (ruleId, reason) => {
    try {
      const updated = await approveRule(ruleId, reason);
      setRules(prev => prev.map(r => r.id === ruleId ? updated : r));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleReject = async (ruleId, reason) => {
    try {
      const updated = await rejectRule(ruleId, reason);
      setRules(prev => prev.map(r => r.id === ruleId ? updated : r));
    } catch (err) {
      setError(err.message);
    }
  };

  // -- Auto-approve all -----------------------------------------

  const handleAutoApprove = async () => {
    setAutoBusy(true);
    setError(null);
    try {
      const result = await autoApproveForReport(Number(selectedId));
      setAutoResult(result);
      // Refresh rules list to reflect new statuses
      const data = await getRulesForReport(Number(selectedId));
      setRules(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setAutoBusy(false);
    }
  };

  // -- Stats -----------------------------------------------------

  const counts = rules.reduce((acc, r) => {
    acc[r.ruleStatus] = (acc[r.ruleStatus] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="page">
      <PageHeader
        title="Transformation Rules"
        subtitle="Review, approve, and reject generated transformation rules"
      />

      {/* -- Report selector -- */}
      <div className="section">
        <label htmlFor="report-selector" className="label">Select Report</label>
        <select
          id="report-selector"
          className="form-select"
          value={selectedId}
          onChange={e => { setSelectedId(e.target.value); setRules([]); setAutoResult(null); }}
          disabled={reportsLoading}
        >
          <option value="">— {reportsLoading ? 'Loading reports…' : 'Select a report'} —</option>
          {allReports.map(r => (
            <option key={r.id} value={r.id}>
              #{r.id} · {r.sourceVersionName} ? {r.targetVersionName} ·{' '}
              {r.compatibilityStatus?.replace(/_/g, ' ')} · {new Date(r.createdAt).toLocaleDateString()}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner message={error} onDismiss={() => setError(null)} />

      {selectedId && (
        <>
          {/* -- Stats + auto-approve -- */}
          <div className="rules-toolbar">
            <div className="rules-stats">
              {Object.entries(counts).map(([status, n]) => (
                <span key={status} className={`rules-stat rules-stat--${status.toLowerCase()}`}>
                  {n} {status}
                </span>
              ))}
            </div>
            <button
              id="auto-approve-btn"
              className="btn btn-primary"
              onClick={handleAutoApprove}
              disabled={autoBusy || !selectedId || rules.length === 0}
            >
              {autoBusy ? '? Approving…' : '? Auto-Approve Eligible'}
            </button>
          </div>

          {autoResult && (
            <div className="gen-summary">
              Auto-approved {autoResult.autoApproved} rule(s) ·{' '}
              {autoResult.requiresHumanReview} need human review ·{' '}
              {autoResult.skippedLowConfidence} low-confidence skipped
            </div>
          )}

          {/* -- Rules table -- */}
          {loading ? (
            <p className="muted-text">Loading rules…</p>
          ) : rules.length === 0 ? (
            <EmptyState icon="??" title="No rules for this report"
              message="Generate rules from the Analysis ? Report page first." />
          ) : (
            <div className="section">
              <div className="table-wrap">
                <table className="data-table" id="rules-table">
                  <thead>
                    <tr>
                      <th>Mapping</th>
                      <th>Operation</th>
                      <th>Confidence</th>
                      <th>Status</th>
                      <th>Timestamps</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rules.map(rule => (
                      <RuleRow
                        key={rule.id}
                        rule={rule}
                        onApprove={handleApprove}
                        onReject={handleReject}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
