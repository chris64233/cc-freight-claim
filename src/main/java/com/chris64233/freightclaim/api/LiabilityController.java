package com.chris64233.freightclaim.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.freightclaim.api.dto.DecisionAllocationResponse;
import com.chris64233.freightclaim.api.dto.DecisionConfirmRequest;
import com.chris64233.freightclaim.api.dto.DecisionRequest;
import com.chris64233.freightclaim.api.dto.DecisionResponse;
import com.chris64233.freightclaim.api.dto.LiabilityEntryResponse;
import com.chris64233.freightclaim.domain.DecisionAllocationLine;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.service.LiabilityService;

@RestController
@RequestMapping("/api/decisions")
public class LiabilityController {

    private final LiabilityService liabilityService;

    public LiabilityController(LiabilityService liabilityService) {
        this.liabilityService = liabilityService;
    }

    /** 为索赔创建责任决定草稿。 */
    @PostMapping("/for-claim/{claimId}")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisionResponse createDraft(@PathVariable Long claimId,
                                        @Valid @RequestBody DecisionRequest request) {
        LiabilityDecision decision = liabilityService.createDraft(claimId, request);
        return toDraftResponse(decision);
    }

    /** 修改草稿分摊方案。 */
    @PutMapping("/{id}")
    public DecisionResponse updateDraft(@PathVariable Long id,
                                        @Valid @RequestBody DecisionRequest request) {
        LiabilityDecision decision = liabilityService.updateDraft(id, request);
        return toDraftResponse(decision);
    }

    /**
     * 确认决定：一次性生成全部责任分录。
     * 依据旧版本（新交接证据/索赔内容变化）或并发重复确认返回 409。
     */
    @PostMapping("/{id}/confirm")
    public DecisionResponse confirm(@PathVariable Long id,
                                    @RequestBody(required = false) DecisionConfirmRequest request) {
        LiabilityDecision decision = liabilityService.confirm(id, request);
        List<LiabilityEntryResponse> entries = liabilityService.listEntries(id).stream()
                .map(LiabilityEntryResponse::of).toList();
        return DecisionResponse.confirmed(decision, entries);
    }

    @GetMapping("/{id}")
    public DecisionResponse get(@PathVariable Long id) {
        LiabilityDecision decision = liabilityService.getDecision(id);
        return switch (decision.getStatus()) {
            case CONFIRMED -> {
                List<LiabilityEntryResponse> entries = liabilityService.listEntries(id).stream()
                        .map(LiabilityEntryResponse::of).toList();
                yield DecisionResponse.confirmed(decision, entries);
            }
            case DRAFT -> toDraftResponse(decision);
        };
    }

    /** 责任分摊台账：索赔下的责任决定及其分录。 */
    @GetMapping("/for-claim/{claimId}")
    public List<DecisionResponse> listByClaim(@PathVariable Long claimId) {
        return liabilityService.listByClaim(claimId).stream().map(d -> switch (d.getStatus()) {
            case CONFIRMED -> DecisionResponse.confirmed(d,
                    liabilityService.listEntries(d.getId()).stream()
                            .map(LiabilityEntryResponse::of).toList());
            case DRAFT -> draftResponseOf(d);
        }).toList();
    }

    private DecisionResponse toDraftResponse(LiabilityDecision decision) {
        return draftResponseOf(decision);
    }

    private DecisionResponse draftResponseOf(LiabilityDecision d) {
        List<DecisionAllocationResponse> allocations =
                liabilityService.listDraftAllocations(d.getId()).stream()
                        .map(this::toAllocationResponse).toList();
        return DecisionResponse.draft(d, allocations);
    }

    private DecisionAllocationResponse toAllocationResponse(DecisionAllocationLine line) {
        return new DecisionAllocationResponse(line.getSegment().getSeq(),
                line.getSegment().getCarrierCode(), line.getRatioWeight(), null);
    }
}
