package com.chris64233.freightclaim.api.dto;

import com.chris64233.freightclaim.domain.ClaimEvidence;

public record ClaimEvidenceResponse(
        Long id,
        String evidenceRef,
        String evidenceType,
        String summary) {

    public static ClaimEvidenceResponse of(ClaimEvidence e) {
        return new ClaimEvidenceResponse(e.getId(), e.getEvidenceRef(), e.getEvidenceType(), e.getSummary());
    }
}
