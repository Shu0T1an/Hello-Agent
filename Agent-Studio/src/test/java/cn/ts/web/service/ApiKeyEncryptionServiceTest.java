package cn.ts.web.service;

import cn.ts.web.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyEncryptionService 单元测试
 */
class ApiKeyEncryptionServiceTest {

    private ApiKeyEncryptionService encryptionService;
    private EncryptionConfig encryptionConfig;

    @BeforeEach
    void setUp() {
        // 创建测试用的加密配置
        encryptionConfig = new EncryptionConfig() {
            @Override
            public javax.crypto.SecretKey getSecretKey() {
                // 创建一个 32 字节的密钥用于测试
                byte[] keyBytes = new byte[32];
                for (int i = 0; i < 32; i++) {
                    keyBytes[i] = (byte) i;
                }
                return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            }
        };

        encryptionService = new ApiKeyEncryptionService(encryptionConfig);
        try {
            encryptionService.init();
        } catch (Exception e) {
            fail("Failed to initialize encryption service: " + e.getMessage());
        }
    }

    @Test
    void testEncrypt_ValidText_ReturnsEncryptedString() {
        String plainText = "test-api-key-12345";

        String encrypted = encryptionService.encrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);
        // Base64 编码的结果应该比原文长
        assertTrue(encrypted.length() > plainText.length());
    }

    @Test
    void testDecrypt_ValidEncryptedText_ReturnsOriginalText() {
        String plainText = "test-api-key-12345";

        String encrypted = encryptionService.encrypt(plainText);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plainText, decrypted);
    }

    @Test
    void testDecrypt_InvalidText_ThrowsException() {
        String invalidEncrypted = "invalid-encrypted-text";

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decrypt(invalidEncrypted);
        });
    }

    @Test
    void testEncrypt_NullInput_ReturnsNull() {
        String encrypted = encryptionService.encrypt(null);

        assertNull(encrypted);
    }

    @Test
    void testDecrypt_NullInput_ReturnsNull() {
        String decrypted = encryptionService.decrypt(null);

        assertNull(decrypted);
    }

    @Test
    void testEncrypt_EmptyInput_ReturnsEmpty() {
        String encrypted = encryptionService.encrypt("");

        assertEquals("", encrypted);
    }

    @Test
    void testDecrypt_EmptyInput_ReturnsEmpty() {
        String decrypted = encryptionService.decrypt("");

        assertEquals("", decrypted);
    }

    @Test
    void testEncryptDecrypt_RoundTrip_PreservesText() {
        String[] testCases = {
                "simple-key",
                "key-with-special-chars-!@#$%",
                "key-with-numbers-123456",
                "very-long-key-" + "x".repeat(100),
                "key-with-unicode-中文测试"
        };

        for (String plainText : testCases) {
            String encrypted = encryptionService.encrypt(plainText);
            String decrypted = encryptionService.decrypt(encrypted);

            assertEquals(plainText, decrypted, "Round trip failed for: " + plainText);
        }
    }

    @Test
    void testIsValid_ValidEncryptedText_ReturnsTrue() {
        String plainText = "test-key";
        String encrypted = encryptionService.encrypt(plainText);

        assertTrue(encryptionService.isValid(encrypted));
    }

    @Test
    void testIsValid_InvalidText_ReturnsFalse() {
        assertFalse(encryptionService.isValid("invalid-text"));
        assertFalse(encryptionService.isValid(""));
        assertFalse(encryptionService.isValid(null));
    }

    @Test
    void testMultipleEncryptions_ProduceDifferentResults() {
        String plainText = "test-key";

        String encrypted1 = encryptionService.encrypt(plainText);
        String encrypted2 = encryptionService.encrypt(plainText);

        // 相同的明文应该产生不同的密文（因为使用随机 IV）
        assertNotEquals(encrypted1, encrypted2);

        // 但解密后应该得到相同的明文
        assertEquals(plainText, encryptionService.decrypt(encrypted1));
        assertEquals(plainText, encryptionService.decrypt(encrypted2));
    }
}
