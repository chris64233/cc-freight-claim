package com.chris64233.freightclaim.web;

import com.chris64233.freightclaim.service.AllocationItem;
import com.chris64233.freightclaim.service.LiabilityService;
import com.chris64233.freightclaim.web.request.AdjustmentRequest;
import com.chris64233.freightclaim.web.request.DecisionRequest;
import com.chris64233.freightclaim.web.request.SettlementRequest;
import com.chris64233.freightclaim.web.view.AdjustmentView;
import com.chris64233.freightclaim.web.view.DecisionView;
import com.chris64233.freightclaim.web.view.SettlementView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims/{externalClaimNo}")
public class LiabilityController {

    private final LiabilityService liabilityService;

    public LiabilityController(LiabilityService liabilityService) {
        this.liabilityService = liabilityService;
    }

    /** 保存草拟分摊方案（可反复修改，不产生确认结果）。 */
    @PostMapping("/decision/draft")
    public DecisionView draft(@PathVariable String externalClaimNo,
                              @Valid @RequestBody DecisionRequest request) {
        return liabilityService.draft(externalClaimNo, request.approvedAmount(),
                toItems(request.allocations()),
                request.expectedEvidenceVersion(), request.expectedClaimVersion());
    }

    /** 确认责任决定：一次性生成全部责任分录。相同方案重试幂等；过期版本/不同方案返回 409。 */
    @PostMapping("/decision/confirm")
    public DecisionView confirm(@PathVariable String externalClaimNo,
                                @Valid @RequestBody DecisionRequest request) {
        return liabilityService.confirm(externalClaimNo, request.approvedAmount(),
                toItems(request.allocations()),
                request.expectedEvidenceVersion(), request.expectedClaimVersion());
    }

    /** 结算前重开已确认决定，以便基于新证据/新索赔内容重做。 */
    @PostMapping("/decision/reopen")
    public DecisionView reopen(@PathVariable String externalClaimNo) {
        return liabilityService.reopen(externalClaimNo);
    }

    /** 责任分摊台账。 */
    @GetMapping("/decision")
    public DecisionView getDecision(@PathVariable String externalClaimNo) {
        return liabilityService.getDecision(externalClaimNo);
    }

    @PostMapping("/settlement")
    public ResponseEntity<SettlementView> settle(@PathVariable String externalClaimNo,
                                                  @RequestBody(required = false) SettlementRequest request) {
        String remark = request == null ? null : request.remark();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liabilityService.settle(externalClaimNo, remark));
    }

    @GetMapping("/settlement")
    public SettlementView getSettlement(@PathVariable String externalClaimNo) {
        return liabilityService.getSettlement(externalClaimNo);
    }

    /** 结算后追加追偿或冲回（冲回不得超过对应已结算责任）。 */
    @PostMapping("/adjustments")
    public ResponseEntity<AdjustmentView> addAdjustment(@PathVariable String externalClaimNo,
                                                        @Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liabilityService.addAdjustment(externalClaimNo, request.entryId(),
                        request.type(), request.amount(), request.reason()));
    }

    /** 调整台账：追偿/冲回记录。 */
    @GetMapping("/adjustments")
    public List<AdjustmentView> listAdjustments(@PathVariable String externalClaimNo) {
        return liabilityService.listAdjustments(externalClaimNo);
    }

    private List<AllocationItem> toItems(List<com.chris64233.freightclaim.web.request.AllocationRequest> reqs) {
        return reqs.stream()
                .map(r -> new AllocationItem(r.segmentId(), r.weight()))
                .toList();
    }
}
