package com.chris64233.freightclaim.service;

import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimStatus;
import com.chris64233.freightclaim.domain.HandoverEvidence;
import com.chris64233.freightclaim.domain.LossType;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.repo.ClaimRepository;
import com.chris64233.freightclaim.repo.HandoverEvidenceRepository;
import com.chris64233.freightclaim.repo.SettlementRepository;
import com.chris64233.freightclaim.support.ConflictException;
import com.chris64233.freightclaim.support.Money;
import com.chris64233.freightclaim.support.NotFoundException;
import com.chris64233.freightclaim.support.ValidationException;
import com.chris64233.freightclaim.web.view.ClaimView;
import com.chris64233.freightclaim.web.view.EvidenceView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 索赔登记、内容变更与关闭。索赔号是登记接口的幂等键。 */
@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ShipmentService shipmentService;
    private final SettlementRepository settlements;
    private final HandoverEvidenceRepository handoverEvidences;

    public ClaimService(ClaimRepository claims, ShipmentService shipmentService,
                        SettlementRepository settlements, HandoverEvidenceRepository handoverEvidences) {
        this.claims = claims;
        this.shipmentService = shipmentService;
        this.settlements = settlements;
        this.handoverEvidences = handoverEvidences;
    }

    /**
     * 登记索赔（幂等）：
     * <ul>
     *   <li>同一外部索赔号重复登记：运输单与损失事件一致时原样返回已有索赔，不重复建单；
     *       不一致则返回 409（幂等键被不同内容复用）。</li>
     *   <li>同一运输单 + 同一损失事件只允许一笔活动索赔，由数据库唯一约束兜底并发竞争。</li>
     * </ul>
     */
    @Transactional
    public ClaimRegistration register(String shipmentNo, String externalClaimNo, String lossEventRef,
                                      BigDecimal lossAmount, LossType lossType, String evidence) {
        if (externalClaimNo == null || externalClaimNo.isBlank()) {
            throw new ValidationException("外部索赔号不能为空");
        }
        if (lossEventRef == null || lossEventRef.isBlank()) {
            throw new ValidationException("损失事件标识 lossEventRef 不能为空");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new ValidationException("索赔证据不能为空");
        }
        if (lossType == null) {
            throw new ValidationException("损失类型不能为空");
        }
        BigDecimal amount = Money.requirePositive(lossAmount, "损失金额");
        Shipment shipment = shipmentService.requireShipment(shipmentNo);
        String claimNo = externalClaimNo.trim();
        String eventRef = lossEventRef.trim();

        Claim existing = claims.findByExternalClaimNo(claimNo).orElse(null);
        if (existing != null) {
            if (!existing.getShipment().getId().equals(shipment.getId())
                    || !existing.getLossEventRef().equals(eventRef)) {
                throw new ConflictException(
                        "外部索赔号已存在但运输单或损失事件不一致: " + claimNo);
            }
            // 幂等：同一业务请求重试，原样返回。
            return new ClaimRegistration(toView(existing), false);
        }

        // 预检活动索赔唯一（并发热点由 (shipment_id, active_slot) 唯一约束兜底）。
        boolean activeEventExists = claims
                .findByShipmentIdAndStatusOrderByIdAsc(shipment.getId(), ClaimStatus.ACTIVE)
                .stream().anyMatch(c -> c.getLossEventRef().equals(eventRef));
        if (activeEventExists) {
            throw new ConflictException(
                    "运输单 " + shipmentNo + " 的损失事件 " + eventRef + " 已存在活动索赔");
        }

        try {
            Claim claim = new Claim(claimNo, shipment, eventRef, amount, lossType,
                    evidence, OffsetDateTime.now(ZoneOffset.UTC));
            return new ClaimRegistration(toView(claims.saveAndFlush(claim)), true);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "索赔登记冲突：索赔号重复，或该运输单损失事件已存在活动索赔");
        }
    }

    /**
     * 变更索赔内容（金额/损失类型/证据），内容版本加 1。
     * 已结算索赔不可修改；变更后基于旧内容版本的责任决定将返回冲突。
     */
    @Transactional
    public ClaimView revise(String externalClaimNo, BigDecimal lossAmount, LossType lossType, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            throw new ValidationException("索赔证据不能为空");
        }
        if (lossType == null) {
            throw new ValidationException("损失类型不能为空");
        }
        BigDecimal amount = Money.requirePositive(lossAmount, "损失金额");
        Claim claim = requireByExternalNo(externalClaimNo);
        if (!claim.isActive()) {
            throw new ConflictException("索赔已关闭，不能修改内容: " + externalClaimNo);
        }
        if (settlements.existsByDecisionClaimId(claim.getId())) {
            throw new ConflictException("索赔已结算，不能修改内容: " + externalClaimNo);
        }
        claim.revise(amount, lossType, evidence, OffsetDateTime.now(ZoneOffset.UTC));
        return toView(claim);
    }

    /** 关闭索赔并释放活动槽位（已结算的索赔不能关闭）。 */
    @Transactional
    public ClaimView close(String externalClaimNo) {
        Claim claim = requireByExternalNo(externalClaimNo);
        if (!claim.isActive()) {
            return toView(claim);
        }
        if (settlements.existsByDecisionClaimId(claim.getId())) {
            throw new ConflictException("索赔已结算，不能关闭: " + externalClaimNo);
        }
        claim.close(OffsetDateTime.now(ZoneOffset.UTC));
        return toView(claim);
    }

    @Transactional(readOnly = true)
    public Claim requireByExternalNo(String externalClaimNo) {
        return claims.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
    }

    @Transactional(readOnly = true)
    public Claim getByExternalNo(String externalClaimNo) {
        return requireByExternalNo(externalClaimNo);
    }

    /** 索赔清单台账：按运输单列出全部索赔。 */
    @Transactional(readOnly = true)
    public List<ClaimView> listByShipment(String shipmentNo) {
        Shipment shipment = shipmentService.requireShipment(shipmentNo);
        return claims.findByShipmentIdOrderByIdAsc(shipment.getId()).stream()
                .map(ClaimService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimView getView(String externalClaimNo) {
        return toView(requireByExternalNo(externalClaimNo));
    }

    /**
     * 索赔证据台账：运输单上的全部交接证据 + 该运输单各索赔自带的证据，按时间顺序合并。
     */
    @Transactional(readOnly = true)
    public List<EvidenceView> evidenceLedger(String shipmentNo) {
        Shipment shipment = shipmentService.requireShipment(shipmentNo);
        List<EvidenceView> result = new ArrayList<>();
        for (HandoverEvidence e : handoverEvidences.findByShipmentIdOrderByIdAsc(shipment.getId())) {
            result.add(new EvidenceView("HANDOVER", null, e.getAfterSegmentSeq(),
                    e.getLocation(), e.getContent(), e.getRecordedAt()));
        }
        for (Claim c : claims.findByShipmentIdOrderByIdAsc(shipment.getId())) {
            result.add(new EvidenceView("CLAIM", c.getExternalClaimNo(), null,
                    null, c.getEvidence(), c.getUpdatedAt()));
        }
        return result;
    }

    /** 领域对象转视图。 */
    public static ClaimView toView(Claim claim) {
        return new ClaimView(
                claim.getId(),
                claim.getExternalClaimNo(),
                claim.getShipment().getShipmentNo(),
                claim.getLossEventRef(),
                Money.normalize(claim.getLossAmount()),
                claim.getLossType().name(),
                claim.getEvidence(),
                claim.getStatus().name(),
                claim.getContentVersion(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }
}
