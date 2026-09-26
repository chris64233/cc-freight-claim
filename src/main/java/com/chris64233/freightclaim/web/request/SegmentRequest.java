package com.chris64233.freightclaim.web.request;

import jakarta.validation.constraints.NotBlank;

public record SegmentRequest(
        @NotBlank String carrierCode,
        String carrierName,
        String startNode,
        String endNode) {
}
