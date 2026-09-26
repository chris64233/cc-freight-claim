package com.chris64233.freightclaim.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 责任决定：把索赔的认可金额按比例分摊到多个承运段。
 *
 * <p>生命周期：草拟（可改分摊方案，不产生正式分录）→ 确认（一次性生成全部责任分录）。
 * 确认后、结算前若依据的证据或索赔内容发生变化，需要重开为草拟重做；
 * 一旦结算，决定永久不可变。</p>
 *
 * <p>并发安全：{@code persistVersion} 乐观锁保证并发确认/重做只有一方成功；
 * {@code claim_id} 唯一约束保证一笔索赔只存在一行决定（重做即复用该行），
 * 从根本上杜绝并发产生两套责任结果。</p>
 */
@Entity
@Table(name = "liability_decision", uniqueConstraints =
        @UniqueConstraint(name = "uk_decision_claim", columnNames = "claim_id"))
public class LiabilityDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 认可金额：责任方认可的索赔金额，不超过索赔损失金额。 */
    @Column(name = "approved_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DecisionStatus status = DecisionStatus.DRAFT;

    /** 决定所基于的交接证据版本（创建/更新时的 Shipment.evidenceVersion）。 */
    @Column(name = "based_evidence_version", nullable = false)
    private int basedEvidenceVersion;

    /** 决定所基于的索赔内容版本。 */
    @Column(name = "based_claim_version", nullable = false)
    private int basedClaimVersion;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** JPA 乐观锁：并发确认/重做只有一方提交成功。 */
    @Version
    private long persistVersion;

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LiabilityEntry> entries = new ArrayList<>();

    protected LiabilityDecision() {
    }

    public LiabilityDecision(Claim claim, BigDecimal approvedAmount,
                             int basedEvidenceVersion, int basedClaimVersion, OffsetDateTime now) {
        this.claim = claim;
        this.approvedAmount = approvedAmount;
        this.basedEvidenceVersion = basedEvidenceVersion;
        this.basedClaimVersion = basedClaimVersion;
        this.status = DecisionStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 草拟阶段更新分摊方案，同时重置依据版本为当前最新版本。 */
    public void revise(BigDecimal newApprovedAmount, int evidenceVersion, int claimVersion, OffsetDateTime now) {
        this.approvedAmount = newApprovedAmount;
        this.basedEvidenceVersion = evidenceVersion;
        this.basedClaimVersion = claimVersion;
        this.updatedAt = now;
    }

    public void addEntry(LiabilityEntry entry) {
        entry.setDecision(this);
        this.entries.add(entry);
    }

    /** 确认：分录已在服务层全部生成并校验。 */
    public void markConfirmed(OffsetDateTime now) {
        this.status = DecisionStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /** 结算前依据失效，重开为草拟以便重做分摊。 */
    public void reopenAsDraft(OffsetDateTime now) {
        this.status = DecisionStatus.DRAFT;
        this.confirmedAt = null;
        this.updatedAt = now;
    }

    public boolean isConfirmed() {
        return status == DecisionStatus.CONFIRMED;
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

    public int getBasedEvidenceVersion() {
        return basedEvidenceVersion;
    }

    public int getBasedClaimVersion() {
        return basedClaimVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public long getPersistVersion() {
        return persistVersion;
    }

    public List<LiabilityEntry> getEntries() {
        return entries;
    }
}
