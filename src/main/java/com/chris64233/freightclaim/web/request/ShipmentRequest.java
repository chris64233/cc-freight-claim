package com.chris64233.freightclaim.web.request;

import jakarta.validation.constraints.NotBlank;

public record ShipmentRequest(
        @NotBlank String shipmentNo,
        String origin,
        String destination) {
}
