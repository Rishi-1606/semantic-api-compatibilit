import { useNavigate } from 'react-router-dom';
import ConfidenceBar from '../common/ConfidenceBar.jsx';

/**
 * ReportSummaryCard
 * Compact card for a CompatibilityReportResponse (list view).
 *
 * Fields used: id, sourceVersionName, targetVersionName,
 *   compatibilityStatus, overallScore, matchedFields, totalSourceFields, createdAt
 */
export default function ReportSummaryCard({ report }) {
  const navigate = useNavigate();

  const statusColor = {
    COMPATIBLE:           'var(--accent-green)',
    PARTIALLY_COMPATIBLE: 'var(--accent-yellow)',
    INCOMPATIBLE:         'var(--accent-red)',
  }[report.compatibilityStatus] || 'var(--text-secondary)';

  return (
    <div
      className="report-card"
      onClick={() => navigate(`/analysis/${report.id}`)}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && navigate(`/analysis/${report.id}`)}
      id={`report-card-${report.id}`}
    >
      <div className="report-card-header">
        <span className="report-card-versions">
          {report.sourceVersionName} <span className="arrow">?</span> {report.targetVersionName}
        </span>
        <span className="report-card-status" style={{ color: statusColor }}>
          {report.compatibilityStatus?.replace(/_/g, ' ')}
        </span>
      </div>
      <div className="report-card-score">
        <ConfidenceBar value={report.overallScore} />
      </div>
      <p className="report-card-meta">
        {report.matchedFields}/{report.totalSourceFields} fields matched ·{' '}
        {report.createdAt ? new Date(report.createdAt).toLocaleString() : ''}
      </p>
    </div>
  );
}
