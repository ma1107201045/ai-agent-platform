package com.agent.platform.support;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.ratelimit.SlidingWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 公开门户防护：按客户端 IP 做滑动窗口限流。
 * <p>
 * 匿名访问（未携带 API Key）默认 30 次/分钟；
 * 携带合法 API Key 的访问默认 120 次/分钟（应用级配额仍由 AppApiKeyService 控制）。
 * 阈值可通过 application.yml 的 portal.public.rate-limit.* 调整。
 */
@Component
public class PortalPublicGuard {

    private final SlidingWindowRateLimiter anonLimiter;
    private final SlidingWindowRateLimiter keyedLimiter;

    public PortalPublicGuard(
            @Value("${portal.public.rate-limit.anon-per-minute:30}") int anonPerMinute,
            @Value("${portal.public.rate-limit.keyed-per-minute:120}") int keyedPerMinute) {
        this.anonLimiter = new SlidingWindowRateLimiter(60_000, anonPerMinute);
        this.keyedLimiter = new SlidingWindowRateLimiter(60_000, keyedPerMinute);
    }

    /**
     * 限流检查，超限抛出 BizException(429 语义)。
     *
     * @param authenticated 是否已携带并通过 API Key 校验
     */
    public void check(String clientIp, boolean authenticated) {
        SlidingWindowRateLimiter limiter = authenticated ? keyedLimiter : anonLimiter;
        if (!limiter.tryAcquire(clientIp)) {
            throw new BizException(429, "访问过于频繁，请稍后再试");
        }
    }

    /** 提取客户端 IP：优先代理头 X-Forwarded-For 首跳，其次直连地址 */
    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
