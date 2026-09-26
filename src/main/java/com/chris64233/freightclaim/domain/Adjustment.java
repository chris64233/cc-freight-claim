package com.chris64233.freightclaim.domain;

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
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 结算后调整台账记录：追偿或冲回。
 *
 * <ul>
 *   <li>追偿（RECOVERY）：向责任承运段追加追回，不设上限。</li>
 *   <li>冲回（REVERSAL）：冲减已结算责任，单笔冲回后该分录累计冲回额不得超过其已结算责任金额。</li>
 * </ul>
 */
@Entity
@Table(name = "adjustment")
public class Adjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private LiabilityEntry entry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AdjustmentType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 256)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected Adjustment() {
    }

    public Adjustment(LiabilityEntry entry, AdjustmentType type, BigDecimal amount,
                      String reason, OffsetDateTime createdAt) {
        this.entry = entry;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LiabilityEntry getEntry() {
        return entry;
    }

    public AdjustmentType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
