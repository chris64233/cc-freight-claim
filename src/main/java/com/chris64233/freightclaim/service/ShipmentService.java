package com.chris64233.freightclaim.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.freightclaim.api.dto.HandoverEvidenceRequest;
import com.chris64233.freightclaim.api.dto.SegmentRequest;
import com.chris64233.freightclaim.api.dto.ShipmentRequest;
import com.chris64233.freightclaim.domain.CarrierSegment;
import com.chris64233.freightclaim.domain.HandoverEvidence;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.exception.BusinessRuleException;
import com.chris64233.freightclaim.exception.ConflictException;
import com.chris64233.freightclaim.exception.NotFoundException;
import com.chris64233.freightclaim.repository.CarrierSegmentRepository;
import com.chris64233.freightclaim.repository.HandoverEvidenceRepository;
import com.chris64233.freightclaim.repository.ShipmentRepository;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final CarrierSegmentRepository segmentRepository;
    private final HandoverEvidenceRepository handoverRepository;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           CarrierSegmentRepository segmentRepository,
                           HandoverEvidenceRepository handoverRepository) {
        this.shipmentRepository = shipmentRepository;
        this.segmentRepository = segmentRepository;
        this.handoverRepository = handoverRepository;
    }

    @Transactional
    public Shipment createShipment(ShipmentRequest request) {
        if (shipmentRepository.existsByShipmentNo(request.shipmentNo())) {
            throw new ConflictException("运输单号已存在: " + request.shipmentNo());
        }
        Shipment shipment = new Shipment(request.shipmentNo(), request.origin(), request.destination());
        shipment = shipmentRepository.save(shipment);

        List<SegmentRequest> segments = request.segments();
        if (segments != null) {
            int seq = 1;
            for (SegmentRequest s : segments) {
                segmentRepository.save(new CarrierSegment(shipment, seq, s.carrierCode(), s.carrierName(),
                        s.startLocation(), s.endLocation()));
                seq++;
            }
        }
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getByShipmentNo(String shipmentNo) {
        return shipmentRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new NotFoundException("运输单不存在: " + shipmentNo));
    }

    @Transactional(readOnly = true)
    public List<CarrierSegment> listSegments(Long shipmentId) {
        return segmentRepository.findByShipmentIdOrderBySeqAsc(shipmentId);
    }

    @Transactional(readOnly = true)
    public List<HandoverEvidence> listHandoverEvidences(Long shipmentId) {
        return handoverRepository.findByShipmentIdOrderBySeqAsc(shipmentId);
    }

    /**
     * 追加交接证据：证据序号按到达顺序分配，并使运输单证据版本 +1。
     * 版本递增后，任何仍基于旧证据版本的责任决定在确认时会得到版本冲突。
     */
    @Transactional
    public HandoverEvidence addHandoverEvidence(String shipmentNo, HandoverEvidenceRequest request) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(getByShipmentNo(shipmentNo).getId())
                .orElseThrow(() -> new NotFoundException("运输单不存在: " + shipmentNo));
        int maxSeq = (int) segmentRepository.countByShipmentId(shipment.getId());
        if (request.toSegmentSeq() == null || request.toSegmentSeq() < 1 || request.toSegmentSeq() > maxSeq) {
            throw new BusinessRuleException("接收承运段顺序号非法: " + request.toSegmentSeq());
        }
        if (request.fromSegmentSeq() != null
                && (request.fromSegmentSeq() < 1 || request.fromSegmentSeq() > maxSeq)) {
            throw new BusinessRuleException("交出承运段顺序号非法: " + request.fromSegmentSeq());
        }
        int nextSeq = (int) handoverRepository.countByShipmentId(shipment.getId()) + 1;
        HandoverEvidence evidence = new HandoverEvidence(shipment, nextSeq,
                request.fromSegmentSeq(), request.toSegmentSeq(),
                request.evidenceRef(), request.summary());
        handoverRepository.save(evidence);

        shipment.bumpEvidenceVersion();
        shipmentRepository.save(shipment);
        return evidence;
    }
}
