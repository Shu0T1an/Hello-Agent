package cn.ts.web.service;

import cn.ts.web.config.EncryptionConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * API 密钥加密服务
 * 使用 AES-256-GCM 算法进行加密和解密
 */
@Service
public class ApiKeyEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyEncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128; // GCM 认证标签长度
    private static final int IV_LENGTH_BYTE = 12;  // GCM IV 长度

    private final EncryptionConfig encryptionConfig;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    public ApiKeyEncryptionService(EncryptionConfig encryptionConfig) {
        this.encryptionConfig = encryptionConfig;
    }

    @PostConstruct
    public void init() throws Exception {
        SecretKey secretKey = encryptionConfig.getSecretKey();
        encryptCipher = Cipher.getInstance(ALGORITHM);
        decryptCipher = Cipher.getInstance(ALGORITHM);
        logger.info("ApiKeyEncryptionService initialized successfully");
    }

    /**
     * 加密明文
     *
     * @param plainText 明文
     * @return Base64 编码的密文
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[IV_LENGTH_BYTE];
            java.security.SecureRandom.getInstanceStrong().nextBytes(iv);

            // 初始化加密器
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            encryptCipher.init(Cipher.ENCRYPT_MODE, encryptionConfig.getSecretKey(), parameterSpec);

            // 加密
            byte[] cipherText = encryptCipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 组合 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // 返回 Base64 编码
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            logger.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * 解密密文
     *
     * @param encryptedText Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Base64 解码
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // 分离 IV 和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            // 初始化解密器
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            decryptCipher.init(Cipher.DECRYPT_MODE, encryptionConfig.getSecretKey(), parameterSpec);

            // 解密
            byte[] plainText = decryptCipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * 检查密文是否有效
     *
     * @param encryptedText 密文
     * @return 是否有效
     */
    public boolean isValid(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return false;
        }
        try {
            decrypt(encryptedText);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
