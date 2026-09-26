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

import com.chris64233.freightclaim.api.dto.ClaimEvidenceRequest;
import com.chris64233.freightclaim.api.dto.ClaimEvidenceResponse;
import com.chris64233.freightclaim.api.dto.ClaimRequest;
import com.chris64233.freightclaim.api.dto.ClaimResponse;
import com.chris64233.freightclaim.api.dto.ClaimUpdateRequest;
import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.ClaimEvidence;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.service.ShipmentService;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final ShipmentService shipmentService;

    public ClaimController(ClaimService claimService, ShipmentService shipmentService) {
        this.claimService = claimService;
        this.shipmentService = shipmentService;
    }

    /** 创建索赔；外部索赔号重复时幂等返回既有索赔（200）。 */
    @PostMapping
    public ClaimResponse create(@Valid @RequestBody ClaimRequest request) {
        Claim claim = claimService.create(request);
        return ClaimResponse.of(claim);
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable Long id) {
        return ClaimResponse.of(claimService.getById(id));
    }

    @GetMapping("/by-external/{externalClaimNo}")
    public ClaimResponse getByExternalNo(@PathVariable String externalClaimNo) {
        return ClaimResponse.of(claimService.getByExternalNo(externalClaimNo));
    }

    @GetMapping
    public List<ClaimResponse> listByShipmentNo(@org.springframework.web.bind.annotation.RequestParam
                                                String shipmentNo) {
        Long shipmentId = shipmentService.getByShipmentNo(shipmentNo).getId();
        return claimService.listByShipment(shipmentId).stream().map(ClaimResponse::of).toList();
    }

    /** 索赔证据台账。 */
    @GetMapping("/{id}/evidences")
    public List<ClaimEvidenceResponse> evidences(@PathVariable Long id) {
        claimService.getById(id);
        return claimService.listEvidences(id).stream().map(ClaimEvidenceResponse::of).toList();
    }

    @PostMapping("/{id}/evidences")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimEvidenceResponse addEvidence(@PathVariable Long id,
                                             @Valid @RequestBody ClaimEvidenceRequest request) {
        ClaimEvidence evidence = claimService.addEvidence(id, request);
        return ClaimEvidenceResponse.of(evidence);
    }

    /** 修改索赔内容（金额/类型/描述），内容版本递增。 */
    @PutMapping("/{id}")
    public ClaimResponse update(@PathVariable Long id, @Valid @RequestBody ClaimUpdateRequest request) {
        return ClaimResponse.of(claimService.updateContent(id, request));
    }

    @PostMapping("/{id}/close")
    public ClaimResponse close(@PathVariable Long id) {
        return ClaimResponse.of(claimService.close(id));
    }
}
