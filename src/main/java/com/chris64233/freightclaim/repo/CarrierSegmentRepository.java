package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.CarrierSegment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierSegmentRepository extends JpaRepository<CarrierSegment, Long> {
    List<CarrierSegment> findByShipmentIdOrderBySequenceNoAsc(Long shipmentId);

    Optional<CarrierSegment> findByShipmentIdAndId(Long shipmentId, Long id);
}
