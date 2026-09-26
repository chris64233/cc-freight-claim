package com.chris64233.freightclaim.domain;

import java.math.BigDecimal;
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

/** 结算单：对已确认责任决定的一次（或分次）结算。 */
@Entity
@Table(name = "settlement", uniqueConstraints = {
        @UniqueConstraint(name = "uk_settlement_no", columnNames = "settlement_no")
})
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_no", nullable = false, length = 64)
    private String settlementNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private LiabilityDecision decision;

    /** 本次结算总金额（各分录行之和）。 */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt = Instant.now();

    @Column(length = 512)
    private String remark;

    protected Settlement() {
    }

    public Settlement(String settlementNo, LiabilityDecision decision, BigDecimal totalAmount, String remark) {
        this.settlementNo = settlementNo;
        this.decision = decision;
        this.totalAmount = Money.of(totalAmount);
        this.remark = remark;
    }

    public Long getId() {
        return id;
    }

    public String getSettlementNo() {
        return settlementNo;
    }

    public LiabilityDecision getDecision() {
        return decision;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getRemark() {
        return remark;
    }
}
