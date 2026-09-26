package com.chris64233.freightclaim.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.freightclaim.api.dto.AdjustmentRequest;
import com.chris64233.freightclaim.api.dto.AdjustmentResponse;
import com.chris64233.freightclaim.domain.AdjustmentRecord;
import com.chris64233.freightclaim.service.AdjustmentService;
import com.chris64233.freightclaim.service.LiabilityService;

@RestController
@RequestMapping("/api/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;
    private final LiabilityService liabilityService;

    public AdjustmentController(AdjustmentService adjustmentService,
                                LiabilityService liabilityService) {
        this.adjustmentService = adjustmentService;
        this.liabilityService = liabilityService;
    }

    /**
     * 结算后追加调整：RECOVERY 追偿 / REVERSAL 冲回。
     * 已确认决定不可修改，只能追加台账记录。
     */
    @PostMapping("/for-decision/{decisionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse add(@PathVariable Long decisionId,
                                  @Valid @RequestBody AdjustmentRequest request) {
        AdjustmentRecord record = adjustmentService.addAdjustment(decisionId, request);
        return AdjustmentResponse.of(record);
    }

    @GetMapping("/for-entry/{liabilityEntryId}")
    public List<AdjustmentResponse> listByEntry(@PathVariable Long liabilityEntryId) {
        return adjustmentService.listByEntry(liabilityEntryId).stream()
                .map(AdjustmentResponse::of).toList();
    }

    /** 索赔维度调整台账（追偿/冲回）。 */
    @GetMapping("/ledger")
    public List<AdjustmentResponse> claimLedger(@RequestParam Long claimId) {
        liabilityService.getConfirmedDecision(claimId);
        return adjustmentService.listClaimLedger(claimId).stream()
                .map(AdjustmentResponse::of).toList();
    }
}
