package com.chris64233.freightclaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.freightclaim.domain.AdjustmentType;
import com.chris64233.freightclaim.service.AllocationItem;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.service.LiabilityService;
import com.chris64233.freightclaim.support.ConflictException;
import com.chris64233.freightclaim.support.ValidationException;
import com.chris64233.freightclaim.web.view.AdjustmentView;
import com.chris64233.freightclaim.web.view.DecisionView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LiabilityServiceTest {

    @Autowired
    private Fixtures fixtures;
    @Autowired
    private LiabilityService liability;
    @Autowired
    private ClaimService claims;

    private List<Long> setupClaim(String shipmentNo, String claimNo) {
        fixtures.newShipment(shipmentNo);
        List<Long> segmentIds = fixtures.segments(shipmentNo, 3);
        fixtures.evidence(shipmentNo, "交接证据-1");
        fixtures.claim(shipmentNo, claimNo, "EVT-1", "100.00");
        return segmentIds;
    }

    @Test
    void confirmGeneratesEntriesSummingExactlyToApprovedAmount() {
        List<Long> seg = setupClaim("S-ALLOC", "CL-ALLOC");
        var req = List.of(
                new AllocationItem(seg.get(0), new BigDecimal("1")),
                new AllocationItem(seg.get(1), new BigDecimal("1")),
                new AllocationItem(seg.get(2), new BigDecimal("1")));

        DecisionView decision = liability.confirm("CL-ALLOC", new BigDecimal("100.00"),
                req, null, null);

        assertThat(decision.status()).isEqualTo("CONFIRMED");
        assertThat(decision.entries()).hasSize(3);
        assertThat(decision.entries()).extracting(e -> e.allocatedAmount())
                .containsExactly(new BigDecimal("33.33"),
                        new BigDecimal("33.33"), new BigDecimal("33.34"));
        BigDecimal sum = decision.entries().stream()
                .map(e -> e.allocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
        // 每笔分录只生成一次。
        assertThat(liability.getDecision("CL-ALLOC").entries()).hasSize(3);
    }

    @Test
    void approvedAmountMustNotExceedClaimAmount() {
        List<Long> seg = setupClaim("S-CAP", "CL-CAP");
        assertThatThrownBy(() -> liability.confirm("CL-CAP", new BigDecimal("120.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("不能超过索赔金额");
    }

    @Test
    void allocationSegmentMustBelongToShipment() {
        fixtures.newShipment("S-OWN1");
        fixtures.segments("S-OWN1", 1);
        fixtures.claim("S-OWN1", "CL-OWN1", "EVT-1", "100.00");
        fixtures.newShipment("S-OWN2");
        Long otherSegment = fixtures.segments("S-OWN2", 1).get(0);

        assertThatThrownBy(() -> liability.confirm("CL-OWN1", new BigDecimal("50.00"),
                List.of(new AllocationItem(otherSegment, new BigDecimal("1"))), null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("不属于运输单");
    }

    @Test
    void duplicateConfirmWithSamePlanIsIdempotent() {
        List<Long> seg = setupClaim("S-IDEM", "CL-IDEM-D");
        var req = List.of(new AllocationItem(seg.get(0), new BigDecimal("2")),
                new AllocationItem(seg.get(1), new BigDecimal("1")));

        DecisionView first = liability.confirm("CL-IDEM-D", new BigDecimal("90.00"), req, null, null);
        DecisionView second = liability.confirm("CL-IDEM-D", new BigDecimal("90.00"), req, null, null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(liability.getDecision("CL-IDEM-D").entries()).hasSize(2);
    }

    @Test
    void confirmWithDifferentPlanAfterConfirmationConflicts() {
        List<Long> seg = setupClaim("S-DIFF", "CL-DIFF");
        liability.confirm("CL-DIFF", new BigDecimal("90.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                        new AllocationItem(seg.get(1), new BigDecimal("1"))), null, null);

        // 同索赔提交不同认可金额的方案 -> 冲突。
        assertThatThrownBy(() -> liability.confirm("CL-DIFF", new BigDecimal("60.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已确认");
    }

    @Test
    void newHandoverEvidenceMakesDecisionStaleAndSettleConflicts() {
        List<Long> seg = setupClaim("S-EV", "CL-EV");
        DecisionView confirmed = liability.confirm("CL-EV", new BigDecimal("90.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                        new AllocationItem(seg.get(1), new BigDecimal("1"))), null, null);
        assertThat(confirmed.stale()).isFalse();

        // 新交接证据到达。
        fixtures.evidence("S-EV", "交接证据-2（新）");

        DecisionView stale = liability.getDecision("CL-EV");
        assertThat(stale.stale()).isTrue();
        assertThatThrownBy(() -> liability.settle("CL-EV", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("交接证据");

        // 携带旧证据版本号的重做请求同样冲突。
        assertThatThrownBy(() -> liability.confirm("CL-EV", new BigDecimal("90.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                        new AllocationItem(seg.get(1), new BigDecimal("1"))), 1, 1))
                .isInstanceOf(ConflictException.class);

        // 重开 → 基于新版本重做 → 可结算。
        liability.reopen("CL-EV");
        liability.confirm("CL-EV", new BigDecimal("90.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                        new AllocationItem(seg.get(2), new BigDecimal("1"))), null, null);
        var settlement = liability.settle("CL-EV", "正常结算");
        assertThat(settlement.settledAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void claimContentChangeMakesConfirmedDecisionStale() {
        List<Long> seg = setupClaim("S-CV", "CL-CV");
        liability.confirm("CL-CV", new BigDecimal("90.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null);

        // 索赔内容变化（内容版本 1 -> 2）。
        claims.revise("CL-CV", new BigDecimal("95.00"),
                com.chris64233.freightclaim.domain.LossType.SHORTAGE, "补充证据");

        assertThat(liability.getDecision("CL-CV").stale()).isTrue();
        assertThatThrownBy(() -> liability.settle("CL-CV", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("索赔内容");

        liability.reopen("CL-CV");
        liability.confirm("CL-CV", new BigDecimal("95.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null);
        assertThat(liability.getDecision("CL-CV").stale()).isFalse();
    }

    @Test
    void settledDecisionCannotBeModifiedButAdjustmentsCanBeAppended() {
        List<Long> seg = setupClaim("S-SETTLE", "CL-SETTLE");
        liability.confirm("CL-SETTLE", new BigDecimal("100.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                        new AllocationItem(seg.get(1), new BigDecimal("1"))), null, null);
        liability.settle("CL-SETTLE", null);

        // 草稿/重开/结算后修改全部禁止。
        assertThatThrownBy(() -> liability.draft("CL-SETTLE", new BigDecimal("50.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已结算");
        assertThatThrownBy(() -> liability.reopen("CL-SETTLE"))
                .isInstanceOf(ConflictException.class);
        // 结算重试幂等：返回同一笔结算，不重复生成。
        var firstSettlement = liability.getSettlement("CL-SETTLE");
        var retriedSettlement = liability.settle("CL-SETTLE", null);
        assertThat(retriedSettlement.id()).isEqualTo(firstSettlement.id());

        Long entry0 = liability.getDecision("CL-SETTLE").entries().get(0).id();
        // 追偿不设上限。
        liability.addAdjustment("CL-SETTLE", entry0, AdjustmentType.RECOVERY,
                new BigDecimal("10.00"), "代位追偿");
        // 冲回不得超过该段已结算责任（50.00）。
        liability.addAdjustment("CL-SETTLE", entry0, AdjustmentType.REVERSAL,
                new BigDecimal("30.00"), "部分冲回");
        assertThatThrownBy(() -> liability.addAdjustment("CL-SETTLE", entry0,
                AdjustmentType.REVERSAL, new BigDecimal("20.01"), "超额冲回"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("冲回");

        List<AdjustmentView> ledger = liability.listAdjustments("CL-SETTLE");
        assertThat(ledger).hasSize(2);
        var entry = liability.getDecision("CL-SETTLE").entries().get(0);
        assertThat(entry.totalRecovery()).isEqualByComparingTo("10.00");
        assertThat(entry.totalReversal()).isEqualByComparingTo("30.00");
        assertThat(entry.netLiability()).isEqualByComparingTo("30.00");
        assertThat(entry.settled()).isTrue();
    }

    @Test
    void adjustmentRequiresSettlement() {
        List<Long> seg = setupClaim("S-PRE", "CL-PRE");
        liability.confirm("CL-PRE", new BigDecimal("100.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null);
        Long entryId = liability.getDecision("CL-PRE").entries().get(0).id();

        assertThatThrownBy(() -> liability.addAdjustment("CL-PRE", entryId,
                AdjustmentType.RECOVERY, new BigDecimal("5.00"), "未结算先追偿"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("尚未结算");
    }

    @Test
    void draftCanBeRepeatedlyRewrittenWithOverlappingSegments() {
        List<Long> seg = setupClaim("S-REWRITE", "CL-REWRITE");
        var plan1 = List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                new AllocationItem(seg.get(1), new BigDecimal("1")));
        var plan2 = List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                new AllocationItem(seg.get(2), new BigDecimal("1")),
                new AllocationItem(seg.get(1), new BigDecimal("1")));

        liability.draft("CL-REWRITE", new BigDecimal("90.00"), plan1, null, null);
        // 第二次草拟与第一次存在重叠承运段，清空重建不能撞唯一约束。
        DecisionView rewritten = liability.draft("CL-REWRITE", new BigDecimal("90.00"), plan2, null, null);
        assertThat(rewritten.entries()).hasSize(3);
        assertThat(rewritten.status()).isEqualTo("DRAFT");
    }

    @Test
    void expectedVersionCheckRejectsStaleSubmit() {
        List<Long> seg = setupClaim("S-EXV", "CL-EXV");
        // 初始证据版本为 1（建索赔前已追加一条证据）。携带版本 0 提交应冲突。
        assertThatThrownBy(() -> liability.draft("CL-EXV", new BigDecimal("50.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), 0, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("交接证据");
    }
}
