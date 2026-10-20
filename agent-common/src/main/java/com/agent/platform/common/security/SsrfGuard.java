package com.agent.platform.common.security;

import com.agent.platform.common.exception.BizException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * SSRF 防护：校验出站请求 URL，阻断对内网/回环/云元数据地址的访问。
 *
 * <p>用于工作流 HTTP 节点、工具（connector）调用等"用户可配置 URL"的出口请求。
 * 拦截范围：非 http(s) 协议、localhost、回环地址、内网地址（10/8、172.16/12、192.168/16）、
 * 链路本地地址（169.254/16，含云元数据 169.254.169.254）、0.0.0.0、组播地址。</p>
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * 校验 URL 是否允许访问，不允许时抛出 BizException
     *
     * @param url 渲染变量后的最终请求地址
     */
    public static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BizException("请求地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BizException("请求地址格式非法: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BizException("请求地址缺少协议（仅支持 http/https）: " + url);
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BizException("仅支持 http/https 协议，当前为: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException("请求地址缺少主机名: " + url);
        }
        if ("localhost".equalsIgnoreCase(host) || host.endsWith(".localhost")
                || "metadata.google.internal".equalsIgnoreCase(host)) {
            throw new BizException("禁止访问本机或云元数据地址: " + host);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BizException("无法解析主机名: " + host);
        }
        for (InetAddress addr : addresses) {
            if (isInternal(addr)) {
                throw new BizException("禁止访问内网/保留地址: " + host + " (" + addr.getHostAddress() + ")");
            }
        }
    }

    /** 是否为内网/保留地址 */
    private static boolean isInternal(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        // 兜底：部分保留段未被 JDK 判定（如 100.64/10 CGNAT），按 IPv4 首字节再拦一道
        if (addr instanceof Inet4Address) {
            int first = addr.getAddress()[0] & 0xff;
            int second = addr.getAddress()[1] & 0xff;
            if (first == 0 || first == 127 || first == 10 || first == 100 && second >= 64 && second <= 127
                    || first == 169 && second == 254 || first == 172 && second >= 16 && second <= 31
                    || first == 192 && (second == 168 || second == 0 || second == 2)
                    || first == 198 && (second == 18 || second == 19 || second == 51)
                    || first >= 224) {
                return true;
            }
        }
        return false;
    }
}
