package com.chris64233.freightclaim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 交接证据：运输单在相邻承运段交接时产生的凭据（签收单、验货记录、照片编号等）。
 * 每新增一条都会推高 {@link Shipment#getEvidenceVersion()}。
 */
@Entity
@Table(name = "handover_evidence")
public class HandoverEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** 交接发生在第几个承运段之后（0 表示起运交接）。 */
    @Column(nullable = false)
    private int afterSegmentSeq;

    /** 交接地点。 */
    @Column(length = 128)
    private String location;

    /** 证据内容/凭据编号/说明。 */
    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private OffsetDateTime recordedAt;

    protected HandoverEvidence() {
    }

    public HandoverEvidence(int afterSegmentSeq, String location, String content, OffsetDateTime recordedAt) {
        this.afterSegmentSeq = afterSegmentSeq;
        this.location = location;
        this.content = content;
        this.recordedAt = recordedAt;
    }

    void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public Long getId() {
        return id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public int getAfterSegmentSeq() {
        return afterSegmentSeq;
    }

    public String getLocation() {
        return location;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}
