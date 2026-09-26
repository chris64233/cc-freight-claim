package com.chris64233.freightclaim.web;

import com.chris64233.freightclaim.service.ShipmentService;
import com.chris64233.freightclaim.web.request.EvidenceRequest;
import com.chris64233.freightclaim.web.request.SegmentRequest;
import com.chris64233.freightclaim.web.request.ShipmentRequest;
import com.chris64233.freightclaim.web.view.HandoverEvidenceView;
import com.chris64233.freightclaim.web.view.SegmentView;
import com.chris64233.freightclaim.web.view.ShipmentView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<ShipmentView> create(@Valid @RequestBody ShipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentService.createShipment(request.shipmentNo(), request.origin(),
                        request.destination()));
    }

    @GetMapping("/{shipmentNo}")
    public ShipmentView get(@PathVariable String shipmentNo) {
        return shipmentService.getShipment(shipmentNo);
    }

    @PostMapping("/{shipmentNo}/segments")
    public ResponseEntity<SegmentView> appendSegment(@PathVariable String shipmentNo,
                                                     @Valid @RequestBody SegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentService.appendSegment(shipmentNo, request.carrierCode(),
                        request.carrierName(), request.startNode(), request.endNode()));
    }

    @GetMapping("/{shipmentNo}/segments")
    public List<SegmentView> listSegments(@PathVariable String shipmentNo) {
        return shipmentService.listSegments(shipmentNo);
    }

    @PostMapping("/{shipmentNo}/evidences")
    public ResponseEntity<HandoverEvidenceView> appendEvidence(@PathVariable String shipmentNo,
                                                               @Valid @RequestBody EvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shipmentService.appendEvidence(shipmentNo, request.afterSegmentSeq(),
                        request.location(), request.content(), request.recordedAt()));
    }

    @GetMapping("/{shipmentNo}/evidences")
    public List<HandoverEvidenceView> listEvidences(@PathVariable String shipmentNo) {
        return shipmentService.listEvidences(shipmentNo);
    }
}
