import { useNavigate } from 'react-router-dom';

/**
 * ApiCard
 * Summary card for a registered API (ApiResponse shape).
 *
 * ApiResponse: { id, name, description, versionCount, createdAt }
 */
export default function ApiCard({ api }) {
  const navigate = useNavigate();
  return (
    <div
      className="api-card"
      onClick={() => navigate(`/apis/${api.id}`)}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && navigate(`/apis/${api.id}`)}
      id={`api-card-${api.id}`}
    >
      <div className="api-card-header">
        <span className="api-card-icon">??</span>
        <h3 className="api-card-name">{api.name}</h3>
        <span className="api-card-badge">{api.versionCount} version{api.versionCount !== 1 ? 's' : ''}</span>
      </div>
      {api.description && (
        <p className="api-card-desc">{api.description}</p>
      )}
      <p className="api-card-meta">
        Registered {api.createdAt ? new Date(api.createdAt).toLocaleDateString() : '—'}
      </p>
    </div>
  );
}
