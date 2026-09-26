package com.chris64233.freightclaim;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.freightclaim.domain.AdjustmentType;
import com.chris64233.freightclaim.domain.LossType;
import com.chris64233.freightclaim.service.AllocationItem;
import com.chris64233.freightclaim.service.ClaimService;
import com.chris64233.freightclaim.service.LiabilityService;
import com.chris64233.freightclaim.support.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 并发场景：悲观行锁 + 唯一约束必须保证
 * 1) 并发责任决定不会产生两套结果；2) 并发冲回不超额；3) 同事件活动索赔唯一。
 */
@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private Fixtures fixtures;
    @Autowired
    private LiabilityService liability;
    @Autowired
    private ClaimService claims;

    @Test
    void concurrentConfirmsProduceExactlyOneSetOfEntries() throws Exception {
        fixtures.newShipment("S-CC");
        List<Long> seg = fixtures.segments("S-CC", 2);
        fixtures.evidence("S-CC", "证据");
        fixtures.claim("S-CC", "CL-CC", "EVT-1", "100.00");

        var planA = List.of(new AllocationItem(seg.get(0), new BigDecimal("1")),
                new AllocationItem(seg.get(1), new BigDecimal("1")));
        var planB = List.of(new AllocationItem(seg.get(0), new BigDecimal("3")),
                new AllocationItem(seg.get(1), new BigDecimal("1")));

        runConcurrently(
                () -> liability.confirm("CL-CC", new BigDecimal("100.00"), planA, null, null),
                () -> liability.confirm("CL-CC", new BigDecimal("100.00"), planB, null, null));

        // 无论谁胜出：只有一行决定、一套分录（2 条），不会出现 4 条或两套结果。
        var decision = liability.getDecision("CL-CC");
        assertThat(decision.entries()).hasSize(2);
        assertThat(decision.approvedAmount()).isEqualByComparingTo("100.00");
        BigDecimal sum = decision.entries().stream()
                .map(e -> e.allocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentReversalsCannotExceedSettledLiability() throws Exception {
        fixtures.newShipment("S-CR");
        List<Long> seg = fixtures.segments("S-CR", 1);
        fixtures.evidence("S-CR", "证据");
        fixtures.claim("S-CR", "CL-CR", "EVT-1", "100.00");
        liability.confirm("CL-CR", new BigDecimal("50.00"),
                List.of(new AllocationItem(seg.get(0), new BigDecimal("1"))), null, null);
        liability.settle("CL-CR", null);
        Long entryId = liability.getDecision("CL-CR").entries().get(0).id();

        // 两个线程同时冲回 50.00（= 全部已结算责任），只能有一笔成功。
        runConcurrently(
                () -> liability.addAdjustment("CL-CR", entryId, AdjustmentType.REVERSAL,
                        new BigDecimal("50.00"), "冲回1"),
                () -> liability.addAdjustment("CL-CR", entryId, AdjustmentType.REVERSAL,
                        new BigDecimal("50.00"), "冲回2"));

        var entry = liability.getDecision("CL-CR").entries().get(0);
        assertThat(entry.totalReversal()).isEqualByComparingTo("50.00");
        assertThat(entry.netLiability()).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentRegistrationOfSameEventYieldsOneActiveClaim() throws Exception {
        fixtures.newShipment("S-CA");
        fixtures.segments("S-CA", 1);

        runConcurrently(
                () -> claims.register("S-CA", "CL-CA-1", "EVT-1",
                        new BigDecimal("100.00"), LossType.DAMAGE, "证据1"),
                () -> claims.register("S-CA", "CL-CA-2", "EVT-1",
                        new BigDecimal("100.00"), LossType.DAMAGE, "证据2"));

        List<com.chris64233.freightclaim.web.view.ClaimView> active =
                claims.listByShipment("S-CA");
        assertThat(active).hasSize(1);
    }

    /** 并发执行两个任务并等待结束；允许其中之一抛出 {@link ConflictException}。 */
    private void runConcurrently(Callable<?> a, Callable<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = pool.submit(synced(a, ready, start));
            Future<?> f2 = pool.submit(synced(b, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int conflicts = 0;
            for (Future<?> f : List.of(f1, f2)) {
                try {
                    f.get(15, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof ConflictException) {
                        conflicts++;
                    } else {
                        throw new AssertionError("并发任务出现非预期异常: " + cause, cause);
                    }
                }
            }
            assertThat(conflicts)
                    .as("并发竞争中应有且仅有一个失败者")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** 让任务在起跑栅栏后同时执行。 */
    private Callable<Object> synced(Callable<?> task, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return task.call();
        };
    }
}
