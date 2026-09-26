package com.chris64233.freightclaim.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 调整台账记录：责任决定结算后只能追加记录，不能改单。
 * <ul>
 *   <li>{@link AdjustmentType#RECOVERY} 追偿：累计追偿不超过该分录认可分摊金额；</li>
 *   <li>{@link AdjustmentType#REVERSAL} 冲回：累计冲回不超过该分录已结算金额。</li>
 * </ul>
 */
@Entity
@Table(name = "adjustment_record")
public class AdjustmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liability_entry_id", nullable = false)
    private LiabilityEntry liabilityEntry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdjustmentType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 业务凭证号（可选）。 */
    @Column(name = "adjustment_ref", length = 64)
    private String adjustmentRef;

    @Column(length = 512)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AdjustmentRecord() {
    }

    public AdjustmentRecord(LiabilityEntry liabilityEntry, AdjustmentType type, BigDecimal amount,
                            String adjustmentRef, String remark) {
        this.liabilityEntry = liabilityEntry;
        this.type = type;
        this.amount = Money.of(amount);
        this.adjustmentRef = adjustmentRef;
        this.remark = remark;
    }

    public Long getId() {
        return id;
    }

    public LiabilityEntry getLiabilityEntry() {
        return liabilityEntry;
    }

    public AdjustmentType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAdjustmentRef() {
        return adjustmentRef;
    }

    public String getRemark() {
        return remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
