package com.chris64233.freightclaim.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;

/**
 * 运输单：承运段与交接证据的聚合根。
 *
 * <p>{@code evidenceVersion} 是交接证据的单调版本号，每追加一条交接证据加 1，
 * 责任决定基于该版本号做乐观冲突检测。</p>
 */
@Entity
@Table(name = "shipment")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String shipmentNo;

    @Column(length = 128)
    private String origin;

    @Column(length = 128)
    private String destination;

    /** 交接证据版本，从 0 开始，每追加一条证据加 1。 */
    @Column(nullable = false)
    private int evidenceVersion = 0;

    /** JPA 乐观锁，保护承运段/证据的并发追加。 */
    @Version
    private long persistVersion;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<CarrierSegment> segments = new ArrayList<>();

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<HandoverEvidence> handoverEvidences = new ArrayList<>();

    protected Shipment() {
    }

    public Shipment(String shipmentNo, String origin, String destination) {
        this.shipmentNo = shipmentNo;
        this.origin = origin;
        this.destination = destination;
    }

    public void addSegment(CarrierSegment segment) {
        segment.setShipment(this);
        this.segments.add(segment);
    }

    public void addEvidence(HandoverEvidence evidence) {
        evidence.setShipment(this);
        this.handoverEvidences.add(evidence);
        this.evidenceVersion++;
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

    public int getEvidenceVersion() {
        return evidenceVersion;
    }

    public List<CarrierSegment> getSegments() {
        return segments;
    }

    public List<HandoverEvidence> getHandoverEvidences() {
        return handoverEvidences;
    }
}
