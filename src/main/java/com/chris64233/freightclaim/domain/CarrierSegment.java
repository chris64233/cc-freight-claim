package com.chris64233.freightclaim.domain;

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
 * 承运段：运输单上按 {@code sequenceNo} 顺序发生的一段运输，由某承运商承担。
 * 责任分录最终挂在承运段上。
 */
@Entity
@Table(name = "carrier_segment", uniqueConstraints =
        @UniqueConstraint(name = "uk_segment_shipment_seq", columnNames = {"shipment_id", "sequence_no"}))
public class CarrierSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false, length = 64)
    private String carrierCode;

    @Column(length = 128)
    private String carrierName;

    @Column(length = 128)
    private String startNode;

    @Column(length = 128)
    private String endNode;

    protected CarrierSegment() {
    }

    public CarrierSegment(int sequenceNo, String carrierCode, String carrierName,
                          String startNode, String endNode) {
        this.sequenceNo = sequenceNo;
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.startNode = startNode;
        this.endNode = endNode;
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

    public int getSequenceNo() {
        return sequenceNo;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public String getStartNode() {
        return startNode;
    }

    public String getEndNode() {
        return endNode;
    }
}
