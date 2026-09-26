package com.chris64233.freightclaim.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SegmentRequest(
        @NotBlank String carrierCode,
        String carrierName,
        String startLocation,
        String endLocation) {
}
