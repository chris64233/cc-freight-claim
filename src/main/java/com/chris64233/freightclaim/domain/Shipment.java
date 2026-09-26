package com.chris64233.freightclaim.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 运输单：一票货物的运输主记录。 */
@Entity
@Table(name = "shipment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipment_no", columnNames = "shipment_no")
})
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 运输单号（业务唯一）。 */
    @Column(name = "shipment_no", nullable = false, length = 64)
    private String shipmentNo;

    @Column(length = 128)
    private String origin;

    @Column(length = 128)
    private String destination;

    /**
     * 交接证据版本：每追加一条交接证据递增 1。
     * 责任决定确认时会校验决定所依据的版本是否仍为最新。
     */
    @Column(name = "evidence_version", nullable = false)
    private long evidenceVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Shipment() {
    }

    public Shipment(String shipmentNo, String origin, String destination) {
        this.shipmentNo = shipmentNo;
        this.origin = origin;
        this.destination = destination;
        this.evidenceVersion = 0L;
    }

    public Long getId() {
        return id;
    }

    public String getShipmentNo() {
        return shipmentNo;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public long getEvidenceVersion() {
        return evidenceVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void bumpEvidenceVersion() {
        this.evidenceVersion++;
    }
}
