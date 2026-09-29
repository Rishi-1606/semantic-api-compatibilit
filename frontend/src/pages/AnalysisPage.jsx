import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { listApis, getVersionsForApi } from '../api/apis.js';
import { getReportsForVersion } from '../api/compatibility.js';
import PageHeader from '../components/layout/PageHeader.jsx';
import RunAnalysisForm from '../components/analysis/RunAnalysisForm.jsx';
import ReportSummaryCard from '../components/analysis/ReportSummaryCard.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorBanner from '../components/common/ErrorBanner.jsx';

/**
 * AnalysisPage  /analysis
 * Run new compatibility analysis and browse past reports.
 */
export default function AnalysisPage() {
  const navigate = useNavigate();

  const [allVersions, setAllVersions] = useState([]);
  const [reports,     setReports]     = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState(null);
  const [showForm,    setShowForm]    = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const apis = await listApis();
      // Collect all versions for the run-form dropdowns
      const versionsArrays = await Promise.all(apis.map(a => getVersionsForApi(a.id).catch(() => [])));
      const flat = versionsArrays.flat();
      setAllVersions(flat);

      // Load reports for each version (deduplicated by report id)
      const reportMap = {};
      await Promise.all(flat.map(async v => {
        try {
          const vReports = await getReportsForVersion(v.id);
          vReports.forEach(r => { reportMap[r.id] = r; });
        } catch { /* version may have no reports */ }
      }));
      setReports(Object.values(reportMap).sort((a, b) =>
        new Date(b.createdAt) - new Date(a.createdAt)));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const handleNewReport = (reportId) => {
    setShowForm(false);
    navigate(`/analysis/${reportId}`);
  };

  return (
    <div className="page">
      <PageHeader
        title="Compatibility Analysis"
        subtitle="Run semantic analysis between API versions and view reports"
        actions={
          <button id="run-analysis-toggle-btn" className="btn btn-primary" onClick={() => setShowForm(f => !f)}>
            {showForm ? '? Cancel' : '? New Analysis'}
          </button>
        }
      />

      {showForm && (
        <div className="section">
          <RunAnalysisForm allVersions={allVersions} onSuccess={handleNewReport} />
        </div>
      )}

      <ErrorBanner message={error} onDismiss={() => setError(null)} />

      <section className="section">
        <h2 className="section-title">Past Reports ({reports.length})</h2>
        {loading ? (
          <p className="muted-text">Loading…</p>
        ) : reports.length === 0 ? (
          <EmptyState icon="??" title="No reports yet"
            message="Run your first analysis above." />
        ) : (
          <div className="report-list" id="report-list">
            {reports.map(r => <ReportSummaryCard key={r.id} report={r} />)}
          </div>
        )}
      </section>
    </div>
  );
}
