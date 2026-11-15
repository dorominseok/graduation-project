package com.fitness.backend.notification.push;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 본문 암호화 — RFC 8291(Message Encryption for Web Push) + RFC 8188(aes128gcm). JDK 암호만 쓴다.
 *
 * <pre>
 *   ecdh_secret = ECDH(서버 임시 비밀키, 브라우저 공개키)
 *   PRK_key     = HMAC-SHA-256(auth_secret, ecdh_secret)
 *   IKM         = HMAC-SHA-256(PRK_key, "WebPush: info" ‖ 0x00 ‖ 브라우저 공개키 ‖ 서버 임시 공개키 ‖ 0x01)
 *   PRK         = HMAC-SHA-256(salt, IKM)
 *   CEK         = HMAC-SHA-256(PRK, "Content-Encoding: aes128gcm" ‖ 0x00 ‖ 0x01)의 앞 16바이트
 *   NONCE       = HMAC-SHA-256(PRK, "Content-Encoding: nonce" ‖ 0x00 ‖ 0x01)의 앞 12바이트
 *   본문        = salt(16) ‖ rs(4096, 4바이트) ‖ idlen(65) ‖ 서버 임시 공개키 ‖ AES-128-GCM(CEK, NONCE, 평문 ‖ 0x02)
 * </pre>
 *
 * <p>레코드는 하나뿐이라 평문 뒤에 마지막 레코드 표시(0x02)만 붙이고 채움은 넣지 않는다. 맞는지는 RFC 8291
 * 부록 A의 값으로 테스트한다({@code WebPushEncryptionTest}).
 */
public final class WebPushEncryption {

    /** 레코드 크기 — 본문이 한 레코드에 들어가야 한다 */
    static final int RECORD_SIZE = 4096;
    /** 평문 상한. 레코드 크기에서 마지막 표시(1)와 GCM 태그(16)를 뺀 것 */
    public static final int MAX_PLAINTEXT = RECORD_SIZE - 17;

    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /** 보낼 때마다 서버 임시 키와 솔트를 새로 만든다 */
    public static byte[] encrypt(byte[] plaintext, ECPublicKey userAgentPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, userAgentPublic, authSecret, EcKeys.generate(), salt);
    }

    /** 임시 키와 솔트를 받는다 — RFC의 테스트 값을 그대로 넣어 보려고 */
    static byte[] encrypt(byte[] plaintext, ECPublicKey userAgentPublic, byte[] authSecret, KeyPair serverKeys,
                          byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("푸시 본문이 너무 길다: " + plaintext.length + "바이트");
        }
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(serverKeys.getPrivate());
            agreement.doPhase(userAgentPublic, true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] userAgentKey = EcKeys.encode(userAgentPublic);
            byte[] serverKey = EcKeys.encode((ECPublicKey) serverKeys.getPublic());

            byte[] prkKey = hmac(authSecret, ecdhSecret);
            byte[] ikm = hmac(prkKey, concat(KEY_INFO, userAgentKey, serverKey, new byte[] {1}));
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, CEK_INFO), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, NONCE_INFO), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

            byte[] header = ByteBuffer.allocate(16 + 4 + 1 + serverKey.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) serverKey.length).put(serverKey)
                    .array();
            return concat(header, ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("푸시 본문을 암호화하지 못했다", e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
