package com.chris64233.freightclaim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.freightclaim.api.dto.AdjustmentRequest;
import com.chris64233.freightclaim.api.dto.ClaimEvidenceRequest;
import com.chris64233.freightclaim.api.dto.ClaimRequest;
import com.chris64233.freightclaim.api.dto.DecisionConfirmRequest;
import com.chris64233.freightclaim.api.dto.DecisionRequest;
import com.chris64233.freightclaim.api.dto.HandoverEvidenceRequest;
import com.chris64233.freightclaim.api.dto.SegmentRequest;
import com.chris64233.freightclaim.api.dto.SettlementRequest;
import com.chris64233.freightclaim.api.dto.ShipmentRequest;
import com.chris64233.freightclaim.domain.AdjustmentType;
import com.chris64233.freightclaim.domain.Claim;
import com.chris64233.freightclaim.domain.DecisionStatus;
import com.chris64233.freightclaim.domain.LiabilityDecision;
import com.chris64233.freightclaim.domain.LiabilityEntry;
import com.chris64233.freightclaim.domain.LossType;
import com.chris64233.freightclaim.exception.ConflictException;

@SpringBootTest
class ClaimWorkflowServiceTest {

    @Autowired
    private ShipmentService shipmentService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private LiabilityService liabilityService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private AdjustmentService adjustmentService;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private ShipmentRequest shipmentRequest(String no) {
        return new ShipmentRequest(no, "上海", "北京", List.of(
                new SegmentRequest("C1", "承运甲", "上海", "南京"),
                new SegmentRequest("C2", "承运乙", "南京", "济南"),
                new SegmentRequest("C3", "承运丙", "济南", "北京")));
    }

    private ClaimRequest claimRequest(String externalNo, String shipmentNo, String eventNo, String amount) {
        return new ClaimRequest(externalNo, shipmentNo, eventNo, LossType.DAMAGE,
                bd(amount), "外箱破损",
                List.of(new ClaimEvidenceRequest("IMG-1", "PHOTO", "破损照片")));
    }

    private DecisionRequest decisionRequest(String approved) {
        return new DecisionRequest(bd(approved), "三方按 2:3:5 分摊", List.of(
                new DecisionRequest.AllocationRequest(1, bd("2")),
                new DecisionRequest.AllocationRequest(2, bd("3")),
                new DecisionRequest.AllocationRequest(3, bd("5"))));
    }

    @Test
    void fullWorkflow_confirmSettleAdjust() {
        shipmentService.createShipment(shipmentRequest("S-FULL"));
        Claim claim = claimService.create(claimRequest("EXT-FULL", "S-FULL", "EVT-1", "1000.00"));

        LiabilityDecision draft = liabilityService.createDraft(claim.getId(), decisionRequest("800.00"));
        LiabilityDecision confirmed = liabilityService.confirm(draft.getId(), new DecisionConfirmRequest(null, null));

        assertThat(confirmed.getStatus()).isEqualTo(DecisionStatus.CONFIRMED);
        List<LiabilityEntry> entries = liabilityService.listEntries(confirmed.getId());
        assertThat(entries).hasSize(3);
        BigDecimal sum = entries.stream().map(LiabilityEntry::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 分摊之和严格等于认可金额
        assertThat(sum).isEqualByComparingTo("800.00");
        assertThat(entries).extracting(LiabilityEntry::getAllocatedAmount)
                .containsExactly(bd("160.00"), bd("240.00"), bd("400.00"));

        // 结算前不能冲回
        assertThatThrownBy(() -> adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(2, AdjustmentType.REVERSAL, bd("10.00"), null, null)))
                .isInstanceOf(Exception.class);

        // 第一次结算：先结前两段
        settlementService.settle(confirmed.getId(), new SettlementRequest("STL-1", "首期", List.of(
                new SettlementRequest.SettlementLineRequest(1, bd("160.00")),
                new SettlementRequest.SettlementLineRequest(2, bd("100.00")))));
        // 第二次结算超出第二段剩余（140）应拒绝
        assertThatThrownBy(() -> settlementService.settle(confirmed.getId(),
                new SettlementRequest("STL-2", "超额", List.of(
                        new SettlementRequest.SettlementLineRequest(2, bd("140.01"))))))
                .isInstanceOf(Exception.class);
        // 正常结算剩余
        settlementService.settle(confirmed.getId(), new SettlementRequest("STL-2", "尾款", List.of(
                new SettlementRequest.SettlementLineRequest(2, bd("140.00")),
                new SettlementRequest.SettlementLineRequest(3, bd("400.00")))));

        // 追偿累计不超过认可分摊
        adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(1, AdjustmentType.RECOVERY, bd("160.00"), "REC-1", null));
        assertThatThrownBy(() -> adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(1, AdjustmentType.RECOVERY, bd("0.01"), null, null)))
                .isInstanceOf(Exception.class);

        // 冲回不超过已结算责任
        adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(2, AdjustmentType.REVERSAL, bd("200.00"), "REV-1", null));
        assertThatThrownBy(() -> adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(2, AdjustmentType.REVERSAL, bd("40.01"), null, null)))
                .isInstanceOf(Exception.class);
        // 刚好冲平允许
        adjustmentService.addAdjustment(confirmed.getId(),
                new AdjustmentRequest(2, AdjustmentType.REVERSAL, bd("40.00"), "REV-2", null));

        // 台账查询
        assertThat(settlementService.listClaimLedger(claim.getId())).hasSize(4);
        // 成功落账的调整：追偿 1 笔 + 冲回 2 笔（超额的两笔被拒绝，不入账）
        assertThat(adjustmentService.listClaimLedger(claim.getId())).hasSize(3);
        assertThat(claimService.listEvidences(claim.getId())).hasSize(1);
    }

    @Test
    void claimIdempotentByExternalClaimNo() {
        shipmentService.createShipment(shipmentRequest("S-IDEM"));
        Claim first = claimService.create(claimRequest("EXT-IDEM", "S-IDEM", "EVT-1", "100.00"));
        Claim second = claimService.create(claimRequest("EXT-IDEM", "S-IDEM", "EVT-1", "999.00"));
        // 同一外部索赔号：幂等返回既有记录，金额不被覆盖
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getLossAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void onlyOneActiveClaimPerShipmentAndLossEvent() {
        shipmentService.createShipment(shipmentRequest("S-ACTIVE"));
        claimService.create(claimRequest("EXT-A1", "S-ACTIVE", "EVT-X", "100.00"));
        // 不同外部索赔号、同一运输单与损失事件 -> 冲突
        assertThatThrownBy(() -> claimService.create(
                claimRequest("EXT-A2", "S-ACTIVE", "EVT-X", "100.00")))
                .isInstanceOf(ConflictException.class);

        // 关闭旧索赔后可重新提起
        claimService.close(claimService.getByExternalNo("EXT-A1").getId());
        Claim reopened = claimService.create(claimRequest("EXT-A3", "S-ACTIVE", "EVT-X", "120.00"));
        assertThat(reopened.getLossAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void approvedAmountCannotExceedClaimAmountAndAllocationMustEqualApproved() {
        shipmentService.createShipment(shipmentRequest("S-APPR"));
        Claim claim = claimService.create(claimRequest("EXT-APPR", "S-APPR", "EVT-1", "100.00"));
        assertThatThrownBy(() -> liabilityService.createDraft(claim.getId(), decisionRequest("100.01")))
                .isInstanceOf(Exception.class);

        LiabilityDecision draft = liabilityService.createDraft(claim.getId(), decisionRequest("100.00"));
        liabilityService.confirm(draft.getId(), null);
        List<LiabilityEntry> entries = liabilityService.listEntries(draft.getId());
        BigDecimal sum = entries.stream().map(LiabilityEntry::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    void newHandoverEvidenceConflictsDecisionBasedOnOldVersion() {
        shipmentService.createShipment(shipmentRequest("S-EV"));
        Claim claim = claimService.create(claimRequest("EXT-EV", "S-EV", "EVT-1", "300.00"));
        LiabilityDecision draft = liabilityService.createDraft(claim.getId(), decisionRequest("300.00"));

        // 新交接证据到达，运输单证据版本递增
        shipmentService.addHandoverEvidence("S-EV",
                new HandoverEvidenceRequest(1, 2, "HO-1", "南京交接"));

        assertThatThrownBy(() -> liabilityService.confirm(draft.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("旧证据版本");
    }

    @Test
    void claimContentChangeConfirmsOldDecisionConflicts() {
        shipmentService.createShipment(shipmentRequest("S-CC"));
        Claim claim = claimService.create(claimRequest("EXT-CC", "S-CC", "EVT-1", "300.00"));
        LiabilityDecision draft = liabilityService.createDraft(claim.getId(), decisionRequest("300.00"));

        // 追加索赔证据 -> 内容版本递增
        claimService.addEvidence(claim.getId(),
                new ClaimEvidenceRequest("IMG-2", "PHOTO", "新增照片"));
        assertThatThrownBy(() -> liabilityService.confirm(draft.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("旧内容版本");

        // 重新拟定草稿后可确认
        liabilityService.updateDraft(draft.getId(), decisionRequest("280.00"));
        LiabilityDecision confirmed = liabilityService.confirm(draft.getId(), null);
        assertThat(confirmed.getStatus()).isEqualTo(DecisionStatus.CONFIRMED);
        List<LiabilityEntry> entries = liabilityService.listEntries(confirmed.getId());
        BigDecimal sum = entries.stream().map(LiabilityEntry::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("280.00");

        // 确认后不能再修改
        assertThatThrownBy(() -> liabilityService.updateDraft(confirmed.getId(), decisionRequest("10.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmedDecisionCannotBeConfirmedTwice() {
        shipmentService.createShipment(shipmentRequest("S-DUP"));
        Claim claim = claimService.create(claimRequest("EXT-DUP", "S-DUP", "EVT-1", "100.00"));
        LiabilityDecision draft = liabilityService.createDraft(claim.getId(), decisionRequest("100.00"));
        liabilityService.confirm(draft.getId(), null);

        assertThatThrownBy(() -> liabilityService.confirm(draft.getId(), null))
                .isInstanceOf(ConflictException.class);
        // 同一索赔不能再建第二笔草稿/决定
        assertThatThrownBy(() -> liabilityService.createDraft(claim.getId(), decisionRequest("100.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void concurrentConfirmsProduceOnlyOneSetOfEntries() throws Exception {
        shipmentService.createShipment(shipmentRequest("S-CONC"));
        Claim claim = claimService.create(claimRequest("EXT-CONC", "S-CONC", "EVT-1", "300.00"));
        // 预先建两笔草稿，两线程各自确认自己的草稿，模拟并发责任决定
        LiabilityDecision d1 = liabilityService.createDraft(claim.getId(), decisionRequest("300.00"));
        LiabilityDecision d2 = liabilityService.createDraft(claim.getId(), decisionRequest("300.00"));

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Long> decisionIds = List.of(d1.getId(), d2.getId());

        for (int i = 0; i < threads; i++) {
            final long decisionId = decisionIds.get(i);
            pool.submit(() -> {
                try {
                    start.await();
                    liabilityService.confirm(decisionId, null);
                    success.incrementAndGet();
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                } catch (Exception e) {
                    // 锁等待/约束异常也计为失败路径，不应出现两套成功结果
                    conflict.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
        // 最终只有一套责任分录
        List<LiabilityDecision> all = liabilityService.listByClaim(claim.getId());
        long confirmedCount = all.stream().filter(d -> d.getStatus() == DecisionStatus.CONFIRMED).count();
        assertThat(confirmedCount).isEqualTo(1);
        long entryCount = all.stream()
                .filter(d -> d.getStatus() == DecisionStatus.CONFIRMED)
                .mapToLong(d -> liabilityService.listEntries(d.getId()).size())
                .sum();
        assertThat(entryCount).isEqualTo(3);
    }
}
