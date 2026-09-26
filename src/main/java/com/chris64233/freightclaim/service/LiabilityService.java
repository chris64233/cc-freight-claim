package com.chris64233.freightclaim.service;

import com.chris64233.freightclaim.domain.Adjustment;
import com.chris64233.freightclaim.domain.AdjustmentType;
import com.chris64233.freightclaim.domain.CarrierSegment;
import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.domain.Settlement;
import com.chris64233.freightclaim.repo.AdjustmentRepository;
import com.chris64233.freightclaim.repo.CarrierSegmentRepository;
import com.chris64233.freightclaim.repo.ClaimRepository;
import com.chris64233.freightclaim.repo.LiabilityDecisionRepository;
import com.chris64233.freightclaim.repo.LiabilityEntryRepository;
import com.chris64233.freightclaim.repo.SettlementRepository;
import com.chris64233.freightclaim.support.ConflictException;
import com.chris64233.freightclaim.support.Money;
import com.chris64233.freightclaim.support.NotFoundException;
import com.chris64233.freightclaim.support.ValidationException;
import com.chris64233.freightclaim.web.view.AdjustmentView;
import com.chris64233.freightclaim.web.view.DecisionView;
import com.chris64233.freightclaim.web.view.EntryView;
import com.chris64233.freightclaim.web.view.SettlementView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 责任认定、结算与结算后调整。
 *
 * <p>并发策略：所有对同一索赔责任决定的写操作先以 {@code PESSIMISTIC_WRITE} 锁定索赔行，
 * 串行化确认/重做/结算；配合 {@code liability_decision.claim_id} 唯一约束，
 * 并发责任决定不可能产生两套结果。分录冲回则锁定责任分录行，防止并发冲回超额。</p>
 *
 * <p>版本策略：决定记录所基于的交接证据版本与索赔内容版本；调用方可携带期望版本做乐观检测，
 * 已确认决定在结算时若依据版本落后于当前版本，一律返回冲突，须先重做。</p>
 */
@Service
public class LiabilityService {

    /** 分摊比例留痕精度（4 位小数）。 */
    private static final int RATIO_SCALE = 4;

    private final ClaimRepository claims;
    private final LiabilityDecisionRepository decisions;
    private final LiabilityEntryRepository entries;
    private final CarrierSegmentRepository segments;
    private final SettlementRepository settlements;
    private final AdjustmentRepository adjustments;

    public LiabilityService(ClaimRepository claims,
                            LiabilityDecisionRepository decisions,
                            LiabilityEntryRepository entries,
                            CarrierSegmentRepository segments,
                            SettlementRepository settlements,
                            AdjustmentRepository adjustments) {
        this.claims = claims;
        this.decisions = decisions;
        this.entries = entries;
        this.segments = segments;
        this.settlements = settlements;
        this.adjustments = adjustments;
    }

    // ---------------------------------------------------------------------
    // 责任决定：草拟 / 确认 / 重开
    // ---------------------------------------------------------------------

    /**
     * 保存草拟分摊方案（可反复修改，不产生正式责任分录语义；分录随草稿持久化以便预览，
     * 确认时以最终方案一次性重建）。
     *
     * @param expectedEvidenceVersion 调用方持有的证据版本，非空时与当前版本不一致即冲突
     * @param expectedClaimVersion    调用方持有的索赔内容版本，非空时与当前版本不一致即冲突
     */
    @Transactional
    public DecisionView draft(String externalClaimNo, BigDecimal approvedAmount,
                              List<AllocationItem> allocations,
                              Integer expectedEvidenceVersion, Integer expectedClaimVersion) {
        Claim claim = lockClaim(externalClaimNo);
        requireNotSettled(claim);
        checkExpectedVersions(claim, expectedEvidenceVersion, expectedClaimVersion);
        LiabilityDecision decision = applyDecision(claim, approvedAmount, allocations, false);
        return toDecisionView(decision, false);
    }

    /**
     * 确认责任决定：校验分摊规则后一次性生成全部责任分录。
     *
     * <p>若同一索赔已有已确认决定：依据版本仍是最新且方案一致，视为重试，幂等返回；
     * 依据版本已落后或方案不同，返回冲突，需先重开重做。</p>
     */
    @Transactional
    public DecisionView confirm(String externalClaimNo, BigDecimal approvedAmount,
                                List<AllocationItem> allocations,
                                Integer expectedEvidenceVersion, Integer expectedClaimVersion) {
        Claim claim = lockClaim(externalClaimNo);
        requireNotSettled(claim);
        checkExpectedVersions(claim, expectedEvidenceVersion, expectedClaimVersion);

        LiabilityDecision existing = decisions.findByClaimId(claim.getId()).orElse(null);
        if (existing != null && existing.isConfirmed()) {
            // 已确认决定：依据必须仍为最新版本，否则旧版本决定一律冲突。
            requireBasedOnCurrent(claim, existing);
            DecisionPlan requested = buildPlan(claim, approvedAmount, allocations);
            if (!planMatches(existing, requested)) {
                throw new ConflictException(
                        "责任决定已确认且分摊方案与本次请求不一致，请先重开决定再修改: "
                                + externalClaimNo);
            }
            // 同一方案的重复确认按幂等重试用意处理：直接返回既有结果，绝不产生第二套分录。
            return toDecisionView(existing, false);
        }
        LiabilityDecision decision = applyDecision(claim, approvedAmount, allocations, true);
        return toDecisionView(decision, false);
    }

    /**
     * 重开已确认但尚未结算的决定为草拟，以便在证据/索赔内容变化后重做分摊。
     * 必须携带当初决定所基于的版本；与当前版本一致时无需重做（直接返回），
     * 调用方也可不携带版本强制重开。
     */
    @Transactional
    public DecisionView reopen(String externalClaimNo) {
        Claim claim = lockClaim(externalClaimNo);
        requireNotSettled(claim);
        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseThrow(() -> new ValidationException("索赔尚无责任决定，无需重开: " + externalClaimNo));
        if (!decision.isConfirmed()) {
            return toDecisionView(decision, false);
        }
        wipeEntries(decision);
        decision.reopenAsDraft(now());
        return toDecisionView(decision, false);
    }

    /**
     * 落地分摊方案：校验金额与承运段归属、按权重分摊、重建全部责任分录。
     *
     * @param confirm true 时在重建后一次性确认
     */
    private LiabilityDecision applyDecision(Claim claim, BigDecimal approvedAmountInput,
                                            List<AllocationItem> allocations, boolean confirm) {
        DecisionPlan plan = buildPlan(claim, approvedAmountInput, allocations);

        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseGet(() -> new LiabilityDecision(claim, plan.approvedAmount(),
                        claim.getShipment().getEvidenceVersion(), claim.getContentVersion(), now()));
        if (decision.isConfirmed()) {
            // 正常路径下确认后的决定必须先重开；兜底防御。
            throw new ConflictException("责任决定已确认，请先重开再修改: " + claim.getExternalClaimNo());
        }
        wipeEntries(decision);
        decision.revise(plan.approvedAmount(),
                claim.getShipment().getEvidenceVersion(), claim.getContentVersion(), now());
        for (PlannedEntry pe : plan.entries()) {
            decision.addEntry(new LiabilityEntry(pe.segment(), pe.ratio(), pe.amount()));
        }

        LiabilityDecision saved = decisions.saveAndFlush(decision);
        if (confirm) {
            saved.markConfirmed(now());
        }
        return saved;
    }

    /**
     * 删除决定下已有的全部责任分录。
     *
     * <p>解除集合关联后必须立即 flush：orphanRemoval 的删除在 flush 时才落库，
     * 若不清不楚地继续插入，Hibernate 在同一事务里可能"先插新分录后删旧分录"，
     * 与 {@code (decision_id, segment_id)} 唯一约束冲突（分摊承运段重叠时必然触发）。
     * 先清空 + flush 使 DELETE 先于新 INSERT 执行。</p>
     */
    private void wipeEntries(LiabilityDecision decision) {
        if (!decision.getEntries().isEmpty()) {
            decision.getEntries().clear();
            entries.flush();
        }
    }

    /** 校验入参并纯计算分摊方案（认可金额、每段承运段/占比/金额）。 */
    private DecisionPlan buildPlan(Claim claim, BigDecimal approvedAmountInput,
                                   List<AllocationItem> allocations) {
        if (!claim.isActive()) {
            throw new ConflictException("索赔不是活动状态，不能作出责任决定: " + claim.getExternalClaimNo());
        }
        BigDecimal approvedAmount = Money.requirePositive(approvedAmountInput, "认可金额");
        if (approvedAmount.compareTo(claim.getLossAmount()) > 0) {
            throw new ValidationException(
                    "认可金额 " + approvedAmount + " 不能超过索赔金额 " + claim.getLossAmount());
        }
        List<WeightedSegment> weighted = resolveAllocations(claim, allocations);

        List<BigDecimal> weights = weighted.stream().map(WeightedSegment::weight).toList();
        List<BigDecimal> amounts = Money.allocateByWeights(approvedAmount, weights);

        // 硬性不变量：分摊之和必须恰好等于认可金额。
        BigDecimal sum = amounts.stream().reduce(Money.ZERO, BigDecimal::add);
        if (sum.compareTo(approvedAmount) != 0) {
            throw new ConflictException("分摊金额之和 " + sum + " 不等于认可金额 " + approvedAmount);
        }

        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PlannedEntry> planned = new ArrayList<>();
        for (int i = 0; i < weighted.size(); i++) {
            CarrierSegment segment = weighted.get(i).segment();
            BigDecimal ratio = weights.get(i).divide(weightSum, RATIO_SCALE, Money.ROUNDING);
            planned.add(new PlannedEntry(segment, ratio, amounts.get(i)));
        }
        return new DecisionPlan(approvedAmount, planned);
    }

    /** 请求方案与已确认方案是否一致（认可金额、承运段集合、各段分摊金额一致即视为同一方案）。 */
    private boolean planMatches(LiabilityDecision existing, DecisionPlan requested) {
        if (existing.getApprovedAmount().compareTo(requested.approvedAmount()) != 0) {
            return false;
        }
        if (existing.getEntries().size() != requested.entries().size()) {
            return false;
        }
        for (PlannedEntry pe : requested.entries()) {
            boolean match = existing.getEntries().stream().anyMatch(e ->
                    e.getSegment().getId().equals(pe.segment().getId())
                            && e.getAllocatedAmount().compareTo(pe.amount()) == 0);
            if (!match) {
                return false;
            }
        }
        return true;
    }

    private record PlannedEntry(CarrierSegment segment, BigDecimal ratio, BigDecimal amount) {
    }

    private record DecisionPlan(BigDecimal approvedAmount, List<PlannedEntry> entries) {
    }

    private record WeightedSegment(CarrierSegment segment, BigDecimal weight) {
    }

    private List<WeightedSegment> resolveAllocations(Claim claim, List<AllocationItem> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new ValidationException("至少需要一个责任分摊承运段");
        }
        List<CarrierSegment> shipmentSegments =
                segments.findByShipmentIdOrderBySequenceNoAsc(claim.getShipment().getId());
        if (shipmentSegments.isEmpty()) {
            throw new ValidationException("运输单尚无承运段，不能分摊责任");
        }
        List<WeightedSegment> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AllocationItem item : allocations) {
            if (item == null || item.segmentId() == null) {
                throw new ValidationException("分摊承运段 ID 不能为空");
            }
            if (!seen.add(item.segmentId())) {
                throw new ValidationException("同一承运段在一次分摊中不能出现多次: " + item.segmentId());
            }
            BigDecimal weight = Money.requirePositive(item.weight(), "分摊权重");
            CarrierSegment segment = shipmentSegments.stream()
                    .filter(s -> s.getId().equals(item.segmentId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException(
                            "承运段 " + item.segmentId() + " 不属于运输单 " + claim.getShipment().getShipmentNo()));
            result.add(new WeightedSegment(segment, weight));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // 结算
    // ---------------------------------------------------------------------

    /** 结算已确认责任决定。结算后决定冻结，只能追加追偿/冲回。 */
    @Transactional
    public SettlementView settle(String externalClaimNo, String remark) {
        Claim claim = lockClaim(externalClaimNo);
        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseThrow(() -> new ValidationException("索赔尚无责任决定，不能结算: " + externalClaimNo));
        if (!decision.isConfirmed()) {
            throw new ConflictException("责任决定尚未确认，不能结算: " + externalClaimNo);
        }
        requireBasedOnCurrent(claim, decision);
        Settlement existing = settlements.findByDecisionId(decision.getId()).orElse(null);
        if (existing != null) {
            // 结算重试幂等返回。
            return toSettlementView(existing);
        }
        BigDecimal total = decision.getEntries().stream()
                .map(LiabilityEntry::getAllocatedAmount)
                .reduce(Money.ZERO, BigDecimal::add);
        Settlement settlement = new Settlement(decision, total, now(), remark);
        settlements.saveAndFlush(settlement);
        return toSettlementView(settlement);
    }

    // ---------------------------------------------------------------------
    // 结算后调整：追偿 / 冲回
    // ---------------------------------------------------------------------

    /**
     * 追加追偿或冲回。仅在结算后允许；冲回累计金额不得超过对应承运段的已结算责任金额。
     */
    @Transactional
    public AdjustmentView addAdjustment(String externalClaimNo, Long entryId, AdjustmentType type,
                                        BigDecimal amountInput, String reason) {
        if (type == null) {
            throw new ValidationException("调整类型不能为空");
        }
        BigDecimal amount = Money.requirePositive(amountInput, "调整金额");
        Claim claim = claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseThrow(() -> new ValidationException("索赔尚无责任决定: " + externalClaimNo));
        Settlement settlement = settlements.findByDecisionId(decision.getId())
                .orElseThrow(() -> new ConflictException(
                        "责任决定尚未结算，结算前请直接修改决定而非追加调整: " + externalClaimNo));

        // 锁定分录行，串行化并发冲回。
        LiabilityEntry entry = entries.lockById(entryId)
                .orElseThrow(() -> new NotFoundException("责任分录不存在: " + entryId));
        if (!entry.getDecision().getId().equals(decision.getId())) {
            throw new ValidationException(
                    "责任分录 " + entryId + " 不属于索赔 " + externalClaimNo + " 的责任决定");
        }

        if (type == AdjustmentType.REVERSAL) {
            BigDecimal alreadyReversed =
                    adjustments.sumByEntryAndType(entry.getId(), AdjustmentType.REVERSAL);
            BigDecimal remaining = entry.getAllocatedAmount().subtract(alreadyReversed);
            if (amount.compareTo(remaining) > 0) {
                throw new ConflictException("冲回金额 " + amount + " 超过该承运段剩余可冲回额度 "
                        + remaining + "（已结算责任 " + entry.getAllocatedAmount()
                        + "，累计冲回 " + alreadyReversed + "）");
            }
        }
        Adjustment adjustment =
                adjustments.saveAndFlush(new Adjustment(entry, type, amount, reason, now()));
        return toAdjustmentView(adjustment);
    }

    // ---------------------------------------------------------------------
    // 台账查询
    // ---------------------------------------------------------------------

    /** 责任分摊台账。 */
    @Transactional(readOnly = true)
    public DecisionView getDecision(String externalClaimNo) {
        Claim claim = claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseThrow(() -> new NotFoundException("索赔尚无责任决定: " + externalClaimNo));
        boolean settled = settlements.existsByDecisionClaimId(claim.getId());
        return toDecisionView(decision, settled);
    }

    /** 结算台账。 */
    @Transactional(readOnly = true)
    public SettlementView getSettlement(String externalClaimNo) {
        Claim claim = claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
        LiabilityDecision decision = decisions.findByClaimId(claim.getId())
                .orElseThrow(() -> new NotFoundException("索赔尚无责任决定: " + externalClaimNo));
        Settlement settlement = settlements.findByDecisionId(decision.getId())
                .orElseThrow(() -> new NotFoundException("索赔尚未结算: " + externalClaimNo));
        return toSettlementView(settlement);
    }

    /** 调整台账：某索赔结算后全部追偿/冲回记录。 */
    @Transactional(readOnly = true)
    public List<AdjustmentView> listAdjustments(String externalClaimNo) {
        Claim claim = claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
        return adjustments.findByEntryDecisionClaimIdOrderByIdAsc(claim.getId()).stream()
                .map(this::toAdjustmentView)
                .toList();
    }

    // ---------------------------------------------------------------------
    // 内部规则
    // ---------------------------------------------------------------------

    private Claim lockClaim(String externalClaimNo) {
        Claim claim = claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
        // SELECT ... FOR UPDATE：同一索赔上的并发责任决定在此排队。
        return claims.lockById(claim.getId()).orElseThrow();
    }

    private void requireNotSettled(Claim claim) {
        if (settlements.existsByDecisionClaimId(claim.getId())) {
            throw new ConflictException(
                    "索赔已结算，责任决定不可修改，只能追加追偿或冲回: " + claim.getExternalClaimNo());
        }
    }

    private void checkExpectedVersions(Claim claim, Integer expectedEvidenceVersion,
                                       Integer expectedClaimVersion) {
        if (expectedEvidenceVersion != null
                && expectedEvidenceVersion != claim.getShipment().getEvidenceVersion()) {
            throw new ConflictException("交接证据已变化：当前版本 "
                    + claim.getShipment().getEvidenceVersion() + "，请求基于版本 "
                    + expectedEvidenceVersion + "，请基于最新证据重新提交");
        }
        if (expectedClaimVersion != null && expectedClaimVersion != claim.getContentVersion()) {
            throw new ConflictException("索赔内容已变化：当前版本 " + claim.getContentVersion()
                    + "，请求基于版本 " + expectedClaimVersion + "，请基于最新索赔内容重新提交");
        }
    }

    private void requireBasedOnCurrent(Claim claim, LiabilityDecision decision) {
        if (decision.getBasedEvidenceVersion() != claim.getShipment().getEvidenceVersion()) {
            throw new ConflictException("责任决定基于过期的交接证据版本 "
                    + decision.getBasedEvidenceVersion() + "，当前版本为 "
                    + claim.getShipment().getEvidenceVersion() + "，请重开决定并重新分摊");
        }
        if (decision.getBasedClaimVersion() != claim.getContentVersion()) {
            throw new ConflictException("责任决定基于过期的索赔内容版本 "
                    + decision.getBasedClaimVersion() + "，当前版本为 " + claim.getContentVersion()
                    + "，请重开决定并重新分摊");
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    // ---------------------------------------------------------------------
    // 视图装配
    // ---------------------------------------------------------------------

    private DecisionView toDecisionView(LiabilityDecision decision, boolean settled) {
        Claim claim = decision.getClaim();
        boolean isStale = decision.getBasedEvidenceVersion() != claim.getShipment().getEvidenceVersion()
                || decision.getBasedClaimVersion() != claim.getContentVersion();
        List<EntryView> entryViews = decision.getEntries().stream()
                .map(e -> toEntryView(e, settled))
                .toList();
        return new DecisionView(
                decision.getId(),
                claim.getExternalClaimNo(),
                decision.getStatus().name(),
                Money.normalize(decision.getApprovedAmount()),
                decision.getBasedEvidenceVersion(),
                decision.getBasedClaimVersion(),
                claim.getShipment().getEvidenceVersion(),
                claim.getContentVersion(),
                isStale,
                settled,
                entryViews,
                decision.getCreatedAt(),
                decision.getUpdatedAt(),
                decision.getConfirmedAt());
    }

    private EntryView toEntryView(LiabilityEntry entry, boolean settled) {
        BigDecimal recovery = adjustments.sumByEntryAndType(entry.getId(), AdjustmentType.RECOVERY);
        BigDecimal reversal = adjustments.sumByEntryAndType(entry.getId(), AdjustmentType.REVERSAL);
        BigDecimal net = entry.getAllocatedAmount().add(recovery).subtract(reversal);
        CarrierSegment segment = entry.getSegment();
        return new EntryView(
                entry.getId(),
                segment.getId(),
                segment.getSequenceNo(),
                segment.getCarrierCode(),
                segment.getCarrierName(),
                entry.getShareRatio(),
                Money.normalize(entry.getAllocatedAmount()),
                Money.normalize(recovery),
                Money.normalize(reversal),
                Money.normalize(net),
                settled);
    }

    private SettlementView toSettlementView(Settlement settlement) {
        return new SettlementView(
                settlement.getId(),
                settlement.getDecision().getId(),
                Money.normalize(settlement.getSettledAmount()),
                settlement.getSettledAt(),
                settlement.getRemark());
    }

    private AdjustmentView toAdjustmentView(Adjustment adjustment) {
        CarrierSegment segment = adjustment.getEntry().getSegment();
        return new AdjustmentView(
                adjustment.getId(),
                adjustment.getEntry().getId(),
                segment.getId(),
                segment.getCarrierCode(),
                adjustment.getType().name(),
                Money.normalize(adjustment.getAmount()),
                adjustment.getReason(),
                adjustment.getCreatedAt());
    }
}
