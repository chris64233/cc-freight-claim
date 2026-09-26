package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    Optional<Claim> findByExternalClaimNo(String externalClaimNo);

    List<Claim> findByShipmentIdAndStatusOrderByIdAsc(Long shipmentId, ClaimStatus status);

    List<Claim> findByShipmentIdOrderByIdAsc(Long shipmentId);

    /** 悲观行锁：串行化同一索赔上的责任决定确认/重做/结算。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> lockById(@Param("id") Long id);
}
