package cn.ts.web.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 加密配置类
 * 使用 AES-256-GCM 算法加密敏感数据
 */
@Configuration
public class EncryptionConfig {

    /**
     * 加密密钥（从环境变量获取，长度必须是 32 字节）
     */
    @Value("${app.encryption.key:default-32-byte-encryption-key-1234567890}")
    private String encryptionKey;

    /**
     * 获取加密密钥
     */
    public SecretKey getSecretKey() {
        // 确保密钥长度为 32 字节（256 位）
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes32 = new byte[32];
        System.arraycopy(keyBytes, 0, keyBytes32, 0, Math.min(keyBytes.length, 32));
        return new SecretKeySpec(keyBytes32, "AES");
    }
}
