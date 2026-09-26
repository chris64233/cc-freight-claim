package com.chris64233.freightclaim.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.CarrierSegment;

public interface CarrierSegmentRepository extends JpaRepository<CarrierSegment, Long> {

    List<CarrierSegment> findByShipmentIdOrderBySeqAsc(Long shipmentId);

    Optional<CarrierSegment> findByShipmentIdAndSeq(Long shipmentId, Integer seq);

    long countByShipmentId(Long shipmentId);
}
