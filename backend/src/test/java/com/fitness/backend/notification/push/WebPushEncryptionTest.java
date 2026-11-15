package com.fitness.backend.notification.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RFC 8291 부록 A(Intermediate Values for Encryption)의 값을 그대로 넣어 같은 암호문이 나오는지 본다 */
class WebPushEncryptionTest {

    private static final String PLAINTEXT = "V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String CIPHERTEXT =
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    @Test
    @DisplayName("RFC 8291 부록 A의 입력이면 머리(솔트·4096·65·서버 공개키)와 암호문이 RFC 값과 같다")
    void matchesRfc8291AppendixA() {
        byte[] serverPublic = EcKeys.fromBase64Url(AS_PUBLIC);
        KeyPair serverKeys = new KeyPair(EcKeys.publicKey(serverPublic), EcKeys.privateKey(EcKeys.fromBase64Url(AS_PRIVATE)));

        byte[] body = WebPushEncryption.encrypt(EcKeys.fromBase64Url(PLAINTEXT),
                EcKeys.publicKey(EcKeys.fromBase64Url(UA_PUBLIC)), EcKeys.fromBase64Url(AUTH_SECRET),
                serverKeys, EcKeys.fromBase64Url(SALT));

        ByteBuffer header = ByteBuffer.wrap(body, 0, 86);
        byte[] salt = new byte[16];
        header.get(salt);
        assertArrayEquals(EcKeys.fromBase64Url(SALT), salt);
        assertEquals(4096, header.getInt());
        assertEquals(65, header.get());
        byte[] keyId = new byte[65];
        header.get(keyId);
        assertArrayEquals(serverPublic, keyId);
        assertEquals(CIPHERTEXT, EcKeys.toBase64Url(Arrays.copyOfRange(body, 86, body.length)));
    }

    @Test
    @DisplayName("키를 바이트로 바꿨다 되돌려도 같다 — 공개키 65바이트, 비밀키 32바이트")
    void keysRoundTrip() {
        KeyPair pair = EcKeys.generate();
        byte[] publicBytes = EcKeys.encode((java.security.interfaces.ECPublicKey) pair.getPublic());
        byte[] privateBytes = EcKeys.encode((java.security.interfaces.ECPrivateKey) pair.getPrivate());
        assertEquals(65, publicBytes.length);
        assertEquals(32, privateBytes.length);
        assertArrayEquals(publicBytes, EcKeys.encode(EcKeys.publicKey(publicBytes)));
        assertArrayEquals(privateBytes, EcKeys.encode(EcKeys.privateKey(privateBytes)));
    }

    @Test
    @DisplayName("한 레코드에 들어가지 않는 긴 본문은 받지 않는다")
    void rejectsTooLongPlaintext() {
        KeyPair browser = EcKeys.generate();
        assertThrows(IllegalArgumentException.class, () -> WebPushEncryption.encrypt(
                new byte[WebPushEncryption.MAX_PLAINTEXT + 1],
                (java.security.interfaces.ECPublicKey) browser.getPublic(), new byte[16]));
    }
}
