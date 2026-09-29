/**
 * EmptyState
 * Consistent "nothing here yet" placeholder block.
 */
export default function EmptyState({ icon = '??', title, message }) {
  return (
    <div className="empty-state">
      <div className="empty-state-icon">{icon}</div>
      {title && <p className="empty-state-title">{title}</p>}
      {message && <p className="empty-state-msg">{message}</p>}
    </div>
  );
}
