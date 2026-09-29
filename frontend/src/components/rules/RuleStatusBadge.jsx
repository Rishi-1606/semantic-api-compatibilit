/**
 * RuleStatusBadge
 * Colored chip for a transformation rule status.
 * Statuses (from Phase 9 lifecycle): GENERATED | APPROVED | REJECTED | INVALID
 *
 * @param {string} status
 */
export default function RuleStatusBadge({ status }) {
  const cls = {
    GENERATED: 'rule-badge--generated',
    APPROVED:  'rule-badge--approved',
    REJECTED:  'rule-badge--rejected',
    INVALID:   'rule-badge--invalid',
  }[status] || 'rule-badge--generated';

  return (
    <span className={`rule-badge ${cls}`}>{status}</span>
  );
}
