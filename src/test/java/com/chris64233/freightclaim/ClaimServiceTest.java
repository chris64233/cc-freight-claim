package com.chris64233.freightclaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.freightclaim.domain.LossType;
import com.chris64233.freightclaim.service.ClaimRegistration;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.support.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ClaimServiceTest {

    @Autowired
    private Fixtures fixtures;
    @Autowired
    private ClaimService claims;

    @Test
    void registerIsIdempotentByExternalClaimNo() {
        fixtures.newShipment("S-IDEM");
        fixtures.segments("S-IDEM", 2);

        ClaimRegistration first = claims.register("S-IDEM", "CL-IDEM-1", "EVT-1",
                new BigDecimal("100.00"), LossType.DAMAGE, "证据A");
        ClaimRegistration retry = claims.register("S-IDEM", "CL-IDEM-1", "EVT-1",
                new BigDecimal("100.00"), LossType.DAMAGE, "证据A");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.claim().id()).isEqualTo(first.claim().id());
        assertThat(claims.listByShipment("S-IDEM")).hasSize(1);
    }

    @Test
    void sameClaimNoWithDifferentEventConflicts() {
        fixtures.newShipment("S-IDEM2");
        fixtures.segments("S-IDEM2", 2);
        claims.register("S-IDEM2", "CL-IDEM-2", "EVT-1",
                new BigDecimal("100.00"), LossType.DAMAGE, "证据A");

        assertThatThrownBy(() -> claims.register("S-IDEM2", "CL-IDEM-2", "EVT-2",
                new BigDecimal("100.00"), LossType.DAMAGE, "证据B"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOneActiveClaimPerShipmentAndLossEvent() {
        fixtures.newShipment("S-ACTIVE");
        fixtures.segments("S-ACTIVE", 2);
        fixtures.claim("S-ACTIVE", "CL-A1", "EVT-X", "100.00");

        assertThatThrownBy(() ->
                fixtures.claim("S-ACTIVE", "CL-A2", "EVT-X", "100.00"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("活动索赔");

        // 关闭后同一损失事件可以再次登记。
        claims.close("CL-A1");
        var second = fixtures.claim("S-ACTIVE", "CL-A2", "EVT-X", "100.00");
        assertThat(second.status()).isEqualTo("ACTIVE");
    }

    @Test
    void differentLossEventsOnSameShipmentCanCoexist() {
        fixtures.newShipment("S-MULTI");
        fixtures.segments("S-MULTI", 2);
        fixtures.claim("S-MULTI", "CL-M1", "EVT-1", "100.00");
        fixtures.claim("S-MULTI", "CL-M2", "EVT-2", "200.00");
        assertThat(claims.listByShipment("S-MULTI")).hasSize(2);
    }

    @Test
    void reviseAdvancesContentVersion() {
        fixtures.newShipment("S-REV");
        fixtures.segments("S-REV", 2);
        fixtures.claim("S-REV", "CL-REV", "EVT-1", "100.00");

        var revised = claims.revise("CL-REV", new BigDecimal("120.00"),
                LossType.SHORTAGE, "新证据");
        assertThat(revised.contentVersion()).isEqualTo(2);
        assertThat(revised.lossAmount()).isEqualByComparingTo("120.00");
    }
}
