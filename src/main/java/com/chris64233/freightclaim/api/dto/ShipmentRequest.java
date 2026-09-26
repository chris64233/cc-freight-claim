package com.chris64233.freightclaim.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ShipmentRequest(
        @NotBlank String shipmentNo,
        String origin,
        String destination,
        @Valid List<SegmentRequest> segments) {
}
