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
 * 货运损失索赔。
 *
 * <p>约束：
 * <ul>
 *   <li>外部索赔号全局唯一，保证创建幂等；</li>
 *   <li>同一运输单 + 同一损失事件只能存在一笔 {@link ClaimStatus#ACTIVE} 索赔
 *       （部分唯一索引，仅数据库层面保证并发安全）。</li>
 * </ul>
 */
@Entity
@Table(name = "claim", uniqueConstraints = {
        @UniqueConstraint(name = "uk_claim_external_no", columnNames = "external_claim_no"),
        // 等价于 (shipment_id, loss_event_no) WHERE status='ACTIVE' 的部分唯一索引：
        // 活动索赔该列为业务键，关闭后为 null（唯一索引允许重复 null），数据库层兜底并发唯一性。
        @UniqueConstraint(name = "uk_claim_active_lock", columnNames = "active_lock_key")
})
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部索赔号（业务唯一、创建幂等键）。 */
    @Column(name = "external_claim_no", nullable = false, length = 64)
    private String externalClaimNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    /** 损失事件标识：同一运输单下相同事件只允许一笔活动索赔。 */
    @Column(name = "loss_event_no", nullable = false, length = 64)
    private String lossEventNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_type", nullable = false, length = 16)
    private LossType lossType;

    /** 损失金额（申报金额）。 */
    @Column(name = "loss_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal lossAmount;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClaimStatus status = ClaimStatus.ACTIVE;

    /**
     * 索赔内容版本：损失金额/类型/描述/证据变化时递增。
     * 责任决定确认时校验决定所依据的内容版本是否仍为最新。
     */
    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    /** 创建时运输单的交接证据版本（仅留痕）。 */
    @Column(name = "shipment_evidence_version", nullable = false)
    private long shipmentEvidenceVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * 活动索赔唯一锁键：{@code shipmentId + ":" + lossEventNo}；索赔关闭后为 null。
     * 由 JPA 生命周期回调根据当前状态维护，不允许外部直接修改。
     */
    @Column(name = "active_lock_key", length = 160, insertable = true, updatable = true)
    private String activeLockKey;

    protected Claim() {
    }

    public Claim(String externalClaimNo, Shipment shipment, String lossEventNo, LossType lossType,
                 BigDecimal lossAmount, String description, long shipmentEvidenceVersion) {
        this.externalClaimNo = externalClaimNo;
        this.shipment = shipment;
        this.lossEventNo = lossEventNo;
        this.lossType = lossType;
        this.lossAmount = Money.of(lossAmount);
        this.description = description;
        this.status = ClaimStatus.ACTIVE;
        this.contentVersion = 0L;
        this.shipmentEvidenceVersion = shipmentEvidenceVersion;
        syncActiveLockKey();
    }

    /** 持久化/更新前同步唯一锁键。 */
    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void syncActiveLockKey() {
        this.activeLockKey = this.status == ClaimStatus.ACTIVE
                ? (shipment.getId() + ":" + lossEventNo)
                : null;
    }

    /** 修改索赔内容（损失金额/类型/描述）并递增内容版本。 */
    public void changeContent(LossType lossType, BigDecimal lossAmount, String description) {
        this.lossType = lossType;
        this.lossAmount = Money.of(lossAmount);
        this.description = description;
        this.contentVersion++;
        this.updatedAt = Instant.now();
    }

    /** 追加/删除索赔证据后递增内容版本。 */
    public void bumpContentVersion() {
        this.contentVersion++;
        this.updatedAt = Instant.now();
    }

    public void close() {
        this.status = ClaimStatus.CLOSED;
        this.updatedAt = Instant.now();
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

    public String getLossEventNo() {
        return lossEventNo;
    }

    public LossType getLossType() {
        return lossType;
    }

    public BigDecimal getLossAmount() {
        return lossAmount;
    }

    public String getDescription() {
        return description;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public long getShipmentEvidenceVersion() {
        return shipmentEvidenceVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
