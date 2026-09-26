package com.chris64233.freightclaim.web.view;

public record ShipmentView(
        Long id,
        String shipmentNo,
        String origin,
        String destination,
        int evidenceVersion,
        int segmentCount,
        int evidenceCount) {
}
