import { Routes, Route } from 'react-router-dom';
import Sidebar from './components/layout/Sidebar.jsx';
import DashboardPage     from './pages/DashboardPage.jsx';
import ApiManagementPage from './pages/ApiManagementPage.jsx';
import ApiDetailPage     from './pages/ApiDetailPage.jsx';
import AnalysisPage      from './pages/AnalysisPage.jsx';
import ReportDetailPage  from './pages/ReportDetailPage.jsx';
import RulesPage         from './pages/RulesPage.jsx';
import TesterPage        from './pages/TesterPage.jsx';
import './App.css';

/**
 * App — root routing shell for the Phase 12 Developer Dashboard.
 *
 * Layout: fixed left Sidebar + scrollable main content area.
 * All backend calls go through the Spring Boot REST API (port 8080).
 * The Node.js gateway (port 3001) handles runtime proxy traffic only.
 */
export default function App() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="app-content" id="main-content">
        <Routes>
          <Route path="/"                element={<DashboardPage />} />
          <Route path="/apis"            element={<ApiManagementPage />} />
          <Route path="/apis/:apiId"     element={<ApiDetailPage />} />
          <Route path="/analysis"        element={<AnalysisPage />} />
          <Route path="/analysis/:reportId" element={<ReportDetailPage />} />
          <Route path="/rules"           element={<RulesPage />} />
          <Route path="/tester"          element={<TesterPage />} />
          <Route path="*"               element={<NotFound />} />
        </Routes>
      </main>
    </div>
  );
}

function NotFound() {
  return (
    <div className="page">
      <div className="empty-state">
        <div className="empty-state-icon">??</div>
        <p className="empty-state-title">Page not found</p>
        <p className="empty-state-msg">
          Use the sidebar to navigate to a valid page.
        </p>
      </div>
    </div>
  );
}
