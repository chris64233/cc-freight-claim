package com.chris64233.freightclaim.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.freightclaim.api.dto.HandoverEvidenceRequest;
import com.chris64233.freightclaim.api.dto.HandoverEvidenceResponse;
import com.chris64233.freightclaim.api.dto.SegmentResponse;
import com.chris64233.freightclaim.api.dto.ShipmentRequest;
import com.chris64233.freightclaim.api.dto.ShipmentResponse;
import com.chris64233.freightclaim.domain.HandoverEvidence;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.service.ShipmentService;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentResponse create(@Valid @RequestBody ShipmentRequest request) {
        Shipment shipment = shipmentService.createShipment(request);
        List<SegmentResponse> segments = shipmentService.listSegments(shipment.getId()).stream()
                .map(SegmentResponse::of).toList();
        return ShipmentResponse.of(shipment, segments);
    }

    @GetMapping("/{shipmentNo}")
    public ShipmentResponse get(@PathVariable String shipmentNo) {
        Shipment shipment = shipmentService.getByShipmentNo(shipmentNo);
        List<SegmentResponse> segments = shipmentService.listSegments(shipment.getId()).stream()
                .map(SegmentResponse::of).toList();
        return ShipmentResponse.of(shipment, segments);
    }

    @GetMapping("/{shipmentNo}/segments")
    public List<SegmentResponse> segments(@PathVariable String shipmentNo) {
        Shipment shipment = shipmentService.getByShipmentNo(shipmentNo);
        return shipmentService.listSegments(shipment.getId()).stream()
                .map(SegmentResponse::of).toList();
    }

    @GetMapping("/{shipmentNo}/handover-evidences")
    public List<HandoverEvidenceResponse> handoverEvidences(@PathVariable String shipmentNo) {
        Shipment shipment = shipmentService.getByShipmentNo(shipmentNo);
        return shipmentService.listHandoverEvidences(shipment.getId()).stream()
                .map(HandoverEvidenceResponse::of).toList();
    }

    @PostMapping("/{shipmentNo}/handover-evidences")
    @ResponseStatus(HttpStatus.CREATED)
    public HandoverEvidenceResponse addHandoverEvidence(@PathVariable String shipmentNo,
                                                        @Valid @RequestBody HandoverEvidenceRequest request) {
        HandoverEvidence evidence = shipmentService.addHandoverEvidence(shipmentNo, request);
        return HandoverEvidenceResponse.of(evidence);
    }
}
