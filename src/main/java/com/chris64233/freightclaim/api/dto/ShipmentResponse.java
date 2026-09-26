package com.chris64233.freightclaim.api.dto;

import java.time.Instant;
import java.util.List;

import com.chris64233.freightclaim.domain.Shipment;

public record ShipmentResponse(
        Long id,
        String shipmentNo,
        String origin,
        String destination,
        long evidenceVersion,
        Instant createdAt,
        List<SegmentResponse> segments) {

    public static ShipmentResponse of(Shipment shipment, List<SegmentResponse> segments) {
        return new ShipmentResponse(shipment.getId(), shipment.getShipmentNo(),
                shipment.getOrigin(), shipment.getDestination(),
                shipment.getEvidenceVersion(), shipment.getCreatedAt(), segments);
    }
}
