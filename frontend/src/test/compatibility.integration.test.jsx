/**
 * compatibility.integration.test.jsx
 *
 * Integration test 1: Running compatibility analysis and displaying the report.
 * Integration test 2: Approving a rule and updating its UI status.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// ── Mock the API modules ─────────────────────────────────────────────────────

vi.mock('../api/apis.js', () => ({
  listApis: vi.fn(),
  getVersionsForApi: vi.fn(),
  getEndpointsForVersion: vi.fn(),
}));

vi.mock('../api/compatibility.js', () => ({
  runSemanticAnalysis: vi.fn(),
  runCompatibilityReport: vi.fn(),
  getReportsForVersion: vi.fn(),
  getReport: vi.fn(),
  getMatchSession: vi.fn(),
}));

vi.mock('../api/rules.js', () => ({
  generateRules: vi.fn(),
  getRulesForReport: vi.fn(),
  approveRule: vi.fn(),
  rejectRule: vi.fn(),
  autoApproveForReport: vi.fn(),
}));

import * as apisApi from '../api/apis.js';
import * as compatApi from '../api/compatibility.js';
import * as rulesApi from '../api/rules.js';

// ── Fixtures ─────────────────────────────────────────────────────────────────

const MOCK_APIS = [{ id: 1, name: 'Customer API', description: '', versionCount: 2, createdAt: '2026-09-01T00:00:00Z' }];
const MOCK_VERSIONS = [
  { id: 10, apiId: 1, apiName: 'Customer API', version: 'V1', baseUrl: 'http://api.example.com/v1', isActive: true, endpointCount: 2 },
  { id: 11, apiId: 1, apiName: 'Customer API', version: 'V2', baseUrl: 'http://api.example.com/v2', isActive: true, endpointCount: 2 },
];
const MOCK_SESSION = {
  id: 100,
  sourceVersionId: 10, sourceVersionName: 'V1',
  targetVersionId: 11, targetVersionName: 'V2',
  status: 'COMPLETED', compatibilityStatus: 'PARTIALLY_COMPATIBLE', overallScore: 0.82,
  candidates: [{
    id: 1, sessionId: 100,
    sourcePath: 'name', sourceOriginalName: 'name',
    targetPath: 'fullName', targetOriginalName: 'fullName',
    confidence: 0.92, approvalStatus: 'AUTO_APPROVED',
    cosineSimilarity: 0.91, typeFactor: 1.0, pathFactor: 0.9,
  }],
};
const MOCK_REPORT = {
  id: 55, matchSessionId: 100,
  sourceVersionId: 10, sourceVersionName: 'V1',
  targetVersionId: 11, targetVersionName: 'V2',
  compatibilityStatus: 'PARTIALLY_COMPATIBLE', overallScore: 0.82,
  totalSourceFields: 5, matchedFields: 4,
  unmatchedRequiredFields: 1, autoApprovedCount: 3, requiresReviewCount: 1, lowConfidenceCount: 1,
  explanation: 'Most fields matched with high confidence.',
  createdAt: '2026-09-29T10:00:00Z',
  endpointResults: [],
};
const MOCK_RULE_GENERATED = {
  id: 7, reportId: 55, sourcePath: 'name', targetPath: 'fullName',
  operation: 'RENAME', confidence: 0.92, ruleStatus: 'GENERATED',
  candidateStatus: 'AUTO_APPROVED',
  sourceEndpointPath: '/v1/customer', targetEndpointPath: '/v2/customer',
  createdAt: '2026-09-29T10:05:00Z', updatedAt: '2026-09-29T10:05:00Z',
  approvedAt: null, rejectedAt: null, approvalNote: null,
};
const MOCK_RULE_APPROVED = { ...MOCK_RULE_GENERATED, ruleStatus: 'APPROVED', approvedAt: '2026-09-29T11:00:00Z' };

// ── Integration Test 1 ───────────────────────────────────────────────────────

describe('Integration: Run compatibility analysis and display report', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apisApi.listApis.mockResolvedValue(MOCK_APIS);
    apisApi.getVersionsForApi.mockResolvedValue(MOCK_VERSIONS);
    compatApi.getReportsForVersion.mockResolvedValue([]);
    compatApi.runSemanticAnalysis.mockResolvedValue(MOCK_SESSION);
    compatApi.runCompatibilityReport.mockResolvedValue(MOCK_REPORT);
  });

  it('calls Phase-6 and Phase-7 APIs when analysis is submitted', async () => {
    const { default: AnalysisPage } = await import('../pages/AnalysisPage.jsx');
    const { unmount } = render(
      <MemoryRouter initialEntries={['/analysis']}>
        <Routes>
          <Route path="/analysis"            element={<AnalysisPage />} />
          <Route path="/analysis/:reportId"  element={<div data-testid="report-page" />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(apisApi.listApis).toHaveBeenCalledOnce());

    // Open the run-analysis form
    const toggle = await screen.findByRole('button', { name: /New Analysis/i });
    await act(async () => { fireEvent.click(toggle); });

    // Select source and target versions
    const srcSelect = await screen.findByLabelText(/Source Version/i);
    const tgtSelect = await screen.findByLabelText(/Target Version/i);
    await act(async () => {
      fireEvent.change(srcSelect, { target: { value: '10' } });
      fireEvent.change(tgtSelect, { target: { value: '11' } });
    });

    // Submit via the button with id="run-analysis-btn"
    const runBtn = document.getElementById('run-analysis-btn');
    await act(async () => { fireEvent.click(runBtn); });

    await waitFor(() => expect(compatApi.runSemanticAnalysis).toHaveBeenCalledWith(10, 11));
    await waitFor(() => expect(compatApi.runCompatibilityReport).toHaveBeenCalledWith(100));

    unmount();
  });

  it('ReportDetailPage renders version names, status, and explanation from the report', async () => {
    compatApi.getReport.mockResolvedValue(MOCK_REPORT);
    compatApi.getMatchSession.mockResolvedValue(MOCK_SESSION);
    const { default: ReportDetailPage } = await import('../pages/ReportDetailPage.jsx');

    render(
      <MemoryRouter initialEntries={['/analysis/55']}>
        <Routes>
          <Route path="/analysis/:reportId" element={<ReportDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(compatApi.getReport).toHaveBeenCalledWith(55));
    await waitFor(() => {
      expect(screen.getByText(/V1/)).toBeInTheDocument();
      expect(screen.getByText(/V2/)).toBeInTheDocument();
    });
    expect(screen.getByText(/PARTIALLY COMPATIBLE/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(/Most fields matched/)).toBeInTheDocument();
    });
    await waitFor(() => {
      const nameEls = screen.getAllByText('name');
      expect(nameEls.length).toBeGreaterThan(0);
    });
  });
});

// ── Integration Test 2 ───────────────────────────────────────────────────────

describe('Integration: Approve a rule and update its UI status', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apisApi.listApis.mockResolvedValue(MOCK_APIS);
    apisApi.getVersionsForApi.mockResolvedValue(MOCK_VERSIONS);
    compatApi.getReportsForVersion.mockResolvedValue([MOCK_REPORT]);
    rulesApi.getRulesForReport.mockResolvedValue([MOCK_RULE_GENERATED]);
    rulesApi.approveRule.mockResolvedValue(MOCK_RULE_APPROVED);
    rulesApi.autoApproveForReport.mockResolvedValue({
      reportId: 55, autoApproved: 1, requiresHumanReview: 0,
      skippedLowConfidence: 0, skippedRequiresReview: 0, skippedInvalidOrFailed: 0,
      approvedRules: [MOCK_RULE_APPROVED],
    });
  });

  it('shows GENERATED rule with Approve button when reportId is in the URL', async () => {
    const { default: RulesPage } = await import('../pages/RulesPage.jsx');
    render(
      <MemoryRouter initialEntries={['/rules?reportId=55']}>
        <Routes>
          <Route path="/rules" element={<RulesPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(rulesApi.getRulesForReport).toHaveBeenCalledWith(55));
    await waitFor(() => expect(screen.getByText('GENERATED')).toBeInTheDocument());

    // Approve button is present (using its unique id attribute)
    expect(document.getElementById('approve-rule-7')).not.toBeNull();
  });

  it('calls approveRule and replaces GENERATED badge with APPROVED in the UI', async () => {
    const { default: RulesPage } = await import('../pages/RulesPage.jsx');
    render(
      <MemoryRouter initialEntries={['/rules?reportId=55']}>
        <Routes>
          <Route path="/rules" element={<RulesPage />} />
        </Routes>
      </MemoryRouter>
    );

    // Wait for rules to load
    await waitFor(() => screen.getByText('GENERATED'));

    // Click the Approve button (by unique id — avoids emoji encoding issues)
    const approveBtn = document.getElementById('approve-rule-7');
    await act(async () => { fireEvent.click(approveBtn); });

    // The Modal opens — find the confirm button by its text
    const modalConfirm = await screen.findByRole('button', { name: 'Approve' });
    await act(async () => { fireEvent.click(modalConfirm); });

    // approveRule called with correct id and null reason (no reason entered)
    await waitFor(() => expect(rulesApi.approveRule).toHaveBeenCalledWith(7, null));

    // Status badge updates to APPROVED in place
    await waitFor(() => expect(screen.getByText('APPROVED')).toBeInTheDocument());

    // GENERATED badge is gone
    expect(screen.queryByText('GENERATED')).not.toBeInTheDocument();
  });
});