package com.chris64233.freightclaim.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimStatus;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Optional<Claim> findByExternalClaimNo(String externalClaimNo);

    List<Claim> findByShipmentIdOrderByIdAsc(Long shipmentId);

    /**
     * 同一运输单 + 损失事件是否已存在指定状态的索赔。
     * 活动索赔唯一性的最终保障是数据库部分唯一索引 uk_claim_active。
     */
    @Query("select count(c) > 0 from Claim c where c.shipment.id = :shipmentId "
            + "and c.lossEventNo = :lossEventNo and c.status = :status")
    boolean existsActive(@Param("shipmentId") Long shipmentId,
                         @Param("lossEventNo") String lossEventNo,
                         @Param("status") ClaimStatus status);

    /** 行锁：串行化同一索赔上的责任决定确认。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(@Param("id") Long id);
}
