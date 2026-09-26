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
import jakarta.persistence.UniqueConstraint;

/**
 * 责任决定：对一笔索赔作出的责任认定。
 *
 * <p>每笔索赔至多有一笔已确认决定（部分唯一索引保证并发确认不会产生两套结果）。
 * 决定记录其依据的“索赔内容版本”和“运输单交接证据版本”：
 * 确认时若任一份已过期（索赔内容变化或有新交接证据），返回版本冲突。
 */
@Entity
@Table(name = "liability_decision", uniqueConstraints = {
        // 等价于 (claim_id) WHERE status='CONFIRMED' 的部分唯一索引：
        // 已确认决定该列锁定为索赔 id，草稿时为 null（唯一索引允许重复 null）。
        @UniqueConstraint(name = "uk_decision_confirmed_lock", columnNames = "confirmed_lock_key")
})
public class LiabilityDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 认可金额（承认赔付的金额），不超过索赔损失金额。 */
    @Column(name = "approved_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DecisionStatus status = DecisionStatus.DRAFT;

    /** 决定依据的索赔内容版本。 */
    @Column(name = "based_content_version", nullable = false)
    private long basedContentVersion;

    /** 决定依据的运输单交接证据版本。 */
    @Column(name = "based_evidence_version", nullable = false)
    private long basedEvidenceVersion;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(length = 512)
    private String remark;

    /**
     * 已确认唯一锁键：确认后为索赔 id 的字符串形式，草稿时为 null。
     * 由 JPA 生命周期回调根据状态维护，保证每笔索赔至多一条已确认决定。
     */
    @Column(name = "confirmed_lock_key", length = 32)
    private String confirmedLockKey;

    protected LiabilityDecision() {
    }

    public LiabilityDecision(Claim claim, BigDecimal approvedAmount,
                             long basedContentVersion, long basedEvidenceVersion, String remark) {
        this.claim = claim;
        this.approvedAmount = Money.of(approvedAmount);
        this.status = DecisionStatus.DRAFT;
        this.basedContentVersion = basedContentVersion;
        this.basedEvidenceVersion = basedEvidenceVersion;
        this.remark = remark;
    }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void syncConfirmedLockKey() {
        this.confirmedLockKey = this.status == DecisionStatus.CONFIRMED
                ? String.valueOf(claim.getId())
                : null;
    }

    /** 草稿阶段可更新认可金额/备注（分摊明细由服务层重建）。 */
    public void revise(BigDecimal approvedAmount, String remark) {
        if (this.status != DecisionStatus.DRAFT) {
            throw new IllegalStateException("只有草稿状态的决定可以修改");
        }
        this.approvedAmount = Money.of(approvedAmount);
        this.remark = remark;
    }

    /** 草稿重新拟定后，把依据版本刷新为当前最新版本。 */
    public void refreshBasisVersions(long contentVersion, long evidenceVersion) {
        if (this.status != DecisionStatus.DRAFT) {
            throw new IllegalStateException("只有草稿状态的决定可以刷新依据版本");
        }
        this.basedContentVersion = contentVersion;
        this.basedEvidenceVersion = evidenceVersion;
    }

    public void confirm() {
        if (this.status != DecisionStatus.DRAFT) {
            throw new IllegalStateException("决定已确认，不能重复确认");
        }
        this.status = DecisionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public long getBasedContentVersion() {
        return basedContentVersion;
    }

    public long getBasedEvidenceVersion() {
        return basedEvidenceVersion;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public String getRemark() {
        return remark;
    }
}
