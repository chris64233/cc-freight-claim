package com.chris64233.freightclaim.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimEvidenceRequest(
        @NotBlank String evidenceRef,
        String evidenceType,
        String summary) {
}
