package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByShipmentNo(String shipmentNo);
}
