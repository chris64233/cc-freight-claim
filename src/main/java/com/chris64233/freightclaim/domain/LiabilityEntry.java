package com.chris64233.freightclaim.domain;

import java.math.BigDecimal;

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
 * 责任分录：责任决定确认时一次性生成的承运段分摊明细。
 * 同一决定下同一承运段至多一条；各分录金额之和严格等于决定认可金额。
 */
@Entity
@Table(name = "liability_entry", uniqueConstraints = {
        @UniqueConstraint(name = "uk_entry_decision_segment",
                columnNames = {"decision_id", "segment_id"})
})
public class LiabilityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private LiabilityDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false)
    private CarrierSegment segment;

    /** 分摊比例（权重），非负。 */
    @Column(name = "ratio_weight", nullable = false, precision = 12, scale = 6)
    private BigDecimal ratioWeight;

    /** 分摊金额，2 位小数。 */
    @Column(name = "allocated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal allocatedAmount;

    /** 已结算金额。 */
    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO.setScale(Money.SCALE);

    /** 已追偿金额累计。 */
    @Column(name = "recovered_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO.setScale(Money.SCALE);

    /** 已冲回金额累计，不得超过已结算金额。 */
    @Column(name = "reversed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal reversedAmount = BigDecimal.ZERO.setScale(Money.SCALE);

    protected LiabilityEntry() {
    }

    public LiabilityEntry(LiabilityDecision decision, CarrierSegment segment,
                          BigDecimal ratioWeight, BigDecimal allocatedAmount) {
        this.decision = decision;
        this.segment = segment;
        this.ratioWeight = ratioWeight;
        this.allocatedAmount = Money.of(allocatedAmount);
    }

    /** 追加已结算金额。 */
    public void addSettled(BigDecimal amount) {
        this.settledAmount = Money.of(this.settledAmount.add(amount));
    }

    /** 追加追偿。 */
    public void addRecovery(BigDecimal amount) {
        this.recoveredAmount = Money.of(this.recoveredAmount.add(amount));
    }

    /**
     * 追加冲回，冲回后累计冲回不得超过已结算金额。
     *
     * @return 当前可冲回余额（已结算 - 已冲回）
     */
    public BigDecimal reversibleRemaining() {
        return Money.of(this.settledAmount.subtract(this.reversedAmount));
    }

    public void addReversal(BigDecimal amount) {
        BigDecimal next = Money.of(this.reversedAmount.add(amount));
        if (next.compareTo(this.settledAmount) > 0) {
            throw new IllegalArgumentException("冲回金额超过对应已结算责任");
        }
        this.reversedAmount = next;
    }

    public Long getId() {
        return id;
    }

    public LiabilityDecision getDecision() {
        return decision;
    }

    public CarrierSegment getSegment() {
        return segment;
    }

    public BigDecimal getRatioWeight() {
        return ratioWeight;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public BigDecimal getRecoveredAmount() {
        return recoveredAmount;
    }

    public BigDecimal getReversedAmount() {
        return reversedAmount;
    }
}
