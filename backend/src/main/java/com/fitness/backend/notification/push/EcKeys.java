package com.fitness.backend.notification.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시가 쓰는 P-256 키를 바이트·base64url과 오간다. JDK 암호만 쓴다.
 *
 * <p>공개키는 "압축하지 않은 점"(0x04 ‖ x 32바이트 ‖ y 32바이트, 65바이트), 비밀키는 스칼라 32바이트다 —
 * 브라우저의 {@code PushSubscription.getKey('p256dh')}와 VAPID 키가 이 형식이다.
 */
public final class EcKeys {

    private static final int COORDINATE = 32;
    private static final ECParameterSpec P256;

    static {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            P256 = parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256을 쓸 수 없다", e);
        }
    }

    private EcKeys() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @param uncompressed 0x04 ‖ x ‖ y (65바이트) */
    public static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 1 + 2 * COORDINATE || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 공개키는 0x04로 시작하는 65바이트여야 한다");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + COORDINATE));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + COORDINATE, uncompressed.length));
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 위의 점이 아니다", e);
        }
    }

    /** @param scalar 비밀키 32바이트 */
    public static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar.length != COORDINATE) {
            throw new IllegalArgumentException("P-256 비밀키는 32바이트여야 한다");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[1 + 2 * COORDINATE];
        out[0] = 0x04;
        unsigned(key.getW().getAffineX(), out, 1);
        unsigned(key.getW().getAffineY(), out, 1 + COORDINATE);
        return out;
    }

    public static byte[] encode(ECPrivateKey key) {
        byte[] out = new byte[COORDINATE];
        unsigned(key.getS(), out, 0);
        return out;
    }

    public static byte[] fromBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value.trim());
    }

    public static String toBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** 부호 없는 큰 정수를 앞을 0으로 채운 32바이트로 */
    private static void unsigned(BigInteger value, byte[] out, int offset) {
        byte[] raw = value.toByteArray();
        int skip = raw.length > COORDINATE ? raw.length - COORDINATE : 0;
        int length = raw.length - skip;
        System.arraycopy(raw, skip, out, offset + COORDINATE - length, length);
    }
}
