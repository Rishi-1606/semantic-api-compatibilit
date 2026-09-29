import { useState } from 'react';
import { createPortal } from 'react-dom';

/**
 * Modal
 * A simple confirm/form modal with optional reason text input.
 *
 * Renders via ReactDOM.createPortal into document.body so that it never
 * becomes an invalid child of a table element (e.g. <tbody> from RuleRow).
 *
 * @param {boolean}  isOpen
 * @param {string}   title
 * @param {string}   confirmLabel   Label for the confirm button (default "Confirm")
 * @param {string}   confirmVariant "danger" | "success" (default "success")
 * @param {boolean}  showReason     Whether to show an optional "Reason" textarea
 * @param {function} onConfirm(reason: string|null)
 * @param {function} onCancel
 */
export default function Modal({
  isOpen,
  title,
  children,
  confirmLabel = 'Confirm',
  confirmVariant = 'success',
  showReason = false,
  onConfirm,
  onCancel,
}) {
  const [reason, setReason] = useState('');

  if (!isOpen) return null;

  const handleConfirm = () => {
    onConfirm(showReason ? (reason.trim() || null) : null);
    setReason('');
  };

  const handleCancel = () => {
    setReason('');
    onCancel();
  };

  const modalContent = (
    <div className="modal-overlay" onClick={handleCancel}>
      <div className="modal-box" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">{title}</h3>
          <button className="modal-close" onClick={handleCancel}>✕</button>
        </div>
        <div className="modal-body">
          {children}
          {showReason && (
            <div className="modal-reason">
              <label className="label" htmlFor="modal-reason-input">
                Reason (optional)
              </label>
              <textarea
                id="modal-reason-input"
                className="form-textarea"
                rows={3}
                placeholder="Enter a reason…"
                value={reason}
                onChange={e => setReason(e.target.value)}
              />
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={handleCancel}>Cancel</button>
          <button
            className={`btn ${confirmVariant === 'danger' ? 'btn-danger' : 'btn-success'}`}
            onClick={handleConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );

  // Portal renders outside the component tree into document.body,
  // preventing the overlay <div> from appearing inside <tbody> or any
  // other DOM element that cannot legally contain block elements.
  return createPortal(modalContent, document.body);
}

