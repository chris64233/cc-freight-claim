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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 货运损失索赔。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>{@code externalClaimNo} 全局唯一，是登记接口的幂等键。</li>
 *   <li>同一运输单 + 同一损失事件（{@code lossEventRef}）只能存在一笔活动索赔。
 *       通过 {@code (shipment_id, active_slot)} 唯一约束保证：活动索赔的 activeSlot 等于
 *       损失事件标识，关闭后置空（数据库唯一约束允许重复 NULL），由 DB 兜底并发竞争。</li>
 *   <li>{@code contentVersion} 是索赔内容版本，金额/损失类型/证据每变一次加 1，
 *       责任决定据此做乐观冲突检测。</li>
 * </ul>
 */
@Entity
@Table(name = "claim", uniqueConstraints = {
        @UniqueConstraint(name = "uk_claim_external_no", columnNames = "external_claim_no"),
        @UniqueConstraint(name = "uk_claim_active_event", columnNames = {"shipment_id", "active_slot"})
})
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部索赔号（业务方系统的索赔编号），幂等键。 */
    @Column(name = "external_claim_no", nullable = false, length = 64)
    private String externalClaimNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** 损失事件标识：同一运输单上同一次损失事件的业务去重键。 */
    @Column(name = "loss_event_ref", nullable = false, length = 64)
    private String lossEventRef;

    @Column(name = "loss_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal lossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_type", nullable = false, length = 20)
    private LossType lossType;

    /** 索赔证据内容（凭据编号、照片、说明等）。 */
    @Lob
    @Column(name = "evidence", nullable = false)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClaimStatus status = ClaimStatus.ACTIVE;

    /** 索赔内容版本，登记时为 1，每次内容变更加 1。 */
    @Column(name = "content_version", nullable = false)
    private int contentVersion = 1;

    /** 活动槽位：活动期间等于 lossEventRef，关闭后置空；配合唯一约束实现活动索赔唯一。 */
    @Column(name = "active_slot", length = 64)
    private String activeSlot;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected Claim() {
    }

    public Claim(String externalClaimNo, Shipment shipment, String lossEventRef,
                 BigDecimal lossAmount, LossType lossType, String evidence, OffsetDateTime now) {
        this.externalClaimNo = externalClaimNo;
        this.shipment = shipment;
        this.lossEventRef = lossEventRef;
        this.lossAmount = lossAmount;
        this.lossType = lossType;
        this.evidence = evidence;
        this.status = ClaimStatus.ACTIVE;
        this.contentVersion = 1;
        this.activeSlot = lossEventRef;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 修改索赔内容并推进内容版本。 */
    public void revise(BigDecimal newLossAmount, LossType newLossType, String newEvidence, OffsetDateTime now) {
        this.lossAmount = newLossAmount;
        this.lossType = newLossType;
        this.evidence = newEvidence;
        this.contentVersion++;
        this.updatedAt = now;
    }

    /** 关闭索赔，释放活动槽位。 */
    public void close(OffsetDateTime now) {
        this.status = ClaimStatus.CLOSED;
        this.activeSlot = null;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == ClaimStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getExternalClaimNo() {
        return externalClaimNo;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public String getLossEventRef() {
        return lossEventRef;
    }

    public BigDecimal getLossAmount() {
        return lossAmount;
    }

    public LossType getLossType() {
        return lossType;
    }

    public String getEvidence() {
        return evidence;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public String getActiveSlot() {
        return activeSlot;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
