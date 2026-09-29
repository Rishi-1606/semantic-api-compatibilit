import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getReport } from '../api/compatibility.js';
import { generateRules } from '../api/rules.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import MappingTable from '../components/analysis/MappingTable.jsx';
import EndpointResultRow from '../components/analysis/EndpointResultRow.jsx';
import ConfidenceBar from '../components/common/ConfidenceBar.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';

/**
 * ReportDetailPage  /analysis/:reportId
 * Full compatibility report with field mappings and endpoint breakdown.
 *
 * Uses GET /api/reports/{reportId} which returns:
 *   CompatibilityReportResponse with embedded endpointResults
 *   (endpointResults does NOT include candidate lists — use matchSession for those)
 *
 * To display field mapping candidates, we load the match session via
 * GET /api/compatibility/{sessionId} (which includes candidates[]).
 */
export default function ReportDetailPage() {
  const { reportId } = useParams();
  const navigate     = useNavigate();

  const [report,     setReport]     = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState(null);
  const [genBusy,    setGenBusy]    = useState(false);
  const [genResult,  setGenResult]  = useState(null);
  const [genError,   setGenError]   = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const r = await getReport(Number(reportId));
      setReport(r);
      // Load match session to get candidates (field-level mappings)
      const { getMatchSession } = await import('../api/compatibility.js');
      if (r.matchSessionId) {
        const session = await getMatchSession(r.matchSessionId);
        setCandidates(session.candidates || []);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [reportId]);

  useEffect(() => { load(); }, [load]);

  const handleGenerateRules = async () => {
    setGenBusy(true);
    setGenError(null);
    try {
      const result = await generateRules(Number(reportId));
      setGenResult(result);
      navigate(`/rules?reportId=${reportId}`);
    } catch (err) {
      setGenError(err.message);
    } finally {
      setGenBusy(false);
    }
  };

  if (loading) return <div className="page"><p className="muted-text">Loading report…</p></div>;
  if (error)   return <div className="page"><ErrorBanner message={error} /></div>;
  if (!report) return <div className="page"><p className="muted-text">Report not found.</p></div>;

  const statusColor = {
    COMPATIBLE:           'var(--accent-green)',
    PARTIALLY_COMPATIBLE: 'var(--accent-yellow)',
    INCOMPATIBLE:         'var(--accent-red)',
  }[report.compatibilityStatus] || 'var(--text-secondary)';

  return (
    <div className="page">
      <PageHeader
        title={`${report.sourceVersionName} ? ${report.targetVersionName}`}
        subtitle={`Compatibility Report #${report.id}`}
        actions={
          <button className="btn btn-secondary" onClick={() => navigate('/analysis')}>? Back</button>
        }
      />

      {/* -- Overall score -- */}
      <div className="report-score-block">
        <div className="report-score-badge" style={{ borderColor: statusColor, color: statusColor }}>
          {report.compatibilityStatus?.replace(/_/g, ' ')}
        </div>
        <div className="report-score-bar">
          <ConfidenceBar value={report.overallScore} />
          <span className="report-score-detail">
            {report.matchedFields}/{report.totalSourceFields} fields matched ·{' '}
            {report.autoApprovedCount} auto-approved · {report.requiresReviewCount} need review
          </span>
        </div>
      </div>

      {report.explanation && (
        <div className="section">
          <p className="report-explanation">{report.explanation}</p>
        </div>
      )}

      {/* -- Endpoint breakdown -- */}
      {report.endpointResults && report.endpointResults.length > 0 && (
        <section className="section">
          <h2 className="section-title">Endpoint Breakdown</h2>
          <div className="table-wrap">
            <table className="data-table" id="endpoint-breakdown-table">
              <thead>
                <tr>
                  <th>Source Endpoint</th>
                  <th>Target Endpoint</th>
                  <th>Status</th>
                  <th>Score</th>
                  <th>Fields</th>
                </tr>
              </thead>
              <tbody>
                {report.endpointResults.map(ep => (
                  <EndpointResultRow key={ep.id} result={ep} />
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* -- Field mapping candidates -- */}
      <section className="section">
        <h2 className="section-title">Field Mappings ({candidates.length})</h2>
        <MappingTable candidates={candidates} />
      </section>

      {/* -- Generate rules -- */}
      <section className="section">
        <div className="section-header">
          <h2 className="section-title">Transformation Rules</h2>
          <button
            id="generate-rules-btn"
            className="btn btn-primary"
            onClick={handleGenerateRules}
            disabled={genBusy}
          >
            {genBusy ? '? Generating…' : '? Generate Transformation Rules'}
          </button>
        </div>
        <ErrorBanner message={genError} onDismiss={() => setGenError(null)} />
        {genResult && (
          <div className="gen-summary">
            Generated {genResult.rulesGenerated} rule(s) · {genResult.skippedNoOp} no-op · {genResult.rulesInvalid} invalid
          </div>
        )}
        <p className="muted-text">
          After generating rules, review and approve them in the{' '}
          <button className="link-btn" onClick={() => navigate(`/rules?reportId=${reportId}`)}>
            Rules page ?
          </button>
        </p>
      </section>
    </div>
  );
}
