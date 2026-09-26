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
import java.math.BigDecimal;

/**
 * 责任分录：确认责任决定时一次性生成，把认可金额落到单个承运段。
 *
 * <p>分录在决定确认后即不可变；结算后通过 {@link Adjustment} 追加追偿/冲回，
 * 不直接修改分录金额。</p>
 */
@Entity
@Table(name = "liability_entry", uniqueConstraints =
        @UniqueConstraint(name = "uk_entry_decision_segment", columnNames = {"decision_id", "segment_id"}))
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

    /** 分摊权重比例（占认可金额的比例，保留 4 位小数，例如 30% 记为 0.3000），仅作展示留痕。 */
    @Column(name = "share_ratio", nullable = false, precision = 9, scale = 4)
    private BigDecimal shareRatio;

    /** 分摊金额（2 位小数，统一舍入），全部分录之和等于认可金额。 */
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    protected LiabilityEntry() {
    }

    public LiabilityEntry(CarrierSegment segment, BigDecimal shareRatio, BigDecimal allocatedAmount) {
        this.segment = segment;
        this.shareRatio = shareRatio;
        this.allocatedAmount = allocatedAmount;
    }

    void setDecision(LiabilityDecision decision) {
        this.decision = decision;
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

    public BigDecimal getShareRatio() {
        return shareRatio;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }
}
