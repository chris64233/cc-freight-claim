package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.HandoverEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverEvidenceRepository extends JpaRepository<HandoverEvidence, Long> {
    List<HandoverEvidence> findByShipmentIdOrderByIdAsc(Long shipmentId);
}
