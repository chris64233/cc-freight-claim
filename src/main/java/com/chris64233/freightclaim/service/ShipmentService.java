package com.chris64233.freightclaim.service;

import com.chris64233.freightclaim.domain.CarrierSegment;
import com.chris64233.freightclaim.domain.HandoverEvidence;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.repo.CarrierSegmentRepository;
import com.chris64233.freightclaim.repo.HandoverEvidenceRepository;
import com.chris64233.freightclaim.repo.ShipmentRepository;
import com.chris64233.freightclaim.support.ConflictException;
import com.chris64233.freightclaim.support.NotFoundException;
import com.chris64233.freightclaim.support.ValidationException;
import com.chris64233.freightclaim.web.view.HandoverEvidenceView;
import com.chris64233.freightclaim.web.view.SegmentView;
import com.chris64233.freightclaim.web.view.ShipmentView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 运输单、承运段与交接证据管理。 */
@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final CarrierSegmentRepository segments;
    private final HandoverEvidenceRepository evidences;

    public ShipmentService(ShipmentRepository shipments,
                           CarrierSegmentRepository segments,
                           HandoverEvidenceRepository evidences) {
        this.shipments = shipments;
        this.segments = segments;
        this.evidences = evidences;
    }

    @Transactional
    public ShipmentView createShipment(String shipmentNo, String origin, String destination) {
        if (shipmentNo == null || shipmentNo.isBlank()) {
            throw new ValidationException("运输单号不能为空");
        }
        if (shipments.findByShipmentNo(shipmentNo).isPresent()) {
            throw new ConflictException("运输单已存在: " + shipmentNo);
        }
        try {
            Shipment shipment = new Shipment(shipmentNo.trim(), origin, destination);
            return toShipmentView(shipments.saveAndFlush(shipment));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("运输单已存在: " + shipmentNo);
        }
    }

    /** 追加承运段，段序号按运输单现有段数自增，保证承运段按顺序发生。 */
    @Transactional
    public SegmentView appendSegment(String shipmentNo, String carrierCode, String carrierName,
                                     String startNode, String endNode) {
        if (carrierCode == null || carrierCode.isBlank()) {
            throw new ValidationException("承运商编码不能为空");
        }
        Shipment shipment = requireShipment(shipmentNo);
        int nextSeq = shipment.getSegments().size() + 1;
        CarrierSegment segment =
                new CarrierSegment(nextSeq, carrierCode.trim(), carrierName, startNode, endNode);
        shipment.addSegment(segment);
        return toSegmentView(segments.saveAndFlush(segment));
    }

    /** 追加交接证据并推进运输单证据版本。 */
    @Transactional
    public HandoverEvidenceView appendEvidence(String shipmentNo, Integer afterSegmentSeq,
                                               String location, String content,
                                               OffsetDateTime recordedAt) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("交接证据内容不能为空");
        }
        Shipment shipment = requireShipment(shipmentNo);
        int seq = afterSegmentSeq == null ? shipment.getSegments().size() : afterSegmentSeq;
        if (seq < 0 || seq > shipment.getSegments().size()) {
            throw new ValidationException(
                    "交接位置 afterSegmentSeq 必须在 0 与承运段数 " + shipment.getSegments().size() + " 之间");
        }
        OffsetDateTime at = recordedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : recordedAt;
        HandoverEvidence evidence = new HandoverEvidence(seq, location, content, at);
        shipment.addEvidence(evidence);
        HandoverEvidence saved = evidences.saveAndFlush(evidence);
        return new HandoverEvidenceView(saved.getId(), saved.getAfterSegmentSeq(),
                saved.getLocation(), saved.getContent(), saved.getRecordedAt(),
                shipment.getEvidenceVersion());
    }

    @Transactional(readOnly = true)
    public Shipment requireShipment(String shipmentNo) {
        return shipments.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new NotFoundException("运输单不存在: " + shipmentNo));
    }

    @Transactional(readOnly = true)
    public ShipmentView getShipment(String shipmentNo) {
        return toShipmentView(requireShipment(shipmentNo));
    }

    @Transactional(readOnly = true)
    public List<SegmentView> listSegments(String shipmentNo) {
        Shipment shipment = requireShipment(shipmentNo);
        return segments.findByShipmentIdOrderBySequenceNoAsc(shipment.getId()).stream()
                .map(ShipmentService::toSegmentView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HandoverEvidenceView> listEvidences(String shipmentNo) {
        Shipment shipment = requireShipment(shipmentNo);
        // 第 n 条证据追加后的证据版本就是 n（初始为 0）。
        List<HandoverEvidence> list = evidences.findByShipmentIdOrderByIdAsc(shipment.getId());
        List<HandoverEvidenceView> views = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            HandoverEvidence e = list.get(i);
            views.add(new HandoverEvidenceView(e.getId(), e.getAfterSegmentSeq(),
                    e.getLocation(), e.getContent(), e.getRecordedAt(), i + 1));
        }
        return views;
    }

    private static ShipmentView toShipmentView(Shipment s) {
        return new ShipmentView(s.getId(), s.getShipmentNo(), s.getOrigin(), s.getDestination(),
                s.getEvidenceVersion(), s.getSegments().size(), s.getHandoverEvidences().size());
    }

    private static SegmentView toSegmentView(CarrierSegment s) {
        return new SegmentView(s.getId(), s.getSequenceNo(), s.getCarrierCode(),
                s.getCarrierName(), s.getStartNode(), s.getEndNode());
    }
}
