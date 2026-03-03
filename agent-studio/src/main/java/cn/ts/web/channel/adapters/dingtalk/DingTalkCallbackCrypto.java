package cn.ts.web.channel.adapters.dingtalk;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class DingTalkCallbackCrypto {

    private static final int RANDOM_PREFIX_LENGTH = 16;
    private static final int PKCS7_BLOCK_SIZE = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String decryptAndVerify(String signature,
                                   String timestamp,
                                   String nonce,
                                   String encryptedBase64,
                                   DingTalkSecurityContext context) {
        validateNotBlank(signature, "signature is required");
        validateNotBlank(timestamp, "timestamp is required");
        validateNotBlank(nonce, "nonce is required");
        validateNotBlank(encryptedBase64, "encrypt is required");
        validateContext(context);

        String expectedSignature = generateSignature(context.token(), timestamp, nonce, encryptedBase64);
        if (!expectedSignature.equals(signature)) {
            throw new IllegalArgumentException("Invalid DingTalk callback signature");
        }

        byte[] aesKey = decodeAesKey(context.aesKey());
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        byte[] plain = aesDecrypt(encrypted, aesKey);
        byte[] unpadded = removePkcs7Padding(plain);

        if (unpadded.length < RANDOM_PREFIX_LENGTH + 4) {
            throw new IllegalArgumentException("Invalid DingTalk callback payload");
        }

        ByteBuffer lengthBuffer = ByteBuffer.wrap(unpadded, RANDOM_PREFIX_LENGTH, 4).order(ByteOrder.BIG_ENDIAN);
        int messageLength = lengthBuffer.getInt();
        int messageStart = RANDOM_PREFIX_LENGTH + 4;
        int messageEnd = messageStart + messageLength;
        if (messageLength < 0 || messageEnd > unpadded.length) {
            throw new IllegalArgumentException("Invalid DingTalk callback payload length");
        }

        String ownerKeyInPayload = new String(unpadded, messageEnd, unpadded.length - messageEnd, StandardCharsets.UTF_8);
        if (!context.ownerKey().isBlank() && !context.ownerKey().equals(ownerKeyInPayload)) {
            throw new IllegalArgumentException("DingTalk callback owner key mismatch");
        }

        return new String(unpadded, messageStart, messageLength, StandardCharsets.UTF_8);
    }

    public Map<String, String> buildSuccessResponse(DingTalkSecurityContext context) {
        validateContext(context);
        String nonce = randomAlphaNumeric(RANDOM_PREFIX_LENGTH);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String encrypted = encrypt("success", context, timestamp, nonce);
        String signature = generateSignature(context.token(), timestamp, nonce, encrypted);

        Map<String, String> response = new HashMap<>();
        response.put("msg_signature", signature);
        response.put("encrypt", encrypted);
        response.put("timeStamp", timestamp);
        response.put("nonce", nonce);
        return response;
    }

    public String encrypt(String plainText,
                          DingTalkSecurityContext context,
                          String timestamp,
                          String nonce) {
        validateNotBlank(plainText, "plainText is required");
        validateNotBlank(timestamp, "timestamp is required");
        validateNotBlank(nonce, "nonce is required");
        validateContext(context);

        byte[] aesKey = decodeAesKey(context.aesKey());
        byte[] random = randomAlphaNumeric(RANDOM_PREFIX_LENGTH).getBytes(StandardCharsets.UTF_8);
        byte[] plain = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] ownerKey = context.ownerKey().getBytes(StandardCharsets.UTF_8);
        byte[] messageLengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(plain.length).array();

        byte[] raw = concat(random, messageLengthBytes, plain, ownerKey);
        byte[] padded = addPkcs7Padding(raw);
        byte[] encrypted = aesEncrypt(padded, aesKey);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String generateSignature(String token, String timestamp, String nonce, String encryptedBase64) {
        validateNotBlank(token, "token is required");
        validateNotBlank(timestamp, "timestamp is required");
        validateNotBlank(nonce, "nonce is required");
        validateNotBlank(encryptedBase64, "encrypted payload is required");
        try {
            String[] values = new String[]{token, timestamp, nonce, encryptedBase64};
            Arrays.sort(values);
            String joined = String.join("", values);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(joined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                String piece = Integer.toHexString(b & 0xff);
                if (piece.length() < 2) {
                    hex.append('0');
                }
                hex.append(piece);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate DingTalk signature", e);
        }
    }

    private void validateContext(DingTalkSecurityContext context) {
        if (context == null) {
            throw new IllegalArgumentException("DingTalk security context is required");
        }
        validateNotBlank(context.token(), "DingTalk callback token is required");
        validateNotBlank(context.aesKey(), "DingTalk callback aesKey is required");
        validateNotBlank(context.ownerKey(), "DingTalk callback owner key is required");
    }

    private byte[] decodeAesKey(String aesKey) {
        try {
            String normalized = aesKey.endsWith("=") ? aesKey : aesKey + "=";
            byte[] decoded = Base64.getDecoder().decode(normalized);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("DingTalk callback aesKey must decode to 32 bytes");
            }
            return decoded;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid DingTalk callback aesKey", e);
        }
    }

    private byte[] aesDecrypt(byte[] encrypted, byte[] aesKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt DingTalk callback payload", e);
        }
    }

    private byte[] aesEncrypt(byte[] plain, byte[] aesKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encrypt DingTalk callback payload", e);
        }
    }

    private byte[] addPkcs7Padding(byte[] input) {
        int amountToPad = PKCS7_BLOCK_SIZE - (input.length % PKCS7_BLOCK_SIZE);
        if (amountToPad == 0) {
            amountToPad = PKCS7_BLOCK_SIZE;
        }
        byte padChr = (byte) amountToPad;
        byte[] output = Arrays.copyOf(input, input.length + amountToPad);
        Arrays.fill(output, input.length, output.length, padChr);
        return output;
    }

    private byte[] removePkcs7Padding(byte[] input) {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Invalid DingTalk callback padding");
        }
        int pad = input[input.length - 1] & 0xff;
        if (pad <= 0 || pad > PKCS7_BLOCK_SIZE || pad > input.length) {
            throw new IllegalArgumentException("Invalid DingTalk callback padding");
        }
        for (int i = input.length - pad; i < input.length; i++) {
            if ((input[i] & 0xff) != pad) {
                throw new IllegalArgumentException("Invalid DingTalk callback padding");
            }
        }
        return Arrays.copyOf(input, input.length - pad);
    }

    private byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] result = new byte[total];
        int position = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, position, array.length);
            position += array.length;
        }
        return result;
    }

    private String randomAlphaNumeric(int size) {
        char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        StringBuilder value = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            value.append(chars[SECURE_RANDOM.nextInt(chars.length)]);
        }
        return value.toString();
    }

    private void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public record DingTalkSecurityContext(String token, String aesKey, String ownerKey) {
    }
}
