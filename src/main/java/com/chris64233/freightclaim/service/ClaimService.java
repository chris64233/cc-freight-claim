package com.chris64233.freightclaim.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.freightclaim.api.dto.ClaimEvidenceRequest;
import com.chris64233.freightclaim.api.dto.ClaimRequest;
import com.chris64233.freightclaim.api.dto.ClaimUpdateRequest;
import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimEvidence;
import com.chris64233.freightclaim.domain.ClaimStatus;
import com.chris64233.freightclaim.domain.Shipment;
import com.chris64233.freightclaim.exception.BusinessRuleException;
import com.chris64233.freightclaim.exception.ConflictException;
import com.chris64233.freightclaim.exception.NotFoundException;
import com.chris64233.freightclaim.repository.ClaimEvidenceRepository;
import com.chris64233.freightclaim.repository.ClaimRepository;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimEvidenceRepository evidenceRepository;
    private final ShipmentService shipmentService;

    public ClaimService(ClaimRepository claimRepository,
                        ClaimEvidenceRepository evidenceRepository,
                        ShipmentService shipmentService) {
        this.claimRepository = claimRepository;
        this.evidenceRepository = evidenceRepository;
        this.shipmentService = shipmentService;
    }

    /**
     * 创建索赔。外部索赔号幂等：重复提交同一外部索赔号直接返回既有索赔，
     * 不重复创建、不报错。
     * 同一运输单 + 损失事件只能存在一笔活动索赔（数据库唯一锁键兜底并发）。
     */
    @Transactional
    public Claim create(ClaimRequest request) {
        Claim existing = claimRepository.findByExternalClaimNo(request.externalClaimNo()).orElse(null);
        if (existing != null) {
            // 幂等：同一外部索赔号返回既有记录
            return existing;
        }
        Shipment shipment = shipmentService.getByShipmentNo(request.shipmentNo());
        if (claimRepository.existsActive(shipment.getId(), request.lossEventNo(), ClaimStatus.ACTIVE)) {
            throw new ConflictException("同一运输单与损失事件已存在活动索赔: "
                    + request.shipmentNo() + " / " + request.lossEventNo());
        }
        Claim claim = new Claim(request.externalClaimNo(), shipment, request.lossEventNo(),
                request.lossType(), request.lossAmount(), request.description(),
                shipment.getEvidenceVersion());
        try {
            claim = claimRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException e) {
            // 并发下活动索赔唯一锁键冲突
            throw new ConflictException("同一运输单与损失事件已存在活动索赔（并发冲突）");
        }

        if (request.evidences() != null) {
            for (ClaimEvidenceRequest ev : request.evidences()) {
                evidenceRepository.save(new ClaimEvidence(claim, ev.evidenceRef(),
                        ev.evidenceType(), ev.summary()));
            }
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public Claim getById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("索赔不存在: id=" + id));
    }

    @Transactional(readOnly = true)
    public Claim getByExternalNo(String externalClaimNo) {
        return claimRepository.findByExternalClaimNo(externalClaimNo)
                .orElseThrow(() -> new NotFoundException("索赔不存在: " + externalClaimNo));
    }

    @Transactional(readOnly = true)
    public List<Claim> listByShipment(Long shipmentId) {
        return claimRepository.findByShipmentIdOrderByIdAsc(shipmentId);
    }

    @Transactional(readOnly = true)
    public List<ClaimEvidence> listEvidences(Long claimId) {
        return evidenceRepository.findByClaimIdOrderByIdAsc(claimId);
    }

    /** 追加索赔证据并递增索赔内容版本（旧版本上的责任决定确认将冲突）。 */
    @Transactional
    public ClaimEvidence addEvidence(Long claimId, ClaimEvidenceRequest request) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.ACTIVE) {
            throw new ConflictException("索赔已关闭，不能追加证据");
        }
        if (evidenceRepository.existsByClaimIdAndEvidenceRef(claimId, request.evidenceRef())) {
            throw new ConflictException("索赔证据编号已存在: " + request.evidenceRef());
        }
        ClaimEvidence evidence = new ClaimEvidence(claim, request.evidenceRef(),
                request.evidenceType(), request.summary());
        evidence = evidenceRepository.save(evidence);

        claim.bumpContentVersion();
        claimRepository.save(claim);
        return evidence;
    }

    /** 修改索赔内容（金额/类型/描述）并递增内容版本。 */
    @Transactional
    public Claim updateContent(Long claimId, ClaimUpdateRequest request) {
        Claim claim = getById(claimId);
        if (claim.getStatus() != ClaimStatus.ACTIVE) {
            throw new ConflictException("索赔已关闭，不能修改内容");
        }
        claim.changeContent(request.lossType(), request.lossAmount(), request.description());
        return claimRepository.save(claim);
    }

    /**
     * 关闭索赔。关闭后释放“活动索赔唯一锁键”（置 null），
     * 同一损失事件可重新提起索赔；已确认/已结算的决定仍然只读保留。
     */
    @Transactional
    public Claim close(Long claimId) {
        Claim claim = getById(claimId);
        if (claim.getStatus() == ClaimStatus.CLOSED) {
            throw new BusinessRuleException("索赔已关闭");
        }
        claim.close();
        return claimRepository.save(claim);
    }
}
