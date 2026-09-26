package com.chris64233.freightclaim.web;

import com.chris64233.freightclaim.service.ClaimRegistration;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.web.request.ClaimRequest;
import com.chris64233.freightclaim.web.request.ClaimReviseRequest;
import com.chris64233.freightclaim.web.view.ClaimView;
import com.chris64233.freightclaim.web.view.EvidenceView;
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
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /** 登记索赔（外部索赔号幂等）。重复的同内容请求返回 200 + 既有索赔，新建返回 201。 */
    @PostMapping
    public ResponseEntity<ClaimView> register(@Valid @RequestBody ClaimRequest request) {
        ClaimRegistration result = claimService.register(request.shipmentNo(), request.externalClaimNo(),
                request.lossEventRef(), request.lossAmount(), request.lossType(), request.evidence());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(result.claim())
                : ResponseEntity.ok(result.claim());
    }

    @GetMapping("/{externalClaimNo}")
    public ClaimView get(@PathVariable String externalClaimNo) {
        return claimService.getView(externalClaimNo);
    }

    @PostMapping("/{externalClaimNo}/revise")
    public ClaimView revise(@PathVariable String externalClaimNo,
                            @Valid @RequestBody ClaimReviseRequest request) {
        return claimService.revise(externalClaimNo, request.lossAmount(),
                request.lossType(), request.evidence());
    }

    @PostMapping("/{externalClaimNo}/close")
    public ClaimView close(@PathVariable String externalClaimNo) {
        return claimService.close(externalClaimNo);
    }

    /** 索赔证据台账：运输单的交接证据 + 各索赔自带证据。 */
    @GetMapping("/shipment/{shipmentNo}/evidence-ledger")
    public List<EvidenceView> evidenceLedger(@PathVariable String shipmentNo) {
        return claimService.evidenceLedger(shipmentNo);
    }

    /** 按运输单列出索赔台账。 */
    @GetMapping("/shipment/{shipmentNo}")
    public List<ClaimView> listByShipment(@PathVariable String shipmentNo) {
        return claimService.listByShipment(shipmentNo);
    }
}
