/**
 * StatusBadge
 * Small UP/DOWN/Checking status chip (extracted from Phase 1 App.jsx).
 *
 * @param {boolean|null} ok      null = loading
 * @param {boolean}      loading
 */
export default function StatusBadge({ ok, loading }) {
  if (loading) return <span className="badge badge-loading">Checking…</span>;
  return ok
    ? <span className="badge badge-up">UP</span>
    : <span className="badge badge-down">DOWN</span>;
}
