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

/** 承运段：运输单上按顺序发生的一段承运责任区间。 */
@Entity
@Table(name = "carrier_segment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_segment_shipment_seq", columnNames = {"shipment_id", "seq"})
})
public class CarrierSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** 在运输单中的顺序，从 1 开始。 */
    @Column(nullable = false)
    private Integer seq;

    @Column(name = "carrier_code", nullable = false, length = 64)
    private String carrierCode;

    @Column(name = "carrier_name", length = 128)
    private String carrierName;

    @Column(name = "start_location", length = 128)
    private String startLocation;

    @Column(name = "end_location", length = 128)
    private String endLocation;

    protected CarrierSegment() {
    }

    public CarrierSegment(Shipment shipment, Integer seq, String carrierCode, String carrierName,
                          String startLocation, String endLocation) {
        this.shipment = shipment;
        this.seq = seq;
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
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

    public String getCarrierCode() {
        return carrierCode;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public String getEndLocation() {
        return endLocation;
    }
}
