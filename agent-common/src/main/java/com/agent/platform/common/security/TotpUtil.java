package com.agent.platform.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP（RFC 6238，HMAC-SHA1 / 30s / 6 位）工具，用于账号 MFA 二次验证。
 *
 * <p>无第三方依赖，Base32 编解码为手写实现。</p>
 */
public final class TotpUtil {

    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int WINDOW = 1;

    private TotpUtil() {
    }

    /** 生成 20 字节随机密钥（Base32，32 字符） */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** 生成 otpauth 迁移 URL，供用户扫码绑定（Google Authenticator 等） */
    public static String otpauthUrl(String secret, String account, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /** 校验动态口令（允许前后 1 个时间窗抖动） */
    public static boolean verify(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        byte[] key;
        try {
            key = base32Decode(secret.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String target = code.trim();
        long counter = System.currentTimeMillis() / 1000L / 30L;
        for (long offset = -WINDOW; offset <= WINDOW; offset++) {
            if (constantTimeEquals(hotp(key, counter + offset), target)) {
                return true;
            }
        }
        return false;
    }

    private static String hotp(byte[] key, long counter) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // ---------- Base32 ----------

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String text) throws IllegalArgumentException {
        String upper = text.toUpperCase().replace("=", "");
        int bytesLen = upper.length() * 5 / 8;
        byte[] result = new byte[bytesLen];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (int i = 0; i < upper.length(); i++) {
            int value = indexOf(upper.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("非法 Base32 字符: " + upper.charAt(i));
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                result[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return result;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < BASE32.length; i++) {
            if (BASE32[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
