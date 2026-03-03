package cn.ts.web.channel.adapters.dingtalk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkCallbackCryptoTest {

    private final DingTalkCallbackCrypto crypto = new DingTalkCallbackCrypto();

    @Test
    void encryptAndDecrypt_ShouldRoundTrip() {
        DingTalkCallbackCrypto.DingTalkSecurityContext context = buildContext();
        String plain = "{\"text\":{\"content\":\"hello\"}}";
        String timestamp = "1700001000";
        String nonce = "nonce-1000";

        String encrypted = crypto.encrypt(plain, context, timestamp, nonce);
        String signature = crypto.generateSignature(context.token(), timestamp, nonce, encrypted);

        String decrypted = crypto.decryptAndVerify(signature, timestamp, nonce, encrypted, context);
        assertEquals(plain, decrypted);
    }

    @Test
    void decryptAndVerify_ShouldRejectInvalidSignature() {
        DingTalkCallbackCrypto.DingTalkSecurityContext context = buildContext();
        String plain = "{\"text\":{\"content\":\"hello\"}}";
        String timestamp = "1700001001";
        String nonce = "nonce-1001";
        String encrypted = crypto.encrypt(plain, context, timestamp, nonce);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> crypto.decryptAndVerify("invalid-signature", timestamp, nonce, encrypted, context)
        );

        assertTrue(ex.getMessage().contains("signature"));
    }

    @Test
    void buildSuccessResponse_ShouldProduceDecryptablePayload() {
        DingTalkCallbackCrypto.DingTalkSecurityContext context = buildContext();
        Map<String, String> response = crypto.buildSuccessResponse(context);

        assertNotNull(response.get("msg_signature"));
        assertNotNull(response.get("encrypt"));
        assertNotNull(response.get("timeStamp"));
        assertNotNull(response.get("nonce"));

        String decrypted = crypto.decryptAndVerify(
                response.get("msg_signature"),
                response.get("timeStamp"),
                response.get("nonce"),
                response.get("encrypt"),
                context
        );
        assertEquals("success", decrypted);
    }

    private DingTalkCallbackCrypto.DingTalkSecurityContext buildContext() {
        byte[] aesBytes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String aesKey = Base64.getEncoder().encodeToString(aesBytes).replace("=", "");
        return new DingTalkCallbackCrypto.DingTalkSecurityContext("token-1000", aesKey, "app-key-1000");
    }
}
