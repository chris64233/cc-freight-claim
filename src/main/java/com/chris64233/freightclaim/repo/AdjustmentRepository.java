package com.chris64233.freightclaim.repo;

import com.chris64233.freightclaim.domain.Adjustment;
import com.chris64233.freightclaim.domain.AdjustmentType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

    List<Adjustment> findByEntryIdOrderByIdAsc(Long entryId);

    List<Adjustment> findByEntryDecisionClaimIdOrderByIdAsc(Long claimId);

    /** 某条责任分录下指定类型调整的累计金额。 */
    @Query("select coalesce(sum(a.amount), 0) from Adjustment a "
            + "where a.entry.id = :entryId and a.type = :type")
    BigDecimal sumByEntryAndType(@Param("entryId") Long entryId, @Param("type") AdjustmentType type);
}
