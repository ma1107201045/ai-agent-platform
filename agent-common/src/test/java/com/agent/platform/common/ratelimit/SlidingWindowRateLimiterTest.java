package com.agent.platform.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 滑动窗口限流器测试（注入可控时钟，不依赖真实时间）
 */
class SlidingWindowRateLimiterTest {

    @Test
    void allowsWithinLimitAndBlocksBeyond() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000, 3);
        assertTrue(limiter.tryAcquire("ip1"));
        assertTrue(limiter.tryAcquire("ip1"));
        assertTrue(limiter.tryAcquire("ip1"));
        assertFalse(limiter.tryAcquire("ip1"), "第 4 次应被限流");
        assertEquals(3, limiter.currentCount("ip1"));
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000, 1);
        assertTrue(limiter.tryAcquire("ip1"));
        assertTrue(limiter.tryAcquire("ip2"), "不同 IP 互不影响");
        assertFalse(limiter.tryAcquire("ip1"));
    }

    @Test
    void windowSlidesAndReleasesPermits() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000, 2, 10_000, clock::get);

        assertTrue(limiter.tryAcquire("ip1"));   // t=0
        clock.set(30_000);
        assertTrue(limiter.tryAcquire("ip1"));   // t=30s
        assertFalse(limiter.tryAcquire("ip1"));  // 窗口内已满

        clock.set(61_000);                       // t=61s，第一次请求滑出窗口
        assertTrue(limiter.tryAcquire("ip1"), "窗口滑动后应释放许可");
    }

    @Test
    void blankKeyFallsBackToSharedBucket() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000, 1);
        assertTrue(limiter.tryAcquire(null));
        assertFalse(limiter.tryAcquire("  "), "空 key 共用兜底桶，应被限流");
    }

    @Test
    void idleKeysAreEvictedOnCleanup() {
        AtomicLong clock = new AtomicLong(0);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000, 1, 2, clock::get);
        limiter.tryAcquire("ip1");
        limiter.tryAcquire("ip2");
        clock.set(120_000); // 全部过期
        limiter.tryAcquire("ip3"); // 触发惰性清理（size=2 > threshold=2? 触发于 > 阈值，第三次 insert 时 size=2 不超，第四次才清理）
        assertEquals(1, limiter.currentCount("ip3"));
        assertEquals(0, limiter.currentCount("ip1"), "过期 key 计数应为 0");
    }
}
