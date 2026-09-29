import ConfidenceBar from '../common/ConfidenceBar.jsx';

/**
 * MappingTable
 * Displays semantic match candidates as a table with confidence bars.
 *
 * MatchCandidateResponse fields used:
 *   sourcePath, sourceOriginalName, targetPath, targetOriginalName,
 *   confidence, approvalStatus, cosineSimilarity
 *
 * @param {Array} candidates - MatchCandidateResponse[]
 */
export default function MappingTable({ candidates }) {
  if (!candidates || candidates.length === 0) {
    return <p className="muted-text">No field mappings found.</p>;
  }

  const statusColor = {
    AUTO_APPROVED:    'var(--accent-green)',
    REQUIRES_REVIEW:  'var(--accent-yellow)',
    LOW_CONFIDENCE:   'var(--accent-red)',
    REJECTED:         'var(--accent-red)',
  };

  return (
    <div className="table-wrap">
      <table className="data-table" id="mapping-table">
        <thead>
          <tr>
            <th>Source Field</th>
            <th>Target Field</th>
            <th>Confidence</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {candidates.map(c => (
            <tr key={c.id} className="data-tr">
              <td>
                <code className="rule-path">{c.sourcePath}</code>
                {c.sourceOriginalName !== c.sourcePath && (
                  <small className="field-orig"> ({c.sourceOriginalName})</small>
                )}
              </td>
              <td>
                <code className="rule-path">{c.targetPath}</code>
                {c.targetOriginalName !== c.targetPath && (
                  <small className="field-orig"> ({c.targetOriginalName})</small>
                )}
              </td>
              <td><ConfidenceBar value={c.confidence} /></td>
              <td>
                <span className="candidate-status" style={{ color: statusColor[c.approvalStatus] }}>
                  {c.approvalStatus?.replace('_', ' ')}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
