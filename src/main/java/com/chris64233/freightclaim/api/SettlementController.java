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

import com.chris64233.freightclaim.api.dto.SettlementLineResponse;
import com.chris64233.freightclaim.api.dto.SettlementRequest;
import com.chris64233.freightclaim.api.dto.SettlementResponse;
import com.chris64233.freightclaim.domain.Settlement;
import com.chris64233.freightclaim.service.LiabilityService;
import com.chris64233.freightclaim.service.SettlementService;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final LiabilityService liabilityService;

    public SettlementController(SettlementService settlementService,
                                LiabilityService liabilityService) {
        this.settlementService = settlementService;
        this.liabilityService = liabilityService;
    }

    /** 对已确认责任决定发起结算（可分次）。 */
    @PostMapping("/for-decision/{decisionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse settle(@PathVariable Long decisionId,
                                     @Valid @RequestBody SettlementRequest request) {
        Settlement settlement = settlementService.settle(decisionId, request);
        return toResponse(settlement);
    }

    @GetMapping("/{settlementNo}")
    public SettlementResponse get(@PathVariable String settlementNo) {
        return toResponse(settlementService.getByNo(settlementNo));
    }

    @GetMapping("/for-decision/{decisionId}")
    public List<SettlementResponse> listByDecision(@PathVariable Long decisionId) {
        return settlementService.listByDecision(decisionId).stream()
                .map(this::toResponse).toList();
    }

    /** 索赔维度结算台账。 */
    @GetMapping("/ledger")
    public List<SettlementLineResponse> claimLedger(@RequestParam Long claimId) {
        liabilityService.getConfirmedDecision(claimId);
        return settlementService.listClaimLedger(claimId).stream()
                .map(SettlementLineResponse::of).toList();
    }

    private SettlementResponse toResponse(Settlement settlement) {
        List<SettlementLineResponse> lines =
                settlementService.listLines(settlement.getId()).stream()
                        .map(SettlementLineResponse::of).toList();
        return SettlementResponse.of(settlement, lines);
    }
}
