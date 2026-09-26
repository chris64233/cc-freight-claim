package com.chris64233.freightclaim.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.freightclaim.api.dto.AdjustmentRequest;
import com.chris64233.freightclaim.domain.AdjustmentRecord;
import com.chris64233.freightclaim.domain.AdjustmentType;
import com.chris64233.freightclaim.domain.DecisionStatus;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.domain.Money;
import com.chris64233.freightclaim.exception.BusinessRuleException;
import com.chris64233.freightclaim.exception.ConflictException;
import com.chris64233.freightclaim.exception.NotFoundException;
import com.chris64233.freightclaim.repository.AdjustmentRecordRepository;
import com.chris64233.freightclaim.repository.LiabilityDecisionRepository;
import com.chris64233.freightclaim.repository.LiabilityEntryRepository;

/**
 * 结算后调整：责任决定确认/结算后不可修改，只能追加追偿或冲回记录。
 * 行级锁保证并发调整不超额。
 */
@Service
public class AdjustmentService {

    private final LiabilityDecisionRepository decisionRepository;
    private final LiabilityEntryRepository entryRepository;
    private final AdjustmentRecordRepository adjustmentRepository;

    public AdjustmentService(LiabilityDecisionRepository decisionRepository,
                             LiabilityEntryRepository entryRepository,
                             AdjustmentRecordRepository adjustmentRepository) {
        this.decisionRepository = decisionRepository;
        this.entryRepository = entryRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional
    public AdjustmentRecord addAdjustment(Long decisionId, AdjustmentRequest request) {
        LiabilityDecision decision = decisionRepository.findById(decisionId)
                .orElseThrow(() -> new NotFoundException("责任决定不存在: id=" + decisionId));
        if (decision.getStatus() != DecisionStatus.CONFIRMED) {
            throw new ConflictException("责任决定尚未确认，不能追加调整记录");
        }

        LiabilityEntry target = entryRepository.findByDecisionIdOrderByIdAsc(decisionId).stream()
                .filter(e -> e.getSegment().getSeq().equals(request.segmentSeq()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "承运段在该责任决定中没有分摊分录: seq=" + request.segmentSeq()));

        BigDecimal amount = Money.of(request.amount());
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("调整金额必须为正");
        }

        // 行锁：串行化并发追偿/冲回
        LiabilityEntry entry = entryRepository.findByIdForUpdate(target.getId()).orElseThrow();

        if (request.type() == AdjustmentType.RECOVERY) {
            BigDecimal recoveryRemaining =
                    entry.getAllocatedAmount().subtract(entry.getRecoveredAmount());
            if (amount.compareTo(recoveryRemaining) > 0) {
                throw new BusinessRuleException("追偿金额超过该承运段认可分摊余额，余额=" + recoveryRemaining);
            }
            entry.addRecovery(amount);
        } else {
            // 冲回：累计不得超过对应已结算责任（已结算 - 已冲回）
            if (entry.getSettledAmount().signum() <= 0) {
                throw new BusinessRuleException("该承运段责任尚未结算，不能冲回");
            }
            BigDecimal reversible = entry.reversibleRemaining();
            if (amount.compareTo(reversible) > 0) {
                throw new BusinessRuleException("冲回金额超过对应已结算责任，可冲回余额=" + reversible);
            }
            entry.addReversal(amount);
        }
        entryRepository.save(entry);

        AdjustmentRecord record = new AdjustmentRecord(entry, request.type(), amount,
                request.adjustmentRef(), request.remark());
        return adjustmentRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<AdjustmentRecord> listByEntry(Long liabilityEntryId) {
        return adjustmentRepository.findByLiabilityEntryIdOrderByIdAsc(liabilityEntryId);
    }

    /** 索赔维度调整台账。 */
    @Transactional(readOnly = true)
    public List<AdjustmentRecord> listClaimLedger(Long claimId) {
        return adjustmentRepository.findByLiabilityEntryDecisionClaimIdOrderByIdAsc(claimId);
    }
}
