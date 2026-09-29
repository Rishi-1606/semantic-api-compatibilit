import ConfidenceBar from '../common/ConfidenceBar.jsx';

/**
 * EndpointResultRow
 * One row in the endpoint-level compatibility breakdown table.
 *
 * EndpointCompatibilityResultResponse fields used:
 *   sourceEndpointPath, targetEndpointPath, compatibilityStatus,
 *   compatibilityScore, matchedRequiredFields, unmatchedRequiredFields,
 *   totalSourceFields
 */
export default function EndpointResultRow({ result }) {
  const statusColor = {
    COMPATIBLE:           'var(--accent-green)',
    PARTIALLY_COMPATIBLE: 'var(--accent-yellow)',
    INCOMPATIBLE:         'var(--accent-red)',
  }[result.compatibilityStatus] || 'var(--text-secondary)';

  return (
    <tr className="data-tr">
      <td><code className="rule-path">{result.sourceEndpointPath}</code></td>
      <td><code className="rule-path">{result.targetEndpointPath}</code></td>
      <td>
        <span style={{ color: statusColor, fontWeight: 600 }}>
          {result.compatibilityStatus?.replace('_', ' ')}
        </span>
      </td>
      <td><ConfidenceBar value={result.compatibilityScore} /></td>
      <td className="muted-text">
        {result.matchedRequiredFields}/{result.totalSourceFields} fields
      </td>
    </tr>
  );
}
