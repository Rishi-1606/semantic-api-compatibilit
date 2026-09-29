import { NavLink } from 'react-router-dom';

const NAV = [
  { to: '/',         icon: '??', label: 'Dashboard'     },
  { to: '/apis',     icon: '??', label: 'API Management' },
  { to: '/analysis', icon: '??', label: 'Analysis'       },
  { to: '/rules',    icon: '??', label: 'Rules'          },
  { to: '/tester',   icon: '??', label: 'Tester'         },
];

/**
 * Sidebar navigation.
 * Uses React Router NavLink for active-link highlighting.
 */
export default function Sidebar() {
  return (
    <nav className="sidebar" aria-label="Main navigation">
      <div className="sidebar-brand">
        <span className="sidebar-brand-icon">??</span>
        <span className="sidebar-brand-text">Semantic API</span>
      </div>
      <ul className="sidebar-nav" role="list">
        {NAV.map(({ to, icon, label }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `sidebar-link${isActive ? ' sidebar-link--active' : ''}`
              }
            >
              <span className="sidebar-link-icon">{icon}</span>
              <span className="sidebar-link-label">{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
      <div className="sidebar-footer">
        <span className="sidebar-footer-text">Phase 12 Dashboard</span>
      </div>
    </nav>
  );
}
