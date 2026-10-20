package com.agent.platform.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段落库加密（AES-GCM）。
 *
 * <p>用途：模型供应商 apiKey 等敏感配置加密存储，避免明文落库。</p>
 *
 * <p>行为约定：</p>
 * <ul>
 *   <li>配置 platform.crypto.secret（环境变量 PLATFORM_CRYPTO_SECRET，≥16 字符）后启用加密；
 *       密文带 {@value #PREFIX} 前缀，格式 Base64(IV + 密文)。</li>
 *   <li>未配置密钥时 encrypt 原样返回（明文兼容，打印警告），decrypt 对非密文原样返回，
 *       因此历史明文数据与新加密数据可共存、平滑迁移。</li>
 * </ul>
 */
@Slf4j
@Component
public class CryptoUtil {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    public CryptoUtil(@Value("${platform.crypto.secret:}") String secret) {
        if (secret == null || secret.trim().length() < 16) {
            this.key = null;
            log.warn("[CryptoUtil] platform.crypto.secret 未配置或过短（<16 字符），敏感字段将以明文存储。"
                    + "生产环境请通过环境变量 PLATFORM_CRYPTO_SECRET 注入随机密钥");
        } else {
            this.key = new SecretKeySpec(sha256(secret.trim()), "AES");
        }
    }

    /** 加密（未配置密钥或已是密文时原样返回） */
    public String encrypt(String plain) {
        if (!StringUtils.hasText(plain) || key == null || isEncrypted(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /** 解密（非密文原样返回，兼容历史明文数据） */
    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        if (key == null) {
            throw new IllegalStateException(
                    "platform.crypto.secret 未配置，无法解密已加密的密钥，请配置与加密时相同的密钥");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            ByteBuffer buf = ByteBuffer.wrap(raw);
            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段解密失败（密钥是否被更换？）", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
