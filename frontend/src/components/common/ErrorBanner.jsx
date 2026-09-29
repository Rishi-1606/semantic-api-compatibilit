/**
 * ErrorBanner
 * Displays an API error in a styled alert strip.
 *
 * @param {string|null} message
 * @param {function}    onDismiss Optional dismiss handler
 */
export default function ErrorBanner({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div className="error-banner" role="alert">
      <span className="error-banner-icon">??</span>
      <span className="error-banner-msg">{message}</span>
      {onDismiss && (
        <button className="error-banner-dismiss" onClick={onDismiss} aria-label="Dismiss">
          ?
        </button>
      )}
    </div>
  );
}
