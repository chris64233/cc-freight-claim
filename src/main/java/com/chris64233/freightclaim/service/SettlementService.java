package com.chris64233.freightclaim.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.freightclaim.api.dto.SettlementRequest;
import com.chris64233.freightclaim.domain.DecisionStatus;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.domain.Money;
import com.chris64233.freightclaim.domain.Settlement;
import com.chris64233.freightclaim.domain.SettlementLine;
import com.chris64233.freightclaim.exception.BusinessRuleException;
import com.chris64233.freightclaim.exception.ConflictException;
import com.chris64233.freightclaim.exception.NotFoundException;
import com.chris64233.freightclaim.repository.LiabilityDecisionRepository;
import com.chris64233.freightclaim.repository.LiabilityEntryRepository;
import com.chris64233.freightclaim.repository.SettlementLineRepository;
import com.chris64233.freightclaim.repository.SettlementRepository;

@Service
public class SettlementService {

    private final LiabilityDecisionRepository decisionRepository;
    private final LiabilityEntryRepository entryRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementLineRepository settlementLineRepository;

    public SettlementService(LiabilityDecisionRepository decisionRepository,
                             LiabilityEntryRepository entryRepository,
                             SettlementRepository settlementRepository,
                             SettlementLineRepository settlementLineRepository) {
        this.decisionRepository = decisionRepository;
        this.entryRepository = entryRepository;
        this.settlementRepository = settlementRepository;
        this.settlementLineRepository = settlementLineRepository;
    }

    private Map<Integer, LiabilityEntry> entriesBySegmentSeq(List<LiabilityEntry> entries) {
        // 同一决定下每个承运段只有一条分录
        Map<Integer, LiabilityEntry> bySeq = new LinkedHashMap<>();
        for (LiabilityEntry e : entries) {
            bySeq.put(e.getSegment().getSeq(), e);
        }
        return bySeq;
    }

    /**
     * 结算已确认的责任决定（支持一次或分次结算）。
     * 行级锁串行化并发结算；每条分录本次结算后累计已结算不得超过其认可分摊金额。
     * 结算单与全部行在同一事务内一次性落库。
     */
    @Transactional
    public Settlement settle(Long decisionId, SettlementRequest request) {
        LiabilityDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new NotFoundException("责任决定不存在: id=" + decisionId));
        if (decision.getStatus() != DecisionStatus.CONFIRMED) {
            throw new ConflictException("责任决定尚未确认，不能结算");
        }
        if (settlementRepository.findBySettlementNo(request.settlementNo()).isPresent()) {
            throw new ConflictException("结算单号已存在: " + request.settlementNo());
        }

        List<LiabilityEntry> entries = entryRepository.findByDecisionIdOrderByIdAsc(decisionId);
        Map<Integer, LiabilityEntry> bySeq = entriesBySegmentSeq(entries);

        // 先对涉及的分录按 id 排序加锁，避免死锁
        List<SettlementRequest.SettlementLineRequest> lines = request.lines().stream()
                .sorted(java.util.Comparator.comparing(SettlementRequest.SettlementLineRequest::segmentSeq))
                .toList();

        BigDecimal total = BigDecimal.ZERO.setScale(Money.SCALE);
        Map<LiabilityEntry, BigDecimal> perEntry = new LinkedHashMap<>();
        for (SettlementRequest.SettlementLineRequest line : lines) {
            LiabilityEntry entry = bySeq.get(line.segmentSeq());
            if (entry == null) {
                throw new BusinessRuleException("承运段在该责任决定中没有分摊分录: seq=" + line.segmentSeq());
            }
            BigDecimal amount = Money.of(line.amount());
            if (amount.signum() <= 0) {
                throw new BusinessRuleException("结算金额必须为正: seq=" + line.segmentSeq());
            }
            // 加行锁后读取最新累计值
            LiabilityEntry locked = entryRepository.findByIdForUpdate(entry.getId()).orElseThrow();
            BigDecimal remaining = locked.getAllocatedAmount().subtract(locked.getSettledAmount());
            if (amount.compareTo(remaining) > 0) {
                throw new BusinessRuleException("结算金额超过该承运段未结算责任余额: seq=" + line.segmentSeq()
                        + "，余额=" + remaining);
            }
            perEntry.put(locked, amount);
            total = total.add(amount);
        }

        Settlement settlement = new Settlement(request.settlementNo(), decision, total, request.remark());
        settlement = settlementRepository.save(settlement);
        for (Map.Entry<LiabilityEntry, BigDecimal> e : perEntry.entrySet()) {
            LiabilityEntry entry = e.getKey();
            entry.addSettled(e.getValue());
            entryRepository.save(entry);
            settlementLineRepository.save(new SettlementLine(settlement, entry, e.getValue()));
        }
        return settlement;
    }

    @Transactional(readOnly = true)
    public Settlement getByNo(String settlementNo) {
        return settlementRepository.findBySettlementNo(settlementNo)
                .orElseThrow(() -> new NotFoundException("结算单不存在: " + settlementNo));
    }

    @Transactional(readOnly = true)
    public List<Settlement> listByDecision(Long decisionId) {
        return settlementRepository.findByDecisionIdOrderByIdAsc(decisionId);
    }

    @Transactional(readOnly = true)
    public List<SettlementLine> listLines(Long settlementId) {
        return settlementLineRepository.findBySettlementIdOrderByIdAsc(settlementId);
    }

    /** 索赔维度结算台账。 */
    @Transactional(readOnly = true)
    public List<SettlementLine> listClaimLedger(Long claimId) {
        return settlementLineRepository.findByLiabilityEntryDecisionClaimIdOrderByIdAsc(claimId);
    }
}
