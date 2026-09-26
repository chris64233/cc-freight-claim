package com.chris64233.freightclaim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 结算记录：已确认责任决定一次性结算。结算后决定与分录全部冻结，
 * 只能追加 {@link Adjustment}（追偿/冲回）。
 */
@Entity
@Table(name = "settlement")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, unique = true)
    private LiabilityDecision decision;

    /** 结算金额：确认时各责任分录金额之和（= 认可金额），结算时点留痕。 */
    @Column(name = "settled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(nullable = false)
    private OffsetDateTime settledAt;

    @Column(length = 256)
    private String remark;

    protected Settlement() {
    }

    public Settlement(LiabilityDecision decision, BigDecimal settledAmount, OffsetDateTime settledAt, String remark) {
        this.decision = decision;
        this.settledAmount = settledAmount;
        this.settledAt = settledAt;
        this.remark = remark;
    }

    public Long getId() {
        return id;
    }

    public LiabilityDecision getDecision() {
        return decision;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public String getRemark() {
        return remark;
    }
}
