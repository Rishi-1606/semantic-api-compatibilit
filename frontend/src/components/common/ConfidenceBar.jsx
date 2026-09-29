/**
 * ConfidenceBar
 * Displays a confidence score as a colored horizontal bar + percentage text.
 *
 * Color thresholds (from spec § 7):
 *   >= 0.90  ? green  (auto-approve eligible)
 *   0.70–0.89 ? yellow (requires human review)
 *   < 0.70   ? red    (low confidence)
 *
 * @param {number} value  Score in [0, 1]
 */
export default function ConfidenceBar({ value }) {
  const pct = Math.round((value || 0) * 100);

  const color =
    pct >= 90 ? 'var(--accent-green)' :
    pct >= 70 ? 'var(--accent-yellow)' :
                'var(--accent-red)';

  return (
    <div className="confidence-bar-wrap">
      <div className="confidence-bar-bg">
        <div
          className="confidence-bar-fill"
          style={{ width: `${pct}%`, backgroundColor: color }}
        />
      </div>
      <span className="confidence-bar-label" style={{ color }}>
        {pct}%
      </span>
    </div>
  );
}
