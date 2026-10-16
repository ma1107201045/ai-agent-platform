package com.agent.platform.common.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量内存滑动窗口限流器（单实例，无外部依赖）。
 * <p>
 * 适用于公开门户等场景的 IP 级防护：记录每个 key 在窗口内的请求时间戳，
 * 超限时拒绝。集群部署时需替换为 Redis 等集中式实现。
 * <p>
 * 线程安全：按 key 粒度同步；附带惰性清理防止 key 无限增长。
 */
public class SlidingWindowRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final long windowMillis;
    private final int maxPermits;
    /** 触发全量惰性清理的 key 数量阈值 */
    private final int cleanupThreshold;
    private final java.util.function.LongSupplier clock;

    public SlidingWindowRateLimiter(long windowMillis, int maxPermits) {
        this(windowMillis, maxPermits, 10_000, System::currentTimeMillis);
    }

    /** 可注入时钟，便于测试 */
    public SlidingWindowRateLimiter(long windowMillis, int maxPermits, int cleanupThreshold,
                                    java.util.function.LongSupplier clock) {
        this.windowMillis = windowMillis;
        this.maxPermits = maxPermits;
        this.cleanupThreshold = cleanupThreshold;
        this.clock = clock;
    }

    /**
     * 尝试获取一次许可。
     *
     * @return true 放行；false 超限
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            key = "_unknown_";
        }
        if (windows.size() > cleanupThreshold) {
            evictIdleKeys();
        }
        long now = clock.getAsLong();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            evictExpired(deque, now);
            if (deque.size() >= maxPermits) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** 当前 key 在窗口内的已用次数（主要供测试与监控） */
    public int currentCount(String key) {
        Deque<Long> deque = windows.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            evictExpired(deque, clock.getAsLong());
            return deque.size();
        }
    }

    private void evictExpired(Deque<Long> deque, long now) {
        long cutoff = now - windowMillis;
        while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
            deque.pollFirst();
        }
    }

    private void evictIdleKeys() {
        long now = clock.getAsLong();
        for (Map.Entry<String, Deque<Long>> entry : windows.entrySet()) {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                evictExpired(deque, now);
                if (deque.isEmpty()) {
                    windows.remove(entry.getKey(), deque);
                }
            }
        }
    }
}
