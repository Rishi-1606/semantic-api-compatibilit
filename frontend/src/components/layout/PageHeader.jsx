/**
 * PageHeader
 * Consistent page title + optional subtitle at the top of each page.
 *
 * @param {string}      title
 * @param {string}      subtitle Optional
 * @param {ReactNode}   actions  Optional right-side actions (buttons, etc.)
 */
export default function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div className="page-header-text">
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </div>
  );
}
