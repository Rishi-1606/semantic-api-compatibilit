import { useState } from 'react';
import ConfidenceBar from '../common/ConfidenceBar.jsx';
import RuleStatusBadge from './RuleStatusBadge.jsx';
import Modal from '../common/Modal.jsx';

/**
 * RuleRow
 * Single table row for a TransformationRule with inline approve/reject actions.
 *
 * Rule shape (from TransformationRuleResponse DTO):
 *   id, sourcePath, targetPath, operation, confidence, ruleStatus,
 *   candidateStatus, createdAt, approvedAt, rejectedAt, approvalNote,
 *   rejectionReason, sourceEndpointPath, targetEndpointPath
 *
 * @param {object}   rule
 * @param {function} onApprove(ruleId, reason)
 * @param {function} onReject(ruleId, reason)
 */
export default function RuleRow({ rule, onApprove, onReject }) {
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen,  setRejectOpen]  = useState(false);
  const [busy, setBusy] = useState(false);

  const isApprovable = rule.ruleStatus === 'GENERATED';
  const isRejectable = rule.ruleStatus === 'GENERATED' || rule.ruleStatus === 'APPROVED';
  const isInvalid    = rule.ruleStatus === 'INVALID';

  const handleApprove = async (reason) => {
    setApproveOpen(false);
    setBusy(true);
    try { await onApprove(rule.id, reason); } finally { setBusy(false); }
  };

  const handleReject = async (reason) => {
    setRejectOpen(false);
    setBusy(true);
    try { await onReject(rule.id, reason); } finally { setBusy(false); }
  };

  const fmtDate = (iso) =>
    iso ? new Date(iso).toLocaleString() : '—';

  return (
    <>
      <tr className={`rule-row${isInvalid ? ' rule-row--invalid' : rule.ruleStatus === 'REJECTED' ? ' rule-row--rejected' : ''}`}>
        <td className="rule-td">
          <code className="rule-path">{rule.sourcePath || '—'}</code>
          <span className="rule-arrow"> ? </span>
          <code className="rule-path">{rule.targetPath || '—'}</code>
        </td>
        <td className="rule-td rule-op">{rule.operation}</td>
        <td className="rule-td">
          <ConfidenceBar value={rule.confidence} />
        </td>
        <td className="rule-td">
          <RuleStatusBadge status={rule.ruleStatus} />
        </td>
        <td className="rule-td rule-dates">
          <small title="Created">?? {fmtDate(rule.createdAt)}</small>
          {rule.approvedAt && <small title="Approved">? {fmtDate(rule.approvedAt)}</small>}
          {rule.rejectedAt && <small title="Rejected">? {fmtDate(rule.rejectedAt)}</small>}
        </td>
        <td className="rule-td rule-actions">
          {isApprovable && (
            <button
              id={`approve-rule-${rule.id}`}
              className="btn btn-success btn-sm"
              onClick={() => setApproveOpen(true)}
              disabled={busy}
              title="Approve this rule"
            >
              ? Approve
            </button>
          )}
          {isRejectable && (
            <button
              id={`reject-rule-${rule.id}`}
              className="btn btn-danger btn-sm"
              onClick={() => setRejectOpen(true)}
              disabled={busy}
              title="Reject this rule"
            >
              ? Reject
            </button>
          )}
          {!isApprovable && !isRejectable && <span className="rule-no-action">—</span>}
        </td>
      </tr>

      {/* Approve modal */}
      <Modal
        isOpen={approveOpen}
        title="Approve Transformation Rule"
        confirmLabel="Approve"
        confirmVariant="success"
        showReason
        onConfirm={handleApprove}
        onCancel={() => setApproveOpen(false)}
      >
        <p>Approve rule: <strong>{rule.sourcePath} ? {rule.targetPath}</strong>?</p>
        <p className="modal-op">Operation: {rule.operation}</p>
      </Modal>

      {/* Reject modal */}
      <Modal
        isOpen={rejectOpen}
        title="Reject Transformation Rule"
        confirmLabel="Reject"
        confirmVariant="danger"
        showReason
        onConfirm={handleReject}
        onCancel={() => setRejectOpen(false)}
      >
        <p>Reject rule: <strong>{rule.sourcePath} ? {rule.targetPath}</strong>?</p>
        <p className="modal-op">This rule will not be executable by the gateway.</p>
      </Modal>
    </>
  );
}
