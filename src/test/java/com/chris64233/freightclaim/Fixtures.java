package com.chris64233.freightclaim;

import com.chris64233.freightclaim.domain.LossType;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.service.ShipmentService;
import com.chris64233.freightclaim.web.view.ClaimView;
import com.chris64233.freightclaim.web.view.SegmentView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 测试夹具：快速搭建 运输单→承运段→交接证据→索赔。 */
@Component
public class Fixtures {

    private final ShipmentService shipmentService;
    private final ClaimService claimService;

    public Fixtures(ShipmentService shipmentService, ClaimService claimService) {
        this.shipmentService = shipmentService;
        this.claimService = claimService;
    }

    public String newShipment(String shipmentNo) {
        shipmentService.createShipment(shipmentNo, "上海", "北京");
        return shipmentNo;
    }

    /** 追加 n 个承运段，承运商编码 C1..Cn。 */
    public List<Long> segments(String shipmentNo, int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            SegmentView v = shipmentService.appendSegment(shipmentNo,
                    "C" + i, "承运商" + i, "节点" + (i - 1), "节点" + i);
            ids.add(v.id());
        }
        return ids;
    }

    public int evidence(String shipmentNo, String content) {
        return shipmentService.appendEvidence(shipmentNo, null, "交接点", content, null)
                .evidenceVersionAfter();
    }

    public ClaimView claim(String shipmentNo, String claimNo, String eventRef, String amount) {
        return claimService.register(shipmentNo, claimNo, eventRef,
                new BigDecimal(amount), LossType.DAMAGE, "破损照片 IMG-1").claim();
    }
}
