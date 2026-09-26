package com.chris64233.freightclaim.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.freightclaim.domain.HandoverEvidence;

public interface HandoverEvidenceRepository extends JpaRepository<HandoverEvidence, Long> {

    List<HandoverEvidence> findByShipmentIdOrderBySeqAsc(Long shipmentId);

    long countByShipmentId(Long shipmentId);
}
