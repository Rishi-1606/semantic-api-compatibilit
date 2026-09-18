package com.semanticapi.dto;

/**
 * Optional request body for approve/reject rule endpoints.
 *
 * <p>Both {@code POST /api/transformation-rules/{id}/approve} and
 * {@code POST /api/transformation-rules/{id}/reject} accept this body.
 * The {@code reason} field is optional for approve, recommended for reject.
 */
public class ApproveRejectRuleRequest {

    /** Human-readable reason for the approval or rejection decision. */
    private String reason;

    public String getReason()              { return reason; }
    public void setReason(String reason)   { this.reason = reason; }
}
