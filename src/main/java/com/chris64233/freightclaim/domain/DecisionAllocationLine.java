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
 * 责任决定草稿的分摊方案行（按承运段配置的分摊权重）。
 * 正式 {@link LiabilityEntry} 只在决定确认时一次性生成。
 */
@Entity
@Table(name = "decision_allocation_line", uniqueConstraints = {
        @UniqueConstraint(name = "uk_alloc_decision_segment",
                columnNames = {"decision_id", "segment_id"})
})
public class DecisionAllocationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    private LiabilityDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false)
    private CarrierSegment segment;

    @Column(name = "ratio_weight", nullable = false, precision = 12, scale = 6)
    private BigDecimal ratioWeight;

    protected DecisionAllocationLine() {
    }

    public DecisionAllocationLine(LiabilityDecision decision, CarrierSegment segment, BigDecimal ratioWeight) {
        this.decision = decision;
        this.segment = segment;
        this.ratioWeight = ratioWeight;
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
}
