package com.chris64233.freightclaim.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 交接证据：承运段之间交接环节的凭证（交接单、照片、签收记录等）。
 * 新证据追加会使运输单 evidenceVersion 递增。
 */
@Entity
@Table(name = "handover_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_handover_shipment_seq", columnNames = {"shipment_id", "seq"})
})
public class HandoverEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** 证据到达顺序，从 1 开始。 */
    @Column(nullable = false)
    private Integer seq;

    /** 交出承运段顺序（首段可为空）。 */
    @Column(name = "from_segment_seq")
    private Integer fromSegmentSeq;

    /** 接收承运段顺序。 */
    @Column(name = "to_segment_seq")
    private Integer toSegmentSeq;

    /** 凭证编号（单据号、影像号等）。 */
    @Column(name = "evidence_ref", length = 128)
    private String evidenceRef;

    @Column(length = 512)
    private String summary;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected HandoverEvidence() {
    }

    public HandoverEvidence(Shipment shipment, Integer seq, Integer fromSegmentSeq, Integer toSegmentSeq,
                            String evidenceRef, String summary) {
        this.shipment = shipment;
        this.seq = seq;
        this.fromSegmentSeq = fromSegmentSeq;
        this.toSegmentSeq = toSegmentSeq;
        this.evidenceRef = evidenceRef;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public Integer getSeq() {
        return seq;
    }

    public Integer getFromSegmentSeq() {
        return fromSegmentSeq;
    }

    public Integer getToSegmentSeq() {
        return toSegmentSeq;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
