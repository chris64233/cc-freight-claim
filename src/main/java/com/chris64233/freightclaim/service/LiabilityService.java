package com.chris64233.freightclaim.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.freightclaim.api.dto.DecisionConfirmRequest;
import com.chris64233.freightclaim.api.dto.DecisionRequest;
import com.chris64233.freightclaim.domain.CarrierSegment;
import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimStatus;
import com.chris64233.freightclaim.domain.DecisionAllocationLine;
import com.chris64233.freightclaim.domain.DecisionStatus;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.domain.Money;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.exception.BusinessRuleException;
import com.chris64233.freightclaim.exception.ConflictException;
import com.chris64233.freightclaim.exception.NotFoundException;
import com.chris64233.freightclaim.repository.CarrierSegmentRepository;
import com.chris64233.freightclaim.repository.ClaimRepository;
import com.chris64233.freightclaim.repository.DecisionAllocationLineRepository;
import com.chris64233.freightclaim.repository.LiabilityDecisionRepository;
import com.chris64233.freightclaim.repository.LiabilityEntryRepository;
import com.chris64233.freightclaim.repository.ShipmentRepository;

@Service
public class LiabilityService {

    private final ClaimRepository claimRepository;
    private final ShipmentRepository shipmentRepository;
    private final CarrierSegmentRepository segmentRepository;
    private final LiabilityDecisionRepository decisionRepository;
    private final DecisionAllocationLineRepository allocationLineRepository;
    private final LiabilityEntryRepository entryRepository;

    public LiabilityService(ClaimRepository claimRepository,
                            ShipmentRepository shipmentRepository,
                            CarrierSegmentRepository segmentRepository,
                            LiabilityDecisionRepository decisionRepository,
                            DecisionAllocationLineRepository allocationLineRepository,
                            LiabilityEntryRepository entryRepository) {
        this.claimRepository = claimRepository;
        this.shipmentRepository = shipmentRepository;
        this.segmentRepository = segmentRepository;
        this.decisionRepository = decisionRepository;
        this.allocationLineRepository = allocationLineRepository;
        this.entryRepository = entryRepository;
    }

    /** 把 (承运段顺序号 -> 权重) 解析为 (承运段 -> 权重)，并做合法性校验。 */
    private Map<CarrierSegment, BigDecimal> resolveAllocations(Claim claim,
                                                               List<DecisionRequest.AllocationRequest> allocations) {
        List<CarrierSegment> segments = segmentRepository.findByShipmentIdOrderBySeqAsc(claim.getShipment().getId());
        Map<Integer, CarrierSegment> bySeq = new LinkedHashMap<>();
        for (CarrierSegment s : segments) {
            bySeq.put(s.getSeq(), s);
        }
        Map<CarrierSegment, BigDecimal> weights = new LinkedHashMap<>();
        for (DecisionRequest.AllocationRequest a : allocations) {
            CarrierSegment segment = bySeq.get(a.segmentSeq());
            if (segment == null) {
                throw new BusinessRuleException("承运段顺序号不属于该运输单: " + a.segmentSeq());
            }
            if (weights.put(segment, Money.of(a.ratioWeight())) != null) {
                throw new BusinessRuleException("同一承运段出现多次分摊: seq=" + a.segmentSeq());
            }
        }
        return weights;
    }

    private void validateApproved(Claim claim, BigDecimal approved) {
        if (approved.signum() < 0) {
            throw new BusinessRuleException("认可金额不能为负");
        }
        if (approved.compareTo(claim.getLossAmount()) > 0) {
            throw new BusinessRuleException(
                    "认可金额不能超过索赔金额: " + approved + " > " + claim.getLossAmount());
        }
    }

    /** 创建责任决定草稿（每笔索赔至多一笔未确认草稿）。 */
    @Transactional
    public LiabilityDecision createDraft(Long claimId, DecisionRequest request) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("索赔不存在: id=" + claimId));
        if (claim.getStatus() != ClaimStatus.ACTIVE) {
            throw new ConflictException("索赔已关闭，不能创建责任决定");
        }
        // 允许存在多笔草稿（可能并发拟定），但每笔索赔最终至多只能有一笔确认成功；
        // 已确认后不再允许新建，由 confirmed_lock_key 唯一约束兜底。
        if (decisionRepository.findFirstByClaimIdAndStatus(claimId, DecisionStatus.CONFIRMED).isPresent()) {
            throw new ConflictException("该索赔已有确认的责任决定，不能重复认定");
        }

        BigDecimal approved = Money.of(request.approvedAmount());
        validateApproved(claim, approved);
        Map<CarrierSegment, BigDecimal> weights = resolveAllocations(claim, request.allocations());

        LiabilityDecision decision = new LiabilityDecision(claim, approved,
                claim.getContentVersion(), claim.getShipment().getEvidenceVersion(), request.remark());
        decision = decisionRepository.save(decision);
        for (Map.Entry<CarrierSegment, BigDecimal> e : weights.entrySet()) {
            allocationLineRepository.save(new DecisionAllocationLine(decision, e.getKey(), e.getValue()));
        }
        return decision;
    }

    /** 修改草稿（认可金额/备注/分摊权重），并把依据版本刷新到当前最新。 */
    @Transactional
    public LiabilityDecision updateDraft(Long decisionId, DecisionRequest request) {
        LiabilityDecision decision = getDecision(decisionId);
        if (decision.getStatus() != DecisionStatus.DRAFT) {
            throw new ConflictException("责任决定已确认，不能修改");
        }
        Claim claim = decision.getClaim();
        BigDecimal approved = Money.of(request.approvedAmount());
        validateApproved(claim, approved);
        Map<CarrierSegment, BigDecimal> weights = resolveAllocations(claim, request.allocations());

        decision.revise(approved, request.remark());
        // 重新拟定草稿即表示在最新证据/内容基础上认定，刷新依据版本
        decision.refreshBasisVersions(claim.getContentVersion(),
                claim.getShipment().getEvidenceVersion());
        decision = decisionRepository.save(decision);

        allocationLineRepository.deleteByDecisionId(decisionId);
        allocationLineRepository.flush();
        for (Map.Entry<CarrierSegment, BigDecimal> e : weights.entrySet()) {
            allocationLineRepository.save(new DecisionAllocationLine(decision, e.getKey(), e.getValue()));
        }
        return decision;
    }

    /**
     * 确认责任决定。
     *
     * <p>并发与版本控制：
     * <ol>
     *   <li>依次对索赔行、运输单行加悲观写锁，串行化“证据追加/索赔变更”与“确认”；</li>
     *   <li>校验决定仍是草稿、依据的内容版本与证据版本均为最新（客户端可附期望版本，过期直接冲突）；</li>
     *   <li>按权重把认可金额比例分摊（最大余额法保证各分录之和严格等于认可金额），
     *       一次性生成全部责任分录；</li>
     *   <li>confirmed_lock_key 唯一约束兜底：并发确认只有一方成功，绝不产生两套结果。</li>
     * </ol>
     */
    @Transactional
    public LiabilityDecision confirm(Long decisionId, DecisionConfirmRequest request) {
        LiabilityDecision decision = getDecision(decisionId);
        // 固定加锁顺序：先 claim，再 shipment，避免死锁
        Claim claim = claimRepository.findByIdForUpdate(decision.getClaim().getId())
                .orElseThrow(() -> new NotFoundException("索赔不存在"));
        Shipment shipment = shipmentRepository.findByIdForUpdate(claim.getShipment().getId())
                .orElseThrow(() -> new NotFoundException("运输单不存在"));
        // 重新读取决定行（持锁后刷新状态）
        decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new NotFoundException("责任决定不存在"));

        if (decision.getStatus() != DecisionStatus.DRAFT) {
            throw new ConflictException("责任决定已确认，不能重复确认");
        }

        if (request != null) {
            if (request.expectedContentVersion() != null
                    && request.expectedContentVersion() != claim.getContentVersion()) {
                throw new ConflictException("客户端索赔内容版本已过期，请刷新后重试");
            }
            if (request.expectedEvidenceVersion() != null
                    && request.expectedEvidenceVersion() != shipment.getEvidenceVersion()) {
                throw new ConflictException("客户端交接证据版本已过期，请刷新后重试");
            }
        }

        if (decision.getBasedContentVersion() != claim.getContentVersion()) {
            throw new ConflictException("索赔内容已变化，责任决定基于旧内容版本，请重新拟定分摊方案");
        }
        if (decision.getBasedEvidenceVersion() != shipment.getEvidenceVersion()) {
            throw new ConflictException("有新交接证据到达，责任决定基于旧证据版本，请重新拟定分摊方案");
        }
        if (claim.getStatus() != ClaimStatus.ACTIVE) {
            throw new ConflictException("索赔已关闭，不能确认责任决定");
        }

        List<DecisionAllocationLine> lines = allocationLineRepository.findByDecisionIdOrderByIdAsc(decisionId);
        if (lines.isEmpty()) {
            throw new BusinessRuleException("责任决定没有分摊方案");
        }
        // 认可金额最终校验（防止草稿期与索赔金额脱节）
        validateApproved(claim, decision.getApprovedAmount());

        List<CarrierSegment> orderedSegments = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (DecisionAllocationLine line : lines) {
            orderedSegments.add(line.getSegment());
            weights.add(line.getRatioWeight());
        }
        List<BigDecimal> allocated = Money.allocate(decision.getApprovedAmount(), weights);

        try {
            decision.confirm();
            decisionRepository.saveAndFlush(decision); // 先抢 confirmed_lock_key 唯一锁
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("并发责任决定冲突：该索赔已存在确认结果");
        }

        for (int i = 0; i < orderedSegments.size(); i++) {
            entryRepository.save(new LiabilityEntry(decision, orderedSegments.get(i),
                    weights.get(i), allocated.get(i)));
        }
        return decision;
    }

    @Transactional(readOnly = true)
    public LiabilityDecision getDecision(Long decisionId) {
        return decisionRepository.findById(decisionId)
                .orElseThrow(() -> new NotFoundException("责任决定不存在: id=" + decisionId));
    }

    @Transactional(readOnly = true)
    public List<LiabilityDecision> listByClaim(Long claimId) {
        return decisionRepository.findByClaimIdOrderByIdAsc(claimId);
    }

    @Transactional(readOnly = true)
    public List<LiabilityEntry> listEntries(Long decisionId) {
        return entryRepository.findByDecisionIdOrderByIdAsc(decisionId);
    }

    /** 读取索赔当前已确认的责任决定。 */
    @Transactional(readOnly = true)
    public LiabilityDecision getConfirmedDecision(Long claimId) {
        return decisionRepository.findFirstByClaimIdAndStatus(claimId, DecisionStatus.CONFIRMED)
                .orElseThrow(() -> new NotFoundException("该索赔没有已确认的责任决定"));
    }

    /** 草稿分摊方案（按承运段顺序号排序）。 */
    @Transactional(readOnly = true)
    public List<DecisionAllocationLine> listDraftAllocations(Long decisionId) {
        return allocationLineRepository.findByDecisionIdOrderByIdAsc(decisionId).stream()
                .sorted(Comparator.comparingInt(l -> l.getSegment().getSeq()))
                .toList();
    }
}
