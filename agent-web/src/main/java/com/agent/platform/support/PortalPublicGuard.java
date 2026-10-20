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
 * <p>
 * IP 识别策略：默认只信任 TCP 直连地址（remoteAddr），忽略客户端可伪造的
 * X-Forwarded-For / X-Real-IP 头，防止攻击者伪造来源绕过限流。
 * 仅当部署在可信反向代理之后时，将 portal.public.trust-proxy 设为 true 才读取代理头。
 */
@Component
public class PortalPublicGuard {

    /** 是否信任代理头（仅当处于可信反向代理之后时开启） */
    private static volatile boolean trustProxy = false;

    private final SlidingWindowRateLimiter anonLimiter;
    private final SlidingWindowRateLimiter keyedLimiter;

    public PortalPublicGuard(
            @Value("${portal.public.rate-limit.anon-per-minute:30}") int anonPerMinute,
            @Value("${portal.public.rate-limit.keyed-per-minute:120}") int keyedPerMinute,
            @Value("${portal.public.trust-proxy:false}") boolean trustProxy) {
        this.anonLimiter = new SlidingWindowRateLimiter(60_000, anonPerMinute);
        this.keyedLimiter = new SlidingWindowRateLimiter(60_000, keyedPerMinute);
        PortalPublicGuard.trustProxy = trustProxy;
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

    /**
     * 提取客户端 IP。
     * 默认仅使用 remoteAddr（客户端无法伪造）；trust-proxy=true 时才读取
     * X-Forwarded-For 首跳 / X-Real-IP（此时假定只有可信代理能写入这些头）。
     */
    public static String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
